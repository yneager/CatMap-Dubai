"""Shared examples: the same values are in the cat app's SearchTermsTest.kt.

    .venv/Scripts/python.exe tests/test_searchterms.py
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
import searchterms  # noqa: E402

assert searchterms.of(["simba", "orange", "tabby", "al", "barsha"]) == [
    "simba", "orange", "tabby", "al", "barsha", "sim", "simb", "ora", "oran", "orang", "tab", "tabb", "bar", "bars", "barsh"]
assert searchterms.words("Simba (the KING)", "simba", "a") == ["simba", "the", "king"]
assert searchterms.words("قطة برتقالية") == ["قطة", "برتقالية"]
long = [f"w{i:02}xxxxxx" for i in range(40)]
t = searchterms.of(long)
assert "w00xxxx" in t and "w30xxx" not in t and "w39xxxx" in t and "w30xxxxxx" in t
assert len(searchterms.of([f"{i:03}abcdefgh" for i in range(80)])) == 200
assert searchterms.needed_post({"keywords": ["rex"]}) == ["rex"]
assert searchterms.needed_post({"keywords": ["rex"], "searchTerms": []}) is None
assert searchterms.needed_cat({"name": "Zafran", "aliases": ["Ginger"]}) == ["zafran", "ginger", "zaf", "zafr", "zafra", "gin", "ging", "ginge"]
print("searchterms: 8 checks passed")
