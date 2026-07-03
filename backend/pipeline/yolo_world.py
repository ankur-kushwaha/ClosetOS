"""YOLO-World Object Detection Wrapper."""

from typing import List, Tuple
from PIL import Image

from .config import YOLO_WORLD_VOCAB

def detect_yolo_world(
    image: Image.Image,
    device: str,
    model_loaders,
    confidence: float = 0.1,
) -> List[Tuple[List[float], str, float]]:
    """Detects garments (TopWear, BottomWear, Dress, Footwear) in an image using YOLO-World.
    
    Returns:
        List of tuples: (bbox, label, score) where bbox is [x1, y1, x2, y2]
    """
    model = model_loaders.get_yolo_world_model()

    # Only set classes if not already set (to avoid MPS PyTorch embedding bugs on re-setting)
    if list(model.names.values()) != YOLO_WORLD_VOCAB:
        print("Initializing YOLO-World classes...")
        model.set_classes(YOLO_WORLD_VOCAB)
    
    # Force CPU for YOLO-World on macOS (MPS) to avoid coordinate corruption bug in PyTorch MPS backend
    run_device = "cpu" if device == "mps" else device
    
    # Try predicting on the requested device, fallback to CPU on error (common with MPS compatibility issues)
    try:
        results = model.predict(image, device=run_device, conf=confidence, agnostic_nms=True, verbose=False)
    except Exception as e:
        print(f"YOLO-World prediction failed on device {run_device}, falling back to cpu: {e}")
        results = model.predict(image, device="cpu", conf=confidence, agnostic_nms=True, verbose=False)
        
    def map_label(lbl: str) -> str:
        lbl_l = lbl.lower()
        if any(w in lbl_l for w in ["pants", "jeans", "trousers", "shorts", "skirt"]):
            return "BottomWear"
        if any(w in lbl_l for w in ["top", "shirt", "t-shirt", "jacket", "sweater", "hoodie", "blouse"]):
            return "TopWear"
        if "dress" in lbl_l:
            return "Dress"
        if any(w in lbl_l for w in ["shoe", "sneaker", "boot", "loafer", "sandal", "heel"]):
            return "Footwear"
        return "Other"

    detected = []
    if len(results) > 0:
        boxes = results[0].boxes
        for box in boxes:
            # Get coords in float list
            xyxy = box.xyxy[0].cpu().numpy().tolist()
            conf = float(box.conf[0].cpu().numpy())
            cls_id = int(box.cls[0].cpu().numpy())
            raw_label = results[0].names[cls_id]
            mapped = map_label(raw_label)
            if mapped != "Other":
                detected.append((xyxy, mapped, conf))
            
    return detected

