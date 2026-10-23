"""Shared geohash vectors: the same values are in the cat app's GeohashTest.kt.

    .venv/Scripts/python.exe tests/test_geocells.py
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
import geocells  # noqa: E402

assert geocells.encode(57.64911, 10.40744, 11) == "u4pruydqqvj"
assert geocells.encode(25.1972, 55.2744, 6) == "thrr3g"
assert geocells.encode(25.2048, 55.2708, 6) == "thrr3s"
assert geocells.encode(-33.8688, 151.2093, 6) == "r3gx2f"
assert geocells.cells(25.2048, 55.2708) == ["thrr", "thrr3", "thrr3s"]
assert geocells.cells(0, 0) == []
assert geocells.needed({"lat": 25.2048, "lng": 55.2708}) == ["thrr", "thrr3", "thrr3s"]
assert geocells.needed({"lat": 25.2048, "lng": 55.2708, "geoCells": ["thrr", "thrr3", "thrr3s"]}) is None
assert geocells.needed({"lat": "25", "lng": 55.0}) is None
assert geocells.needed({}) is None
print("geocells: 10 checks passed")
