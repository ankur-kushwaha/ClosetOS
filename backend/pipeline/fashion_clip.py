"""FashionCLIP image embeddings (512-d)."""

from typing import List
from PIL import Image
import torch
import numpy as np


_fashion_clip_model = None


def get_fashion_clip_model(device: str):
    global _fashion_clip_model
    if _fashion_clip_model is None:
        try:
            from fashion_clip.fashion_clip import FashionCLIP
            print("Loading FashionCLIP...")
            _fashion_clip_model = ("fashion_clip_pkg", FashionCLIP("fashion-clip"), device)
        except ImportError:
            from transformers import CLIPModel, CLIPProcessor
            print("FashionCLIP package not found; using Marqo fashion CLIP weights via transformers...")
            model_id = "patrickjohncyh/fashion-clip"
            model = CLIPModel.from_pretrained(model_id).to(device)
            processor = CLIPProcessor.from_pretrained(model_id)
            _fashion_clip_model = ("transformers", model, processor, device)
    return _fashion_clip_model


def encode_image(image: Image.Image, device: str) -> List[float]:
    model_info = get_fashion_clip_model(device)
    kind = model_info[0]

    if kind == "fashion_clip_pkg":
        _, fclip, dev = model_info
        import tempfile
        import os
        with tempfile.NamedTemporaryFile(suffix=".png", delete=False) as tmp:
            image.convert("RGB").save(tmp.name)
            tmp_path = tmp.name
        try:
            embeds = fclip.encode_images([tmp_path], batch_size=1)
            vec = embeds[0]
            if hasattr(vec, "tolist"):
                vec = vec.tolist()
            norm = np.linalg.norm(vec)
            if norm > 0:
                vec = (np.array(vec) / norm).tolist()
            return vec
        finally:
            os.unlink(tmp_path)

    _, model, processor, dev = model_info
    inputs = processor(images=image.convert("RGB"), return_tensors="pt").to(dev)
    with torch.no_grad():
        features = model.get_image_features(**inputs)
        features = features / features.norm(p=2, dim=-1, keepdim=True)
    return features[0].cpu().numpy().tolist()
