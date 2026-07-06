import os
import io
import json
import time
import uuid
import base64
import asyncio
import threading
from typing import List, Dict, Any, Optional
from concurrent.futures import ThreadPoolExecutor

import torch
import numpy as np
from PIL import Image, ImageOps
from fastapi import FastAPI, UploadFile, File, HTTPException, BackgroundTasks, Depends, Request
from fastapi.responses import HTMLResponse, FileResponse
from fastapi.staticfiles import StaticFiles
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, EmailStr, Field

# Database and pipeline imports
from database import DatabaseManager
from auth import (
    hash_password,
    verify_password,
    create_access_token,
    get_current_user_id,
    new_user_id,
)
from storage.r2 import upload_image, delete_garment_images, local_image_path, is_r2_configured, check_r2
from pipeline.config import (
    DETECTION_MODEL,
    GARMENTS_DIR,
    STORAGE_DIR,
    NORMALIZATION_PROVIDER,
    MIN_DETECTION_CONFIDENCE,
    TRY_ON_MODEL,
)


def _sanitize_for_json(value: Any) -> Any:
    """Recursively convert numpy types to native Python for json.dumps / FastAPI."""
    if isinstance(value, np.ndarray):
        return value.tolist()
    if isinstance(value, (np.floating, np.integer)):
        return value.item()
    if isinstance(value, dict):
        return {k: _sanitize_for_json(v) for k, v in value.items()}
    if isinstance(value, (list, tuple)):
        return [_sanitize_for_json(v) for v in value]
    return value

from pipeline.model_loaders import ModelLoaders
from pipeline.normalization import normalize_garment
from pipeline.florence_attrs import extract_attributes
from pipeline.fashion_clip import encode_image
from pipeline.yolo_world import detect_yolo_world
from pipeline.travel_capsule import generate_travel_capsule
from pipeline.try_on import render_try_on, TryOnError

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

# Serialize ML inference (FashionCLIP) — one request at a time
_inference_lock = threading.Lock()

# Determine PyTorch device acceleration
device = os.getenv("TORCH_DEVICE", "").strip().lower()
if device not in ("cpu", "cuda", "mps"):
    device = "cuda" if torch.cuda.is_available() else "cpu"
    if device == "cpu" and torch.backends.mps.is_available():
        device = "mps"
if device == "cpu":
    torch.set_num_threads(max(1, (os.cpu_count() or 4) // 2))
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
        "try_on_model": TRY_ON_MODEL,
        "database": db_manager.db_type,
        "storage": GARMENTS_DIR,
        "health_check": "/health/check",
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
    garment_id: Optional[str] = None

class PrecomputedAttributes(BaseModel):
    category: str
    subcategory: str
    colorName: str
    labColor: List[float]
    material: str
    pattern: str
    fit: str
    seasons: List[str]
    formalityScore: float
    silhouette: str
    embedding: List[float] = []
    florenceCaption: str = ""


class FinalizePayload(BaseModel):
    image_base64: str
    crop_base64: Optional[str] = None
    label: str
    source_image_id: Optional[str] = None
    brand: Optional[str] = "Inferred"
    price: Optional[float] = 150.0
    precomputed_attributes: Optional[PrecomputedAttributes] = None


class ExtractMetadataPayload(BaseModel):
    crop_base64: str
    label: str


class BulkMetadataItem(BaseModel):
    id: str
    crop_base64: str
    label: str


class BulkMetadataPayload(BaseModel):
    items: List[BulkMetadataItem]

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


class TryOnGarmentInput(BaseModel):
    id: str
    category: str
    subcategory: str
    colorName: str = ""
    image_base64: str


class TryOnRequest(BaseModel):
    person_image_base64: str
    garments: List[TryOnGarmentInput]
    outfit_id: Optional[str] = None

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

def _build_garment_attributes(
    attrs: Dict[str, Any],
    embedding,
    image: Image.Image,
    label: str,
    brand: str = "Inferred",
    price: float = 150.0,
    normalization_provider: str = "gpt-image",
) -> Dict[str, Any]:
    """Shared attribute response used by extract-metadata and finalize."""
    from pipeline.orchestrator import rgb_to_lab

    img_np = np.array(image)
    avg_rgb = [128, 128, 128]
    if img_np.size > 0:
        mask = img_np[:, :, 3] > 10 if img_np.shape[2] == 4 else np.ones(img_np.shape[:2], dtype=bool)
        if mask.any():
            avg_rgb = [int(c) for c in img_np[mask].mean(axis=0)[:3]]
    lab_color = rgb_to_lab(avg_rgb)

    category = attrs["category"]
    subcategory = attrs["subcategory"]
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

    return _sanitize_for_json({
        "category": category,
        "subcategory": subcategory,
        "colorName": attrs["colorName"],
        "labColor": lab_color,
        "material": attrs["material"],
        "pattern": attrs["pattern"],
        "fit": attrs["fit"],
        "seasons": seasons,
        "formalityScore": formality_score,
        "silhouette": subcategory.split()[-1] if subcategory else "clothing",
        "brand": brand,
        "price": price,
        "embedding": embedding.tolist() if hasattr(embedding, "tolist") else list(embedding),
        "florenceCaption": attrs.get("florence_caption", ""),
        "normalizationProvider": normalization_provider,
    })


@app.post("/yolo-world/embed")
def embed_garment_crop(payload: CropPayload):
    """FashionCLIP embedding only — serialized via inference lock."""
    try:
        from pipeline.fashion_clip import encode_image

        crop_bytes = base64.b64decode(payload.crop_base64)
        image = Image.open(io.BytesIO(crop_bytes)).convert("RGBA")
        with _inference_lock:
            embedding = encode_image(image, loaders.device)
        return {"embedding": _sanitize_for_json(embedding)}
    except Exception as e:
        import traceback
        traceback.print_exc()
        raise HTTPException(status_code=500, detail=str(e))


@app.post("/yolo-world/extract-metadata")
def extract_garment_metadata(payload: ExtractMetadataPayload):
    """Label + pixel color only (no embedding — that runs on finalize)."""
    try:
        from pipeline.florence_attrs import attrs_from_image

        crop_bytes = base64.b64decode(payload.crop_base64)
        image = Image.open(io.BytesIO(crop_bytes)).convert("RGBA")
        attrs = attrs_from_image(image, payload.label)

        return {
            "attributes": _build_garment_attributes(
                attrs, [], image, payload.label
            )
        }
    except Exception as e:
        import traceback
        traceback.print_exc()
        raise HTTPException(status_code=500, detail=str(e))


@app.post("/yolo-world/extract-metadata/bulk")
def extract_garment_metadata_bulk(payload: BulkMetadataPayload):
    """Bulk light metadata — pixel color + label heuristics only (CPU-friendly)."""
    if len(payload.items) > 64:
        raise HTTPException(status_code=400, detail="Maximum 64 items per bulk request")

    from pipeline.florence_attrs import attrs_from_image

    results = []
    for item in payload.items:
        try:
            crop_bytes = base64.b64decode(item.crop_base64)
            image = Image.open(io.BytesIO(crop_bytes)).convert("RGBA")
            attrs = attrs_from_image(image, item.label)
            results.append({
                "id": item.id,
                "ok": True,
                "attributes": _build_garment_attributes(
                    attrs, [], image, item.label
                ),
            })
        except Exception as e:
            results.append({"id": item.id, "ok": False, "error": str(e)})

    return {"items": results}


@app.post("/yolo-world/normalize")
def normalize_crop(payload: CropPayload, request: Request):
    try:
        crop_bytes = base64.b64decode(payload.crop_base64)
        crop_img = Image.open(io.BytesIO(crop_bytes)).convert("RGBA")

        normalized, provider = normalize_garment(crop_img, payload.label or "garment")

        buffered = io.BytesIO()
        normalized.save(buffered, format="PNG")
        img_bytes = buffered.getvalue()
        img_str = base64.b64encode(img_bytes).decode("utf-8")

        straightened_url = None
        # Extract user_id if authenticated
        auth_header = request.headers.get("Authorization")
        user_id = None
        if auth_header and auth_header.startswith("Bearer "):
            token = auth_header.split(" ")[1]
            from auth import decode_token
            user_id = decode_token(token)

        if payload.garment_id and user_id:
            # Upload straightened image directly to R2 / local disk
            straightened_url = upload_image(img_bytes, user_id, payload.garment_id, "straightened.png")
            # Fetch the existing wardrobe item
            existing_item = db_manager.get_wardrobe_item(user_id, payload.garment_id)
            if existing_item:
                stored_json = {k: v for k, v in existing_item.items() if k not in ("imagePath", "straightenedImagePath")}
                existing_image_url = existing_item.get("imagePath")
                db_manager.upsert_wardrobe_item(
                    user_id=user_id,
                    item_id=payload.garment_id,
                    garment_json=stored_json,
                    image_url=existing_image_url,
                    straightened_image_url=straightened_url
                )
            if straightened_url and straightened_url.startswith("http"):
                straightened_url = f"{straightened_url}?v={int(time.time())}"

        return {
            "image_base64": img_str,
            "provider": provider,
            "straightened_image_url": straightened_url
        }
    except Exception as e:
        import traceback
        traceback.print_exc()
        raise HTTPException(status_code=500, detail=str(e))

@app.post("/yolo-world/finalize")
def yolo_world_finalize(payload: FinalizePayload):
    try:
        from pipeline.fashion_clip import encode_image
        from pipeline.orchestrator import map_to_try_on_category, map_to_try_on_subcategory
        
        # Decode base64 image
        img_bytes = base64.b64decode(payload.image_base64)
        image = Image.open(io.BytesIO(img_bytes)).convert("RGBA")
        
        if payload.precomputed_attributes:
            pre = payload.precomputed_attributes
            attrs = {
                "category": pre.category,
                "subcategory": pre.subcategory,
                "colorName": pre.colorName or "Unknown",
                "colors": [pre.colorName.lower() if pre.colorName else "gray"],
                "material": pre.material,
                "pattern": pre.pattern,
                "fit": pre.fit,
                "florence_caption": pre.florenceCaption or payload.label,
            }
            if pre.embedding:
                embedding = np.array(pre.embedding)
            else:
                with _inference_lock:
                    embedding = encode_image(image, loaders.device)
            response_attrs = _build_garment_attributes(
                attrs,
                embedding,
                image,
                payload.label,
                brand=payload.brand or "Inferred",
                price=payload.price or 150.0,
                normalization_provider="none",
            )
        else:
            from pipeline.florence_attrs import attrs_from_image
            attrs = attrs_from_image(image, payload.label)
            with _inference_lock:
                embedding = encode_image(image, loaders.device)
            response_attrs = _build_garment_attributes(
                attrs,
                embedding,
                image,
                payload.label,
                brand=payload.brand or "Inferred",
                price=payload.price or 150.0,
                normalization_provider="none",
            )
        
        # Create file assets on disk
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
                temp_path = os.path.join(STORAGE_DIR, "temp_sources", f"{payload.source_image_id}.jpg")
                if os.path.exists(temp_path):
                    import shutil
                    shutil.copy(temp_path, os.path.join(g_dir, "source_image.jpg"))
            except Exception as e:
                print(f"Error copying source_image.jpg: {str(e)}")
        
        category = attrs["category"]
        subcategory = attrs["subcategory"]
        detected_colors = attrs.get("colors", ["gray"])
        pattern = attrs["pattern"]

        embedding_list = _sanitize_for_json(embedding)
        
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
            "clip_embedding": embedding_list,
            "bbox": [0, 0, image.width, image.height],
            "source_image_id": payload.source_image_id,
            "extraction_confidence": 1.0,
            "normalization_provider": response_attrs.get("normalizationProvider", "gpt-image"),
            "florence_caption": attrs.get("florence_caption", ""),
        }
        
        # Persist to database
        metadata_dict = _sanitize_for_json(metadata_dict)
        db_manager.save_garment(metadata_dict)
        with open(os.path.join(g_dir, "metadata.json"), "w") as f:
            json.dump(metadata_dict, f, indent=2)

        response_attrs = _sanitize_for_json(response_attrs)
            
        return {
            "garment_id": g_id,
            "image_base64": payload.image_base64,
            "straightened_image_base64": None,
            "attributes": response_attrs,
        }
    except Exception as e:
        import traceback
        traceback.print_exc()
        raise HTTPException(status_code=500, detail=str(e))


def _check_try_on() -> Dict[str, Any]:
    """Diagnose Gemini billing vs known image-model quota bug."""
    from pipeline.config import GEMINI_API_KEY, TRY_ON_MODEL

    if not GEMINI_API_KEY:
        return {
            "ok": False,
            "model": TRY_ON_MODEL,
            "error": "GEMINI_API_KEY is not set in backend/.env",
        }

    key_hint = f"{GEMINI_API_KEY[:4]}…{GEMINI_API_KEY[-4:]}" if len(GEMINI_API_KEY) > 8 else "(too short)"
    result: Dict[str, Any] = {
        "ok": False,
        "model": TRY_ON_MODEL,
        "key_hint": key_hint,
        "text_model_ok": False,
        "image_model_ok": False,
    }

    try:
        from google import genai
        from google.genai import types

        client = genai.Client(api_key=GEMINI_API_KEY)
        client.models.get(model=TRY_ON_MODEL)

        try:
            client.models.generate_content(
                model="gemini-2.5-flash-lite",
                contents="Reply with OK",
            )
            result["text_model_ok"] = True
        except Exception as text_exc:
            result["text_error"] = str(text_exc)[:300]

        try:
            client.models.generate_content(
                model=TRY_ON_MODEL,
                contents="A red circle on white background",
                config=types.GenerateContentConfig(response_modalities=["IMAGE"]),
            )
            result["image_model_ok"] = True
            result["ok"] = True
            result["message"] = "Image generation is working."
            return result
        except Exception as image_exc:
            err = str(image_exc)
            result["image_error"] = err[:500]
            if "free_tier" in err and result["text_model_ok"]:
                result["diagnosis"] = "known_platform_bug"
                result["message"] = (
                    "Billing is active (text models work) but image models are still "
                    "routed to free_tier with limit 0. This is a known Google bug "
                    "affecting Paid Tier 1 image generation since Feb 2026."
                )
                result["actions"] = [
                    "Wait 30–60 min after enabling billing (you paid today) and retry.",
                    "Delete and recreate the ClosetOS API key in AI Studio.",
                    "In GCP Console (project adboard-booking): APIs & Services → enable 'Generative Language API'.",
                    "Report at https://discuss.ai.google.dev/t/bug-paid-tier-1-account-getting-free-tier-requests-limit-0-on-image-generation-models-gemini-2-5-flash-image-gemini-3-pro-image-preview/123906",
                    "Or use Vertex AI with a service account (bypasses this AI Studio bug).",
                ]
            elif "free_tier" in err:
                result["diagnosis"] = "billing_not_linked"
                result["message"] = "API key project has no paid image quota. Link billing in AI Studio."
            else:
                result["diagnosis"] = "other_error"
                result["message"] = "Image generation failed for another reason."
            return result
    except Exception as e:
        result["error"] = str(e)
        return result


@app.get("/health/check")
def health_check():
    """Diagnose try-on (Gemini) and wardrobe image storage (Cloudflare R2)."""
    try_on = _check_try_on()
    r2 = check_r2()
    return {
        "ok": try_on.get("ok", False) and r2.get("ok", False),
        "try_on": try_on,
        "r2": r2,
        "database": db_manager.db_type,
        "image_storage": "r2" if is_r2_configured() else "local",
    }


@app.post("/try-on/render")
def try_on_render(payload: TryOnRequest):
    try:
        if not payload.person_image_base64:
            raise HTTPException(status_code=400, detail="person_image_base64 is required")
        if not payload.garments:
            raise HTTPException(status_code=400, detail="At least one garment is required")

        garment_dicts = [g.model_dump() for g in payload.garments]
        result = render_try_on(payload.person_image_base64, garment_dicts)
        result["outfit_id"] = payload.outfit_id
        return result
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))
    except TryOnError as e:
        raise HTTPException(status_code=e.status_code, detail=str(e))
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


# --- Auth ---

class SignupPayload(BaseModel):
    email: EmailStr
    password: str = Field(min_length=6)
    name: str = Field(min_length=1, max_length=255)


class LoginPayload(BaseModel):
    email: EmailStr
    password: str


class OnboardingPayload(BaseModel):
    taste: Dict[str, Any]
    onboarding_completed: bool = True


def _public_user(user: Dict[str, Any]) -> Dict[str, Any]:
    return {
        "user_id": user["user_id"],
        "email": user["email"],
        "name": user["name"],
        "taste": user.get("taste"),
        "onboarding_completed": user.get("onboarding_completed", False),
    }


@app.post("/auth/signup")
def signup(payload: SignupPayload):
    existing = db_manager.get_user_by_email(payload.email)
    if existing:
        raise HTTPException(status_code=409, detail="Email already registered")

    user_id = new_user_id()
    user = db_manager.create_user(
        user_id=user_id,
        email=payload.email,
        password_hash=hash_password(payload.password),
        name=payload.name.strip(),
    )
    if not user:
        raise HTTPException(status_code=500, detail="Failed to create user")

    token = create_access_token(user_id)
    return {"token": token, "user": _public_user(user)}


@app.post("/auth/login")
def login(payload: LoginPayload):
    user = db_manager.get_user_by_email(payload.email)
    if not user or not verify_password(payload.password, user["password_hash"]):
        raise HTTPException(status_code=401, detail="Invalid email or password")

    token = create_access_token(user["user_id"])
    return {"token": token, "user": _public_user(user)}


@app.get("/auth/me")
def get_me(user_id: str = Depends(get_current_user_id)):
    user = db_manager.get_user_by_id(user_id)
    if not user:
        raise HTTPException(status_code=404, detail="User not found")
    return _public_user(user)


@app.patch("/auth/onboarding")
def update_onboarding(
    payload: OnboardingPayload,
    user_id: str = Depends(get_current_user_id),
):
    ok = db_manager.update_user_profile(
        user_id,
        taste=payload.taste,
        onboarding_completed=payload.onboarding_completed,
    )
    if not ok:
        raise HTTPException(status_code=500, detail="Failed to save onboarding")

    user = db_manager.get_user_by_id(user_id)
    if not user:
        raise HTTPException(status_code=404, detail="User not found")
    return _public_user(user)


# --- Wardrobe sync (Cloudflare R2 or local disk for images) ---

class WardrobeSyncPayload(BaseModel):
    garment: Dict[str, Any]
    image_base64: Optional[str] = None
    straightened_image_base64: Optional[str] = None


def _decode_image_b64(value: Optional[str]) -> Optional[bytes]:
    if not value:
        return None
    if value.startswith("b64://"):
        value = value.split("/", 2)[-1]
    try:
        return base64.b64decode(value)
    except Exception:
        return None


def _resolve_image_bytes(path_or_b64: Optional[str], explicit_b64: Optional[str]) -> Optional[bytes]:
    if explicit_b64:
        return _decode_image_b64(explicit_b64)
    if not path_or_b64:
        return None
    if path_or_b64.startswith("http://") or path_or_b64.startswith("https://"):
        return None  # already uploaded
    return _decode_image_b64(path_or_b64)


def _sync_wardrobe_item(
    user_id: str,
    garment: Dict[str, Any],
    image_base64: Optional[str] = None,
    straightened_image_base64: Optional[str] = None,
) -> Dict[str, Any]:
    item_id = garment.get("id") or str(uuid.uuid4())
    garment = {**garment, "id": item_id}

    image_url = garment.get("imagePath") if str(garment.get("imagePath", "")).startswith("http") else None
    straightened_url = (
        garment.get("straightenedImagePath")
        if str(garment.get("straightenedImagePath", "")).startswith("http")
        else None
    )

    crop_bytes = _resolve_image_bytes(garment.get("imagePath"), image_base64)
    if crop_bytes:
        image_url = upload_image(crop_bytes, user_id, item_id, "crop.png")

    straight_bytes = _resolve_image_bytes(
        garment.get("straightenedImagePath"), straightened_image_base64
    )
    if straight_bytes:
        if crop_bytes and straight_bytes == crop_bytes:
            straightened_url = image_url
        else:
            straightened_url = upload_image(straight_bytes, user_id, item_id, "straightened.png")
    elif crop_bytes and not straightened_url:
        straightened_url = image_url

    # Store metadata without inline base64
    stored = {k: v for k, v in garment.items() if k not in ("imagePath", "straightenedImagePath")}
    stored_json = {**stored, "id": item_id}

    ok = db_manager.upsert_wardrobe_item(
        user_id=user_id,
        item_id=item_id,
        garment_json=stored_json,
        image_url=image_url,
        straightened_image_url=straightened_url,
    )
    if not ok:
        raise HTTPException(status_code=500, detail="Failed to save wardrobe item")

    result = {**stored_json}
    if image_url:
        result["imagePath"] = image_url
    if straightened_url:
        result["straightenedImagePath"] = straightened_url
    return result


@app.get("/wardrobe")
def list_wardrobe(user_id: str = Depends(get_current_user_id)):
    items = db_manager.get_wardrobe_items(user_id)
    return {"garments": items, "storage": "r2" if is_r2_configured() else "local"}


@app.post("/wardrobe/sync")
def sync_wardrobe_item(
    payload: WardrobeSyncPayload,
    user_id: str = Depends(get_current_user_id),
):
    try:
        garment = _sync_wardrobe_item(
            user_id,
            payload.garment,
            payload.image_base64,
            payload.straightened_image_base64,
        )
        return {"garment": garment}
    except HTTPException:
        raise
    except Exception as e:
        import traceback
        traceback.print_exc()
        raise HTTPException(status_code=500, detail=str(e))


@app.post("/wardrobe/sync-all")
def sync_wardrobe_bulk(
    garments: List[WardrobeSyncPayload],
    user_id: str = Depends(get_current_user_id),
):
    synced = []
    for entry in garments:
        synced.append(
            _sync_wardrobe_item(
                user_id,
                entry.garment,
                entry.image_base64,
                entry.straightened_image_base64,
            )
        )
    return {"garments": synced, "count": len(synced)}


@app.delete("/wardrobe/{item_id}")
def delete_wardrobe_item(
    item_id: str,
    user_id: str = Depends(get_current_user_id),
):
    deleted = db_manager.delete_wardrobe_item(user_id, item_id)
    if not deleted:
        raise HTTPException(status_code=404, detail="Item not found")
    try:
        delete_garment_images(user_id, item_id)
    except Exception as e:
        print(f"Warning: could not delete images for {item_id}: {e}")
    return {"deleted": True, "item_id": item_id}


@app.get("/wardrobe/images/{user_id}/{garment_id}/{filename}")
def serve_wardrobe_image(user_id: str, garment_id: str, filename: str):
    path = local_image_path(user_id, garment_id, filename)
    if not path:
        raise HTTPException(status_code=404, detail="Image not found")
    media = "image/png" if filename.endswith(".png") else "image/jpeg"
    return FileResponse(path, media_type=media)


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
    uvicorn.run("server:app", host="0.0.0.0", port=8000, reload=True)
