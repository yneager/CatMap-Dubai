"""matching.py: the shared cases file is up to date, and the rules keep their promises."""
import subprocess
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent))
import matching  # noqa: E402

CASES = next((p for p in HERE.parents if (p / 'tools/matching/cases.json').is_file()), HERE.parent.parent / 'cat') / "tools" / "matching" / "cases.json"


def test_cases_file_matches_the_code():
    before = CASES.read_text(encoding="utf-8")
    subprocess.run([sys.executable, str(HERE / "make_match_cases.py")], check=True, capture_output=True)
    assert CASES.read_text(encoding="utf-8") == before, "matching.py changed: commit the regenerated cases.json"


def test_colour_alone_is_never_strong():
    s = matching.Side(25.2, 55.27, 0)
    assert matching.score(s, s, "fingerprint", 1.0).tier == "possible"


def test_place_and_time_move_one_step_each():
    lost = matching.Side(25.2, 55.27, 0)
    far_before = matching.Side(25.2 + 60 / 111.2, 55.27, -10 * matching.DAY_MS)
    assert matching.score(lost, matching.Side(25.2, 55.27, matching.DAY_MS), "embedding", 0.95).tier == "strong"
    assert matching.score(lost, far_before, "embedding", 0.95).tier == "weak"


def test_place_and_time_never_remove_a_photo_match():
    # 27 Oct: a street cat 12 km away, posted 12 days before the pet went missing, whose photo scored 0.808
    # ("possible"), fell to "none" and was never stored.
    lost = matching.Side(25.2, 55.27, 0)
    street = matching.Side(25.2 + 12 / 111.2, 55.27, -12 * matching.DAY_MS)
    assert matching.score(lost, street, "embedding", 0.808).tier == "weak"
    assert matching.score(lost, street, "embedding", 0.70).tier == "weak"
    assert matching.score(lost, street, "embedding", 0.50).tier == "none"


def test_cats_need_more_than_dogs():
    s = matching.Side(25.2, 55.27, 0)
    assert matching.score(s, s, "embedding", 0.7, "dog").tier == "possible"
    assert matching.score(s, s, "embedding", 0.7, "cat").tier == "weak"


if __name__ == "__main__":
    for name, fn in list(globals().items()):
        if name.startswith("test_"):
            fn()
            print("ok", name)
