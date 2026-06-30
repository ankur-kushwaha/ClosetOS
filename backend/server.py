import os
import io
import base64
import numpy as np
from PIL import Image
import torch
import torchvision.transforms as T
from fastapi import FastAPI, UploadFile, File, HTTPException, BackgroundTasks
from pydantic import BaseModel
from typing import List, Dict, Any, Optional
import cv2
import uuid
import time
import json
import shutil
import asyncio
from concurrent.futures import ThreadPoolExecutor
from diffusers import StableDiffusionImg2ImgPipeline

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

# Custom Database Manager
from database import DatabaseManager

app = FastAPI(title="WardrobeOS Digitization Pipeline Backend")
db_manager = DatabaseManager()

# Thread Executor for running heavy ML tasks in background
executor = ThreadPoolExecutor(max_workers=2)

# Global model pointers
dino_processor = None
dino_model = None
sam_model = None
sam_processor = None
clip_model = None
clip_processor = None
florence_model = None
florence_processor = None
sd_pipe = None
rembg_session = None

# Active object detection configuration: "florence" or "dino"
DETECTION_MODEL = "dino"
print(f"Active object detection model: {DETECTION_MODEL}")

# Detect device
device = "cuda" if torch.cuda.is_available() else "cpu"
if device == "cpu" and torch.backends.mps.is_available():
    device = "mps"
print(f"Using ML acceleration device: {device}")

# In-memory jobs tracking
jobs = {}

STORAGE_DIR = "storage"
GARMENTS_DIR = os.path.join(STORAGE_DIR, "garments")
os.makedirs(GARMENTS_DIR, exist_ok=True)

# Helper: Lazy load model functions to prevent startup timeout
def get_sam_model():
    global sam_model, sam_processor
    if sam_model is None:
        print("Loading SAM (Segment Anything)...")
        sam_model = SamModel.from_pretrained("facebook/sam-vit-base").to(device)
        sam_processor = SamProcessor.from_pretrained("facebook/sam-vit-base")
    return sam_model, sam_processor

def get_clip_model():
    global clip_model, clip_processor
    if clip_model is None:
        print("Loading CLIP (FashionCLIP equivalent)...")
        clip_model = CLIPModel.from_pretrained("openai/clip-vit-base-patch32").to(device)
        clip_processor = CLIPProcessor.from_pretrained("openai/clip-vit-base-patch32")
    return clip_model, clip_processor

def get_sd_pipeline():
    global sd_pipe
    if sd_pipe is None:
        print("Loading Stable Diffusion Image-to-Image pipeline...")
        sd_model_id = "runwayml/stable-diffusion-v1-5"
        sd_dtype = torch.float16 if device in ["cuda", "mps"] else torch.float32
        sd_pipe = StableDiffusionImg2ImgPipeline.from_pretrained(
            sd_model_id, 
            torch_dtype=sd_dtype,
            safety_checker=None
        ).to(device)
        if device == "mps":
            sd_pipe.enable_attention_slicing()
    return sd_pipe

def get_florence_model():
    global florence_model, florence_processor
    if florence_model is None:
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
    return florence_model, florence_processor

def get_dino_model():
    global dino_processor, dino_model
    if dino_model is None:
        print("Loading Grounding DINO...")
        dino_processor = AutoProcessor.from_pretrained("IDEA-Research/grounding-dino-tiny")
        dino_model = AutoModelForZeroShotObjectDetection.from_pretrained("IDEA-Research/grounding-dino-tiny").to(device)
    return dino_processor, dino_model

def get_rembg_session():
    global rembg_session
    if rembg_session is None:
        try:
            import rembg
            print("Initializing rembg cloth session...")
            rembg_session = rembg.new_session("u2net_cloth_seg")
        except Exception as e:
            print(f"Error loading rembg session: {e}")
    return rembg_session

# Helper: Convert RGB to L*a*b* color spaces
def rgb_to_lab(rgb_color):
    r, g, b = [c / 255.0 for c in rgb_color]
    l = (0.2126 * r + 0.7152 * g + 0.0722 * b) * 100
    a = (r - g) * 100
    bb = (g - b) * 100
    return [float(l), float(a), float(bb)]

def calculate_iou(box1, box2):
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

# Map internal categories to try-on models routing categories
def map_to_try_on_category(ui_category):
    mapping = {
        "Top": "upper_body",
        "Bottom": "lower_body",
        "Outerwear": "upper_body",
        "Shoes": "footwear"
    }
    return mapping.get(ui_category, "accessory")

# Map subcategory to general try-on labels
def map_to_try_on_subcategory(ui_subcategory):
    sub = ui_subcategory.lower()
    if "shirt" in sub:
        return "shirt"
    elif "blouse" in sub:
        return "blouse"
    elif "t-shirt" in sub:
        return "t-shirt"
    elif "sweater" in sub:
        return "sweater"
    elif "jacket" in sub or "blazer" in sub:
        return "jacket"
    elif "coat" in sub:
        return "coat"
    elif "trousers" in sub or "pants" in sub or "chino" in sub:
        return "pants"
    elif "jeans" in sub:
        return "jeans"
    elif "skirt" in sub:
        return "skirt"
    elif "shorts" in sub:
        return "shorts"
    elif "sneakers" in sub:
        return "sneakers"
    elif "loafers" in sub or "boots" in sub or "shoes" in sub or "derbies" in sub:
        return "shoes"
    else:
        return "garment"

# High-resolution upscaling async wrapper
def run_upscale_async(input_path: str, output_path: str):
    try:
        from realesrgan import RealESRGANer
        print("Upscaling image with RealESRGAN...")
        model = RealESRGANer(scale=4, model_path="RealESRGAN_x4plus.pth", gpu_id=None)
        img = cv2.imread(input_path, cv2.IMREAD_UNCHANGED)
        output, _ = model.enhance(img, outscale=4)
        cv2.imwrite(output_path, output)
        print(f"Upscaling complete! Saved to {output_path}")
    except Exception as e:
        print(f"RealESRGAN failed, falling back to high-quality PIL Lanczos upscale: {e}")
        try:
            img = Image.open(input_path)
            w, h = img.size
            img_resized = img.resize((w * 4, h * 4), Image.Resampling.LANCZOS)
            img_resized.save(output_path)
            print(f"PIL upscaled image saved to {output_path}")
        except Exception as ex:
            print(f"Failed fallback upscaling: {ex}")

# Job updates helper
def update_job_step(job_id: str, step_index: int, status: str, progress: float, label: str = None, elapsed: float = 0.0):
    if job_id not in jobs:
        return
    job = jobs[job_id]
    step = job["steps"][step_index]
    step["status"] = status
    step["progress"] = progress
    if label:
        step["label"] = label
    if status == "running" and step["started_at"] is None:
        step["started_at"] = time.time()
    elif status in ["completed", "failed"]:
        step["completed_at"] = time.time()
        if step["started_at"]:
            step["elapsed"] = round(step["completed_at"] - step["started_at"], 2)
    
    # Recalculate overall progress
    total_progress = sum(s["progress"] for s in job["steps"]) / len(job["steps"])
    job["progress"] = round(total_progress, 2)
    job["current_step"] = step["name"]

def filter_and_nms_garments(detected_items, width, height):
    # 1. Spatial filtering
    filtered_spatially = []
    for item in detected_items:
        box = item[0]
        box_w = box[2] - box[0]
        gcx = (box[0] + box[2]) / 2
        
        # Filter out giant background boxes (covering > 70% width)
        if box_w > width * 0.70:
            continue
        # Filter out edge boxes (must be in middle 60% of width)
        if not (width * 0.20 <= gcx <= width * 0.80):
            continue
            
        filtered_spatially.append(item)

    # Helper category checks
    def get_cat_group(lbl: str) -> str:
        lbl_l = lbl.lower()
        if any(w in lbl_l for w in ["pants", "shorts", "jeans", "skirt", "trousers"]):
            return "bottom"
        if any(w in lbl_l for w in ["top", "shirt", "blouse", "sweater", "t-shirt", "jacket", "coat", "cardigan", "suit"]):
            return "top"
        return "other"

    # 2. Sort and run containment-enabled NMS
    filtered_spatially.sort(key=lambda x: x[2], reverse=True)
    nms_results = []
    for item in filtered_spatially:
        box = item[0]
        label = item[1].lower()
        cat = get_cat_group(label)
        overlap = False
        for accepted in nms_results:
            ax1, ay1, ax2, ay2 = accepted[0]
            gx1, gy1, gx2, gy2 = box
            
            inter_x1 = max(ax1, gx1)
            inter_y1 = max(ay1, gy1)
            inter_x2 = min(ax2, gx2)
            inter_y2 = min(ay2, gy2)
            inter_area = max(0, inter_x2 - inter_x1) * max(0, inter_y2 - inter_y1)
            
            a_area = (ax2 - ax1) * (ay2 - ay1)
            g_area = (gx2 - gx1) * (gy2 - gy1)
            
            iou = inter_area / (a_area + g_area - inter_area) if (a_area + g_area - inter_area) > 0 else 0
            containment = inter_area / min(a_area, g_area) if min(a_area, g_area) > 0 else 0
            
            accepted_cat = get_cat_group(accepted[1])
            if iou > 0.85:
                overlap = True
                break
            elif iou > 0.35 and accepted_cat == cat and cat != "other":
                overlap = True
                break
            elif containment > 0.70 and accepted_cat == cat and cat != "other":
                overlap = True
                break
        if not overlap:
            nms_results.append(item)
    return nms_results[:5]

# Core digitization execution pipeline (runs in background)
def run_digitize_pipeline(job_id: str, image_bytes: bytes):
    try:
        t_start = time.time()
        
        # Step 1: PRE_FLIGHT
        update_job_step(job_id, 0, "running", 0.3)
        image = Image.open(io.BytesIO(image_bytes)).convert("RGB")
        width, height = image.size
        
        # Save source image in a temp job directory
        job_dir = os.path.join(STORAGE_DIR, "jobs", job_id)
        os.makedirs(job_dir, exist_ok=True)
        source_img_path = os.path.join(job_dir, "source_image.jpg")
        image.save(source_img_path)
        update_job_step(job_id, 0, "completed", 1.0, f"Loaded input photo ({width}x{height})")

        # Step 2: GARMENT_DETECTION
        update_job_step(job_id, 1, "running", 0.5)
        boxes_to_process = []
        
        if DETECTION_MODEL == "florence":
            fl_model, fl_processor = get_florence_model()
            task_prompt = "<CAPTION_TO_PHRASE_GROUNDING>"
            garment_prompt = "dress . shirt . jacket . pants . shoes . coat . skirt . top . blouse . shorts . jeans . t-shirt . sweater"
            
            florence_inputs = fl_processor(text=task_prompt + garment_prompt, images=image, return_tensors="pt").to(device)
            with torch.no_grad():
                generated_ids = fl_model.generate(
                    input_ids=florence_inputs["input_ids"],
                    pixel_values=florence_inputs["pixel_values"],
                    max_new_tokens=1024,
                    num_beams=3
                )
            generated_text = fl_processor.batch_decode(generated_ids, skip_special_tokens=True)[0]
            od_results = fl_processor.post_process_generation(generated_text, task=task_prompt, image_size=image.size)
            parsed_od = od_results.get(task_prompt, {"bboxes": [], "labels": []})
            bboxes = parsed_od.get("bboxes", [])
            labels = parsed_od.get("labels", [])
            
            if len(bboxes) == 0:
                print("Florence-2 phrase grounding failed, falling back to general <OD>...")
                task_prompt = "<OD>"
                florence_inputs = fl_processor(text=task_prompt, images=image, return_tensors="pt").to(device)
                with torch.no_grad():
                    generated_ids = fl_model.generate(
                        input_ids=florence_inputs["input_ids"],
                        pixel_values=florence_inputs["pixel_values"],
                        max_new_tokens=1024,
                        num_beams=3
                    )
                generated_text = fl_processor.batch_decode(generated_ids, skip_special_tokens=True)[0]
                od_results = fl_processor.post_process_generation(generated_text, task=task_prompt, image_size=image.size)
                parsed_od = od_results.get(task_prompt, {"bboxes": [], "labels": []})
                bboxes = parsed_od.get("bboxes", [])
                labels = parsed_od.get("labels", [])
                
            clothing_words = ["dress", "shirt", "jacket", "pants", "shoes", "coat", "skirt", "top", "blouse", "shorts", "jeans", "t-shirt", "suit", "cardigan", "sweater", "trousers", "sock"]
            detected = []
            for box, label in zip(bboxes, labels):
                lbl = label.lower()
                is_clothing = any(word in lbl for word in clothing_words)
                if is_clothing or len(bboxes) <= 3:
                    detected.append((box, label, 1.0))
            
            boxes_to_process = filter_and_nms_garments(detected, width, height)
        else:
            # Dino detection fallback
            d_proc, d_mod = get_dino_model()
            garment_prompt = "dress . shirt . jacket . pants . shoes . coat . skirt . top . blouse . shorts . jeans . t-shirt . sweater"
            inputs = d_proc(images=image, text=garment_prompt, return_tensors="pt").to(device)
            with torch.no_grad():
                outputs = d_mod(**inputs)
            
            results = d_proc.post_process_grounded_object_detection(
                outputs, inputs.input_ids, box_threshold=0.10, text_threshold=0.10, target_sizes=[image.size[::-1]]
            )[0]
            
            if len(results["boxes"]) > 0:
                scores = results["scores"].cpu().numpy()
                detected = []
                for idx, score in enumerate(scores):
                    if score >= 0.10:
                        box = results["boxes"][idx].cpu().numpy().tolist()
                        label = results["labels"][idx]
                        detected.append((box, label, score))
                
                boxes_to_process = filter_and_nms_garments(detected, width, height)
        
        if len(boxes_to_process) == 0:
            print("No garments detected. Creating a full center bbox fallback.")
            box = [width * 0.1, height * 0.1, width * 0.9, height * 0.9]
            boxes_to_process = [(box, "clothing", 0.5)]

        update_job_step(job_id, 1, "completed", 1.0, f"Detected {len(boxes_to_process)} clothing items")
        num_garments = len(boxes_to_process)
        garment_results = []

        # Process each garment through steps 3-9
        for g_idx, (box, label, score) in enumerate(boxes_to_process):
            g_id = str(uuid.uuid4())
            g_dir = os.path.join(GARMENTS_DIR, g_id)
            os.makedirs(g_dir, exist_ok=True)
            
            x1, y1, x2, y2 = [int(coord) for coord in box]
            x1, y1 = max(0, x1), max(0, y1)
            x2, y2 = min(width, x2), min(height, y2)
            
            # Step 3: SEGMENTATION
            update_job_step(job_id, 2, "running", 0.5, f"Segmenting item {g_idx+1}/{num_garments} ({label})")
            sam_m, sam_p = get_sam_model()
            sam_inputs = sam_p(image, input_boxes=[[box]], return_tensors="pt")
            for k, v in sam_inputs.items():
                if torch.is_tensor(v) and v.dtype == torch.float64:
                    sam_inputs[k] = v.to(torch.float32)
            sam_inputs = sam_inputs.to(device)
            with torch.no_grad():
                sam_outputs = sam_m(**sam_inputs)
            masks = sam_p.image_processor.post_process_masks(
                sam_outputs.pred_masks.cpu(), sam_inputs.original_sizes.cpu(), sam_inputs.reshaped_input_sizes.cpu()
            )[0]
            binary_mask = masks[0][0].numpy()
            update_job_step(job_id, 2, "completed", 1.0, f"Segmented item {g_idx+1}/{num_garments}")

            # Step 4: MASK_CLEANUP
            update_job_step(job_id, 3, "running", 0.5, f"Cleaning mask for item {g_idx+1}")
            mask_u8 = (binary_mask * 255).astype(np.uint8)
            kernel = cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (5, 5))
            cleaned_mask = cv2.morphologyEx(mask_u8, cv2.MORPH_CLOSE, kernel)
            cleaned_mask = cv2.morphologyEx(cleaned_mask, cv2.MORPH_OPEN, kernel)
            cleaned_mask = cv2.GaussianBlur(cleaned_mask, (3, 3), 0)
            update_job_step(job_id, 3, "completed", 1.0, f"Cleaned mask for item {g_idx+1}")

            # Step 5: BACKGROUND_REMOVAL (rembg u2net_cloth_seg on white bg crop)
            update_job_step(job_id, 4, "running", 0.5, f"Removing background for item {g_idx+1}")
            
            # Crop bbox from original image
            crop_img = image.crop((x1, y1, x2, y2))
            crop_mask = Image.fromarray(cleaned_mask).crop((x1, y1, x2, y2))
            
            # Apply cleaned mask to crop alpha
            crop_rgba = crop_img.convert("RGBA")
            crop_rgba.putalpha(crop_mask)
            
            # Composite onto white background before running rembg for better edges
            white_bg_crop = Image.new("RGBA", crop_rgba.size, (255, 255, 255, 255))
            white_bg_crop.paste(crop_rgba, mask=crop_rgba.split()[3])
            
            # Apply rembg
            rem_session = get_rembg_session()
            if rem_session is not None:
                try:
                    import rembg
                    masked_raw = rembg.remove(white_bg_crop, session=rem_session, alpha_matting=True)
                    
                    # u2net_cloth_seg returns 3 vertically concatenated masks (upper, lower, outerwear)
                    if masked_raw.height == 3 * white_bg_crop.height:
                        H = white_bg_crop.height
                        W = white_bg_crop.width
                        lbl_l = label.lower()
                        if any(w in lbl_l for w in ["pants", "shorts", "jeans", "skirt"]):
                            masked = masked_raw.crop((0, H, W, 2 * H))
                        elif any(w in lbl_l for w in ["jacket", "coat", "cardigan", "suit", "outerwear"]):
                            masked = masked_raw.crop((0, 2 * H, W, 3 * H))
                        else:
                            # Default is upper body (first segment)
                            masked = masked_raw.crop((0, 0, W, H))
                    else:
                        masked = masked_raw
                except Exception as e:
                    print(f"Rembg failed, using fallback cropped mask: {e}")
                    masked = crop_rgba
            else:
                masked = crop_rgba
            
            # Fill internal mask holes to prevent shredded clothing artifacts (e.g. from railing posts or glare)
            alpha_np = np.array(masked.split()[-1])
            contours, _ = cv2.findContours(alpha_np, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
            solid_alpha = np.zeros_like(alpha_np)
            cv2.drawContours(solid_alpha, contours, -1, 255, thickness=-1)
            # Smooth the filled alpha channel slightly
            solid_alpha = cv2.GaussianBlur(solid_alpha, (3, 3), 0)
            masked.putalpha(Image.fromarray(solid_alpha))
            
            # Crop tightly to the non-zero pixels of the mask to eliminate excessive white space
            alpha = np.array(masked.split()[-1]) # alpha channel
            y_indices, x_indices = np.where(alpha > 0)
            if len(y_indices) > 0:
                ymin_mask, xmin_mask = y_indices.min(), x_indices.min()
                ymax_mask, xmax_mask = y_indices.max(), x_indices.max()
                
                # Add 4px padding for nicer edges
                padding = 4
                ymin_mask = max(0, ymin_mask - padding)
                xmin_mask = max(0, xmin_mask - padding)
                ymax_mask = min(masked.height, ymax_mask + padding)
                xmax_mask = min(masked.width, xmax_mask + padding)
                
                masked = masked.crop((xmin_mask, ymin_mask, xmax_mask, ymax_mask))
                # Adjust original crop to match the tight bounds
                original_crop = crop_img.crop((xmin_mask, ymin_mask, xmax_mask, ymax_mask))
                
                # Update box and x1, y1, x2, y2 coordinates to match tighter bounds relative to source image
                new_x1 = x1 + xmin_mask
                new_y1 = y1 + ymin_mask
                new_x2 = x1 + xmax_mask
                new_y2 = y1 + ymax_mask
                box = [new_x1, new_y1, new_x2, new_y2]
                x1, y1, x2, y2 = new_x1, new_y1, new_x2, new_y2
            else:
                original_crop = crop_img
            
            # Save masked.png
            masked_path = os.path.join(g_dir, "masked.png")
            masked.save(masked_path, "PNG")
            update_job_step(job_id, 4, "completed", 1.0, f"Background removed for item {g_idx+1}")
 
            # Step 6: WHITE_BG_COMPOSITE
            update_job_step(job_id, 5, "running", 0.5, f"Compositing white background for item {g_idx+1}")
            background = Image.new("RGBA", masked.size, (255, 255, 255, 255))
            white_bg = Image.alpha_composite(background, masked).convert("RGB")
            white_bg_path = os.path.join(g_dir, "white_bg.png")
            white_bg.save(white_bg_path, "PNG")
            update_job_step(job_id, 5, "completed", 1.0, f"White composite saved for item {g_idx+1}")
 
            # Step 7: ORIGINAL_CROP
            update_job_step(job_id, 6, "running", 0.5, f"Saving original crop for item {g_idx+1}")
            orig_path = os.path.join(g_dir, "original_crop.png")
            original_crop.save(orig_path, "PNG")
            update_job_step(job_id, 6, "completed", 1.0, f"Original crop saved for item {g_idx+1}")

            # Step 8: THUMBNAIL
            update_job_step(job_id, 7, "running", 0.5, f"Generating thumbnail for item {g_idx+1}")
            thumb = white_bg.copy()
            thumb.thumbnail((256, 256), Image.Resampling.LANCZOS)
            canvas = Image.new("RGB", (256, 256), (255, 255, 255))
            offset = ((256 - thumb.width) // 2, (256 - thumb.height) // 2)
            canvas.paste(thumb, offset)
            thumb_path = os.path.join(g_dir, "thumbnail.jpg")
            canvas.save(thumb_path, "JPEG", quality=90)
            update_job_step(job_id, 7, "completed", 1.0, f"Thumbnail created for item {g_idx+1}")

            # Step 9: METADATA_EXTRACTION
            update_job_step(job_id, 8, "running", 0.5, f"Running FashionCLIP embedding and tagging for item {g_idx+1}")
            cl_model, cl_processor = get_clip_model()
            
            # Generate FashionCLIP embedding from white_bg
            clip_inputs = cl_processor(images=white_bg, return_tensors="pt").to(device)
            with torch.no_grad():
                image_features = cl_model.get_image_features(**clip_inputs)
                image_features = image_features / image_features.norm(p=2, dim=-1, keepdim=True)
                embedding = image_features[0].cpu().numpy().tolist()
            
            # Zero-shot classification
            def zero_shot_classify(img, labels_list: List[str]) -> str:
                inputs = cl_processor(text=labels_list, images=img, return_tensors="pt", padding=True).to(device)
                with torch.no_grad():
                    outputs = cl_model(**inputs)
                logits_per_image = outputs.logits_per_image
                probs = logits_per_image.softmax(dim=-1)[0].cpu().numpy()
                best_idx = np.argmax(probs)
                return labels_list[best_idx]
            
            # Zero-shot multi-color classifier (returns list of matching colors)
            def zero_shot_classify_colors(img) -> List[str]:
                color_labels = ["black", "white", "navy", "blue", "gray", "red", "green", "yellow", "orange", "brown", "pink", "purple", "olive", "khaki", "cream", "beige"]
                inputs = cl_processor(text=color_labels, images=img, return_tensors="pt", padding=True).to(device)
                with torch.no_grad():
                    outputs = cl_model(**inputs)
                logits_per_image = outputs.logits_per_image
                probs = logits_per_image.softmax(dim=-1)[0].cpu().numpy()
                
                # Retrieve all colors with probability > 0.15, or at least the top color
                detected_colors = []
                for c_idx, prob in enumerate(probs):
                    if prob > 0.15:
                        detected_colors.append(color_labels[c_idx])
                if len(detected_colors) == 0:
                    detected_colors.append(color_labels[np.argmax(probs)])
                return detected_colors

            categories = ["Top", "Bottom", "Outerwear", "Shoes"]
            category = zero_shot_classify(white_bg, categories)
            
            sub_map = {
                "Top": ["Oxford Shirt", "Silk Blouse", "T-Shirt", "Camisole", "Knit Sweater"],
                "Bottom": ["Pleated Trousers", "Selvedge Jeans", "Chino Pants", "Midi Skirt", "Denim Shorts"],
                "Outerwear": ["Linen Blazer", "Trench Coat", "Bomber Jacket", "Wool Overcoat", "Denim Jacket"],
                "Shoes": ["Leather Loafers", "Canvas Sneakers", "Running Shoes", "Leather Boots", "Suede Derbies"]
            }
            subcategory = zero_shot_classify(white_bg, sub_map[category])
            
            materials = ["Organic Cotton", "Morus Silk", "Belgian Linen", "Linen Wool", "Japanese Denim", "Calfskin Leather", "Gabardine"]
            material = zero_shot_classify(white_bg, materials)
            
            fits = ["Regular", "Slim", "Relaxed", "Tailored", "Fluid", "Classic"]
            fit = zero_shot_classify(white_bg, fits)
            
            patterns = ["solid", "striped", "floral", "plaid", "checkered", "graphic", "denim"]
            pattern = zero_shot_classify(white_bg, patterns)
            
            detected_colors = zero_shot_classify_colors(white_bg)
            
            # Map try-on schema variables
            try_on_cat = map_to_try_on_category(category)
            try_on_subcat = map_to_try_on_subcategory(subcategory)
            
            # Calculate pixel average RGB/Lab
            cropped_mask = binary_mask[y1:y2, x1:x2]
            cropped_img_np = np.array(image)[y1:y2, x1:x2]
            if cropped_mask.any():
                avg_color = cropped_img_np[cropped_mask].mean(axis=0)
                avg_rgb = [int(c) for c in avg_color]
            else:
                avg_rgb = [128, 128, 128]
            lab_color = rgb_to_lab(avg_rgb)
            
            # Default helper maps
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
            
            # Create try-on metadata.json structure
            metadata_dict = {
                "garment_id": g_id,
                "user_id": None,
                "category": try_on_cat,
                "subcategory": try_on_subcat,
                "color": detected_colors,
                "pattern": pattern,
                "clip_embedding": embedding,
                "bbox": [int(x1), int(y1), int(x2 - x1), int(y2 - y1)],
                "source_image_id": job_id,
                "extraction_confidence": float(score)
            }
            
            # Save metadata to DB
            db_manager.save_garment(metadata_dict)
            
            # Save metadata.json
            with open(os.path.join(g_dir, "metadata.json"), "w") as f:
                json.dump(metadata_dict, f, indent=2)
                
            # Prepare Base64 strings for mobile backwards compatibility
            buffered_masked = io.BytesIO()
            masked.save(buffered_masked, format="PNG")
            masked_b64 = base64.b64encode(buffered_masked.getvalue()).decode("utf-8")
            
            buffered_white = io.BytesIO()
            white_bg.save(buffered_white, format="PNG")
            white_b64 = base64.b64encode(buffered_white.getvalue()).decode("utf-8")
            
            # Construct backend garment response for UI
            ui_garment = {
                "garment_id": g_id,
                "image_base64": masked_b64,
                "straightened_image_base64": white_b64,
                "attributes": {
                    "category": category,
                    "subcategory": subcategory,
                    "colorName": detected_colors[0].capitalize() if detected_colors else "Unknown",
                    "labColor": lab_color,
                    "material": material,
                    "pattern": pattern,
                    "fit": fit,
                    "seasons": seasons,
                    "formalityScore": formality_score,
                    "silhouette": subcategory.split()[-1] if subcategory else "clothing",
                    "brand": "Inferred",
                    "price": price,
                    "embedding": embedding
                }
            }
            garment_results.append(ui_garment)
            update_job_step(job_id, 8, "completed", 1.0, f"FashionCLIP tagging complete for item {g_idx+1}")

            # Step 10: HIRES_UPSCALE (Triggered asynchronously)
            update_job_step(job_id, 9, "running", 0.1, f"Queued hi-res upscale for item {g_idx+1}")
            hires_path = os.path.join(g_dir, "hires.png")
            
            # Run upscaling in a non-blocking background thread
            executor.submit(run_upscale_async, white_bg_path, hires_path)
            update_job_step(job_id, 9, "completed", 1.0, f"Hi-res upscale queued for item {g_idx+1}")

        # Complete Job
        jobs[job_id]["status"] = "completed"
        jobs[job_id]["garments"] = garment_results
        print(f"Job {job_id} completed successfully in {time.time() - t_start:.2f} seconds!")

    except Exception as e:
        print(f"Pipeline error in job {job_id}: {e}")
        import traceback
        traceback.print_exc()
        if job_id in jobs:
            jobs[job_id]["status"] = "failed"
            jobs[job_id]["error"] = str(e)
            # Fail all incomplete steps
            for step in jobs[job_id]["steps"]:
                if step["status"] in ["pending", "running"]:
                    step["status"] = "failed"
                    step["completed_at"] = time.time()

# --- fastapi endpoint definitions ---

@app.get("/")
def read_root():
    return {
        "app": "ClosetOS Ingestion & Try-On Pipeline Backend",
        "device": device,
        "active_detector": DETECTION_MODEL,
        "database": db_manager.db_type,
        "storage": GARMENTS_DIR
    }

# 1. POST /digitize/start - Start async job
@app.post("/digitize/start")
async def start_digitization_job(file: UploadFile = File(...), background_tasks: BackgroundTasks = None):
    try:
        contents = await file.read()
        job_id = str(uuid.uuid4())
        
        # Initialize job steps
        job_steps = [
            {"name": "PRE_FLIGHT", "status": "pending", "progress": 0.0, "label": "Checking file integrity...", "started_at": None, "completed_at": None, "elapsed": 0.0},
            {"name": "GARMENT_DETECTION", "status": "pending", "progress": 0.0, "label": "Florence-2: Running object detection...", "started_at": None, "completed_at": None, "elapsed": 0.0},
            {"name": "SEGMENTATION", "status": "pending", "progress": 0.0, "label": "SAM: Segmenting detected regions...", "started_at": None, "completed_at": None, "elapsed": 0.0},
            {"name": "MASK_CLEANUP", "status": "pending", "progress": 0.0, "label": "OpenCV: Morphological mask cleanup...", "started_at": None, "completed_at": None, "elapsed": 0.0},
            {"name": "BACKGROUND_REMOVAL", "status": "pending", "progress": 0.0, "label": "Rembg: Isolating clothing edges...", "started_at": None, "completed_at": None, "elapsed": 0.0},
            {"name": "WHITE_BG_COMPOSITE", "status": "pending", "progress": 0.0, "label": "PIL: Compositing on white canvas...", "started_at": None, "completed_at": None, "elapsed": 0.0},
            {"name": "ORIGINAL_CROP", "status": "pending", "progress": 0.0, "label": "PIL: Saving original bounding box crop...", "started_at": None, "completed_at": None, "elapsed": 0.0},
            {"name": "THUMBNAIL", "status": "pending", "progress": 0.0, "label": "PIL: Generating 256x256 thumbnail...", "started_at": None, "completed_at": None, "elapsed": 0.0},
            {"name": "METADATA_EXTRACTION", "status": "pending", "progress": 0.0, "label": "FashionCLIP: Attribute zero-shot tagging...", "started_at": None, "completed_at": None, "elapsed": 0.0},
            {"name": "HIRES_UPSCALE", "status": "pending", "progress": 0.0, "label": "RealESRGAN: Launching async upscaler...", "started_at": None, "completed_at": None, "elapsed": 0.0}
        ]
        
        jobs[job_id] = {
            "job_id": job_id,
            "status": "processing",
            "progress": 0.0,
            "current_step": "PRE_FLIGHT",
            "steps": job_steps,
            "garments": [],
            "error": None
        }
        
        # Dispatch background thread pipeline execution
        background_tasks.add_task(run_digitize_pipeline, job_id, contents)
        
        return {"job_id": job_id, "status": "processing"}
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Failed to submit digitization job: {str(e)}")

# 2. GET /digitize/jobs/{job_id} - Poll job progress
@app.get("/digitize/jobs/{job_id}")
def get_digitization_job_status(job_id: str):
    if job_id not in jobs:
        raise HTTPException(status_code=404, detail="Job not found")
    return jobs[job_id]

# 3. POST /digitize - Blocking legacy compatibility endpoint
@app.post("/digitize")
async def digitize(file: UploadFile = File(...), background_tasks: BackgroundTasks = None):
    try:
        # Submit the job
        res = await start_digitization_job(file, background_tasks)
        job_id = res["job_id"]
        
        # Block and poll until complete or failed
        timeout = 90.0 # 90 seconds max blocking wait
        start_wait = time.time()
        while time.time() - start_wait < timeout:
            job = jobs.get(job_id)
            if not job:
                raise HTTPException(status_code=500, detail="Job state lost")
            if job["status"] == "completed":
                # Reshape structure to match what the old API expected
                # Old API returned: {"garments": [ {image_base64: "...", straightened_image_base64: "...", attributes: {...}} ]}
                # And since we stored exactly that inside job["garments"], we can just return it!
                return {"garments": job["garments"]}
            elif job["status"] == "failed":
                raise HTTPException(status_code=500, detail=f"Inference pipeline failed: {job.get('error')}")
            await asyncio.sleep(0.5)
            
        raise HTTPException(status_code=504, detail="Ingestion pipeline request timed out on server.")
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Blocking ingestion failed: {str(e)}")

# --- Individual Step APIs for Debugging and granular timelines ---

@app.post("/digitize/detect")
async def step_detect(file: UploadFile = File(...)):
    try:
        contents = await file.read()
        image = Image.open(io.BytesIO(contents)).convert("RGB")
        fl_model, fl_processor = get_florence_model()
        task_prompt = "<CAPTION_TO_PHRASE_GROUNDING>"
        garment_prompt = "clothing . garment . dress . shirt . jacket . pants . shoes . coat . skirt . top . blouse . shorts . jeans . t-shirt . sweater"
        
        florence_inputs = fl_processor(text=task_prompt + garment_prompt, images=image, return_tensors="pt").to(device)
        with torch.no_grad():
            generated_ids = fl_model.generate(
                input_ids=florence_inputs["input_ids"],
                pixel_values=florence_inputs["pixel_values"],
                max_new_tokens=1024,
                num_beams=3
            )
        generated_text = fl_processor.batch_decode(generated_ids, skip_special_tokens=True)[0]
        od_results = fl_processor.post_process_generation(generated_text, task=task_prompt, image_size=image.size)
        parsed_od = od_results.get(task_prompt, {"bboxes": [], "labels": []})
        return parsed_od
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@app.post("/digitize/segment")
async def step_segment(file: UploadFile = File(...), bbox: str = "[0,0,100,100]"):
    try:
        box = json.loads(bbox)
        contents = await file.read()
        image = Image.open(io.BytesIO(contents)).convert("RGB")
        sam_m, sam_p = get_sam_model()
        sam_inputs = sam_p(image, input_boxes=[[box]], return_tensors="pt")
        for k, v in sam_inputs.items():
            if torch.is_tensor(v) and v.dtype == torch.float64:
                sam_inputs[k] = v.to(torch.float32)
        sam_inputs = sam_inputs.to(device)
        with torch.no_grad():
            sam_outputs = sam_m(**sam_inputs)
        masks = sam_p.image_processor.post_process_masks(
            sam_outputs.pred_masks.cpu(), sam_inputs.original_sizes.cpu(), sam_inputs.reshaped_input_sizes.cpu()
        )[0]
        binary_mask = masks[0][0].numpy().astype(int).tolist()
        return {"mask": binary_mask}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@app.post("/digitize/remove-bg")
async def step_remove_bg(file: UploadFile = File(...)):
    try:
        contents = await file.read()
        image = Image.open(io.BytesIO(contents)).convert("RGBA")
        rem_session = get_rembg_session()
        if rem_session is None:
            raise HTTPException(status_code=500, detail="rembg not installed or loaded")
        import rembg
        masked = rembg.remove(image, session=rem_session, alpha_matting=True)
        buffered = io.BytesIO()
        masked.save(buffered, format="PNG")
        img_str = base64.b64encode(buffered.getvalue()).decode("utf-8")
        return {"image_base64": img_str}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@app.post("/digitize/upscale")
async def step_upscale(file: UploadFile = File(...)):
    try:
        contents = await file.read()
        temp_in = f"temp_in_{uuid.uuid4()}.png"
        temp_out = f"temp_out_{uuid.uuid4()}.png"
        with open(temp_in, "wb") as f:
            f.write(contents)
        run_upscale_async(temp_in, temp_out)
        
        # Read back
        upscaled_img = Image.open(temp_out)
        buffered = io.BytesIO()
        upscaled_img.save(buffered, format="PNG")
        img_str = base64.b64encode(buffered.getvalue()).decode("utf-8")
        
        # Clean up temp files
        if os.path.exists(temp_in): os.remove(temp_in)
        if os.path.exists(temp_out): os.remove(temp_out)
        
        return {"upscaled_image_base64": img_str}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

# Network discovery helper
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
    print(" 🚀 ClosetOS Digitization Pipeline Backend (Rebuilt Async)")
    print(f" 📍 Local Network LAN URL:   http://{local_ip}:8000")
    print(f" 📍 Localhost Access URL:    http://127.0.0.1:8000")
    print(f" Connect your mobile app to this LAN URL/IP to digitize items!")
    print("="*70 + "\n")
    uvicorn.run(app, host="0.0.0.0", port=8000)
