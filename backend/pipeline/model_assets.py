"""Resolve and download local ML weight files (not stored in git)."""

import urllib.request
from pathlib import Path

from .config import MODELS_DIR, REALESRGAN_MODEL, REALESRGAN_URL


def models_dir() -> Path:
    path = Path(MODELS_DIR)
    path.mkdir(parents=True, exist_ok=True)
    return path


def download_file(url: str, dest: Path) -> Path:
    if dest.exists():
        return dest
    print(f"Downloading {dest.name}...")
    dest.parent.mkdir(parents=True, exist_ok=True)
    urllib.request.urlretrieve(url, dest)
    print(f"Saved to {dest}")
    return dest


def ensure_realesrgan_weights() -> Path:
    return download_file(REALESRGAN_URL, models_dir() / REALESRGAN_MODEL)
