"""Photo review end to end on a demo Firestore emulator: real Worker.handle, real Firestore reads of cats and
thumbnails, the real review model. Needs this PC's git-ignored review bundle and the worker's venv.

    npx firebase-tools emulators:exec --config firebase.rules-test.json --only firestore --project demo-catmap-review
        "<cat-server venv python> tools/worker/tests/test_review_emulator.py"
"""
import io
import json
import os
import queue
import sys
import time
from pathlib import Path
from types import SimpleNamespace

from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
from emulator_safety import require_emulator  # noqa: E402


def thumb(path):
    """What the apps upload as {id}_t: at most 360 px, JPEG quality 72."""
    image = Image.open(path).convert("RGB")
    image.thumbnail((360, 360))
    buffer = io.BytesIO()
    image.save(buffer, "JPEG", quality=72)
    return buffer.getvalue()


def main():
    project = os.environ.get("GCLOUD_PROJECT", "demo-catmap-review")
    host = os.environ.get("FIRESTORE_EMULATOR_HOST", "127.0.0.1:8180")
    require_emulator(project, host)
    os.environ["FIRESTORE_EMULATOR_HOST"] = host
    from google.auth.credentials import AnonymousCredentials
    from google.cloud import firestore
    import review
    import server

    bundle = review.find_bundle()
    example = json.loads((bundle / "example_request.json").read_text())
    db = firestore.Client(project=project, credentials=AnonymousCredentials())
    now = int(time.time() * 1000)

    def photo(photo_id, path):
        db.document(f"images/{photo_id}_t").set({"data": thumb(path), "ownerId": "seed", "audience": "community",
                                                  "chatId": "", "rootId": photo_id, "messageId": "", "createdAt": now})

    ids = []
    for c in example["candidates"]:
        photo_ids = []
        for n, path in enumerate(c["references"]):
            photo_ids.append(f"rv_{c['id']}_{n}")
            photo(photo_ids[-1], path)
        db.document(f"cats/rv_{c['id']}").set({"name": "Cat " + c["id"][-3:], "photoIds": photo_ids, "coverPhotoId": photo_ids[0],
                                                "hidden": False, "mergedInto": "", "lat": 25.2, "lng": 55.27})
        ids.append(f"rv_{c['id']}")
    first = example["candidates"][1]["references"]
    db.document("cats/rv_hidden").set({"name": "Hidden", "photoIds": ["rv_00000061_0", "rv_00000061_1"], "hidden": True, "mergedInto": ""})
    photo("rv_solo_0", first[0])
    db.document("cats/rv_solo").set({"name": "Solo", "photoIds": ["rv_solo_0"], "hidden": False, "mergedInto": ""})
    asked = ids[:6] + ["rv_hidden", "rv_solo"]

    worker = server.Worker.__new__(server.Worker)
    worker.fs = SimpleNamespace(client=db, emulator=True)
    worker.reviewer = review.Reviewer(cpu=False)
    worker.events, worker.answered, worker.handled, worker.names = queue.Queue(), set(), 0, {}
    worker.retry_jobs, worker.retry_attempts = {}, {}
    worker.model = SimpleNamespace(provider=worker.reviewer.provider)
    query = thumb(example["query"])
    timings = []
    for request_id in ("rv-cold", "rv-warm"):
        job = db.document("aiJobs/rv_moderator")
        job.set({"uid": "rv_moderator", "kind": "review", "status": "pending", "query": query, "images": [], "photos": [],
                 "ids": asked, "labels": [a[3:] for a in asked], "requestId": request_id, "createdAt": firestore.SERVER_TIMESTAMP})
        started = time.perf_counter()
        worker.handle(job.get())
        timings.append(time.perf_counter() - started)
        answer = job.get().to_dict()
        assert answer["status"] == "done" and answer["answeredRequestId"] == request_id, answer.get("error")
        result = answer["review"]
        assert result["protocol"] == "review-v1"
        assert result["ranked"][0]["id"] == "rv_00000028", result["ranked"][0]
        assert len(result["ranked"]) == 6 and all(len(r["photoIds"]) == 4 for r in result["ranked"])
        assert {u["id"]: u["reason"] for u in result["unavailable"]} == {"rv_hidden": "hidden", "rv_solo": "too_few_photos"}
        assert "results" not in answer and "verdict" not in json.dumps(result)
    # Firestore counts a document as field names plus values; the job is the query photo plus small fields.
    job_bytes = len(query) + sum(len(a) + 1 for a in asked) * 2 + 200
    print(f"Photo review emulator: 2 requests passed (cold {timings[0]:.2f} s, warm {timings[1]:.2f} s, "
          f"{worker.reviewer.provider}); job document about {job_bytes / 1024:.1f} KB with a {len(query) / 1024:.1f} KB photo")


if __name__ == "__main__":
    main()
