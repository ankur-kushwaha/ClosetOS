import os
from pathlib import Path

from dotenv import load_dotenv

load_dotenv(Path(__file__).resolve().parent.parent / ".env")


def _env(key: str, default: str = "") -> str:
    value = os.getenv(key, default)
    if value and "#" in value:
        value = value.split("#", 1)[0]
    return value.strip()


# Detection: "dino" (Grounding DINO) or "florence"
DETECTION_MODEL = _env("DETECTION_MODEL", "dino")

# Normalization: "gpt", "flux", "auto", or "none" (white-bg composite fallback)
NORMALIZATION_PROVIDER = _env("NORMALIZATION_PROVIDER", "none")

OPENAI_API_KEY = _env("OPENAI_API_KEY", "")
BFL_API_KEY = _env("BFL_API_KEY", "")

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
