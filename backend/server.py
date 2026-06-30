import os
import io
import base64
import numpy as np
from PIL import Image
import torch
import torchvision.transforms as T
from fastapi import FastAPI, UploadFile, File, HTTPException
from pydantic import BaseModel
from typing import List, Dict, Any

# Hugging Face imports
from transformers import (
    AutoProcessor, 
    AutoModelForZeroShotObjectDetection,
    SamModel, 
    SamProcessor,
    CLIPProcessor, 
    CLIPModel,
    AutoModelForCausalLM
)

app = FastAPI(title="WardrobeOS Digitization Pipeline Backend")

# Detect device
device = "cuda" if torch.cuda.is_available() else "cpu"
if device == "cpu" and torch.backends.mps.is_available():
    device = "mps"
print(f"Using ML acceleration device: {device}")

# ----------------- LOAD MODELS -----------------
print("Loading Grounding DINO...")
dino_processor = AutoProcessor.from_pretrained("IDEA-Research/grounding-dino-tiny")
dino_model = AutoModelForZeroShotObjectDetection.from_pretrained("IDEA-Research/grounding-dino-tiny").to(device)

print("Loading SAM (Segment Anything)...")
sam_model = SamModel.from_pretrained("facebook/sam-vit-base").to(device)
sam_processor = SamProcessor.from_pretrained("facebook/sam-vit-base")

print("Loading CLIP (FashionCLIP equivalent)...")
clip_model = CLIPModel.from_pretrained("openai/clip-vit-base-patch32").to(device)
clip_processor = CLIPProcessor.from_pretrained("openai/clip-vit-base-patch32")

# 5. FLORENCE-2 & Zero-Shot Metadata Extraction
# Bypass flash_attn dependency check on CPU/MPS
from unittest.mock import patch
from transformers.dynamic_module_utils import get_imports

def fixed_get_imports(filename) -> list:
    if not str(filename).endswith("modeling_florence2.py"):
        return get_imports(filename)
    imports = get_imports(filename)
    if "flash_attn" in imports:
        imports.remove("flash_attn")
    return imports

print("Loading Florence-2...")
with patch("transformers.dynamic_module_utils.get_imports", fixed_get_imports):
    florence_model = AutoModelForCausalLM.from_pretrained("microsoft/Florence-2-base", trust_remote_code=True).to(device)
    florence_processor = AutoProcessor.from_pretrained("microsoft/Florence-2-base", trust_remote_code=True)

print("All models loaded successfully!")

# Helper to convert RGB to L*a*b* color spaces
def rgb_to_lab(rgb_color):
    r, g, b = [c / 255.0 for c in rgb_color]
    # Simple conversion formula
    l = (0.2126 * r + 0.7152 * g + 0.0722 * b) * 100
    a = (r - g) * 100
    bb = (g - b) * 100
    return [float(l), float(a), float(bb)]

def calculate_iou(box1, box2):
    # box format: [xmin, ymin, xmax, ymax]
    x1 = max(box1[0], box2[0])
    y1 = max(box1[1], box2[1])
    x2 = min(box1[2], box2[2])
    y2 = min(box1[3], box2[3])
    
    intersection = max(0.0, x2 - x1) * max(0.0, y2 - y1)
    area1 = (box1[2] - box1[0]) * (box1[3] - box1[1])
    area2 = (box2[2] - box2[0]) * (box2[3] - box2[1])
    union = area1 + area2 - intersection
    
    if union == 0:
        return 0.0
    return intersection / union

@app.post("/digitize")
async def digitize(file: UploadFile = File(...)):
    try:
        contents = await file.read()
        image = Image.open(io.BytesIO(contents)).convert("RGB")
        width, height = image.size
        
        # 1. GROUNDING DINO: Detect bounding boxes for garments
        print("Running Grounding DINO...")
        # Grounding DINO expects dot-separated phrases for zero-shot detection.
        garment_prompt = "clothing . garment . dress . shirt . jacket . pants . shoes . coat . skirt . top . blouse . shorts ."
        inputs = dino_processor(images=image, text=garment_prompt, return_tensors="pt").to(device)
        with torch.no_grad():
            outputs = dino_model(**inputs)
        
        # Lower thresholds to 0.15 to ensure we detect clothing items in busy pictures
        results = dino_processor.post_process_grounded_object_detection(
            outputs,
            inputs.input_ids,
            box_threshold=0.15,
            text_threshold=0.15,
            target_sizes=[image.size[::-1]]
        )[0]
        
        # Determine target bounding boxes (all detections >= 0.15 score, filtered by IoU NMS)
        boxes_to_process = []
        if len(results["boxes"]) > 0:
            scores = results["scores"].cpu().numpy()
            detected = []
            for idx, score in enumerate(scores):
                if score >= 0.15:
                    box = results["boxes"][idx].cpu().numpy().tolist()
                    label = results["labels"][idx]
                    detected.append((box, label, score))
            
            # Sort by score descending
            detected.sort(key=lambda x: x[2], reverse=True)
            
            # Simple IoU non-maximum suppression to prevent duplicates
            filtered = []
            for item in detected:
                box = item[0]
                overlap = False
                for accepted in filtered:
                    if calculate_iou(box, accepted[0]) > 0.45:
                        overlap = True
                        break
                if not overlap:
                    filtered.append(item)
            
            boxes_to_process = filtered[:5] # Max 5 garments
            
        if len(boxes_to_process) == 0:
            print("Grounding DINO did not find specific items, using center fallback")
            box = [width * 0.1, height * 0.1, width * 0.9, height * 0.9]
            boxes_to_process = [(box, "clothing", 1.0)]

        garments_response = []
        
        for idx, (box, label, score) in enumerate(boxes_to_process):
            print(f"\n--- Processing Garment {idx+1}/{len(boxes_to_process)} (label: {label}, score: {score:.3f}) ---")
            
            # 2. SAM: Generate segmentation mask from bounding box
            print("Running SAM...")
            sam_inputs = sam_processor(image, input_boxes=[[box]], return_tensors="pt")
            for k, v in sam_inputs.items():
                if torch.is_tensor(v) and v.dtype == torch.float64:
                    sam_inputs[k] = v.to(torch.float32)
            sam_inputs = sam_inputs.to(device)
            with torch.no_grad():
                sam_outputs = sam_model(**sam_inputs)
            
            masks = sam_processor.image_processor.post_process_masks(
                sam_outputs.pred_masks.cpu(),
                sam_inputs.original_sizes.cpu(),
                sam_inputs.reshaped_input_sizes.cpu()
            )[0]
            
            binary_mask = masks[0][0].numpy()
            
            # 3. CROP GARMENT: Extract item to transparent PNG
            print("Cropping garment...")
            image_np = np.array(image)
            rgba_image = np.zeros((height, width, 4), dtype=np.uint8)
            rgba_image[:, :, :3] = image_np
            rgba_image[:, :, 3] = (binary_mask * 255).astype(np.uint8)
            
            xmin, ymin, xmax, ymax = [int(coord) for coord in box]
            xmin = max(0, xmin)
            ymin = max(0, ymin)
            xmax = min(width, xmax)
            ymax = min(height, ymax)
            
            cropped_rgba = Image.fromarray(rgba_image).crop((xmin, ymin, xmax, ymax))
            
            max_size = 1024
            if max(cropped_rgba.size) > max_size:
                resample_filter = Image.Resampling.LANCZOS if hasattr(Image, 'Resampling') else Image.ANTIALIAS
                cropped_rgba.thumbnail((max_size, max_size), resample_filter)
            
            buffered = io.BytesIO()
            cropped_rgba.save(buffered, format="PNG")
            img_str = base64.b64encode(buffered.getvalue()).decode("utf-8")
            
            # 4. FASHIONCLIP: Extract embedding
            print("Running CLIP image encoder...")
            cropped_rgb_crop = image.crop((xmin, ymin, xmax, ymax))
            clip_inputs = clip_processor(images=cropped_rgb_crop, return_tensors="pt").to(device)
            with torch.no_grad():
                image_features = clip_model.get_image_features(**clip_inputs)
                image_features = image_features / image_features.norm(p=2, dim=-1, keepdim=True)
                embedding = image_features[0].cpu().numpy().tolist()
                
            # 5. FLORENCE-2
            print("Running Florence-2 Captioning...")
            prompt = "<MORE_DETAILED_CAPTION>"
            florence_inputs = florence_processor(text=prompt, images=cropped_rgb_crop, return_tensors="pt").to(device)
            with torch.no_grad():
                generated_ids = florence_model.generate(
                    input_ids=florence_inputs["input_ids"],
                    pixel_values=florence_inputs["pixel_values"],
                    max_new_tokens=1024,
                    num_beams=3
                )
            florence_description = florence_processor.batch_decode(generated_ids, skip_special_tokens=True)[0]
            print(f"Florence-2 output: {florence_description}")
            
            # Zero-Shot CLIP classification
            def zero_shot_classify(image_crop, candidate_labels: List[str]) -> str:
                inputs = clip_processor(text=candidate_labels, images=image_crop, return_tensors="pt", padding=True).to(device)
                with torch.no_grad():
                    outputs = clip_model(**inputs)
                logits_per_image = outputs.logits_per_image
                probs = logits_per_image.softmax(dim=-1)[0].cpu().numpy()
                best_idx = np.argmax(probs)
                return candidate_labels[best_idx]
                
            categories = ["Top", "Bottom", "Outerwear", "Shoes"]
            category = zero_shot_classify(cropped_rgb_crop, categories)
            
            sub_map = {
                "Top": ["Oxford Shirt", "Silk Blouse", "T-Shirt", "Camisole", "Knit Sweater"],
                "Bottom": ["Pleated Trousers", "Selvedge Jeans", "Chino Pants", "Midi Skirt", "Denim Shorts"],
                "Outerwear": ["Linen Blazer", "Trench Coat", "Bomber Jacket", "Wool Overcoat", "Denim Jacket"],
                "Shoes": ["Leather Loafers", "Canvas Sneakers", "Running Shoes", "Leather Boots", "Suede Derbies"]
            }
            subcategory = zero_shot_classify(cropped_rgb_crop, sub_map[category])
            
            materials = ["Organic Cotton", "Morus Silk", "Belgian Linen", "Linen Wool", "Japanese Denim", "Calfskin Leather", "Gabardine"]
            material = zero_shot_classify(cropped_rgb_crop, materials)
            
            fits = ["Regular", "Slim", "Relaxed", "Tailored", "Fluid", "Classic"]
            fit = zero_shot_classify(cropped_rgb_crop, fits)
            
            colors = ["Ivory White", "Midnight Black", "Classic Gray", "Crimson Red", "Forest Green", "Ocean Blue", "Sun Yellow", "Terracotta Orange", "Chestnut Brown", "Royal Purple", "Sage Olive", "Desert Khaki"]
            color_name = zero_shot_classify(cropped_rgb_crop, colors)
            
            cropped_mask = binary_mask[ymin:ymax, xmin:xmax]
            cropped_img_np = image_np[ymin:ymax, xmin:xmax]
            if cropped_mask.any():
                avg_color = cropped_img_np[cropped_mask].mean(axis=0)
                avg_rgb = [int(c) for c in avg_color]
            else:
                avg_rgb = [128, 128, 128]
                
            lab_color = rgb_to_lab(avg_rgb)
            
            price_map = {
                "T-Shirt": 45.0, "Oxford Shirt": 125.0, "Silk Blouse": 240.0,
                "Pleated Trousers": 180.0, "Selvedge Jeans": 260.0,
                "Linen Blazer": 320.0, "Trench Coat": 850.0,
                "Leather Loafers": 175.0, "Canvas Sneakers": 290.0
            }
            price = price_map.get(subcategory, 150.0)
            
            formality_map = {
                "T-Shirt": 0.1, "Canvas Sneakers": 0.1, "Selvedge Jeans": 0.25,
                "Oxford Shirt": 0.6, "Pleated Trousers": 0.7, "Leather Loafers": 0.75,
                "Linen Blazer": 0.75, "Silk Blouse": 0.8, "Trench Coat": 0.85
            }
            formality_score = formality_map.get(subcategory, 0.5)
            
            seasons = ["Spring", "Summer", "Autumn"] if category != "Outerwear" else ["Autumn", "Winter", "Spring"]
            
            garment_data = {
                "image_base64": img_str,
                "attributes": {
                    "category": category,
                    "subcategory": subcategory,
                    "colorName": color_name,
                    "labColor": lab_color,
                    "material": material,
                    "pattern": "Plain",
                    "fit": fit,
                    "seasons": seasons,
                    "formalityScore": formality_score,
                    "silhouette": subcategory.split()[-1],
                    "brand": "Inferred",
                    "price": price,
                    "embedding": embedding
                }
            }
            garments_response.append(garment_data)
            
        print(f"Digitization complete! Detected {len(garments_response)} garments.")
        return {"garments": garments_response}
        
    except Exception as e:
        print(f"Pipeline error: {str(e)}")
        import traceback
        traceback.print_exc()
        raise HTTPException(status_code=500, detail=f"Inference error: {str(e)}")

def get_local_ip():
    import socket
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        s.connect(('10.254.254.254', 1))
        ip = s.getsockname()[0]
    except Exception:
        ip = '127.0.0.1'
    finally:
        s.close()
    return ip

if __name__ == "__main__":
    import uvicorn
    local_ip = get_local_ip()
    print("\n" + "="*70)
    print(" 🚀 ClosetOS Digitization Pipeline Backend")
    print(f" 📍 Local Network LAN URL:   http://{local_ip}:8000")
    print(f" 📍 Localhost Access URL:    http://127.0.0.1:8000")
    print(f" Connect your mobile app to this LAN URL/IP to digitize items!")
    print("="*70 + "\n")
    uvicorn.run(app, host="0.0.0.0", port=8000)
