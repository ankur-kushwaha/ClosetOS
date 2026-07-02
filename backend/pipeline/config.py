import os
from pathlib import Path

from dotenv import load_dotenv

load_dotenv(Path(__file__).resolve().parent.parent / ".env")


def _env(key: str, default: str = "") -> str:
    value = os.getenv(key, default)
    if value and "#" in value:
        value = value.split("#", 1)[0]
    return value.strip()


# Detection: "yolo" (YOLO-World, default) or "florence"
DETECTION_MODEL = _env("DETECTION_MODEL", "yolo")

# Normalization: "gpt", "flux", "auto", or "none" (white-bg composite fallback)
NORMALIZATION_PROVIDER = _env("NORMALIZATION_PROVIDER", "none")

OPENAI_API_KEY = _env("OPENAI_API_KEY", "")
BFL_API_KEY = _env("BFL_API_KEY", "")
GEMINI_API_KEY = _env("GEMINI_API_KEY", "") or _env("GOOGLE_API_KEY", "")
TRY_ON_MODEL = _env("TRY_ON_MODEL", "gemini-3.1-flash-lite-image")
# "ai_studio" (default) or "vertex" — use vertex to bypass AI Studio image quota bug
TRY_ON_PROVIDER = _env("TRY_ON_PROVIDER", "ai_studio").lower()
GOOGLE_CLOUD_PROJECT = _env("GOOGLE_CLOUD_PROJECT", "adboard-booking")
GOOGLE_CLOUD_LOCATION = _env("GOOGLE_CLOUD_LOCATION", "us-central1")

NORMALIZATION_PROMPT = (
    "Create a professional e-commerce product photograph of this exact garment. "
    "Strictly preserve the exact neckline and collar shape (e.g., V-neck, round neck, polo collar, stand collar), "
    "fabric texture, patterns, colors, stitching, buttons, and proportions. Do not change the neckline type, "
    "redesign, or modify the garment structure. Place the garment against a clean, solid studio white background "
    "with bright, neutral lighting. Do not redesign, invent, or alter details."
)

GARMENT_DETECTION_PROMPT = (
    "dress . shirt . jacket . pants . shoes . coat . skirt . top . blouse . "
    "shorts . jeans . t-shirt . sweater . cardigan . blazer . hoodie . vest"
)

CLOTHING_WORDS = [
    "dress", "shirt", "jacket", "pants", "shoes", "coat", "skirt", "top",
    "blouse", "shorts", "jeans", "t-shirt", "suit", "cardigan", "sweater",
    "trousers", "sock", "blazer", "hoodie", "vest", "sneaker", "boot",
]

# Quality thresholds
MIN_DETECTION_CONFIDENCE = 0.10
MIN_MASK_COVERAGE = 0.25
MIN_CROP_PIXELS = 64
MIN_IMAGE_DIMENSION = 256

STORAGE_DIR = "storage"
GARMENTS_DIR = os.path.join(STORAGE_DIR, "garments")

_BACKEND_ROOT = Path(__file__).resolve().parent.parent
MODELS_DIR = _env("MODELS_DIR", str(_BACKEND_ROOT / ".models"))
YOLO_MODEL = _env("YOLO_MODEL", "yolov8s-worldv2.pt")
REALESRGAN_MODEL = _env("REALESRGAN_MODEL", "RealESRGAN_x4plus.pth")
REALESRGAN_URL = _env(
    "REALESRGAN_URL",
    "https://github.com/xinntao/Real-ESRGAN/releases/download/v0.1.0/RealESRGAN_x4plus.pth",
)
