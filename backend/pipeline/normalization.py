"""E-commerce normalization via GPT Image or FLUX Kontext, with white-bg fallback."""

import io
import base64
import os
from typing import Optional
from PIL import Image

from .config import NORMALIZATION_PROVIDER, NORMALIZATION_PROMPT, OPENAI_API_KEY, BFL_API_KEY


def composite_white_background(masked_rgba: Image.Image) -> Image.Image:
    background = Image.new("RGBA", masked_rgba.size, (255, 255, 255, 255))
    return Image.alpha_composite(background, masked_rgba).convert("RGB")


def normalize_garment(
    masked_rgba: Image.Image,
    label: str = "garment",
    custom_prompt: Optional[str] = None
) -> tuple[Image.Image, str]:
    """
    Returns (normalized_rgb_image, provider_used).
    Tries configured provider, falls back to white-bg composite.
    """
    white_bg = composite_white_background(masked_rgba)
    prompt = custom_prompt if custom_prompt is not None else NORMALIZATION_PROMPT

    if NORMALIZATION_PROVIDER == "gpt" and OPENAI_API_KEY:
        result = _normalize_gpt_image(masked_rgba, prompt)
        if result is not None:
            return result, "gpt-image"

    if NORMALIZATION_PROVIDER == "flux" and BFL_API_KEY:
        result = _normalize_flux_kontext(masked_rgba, prompt)
        if result is not None:
            return result, "flux-kontext"

    # Auto-try GPT if key present but provider not explicitly set
    if NORMALIZATION_PROVIDER == "auto" and OPENAI_API_KEY:
        result = _normalize_gpt_image(masked_rgba, prompt)
        if result is not None:
            return result, "gpt-image"
    if NORMALIZATION_PROVIDER == "auto" and BFL_API_KEY:
        result = _normalize_flux_kontext(masked_rgba, prompt)
        if result is not None:
            return result, "flux-kontext"

    return white_bg, "white-bg-fallback"


def _normalize_gpt_image(masked_rgba: Image.Image, prompt: str) -> Optional[Image.Image]:
    try:
        from openai import OpenAI

        client = OpenAI(api_key=OPENAI_API_KEY)
        buf = io.BytesIO()
        # Keep transparency so DALL-E knows where to perform background editing/inpainting
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
