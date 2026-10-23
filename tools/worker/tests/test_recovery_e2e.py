"""End-to-end test of the background matcher (recovery.py) with the real models against a Firestore emulator.

    (in the cat project)  npx.cmd -y firebase-tools@latest emulators:exec --config firebase.rules-test.json
                          --only firestore --project demo-catmap-recovery "..\\cat-server\\.venv\\Scripts\\python.exe ..\\cat-server\\tests\\test_recovery_e2e.py"

Photos are real (DogFaceNet, tests/fixtures): two photos of one dog ("Max") and a different dog that looks alike.
Scenarios (numbers from the brief's test list):
  1-4   A lost report, then a sighting of the same dog: a match, an alert for the owner and the reporter.
  13    A look-alike different dog: suggested at most weakly, never "strong", no alert.
  7-8   The owner rejects it: never upgraded or announced again, even when its photos change.
  11    A sighting posted *before* the lost report is still found.
  9-10  A reunited report isn't matched any more.
        Restarting the worker (catch-up) creates no duplicate alerts or matches.
  15    Old posts without photos or fingerprints don't break anything (details-only matching).
        Posts saved without map-area codes (v0.0.9) get them, without a new updatedAt.
"""
from __future__ import annotations

import os
import queue
import sys
import time
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent))
os.environ.setdefault("FIRESTORE_EMULATOR_HOST", "127.0.0.1:8180")

from emulator_safety import require_emulator
if os.environ.get('CATMAP_REAL_MODEL_TESTS') != '1':
    print('SKIP real-model test: set CATMAP_REAL_MODEL_TESTS=1 and supply reviewed fixtures/pinned models')
    if __name__ == '__main__': raise SystemExit(0)
    import unittest
    raise unittest.SkipTest('Opt-in real-model emulator integration')
require_emulator(os.environ.get('GCLOUD_PROJECT','demo-catmap-recovery'),os.environ['FIRESTORE_EMULATOR_HOST'])
from google.auth.credentials import AnonymousCredentials  # noqa: E402
from google.cloud import firestore  # noqa: E402

import geocells  # noqa: E402
import recognition  # noqa: E402
from recovery import Recovery  # noqa: E402

PROJECT = os.environ.get("GCLOUD_PROJECT", "demo-catmap-recovery")
assert PROJECT.startswith("demo-"), "only ever run against an emulator project"
db = firestore.Client(project=PROJECT, credentials=AnonymousCredentials())
DAY = 86_400_000
NOW = int(time.time() * 1000)
passed = failed = 0


def check(name: str, ok: bool, detail: str = ""):
    global passed, failed
    if ok:
        passed += 1
        print(f"  ok    {name}")
    else:
        failed += 1
        print(f"  FAIL  {name} {detail}")


def clear():
    for col in ("posts", "images", "matches", "postEmbeddings", "users"):
        for d in db.collection(col).list_documents():
            if col == "users":
                for n in d.collection("notifications").list_documents():
                    n.delete()
            d.delete()


def image(pid: str, path: Path):
    db.document(f"images/{pid}").set({"data": path.read_bytes(), "ownerId": "x", "createdAt": NOW})


def post(pid: str, owner: str, category: str, photos: list[str], lat: float, days: float = 0, **extra):
    d = {"ownerId": owner, "ownerName": owner, "animalType": "dog", "category": category, "status": "open", "name": "",
         "description": "", "color": "brown", "features": "", "photoIds": photos, "lat": lat, "lng": 55.2708,
         "approximate": False, "areaRadiusM": 500 if category == "lost" else 0, "neighborhood": "jumeirah",
         "lastSeenAt": NOW + int(days * DAY), "hidden": False, "createdAt": NOW + int(days * DAY), "updatedAt": NOW}
    d.update(extra)
    db.document(f"posts/{pid}").set(d)


def alerts(uid: str) -> list[str]:
    return sorted(d.id for d in db.collection(f"users/{uid}/notifications").list_documents())


def match(mid: str) -> dict | None:
    s = db.document(f"matches/{mid}").get()
    return s.to_dict() if s.exists else None


def run(rec: Recovery, seconds: float = 25):
    """Lets the listeners deliver, then works until the queue is empty."""
    end = time.time() + seconds
    time.sleep(1.5)
    while time.time() < end:
        rec.work(5)
        time.sleep(0.5)
        if not rec.pending():
            time.sleep(1.0)
            if not rec.pending():
                return


def main():
    fx = HERE / "fixtures"
    for name in ('max_lost.jpg','max_sighting.jpg','lookalike.jpg'):
        if not (fx/name).is_file(): raise SystemExit('Missing reviewed real-model fixture; no emulator data was cleared')
    clear()
    image("img_max1", fx / "max_lost.jpg")
    image("img_max2", fx / "max_sighting.jpg")
    image("img_look", fx / "lookalike.jpg")
    events: queue.Queue = queue.Queue()
    recognizer = recognition.Recognizer(cpu=False)
    rec = Recovery(db, recognizer, events, "catmap-ai-worker")
    rec.start()

    print("Lost report, then sightings")
    post("L1", "owner", "lost", ["img_max1"], 25.2048, name="Max", color="brown white")
    post("S1", "reporter", "spotted", ["img_max2"], 25.2148, days=1)
    post("S2", "stranger", "spotted", ["img_look"], 25.2100, days=1)
    run(rec)
    m1, m2 = match("L1__S1"), match("L1__S2")
    print(f"  (same dog: {m1 and (m1['tier'], m1['visual'])}; look-alike: {m2 and (m2['tier'], m2['visual'])})")
    check("same dog: a match is suggested", m1 is not None and m1["tier"] in ("strong", "possible"), str(m1 and m1["tier"]))
    check("match is scored by the AI model, versioned", m1 is not None and m1["method"] == "embedding" and m1["model"] == recognition.EMBEDDING_MODEL)
    check("both people are members, nobody answered yet", m1 is not None and m1["members"] == ["owner", "reporter"] and m1["ownerState"] == "new")
    check("owner is alerted once", alerts("owner") == ["pm_L1__S1"], str(alerts("owner")))
    check("reporter is alerted once", alerts("reporter") == ["pr_L1__S1"], str(alerts("reporter")))
    alert = db.document("users/owner/notifications/pm_L1__S1").get().to_dict() or {}
    check("alert has no exact place or contact", set(alert.get("params", {})) <= {"name", "type", "area", "lostArea", "tier", "matchId"} and alert.get("route") == "match/L1__S1")
    check("look-alike dog is never 'strong'", m2 is None or m2["tier"] in ("weak", "none"), str(m2 and m2["tier"]))
    check("look-alike dog gets no alert", alerts("stranger") == [], str(alerts("stranger")))
    check("embeddings stored per photo, worker-only collection", len(db.document("postEmbeddings/L1").get().to_dict()["vectors"]) == 1)
    l1 = db.document("posts/L1").get().to_dict()
    check("older post gets its map-area codes", l1.get("geoCells") == geocells.cells(25.2048, 55.2708), str(l1.get("geoCells")))
    check("adding area codes doesn't touch updatedAt", l1.get("updatedAt") == NOW)

    print("Owner rejects; photos change")
    if m2 is None:  # make sure there is a pair to reject
        db.document("matches/L1__S2").set({"lostPostId": "L1", "sightingPostId": "S2", "lostOwnerId": "owner", "sightingOwnerId": "stranger",
                                           "members": ["owner", "stranger"], "animalType": "dog", "tier": "weak", "score": 0.2, "ownerState": "new",
                                           "reporterState": "new", "notified": False})
    db.document("matches/L1__S2").update({"ownerState": "not_mine"})
    time.sleep(1.0)
    image("img_max3", fx / "max_sighting.jpg")
    db.document("posts/S2").update({"photoIds": ["img_max3"]})  # now even looks like Max
    run(rec)
    m2 = match("L1__S2")
    check("rejected pair stays rejected", m2["ownerState"] == "not_mine")
    check("rejected pair is not re-scored upward", m2["tier"] in ("weak", "none"), m2["tier"])
    check("rejected pair is never announced", "pm_L1__S2" not in alerts("owner"))

    print("Sighting first, lost report later")
    post("S3", "reporter2", "found", ["img_max2"], 25.3000, days=-1)
    time.sleep(1.0)
    post("L2", "owner2", "lost", ["img_max1"], 25.3020, days=-2, name="Buddy")
    run(rec)
    m3 = match("L2__S3")
    check("earlier sighting is found for the new report", m3 is not None and m3["tier"] in ("strong", "possible"), str(m3 and m3["tier"]))
    check("its owner is alerted", "pm_L2__S3" in alerts("owner2"))

    print("Owner's report: the exact photo already posted as a street cat days earlier")
    post("S9", "streetposter", "street", ["img_max1"], 25.5000, days=-2.5)
    time.sleep(1.0)
    post("L9", "owner9", "lost", ["img_max1"], 25.5005, name="Same photo")  # last seen left at "now"
    run(rec)
    m9 = match("L9__S9")
    print(f"  (same photo: {m9 and (m9['tier'], m9['visual'], m9['reasons'])})")
    check("same photo posted earlier as a street cat is found", m9 is not None and m9["tier"] in ("strong", "possible"), str(m9))
    check("listeners are healthy", rec.healthy())

    print("Reunited report")
    db.document("posts/L1").update({"status": "reunited", "resolution": "catmap", "resolvedMatchId": "L1__S1"})
    time.sleep(1.0)
    post("S4", "reporter3", "spotted", ["img_max2"], 25.2050, days=2)
    run(rec)
    check("a reunited report gets no new matches", match("L1__S4") is None)
    check("its owner gets no new alerts", alerts("owner") == ["pm_L1__S1"], str(alerts("owner")))

    print("Old records without photos")
    post("L3", "owner3", "lost", [], 25.4000, color="black")
    post("S5", "reporter4", "spotted", ["missing_image"], 25.4010, days=1, color="black")
    run(rec)
    m5 = match("L3__S5")
    check("photo-less pair is matched on details only, at most weak", m5 is None or (m5["method"] == "details" and m5["tier"] in ("weak", "none")), str(m5))

    print("Quick refresh (the 50-minute token refresh re-reads only what changed)")
    import recovery as recovery_module
    full_before = rec.full_since
    overlap = recovery_module.REFRESH_OVERLAP_MS
    # The posts above carry an older updatedAt, so with no overlap a refresh must return none of them.
    recovery_module.REFRESH_OVERLAP_MS = 0
    first_snapshot: list[int] = []
    on_posts = rec.on_posts

    def counting(docs, changes, read_time):
        first_snapshot.append(len(changes))
        on_posts(docs, changes, read_time)

    rec.on_posts = counting  # listen() subscribes self.on_posts
    rec.restart()
    time.sleep(2.0)
    check("a refresh doesn't re-read the whole window", rec.full_since == full_before and (not first_snapshot or first_snapshot[0] == 0),
          str(first_snapshot))
    post("S10", "reporter5", "spotted", ["img_max2"], 25.5010, days=0.5, updatedAt=int(time.time() * 1000))
    run(rec)
    m10 = match("L9__S10")
    check("a sighting posted after a quick refresh is still matched", m10 is not None and m10["tier"] in ("strong", "possible"), str(m10))
    recovery_module.REFRESH_OVERLAP_MS = overlap
    rec.on_posts = on_posts
    check("listeners are healthy after the refresh", rec.healthy())

    print("Restart (catch-up)")
    before_alerts = {u: alerts(u) for u in ("owner", "reporter", "owner2", "reporter2", "stranger")}
    before_matches = sorted(d.id for d in db.collection("matches").list_documents())
    rec.stop()
    rec2 = Recovery(db, recognizer, events, "catmap-ai-worker")
    rec2.start()
    run(rec2)
    check("no duplicate alerts after a restart", {u: alerts(u) for u in before_alerts} == before_alerts)
    check("no duplicate matches after a restart", sorted(d.id for d in db.collection("matches").list_documents()) == before_matches)
    check("photos aren't embedded again", rec2.galleries.keys() >= {"L1", "S1"})
    print("Deleted posts lose their stored vectors and in-memory galleries")
    db.document("posts/L1").delete()
    run(rec2)
    check("deleted post embedding is removed", not db.document("postEmbeddings/L1").get().exists)
    check("deleted post gallery is removed", "L1" not in rec2.galleries)
    db.document("posts/S1").update({"hidden": True})
    run(rec2)
    check("hidden post keeps its stored embedding", db.document("postEmbeddings/S1").get().exists)
    check("hidden post leaves the active gallery", "S1" not in rec2.galleries)
    rec2.stop()
    db.document("postEmbeddings/orphan").set({"model": recognition.EMBEDDING_MODEL, "vectors": []})
    rec3 = Recovery(db, recognizer, events, "catmap-ai-worker")
    rec3.load_embeddings()
    rec3.sweep_orphans()
    check("bounded sweep prunes orphan embeddings", not db.document("postEmbeddings/orphan").get().exists)
    check("startup preserves hidden post embeddings", db.document("postEmbeddings/S1").get().exists)
    print(f"\n{passed} passed, {failed} failed")
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
