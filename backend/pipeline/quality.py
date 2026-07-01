"""Quality validation for segmented garment crops."""

from dataclasses import dataclass
from typing import Tuple
import numpy as np
from PIL import Image

from .config import MIN_MASK_COVERAGE, MIN_CROP_PIXELS, MIN_IMAGE_DIMENSION, MIN_DETECTION_CONFIDENCE


@dataclass
class QualityResult:
    passed: bool
    score: float
    reasons: list


def validate_garment(
    masked_rgba: Image.Image,
    detection_score: float,
    bbox: list,
    source_width: int,
    source_height: int,
) -> QualityResult:
    reasons = []
    scores = []

    w, h = masked_rgba.size
    if w < MIN_CROP_PIXELS or h < MIN_CROP_PIXELS:
        reasons.append(f"crop too small ({w}x{h})")
    else:
        scores.append(1.0)

    if source_width < MIN_IMAGE_DIMENSION or source_height < MIN_IMAGE_DIMENSION:
        reasons.append(f"source image low resolution ({source_width}x{source_height})")
        scores.append(0.3)
    else:
        scores.append(1.0)

    if detection_score < MIN_DETECTION_CONFIDENCE:
        reasons.append(f"low detection confidence ({detection_score:.2f})")
        scores.append(detection_score / max(MIN_DETECTION_CONFIDENCE, 0.01))
    else:
        scores.append(min(1.0, detection_score))

    alpha = np.array(masked_rgba.split()[-1])
    mask_pixels = np.sum(alpha > 128)
    bbox_area = max(1, (bbox[2] - bbox[0]) * (bbox[3] - bbox[1]))
    coverage = mask_pixels / bbox_area
    if coverage < MIN_MASK_COVERAGE:
        reasons.append(f"mask coverage too low ({coverage:.2f})")
        scores.append(coverage / MIN_MASK_COVERAGE)
    else:
        scores.append(min(1.0, coverage))

  # Edge fragmentation: ratio of contour perimeter to sqrt(area)
    import cv2
    contours, _ = cv2.findContours(
        (alpha > 128).astype(np.uint8), cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE
    )
    if contours:
        largest = max(contours, key=cv2.contourArea)
        area = cv2.contourArea(largest)
        perimeter = cv2.arcLength(largest, True)
        if area > 0:
            complexity = perimeter / (np.sqrt(area) + 1e-6)
            if complexity > 25:
                reasons.append("fragmented mask edges")
                scores.append(0.5)
            else:
                scores.append(1.0)

    final_score = float(np.mean(scores)) if scores else 0.0
    passed = final_score >= 0.45 and len([r for r in reasons if "too small" in r or "too low" in r]) == 0

    return QualityResult(passed=passed, score=final_score, reasons=reasons)
