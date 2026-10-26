"""Photo review v1 (docs/MATCHING_REVIEW_V1.md): ranks nearby cat profiles for a person to look through.

The phone sends one photo (its own 360 px thumbnail) and up to 8 cat ids. This module reads each cat's profile and
2-4 of its community photos by id, and orders the cats by how alike the photos look with the frozen review candidate
(tools/matching_candidate/artifacts/review_candidate_v1). It never says "same cat": the candidate didn't reach the
reliability targets, so the answer is an order to review, and the person decides.

The candidate's own code does the maths (CandidateEncoder's model and transform, ReviewGallery's score). Only the
in-memory steps are here, each tested against the research: `prepare` gives byte-identical files to the research's
preparation, and `encode_prepared` gives the same vectors as the frozen `encode(paths)`.
"""
from __future__ import annotations

from collections import OrderedDict
import hashlib
import importlib.util
import io
import os
from pathlib import Path

import numpy as np
from PIL import Image, ImageOps

from model_manifest import MANIFEST

PROTOCOL = "review-v1"
PIN = "catmap/review-candidate-v1"
MAX_CATS = 8
MAX_QUERY_BYTES = 150_000
MAX_REFS = 4
# Photos read per cat at most: room to drop copies and unreadable ones without reading a whole gallery.
MAX_TRIED = 6
CACHE_SIZE = 5000
HERE = Path(__file__).resolve().parent
# cat-server keeps the bundle in the CatMap project next to it; the mirror in tools/worker finds it one level up.
BUNDLES = [HERE.parent / "cat/tools/matching_candidate/artifacts/review_candidate_v1",
           HERE.parent / "matching_candidate/artifacts/review_candidate_v1"]
MEAN = np.array([.485, .456, .406], np.float32)
STD = np.array([.229, .224, .225], np.float32)


class ReviewError(ValueError):
    """A request the protocol refuses; the job is answered with status 'error'."""


def find_bundle() -> Path:
    if os.environ.get("CATMAP_REVIEW_BUNDLE"):
        return Path(os.environ["CATMAP_REVIEW_BUNDLE"])
    found = next((b for b in BUNDLES if b.exists()), None)
    if found is None:
        raise FileNotFoundError("The review bundle wasn't found; set CATMAP_REVIEW_BUNDLE")
    return found


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with open(path, "rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def load_runtime(bundle: Path):
    """The bundle's frozen review_candidate.py, after every file matches the pins in model-manifest.json (checked
    before its code runs, so an edited bundle and manifest can't load)."""
    for name, expected in MANIFEST[PIN]["sha256"].items():
        if sha256(bundle / name) != expected:
            raise ValueError(f"Review bundle integrity check failed: {name}")
    spec = importlib.util.spec_from_file_location("review_candidate_frozen", bundle / "review_candidate.py")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def upright(payload: bytes) -> Image.Image:
    with Image.open(io.BytesIO(payload), formats=("JPEG",)) as opened:
        return ImageOps.exif_transpose(opened).convert("RGB")


def prepare(payload: bytes) -> bytes:
    """The research's own preparation of every evaluated photo: at most 320 px (LANCZOS), JPEG q85."""
    image = upright(payload)
    image.thumbnail((320, 320), Image.Resampling.LANCZOS)
    buffer = io.BytesIO()
    image.save(buffer, "JPEG", quality=85)
    return buffer.getvalue()


class Reviewer:
    def __init__(self, cpu: bool, bundle: Path | None = None):
        bundle = bundle or find_bundle()
        self.runtime = load_runtime(bundle)
        self.encoder = self.runtime.CandidateEncoder(bundle, cpu)
        self.space = self.encoder.space
        self.provider = self.encoder.provider
        self.cache: OrderedDict[str, object] = OrderedDict()

    def encode_prepared(self, payloads: list[bytes]) -> list:
        """CandidateEncoder.encode, from bytes instead of paths (same steps, same order, same batches of 16)."""
        tensors, fingerprints = [], []
        for payload in payloads:
            photo = upright(payload)
            if max(photo.size) > 320:
                raise ValueError("Prepare the photo (at most 320 px) before encoding")
            gray = np.asarray(photo.convert("L").resize((9, 8), Image.Resampling.LANCZOS))
            fingerprint = sum(int(v) << i for i, v in enumerate((gray[:, 1:] > gray[:, :-1]).ravel()))
            fingerprints.append((hashlib.sha256(payload).hexdigest(), fingerprint, photo.width / photo.height))
            pixels = np.asarray(photo.resize((224, 224), Image.Resampling.BICUBIC), np.float32) / 255
            tensors.append(((pixels - MEAN) / STD).transpose(2, 0, 1))
        output = []
        for offset in range(0, len(tensors), 16):
            raw = self.encoder.session.run(None, {"input": np.stack(tensors[offset:offset + 16])})[0]
            vectors = (raw - self.encoder.center) @ self.encoder.whitening
            norms = np.linalg.norm(vectors, axis=1, keepdims=True)
            if not np.isfinite(vectors).all() or np.any(norms <= 1e-8):
                raise ValueError("No usable transformed identity evidence")
            vectors = (vectors / norms).astype(np.float32)
            for pos, vector in enumerate(vectors, offset):
                source, dhash, aspect = fingerprints[pos]
                output.append(self.runtime.PhotoEmbedding(self.space, vector, source, dhash, aspect))
        return output

    def cached(self, photo_id: str):
        photo = self.cache.get(photo_id)
        if photo is None:
            return None
        try:
            self.runtime.validate_photo(photo, self.space)
        except ValueError:
            del self.cache[photo_id]
            return None
        self.cache.move_to_end(photo_id)
        return photo

    def remember(self, photo_id: str, photo):
        self.cache[photo_id] = photo
        self.cache.move_to_end(photo_id)
        while len(self.cache) > CACHE_SIZE:
            self.cache.popitem(last=False)

    def review(self, query: bytes, ids: list[str], get_cats, get_images) -> tuple[dict, dict]:
        """Ranks the cats [ids] for [query]. get_cats(ids) -> {id: dict | None}; get_images(doc ids) ->
        {doc id: bytes | None}. Returns the answer for the job and, for the PC window, {cat id: first photo bytes}."""
        if not isinstance(query, (bytes, bytearray)) or not 0 < len(query) <= MAX_QUERY_BYTES:
            raise ReviewError("the query must be a photo of at most 150 KB")
        if not 1 <= len(ids) <= MAX_CATS or len(set(ids)) != len(ids) \
                or any(not isinstance(i, str) or not 0 < len(i) <= 128 or "/" in i for i in ids):
            raise ReviewError("one to eight different cat ids are needed")
        try:
            target = self.encode_prepared([prepare(bytes(query))])[0]
        except (OSError, ValueError) as e:
            raise ReviewError("the query isn't a usable JPEG photo") from e

        unavailable, chosen = {}, {}
        cats = get_cats(ids)
        for cat_id in ids:
            cat = cats.get(cat_id)
            if not isinstance(cat, dict):
                unavailable[cat_id] = "missing"
            elif cat.get("hidden") is True or cat.get("mergedInto"):
                unavailable[cat_id] = "hidden"
            else:
                photo_ids = [p for p in cat.get("photoIds", []) if isinstance(p, str) and p and "/" not in p]
                cover = cat.get("coverPhotoId")
                # The cover photo first (the one people chose), then the newest; photoIds grow by appending.
                order = ([cover] if cover in photo_ids else []) + [p for p in reversed(photo_ids) if p != cover]
                chosen[cat_id] = list(dict.fromkeys(order))[:MAX_TRIED]

        missing = [p for ps in chosen.values() for p in ps if self.cached(p) is None]
        fetched = get_images([p + "_t" for p in missing]) if missing else {}
        readable, payloads = [], []
        for p in missing:
            data = fetched.get(p + "_t")
            try:
                if data:
                    payloads.append(prepare(bytes(data)))
                    readable.append(p)
            except (OSError, ValueError):
                pass
        if payloads:
            for p, photo in zip(readable, self.encode_prepared(payloads)):
                self.remember(p, photo)

        near = self.runtime.near_copy
        banks, why = {}, {}
        for cat_id, photo_ids in chosen.items():
            kept, unreadable, repeats_query = [], 0, False
            for p in photo_ids:
                photo = self.cached(p)
                if photo is None:
                    unreadable += 1
                elif near(photo, target):
                    repeats_query = True
                elif len(kept) < MAX_REFS and not any(near(photo, k) for _, k in kept):
                    kept.append((p, photo))
            banks[cat_id] = kept
            why[cat_id] = ("same_photo_as_query" if repeats_query else
                           "photo_unreadable" if unreadable and len(photo_ids) - unreadable < 2 else "too_few_photos")
        # A photo on two compared profiles is evidence for neither: drop it from both (ReviewGallery refuses shared
        # photos). Cats already short of photos aren't compared, so they can't knock out a photo of another cat.
        everything = [(c, p, photo) for c, kept in banks.items() if len(kept) >= 2 for p, photo in kept]
        shared = {(c, p) for c, p, a in everything for d, _, b in everything if c != d and near(a, b)}
        for cat_id, kept in list(banks.items()):
            kept = [(p, photo) for p, photo in kept if (cat_id, p) not in shared]
            if len(kept) < 2:
                unavailable[cat_id] = why[cat_id]
                del banks[cat_id]
            else:
                banks[cat_id] = kept

        ranked = []
        if banks:
            gallery = self.runtime.ReviewGallery(
                self.space, [self.runtime.ReferenceBank(c, tuple(photo for _, photo in kept)) for c, kept in banks.items()])
            for r in gallery.rank(target, len(banks))["ranked"]:
                ranked.append({"id": r["id"], "score": round(r["retrievalScore"], 4),
                               "photoIds": [p for p, _ in banks[r["id"]]]})
        answer = {"protocol": PROTOCOL, "space": self.space, "ranked": ranked,
                  "unavailable": [{"id": i, "reason": unavailable[i]} for i in ids if i in unavailable]}
        previews = {r["id"]: fetched.get(r["photoIds"][0] + "_t") for r in ranked}
        return answer, previews
