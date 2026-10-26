"""Photo review v1 (review.py). Logic tests use the frozen candidate runtime with synthetic vectors; real-model tests
(exactness against the research, the example request) run only where the git-ignored bundle and onnxruntime exist."""
import ast
import hashlib
import io
import json
import queue
import sys
import time
import types
import unittest
from pathlib import Path
from unittest.mock import patch

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT))
import review  # noqa: E402

try:
    BUNDLE = review.find_bundle()
except FileNotFoundError:
    BUNDLE = None
try:
    import onnxruntime  # noqa: F401
    HAVE_ORT = True
except ImportError:
    HAVE_ORT = False


def frozen_runtime():
    """The frozen runtime module; onnxruntime is only needed to run the model, so a stub stands in without it."""
    with patch.dict(sys.modules, {} if HAVE_ORT else {"onnxruntime": types.ModuleType("onnxruntime")}):
        return review.load_runtime(BUNDLE)


def unit(seed, dim=768):
    v = np.random.default_rng(seed).standard_normal(dim).astype(np.float32)
    return v / np.linalg.norm(v)


def near(base, seed, amount):
    v = base + amount * unit(seed)
    return (v / np.linalg.norm(v)).astype(np.float32)


@unittest.skipIf(BUNDLE is None, "review bundle not on this machine")
class ReviewLogic(unittest.TestCase):
    """review() with synthetic vectors: prepare is the identity and each payload names its vector."""

    def setUp(self):
        self.runtime = frozen_runtime()
        self.reviewer = review.Reviewer.__new__(review.Reviewer)
        self.reviewer.runtime, self.reviewer.space, self.reviewer.provider = self.runtime, "S", "test"
        from collections import OrderedDict
        self.reviewer.cache = OrderedDict()
        self.vectors, self.encoded = {}, 0
        self.prepare = patch.object(review, "prepare", lambda b: bytes(b))
        self.prepare.start()
        self.addCleanup(self.prepare.stop)
        self.reviewer.encode_prepared = self.encode
        self.fetches = []

    def encode(self, payloads):
        self.encoded += len(payloads)
        out = []
        for p in payloads:
            vector, dhash = self.vectors[p]
            out.append(self.runtime.PhotoEmbedding("S", vector, hashlib.sha256(p).hexdigest(), dhash, 1.0))
        return out

    def photo(self, name, vector, dhash=None):
        # Distinct sources get dHashes far apart; a copy reuses its original's payload and hash.
        payload = name.encode()
        self.vectors[payload] = (vector, dhash if dhash is not None else int.from_bytes(hashlib.sha256(payload).digest()[:8], "big"))
        return payload

    def run_review(self, cats, images, ids, query=b"query"):
        def get_images(doc_ids):
            self.fetches.append(list(doc_ids))
            return {d: images.get(d) for d in doc_ids}
        return self.reviewer.review(query, ids, lambda ids: {i: cats.get(i) for i in ids}, get_images)[0]

    def world(self):
        tom, ash = unit(1), unit(2)
        self.photo("query", near(tom, 10, .4))
        images = {"t1_t": self.photo("t1", near(tom, 11, .5)), "t2_t": self.photo("t2", near(tom, 12, .5)),
                  "a1_t": self.photo("a1", near(ash, 13, .5)), "a2_t": self.photo("a2", near(ash, 14, .5))}
        cats = {"tom": {"photoIds": ["t1", "t2"], "coverPhotoId": "t1"}, "ash": {"photoIds": ["a1", "a2"]}}
        return cats, images

    def test_ranks_the_alike_cat_first_without_verdicts(self):
        cats, images = self.world()
        answer = self.run_review(cats, images, ["ash", "tom"])
        self.assertEqual([r["id"] for r in answer["ranked"]], ["tom", "ash"])
        self.assertEqual(answer["protocol"], "review-v1")
        self.assertEqual(answer["unavailable"], [])
        self.assertEqual(answer["ranked"][0]["photoIds"], ["t1", "t2"])
        flat = json.dumps(answer)
        for word in ("verdict", "same", "maybe", "confidence", "similarity"):
            self.assertNotIn(word, flat)

    def test_each_unusable_cat_gets_its_own_reason(self):
        cats, images = self.world()
        cats.update({"solo": {"photoIds": ["t1"]}, "hid": {"photoIds": ["a1", "a2"], "hidden": True},
                     "gone": None, "merged": {"photoIds": ["a1", "a2"], "mergedInto": "ash"},
                     "broken": {"photoIds": ["x1", "x2", "x3"]}})
        answer = self.run_review(cats, images, ["tom", "solo", "hid", "gone", "merged", "broken"])
        self.assertEqual([r["id"] for r in answer["ranked"]], ["tom"])
        self.assertEqual({u["id"]: u["reason"] for u in answer["unavailable"]},
                         {"solo": "too_few_photos", "hid": "hidden", "gone": "missing", "merged": "hidden",
                          "broken": "photo_unreadable"})

    def test_copies_inside_a_cat_count_once(self):
        cats, images = self.world()
        images["t3_t"] = self.photo("t3", self.vectors[b"t1"][0], self.vectors[b"t1"][1])
        cats["tom"]["photoIds"] = ["t1", "t3"]
        answer = self.run_review(cats, images, ["tom", "ash"])
        self.assertEqual({u["id"]: u["reason"] for u in answer["unavailable"]}, {"tom": "too_few_photos"})

    def test_the_query_photo_on_a_profile_is_not_evidence(self):
        cats, images = self.world()
        images["q_t"] = self.photo("q-copy", self.vectors[b"query"][0], self.vectors[b"query"][1])
        cats["tom"]["photoIds"] = ["t1", "q"]
        answer = self.run_review(cats, images, ["tom", "ash"])
        self.assertEqual({u["id"]: u["reason"] for u in answer["unavailable"]}, {"tom": "same_photo_as_query"})
        cats["tom"]["photoIds"] = ["t1", "q", "t2"]
        self.reviewer.cache.clear()
        answer = self.run_review(cats, images, ["tom", "ash"])
        self.assertEqual(answer["ranked"][0]["photoIds"], ["t1", "t2"])

    def test_a_photo_on_two_profiles_is_dropped_from_both(self):
        cats, images = self.world()
        cats["tom"]["photoIds"] = ["t1", "t2", "a1"]
        answer = self.run_review(cats, images, ["tom", "ash"])
        self.assertEqual([r["id"] for r in answer["ranked"]], ["tom"])
        self.assertEqual(answer["ranked"][0]["photoIds"], ["t1", "t2"])
        self.assertEqual({u["id"]: u["reason"] for u in answer["unavailable"]}, {"ash": "too_few_photos"})

    def test_cover_then_newest_four_and_bounded_reads(self):
        cats, images = self.world()
        base = unit(1)
        names = [f"p{i}" for i in range(9)]
        for i, n in enumerate(names):
            images[n + "_t"] = self.photo(n, near(base, 100 + i, .5))
        cats["tom"] = {"photoIds": names, "coverPhotoId": "p2"}
        answer = self.run_review(cats, images, ["tom", "ash"])
        self.assertEqual(answer["ranked"][0]["photoIds"], ["p2", "p8", "p7", "p6"])
        tom_reads = [d for d in self.fetches[0] if d.startswith("p")]
        self.assertEqual(len(tom_reads), review.MAX_TRIED)

    def test_cached_vectors_are_not_read_again_and_bad_ones_are_replaced(self):
        cats, images = self.world()
        self.run_review(cats, images, ["tom", "ash"])
        self.assertEqual(len(self.fetches), 1)
        self.run_review(cats, images, ["tom", "ash"])
        self.assertEqual(len(self.fetches), 1)
        broken = self.reviewer.cache["t1"]
        self.reviewer.cache["t1"] = self.runtime.PhotoEmbedding("S", np.zeros(512, np.float32), broken.source_sha256, broken.dhash64, 1.0)
        self.run_review(cats, images, ["tom", "ash"])
        self.assertEqual(self.fetches[-1], ["t1_t"])
        other = self.reviewer.cache["t2"]
        self.reviewer.cache["t2"] = self.runtime.PhotoEmbedding("legacy512", other.vector, other.source_sha256, other.dhash64, 1.0)
        self.run_review(cats, images, ["tom", "ash"])
        self.assertEqual(self.fetches[-1], ["t2_t"])

    def test_cache_is_bounded(self):
        with patch.object(review, "CACHE_SIZE", 3):
            cats, images = self.world()
            self.run_review(cats, images, ["tom", "ash"])
            self.assertEqual(len(self.reviewer.cache), 3)
            self.assertNotIn("t1", self.reviewer.cache)

    def test_refuses_malformed_requests_without_cutting_them(self):
        cats, images = self.world()
        for ids in ([], [f"c{i}" for i in range(9)], ["tom", "tom"], ["a/b"], [""], [7]):
            with self.assertRaises(review.ReviewError):
                self.run_review(cats, images, ids)
        with self.assertRaises(review.ReviewError):
            self.run_review(cats, images, ["tom"], query=b"x" * 150_001)
        with self.assertRaises(review.ReviewError):
            self.run_review(cats, images, ["tom"], query=b"")
        self.assertEqual(self.fetches, [])

    def test_no_cat_left_is_an_empty_ranking(self):
        cats, images = self.world()
        answer = self.run_review({"solo": {"photoIds": ["t1"]}}, images, ["solo"])
        self.assertEqual(answer["ranked"], [])
        self.assertEqual(answer["unavailable"], [{"id": "solo", "reason": "too_few_photos"}])


class ReviewJob(unittest.TestCase):
    """Worker.handle for kind 'review', run without the GUI, credentials or models."""

    def setUp(self):
        tree = ast.parse((ROOT / "server.py").read_text(encoding="utf-8"))
        worker = next(n for n in tree.body if isinstance(n, ast.ClassDef) and n.name == "Worker")
        handle = next(n for n in worker.body if isinstance(n, ast.FunctionDef) and n.name == "handle")
        self.ns = {"time": time, "MODEL_NAME": "base", "CLIP_NAME": "clip", "MAX_IMAGES": 16}
        exec(compile(ast.Module(body=[handle], type_ignores=[]), str(ROOT / "server.py"), "exec"), self.ns)
        firestore = types.ModuleType("google.cloud.firestore")
        firestore.SERVER_TIMESTAMP = "timestamp"
        firestore.transactional = lambda fn: fn
        google, cloud = types.ModuleType("google"), types.ModuleType("google.cloud")
        google.cloud, cloud.firestore = cloud, firestore
        modules = patch.dict(sys.modules, {"google": google, "google.cloud": cloud, "google.cloud.firestore": firestore})
        modules.start()
        self.addCleanup(modules.stop)

    def run_job(self, fields, reviewer):
        written = {}
        current = {"requestId": "A", "status": "pending"}
        ref = types.SimpleNamespace(get=lambda transaction=None: types.SimpleNamespace(to_dict=lambda: dict(current), update_time=None),
                                    update=lambda r, value: written.update(value))
        doc = types.SimpleNamespace(id="alice", reference=ref, update_time=None,
                                    to_dict=lambda: {"uid": "alice", "requestId": "A", "status": "pending", **fields})
        worker = types.SimpleNamespace(answered=set(), handled=0, model=types.SimpleNamespace(provider="cpu"), events=queue.Queue(),
                                       username=lambda _: "alice", retry_jobs={}, retry_attempts={}, reviewer=reviewer,
                                       read_cats=None, read_images=None,
                                       fs=types.SimpleNamespace(client=types.SimpleNamespace(transaction=lambda: ref)))
        self.ns["handle"](worker, doc)
        return written

    def test_answers_with_the_review_only(self):
        reviewer = types.SimpleNamespace(provider="dml", review=lambda q, ids, c, i: ({"protocol": "review-v1", "ranked": [], "unavailable": []}, {}))
        out = self.run_job({"kind": "review", "query": b"jpeg", "ids": ["c1"], "images": [], "photos": []}, reviewer)
        self.assertEqual(out["status"], "done")
        self.assertEqual(out["review"]["protocol"], "review-v1")
        self.assertNotIn("results", out)
        self.assertEqual((out["model"], out["provider"], out["answeredRequestId"]), ("review-candidate-v1", "dml", "A"))

    def test_refused_when_review_is_off_or_photos_are_attached(self):
        self.assertEqual(self.run_job({"kind": "review", "query": b"jpeg", "ids": ["c1"]}, None)["status"], "error")
        reviewer = types.SimpleNamespace(provider="dml", review=lambda *a: self.fail("must not run"))
        self.assertEqual(self.run_job({"kind": "review", "query": b"jpeg", "ids": ["c1"], "images": [b"x"]}, reviewer)["status"], "error")

    def test_review_errors_become_an_error_answer(self):
        def refuse(*_):
            raise review.ReviewError("one to eight different cat ids are needed")
        out = self.run_job({"kind": "review", "query": b"jpeg", "ids": []}, types.SimpleNamespace(provider="dml", review=refuse))
        self.assertEqual(out["status"], "error")
        self.assertIn("cat ids", out["error"])


@unittest.skipIf(BUNDLE is None or not HAVE_ORT, "real-model checks need the review bundle and onnxruntime")
class RealModel(unittest.TestCase):
    """Exactness against the research, on this PC's bundle."""

    @classmethod
    def setUpClass(cls):
        cls.reviewer = review.Reviewer(cpu=True, bundle=BUNDLE)
        cls.example = json.loads((BUNDLE / "example_request.json").read_text())

    def test_prepare_is_byte_identical_to_the_research_preparation(self):
        icw = Path(self.example["query"]).parent.parent
        records = json.loads((icw / "source_records.json").read_text())[:25]
        for row in records:
            prepared = review.prepare((icw.parent / "shuffled_input" / row["file"]).read_bytes())
            self.assertEqual(hashlib.sha256(prepared).hexdigest(), row["sha256"], row["file"])

    def test_encode_prepared_matches_the_frozen_encoder(self):
        paths = [self.example["query"]] + [p for c in self.example["candidates"] for p in c["references"]]
        frozen = self.reviewer.encoder.encode(paths)
        mine = self.reviewer.encode_prepared([Path(p).read_bytes() for p in paths])
        for a, b in zip(frozen, mine):
            self.assertLess(float(np.abs(a.vector - b.vector).max()), 1e-6)
            self.assertEqual((a.source_sha256, a.dhash64, a.aspect_ratio), (b.source_sha256, b.dhash64, b.aspect_ratio))

    def test_example_request_ranks_like_the_research_cli(self):
        cats, images = {}, {}
        for c in self.example["candidates"]:
            ids = []
            for n, p in enumerate(c["references"]):
                ids.append(f"{c['id']}-{n}")
                images[f"{c['id']}-{n}_t"] = Path(p).read_bytes()
            cats[c["id"]] = {"photoIds": ids, "coverPhotoId": ids[0]}
        query = Path(self.example["query"]).read_bytes()
        answer, previews = self.reviewer.review(query, list(cats), lambda ids: {i: cats[i] for i in ids},
                                                lambda ids: {i: images.get(i) for i in ids})
        self.assertEqual(answer["ranked"][0]["id"], "00000028")
        self.assertEqual(len(answer["ranked"]), 8)
        self.assertTrue(all(len(r["photoIds"]) == 4 for r in answer["ranked"]))
        self.assertIsNotNone(previews["00000028"])

    def test_an_altered_bundle_does_not_load(self):
        pins = json.loads(json.dumps(review.MANIFEST[review.PIN]))
        pins["sha256"]["transforms.npz"] = "0" * 64
        with patch.dict(review.MANIFEST, {review.PIN: pins}):
            with self.assertRaises(ValueError):
                review.load_runtime(BUNDLE)


def tiny_jpeg(colour):
    buffer = io.BytesIO()
    Image.new("RGB", (40, 30), colour).save(buffer, "JPEG")
    return buffer.getvalue()


class Prepare(unittest.TestCase):
    def test_prepare_keeps_small_photos_and_refuses_png(self):
        out = review.prepare(tiny_jpeg((200, 100, 50)))
        with Image.open(io.BytesIO(out)) as im:
            self.assertEqual((im.format, im.size), ("JPEG", (40, 30)))
        png = io.BytesIO()
        Image.new("RGB", (8, 8)).save(png, "PNG")
        with self.assertRaises(Exception):
            review.prepare(png.getvalue())


if __name__ == "__main__":
    unittest.main(verbosity=2)
