"""Grounded-SAM-2: text-grounded detection + SAM segmentation per garment."""

from typing import List, Tuple
import numpy as np
import cv2
from PIL import Image
import torch

from .config import (
    DETECTION_MODEL,
    GARMENT_DETECTION_PROMPT,
    CLOTHING_WORDS,
    MIN_DETECTION_CONFIDENCE,
)


def filter_and_nms_garments(detected_items, width, height, max_garments: int = 8):
    filtered_spatially = []
    for item in detected_items:
        box = item[0]
        box_w = box[2] - box[0]
        gcx = (box[0] + box[2]) / 2
        if box_w > width * 0.85:
            continue
        if not (width * 0.05 <= gcx <= width * 0.95):
            continue
        filtered_spatially.append(item)

    def get_cat_group(lbl: str) -> str:
        lbl_l = lbl.lower()
        if any(w in lbl_l for w in ["pants", "shorts", "jeans", "skirt", "trousers"]):
            return "bottom"
        if any(w in lbl_l for w in ["top", "shirt", "blouse", "sweater", "t-shirt", "jacket", "coat", "cardigan", "suit", "hoodie", "vest", "blazer"]):
            return "top"
        if any(w in lbl_l for w in ["shoe", "sneaker", "boot", "loafer"]):
            return "shoes"
        return "other"

    filtered_spatially.sort(key=lambda x: x[2], reverse=True)
    nms_results = []
    for item in filtered_spatially:
        box = item[0]
        cat = get_cat_group(item[1])
        overlap = False
        for accepted in nms_results:
            ax1, ay1, ax2, ay2 = accepted[0]
            gx1, gy1, gx2, gy2 = box
            inter_x1 = max(ax1, gx1)
            inter_y1 = max(ay1, gy1)
            inter_x2 = min(ax2, gx2)
            inter_y2 = min(ay2, gy2)
            inter_area = max(0, inter_x2 - inter_x1) * max(0, inter_y2 - inter_y1)
            a_area = (ax2 - ax1) * (ay2 - ay1)
            g_area = (gx2 - gx1) * (gy2 - gy1)
            iou = inter_area / (a_area + g_area - inter_area) if (a_area + g_area - inter_area) > 0 else 0
            containment = inter_area / min(a_area, g_area) if min(a_area, g_area) > 0 else 0
            accepted_cat = get_cat_group(accepted[1])
            if iou > 0.85:
                overlap = True
                break
            if iou > 0.35 and accepted_cat == cat and cat != "other":
                overlap = True
                break
            if containment > 0.70 and accepted_cat == cat and cat != "other":
                overlap = True
                break
        if not overlap:
            nms_results.append(item)
    return nms_results[:max_garments]


def detect_garments(image: Image.Image, device: str, model_loaders) -> List[Tuple[list, str, float]]:
    width, height = image.size
    boxes_to_process = []

    if DETECTION_MODEL == "florence":
        fl_model, fl_processor = model_loaders.get_florence_model()
        task_prompt = "<CAPTION_TO_PHRASE_GROUNDING>"
        florence_inputs = fl_processor(
            text=task_prompt + GARMENT_DETECTION_PROMPT,
            images=image,
            return_tensors="pt",
        ).to(device)
        with torch.no_grad():
            generated_ids = fl_model.generate(
                input_ids=florence_inputs["input_ids"],
                pixel_values=florence_inputs["pixel_values"],
                max_new_tokens=1024,
                num_beams=3,
            )
        generated_text = fl_processor.batch_decode(generated_ids, skip_special_tokens=True)[0]
        od_results = fl_processor.post_process_generation(
            generated_text, task=task_prompt, image_size=image.size
        )
        parsed_od = od_results.get(task_prompt, {"bboxes": [], "labels": []})
        bboxes = parsed_od.get("bboxes", [])
        labels = parsed_od.get("labels", [])

        if len(bboxes) == 0:
            task_prompt = "<OD>"
            florence_inputs = fl_processor(text=task_prompt, images=image, return_tensors="pt").to(device)
            with torch.no_grad():
                generated_ids = fl_model.generate(
                    input_ids=florence_inputs["input_ids"],
                    pixel_values=florence_inputs["pixel_values"],
                    max_new_tokens=1024,
                    num_beams=3,
                )
            generated_text = fl_processor.batch_decode(generated_ids, skip_special_tokens=True)[0]
            od_results = fl_processor.post_process_generation(
                generated_text, task=task_prompt, image_size=image.size
            )
            parsed_od = od_results.get(task_prompt, {"bboxes": [], "labels": []})
            bboxes = parsed_od.get("bboxes", [])
            labels = parsed_od.get("labels", [])

        detected = []
        for box, label in zip(bboxes, labels):
            lbl = label.lower()
            is_clothing = any(word in lbl for word in CLOTHING_WORDS)
            if is_clothing or len(bboxes) <= 3:
                detected.append((box, label, 1.0))
        boxes_to_process = filter_and_nms_garments(detected, width, height)
    else:
        d_proc, d_mod = model_loaders.get_dino_model()
        inputs = d_proc(images=image, text=GARMENT_DETECTION_PROMPT, return_tensors="pt").to(device)
        with torch.no_grad():
            outputs = d_mod(**inputs)
        results = d_proc.post_process_grounded_object_detection(
            outputs,
            inputs.input_ids,
            box_threshold=MIN_DETECTION_CONFIDENCE,
            text_threshold=MIN_DETECTION_CONFIDENCE,
            target_sizes=[image.size[::-1]],
        )[0]
        if len(results["boxes"]) > 0:
            scores = results["scores"].cpu().numpy()
            detected = []
            for idx, score in enumerate(scores):
                if score >= MIN_DETECTION_CONFIDENCE:
                    box = results["boxes"][idx].cpu().numpy().tolist()
                    label = results["labels"][idx]
                    detected.append((box, label, float(score)))
            boxes_to_process = filter_and_nms_garments(detected, width, height)

    if len(boxes_to_process) == 0:
        box = [width * 0.1, height * 0.1, width * 0.9, height * 0.9]
        boxes_to_process = [(box, "clothing", 0.5)]

    return boxes_to_process


def segment_garment(
    image: Image.Image,
    box: list,
    device: str,
    model_loaders,
) -> Tuple[np.ndarray, Image.Image, Image.Image, list]:
    """Returns binary_mask (full image), masked_rgba, original_crop, tight_box."""
    width, height = image.size
    x1, y1, x2, y2 = [int(coord) for coord in box]
    x1, y1 = max(0, x1), max(0, y1)
    x2, y2 = min(width, x2), min(height, y2)

    sam_m, sam_p = model_loaders.get_sam_model()
    sam_inputs = sam_p(image, input_boxes=[[box]], return_tensors="pt")
    for k, v in sam_inputs.items():
        if torch.is_tensor(v) and v.dtype == torch.float64:
            sam_inputs[k] = v.to(torch.float32)
    sam_inputs = sam_inputs.to(device)
    with torch.no_grad():
        sam_outputs = sam_m(**sam_inputs)
    masks = sam_p.image_processor.post_process_masks(
        sam_outputs.pred_masks.cpu(),
        sam_inputs.original_sizes.cpu(),
        sam_inputs.reshaped_input_sizes.cpu(),
    )[0]
    binary_mask = masks[0][0].numpy()

    mask_u8 = (binary_mask * 255).astype(np.uint8)
    kernel = cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (5, 5))
    cleaned_mask = cv2.morphologyEx(mask_u8, cv2.MORPH_CLOSE, kernel)
    cleaned_mask = cv2.morphologyEx(cleaned_mask, cv2.MORPH_OPEN, kernel)
    cleaned_mask = cv2.GaussianBlur(cleaned_mask, (3, 3), 0)

    crop_img = image.crop((x1, y1, x2, y2))
    crop_mask = Image.fromarray(cleaned_mask).crop((x1, y1, x2, y2))
    crop_rgba = crop_img.convert("RGBA")
    crop_rgba.putalpha(crop_mask)

    rem_session = model_loaders.get_rembg_session()
    if rem_session is not None:
        try:
            import rembg
            white_bg_crop = Image.new("RGBA", crop_rgba.size, (255, 255, 255, 255))
            white_bg_crop.paste(crop_rgba, mask=crop_rgba.split()[3])
            masked_raw = rembg.remove(white_bg_crop, session=rem_session, alpha_matting=True)
            if masked_raw.height == 3 * white_bg_crop.height:
                h = white_bg_crop.height
                w = white_bg_crop.width
                masked = masked_raw.crop((0, 0, w, h))
            else:
                masked = masked_raw
        except Exception:
            masked = crop_rgba
    else:
        masked = crop_rgba

    alpha_np = np.array(masked.split()[-1])
    contours, _ = cv2.findContours(alpha_np, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
    solid_alpha = np.zeros_like(alpha_np)
    cv2.drawContours(solid_alpha, contours, -1, 255, thickness=-1)
    solid_alpha = cv2.GaussianBlur(solid_alpha, (3, 3), 0)
    masked.putalpha(Image.fromarray(solid_alpha))

    alpha = np.array(masked.split()[-1])
    y_indices, x_indices = np.where(alpha > 0)
    if len(y_indices) > 0:
        ymin_mask, xmin_mask = y_indices.min(), x_indices.min()
        ymax_mask, xmax_mask = y_indices.max(), x_indices.max()
        padding = 4
        ymin_mask = max(0, ymin_mask - padding)
        xmin_mask = max(0, xmin_mask - padding)
        ymax_mask = min(masked.height, ymax_mask + padding)
        xmax_mask = min(masked.width, xmax_mask + padding)
        masked = masked.crop((xmin_mask, ymin_mask, xmax_mask, ymax_mask))
        original_crop = crop_img.crop((xmin_mask, ymin_mask, xmax_mask, ymax_mask))
        tight_box = [x1 + xmin_mask, y1 + ymin_mask, x1 + xmax_mask, y1 + ymax_mask]
    else:
        original_crop = crop_img
        tight_box = [x1, y1, x2, y2]

    return binary_mask, masked, original_crop, tight_box
