"""Travel capsule wardrobe planner — GPT-powered with rule-based fallback."""

from __future__ import annotations

import json
from typing import Any, Dict, List, Optional

from .config import OPENAI_API_KEY


def _avg_temp_f(temp_low: float, temp_high: float) -> float:
    return (temp_low + temp_high) / 2.0


def _garment_summary(g: Dict[str, Any]) -> str:
    return (
        f"id={g['id']} | {g['category']}/{g['subcategory']} | "
        f"{g['colorName']} {g['material']} | formality={g['formalityScore']:.1f} | "
        f"seasons={','.join(g.get('seasons', []))} | status={g.get('laundryStatus', 'CLEAN')}"
    )


def _build_gpt_prompt(
    destination: str,
    trip_days: int,
    temp_low: float,
    temp_high: float,
    weather_condition: str,
    garments: List[Dict[str, Any]],
    preferred_styles: List[str],
) -> str:
    catalog = "\n".join(_garment_summary(g) for g in garments)
    styles = ", ".join(preferred_styles) if preferred_styles else "versatile, practical"
    avg = _avg_temp_f(temp_low, temp_high)

    return f"""You are an expert travel wardrobe stylist. Build a minimal capsule wardrobe from the user's closet.

TRIP
- Destination: {destination}
- Duration: {trip_days} days
- Forecast: {weather_condition}, {temp_low:.0f}–{temp_high:.0f}°F (avg {avg:.0f}°F)
- Style preferences: {styles}

AVAILABLE GARMENTS (only use these exact ids)
{catalog}

RULES
1. Select the smallest versatile capsule (typically 6–12 items) that covers all {trip_days} days.
2. Prioritize mix-and-match: each top should pair with multiple bottoms.
3. Match weather: no heavy outerwear above 70°F; include layers below 60°F; no shorts below 55°F.
4. Only use garments with laundryStatus CLEAN.
5. Each daily outfit must include at least one Top, one Bottom, and one Shoes item.
6. Add Outerwear only when temperature warrants it.
7. Vary outfits across days — avoid repeating the exact same combination.
8. Return ONLY valid JSON matching this schema:
{{
  "capsule_garment_ids": ["id1", "id2"],
  "daily_outfits": [
    {{"day": 1, "garment_ids": ["id1", "id2", "id3"], "reason": "Brief styling note"}}
  ],
  "packing_notes": "One short paragraph of packing advice"
}}"""


def _call_gpt(prompt: str) -> Optional[Dict[str, Any]]:
    if not OPENAI_API_KEY:
        return None
    try:
        from openai import OpenAI

        client = OpenAI(api_key=OPENAI_API_KEY)
        response = client.chat.completions.create(
            model="gpt-4o-mini",
            messages=[
                {
                    "role": "system",
                    "content": (
                        "You are a travel packing stylist. Respond with valid JSON only, "
                        "no markdown fences."
                    ),
                },
                {"role": "user", "content": prompt},
            ],
            response_format={"type": "json_object"},
            temperature=0.4,
            max_tokens=2000,
        )
        raw = response.choices[0].message.content or ""
        return json.loads(raw)
    except Exception as e:
        print(f"GPT travel capsule failed: {e}")
        return None


def _validate_plan(
    plan: Dict[str, Any],
    garments_by_id: Dict[str, Dict[str, Any]],
    trip_days: int,
) -> Optional[Dict[str, Any]]:
    capsule_ids = plan.get("capsule_garment_ids") or []
    daily = plan.get("daily_outfits") or []

    valid_capsule = [gid for gid in capsule_ids if gid in garments_by_id]
    if len(valid_capsule) < 3:
        return None

    validated_daily: List[Dict[str, Any]] = []
    for entry in daily:
        day = int(entry.get("day", 0))
        if day < 1 or day > trip_days:
            continue
        ids = [gid for gid in (entry.get("garment_ids") or []) if gid in garments_by_id]
        categories = {garments_by_id[gid]["category"] for gid in ids}
        if "Top" not in categories or "Bottom" not in categories or "Shoes" not in categories:
            continue
        validated_daily.append(
            {
                "day": day,
                "garment_ids": ids,
                "reason": str(entry.get("reason") or f"Day {day} outfit"),
            }
        )

    if len(validated_daily) < trip_days:
        return None

    return {
        "capsule_garment_ids": valid_capsule,
        "daily_outfits": validated_daily[:trip_days],
        "packing_notes": str(plan.get("packing_notes") or ""),
        "provider": "gpt-4o-mini",
    }


def _rule_based_capsule(
    destination: str,
    trip_days: int,
    temp_low: float,
    temp_high: float,
    weather_condition: str,
    garments: List[Dict[str, Any]],
) -> Dict[str, Any]:
    avg = _avg_temp_f(temp_low, temp_high)
    clean = [g for g in garments if g.get("laundryStatus", "CLEAN") == "CLEAN"]
    if len(clean) < 3:
        clean = garments

    def score(g: Dict[str, Any]) -> float:
        s = 0.0
        if avg > 75 and g["category"] in ("Top", "Bottom"):
            if any(w in g["subcategory"].lower() for w in ("linen", "polo", "t-shirt", "short")):
                s += 2.0
            if "wool" in g["material"].lower() or "puffer" in g["subcategory"].lower():
                s -= 3.0
        if avg < 58:
            if g["category"] == "Outerwear":
                s += 3.0
            if any(w in g["subcategory"].lower() for w in ("short", "beach")):
                s -= 4.0
        if 55 <= avg <= 75:
            s += g.get("formalityScore", 0.5)
        s -= g.get("wearCount", 0) * 0.05
        return s

    tops = sorted([g for g in clean if g["category"] == "Top"], key=score, reverse=True)
    bottoms = sorted([g for g in clean if g["category"] == "Bottom"], key=score, reverse=True)
    shoes = sorted([g for g in clean if g["category"] == "Shoes"], key=score, reverse=True)
    outer = sorted([g for g in clean if g["category"] == "Outerwear"], key=score, reverse=True)

    capsule: List[str] = []
    for group, limit in ((tops, 3), (bottoms, 2), (shoes, 1), (outer, 1 if avg < 65 else 0)):
        for g in group[:limit]:
            if g["id"] not in capsule:
                capsule.append(g["id"])

    by_id = {g["id"]: g for g in clean}
    daily_outfits: List[Dict[str, Any]] = []
    for day in range(1, trip_days + 1):
        top = tops[(day - 1) % max(len(tops), 1)]["id"] if tops else None
        bottom = bottoms[(day - 1) % max(len(bottoms), 1)]["id"] if bottoms else None
        shoe = shoes[0]["id"] if shoes else None
        ids = [x for x in (top, bottom, shoe) if x]
        if avg < 65 and outer:
            jacket = outer[0]["id"]
            if jacket not in ids:
                ids.append(jacket)
        reason = (
            f"Day {day} capsule for {destination} — {weather_condition}, "
            f"~{avg:.0f}°F. Rotating versatile pieces."
        )
        daily_outfits.append({"day": day, "garment_ids": ids, "reason": reason})

    for outfit in daily_outfits:
        for gid in outfit["garment_ids"]:
            if gid not in capsule:
                capsule.append(gid)

    return {
        "capsule_garment_ids": capsule,
        "daily_outfits": daily_outfits,
        "packing_notes": (
            f"Pack {len(capsule)} versatile pieces for {trip_days} days in {destination}. "
            f"Forecast {temp_low:.0f}–{temp_high:.0f}°F ({weather_condition}). "
            "Mix and match tops with bottoms to maximize outfit variety."
        ),
        "provider": "rule-based",
    }


def generate_travel_capsule(
    destination: str,
    trip_days: int,
    temp_low: float,
    temp_high: float,
    weather_condition: str,
    garments: List[Dict[str, Any]],
    preferred_styles: Optional[List[str]] = None,
) -> Dict[str, Any]:
    trip_days = max(1, min(int(trip_days), 14))
    preferred_styles = preferred_styles or []
    clean_garments = [g for g in garments if g.get("laundryStatus", "CLEAN") == "CLEAN"]
    pool = clean_garments if len(clean_garments) >= 3 else garments
    garments_by_id = {g["id"]: g for g in pool}

    if not pool:
        raise ValueError("No garments available for capsule planning")

    prompt = _build_gpt_prompt(
        destination, trip_days, temp_low, temp_high, weather_condition, pool, preferred_styles
    )
    gpt_plan = _call_gpt(prompt)
    if gpt_plan:
        validated = _validate_plan(gpt_plan, garments_by_id, trip_days)
        if validated:
            return validated

    return _rule_based_capsule(
        destination, trip_days, temp_low, temp_high, weather_condition, pool
    )
