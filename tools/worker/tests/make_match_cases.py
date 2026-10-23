"""Writes cat/tools/matching/cases.json: inputs and the expected results of matching.py, which the Android
(MatchEngineTest) and web (match-engine.test.ts) copies of the rules must reproduce."""
import json
import sys
from dataclasses import asdict
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent))
import matching  # noqa: E402

DAY = 86_400_000
T0 = 1_760_000_000_000
LOST = dict(lat=25.2048, lng=55.2708, time_ms=T0, approximate=False, area_m=500, has_photos=True, colour_words=["orange", "tabby"])


def at(km_north: float):
    return 25.2048 + km_north / 111.2


cases = []
for animal in ("cat", "dog", "rabbit"):
    for method, visuals in (("embedding", [0.95, 0.885, 0.85, 0.8, 0.7, 0.6, 0.4, 0.2]), ("fingerprint", [0.95, 0.9, 0.86, 0.8]), ("details", [-1.0])):
        for km, days, approx in ((0.3, 1, False), (4, 3, True), (40, 2, False), (1, -5, False), (2, 40, False)):
            for v in visuals:
                sighting = dict(lat=at(km), lng=55.2708, time_ms=T0 + int(days * DAY), approximate=approx, area_m=0,
                                has_photos=method != "details", colour_words=["orange"] if km < 3 else ["black"])
                cases.append({"animalType": animal, "method": method, "visual": v, "lost": LOST, "sighting": sighting})
out = []
for c in cases:
    side = lambda d: matching.Side(d["lat"], d["lng"], d["time_ms"], d["approximate"], d["area_m"], d["has_photos"], set(d["colour_words"]))
    r = matching.score(side(c["lost"]), side(c["sighting"]), c["method"], c["visual"], c["animalType"])
    out.append({**c, "expected": asdict(r)})
for like, rep in ((True, False), (False, True), (True, True)):
    base = out[0]
    side = lambda d: matching.Side(d["lat"], d["lng"], d["time_ms"], d["approximate"], d["area_m"], d["has_photos"], set(d["colour_words"]))
    r = matching.apply_feedback(matching.score(side(base["lost"]), side(base["sighting"]), base["method"], base["visual"], base["animalType"]), like, rep)
    out.append({**{k: base[k] for k in ("animalType", "method", "visual", "lost", "sighting")}, "likeRejected": like, "reporterRejected": rep, "expected": asdict(r)})
target = next((p for p in HERE.parents if (p / 'tools/matching/cases.json').is_file()), HERE.parent.parent / 'cat') / "tools" / "matching" / "cases.json"
target.parent.mkdir(parents=True, exist_ok=True)
target.write_text(json.dumps({"spec": matching.SPEC_VERSION, "embed": matching.EMBED, "print": [matching.PRINT_POSSIBLE, matching.PRINT_WEAK], "cases": out}, indent=1), encoding="utf-8")
tiers = {}
for c in out:
    tiers[c["expected"]["tier"]] = tiers.get(c["expected"]["tier"], 0) + 1
print(f"{len(out)} cases -> {target}  tiers: {tiers}")
