"""E-commerce normalization via GPT Image, OpenRouter, or FLUX Kontext.

All public functions return RGBA images with transparent backgrounds (PNG).
The AI model produces a clean white-bg RGB image; rembg then strips the
background to restore transparency.
"""

import io
import base64
import os
from typing import Optional
from PIL import Image

from .config import NORMALIZATION_PROVIDER, NORMALIZATION_PROMPT, OPENAI_API_KEY, BFL_API_KEY

# ── rembg session (lazy, module-level singleton) ──────────────────────────────
_rembg_session = None


def _get_rembg_session():
    global _rembg_session
    if _rembg_session is None:
        try:
            from rembg import new_session
            _rembg_session = new_session("u2net")
            print("rembg: u2net session initialised")
        except Exception as e:
            print(f"rembg unavailable: {e}")
            _rembg_session = False  # sentinel — don't retry
    return _rembg_session if _rembg_session is not False else None


def remove_background(image: Image.Image) -> Image.Image:
    """Return RGBA image with background removed via rembg.

    Falls back to the original image (converted to RGBA) if rembg fails.
    """
    session = _get_rembg_session()
    if session is None:
        return image.convert("RGBA")

    try:
        from rembg import remove as rembg_remove
        buf = io.BytesIO()
        image.convert("RGBA").save(buf, format="PNG")
        result_bytes = rembg_remove(buf.getvalue(), session=session)
        return Image.open(io.BytesIO(result_bytes)).convert("RGBA")
    except Exception as e:
        print(f"rembg remove failed: {e}")
        return image.convert("RGBA")


def composite_white_background(masked_rgba: Image.Image) -> Image.Image:
    """Flatten RGBA onto white — used only for thumbnails & legacy consumers."""
    background = Image.new("RGBA", masked_rgba.size, (255, 255, 255, 255))
    return Image.alpha_composite(background, masked_rgba.convert("RGBA")).convert("RGB")


def normalize_garment(
    masked_rgba: Image.Image,
    label: str = "garment",
    custom_prompt: Optional[str] = None,
) -> tuple[Image.Image, str]:
    """
    Returns (normalized_rgba_image, provider_used).

    The returned image is always RGBA with a transparent background (PNG-safe).
    Tries configured AI provider first, then falls back to rembg on the original
    SAM-masked input.
    """
    prompt = custom_prompt if custom_prompt is not None else NORMALIZATION_PROMPT
    if "{label}" in prompt:
        prompt = prompt.format(label=label)
    elif "this exact garment" in prompt:
        prompt = prompt.replace("this exact garment", f"this exact {label}")

    if NORMALIZATION_PROVIDER == "openrouter":
        api_key = os.getenv("OPENROUTER_API_KEY") or OPENAI_API_KEY
        if api_key:
            result = _normalize_openrouter_image(masked_rgba, prompt)
            if result is not None:
                # AI produced white-bg RGB — strip bg → RGBA transparent
                transparent = remove_background(result)
                return transparent, "openrouter"

    if NORMALIZATION_PROVIDER == "gpt" and OPENAI_API_KEY:
        result = _normalize_gpt_image(masked_rgba, prompt)
        if result is not None:
            transparent = remove_background(result)
            return transparent, "gpt-image"

    # Fallback: rembg directly on the SAM masked image
    transparent = remove_background(masked_rgba)
    return transparent, "rembg-fallback"


def _normalize_openrouter_image(masked_rgba: Image.Image, prompt: str) -> Optional[Image.Image]:
    """Returns RGB image (white bg) from OpenRouter — caller applies rembg."""
    try:
        from openai import OpenAI
        import re

        api_key = os.getenv("OPENROUTER_API_KEY") or OPENAI_API_KEY
        if not api_key:
            print("No API key configured for OpenRouter / OpenAI")
            return None

        client = OpenAI(
            base_url="https://openrouter.ai/api/v1",
            api_key=api_key,
        )

        buf = io.BytesIO()
        masked_rgba.save(buf, format="PNG")
        b64_img = base64.b64encode(buf.getvalue()).decode("utf-8")

        messages = [
            {
                "role": "user",
                "content": [
                    {"type": "text", "text": prompt},
                    {
                        "type": "image_url",
                        "image_url": {
                            "url": f"data:image/png;base64,{b64_img}"
                        }
                    }
                ]
            }
        ]

        response = client.chat.completions.create(
            model="google/gemini-3.1-flash-lite-image",
            messages=messages,
            extra_body={
                "modalities": ["image", "text"]
            }
        )

        if response.choices and len(response.choices) > 0:
            message = response.choices[0].message

            message_dict = message.model_dump()
            images = message_dict.get("images") or getattr(message, "images", None)
            if images and len(images) > 0:
                for img_obj in images:
                    if isinstance(img_obj, dict):
                        url = img_obj.get("image_url", {}).get("url", "")
                        match = re.search(r"data:image/(?:png|jpeg|webp);base64,([A-Za-z0-9+/=]+)", url)
                        if match:
                            img_bytes = base64.b64decode(match.group(1))
                            return Image.open(io.BytesIO(img_bytes)).convert("RGB")

            content = message.content
            if content:
                if isinstance(content, list):
                    for part in content:
                        if isinstance(part, dict) and part.get("type") == "image_url":
                            url = part.get("image_url", {}).get("url", "")
                            match = re.search(r"data:image/(?:png|jpeg|webp);base64,([A-Za-z0-9+/=]+)", url)
                            if match:
                                img_bytes = base64.b64decode(match.group(1))
                                return Image.open(io.BytesIO(img_bytes)).convert("RGB")
                elif isinstance(content, str):
                    match = re.search(r"data:image/(?:png|jpeg|webp);base64,([A-Za-z0-9+/=]+)", content)
                    if match:
                        img_bytes = base64.b64decode(match.group(1))
                        return Image.open(io.BytesIO(img_bytes)).convert("RGB")
    except Exception as e:
        print(f"OpenRouter normalization failed: {e}")
        import traceback
        traceback.print_exc()
    return None


def _normalize_gpt_image(masked_rgba: Image.Image, prompt: str) -> Optional[Image.Image]:
    """Returns RGB image (white bg) from GPT Image — caller applies rembg."""
    try:
        from openai import OpenAI

        client = OpenAI(api_key=OPENAI_API_KEY)
        buf = io.BytesIO()
        masked_rgba.save(buf, format="PNG")
        buf.seek(0)
        buf.name = "garment.png"

        response = client.images.edit(
            model="gpt-image-1",
            image=buf,
            prompt=prompt,
            size="1024x1024",
        )
        if response.data and len(response.data) > 0:
            item = response.data[0]
            if item.b64_json:
                img_bytes = base64.b64decode(item.b64_json)
                return Image.open(io.BytesIO(img_bytes)).convert("RGB")
            if item.url:
                import urllib.request
                with urllib.request.urlopen(item.url) as resp:
                    return Image.open(io.BytesIO(resp.read())).convert("RGB")
    except Exception as e:
        print(f"GPT Image normalization failed: {e}")
    return None


def _normalize_flux_kontext(masked_rgba: Image.Image, prompt: str) -> Optional[Image.Image]:
    """Returns RGB image (white bg) from FLUX Kontext — caller applies rembg."""
    try:
        import json
        import urllib.request

        buf = io.BytesIO()
        composite_white_background(masked_rgba).save(buf, format="PNG")
        b64 = base64.b64encode(buf.getvalue()).decode("utf-8")

        payload = json.dumps({
            "prompt": prompt,
            "input_image": b64,
            "aspect_ratio": "1:1",
            "output_format": "png",
            "safety_tolerance": 2,
        }).encode("utf-8")

        req = urllib.request.Request(
            "https://api.bfl.ai/v1/flux-kontext-pro",
            data=payload,
            headers={
                "Content-Type": "application/json",
                "x-key": BFL_API_KEY,
            },
            method="POST",
        )
        with urllib.request.urlopen(req, timeout=120) as resp:
            result = json.loads(resp.read().decode("utf-8"))

        polling_url = result.get("polling_url")
        if not polling_url:
            return None

        import time
        for _ in range(60):
            time.sleep(2)
            poll_req = urllib.request.Request(
                polling_url,
                headers={"x-key": BFL_API_KEY},
            )
            with urllib.request.urlopen(poll_req, timeout=30) as poll_resp:
                poll_result = json.loads(poll_resp.read().decode("utf-8"))
            status = poll_result.get("status")
            if status == "Ready":
                sample_url = poll_result.get("result", {}).get("sample")
                if sample_url:
                    with urllib.request.urlopen(sample_url) as img_resp:
                        return Image.open(io.BytesIO(img_resp.read())).convert("RGB")
                break
            if status in ("Error", "Failed"):
                break
    except Exception as e:
        print(f"FLUX Kontext normalization failed: {e}")
    return None
