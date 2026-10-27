"""Backfill for the 23 Oct schema (docs/SCHEMA_TRANSITION.md): brings the data that existing accounts made with
v0.1.0 into the shape the new rules and apps expect.

What it changes (the decisions are `plan()` in approved-extras.py, which has its own tests):
  * userLimits/{uid} for every profile, counted from its saved posts, blocked people and each chat's unread map.
    Without it nobody can message that person, and they can't save or block.
  * usernames/{lowercase name} claims for profiles that lack one (rules check the claim on every profile change).
  * hidden=false / mergedInto='' on cats that lack them (the map's cat query filters on both).
  * missing unread counts in chats (0), which the rules read for both members.
  * a public alert-area centre moves to users/{uid}/private/areaFollow; the public document keeps the neighbourhoods.
  * photos that public documents show get audience 'community' (the new rules show other photos to their owner only).
  * a photo sent in a chat is copied to a private c_ id bound to its message; the old copy is removed unless public
    content also shows it.

How it is safe to run:
  * Dry run unless --apply. A dry run reads only and writes a report (paths and field names, never values or photos).
  * --apply reads everything again and plans afresh. Every write is conditional: a document is only changed if it is
    unchanged since this run read it, and only created if it still doesn't exist. Anything that changed is skipped
    and reported; run again to pick it up. A second run with nothing left to do writes nothing.
  * A chat photo's copies, lease and message change are one atomic write; the old photo is removed only after every
    message that showed it moved. An existing userLimits document is never overwritten (the rules keep it right).
  * Problems the owner must decide (a username the new rules refuse, a username claimed by someone else, more than 500
    saved posts, a private area that conflicts) are listed as blockers and those documents are left alone.
  * Deploy the new rules first: from then on old app versions can't change the counters this run computes.

    python tools/migrate/schema_backfill.py --emulator                    local emulators (GCLOUD_PROJECT=demo-*)
    python tools/migrate/schema_backfill.py --project catmap-dubai        dry run against a real project
    python tools/migrate/schema_backfill.py --project catmap-dubai --apply --confirm catmap-dubai

A real project uses Google Application Default Credentials (the owner's own sign-in, which bypasses the rules).
"""
from __future__ import annotations

import argparse
import importlib.util
import json
import os
import re
import sys
import time
from pathlib import Path

HERE = Path(__file__).resolve().parent
_spec = importlib.util.spec_from_file_location("approved_extras", HERE / "approved-extras.py")
approved_extras = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(approved_extras)

IMAGE_FIELDS = ["ownerId", "audience", "chatId", "rootId", "messageId", "createdAt"]
BATCH = 50


class Snapshot:
    """Documents as plain dicts by path, plus the update time each was read at (the write precondition)."""

    def __init__(self):
        self.docs: dict[str, dict] = {}
        self.times: dict[str, object] = {}
        self.missing: set[str] = set()
        self.reads = 0
        self.list_counts: dict[str, dict] = {}

    def add(self, snap):
        self.reads += 1
        path = snap.reference.path
        if snap.exists:
            self.docs[path] = snap.to_dict() or {}
            self.times[path] = snap.update_time
            self.missing.discard(path)
        else:
            self.missing.add(path)


def read_snapshot(db, log=print) -> Snapshot:
    from google.cloud.firestore_v1.base_query import FieldFilter
    s = Snapshot()

    def stream(query, keep=lambda path: True):
        for snap in query.stream():
            if keep(snap.reference.path):
                s.add(snap)
            else:
                s.reads += 1

    def get_all(paths, fields=None):
        paths = [p for p in dict.fromkeys(paths) if p not in s.docs and p not in s.missing]
        for i in range(0, len(paths), 300):
            refs = [db.document(p) for p in paths[i:i + 300]]
            for snap in db.get_all(refs, field_paths=fields):
                s.add(snap)

    stream(db.collection("users"))
    uids = [p.split("/")[1] for p in s.docs if p.startswith("users/") and p.count("/") == 1]
    log(f"  {len(uids)} profiles")
    names = [s.docs["users/" + u].get("username", "") for u in uids]
    get_all(["usernames/" + n.lower() for n in names if isinstance(n, str) and re.fullmatch(r"[A-Za-z0-9_.]{3,20}", n)])
    get_all(["userLimits/" + u for u in uids])
    for u in uids:
        counts = {}
        for bucket in ("favorites", "blocked"):
            result = db.collection(f"users/{u}/{bucket}").count().get()
            counts[bucket] = int(result[0][0].value)
            s.reads += 1 + counts[bucket] // 1000
        s.list_counts[u] = counts
    stream(db.collection("chats"))
    # Per chat rather than one collection-group query: a filtered collection-group query needs an index the
    # project doesn't have. Three small queries per chat read only messages that carry a photo.
    for chat in [p for p in list(s.docs) if p.startswith("chats/") and p.count("/") == 1]:
        for key in ("imageId", "postPhotoId", "matchPhotoId"):
            query = db.collection(chat + "/messages").where(filter=FieldFilter(key, "!=", "")).select(["senderId", "imageId", "postPhotoId", "matchPhotoId"])
            before = s.reads
            stream(query, keep=lambda path: path not in s.docs)
            s.reads = max(s.reads, before + 1)  # an empty query still costs one read
    stream(db.collection("areaFollows"))
    get_all([f"users/{p.split('/')[1]}/private/areaFollow" for p, d in list(s.docs.items())
             if p.startswith("areaFollows/") and any(k in d for k in ("lat", "lng", "radiusKm"))])
    stream(db.collection("cats").select(["hidden", "mergedInto", "photoIds", "coverPhotoId"]))
    stream(db.collection("posts").select(["photoIds", "ownerPhotoId"]))
    for name in ("sightings", "rescues"):
        stream(db.collection(name).select(["photoIds"]))
    stream(db.collection("sitters").select(["photoId"]))
    stream(db.collection("adoptionRequests").select(["postPhotoId"]))
    stream(db.collection_group("updates").select(["photoId"]), keep=lambda path: path.startswith("posts/") and path.count("/") == 3)
    log(f"  {len(s.docs)} documents read so far")

    # Photos: the ones chats send are copied whole; the public ones only need their access fields.
    chat_images = {d.get("imageId") for p, d in s.docs.items() if p.startswith("chats/") and p.count("/") == 3 and d.get("imageId")}
    get_all([f"images/{i}{suffix}" for i in sorted(chat_images) for suffix in ("", "_t")])
    public = public_photo_ids(s.docs)
    get_all([f"images/{i}{suffix}" for i in sorted(public - chat_images) for suffix in ("", "_t")], fields=IMAGE_FIELDS)

    # Whatever the plan would create or replace must be read too, so every write can carry a precondition.
    for _ in range(3):
        plan = approved_extras.plan(s.docs, s.list_counts)
        unseen = [p for p in plan["replacements"] if p not in s.docs and p not in s.missing]
        if not unseen:
            break
        get_all(unseen)
    log(f"  {s.reads} reads in total")
    return s


def public_photo_ids(docs) -> set[str]:
    """The same public references plan() collects, so their photos can be read before planning."""
    ids = set()
    for path, data in docs.items():
        parts = path.split("/")
        if len(parts) == 2 and parts[0] in ("posts", "cats", "sightings", "rescues"):
            ids.update(i for i in data.get("photoIds", []) or [] if isinstance(i, str) and i)
        if (len(parts) == 2 and parts[0] in ("users", "sitters")) or (len(parts) == 4 and parts[0] == "posts" and parts[2] == "updates"):
            if isinstance(data.get("photoId"), str) and data["photoId"]:
                ids.add(data["photoId"])
        for key in ("ownerPhotoId", "coverPhotoId", "postPhotoId", "matchPhotoId"):
            if len(parts) in (2, 4) and isinstance(data.get(key), str) and data[key]:
                ids.add(data[key])
        if len(parts) == 2 and parts[0] == "chats":
            ids.update(v for v in (data.get("photos") or {}).values() if isinstance(v, str) and v)
    return {i for i in ids if not i.startswith("c_")}


def operations(s: Snapshot, plan) -> tuple[list[dict], list[str]]:
    """Turns the plan into groups of conditional writes, committed in order. An atomic group (a chat photo, a private
    area) is written all or nothing; any other group is a batch of independent writes."""
    notes = []
    ops = {}
    for path, value in sorted(plan["replacements"].items()):
        if path in s.docs:
            if path.startswith("userLimits/"):
                notes.append(f"{path}: exists, left as it is (counts differ from the recount: "
                             f"{sorted(k for k in value if s.docs[path].get(k) != value[k])})")
                continue
            before = s.docs[path]
            change = {k: v for k, v in value.items() if before.get(k, _ABSENT) != v}
            removed = [k for k in before if k not in value]
            for k in list(change) + removed:
                if not re.fullmatch(r"[A-Za-z_][A-Za-z0-9_]*", k):
                    raise ValueError(f"{path}: field name {k!r} needs quoting; not supported")
            ops[path] = {"path": path, "kind": "update", "set": change, "remove": removed}
        else:
            ops[path] = {"path": path, "kind": "create", "data": value}
    for path in plan["deletions"]:
        ops[path] = {"path": path, "kind": "delete"}

    groups, taken = [], set()

    def group(paths, atomic):
        g = [ops[p] for p in paths if p in ops and p not in taken]
        taken.update(op["path"] for op in g)
        if g:
            groups.append({"atomic": atomic, "ops": g, "after": []})

    # A chat photo: its two copies, its lease and the message move together.
    moved = {}
    for path, op in ops.items():
        parts = path.split("/")
        if len(parts) == 4 and parts[0] == "chats" and parts[2] == "messages" and op["kind"] == "update" and "imageId" in op["set"]:
            new = op["set"]["imageId"]
            moved.setdefault(s.docs[path].get("imageId"), []).append(path)
            group([path, f"images/{new}", f"images/{new}_t", f"mediaUploads/{new}"], True)
    # A private area: the private copy and the stripped public document together.
    for path in list(ops):
        if path.startswith("areaFollows/"):
            group([f"users/{path.split('/')[1]}/private/areaFollow", path], True)
    rest = [p for p in ops if p not in taken and not (ops[p]["kind"] == "delete" and p.startswith("images/"))]
    for i in range(0, len(rest), BATCH):
        group(rest[i:i + BATCH], False)
    # Old chat photos last, each only once all the messages that showed it have moved (see apply()).
    for path in sorted(p for p in ops if p not in taken):
        image = path.split("/")[1].removesuffix("_t")
        taken.add(path)
        groups.append({"atomic": True, "ops": [ops[path]], "after": moved.get(image, [])})
    return groups, notes


class _Absent:
    pass


_ABSENT = _Absent()


def apply(db, s: Snapshot, groups, log=print, before_write=None) -> dict:
    from google.api_core import exceptions
    from google.cloud import firestore
    done, skipped, failed_paths = 0, [], set()
    if before_write:
        before_write()

    def commit(g):
        batch = db.batch()
        for op in g:
            ref = db.document(op["path"])
            if op["kind"] == "create":
                batch.create(ref, op["data"])
            elif op["kind"] == "update":
                fields = {**op["set"], **{k: firestore.DELETE_FIELD for k in op["remove"]}}
                batch.update(ref, fields, option=db.write_option(last_update_time=s.times[op["path"]]))
            else:
                batch.delete(ref, option=db.write_option(last_update_time=s.times[op["path"]]))
        batch.commit()

    changed = (exceptions.FailedPrecondition, exceptions.AlreadyExists, exceptions.NotFound, exceptions.Aborted)
    for group in groups:
        g = group["ops"]
        if any(dep in failed_paths for dep in group["after"]):
            skipped += [(op["path"], "a message that shows it was not moved") for op in g]
            failed_paths.update(op["path"] for op in g)
            continue
        try:
            commit(g)
            done += len(g)
            continue
        except changed:
            pass
        if group["atomic"] or len(g) == 1:
            skipped += [(op["path"], "changed since it was read") for op in g]
            failed_paths.update(op["path"] for op in g)
            continue
        for op in g:  # independent writes: find the documents that changed and write the rest
            try:
                commit([op])
                done += 1
            except changed:
                skipped.append((op["path"], "changed since it was read"))
                failed_paths.add(op["path"])
    log(f"  {done} writes done, {len(skipped)} skipped")
    return {"written": done, "skipped": [f"{p}: {why}" for p, why in skipped]}


def summary(plan, groups, notes) -> dict:
    kinds = {}
    for g in groups:
        for op in g["ops"]:
            key = op["path"].split("/")[0] if op["path"].count("/") == 1 else "/".join(op["path"].split("/")[0::2])
            kinds.setdefault(f"{op['kind']} {key}", 0)
            kinds[f"{op['kind']} {key}"] += 1
    return {
        "writes": sum(len(g["ops"]) for g in groups),
        "byKind": dict(sorted(kinds.items())),
        "changes": [f"{op['kind']} {op['path']}" + (f" fields {sorted(op['set']) + sorted(op['remove'])}" if op["kind"] == "update" else "")
                    for g in groups for op in g["ops"]],
        "blockers": plan["blockers"],
        "notes": plan["notes"] + notes,
    }


def run(db, apply_changes: bool, log=print, before_write=None) -> dict:
    log("Reading...")
    s = read_snapshot(db, log)
    plan = approved_extras.plan(s.docs, s.list_counts)
    groups, notes = operations(s, plan)
    report = {"project": db.project, "applied": False, "reads": s.reads, **summary(plan, groups, notes)}
    log(f"  plan: {report['writes']} writes, {len(report['blockers'])} blockers, {len(report['notes'])} notes")
    if apply_changes:
        log("Writing...")
        report.update(apply(db, s, groups, log, before_write), applied=True)
    return report


def client(args):
    from google.cloud import firestore
    if args.emulator:
        sys.path.insert(0, str(HERE.parent / "worker"))
        from emulator_safety import require_emulator
        project = os.environ.get("GCLOUD_PROJECT", "demo-catmap")
        host = os.environ.setdefault("FIRESTORE_EMULATOR_HOST", "127.0.0.1:8080")
        require_emulator(project, host)
        from google.auth.credentials import AnonymousCredentials
        return firestore.Client(project=project, credentials=AnonymousCredentials())
    if os.environ.get("FIRESTORE_EMULATOR_HOST"):
        raise SystemExit("FIRESTORE_EMULATOR_HOST is set: use --emulator, or clear it for a real project.")
    if not args.project or args.project.startswith("demo-"):
        raise SystemExit("Name the real project with --project (or use --emulator).")
    if args.apply and args.confirm != args.project:
        raise SystemExit(f"Writing to {args.project} needs --confirm {args.project}.")
    return firestore.Client(project=args.project)


def main():
    p = argparse.ArgumentParser(description="Backfill for the 23 Oct schema (dry run unless --apply)")
    p.add_argument("--emulator", action="store_true", help="local emulators; GCLOUD_PROJECT must be demo-*")
    p.add_argument("--project", help="a real Firebase project (Application Default Credentials)")
    p.add_argument("--apply", action="store_true", help="write the changes (default: dry run)")
    p.add_argument("--confirm", help="the project name again, required with --apply on a real project")
    p.add_argument("--report", type=Path, help="where to write the JSON report (default: next to this script)")
    args = p.parse_args()
    db = client(args)
    print(f"{'APPLY' if args.apply else 'Dry run'}: project {db.project}{' (emulator)' if args.emulator else ''}")
    report = run(db, args.apply)
    stamp = time.strftime("%Y%m%d-%H%M%S")
    out = args.report or HERE / f"backfill-report-{db.project}-{stamp}.json"
    out.write_text(json.dumps(report, indent=2, default=str) + "\n", encoding="utf-8")
    print(f"\nReport: {out}")
    for b in report["blockers"]:
        print("  BLOCKER", b)
    for s in report.get("skipped", []):
        print("  SKIPPED", s)
    if report["blockers"] or report.get("skipped"):
        print("Nothing above was changed. Resolve the blockers, then run again; skipped writes are picked up next run.")


if __name__ == "__main__":
    main()
