"""CatMap's match rules: how a lost report and a sighting are scored, and which strength label they get.

The same rules exist in three places, which must agree:
  * here (the PC, which matches with the AI model),
  * Android `ai/MatchEngine.kt` and web `src/ai/MatchEngine.ts` (the phones' fallback while the PC is off).
`cat/tools/matching/cases.json` holds shared test cases that all three run. The rules are explained in
`cat/docs/MATCHING.md`.

The photo evidence decides; place and time only move a result up or down by one step, never rule it out, because
lost-pet positions are blurred and pets travel. Nothing here ever decides that two animals are the same: the
result is a suggestion that a person reviews.
"""
from __future__ import annotations

import math
from dataclasses import dataclass, field

SPEC_VERSION = 1

# Strength labels, strongest first. "none" means "no reliable match": it is stored (so a later, better check can
# replace it) but never shown.
TIERS = ["strong", "possible", "weak", "none"]

# Similarity of pet-recognition-base embeddings (half the best photo pair, half the averaged photos), per species.
# Measured, not guessed (cat/docs/AI_EVALUATION.md, cat/docs/MATCHING.md):
#   strong   = reached by only 0.1 % of *different* animals of the same breed (Oxford-IIIT Pet look-alikes),
#   possible = reached by 5 % of those look-alikes,
#   weak     = reached by 1 % of different animals of other breeds.
# Cats of one breed look alike to the model (average 0.53), so cats need far higher scores than dogs (0.34).
# Other animal types use the cat values (the stricter ones).
EMBED = {"cat": (0.88, 0.79, 0.68), "dog": (0.89, 0.66, 0.27)}
EMBED_STRONG, EMBED_POSSIBLE, EMBED_WEAK = EMBED["cat"]
# The phone's colour fingerprint (PostAppearance.similarity). Colour alone can't tell two orange tabbies apart, so it
# never gives "strong". possible = 0.1 % and weak = 1 % of different same-breed cats (Oxford-IIIT Pet); on DogFaceNet
# it ranks the right dog first only 18 % of the time, so it is a fallback, not evidence.
PRINT_POSSIBLE = 0.90
PRINT_WEAK = 0.83

GPS_KM = 0.15            # typical phone GPS error
BLUR_KM = 0.45           # an "approximate" post is moved up to ~400 m (LocationPrivacy.fuzz, "high")
ROAM_KM = 3.0            # how far a lost animal plausibly is on day 0...
ROAM_KM_PER_DAY = 1.0    # ...growing each day...
ROAM_MAX_KM = 25.0       # ...up to this (people pick animals up and drive them elsewhere)
NEAR_KM = 1.5
RECENT_DAYS = 14
BEFORE_DAYS = 2          # a sighting from more than 2 days before the pet went missing is probably another animal
DAY_MS = 86_400_000


@dataclass
class Side:
    """What the matcher needs from one post (the lost report, or the sighting)."""
    lat: float
    lng: float
    time_ms: int                 # lastSeenAt, or createdAt when it is missing
    approximate: bool = False
    area_m: int = 0              # the lost report's search area
    has_photos: bool = True
    colour_words: set[str] = field(default_factory=set)


@dataclass
class Result:
    tier: str
    score: float                 # for ranking only (0..1); not a probability
    visual: float                # the raw photo similarity (cosine, or fingerprint 0..1); -1 when there is none
    method: str                  # embedding | fingerprint | details
    distance_km: float
    days_apart: float            # sighting time minus the lost animal's last-seen time, in days
    reasons: list[str]


def distance_km(lat1: float, lng1: float, lat2: float, lng2: float) -> float:
    r = 6371.0
    d_lat, d_lng = math.radians(lat2 - lat1), math.radians(lng2 - lng1)
    a = math.sin(d_lat / 2) ** 2 + math.cos(math.radians(lat1)) * math.cos(math.radians(lat2)) * math.sin(d_lng / 2) ** 2
    return 2 * r * math.asin(math.sqrt(min(1.0, max(0.0, a))))


def down(tier: str) -> str:
    return TIERS[min(len(TIERS) - 1, TIERS.index(tier) + 1)]


def embed_thresholds(animal_type: str) -> tuple[float, float, float]:
    return EMBED.get(animal_type, EMBED["cat"])


def visual_tier(method: str, visual: float, animal_type: str = "cat") -> str:
    if method == "embedding":
        strong, possible, weak = embed_thresholds(animal_type)
        return "strong" if visual >= strong else "possible" if visual >= possible else "weak" if visual >= weak else "none"
    if method == "fingerprint":
        return "possible" if visual >= PRINT_POSSIBLE else "weak" if visual >= PRINT_WEAK else "none"
    return "none"


def visual_norm(method: str, visual: float, animal_type: str = "cat") -> float:
    """The photo similarity on a common 0..1 scale (0.25 at "weak", 0.5 at "possible", 0.8 at "strong")."""
    if method == "embedding":
        strong, possible, weak = embed_thresholds(animal_type)
        points = [(weak - 0.15, 0.0), (weak, 0.25), (possible, 0.5), (strong, 0.8), (1.0, 1.0)]
    elif method == "fingerprint":
        points = [(PRINT_WEAK - 0.15, 0.0), (PRINT_WEAK, 0.25), (PRINT_POSSIBLE, 0.5), (1.0, 0.7)]
    else:
        return 0.0
    if visual <= points[0][0]:
        return 0.0
    for (x0, y0), (x1, y1) in zip(points, points[1:]):
        if visual <= x1:
            return y0 + (y1 - y0) * (visual - x0) / (x1 - x0)
    return points[-1][1]


def score(lost: Side, sighting: Side, method: str, visual: float = -1.0, animal_type: str = "cat") -> Result:
    """Scores a sighting against a lost report. [method] is the photo evidence available: "embedding" (the AI model
    on the PC), "fingerprint" (colour, on the phone) or "details" (no photos to compare)."""
    d = distance_km(lost.lat, lost.lng, sighting.lat, sighting.lng)
    uncertainty = 2 * GPS_KM + (BLUR_KM if lost.approximate else 0) + (BLUR_KM if sighting.approximate else 0) + lost.area_m / 1000
    effective = max(0.0, d - uncertainty)
    days = (sighting.time_ms - lost.time_ms) / DAY_MS
    roam = min(ROAM_MAX_KM, ROAM_KM + ROAM_KM_PER_DAY * max(0.0, days))
    far = effective > roam
    near = effective <= NEAR_KM
    before = days < -BEFORE_DAYS
    recent = -BEFORE_DAYS <= days <= RECENT_DAYS
    reasons: list[str] = []

    if method == "details" or visual < 0:
        method = "details"
        shared = bool(lost.colour_words & sighting.colour_words)
        tier = "weak" if near and recent and shared else "none"
        reasons.append("no_photo")
        if shared:
            reasons.append("same_colour_words")
    else:
        tier = visual_tier(method, visual, animal_type)
        reasons.append({"strong": "photo_very_similar", "possible": "photo_similar", "weak": "photo_somewhat_similar",
                        "none": "photo_different"}[tier])
        if method == "fingerprint":
            reasons.append("colour_only")
            # Colour is weak evidence on its own: only a nearby sighting keeps its label.
            if not near:
                tier = down(tier)
        if far:
            tier = down(tier)
        if before:
            tier = down(tier)
    reasons.append("near" if near else "far" if far else "in_range")
    reasons.append("before_lost" if before else "recent" if recent else "long_after")

    proximity = math.exp(-effective / max(roam, 1e-9))
    time_fit = 0.3 if before else math.exp(-max(0.0, days) / 30)
    rank = 0.85 * visual_norm(method, visual, animal_type) + 0.15 * (0.5 * proximity + 0.5 * time_fit)
    return Result(tier, round(rank, 4), round(visual, 4), method, round(d, 3), round(days, 2), reasons)


def apply_feedback(result: Result, like_rejected: bool, reporter_rejected: bool) -> Result:
    """People's answers: a sighting that looks like one the owner already said wasn't their pet, or that its own
    reporter says is a different animal, drops one step."""
    if like_rejected:
        result.tier = down(result.tier)
        result.reasons.append("like_rejected")
    if reporter_rejected:
        result.tier = down(result.tier)
        result.reasons.append("reporter_rejected")
    return result
