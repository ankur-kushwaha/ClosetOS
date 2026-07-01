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
from pipeline.grounded_sam2 import detect_garments, segment_garment
from pipeline.quality import validate_garment
from pipeline.normalization import normalize_garment
from pipeline.florence_attrs import extract_attributes
from pipeline.fashion_clip import encode_image
from pipeline.orchestrator import run_digitize_pipeline, create_job_steps
from pipeline.yolo_world import detect_yolo_world

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

# 1. POST /digitize/start - Start async job
@app.post("/digitize/start")
async def start_digitization_job(file: UploadFile = File(...), background_tasks: BackgroundTasks = None):
    try:
        contents = await file.read()
        job_id = str(uuid.uuid4())
        
        # Initialize job steps using shared function from orchestrator
        job_steps = create_job_steps()
        
        jobs[job_id] = {
            "job_id": job_id,
            "status": "processing",
            "progress": 0.0,
            "current_step": "UPLOAD",
            "steps": job_steps,
            "garments": [],
            "error": None
        }
        
        # Dispatch background thread pipeline execution
        background_tasks.add_task(
            run_digitize_pipeline,
            job_id,
            contents,
            device,
            jobs,
            db_manager,
            update_job_step,
            executor
        )
        
        return {"job_id": job_id, "status": "processing"}
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Failed to submit digitization job: {str(e)}")

# 2. GET /digitize/jobs/{job_id} - Poll job progress
@app.get("/digitize/jobs/{job_id}")
def get_digitization_job_status(job_id: str):
    if job_id not in jobs:
        raise HTTPException(status_code=404, detail="Job not found")
    return jobs[job_id]

# 3. POST /digitize/detect - Step 2 (Grounding DINO or Florence-2 phrase grounding)
@app.post("/digitize/detect")
async def step_detect(file: UploadFile = File(...)):
    try:
        contents = await file.read()
        os.makedirs(STORAGE_DIR, exist_ok=True)
        with open(os.path.join(STORAGE_DIR, "debug_image.png"), "wb") as f:
            f.write(contents)
        image = Image.open(io.BytesIO(contents)).convert("RGB")
        image = ImageOps.exif_transpose(image)
        boxes_to_process = detect_garments(image, device, loaders)
        bboxes = [item[0] for item in boxes_to_process]
        labels = [item[1] for item in boxes_to_process]
        return {"bboxes": bboxes, "labels": labels}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

# YOLO-World custom endpoint
@app.post("/yolo-world/detect")
async def yolo_world_detect(file: UploadFile = File(...), confidence: float = 0.15):
    try:
        contents = await file.read()
        image = Image.open(io.BytesIO(contents)).convert("RGB")
        image = ImageOps.exif_transpose(image)
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

class FlorencePayload(BaseModel):
    image_base64: str
    task_prompt: Optional[str] = "<MORE_DETAILED_CAPTION>"

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
async def run_florence(payload: FlorencePayload):
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
async def gpt_normalize_crop(payload: CropPayload):
    try:
        # Decode base64 crop image
        crop_bytes = base64.b64decode(payload.crop_base64)
        crop_img = Image.open(io.BytesIO(crop_bytes)).convert("RGBA")
        
        
        # 3. Build custom prompt incorporating the structured caption
        custom_prompt = (
            "Create a professional e-commerce flat-lay product photograph of the garment itself, with NO human model, head, face, hands, arms, legs, skin, or mannequin. "
            # f"The garment to isolate is: {caption}. "
            "Remove any human bodies and isolate ONLY the clothing item. Lay the clothing completely flat and straightened. "
            "Strictly preserve the exact neckline and collar shape (e.g., V-neck, round neck, polo collar, stand collar), "
            "fabric texture, patterns, colors, stitching, buttons, and proportions. Do not change the neckline type "
            "or modify the garment structure. Place the garment against a clean, solid studio white background "
            "with bright, neutral studio lighting. Do NOT generate any humans, models, body parts, or backgrounds other than pure white."
        )
     
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

# 4. POST /digitize/segment - SAM segmentation for a bounding box
@app.post("/digitize/segment")
async def step_segment(file: UploadFile = File(...), bbox: str = "[50,50,400,500]"):
    try:
        box = json.loads(bbox)
        contents = await file.read()
        image = Image.open(io.BytesIO(contents)).convert("RGB")
        binary_mask, masked, original_crop, tight_box = segment_garment(image, box, device, loaders)
        
        # Save cropped masked garment as PNG and base64-encode
        buffered = io.BytesIO()
        masked.save(buffered, format="PNG")
        img_str = base64.b64encode(buffered.getvalue()).decode("utf-8")
        return {"mask_image_base64": img_str}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

# 5. POST /digitize/normalize - standardizes the garment image
@app.post("/digitize/normalize")
async def step_normalize(file: UploadFile = File(...)):
    try:
        contents = await file.read()
        image = Image.open(io.BytesIO(contents)).convert("RGBA")
        normalized, provider = normalize_garment(image, "garment")
        
        buffered = io.BytesIO()
        normalized.save(buffered, format="PNG")
        img_str = base64.b64encode(buffered.getvalue()).decode("utf-8")
        return {"image_base64": img_str, "provider": provider}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

# 6. POST /digitize/attributes - Florence-2 attribute extraction
@app.post("/digitize/attributes")
async def step_attributes(file: UploadFile = File(...), label: str = "shirt"):
    try:
        contents = await file.read()
        image = Image.open(io.BytesIO(contents)).convert("RGB")
        attrs = extract_attributes(image, label, device, loaders)
        return attrs
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

# 7. POST /digitize/embed - FashionCLIP embedding
@app.post("/digitize/embed")
async def step_embed(file: UploadFile = File(...)):
    try:
        contents = await file.read()
        image = Image.open(io.BytesIO(contents)).convert("RGB")
        embedding = encode_image(image, device)
        return {"dimensions": len(embedding), "embedding": embedding}
    except Exception as e:
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
async def search_garments(file: UploadFile = File(...), limit: int = 5):
    try:
        contents = await file.read()
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
