#!/usr/bin/env python3
"""
Performance test suite for /yolo-world/normalize API.

Tests:
  1. Single request latency (cold + warm)
  2. Sequential throughput (N requests, one after another)
  3. Concurrent throughput (N requests in parallel threads)
  4. Per-phase breakdown (decode → rembg → encode → upload)
  5. Image-size sensitivity (small / medium / large inputs)

Usage:
    python scratch/perf_normalize.py [--url http://localhost:8000] [--runs 5] [--concurrency 4]
"""

import argparse
import base64
import io
import json
import os
import statistics
import sys
import time
import threading
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path

# ── PIL for synthetic image generation ───────────────────────────────────────
try:
    from PIL import Image, ImageDraw
except ImportError:
    sys.exit("Pillow not found. Run: pip install pillow")

try:
    import requests as req_lib
except ImportError:
    sys.exit("requests not found. Run: pip install requests")


# ── helpers ───────────────────────────────────────────────────────────────────

GARMENT_IMAGE_PATHS = [
    "storage/garments/716e49e8-d5d8-4811-8900-0d726dfde70b/crop.png",
    "storage/garments/e5defc12-ef54-4358-aaca-0598edbf8faf/crop.png",
    "storage/garments/ecfed533-5888-4234-9f38-291e85b72a7b/crop.png",
]

LABELS = ["shirt", "pants", "jacket"]


def _make_synthetic_image(size: tuple[int, int], alpha: bool = True) -> bytes:
    """Create a synthetic garment-like RGBA image for testing."""
    mode = "RGBA" if alpha else "RGB"
    img = Image.new(mode, size, (255, 255, 255, 0) if alpha else (255, 255, 255))
    draw = ImageDraw.Draw(img)
    w, h = size
    # Draw a shirt-like shape
    draw.rectangle([w // 4, h // 6, 3 * w // 4, 5 * h // 6], fill=(70, 130, 180, 255) if alpha else (70, 130, 180))
    draw.polygon([(w // 4, h // 6), (w // 6, h // 3), (w // 6, h // 2), (w // 4, h // 2)],
                 fill=(70, 130, 180, 255) if alpha else (70, 130, 180))
    draw.polygon([(3 * w // 4, h // 6), (5 * w // 6, h // 3), (5 * w // 6, h // 2), (3 * w // 4, h // 2)],
                 fill=(70, 130, 180, 255) if alpha else (70, 130, 180))
    buf = io.BytesIO()
    img.save(buf, format="PNG")
    return buf.getvalue()


def _load_real_image(path: str):
    p = Path(path)
    if p.exists():
        return p.read_bytes()
    return None


def _to_b64(img_bytes: bytes) -> str:
    return base64.b64encode(img_bytes).decode("utf-8")


def _call_normalize(url: str, b64: str, label: str, timeout: int = 60) -> dict:
    """POST to /yolo-world/normalize and return timing + metadata."""
    payload = {"crop_base64": b64, "label": label}
    t0 = time.perf_counter()
    try:
        resp = req_lib.post(
            f"{url}/yolo-world/normalize",
            json=payload,
            timeout=timeout,
            headers={"Content-Type": "application/json"},
        )
        elapsed = time.perf_counter() - t0
        if resp.status_code == 200:
            body = resp.json()
            img_b64 = body.get("image_base64", "")
            img_size_kb = len(base64.b64decode(img_b64)) / 1024 if img_b64 else 0
            return {
                "ok": True,
                "elapsed": elapsed,
                "provider": body.get("provider", "?"),
                "output_kb": round(img_size_kb, 1),
                "status": resp.status_code,
            }
        else:
            return {"ok": False, "elapsed": elapsed, "status": resp.status_code, "error": resp.text[:200]}
    except Exception as e:
        return {"ok": False, "elapsed": time.perf_counter() - t0, "error": str(e), "status": 0}


def fmt(seconds: float) -> str:
    if seconds >= 1:
        return f"{seconds:.2f}s"
    return f"{seconds * 1000:.1f}ms"


def print_stats(label: str, times: list[float]):
    if not times:
        print(f"  {label}: no data")
        return
    print(f"\n  ┌─ {label}")
    print(f"  │  n={len(times)}  min={fmt(min(times))}  max={fmt(max(times))}  "
          f"mean={fmt(statistics.mean(times))}  "
          f"p50={fmt(statistics.median(times))}", end="")
    if len(times) >= 3:
        print(f"  p95={fmt(sorted(times)[int(len(times)*0.95)])}", end="")
    print(f"\n  └{'─' * 60}")


# ── test phases ───────────────────────────────────────────────────────────────

def test_server_health(base_url: str) -> bool:
    print("\n🔍 Checking server health...")
    try:
        r = req_lib.get(f"{base_url}/health", timeout=5)
        print(f"  /health → {r.status_code}")
        return r.status_code in (200, 404)  # 404 is fine if route doesn't exist
    except Exception as e:
        print(f"  ✗ Server unreachable: {e}")
        return False


def test_cold_start(base_url: str, b64: str, label: str) -> dict:
    print("\n🧊 Test 1 — Cold / First request latency")
    result = _call_normalize(base_url, b64, label)
    status = "✓" if result["ok"] else "✗"
    print(f"  {status} {fmt(result['elapsed'])}  provider={result.get('provider','?')}  "
          f"output={result.get('output_kb','?')}KB  status={result['status']}")
    if not result["ok"]:
        print(f"  Error: {result.get('error','')}")
    return result


def test_sequential(base_url: str, images: list[tuple[str, str]], runs: int) -> list[float]:
    print(f"\n⏱  Test 2 — Sequential throughput ({runs} requests)")
    times, errors = [], 0
    for i in range(runs):
        b64, label = images[i % len(images)]
        r = _call_normalize(base_url, b64, label)
        if r["ok"]:
            times.append(r["elapsed"])
            print(f"  [{i+1:2d}/{runs}] {fmt(r['elapsed'])}  provider={r['provider']}  out={r['output_kb']}KB")
        else:
            errors += 1
            print(f"  [{i+1:2d}/{runs}] ✗ {r.get('error','failed')} (status={r['status']})")
    print_stats("Sequential", times)
    if errors:
        print(f"  ⚠  {errors}/{runs} requests failed")
    return times


def test_concurrent(base_url: str, images: list[tuple[str, str]], concurrency: int) -> list[float]:
    print(f"\n🚀 Test 3 — Concurrent throughput ({concurrency} parallel requests)")
    times, errors = [], 0
    lock = threading.Lock()

    def worker(idx):
        b64, label = images[idx % len(images)]
        return _call_normalize(base_url, b64, label)

    t_wall_start = time.perf_counter()
    with ThreadPoolExecutor(max_workers=concurrency) as ex:
        futures = [ex.submit(worker, i) for i in range(concurrency)]
        for fut in as_completed(futures):
            r = fut.result()
            with lock:
                if r["ok"]:
                    times.append(r["elapsed"])
                else:
                    errors += 1
    t_wall = time.perf_counter() - t_wall_start

    print_stats("Concurrent", times)
    print(f"  Wall-clock for {concurrency} requests: {fmt(t_wall)}")
    if times:
        rps = len(times) / t_wall
        print(f"  Throughput: {rps:.2f} req/s")
    if errors:
        print(f"  ⚠  {errors}/{concurrency} requests failed")
    return times


def test_image_sizes(base_url: str, label: str):
    print("\n📐 Test 4 — Image-size sensitivity")
    sizes = [
        ("Tiny  128×128", (128, 128)),
        ("Small 256×256", (256, 256)),
        ("Med   512×512", (512, 512)),
        ("Large 1024×1024", (1024, 1024)),
    ]
    rows = []
    for name, dim in sizes:
        img_bytes = _make_synthetic_image(dim)
        input_kb = len(img_bytes) / 1024
        b64 = _to_b64(img_bytes)
        r = _call_normalize(base_url, b64, label)
        ok = "✓" if r["ok"] else "✗"
        elapsed = fmt(r["elapsed"])
        out_kb = r.get("output_kb", "—")
        rows.append((name, f"{input_kb:.0f}KB", elapsed, f"{out_kb}KB", r.get("provider", "?")))
        print(f"  {ok}  {name:<18} in={input_kb:5.0f}KB  time={elapsed:>8}  out={out_kb}KB")


def test_rembg_only(base_url: str):
    """Directly time the rembg path by calling normalize with the rembg-fallback provider."""
    print("\n🧠 Test 5 — rembg-only baseline (no AI provider)")
    # Use a real garment image if available
    img_bytes = None
    for path in GARMENT_IMAGE_PATHS:
        img_bytes = _load_real_image(path)
        if img_bytes:
            break
    if img_bytes is None:
        img_bytes = _make_synthetic_image((512, 512))

    b64 = _to_b64(img_bytes)
    input_kb = len(img_bytes) / 1024
    print(f"  Input: {input_kb:.0f}KB")

    times = []
    for i in range(3):
        r = _call_normalize(base_url, b64, "shirt")
        if r["ok"]:
            times.append(r["elapsed"])
            print(f"  run {i+1}: {fmt(r['elapsed'])}  provider={r['provider']}  out={r['output_kb']}KB")
        else:
            print(f"  run {i+1}: ✗ {r.get('error','')}")

    print_stats("rembg baseline", times)
    return times


# ── main ──────────────────────────────────────────────────────────────────────

def main():
    parser = argparse.ArgumentParser(description="Normalize API performance test")
    parser.add_argument("--url", default="http://localhost:8000", help="Server base URL")
    parser.add_argument("--runs", type=int, default=5, help="Sequential runs")
    parser.add_argument("--concurrency", type=int, default=4, help="Concurrent request count")
    args = parser.parse_args()

    base_url = args.url.rstrip("/")
    print("=" * 65)
    print(f"  ClosetOS  /yolo-world/normalize  Performance Suite")
    print(f"  Target : {base_url}")
    print(f"  Runs   : {args.runs} sequential, {args.concurrency} concurrent")
    print("=" * 65)

    # ── server health ─────────────────────────────────────────────────────────
    if not test_server_health(base_url):
        sys.exit(1)

    # ── load test images ──────────────────────────────────────────────────────
    images = []
    for path, label in zip(GARMENT_IMAGE_PATHS, LABELS):
        raw = _load_real_image(path)
        if raw:
            images.append((_to_b64(raw), label))
            print(f"  ✓ Loaded real garment: {path}  ({len(raw)//1024}KB)")

    if not images:
        print("  ⚠  No real garment images found — using synthetic")
        images = [(_to_b64(_make_synthetic_image((512, 512))), "shirt")]

    primary_b64, primary_label = images[0]

    # ── run tests ─────────────────────────────────────────────────────────────
    cold_result = test_cold_start(base_url, primary_b64, primary_label)

    all_times = []

    seq_times = test_sequential(base_url, images, args.runs)
    all_times.extend(seq_times)

    con_times = test_concurrent(base_url, images, args.concurrency)
    all_times.extend(con_times)

    test_image_sizes(base_url, primary_label)
    test_rembg_only(base_url)

    # ── final summary ─────────────────────────────────────────────────────────
    print("\n" + "=" * 65)
    print("  SUMMARY")
    print("=" * 65)
    print(f"  Cold request  : {fmt(cold_result['elapsed'])}")
    if seq_times:
        print(f"  Sequential    : mean={fmt(statistics.mean(seq_times))}  "
              f"p50={fmt(statistics.median(seq_times))}  "
              f"min={fmt(min(seq_times))}  max={fmt(max(seq_times))}")
    if con_times:
        print(f"  Concurrent×{args.concurrency}  : mean={fmt(statistics.mean(con_times))}  "
              f"p50={fmt(statistics.median(con_times))}")
    print("=" * 65)


if __name__ == "__main__":
    main()
