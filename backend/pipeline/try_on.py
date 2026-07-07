"""Virtual try-on via Gemini 3.1 Flash Lite Image."""

import base64
import os
from typing import List, Optional

from .config import (
    GEMINI_API_KEY,
    GOOGLE_CLOUD_LOCATION,
    GOOGLE_CLOUD_PROJECT,
    TRY_ON_MODEL,
    TRY_ON_PROVIDER,
    OPENAI_API_KEY,
)
from .orchestrator import map_to_try_on_category, map_to_try_on_subcategory

GARMENT_LAYER_ORDER = {
    "lower_body": 0,
    "full_body": 1,
    "upper_body": 2,
    "footwear": 3,
    "accessory": 4,
}


class TryOnError(Exception):
    """User-facing try-on failure."""

    def __init__(self, message: str, status_code: int = 503):
        super().__init__(message)
        self.status_code = status_code


def _mime_for_image(data: bytes) -> str:
    if data[:8] == b"\x89PNG\r\n\x1a\n":
        return "image/png"
    if data[:2] == b"\xff\xd8":
        return "image/jpeg"
    return "image/jpeg"


def _decode_image(b64: str) -> tuple:
    raw = base64.b64decode(b64)
    return raw, _mime_for_image(raw)


def _build_try_on_prompt(garments: list) -> str:
    lines = []
    has_full_body = any(
        map_to_try_on_category(g.get("category", "")) == "full_body" for g in garments
    )
    for g in garments:
        cat = map_to_try_on_category(g.get("category", ""))
        sub = map_to_try_on_subcategory(g.get("subcategory", ""))
        color = g.get("colorName", "")
        lines.append(f"- {color} {sub} ({cat})")

    garment_list = "\n".join(lines)
    dress_note = (
        "IMPORTANT: The outfit contains a dress (full-body garment). "
        "Do NOT add any separate top or bottom garment. The dress covers both upper and lower body.\n\n"
        if has_full_body else ""
    )
    return (
        "You are a virtual try-on assistant. The first image is a selfie of the person — treat their face as sacred "
        "and do NOT alter it in any way (no smoothing, reshaping, recoloring, or any modification). "
        "Only the clothing area should change. Skin tone, pose, and background must remain identical to the selfie. "
        "You MAY subtly restyle the hair to complement the outfit (e.g. adjust style or volume), but do not change the hair color.\n\n"
        "Replace the person's current clothing with EXACTLY the garments shown in the reference images. "
        "CRITICAL: Do NOT change the color of any garment. Use the exact same color, pattern, texture, and silhouette "
        "as shown in each reference image. Do not recolor, reinterpret, or approximate the garment colors. "
        "Fit each garment naturally on the person's body with correct layering "
        "(lower body first, then full-body dress if present, then upper body, then outerwear, shoes on feet). "
        "Do not add extra clothing, accessories, or decorative elements not present in the references.\n\n"
        f"{dress_note}"
        f"Outfit to apply:\n{garment_list}"
    )


def _normalize_inline_image_base64(data) -> Optional[str]:
    """Return a single-layer base64 image string from Gemini inline bytes or text."""
    if isinstance(data, str):
        return data if len(data) >= 100 else None
    if not isinstance(data, (bytes, bytearray)):
        return None
    if len(data) < 100:
        return None
    if data[:2] == b"\xff\xd8" or data[:8] == b"\x89PNG\r\n\x1a\n":
        return base64.b64encode(data).decode("utf-8")
    try:
        text = data.decode("ascii")
    except UnicodeDecodeError:
        return base64.b64encode(data).decode("utf-8")
    if text.startswith("/9j/") or text.startswith("iVBORw0KGgo"):
        return text
    return base64.b64encode(data).decode("utf-8")


def _extract_image_base64(response) -> str:
    parts = list(response.parts or [])
    if not parts and response.candidates:
        content = response.candidates[0].content
        if content and content.parts:
            parts = list(content.parts)

    for part in parts:
        if part.inline_data and part.inline_data.data:
            encoded = _normalize_inline_image_base64(part.inline_data.data)
            if encoded:
                return encoded
        try:
            image = part.as_image()
        except Exception:
            image = None
        if image is not None:
            import io

            buf = io.BytesIO()
            image.save(buf, format="PNG")
            encoded = base64.b64encode(buf.getvalue()).decode("utf-8")
            if len(encoded) >= 100:
                return encoded
    raise RuntimeError("Gemini try-on returned no image")


def _is_free_tier_image_bug(exc: Exception) -> bool:
    msg = str(exc).lower()
    return "free_tier" in msg and TRY_ON_MODEL.lower() in msg


def _quota_user_message() -> str:
    return (
        f"Gemini image quota blocked for {TRY_ON_MODEL}. "
        "Your billing is active but Google is still routing image requests to "
        "free_tier (limit 0) — a known Paid Tier 1 bug. "
        "Fix options: (1) wait and retry later, (2) recreate your API key in AI Studio, "
        "(3) set TRY_ON_PROVIDER=vertex with a service account on project "
        f"{GOOGLE_CLOUD_PROJECT}. See GET /health/check for details."
    )


def _map_client_error(exc: Exception) -> TryOnError:
    from google.genai import errors

    if isinstance(exc, errors.ClientError):
        if exc.code == 429 and _is_free_tier_image_bug(exc):
            return TryOnError(_quota_user_message(), status_code=429)
        if exc.code == 429:
            return TryOnError(
                f"Gemini rate limit (429). Retry in ~30s. Details: {exc.message}",
                status_code=429,
            )
        if exc.code == 401 or exc.code == 403:
            return TryOnError(
                f"Gemini auth error ({exc.code}): {exc.message}",
                status_code=exc.code,
            )
        return TryOnError(f"Gemini API error ({exc.code}): {exc.message}", status_code=exc.code)
    return TryOnError(f"Try-on failed: {exc}", status_code=503)


def _make_client():
    from google import genai

    if TRY_ON_PROVIDER == "vertex":
        creds_path = os.getenv("GOOGLE_APPLICATION_CREDENTIALS", "")
        if not creds_path or not os.path.isfile(creds_path):
            raise TryOnError(
                "TRY_ON_PROVIDER=vertex requires GOOGLE_APPLICATION_CREDENTIALS "
                f"pointing to a service account JSON for project {GOOGLE_CLOUD_PROJECT}.",
                status_code=400,
            )
        return genai.Client(
            vertexai=True,
            project=GOOGLE_CLOUD_PROJECT,
            location=GOOGLE_CLOUD_LOCATION,
        )

    if not GEMINI_API_KEY:
        raise ValueError("GEMINI_API_KEY is not configured")
    return genai.Client(api_key=GEMINI_API_KEY)


def _render_openrouter_try_on(person_image_base64: str, garments: List[dict]) -> dict:
    from openai import OpenAI
    import re

    api_key = os.getenv("OPENROUTER_API_KEY") or OPENAI_API_KEY
    if not api_key:
        raise ValueError("OPENROUTER_API_KEY or OPENAI_API_KEY is not configured")

    client = OpenAI(
        base_url="https://openrouter.ai/api/v1",
        api_key=api_key,
    )

    def sort_key(g: dict) -> int:
        cat = map_to_try_on_category(g.get("category", ""))
        return GARMENT_LAYER_ORDER.get(cat, 99)

    sorted_garments = sorted(garments, key=sort_key)
    person_bytes, person_mime = _decode_image(person_image_base64)

    person_uri = f"data:{person_mime};base64,{person_image_base64}"

    content_parts = [
        {"type": "text", "text": _build_try_on_prompt(sorted_garments)},
        {
            "type": "image_url",
            "image_url": {
                "url": person_uri
            }
        }
    ]

    for garment in sorted_garments:
        img_b64 = garment.get("image_base64")
        if not img_b64:
            continue
        g_bytes, g_mime = _decode_image(img_b64)
        content_parts.append({
            "type": "image_url",
            "image_url": {
                "url": f"data:{g_mime};base64,{img_b64}"
            }
        })

    messages = [
        {
            "role": "user",
            "content": content_parts
        }
    ]

    response = client.chat.completions.create(
        model=TRY_ON_MODEL,
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
                        return {
                            "image_base64": match.group(1),
                            "provider": f"openrouter:{TRY_ON_MODEL}",
                            "garment_count": len(sorted_garments)
                        }

        content = message.content
        if content:
            if isinstance(content, list):
                for part in content:
                    if isinstance(part, dict) and part.get("type") == "image_url":
                        url = part.get("image_url", {}).get("url", "")
                        match = re.search(r"data:image/(?:png|jpeg|webp);base64,([A-Za-z0-9+/=]+)", url)
                        if match:
                            return {
                                "image_base64": match.group(1),
                                "provider": f"openrouter:{TRY_ON_MODEL}",
                                "garment_count": len(sorted_garments)
                            }
            elif isinstance(content, str):
                match = re.search(r"data:image/(?:png|jpeg|webp);base64,([A-Za-z0-9+/=]+)", content)
                if match:
                    return {
                        "image_base64": match.group(1),
                        "provider": f"openrouter:{TRY_ON_MODEL}",
                        "garment_count": len(sorted_garments)
                    }

    raise RuntimeError("OpenRouter try-on returned no image")


def render_try_on(person_image_base64: str, garments: List[dict]) -> dict:
    if not garments:
        raise ValueError("At least one garment is required")

    if TRY_ON_PROVIDER == "openrouter":
        return _render_openrouter_try_on(person_image_base64, garments)

    if TRY_ON_PROVIDER != "vertex" and not GEMINI_API_KEY:
        raise ValueError("GEMINI_API_KEY is not configured")

    from google.genai import types

    def sort_key(g: dict) -> int:
        cat = map_to_try_on_category(g.get("category", ""))
        return GARMENT_LAYER_ORDER.get(cat, 99)

    sorted_garments = sorted(garments, key=sort_key)
    person_bytes, person_mime = _decode_image(person_image_base64)

    contents = [
        types.Part.from_text(text=_build_try_on_prompt(sorted_garments)),
        types.Part.from_bytes(data=person_bytes, mime_type=person_mime),
    ]

    for garment in sorted_garments:
        img_b64 = garment.get("image_base64")
        if not img_b64:
            continue
        g_bytes, g_mime = _decode_image(img_b64)
        contents.append(types.Part.from_bytes(data=g_bytes, mime_type=g_mime))

    client = _make_client()
    provider_label = (
        f"vertex:{GOOGLE_CLOUD_PROJECT}/{TRY_ON_MODEL}"
        if TRY_ON_PROVIDER == "vertex"
        else TRY_ON_MODEL
    )

    try:
        response = client.models.generate_content(
            model=TRY_ON_MODEL,
            contents=contents,
            config=types.GenerateContentConfig(
                response_modalities=["IMAGE"],
                image_config=types.ImageConfig(aspect_ratio="3:4"),
            ),
        )
    except TryOnError:
        raise
    except Exception as exc:
        raise _map_client_error(exc) from exc

    return {
        "image_base64": _extract_image_base64(response),
        "provider": provider_label,
        "garment_count": len(sorted_garments),
    }
