"""The Python fingerprint must match the phone's (fixtures exported from real Android bitmaps)."""
import base64
import json
import sys
from pathlib import Path

import numpy as np

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent))
import fingerprint  # noqa: E402

FIXTURES = next((p for p in HERE.parents if (p / 'tools/matching/cases.json').is_file()), HERE.parent.parent / 'cat') / "web" / "tests" / "fixtures" / "android-fingerprints.json"


def test_matches_android():
    fixtures = json.loads(FIXTURES.read_text(encoding="utf-8"))
    assert len(fixtures) >= 3
    for f in fixtures:
        p = f["post"]
        rgba = np.frombuffer(base64.b64decode(p["rgba"]), dtype=np.uint8).reshape(p["height"], p["width"], 4)
        got = fingerprint.describe_rgb(rgba[..., :3])
        want = np.array(p["values"])
        assert np.abs(got - want).max() <= 1e-4, (f["file"], np.abs(got - want).max())
        assert fingerprint.similarity(got, want) > 0.99


if __name__ == "__main__":
    test_matches_android()
    print("ok")
