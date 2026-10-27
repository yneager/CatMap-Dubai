"""Offline tests for schema_backfill.py's write plan and the planner additions it relies on (no Firebase needed).
The emulator rehearsal is test_schema_backfill_emulator.py."""
import importlib.util
import unittest
from pathlib import Path

HERE = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location("schema_backfill", HERE / "schema_backfill.py")
backfill = importlib.util.module_from_spec(spec)
spec.loader.exec_module(backfill)
plan = backfill.approved_extras.plan


def snapshot(docs):
    s = backfill.Snapshot()
    s.docs = docs
    s.times = {p: f"t-{p}" for p in docs}
    return s


class PlannerAdditions(unittest.TestCase):
    def test_list_counts_replace_counting_paths(self):
        p = plan({"users/a": {"username": "anna"}}, {"a": {"favorites": 7, "blocked": 2}})
        self.assertEqual(p["replacements"]["userLimits/a"], {"favorites": 7, "blocked": 2, "unreadCount": 0})

    def test_over_capacity_gets_no_counters(self):
        p = plan({"users/a": {"username": "anna"}}, {"a": {"favorites": 501}})
        self.assertNotIn("userLimits/a", p["replacements"])
        self.assertTrue(any("exceeds capacity" in b for b in p["blockers"]))

    def test_invalid_username_still_gets_counters(self):
        p = plan({"users/a": {"username": "a b"}})
        self.assertIn("userLimits/a", p["replacements"])
        self.assertFalse(any(k.startswith("usernames/") for k in p["replacements"]))

    def test_existing_counter_fields_are_kept(self):
        docs = {"users/a": {"username": "anna"}, "usernames/anna": {"uid": "a"},
                "userLimits/a": {"favorites": 0, "blocked": 0, "unreadCount": 0, "favoritesSource": "p"}}
        self.assertNotIn("userLimits/a", plan(docs)["replacements"])

    def test_copies_of_public_photos_are_community(self):
        docs = {f"images/{i}{s}": {"ownerId": "a"} for i in ("own", "cov", "req", "card", "face") for s in ("", "_t")}
        docs.update({"posts/p": {"ownerPhotoId": "own", "photoIds": []}, "cats/c": {"coverPhotoId": "cov", "photoIds": [], "hidden": False, "mergedInto": ""},
                     "adoptionRequests/r": {"postPhotoId": "req"}, "chats/a_b": {"members": ["a", "b"], "unread": {"a": 0, "b": 0}, "photos": {"a": "face"}},
                     "chats/a_b/messages/m": {"senderId": "a", "matchPhotoId": "card"}})
        r = plan(docs)["replacements"]
        for i in ("own", "cov", "req", "card", "face"):
            self.assertEqual(r[f"images/{i}_t"]["audience"], "community", i)

    def test_missing_unread_counts_are_zero(self):
        r = plan({"chats/a_b": {"members": ["a", "b"], "unread": {"a": 2}}})["replacements"]
        self.assertEqual(r["chats/a_b"]["unread"], {"a": 2, "b": 0})


class WritePlan(unittest.TestCase):
    def fixture(self):
        return {"users/alice": {"username": "alice"}, "usernames/alice": {"uid": "alice"},
                "chats/alice_bob": {"members": ["alice", "bob"], "unread": {"alice": 0, "bob": 1}},
                "chats/alice_bob/messages/m": {"senderId": "alice", "imageId": "old"},
                "images/old": {"ownerId": "alice", "data": b"x", "createdAt": 1}, "images/old_t": {"ownerId": "alice", "data": b"x", "createdAt": 1},
                "areaFollows/alice": {"neighborhoods": ["deira"], "lat": 25, "lng": 55, "radiusKm": 3, "updatedAt": 1}}

    def test_updates_carry_only_changes_and_removals(self):
        s = snapshot(self.fixture())
        groups, _ = backfill.operations(s, plan(s.docs))
        ops = {op["path"]: op for g in groups for op in g["ops"]}
        self.assertEqual(ops["areaFollows/alice"]["set"], {})
        self.assertEqual(sorted(ops["areaFollows/alice"]["remove"]), ["lat", "lng", "radiusKm"])
        self.assertEqual(ops["userLimits/alice"]["kind"], "create")
        self.assertEqual(list(ops["chats/alice_bob/messages/m"]["set"]), ["imageId"])

    def test_chat_photo_and_area_are_atomic_and_deletion_waits(self):
        s = snapshot(self.fixture())
        groups, _ = backfill.operations(s, plan(s.docs))
        message = next(g for g in groups if any(op["path"].endswith("/messages/m") for op in g["ops"]))
        self.assertTrue(message["atomic"])
        self.assertEqual(len(message["ops"]), 4)  # message, two copies, lease
        area = next(g for g in groups if any(op["path"] == "areaFollows/alice" for op in g["ops"]))
        self.assertEqual({op["path"] for op in area["ops"]}, {"areaFollows/alice", "users/alice/private/areaFollow"})
        deletions = [g for g in groups if g["ops"][0]["kind"] == "delete"]
        self.assertEqual({g["ops"][0]["path"] for g in deletions}, {"images/old", "images/old_t"})
        self.assertTrue(all(g["after"] == ["chats/alice_bob/messages/m"] for g in deletions))
        self.assertGreater(groups.index(deletions[0]), groups.index(message))

    def test_existing_user_limits_are_never_overwritten(self):
        docs = self.fixture()
        docs["userLimits/alice"] = {"favorites": 3, "blocked": 0, "unreadCount": 0}
        s = snapshot(docs)
        groups, notes = backfill.operations(s, plan(s.docs, {"alice": {"favorites": 1, "blocked": 0}}))
        self.assertFalse(any(op["path"] == "userLimits/alice" for g in groups for op in g["ops"]))
        self.assertTrue(any(n.startswith("userLimits/alice: exists") for n in notes))

    def test_report_has_paths_and_field_names_only(self):
        s = snapshot(self.fixture())
        p = plan(s.docs)
        groups, notes = backfill.operations(s, p)
        text = repr(backfill.summary(p, groups, notes))
        self.assertNotIn("25", text)
        self.assertNotIn("b'x'", text)


if __name__ == "__main__":
    unittest.main(verbosity=2)
