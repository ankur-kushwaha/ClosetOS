import os
import io
import json
import time
import uuid
import base64
import asyncio
from typing import List, Dict, Any, Optional
from concurrent.futures import ThreadPoolExecutor

import torch
import numpy as np
from PIL import Image, ImageOps
from fastapi import FastAPI, UploadFile, File, HTTPException, BackgroundTasks
from fastapi.responses import HTMLResponse
from fastapi.staticfiles import StaticFiles
from fastapi.middleware.cors import CORSMiddleware

# Database and pipeline imports
from database import DatabaseManager
from pipeline.config import (
    DETECTION_MODEL,
    GARMENTS_DIR,
    STORAGE_DIR,
    NORMALIZATION_PROVIDER,
    MIN_DETECTION_CONFIDENCE,
)
from pipeline.model_loaders import ModelLoaders
from pipeline.normalization import normalize_garment
from pipeline.florence_attrs import extract_attributes
from pipeline.fashion_clip import encode_image
from pipeline.yolo_world import detect_yolo_world
from pipeline.travel_capsule import generate_travel_capsule

app = FastAPI(title="WardrobeOS Ingestion & Try-On Pipeline Backend")

# Enable CORS for local testing
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Initialize Database Manager
db_manager = DatabaseManager()

# Background Thread Executor
executor = ThreadPoolExecutor(max_workers=2)

# Determine PyTorch device acceleration
device = "cuda" if torch.cuda.is_available() else "cpu"
if device == "cpu" and torch.backends.mps.is_available():
    device = "mps"
print(f"Using device: {device}")

# Lazy-loaded model loaders instance
loaders = ModelLoaders(device)

# In-memory jobs tracking status
jobs = {}

# Ensure Storage directories exist
os.makedirs(STORAGE_DIR, exist_ok=True)
os.makedirs(GARMENTS_DIR, exist_ok=True)

# Helper function to update job steps
def update_job_step(job_id: str, step_index: int, status: str, progress: float, label: Optional[str] = None):
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

# --- Endpoints ---

@app.get("/")
def read_root():
    return {
        "app": "ClosetOS Ingestion & Try-On Pipeline Backend",
        "device": device,
        "active_detector": DETECTION_MODEL,
        "normalization_provider": NORMALIZATION_PROVIDER,
        "database": db_manager.db_type,
        "storage": GARMENTS_DIR
    }

@app.get("/test", response_class=HTMLResponse)
def get_test_page():
    static_file_path = os.path.join(os.path.dirname(__file__), "static", "test.html")
    if os.path.exists(static_file_path):
        with open(static_file_path, "r") as f:
            return f.read()
    else:
        raise HTTPException(status_code=404, detail="test.html not found in static directory")

@app.get("/yolo-test", response_class=HTMLResponse)
def get_yolo_test_page():
    static_file_path = os.path.join(os.path.dirname(__file__), "static", "yolo_test.html")
    if os.path.exists(static_file_path):
        with open(static_file_path, "r") as f:
            return f.read()
    else:
        raise HTTPException(status_code=404, detail="yolo_test.html not found in static directory")

# YOLO-World custom endpoint
@app.post("/yolo-world/detect")
def yolo_world_detect(file: UploadFile = File(...), confidence: float = 0.15):
    try:
        contents = file.file.read()
        image = Image.open(io.BytesIO(contents)).convert("RGB")
        image = ImageOps.exif_transpose(image)
        
        # Save source image in temp storage
        source_id = str(uuid.uuid4())
        temp_dir = os.path.join(STORAGE_DIR, "temp_sources")
        os.makedirs(temp_dir, exist_ok=True)
        source_path = os.path.join(temp_dir, f"{source_id}.jpg")
        image.save(source_path, "JPEG", quality=95)
        
        detected_items = detect_yolo_world(image, device, loaders, confidence=confidence)
        bboxes = [item[0] for item in detected_items]
        labels = [item[1] for item in detected_items]
        scores = [item[2] for item in detected_items]
        # Extract cropped garments as base64
        crops_base64 = []
        for bbox in bboxes:
            x1, y1, x2, y2 = [int(coord) for coord in bbox]
            x1, y1 = max(0, x1), max(0, y1)
            x2, y2 = min(image.width, x2), min(image.height, y2)
            if x2 > x1 and y2 > y1:
                crop = image.crop((x1, y1, x2, y2))
                buffered = io.BytesIO()
                crop.save(buffered, format="PNG")
                img_str = base64.b64encode(buffered.getvalue()).decode("utf-8")
                crops_base64.append(img_str)
            else:
                crops_base64.append("")

        return {
            "source_image_id": source_id,
            "bboxes": bboxes,
            "labels": labels,
            "scores": scores,
            "width": image.width,
            "height": image.height,
            "crops_base64": crops_base64
        }
    except Exception as e:
        import traceback
        traceback.print_exc()
        raise HTTPException(status_code=500, detail=str(e))

from pydantic import BaseModel

class CropPayload(BaseModel):
    crop_base64: str
    label: str
    caption: Optional[str] = None

class FinalizePayload(BaseModel):
    image_base64: str
    crop_base64: Optional[str] = None
    label: str
    source_image_id: Optional[str] = None
    brand: Optional[str] = "Inferred"
    price: Optional[float] = 150.0

class FlorencePayload(BaseModel):
    image_base64: str
    task_prompt: Optional[str] = "<MORE_DETAILED_CAPTION>"


class TravelGarmentInput(BaseModel):
    id: str
    category: str
    subcategory: str
    colorName: str
    material: str = ""
    pattern: str = ""
    fit: str = ""
    seasons: List[str] = []
    formalityScore: float = 0.5
    laundryStatus: str = "CLEAN"
    wearCount: int = 0
    brand: str = "Unknown"


class TravelCapsuleRequest(BaseModel):
    destination: str
    trip_days: int
    temp_low_f: float
    temp_high_f: float
    weather_condition: str
    garments: List[TravelGarmentInput]
    preferred_styles: List[str] = []

def clean_garment_caption(raw_caption: str) -> str:
    import re
    # Split into sentences
    sentences = [s.strip() for s in raw_caption.split('.') if s.strip()]
    cleaned = []
    for s in sentences:
        s_lower = s.lower()
        # Ignore sentences about the person's face, looking at camera, body posture, etc.
        if any(w in s_lower for w in ["looking at", "expression", "body is", "standing", "hand in", "hands in", "smiling", "turned to", "pose", "posing", "mood of", "relaxed", "looking directly"]):
            continue
        # Replace "He is wearing a black shirt" -> "A black shirt"
        s = re.sub(r'^(he|she|the person|the model)\s+(is wearing|wears|is dressed in)\s+', 'A ', s, flags=re.IGNORECASE)
        cleaned.append(s)
    return '. '.join(cleaned) + '.' if cleaned else raw_caption

@app.post("/yolo-world/florence")
def run_florence(payload: FlorencePayload):
    try:
        import base64
        import io
        from PIL import Image
        from pipeline.florence_attrs import _run_florence_task
        
        # Decode base64 image
        img_bytes = base64.b64decode(payload.image_base64)
        image = Image.open(io.BytesIO(img_bytes)).convert("RGB")
        
        # Get Florence-2 model & processor
        fl_model, fl_processor = loaders.get_florence_model()
        
        device = loaders.device
        result = _run_florence_task(fl_model, fl_processor, image, payload.task_prompt, device)
        
        if payload.task_prompt in ("<MORE_DETAILED_CAPTION>", "<CAPTION>"):
            result = clean_garment_caption(result)
            
        return {"result": result}
    except Exception as e:
        import traceback
        traceback.print_exc()
        raise HTTPException(status_code=500, detail=str(e))

@app.post("/yolo-world/gpt-normalize")
def gpt_normalize_crop(payload: CropPayload):
    try:
        # Decode base64 crop image
        crop_bytes = base64.b64decode(payload.crop_base64)
        crop_img = Image.open(io.BytesIO(crop_bytes)).convert("RGBA")
        crop_label = payload.label
        
        # 3. Build custom prompt incorporating the structured caption
        custom_prompt = (
            "Create a professional e-commerce flat-lay product photograph of the garment itself, with NO human model, head, face, hands, arms, legs, skin, or mannequin. "
            f"The garment to isolate is: {payload.label}. "
            "Remove any human bodies and isolate ONLY the clothing item. Lay the clothing completely flat and straightened. "
            "Strictly preserve the exact neckline and collar shape (e.g., V-neck, round neck, polo collar, stand collar), "
            "fabric texture, patterns, colors, stitching, buttons, and proportions. Do not change the neckline type "
            "or modify the garment structure. Place the garment against a clean, solid studio white background "
            "with bright, neutral studio lighting. Do NOT generate any humans, models, body parts, or backgrounds other than pure white."
        )

        print(f"Payload label: {payload.label}")
     
        # Run normalization pipeline with the customized prompt
        normalized, provider = normalize_garment(crop_img, payload.label, custom_prompt=custom_prompt)
        
        # Save as base64 PNG
        buffered = io.BytesIO()
        normalized.save(buffered, format="PNG")
        img_str = base64.b64encode(buffered.getvalue()).decode("utf-8")
        
        return {"image_base64": img_str, "provider": provider}
    except Exception as e:
        import traceback
        traceback.print_exc()
        raise HTTPException(status_code=500, detail=str(e))

@app.post("/yolo-world/finalize")
def yolo_world_finalize(payload: FinalizePayload):
    try:
        import base64
        import io
        from PIL import Image
        import numpy as np
        
        from pipeline.florence_attrs import extract_attributes
        from pipeline.fashion_clip import encode_image
        from pipeline.orchestrator import map_to_try_on_category, map_to_try_on_subcategory, rgb_to_lab
        from pipeline.config import GARMENTS_DIR
        
        # Decode base64 image
        img_bytes = base64.b64decode(payload.image_base64)
        image = Image.open(io.BytesIO(img_bytes)).convert("RGBA")
        
        # 1. Run Florence-2 attribute extraction
        device = loaders.device
        attrs = extract_attributes(image.convert("RGB"), payload.label, device, loaders)
        
        # 2. Run FashionCLIP embedding
        embedding = encode_image(image, device)
        
        # 3. Create file assets on disk
        g_id = str(uuid.uuid4())
        g_dir = os.path.join(GARMENTS_DIR, g_id)
        os.makedirs(g_dir, exist_ok=True)
        
        normalized_path = os.path.join(g_dir, "normalized.png")
        image.save(normalized_path, "PNG")
        
        # Legacy compat white_bg.png
        white_bg_path = os.path.join(g_dir, "white_bg.png")
        image.save(white_bg_path, "PNG")
        
        # Save thumbnail
        thumb = image.copy()
        thumb.thumbnail((256, 256), Image.Resampling.LANCZOS)
        canvas = Image.new("RGB", (256, 256), (255, 255, 255))
        canvas.paste(thumb, ((256 - thumb.width) // 2, (256 - thumb.height) // 2))
        canvas.save(os.path.join(g_dir, "thumbnail.jpg"), "JPEG", quality=90)
        
        # Save actual crop if provided
        if payload.crop_base64:
            try:
                crop_bytes = base64.b64decode(payload.crop_base64)
                crop_img = Image.open(io.BytesIO(crop_bytes)).convert("RGBA")
                crop_img.save(os.path.join(g_dir, "crop.png"), "PNG")
            except Exception as e:
                print(f"Error saving crop.png: {str(e)}")
                
        # Copy original source image if available in temp storage
        if payload.source_image_id:
            try:
                from pipeline.config import STORAGE_DIR
                temp_path = os.path.join(STORAGE_DIR, "temp_sources", f"{payload.source_image_id}.jpg")
                if os.path.exists(temp_path):
                    import shutil
                    shutil.copy(temp_path, os.path.join(g_dir, "source_image.jpg"))
            except Exception as e:
                print(f"Error copying source_image.jpg: {str(e)}")
        
        # Average color extraction
        avg_rgb = [128, 128, 128]
        img_np = np.array(image)
        if img_np.size > 0:
            mask = img_np[:, :, 3] > 10 if img_np.shape[2] == 4 else np.ones(img_np.shape[:2], dtype=bool)
            if mask.any():
                avg_rgb = [int(c) for c in img_np[mask].mean(axis=0)[:3]]
        lab_color = rgb_to_lab(avg_rgb)
        
        category = attrs["category"]
        subcategory = attrs["subcategory"]
        detected_colors = attrs.get("colors", ["gray"])
        pattern = attrs["pattern"]
        material = attrs["material"]
        fit = attrs["fit"]
        
        # Map try_on categories/subcategories
        try_on_cat = map_to_try_on_category(category)
        try_on_subcat = map_to_try_on_subcategory(subcategory)
        
        metadata_dict = {
            "garment_id": g_id,
            "user_id": None,
            "category": try_on_cat,
            "subcategory": try_on_subcat,
            "color": detected_colors,
            "pattern": pattern,
            "clip_embedding": embedding,
            "bbox": [0, 0, image.width, image.height],
            "source_image_id": payload.source_image_id,
            "extraction_confidence": 1.0,
            "normalization_provider": "gpt-image",
            "florence_caption": attrs.get("florence_caption", ""),
        }
        
        # Persist to database
        db_manager.save_garment(metadata_dict)
        with open(os.path.join(g_dir, "metadata.json"), "w") as f:
            json.dump(metadata_dict, f, indent=2)
            
        # Compile response
        seasons = (
            ["Spring", "Summer", "Autumn"]
            if category != "Outerwear"
            else ["Autumn", "Winter", "Spring"]
        )
        formality_map = {
            "T-Shirt": 0.1, "Canvas Sneakers": 0.1, "Selvedge Jeans": 0.25,
            "Oxford Shirt": 0.6, "Pleated Trousers": 0.7, "Leather Loafers": 0.75,
            "Linen Blazer": 0.75, "Silk Blouse": 0.8, "Trench Coat": 0.85,
        }
        formality_score = formality_map.get(subcategory, 0.5)
        
        return {
            "garment_id": g_id,
            "image_base64": payload.image_base64,
            "straightened_image_base64": payload.image_base64,
            "attributes": {
                "category": category,
                "subcategory": subcategory,
                "colorName": attrs["colorName"],
                "labColor": lab_color,
                "material": material,
                "pattern": pattern,
                "fit": fit,
                "seasons": seasons,
                "formalityScore": formality_score,
                "silhouette": subcategory.split()[-1] if subcategory else "clothing",
                "brand": payload.brand,
                "price": payload.price,
                "embedding": embedding.tolist() if hasattr(embedding, "tolist") else list(embedding),
                "florenceCaption": attrs.get("florence_caption", ""),
                "normalizationProvider": "gpt-image",
            }
        }
    except Exception as e:
        import traceback
        traceback.print_exc()
        raise HTTPException(status_code=500, detail=str(e))

# Travel capsule planner (GPT + rule-based fallback)
@app.post("/travel/capsule")
def plan_travel_capsule(payload: TravelCapsuleRequest):
    try:
        if not payload.garments:
            raise HTTPException(status_code=400, detail="No garments provided")
        garment_dicts = [g.model_dump() for g in payload.garments]
        result = generate_travel_capsule(
            destination=payload.destination,
            trip_days=payload.trip_days,
            temp_low=payload.temp_low_f,
            temp_high=payload.temp_high_f,
            weather_condition=payload.weather_condition,
            garments=garment_dicts,
            preferred_styles=payload.preferred_styles,
        )
        return result
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))
    except Exception as e:
        import traceback
        traceback.print_exc()
        raise HTTPException(status_code=500, detail=str(e))


# 8. GET /garments - List all garments
@app.get("/garments")
def get_garments():
    try:
        garments_list = db_manager.get_all_garments()
        return {"garments": garments_list}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

# 9. GET /garments/{id} - Get garment by ID
@app.get("/garments/{id}")
def get_garment_by_id(id: str):
    try:
        garment = db_manager.get_garment(id)
        if garment is None:
            raise HTTPException(status_code=404, detail="Garment not found")
        return garment
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

# 10. POST /garments/search - Similarity search
@app.post("/garments/search")
def search_garments(file: UploadFile = File(...), limit: int = 5):
    try:
        contents = file.file.read()
        image = Image.open(io.BytesIO(contents)).convert("RGB")
        embedding = encode_image(image, device)
        results = db_manager.search_similar_garments(embedding, limit=limit)
        return {"results": results}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

# Mount static folder
static_dir = os.path.join(os.path.dirname(__file__), "static")
if os.path.exists(static_dir):
    app.mount("/static", StaticFiles(directory=static_dir), name="static")

if __name__ == "__main__":
    import uvicorn
    local_ip = get_local_ip()
    print("\n" + "="*70)
    print(" 🚀 ClosetOS Ingestion Pipeline Backend")
    print(f" 📍 Local Network LAN URL:   http://{local_ip}:8000")
    print(f" 📍 Localhost Access URL:    http://127.0.0.1:8000")
    print(f" 📍 Interactive API Test UI: http://127.0.0.1:8000/test")
    print("="*70 + "\n")
    uvicorn.run(app, host="0.0.0.0", port=8000)
