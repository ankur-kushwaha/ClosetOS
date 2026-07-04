"""Cloudflare R2 image storage (S3-compatible). Falls back to local disk."""

import logging
import os
from typing import Optional

from pipeline.config import (
    API_BASE_URL,
    R2_ACCESS_KEY_ID,
    R2_BUCKET_NAME,
    R2_ENDPOINT,
    R2_PUBLIC_URL,
    R2_SECRET_ACCESS_KEY,
    STORAGE_DIR,
)

logger = logging.getLogger(__name__)


def is_r2_configured() -> bool:
    return bool(
        R2_ENDPOINT
        and R2_ACCESS_KEY_ID
        and R2_SECRET_ACCESS_KEY
        and R2_BUCKET_NAME
        and R2_PUBLIC_URL
    )


def _r2_client():
    import boto3

    return boto3.client(
        "s3",
        endpoint_url=R2_ENDPOINT,
        aws_access_key_id=R2_ACCESS_KEY_ID,
        aws_secret_access_key=R2_SECRET_ACCESS_KEY,
        region_name="auto",
    )


def _save_local(
    data: bytes, user_id: str, garment_id: str, filename: str
) -> str:
    local_dir = os.path.join(STORAGE_DIR, "wardrobe", user_id, garment_id)
    os.makedirs(local_dir, exist_ok=True)
    local_path = os.path.join(local_dir, filename)
    with open(local_path, "wb") as f:
        f.write(data)
    return f"{API_BASE_URL}/wardrobe/images/{user_id}/{garment_id}/{filename}"


def upload_image(
    data: bytes,
    user_id: str,
    garment_id: str,
    filename: str,
    content_type: str = "image/png",
) -> str:
    """Upload bytes and return a public URL. Falls back to local disk on R2 errors."""
    key = f"users/{user_id}/garments/{garment_id}/{filename}"

    if is_r2_configured():
        try:
            client = _r2_client()
            client.put_object(
                Bucket=R2_BUCKET_NAME,
                Key=key,
                Body=data,
                ContentType=content_type,
            )
            return f"{R2_PUBLIC_URL}/{key}"
        except Exception as e:
            logger.warning(
                "R2 upload failed for bucket=%s key=%s (%s); using local fallback",
                R2_BUCKET_NAME,
                key,
                e,
            )

    return _save_local(data, user_id, garment_id, filename)


def delete_garment_images(user_id: str, garment_id: str) -> None:
    if is_r2_configured():
        try:
            client = _r2_client()
            prefix = f"users/{user_id}/garments/{garment_id}/"
            resp = client.list_objects_v2(Bucket=R2_BUCKET_NAME, Prefix=prefix)
            for obj in resp.get("Contents", []):
                client.delete_object(Bucket=R2_BUCKET_NAME, Key=obj["Key"])
        except Exception as e:
            logger.warning("R2 delete failed for %s/%s: %s", user_id, garment_id, e)

    local_dir = os.path.join(STORAGE_DIR, "wardrobe", user_id, garment_id)
    if os.path.isdir(local_dir):
        for name in os.listdir(local_dir):
            os.remove(os.path.join(local_dir, name))
        os.rmdir(local_dir)


def local_image_path(user_id: str, garment_id: str, filename: str) -> Optional[str]:
    path = os.path.join(STORAGE_DIR, "wardrobe", user_id, garment_id, filename)
    return path if os.path.isfile(path) else None


def check_r2() -> dict:
    """Return R2 connectivity diagnostics (no secrets)."""
    result = {
        "configured": is_r2_configured(),
        "endpoint": R2_ENDPOINT or None,
        "bucket": R2_BUCKET_NAME or None,
        "public_url": R2_PUBLIC_URL or None,
        "ok": False,
        "write_ok": False,
    }

    if not is_r2_configured():
        result["ok"] = True
        result["message"] = "R2 not configured — images use local disk fallback"
        return result

    try:
        client = _r2_client()
        client.head_bucket(Bucket=R2_BUCKET_NAME)
        result["bucket_reachable"] = True
    except Exception as e:
        result["bucket_reachable"] = False
        result["error"] = str(e)[:500]
        result["message"] = "R2 bucket not reachable — check endpoint, bucket name, and token scope"
        return result

    test_key = "_healthcheck/ping.txt"
    try:
        client.put_object(
            Bucket=R2_BUCKET_NAME,
            Key=test_key,
            Body=b"ok",
            ContentType="text/plain",
        )
        client.delete_object(Bucket=R2_BUCKET_NAME, Key=test_key)
        result["write_ok"] = True
        result["ok"] = True
        result["message"] = "R2 bucket reachable and writable"
    except Exception as e:
        result["error"] = str(e)[:500]
        result["message"] = (
            "R2 bucket reachable but write denied — token needs Object Read & Write "
            f"on bucket '{R2_BUCKET_NAME}'"
        )
    return result
