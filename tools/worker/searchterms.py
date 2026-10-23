"""Search words (`searchTerms`) for posts and cats, written by the worker for posts that older app versions saved.

A search on the phone asks Firestore for posts whose `searchTerms` contain the word typed, so every word is stored with
its first few letters. This must give exactly the same list as data/SearchTerms.kt in the cat app;
tests/test_searchterms.py has the shared examples.
"""
from __future__ import annotations

import re

PREFIX_MIN = 3
PREFIX_MAX = 7
HEAD = 25   # prefixes only for the first and last words: names, colours and breeds first, area names last
TAIL = 6
MAX = 200
_SPLIT = re.compile(r"[\W_]+")


def words(*texts: str) -> list[str]:
    """Lower-case words of at least two letters, in order, without repeats (as in a post's `keywords`)."""
    out: list[str] = []
    for t in texts:
        for w in _SPLIT.split(t.lower()):
            if len(w) >= 2 and w not in out:
                out.append(w)
    return out[:80]


def of(ws: list[str]) -> list[str]:
    out: dict[str, None] = {}
    for w in ws:
        out[w] = None
    for i, w in enumerate(ws):
        if i < HEAD or i >= len(ws) - TAIL:
            for k in range(PREFIX_MIN, min(len(w) - 1, PREFIX_MAX) + 1):
                out[w[:k]] = None
    return list(out)[:MAX]


def needed_post(doc: dict) -> list[str] | None:
    """A post's terms when it has none yet (they come from its `keywords`), otherwise None."""
    kw = doc.get("keywords")
    if "searchTerms" in doc or not isinstance(kw, list) or not all(isinstance(w, str) for w in kw):
        return None
    return of(kw)


def needed_cat(doc: dict) -> list[str] | None:
    """A cat's terms when it has none yet (from its name and aliases), otherwise None."""
    if "searchTerms" in doc:
        return None
    name = doc.get("name") if isinstance(doc.get("name"), str) else ""
    aliases = [a for a in doc.get("aliases") or [] if isinstance(a, str)]
    return of(words(name, *aliases))
