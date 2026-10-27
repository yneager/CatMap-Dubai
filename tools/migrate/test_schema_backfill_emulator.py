"""Emulator rehearsal of schema_backfill.py on synthetic v0.1.0-shaped data. No credentials, loopback only.

    npx.cmd -y firebase-tools@15.34.0 emulators:exec --config firebase.rules-test.json --only firestore
      --project demo-catmap-backfill "python tools/migrate/test_schema_backfill_emulator.py && node tools/rulestest/after-backfill.mjs"

Leaves the migrated data in place for after-backfill.mjs, which checks the new rules accept the migrated accounts.
"""
import os
import sys
import time
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent / "worker"))
from emulator_safety import require_emulator  # noqa: E402

import importlib.util  # noqa: E402
spec = importlib.util.spec_from_file_location("schema_backfill", HERE / "schema_backfill.py")
backfill = importlib.util.module_from_spec(spec)
spec.loader.exec_module(backfill)

JPEG = b"\xff\xd8\xff\xe0" + bytes(60)
checks = 0


def check(condition, name):
    global checks
    if not condition:
        raise AssertionError(name)
    checks += 1
    print("  ok  ", name)


def seed(db):
    now = int(time.time() * 1000)
    old = now - 30 * 86400000

    def profile(name, photo=""):
        return {"username": name, "displayName": name, "bio": "", "photoId": photo, "role": "user", "volunteerStatus": "none",
                "stats": {}, "points": 0, "neighborhoods": [], "badges": [], "followedCats": [], "showStatsPublicly": True,
                "lastPostAt": None, "createdAt": old}
    docs = {
        "users/alice": profile("Alice", "pa"), "users/bob": profile("bob"), "users/carol": profile("carol"),
        "users/dave": profile("da"),  # too short for the new rules: a blocker for the owner
        "usernames/carol": {"uid": "carol"},
        "users/alice/favorites/p1": {"postId": "p1", "savedAt": old},
        "users/alice/blocked/dave": {"at": old},
        "posts/p1": {"ownerId": "alice", "ownerName": "Alice", "ownerPhotoId": "pa", "category": "lost", "status": "open", "name": "Tom",
                     "hidden": False, "photoIds": ["pp1"], "createdAt": old, "updatedAt": old},
        "cats/c1": {"name": "Ash", "photoIds": ["legacy2"], "coverPhotoId": "legacy2", "createdBy": "bob"},
        "cats/c2": {"name": "Smoke", "photoIds": [], "coverPhotoId": "", "createdBy": "bob"},
        "chats/alice_bob": {"members": ["alice", "bob"], "names": {"alice": "Alice", "bob": "bob"}, "photos": {"alice": "pa", "bob": ""},
                            "unread": {"alice": 0, "bob": 2}, "lastText": "Post: Tom", "lastSender": "alice", "lastAt": old},
        "chats/alice_bob/messages/m1": {"senderId": "alice", "text": "", "imageId": "legacy1", "createdAt": old},
        "chats/alice_bob/messages/m2": {"senderId": "alice", "text": "", "postId": "p1", "postTitle": "Tom", "postPhotoId": "pp1", "createdAt": old + 1},
        "chats/bob_carol": {"members": ["bob", "carol"], "names": {"bob": "bob", "carol": "carol"}, "photos": {"bob": "", "carol": ""},
                            "unread": {"bob": 1}, "lastText": "Photo", "lastSender": "carol", "lastAt": old},
        "chats/bob_carol/messages/n1": {"senderId": "bob", "text": "", "imageId": "legacy2", "createdAt": old},
        "areaFollows/alice": {"neighborhoods": ["deira"], "lat": 25.27, "lng": 55.3, "radiusKm": 3, "updatedAt": old},
    }
    for image, owner in [("pa", "alice"), ("pp1", "alice"), ("legacy1", "alice"), ("legacy2", "bob"), ("orphan", "alice")]:
        for suffix in ("", "_t"):
            docs[f"images/{image}{suffix}"] = {"data": JPEG, "ownerId": owner, "createdAt": old}
    for path, data in docs.items():
        db.document(path).set(data)


def main():
    project = os.environ.get("GCLOUD_PROJECT", "demo-catmap-backfill")
    host = os.environ.get("FIRESTORE_EMULATOR_HOST", "127.0.0.1:8180")
    require_emulator(project, host)
    os.environ["FIRESTORE_EMULATOR_HOST"] = host
    from google.auth.credentials import AnonymousCredentials
    from google.cloud import firestore
    db = firestore.Client(project=project, credentials=AnonymousCredentials())
    for c in db.collections():
        db.recursive_delete(c)
    seed(db)
    if os.environ.get("BACKFILL_SEED_ONLY") == "1":  # control run: after-backfill.mjs must fail on unmigrated data
        print("Seeded only; no backfill.")
        return
    get = lambda path: db.document(path).get()  # noqa: E731
    quiet = lambda *_: None  # noqa: E731

    dry = backfill.run(db, False, quiet)
    check(not dry["applied"] and dry["writes"] > 0, "dry run plans writes")
    check(not get("userLimits/alice").exists and "lat" in get("areaFollows/alice").to_dict(), "dry run writes nothing")
    check(any(b.startswith("users/dave:") for b in dry["blockers"]), "invalid username is a blocker")
    check(not any("dave" in c and "usernames" in c for c in dry["changes"]), "no claim for the blocked username")
    check(all("lat" not in c and "data" not in c for c in dry["changes"] if "fields" not in c), "report carries no values")

    # Someone edits cat c2 between the read and the write: that write must be skipped, the rest go ahead.
    def edit_c2():
        db.document("cats/c2").update({"name": "Smokey"})
    first = backfill.run(db, True, quiet, before_write=edit_c2)
    check(first["applied"] and first["written"] > 0, "apply writes")
    check(first["skipped"] == ["cats/c2: changed since it was read"], "a changed document is skipped, nothing else")
    c2 = get("cats/c2").to_dict()
    check(c2["name"] == "Smokey" and "hidden" not in c2, "the changed cat is left as the editor saved it")

    limits = {u: get(f"userLimits/{u}").to_dict() for u in ("alice", "bob", "carol", "dave")}
    check(limits["alice"] == {"favorites": 1, "blocked": 1, "unreadCount": 0}, "alice's counters from her lists")
    check(limits["bob"] == {"favorites": 0, "blocked": 0, "unreadCount": 3}, "bob's unread total from both chats")
    check(limits["carol"] == {"favorites": 0, "blocked": 0, "unreadCount": 0}, "carol's counters")
    check(limits["dave"] == {"favorites": 0, "blocked": 0, "unreadCount": 0}, "a blocked username still gets counters")
    check(get("usernames/alice").to_dict() == {"uid": "alice"} and get("usernames/bob").to_dict() == {"uid": "bob"}, "missing claims created")
    check(get("usernames/carol").to_dict() == {"uid": "carol"} and not get("usernames/da").exists, "existing claim kept, invalid one not made")
    c1 = get("cats/c1").to_dict()
    check(c1["hidden"] is False and c1["mergedInto"] == "" and c1["name"] == "Ash", "cat visibility defaults, other fields kept")
    check(get("chats/bob_carol").to_dict()["unread"] == {"bob": 1, "carol": 0}, "missing unread count filled in")
    area = get("areaFollows/alice").to_dict()
    private = get("users/alice/private/areaFollow").to_dict()
    check(area == {"neighborhoods": ["deira"], "updatedAt": area["updatedAt"]}, "public area keeps only neighbourhoods")
    check(private["lat"] == 25.27 and private["radiusKm"] == 3, "centre moved to the private document")
    for image in ("pa", "pa_t", "pp1", "pp1_t", "legacy2", "legacy2_t"):
        d = get("images/" + image).to_dict()
        check(d["audience"] == "community" and d["chatId"] == "" and d["rootId"] == image.removesuffix("_t") and d["data"] == JPEG,
              f"images/{image} is a community photo")
    check(get("images/orphan").to_dict().get("audience") is None, "an unreferenced old photo stays owner-only")
    m1 = get("chats/alice_bob/messages/m1").to_dict()
    copy = get("images/" + m1["imageId"]).to_dict()
    check(m1["imageId"].startswith("c_") and copy["audience"] == "chat" and copy["chatId"] == "alice_bob"
          and copy["messageId"] == "m1" and copy["data"] == JPEG and get(f"images/{m1['imageId']}_t").exists, "chat photo copied and bound")
    lease = get("mediaUploads/" + m1["imageId"]).to_dict()
    check(lease["ownerId"] == "alice" and lease["status"] == "linked", "the copy has a linked lease")
    check(not get("images/legacy1").exists and not get("images/legacy1_t").exists, "the old private chat photo is gone")
    n1 = get("chats/bob_carol/messages/n1").to_dict()
    check(n1["imageId"].startswith("c_") and get("images/legacy2").exists, "a chat photo also on a cat: copied, public original kept")

    second = backfill.run(db, True, quiet)
    check(second["writes"] == 1 and second["changes"][0].startswith("update cats/c2"), "second run only picks up the skipped cat")
    third = backfill.run(db, True, quiet)
    check(third["writes"] == 0 and not third["skipped"], "third run writes nothing")
    check(get("userLimits/bob").to_dict()["unreadCount"] == 3, "counters not touched again")
    print(f"Schema backfill emulator: {checks} checks passed")


if __name__ == "__main__":
    main()
