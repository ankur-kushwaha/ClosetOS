"""Lazy-loaded ML models shared across pipeline steps."""

import torch
from transformers import (
    AutoProcessor,
    SamModel,
    SamProcessor,
    AutoModelForCausalLM,
)


class ModelLoaders:
    def __init__(self, device: str):
        self.device = device
        self._sam_model = None
        self._sam_processor = None
        self._florence_model = None
        self._florence_processor = None
        self._rembg_session = None
        self._yolo_world_model = None

    def get_sam_model(self):
        if self._sam_model is None:
            print("Loading SAM (Segment Anything)...")
            self._sam_model = SamModel.from_pretrained("facebook/sam-vit-base").to(self.device)
            self._sam_processor = SamProcessor.from_pretrained("facebook/sam-vit-base")
        return self._sam_model, self._sam_processor

    def get_florence_model(self):
        if self._florence_model is None:
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
                self._florence_model = AutoModelForCausalLM.from_pretrained(
                    "microsoft/Florence-2-base", trust_remote_code=True
                ).to(self.device)
                self._florence_processor = AutoProcessor.from_pretrained(
                    "microsoft/Florence-2-base", trust_remote_code=True
                )
        return self._florence_model, self._florence_processor



    def get_rembg_session(self):
        if self._rembg_session is None:
            try:
                import rembg
                print("Initializing rembg cloth session...")
                self._rembg_session = rembg.new_session("u2net_cloth_seg")
            except Exception as e:
                print(f"Error loading rembg session: {e}")
        return self._rembg_session

    def get_yolo_world_model(self):
        if self._yolo_world_model is None:
            print("Loading YOLO-World (yolov8s-worldv2.pt)...")
            from ultralytics import YOLOWorld
            self._yolo_world_model = YOLOWorld("yolov8s-worldv2.pt")
        return self._yolo_world_model
