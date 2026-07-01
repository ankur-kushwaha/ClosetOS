"""Florence-2 attribute extraction from normalized garment images."""

import re
from typing import Dict, Any, List
from PIL import Image
import torch


def extract_attributes(
    normalized_image: Image.Image,
    detection_label: str,
    device: str,
    model_loaders,
) -> Dict[str, Any]:
    fl_model, fl_processor = model_loaders.get_florence_model()

    caption = _run_florence_task(
        fl_model, fl_processor, normalized_image, "<MORE_DETAILED_CAPTION>", device
    )
    if not caption:
        caption = _run_florence_task(
            fl_model, fl_processor, normalized_image, "<CAPTION>", device
        )

    od_label = detection_label.lower()
    category = _infer_category(od_label, caption)
    subcategory = _infer_subcategory(od_label, caption, category)
    colors = _infer_colors(caption, normalized_image)
    pattern = _infer_pattern(caption)
    material = _infer_material(caption)
    fit = _infer_fit(caption)

    return {
        "category": category,
        "subcategory": subcategory,
        "colorName": colors[0].capitalize() if colors else "Unknown",
        "colors": colors,
        "material": material,
        "pattern": pattern,
        "fit": fit,
        "florence_caption": caption or od_label,
    }


def _run_florence_task(model, processor, image: Image.Image, task: str, device: str) -> str:
    try:
        inputs = processor(text=task, images=image, return_tensors="pt").to(device)
        with torch.no_grad():
            generated_ids = model.generate(
                input_ids=inputs["input_ids"],
                pixel_values=inputs["pixel_values"],
                max_new_tokens=256,
                num_beams=3,
            )
        text = processor.batch_decode(generated_ids, skip_special_tokens=True)[0]
        parsed = processor.post_process_generation(text, task=task, image_size=image.size)
        return parsed.get(task, text) if isinstance(parsed, dict) else text
    except Exception as e:
        print(f"Florence-2 task {task} failed: {e}")
        return ""


def _infer_category(label: str, caption: str) -> str:
    text = f"{label} {caption}".lower()
    if any(w in text for w in ["sneaker", "loafer", "boot", "shoe", "sandal", "heel", "footwear"]):
        return "Shoes"
    if any(w in text for w in ["jacket", "coat", "blazer", "overcoat", "parka", "cardigan"]):
        return "Outerwear"
    if any(w in text for w in ["pants", "jeans", "trousers", "shorts", "skirt", "leggings"]):
        return "Bottom"
    return "Top"


def _infer_subcategory(label: str, caption: str, category: str) -> str:
    text = f"{label} {caption}".lower()
    sub_map = {
        "shirt": "Oxford Shirt", "oxford": "Oxford Shirt", "blouse": "Silk Blouse",
        "t-shirt": "T-Shirt", "tee": "T-Shirt", "sweater": "Knit Sweater", "knit": "Knit Sweater",
        "jeans": "Selvedge Jeans", "denim": "Selvedge Jeans", "trousers": "Pleated Trousers",
        "pants": "Chino Pants", "chino": "Chino Pants", "shorts": "Denim Shorts",
        "skirt": "Midi Skirt", "jacket": "Bomber Jacket", "blazer": "Linen Blazer",
        "coat": "Trench Coat", "trench": "Trench Coat", "sneaker": "Canvas Sneakers",
        "loafer": "Leather Loafers", "boot": "Leather Boots",
    }
    for key, sub in sub_map.items():
        if key in text:
            return sub
    defaults = {
        "Top": "Oxford Shirt", "Bottom": "Chino Pants",
        "Outerwear": "Bomber Jacket", "Shoes": "Canvas Sneakers",
    }
    return defaults.get(category, "Garment")


def _infer_colors(caption: str, image: Image.Image) -> List[str]:
    text = caption.lower()
    color_words = [
        "black", "white", "navy", "blue", "gray", "grey", "red", "green",
        "yellow", "orange", "brown", "pink", "purple", "olive", "khaki",
        "cream", "beige", "burgundy", "maroon", "teal", "tan",
    ]
    found = [c for c in color_words if re.search(rf"\b{c}\b", text)]
    if found:
        return found[:3]
    # Pixel average fallback
    import numpy as np
    arr = np.array(image.convert("RGB"))
    avg = arr.reshape(-1, 3).mean(axis=0)
    r, g, b = avg
    if r > 200 and g > 200 and b > 200:
        return ["white"]
    if r < 60 and g < 60 and b < 60:
        return ["black"]
    if b > r and b > g:
        return ["blue"]
    if g > r and g > b:
        return ["green"]
    return ["gray"]


def _infer_pattern(caption: str) -> str:
    text = caption.lower()
    for p in ["striped", "plaid", "checkered", "floral", "graphic", "denim", "solid"]:
        if p in text:
            return p if p != "denim" else "solid"
    return "solid"


def _infer_material(caption: str) -> str:
    text = caption.lower()
    materials = {
        "cotton": "Organic Cotton", "silk": "Morus Silk", "linen": "Belgian Linen",
        "wool": "Linen Wool", "denim": "Japanese Denim", "leather": "Calfskin Leather",
        "polyester": "Technical Polyester", "cashmere": "Cashmere Blend",
    }
    for key, mat in materials.items():
        if key in text:
            return mat
    return "Organic Cotton"


def _infer_fit(caption: str) -> str:
    text = caption.lower()
    for f in ["slim", "relaxed", "tailored", "oversized", "fitted", "regular"]:
        if f in text:
            return f.capitalize() if f != "oversized" else "Relaxed"
    return "Regular"
