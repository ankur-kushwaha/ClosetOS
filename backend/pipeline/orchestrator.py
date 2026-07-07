"""
Digitization pipeline orchestrator.

Upload → YOLO-World + SAM → Quality Validation → Normalization →
Florence-2 → FashionCLIP → PostgreSQL + pgvector
"""

import io
import os
import json
import time
import uuid
import base64
from typing import Callable, Optional
import numpy as np
from PIL import Image, ImageOps

from database import DatabaseManager
from .config import GARMENTS_DIR, STORAGE_DIR
from .model_loaders import ModelLoaders
from .grounded_sam2 import detect_garments, segment_garment
from .quality import validate_garment
from .normalization import normalize_garment
from .florence_attrs import extract_attributes
from .fashion_clip import encode_image


def rgb_to_lab(rgb_color):
    r, g, b = [c / 255.0 for c in rgb_color]
    l = (0.2126 * r + 0.7152 * g + 0.0722 * b) * 100
    a = (r - g) * 100
    bb = (g - b) * 100
    return [float(l), float(a), float(bb)]


def map_to_try_on_category(ui_category):
    mapping = {
        "Top": "upper_body",
        "Bottom": "lower_body",
        "Dress": "full_body",
        "Outerwear": "upper_body",
        "Shoes": "footwear",
    }
    return mapping.get(ui_category, "accessory")


def map_to_try_on_subcategory(ui_subcategory):
    sub = ui_subcategory.lower()
    if "shirt" in sub:
        return "shirt"
    if "blouse" in sub:
        return "blouse"
    if "t-shirt" in sub:
        return "t-shirt"
    if "sweater" in sub:
        return "sweater"
    if "jacket" in sub or "blazer" in sub:
        return "jacket"
    if "coat" in sub:
        return "coat"
    if "trousers" in sub or "pants" in sub or "chino" in sub:
        return "pants"
    if "jeans" in sub:
        return "jeans"
    if "skirt" in sub:
        return "skirt"
    if "shorts" in sub:
        return "shorts"
    if any(w in sub for w in ["dress", "gown", "frock", "maxi", "midi"]):
        return "dress"
    if "sneakers" in sub:
        return "sneakers"
    if any(w in sub for w in ["loafers", "boots", "shoes", "derbies"]):
        return "shoes"
    return "garment"


PIPELINE_STEPS = [
    {"name": "UPLOAD", "label": "Uploading and validating image..."},
    {"name": "GROUNDED_SAM2", "label": "YOLO-World + SAM: Detection + segmentation..."},
    {"name": "QUALITY_VALIDATION", "label": "Quality validation..."},
    {"name": "NORMALIZATION", "label": "Normalization (GPT Image / FLUX Kontext)..."},
    {"name": "FLORENCE_2", "label": "Florence-2: Attribute extraction..."},
    {"name": "FASHION_CLIP", "label": "FashionCLIP: Generating embeddings..."},
    {"name": "DATABASE_PERSIST", "label": "PostgreSQL + pgvector: Persisting garment..."},
]


def create_job_steps():
    return [
        {
            "name": s["name"],
            "status": "pending",
            "progress": 0.0,
            "label": s["label"],
            "started_at": None,
            "completed_at": None,
            "elapsed": 0.0,
        }
        for s in PIPELINE_STEPS
    ]


def run_digitize_pipeline(
    job_id: str,
    image_bytes: bytes,
    device: str,
    jobs: dict,
    db_manager: DatabaseManager,
    update_job_step: Callable,
    executor=None,
    custom_boxes=None,
):
    loaders = ModelLoaders(device)
    try:
        t_start = time.time()

        # ── Step 1: UPLOAD ──
        update_job_step(job_id, 0, "running", 0.3)
        image = Image.open(io.BytesIO(image_bytes)).convert("RGB")
        image = ImageOps.exif_transpose(image)
        width, height = image.size
        job_dir = os.path.join(STORAGE_DIR, "jobs", job_id)
        os.makedirs(job_dir, exist_ok=True)
        image.save(os.path.join(job_dir, "source_image.jpg"))
        update_job_step(job_id, 0, "completed", 1.0, f"Loaded photo ({width}x{height})")

        # ── Step 2: GROUNDED_SAM2 — detect or use custom garments ──
        update_job_step(job_id, 1, "running", 0.4, "YOLO-World + SAM: Processing garments...")
        if custom_boxes is not None:
            boxes_to_process = custom_boxes
        else:
            boxes_to_process = detect_garments(image, device, loaders)
        num_garments = len(boxes_to_process)
        update_job_step(
            job_id, 1, "completed", 1.0,
            f"YOLO-World + SAM: Processing {num_garments} garment(s)",
        )

        garment_results = []

        for g_idx, (box, label, score) in enumerate(boxes_to_process):
            g_id = str(uuid.uuid4())
            g_dir = os.path.join(GARMENTS_DIR, g_id)
            os.makedirs(g_dir, exist_ok=True)
            item_label = f"item {g_idx + 1}/{num_garments} ({label})"

            # Segment within GROUNDED_SAM2 step
            update_job_step(
                job_id, 1, "running", 0.5 + (0.5 * g_idx / max(num_garments, 1)),
                f"YOLO-World + SAM: Segmenting {item_label}",
            )
            binary_mask, masked, original_crop, tight_box = segment_garment(
                image, box, device, loaders
            )
            x1, y1, x2, y2 = [int(c) for c in tight_box]
            masked_path = os.path.join(g_dir, "masked.png")
            masked.save(masked_path, "PNG")
            original_crop.save(os.path.join(g_dir, "original_crop.png"), "PNG")

            # ── Step 3: QUALITY_VALIDATION ──
            update_job_step(
                job_id, 2, "running", (g_idx + 0.5) / num_garments,
                f"Quality validation for {item_label}",
            )
            quality = validate_garment(masked, score, tight_box, width, height)
            if not quality.passed:
                print(f"Garment {g_id} failed quality ({quality.reasons}), skipping.")
                update_job_step(
                    job_id, 2, "completed", 1.0,
                    f"Skipped {item_label}: quality failed ({', '.join(quality.reasons)})",
                )
                continue
            update_job_step(
                job_id, 2, "completed", 1.0,
                f"Quality OK for {item_label} (score {quality.score:.2f})",
            )

            # ── Step 4: NORMALIZATION ──
            update_job_step(
                job_id, 3, "running", 0.5,
                f"Normalizing {item_label}...",
            )
            normalized, norm_provider = normalize_garment(masked, label)
            normalized_path = os.path.join(g_dir, "normalized.png")
            normalized.save(normalized_path, "PNG")
            # Legacy alias for clients expecting white_bg / straightened
            white_bg_path = os.path.join(g_dir, "white_bg.png")
            normalized.save(white_bg_path, "PNG")
            update_job_step(
                job_id, 3, "completed", 1.0,
                f"Normalized {item_label} via {norm_provider}",
            )

            # Thumbnail — composite transparent image onto white before JPEG save
            thumb_rgba = normalized.copy().convert("RGBA")
            thumb_rgba.thumbnail((256, 256), Image.Resampling.LANCZOS)
            canvas = Image.new("RGBA", (256, 256), (255, 255, 255, 255))
            offset = ((256 - thumb_rgba.width) // 2, (256 - thumb_rgba.height) // 2)
            canvas.paste(thumb_rgba, offset, mask=thumb_rgba)
            canvas.convert("RGB").save(os.path.join(g_dir, "thumbnail.jpg"), "JPEG", quality=90)

            # ── Step 5: FLORENCE_2 — attributes ──
            update_job_step(
                job_id, 4, "running", 0.5,
                f"Florence-2 attribute extraction for {item_label}",
            )
            attrs = extract_attributes(normalized, label, device, loaders)
            update_job_step(
                job_id, 4, "completed", 1.0,
                f"Florence-2 attributes extracted for {item_label}",
            )

            # ── Step 6: FASHION_CLIP — embeddings ──
            update_job_step(
                job_id, 5, "running", 0.5,
                f"FashionCLIP embedding for {item_label}",
            )
            embedding = encode_image(normalized, device)
            update_job_step(
                job_id, 5, "completed", 1.0,
                f"FashionCLIP embedding for {item_label}",
            )

            # Pixel color from original crop region
            cropped_mask = binary_mask[y1:y2, x1:x2] if y2 > y1 and x2 > x1 else binary_mask
            cropped_img_np = np.array(image)[y1:y2, x1:x2] if y2 > y1 and x2 > x1 else np.array(image)
            if cropped_mask.any() and cropped_img_np.size > 0:
                avg_rgb = [int(c) for c in cropped_img_np[cropped_mask].mean(axis=0)]
            else:
                avg_rgb = [128, 128, 128]
            lab_color = rgb_to_lab(avg_rgb)

            category = attrs["category"]
            subcategory = attrs["subcategory"]
            detected_colors = attrs.get("colors", ["gray"])
            pattern = attrs["pattern"]
            material = attrs["material"]
            fit = attrs["fit"]

            price_map = {
                "T-Shirt": 45.0, "Oxford Shirt": 125.0, "Silk Blouse": 240.0,
                "Pleated Trousers": 180.0, "Selvedge Jeans": 260.0,
                "Linen Blazer": 320.0, "Trench Coat": 850.0,
                "Leather Loafers": 175.0, "Canvas Sneakers": 290.0,
            }
            price = price_map.get(subcategory, 150.0)
            formality_map = {
                "T-Shirt": 0.1, "Canvas Sneakers": 0.1, "Selvedge Jeans": 0.25,
                "Oxford Shirt": 0.6, "Pleated Trousers": 0.7, "Leather Loafers": 0.75,
                "Linen Blazer": 0.75, "Silk Blouse": 0.8, "Trench Coat": 0.85,
            }
            formality_score = formality_map.get(subcategory, 0.5)
            seasons = (
                ["Spring", "Summer", "Autumn"]
                if category != "Outerwear"
                else ["Autumn", "Winter", "Spring"]
            )

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
                "bbox": [int(x1), int(y1), int(x2 - x1), int(y2 - y1)],
                "source_image_id": job_id,
                "extraction_confidence": float(score) * quality.score,
                "normalization_provider": norm_provider,
                "florence_caption": attrs.get("florence_caption", ""),
            }

            # ── Step 7: DATABASE_PERSIST ──
            update_job_step(
                job_id, 6, "running", 0.5,
                f"Persisting {item_label} to PostgreSQL + pgvector",
            )
            db_manager.save_garment(metadata_dict)
            with open(os.path.join(g_dir, "metadata.json"), "w") as f:
                json.dump(metadata_dict, f, indent=2)
            update_job_step(
                job_id, 6, "completed", 1.0,
                f"Saved {item_label} to database",
            )

            buffered_masked = io.BytesIO()
            masked.save(buffered_masked, format="PNG")
            masked_b64 = base64.b64encode(buffered_masked.getvalue()).decode("utf-8")

            buffered_norm = io.BytesIO()
            normalized.save(buffered_norm, format="PNG")
            norm_b64 = base64.b64encode(buffered_norm.getvalue()).decode("utf-8")

            garment_results.append({
                "garment_id": g_id,
                "image_base64": masked_b64,
                "straightened_image_base64": norm_b64,
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
                    "brand": "Inferred",
                    "price": price,
                    "embedding": embedding,
                    "florenceCaption": attrs.get("florence_caption", ""),
                    "normalizationProvider": norm_provider,
                },
            })

            if executor is not None:
                from .upscale import run_upscale_async
                hires_path = os.path.join(g_dir, "hires.png")
                executor.submit(run_upscale_async, normalized_path, hires_path)

        jobs[job_id]["status"] = "completed"
        jobs[job_id]["garments"] = garment_results
        print(f"Job {job_id} completed in {time.time() - t_start:.2f}s — {len(garment_results)} garment(s)")

    except Exception as e:
        print(f"Pipeline error in job {job_id}: {e}")
        import traceback
        traceback.print_exc()
        if job_id in jobs:
            jobs[job_id]["status"] = "failed"
            jobs[job_id]["error"] = str(e)
            for step in jobs[job_id]["steps"]:
                if step["status"] in ("pending", "running"):
                    step["status"] = "failed"
                    step["completed_at"] = time.time()
