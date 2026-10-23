"""Map-area codes (`geoCells`) for posts and cats, written by the worker for posts that older app versions saved.

The phone's map asks Firestore for one area at a time (cat app: data/Geohash.kt, data/MapRepository.kt). An area is a
standard geohash at precision 4, 5 or 6, and each post or cat lists the three that contain its (already blurred)
position. This must give exactly the same codes as Geohash.kt and the web app; tests/test_geocells.py has the shared
vectors.
"""
from __future__ import annotations

BASE32 = "0123456789bcdefghjkmnpqrstuvwxyz"
PRECISIONS = (4, 5, 6)


def encode(lat: float, lng: float, precision: int) -> str:
    lat_lo, lat_hi, lng_lo, lng_hi = -90.0, 90.0, -180.0, 180.0
    out, even, bit, ch = [], True, 0, 0
    while len(out) < precision:
        if even:
            mid = (lng_lo + lng_hi) / 2
            if lng >= mid:
                ch, lng_lo = ch * 2 + 1, mid
            else:
                ch, lng_hi = ch * 2, mid
        else:
            mid = (lat_lo + lat_hi) / 2
            if lat >= mid:
                ch, lat_lo = ch * 2 + 1, mid
            else:
                ch, lat_hi = ch * 2, mid
        even = not even
        bit += 1
        if bit == 5:
            out.append(BASE32[ch])
            bit, ch = 0, 0
    return "".join(out)


def cells(lat: float, lng: float) -> list[str]:
    """The `geoCells` field for a position; empty for a missing position (0, 0)."""
    if lat == 0 and lng == 0:
        return []
    full = encode(lat, lng, PRECISIONS[-1])
    return [full[:p] for p in PRECISIONS]


def needed(doc: dict) -> list[str] | None:
    """The codes a post or cat should have, or None when it already has them (or has no usable position)."""
    lat, lng = doc.get("lat"), doc.get("lng")
    if isinstance(lat, bool) or isinstance(lng, bool) or not isinstance(lat, (int, float)) or not isinstance(lng, (int, float)):
        return None
    want = cells(float(lat), float(lng))
    return None if doc.get("geoCells") == want else want
