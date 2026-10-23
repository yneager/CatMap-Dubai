"""Background matching of lost reports and sightings (the "lost to reunited" workflow), run by the PC worker.

Nothing has to ask for it: the worker keeps one live listener on recent posts. When a lost report or a sighting
(found, spotted or street-animal post) is published or its photos change, it:
  1. finds the animal in each photo, crops it and embeds it (recognition.py), and stores the vectors in
     `postEmbeddings/{postId}` (one 512-d vector per photo, versioned, so a model change recomputes them);
  2. compares it with the other side: every open lost report (for a sighting) or every open sighting (for a lost
     report) of the same animal type within reach, in both directions, so a sighting posted before the lost report is
     still found;
  3. scores each pair with matching.py (photo evidence first, place and time as context) and writes
     `matches/{lostId}__{sightingId}`: one document per pair, so repeats are harmless;
  4. tells the lost animal's owner (and the person who saw it) once per pair, for "possible" or "strong" matches
     only, with an inbox notification whose id is fixed (`pm_{matchId}`), so it can't be sent twice.

A pair the owner rejected ("not my animal") is never upgraded or announced again. The worker never changes a
person's answer, never merges anything and never decides that two animals are the same.
When the PC is off, phones write the same match documents from their colour fingerprint (MatchEngine.kt / .ts);
when the PC comes back, the startup catch-up re-scores them with the AI model.
"""
from __future__ import annotations

import hashlib
import math
from itertools import islice
from collections import OrderedDict
from work_queue import WorkQueue
import queue
import threading
import time
from dataclasses import dataclass, field

import numpy as np

import geocells
import matching
import recognition
import searchterms

WINDOW_DAYS = 60                    # reports and sightings older than this aren't watched
LOST = {"lost"}
SIGHTINGS = {"found", "spotted", "street"}
PREFILTER_KM = 45.0                 # cheap distance cut before scoring (well beyond matching.ROAM_MAX_KM)
NOTIFY_TIERS = {"strong", "possible"}
NOTIFY_TOP = 3                      # announce at most the best 3 candidates of a lost report
MATCH_FIELDS = ["score", "visual", "tier", "method", "model", "distanceKm", "daysApart", "reasons", "source", "updatedAt"]
# Every listener opening is billed one read per document it returns. The worker re-opens its listeners about every
# 50 minutes (fresh sign-in token), so a re-open asks only for what changed since the last data it saw; the full window
# is read at start and every 12 hours, which also catches what a quick refresh can't see (posts that were hidden).
FULL_RESYNC_SECONDS = 12 * 3600
REFRESH_OVERLAP_MS = 60 * 60 * 1000  # phones stamp updatedAt with their own clock; allow an hour of drift


@dataclass
class Gallery:
    signature: str
    vectors: np.ndarray             # (photos, 512)
    mean: np.ndarray = field(init=False)
    def __post_init__(self):
        v = self.vectors.mean(0)
        self.mean = v / max(1e-9, np.linalg.norm(v))


def post_time(p: dict) -> int:
    return int(p.get("lastSeenAt") or 0) or int(p.get("createdAt") or 0)


def colour_words(p: dict) -> set[str]:
    words = (str(p.get("color", "")) + " " + str(p.get("features", ""))).lower().replace(",", " ").split()
    return {w for w in words if len(w) >= 3}


def side(p: dict) -> matching.Side:
    return matching.Side(float(p.get("lat", 0)), float(p.get("lng", 0)), post_time(p), bool(p.get("approximate")),
                         int(p.get("areaRadiusM") or 0) if p.get("category") == "lost" else 0,
                         bool(p.get("photoIds")), colour_words(p))


def signature(p: dict) -> str:
    return hashlib.sha1(("|".join(p.get("photoIds", [])) + "|" + recognition.EMBEDDING_MODEL).encode()).hexdigest()[:16]


def visual_similarity(a: np.ndarray, b: np.ndarray) -> float:
    """Half the best photo pair, half the averaged galleries: the combination that did best in the evaluation."""
    best = float((a @ b.T).max())
    ma, mb = a.mean(0), b.mean(0)
    mean = float(ma @ mb / max(1e-9, np.linalg.norm(ma) * np.linalg.norm(mb)))
    return 0.5 * best + 0.5 * mean


class Recovery:
    def __init__(self, client, recognizer: recognition.Recognizer | None, events: queue.Queue, worker_uid: str, now=time.time):
        self.db, self.rec, self.events, self.uid, self.now = client, recognizer, events, worker_uid, now
        self.posts: dict[str, dict] = {}
        self.galleries: dict[str, Gallery] = OrderedDict()
        self.spatial = {}
        self.spatial_dirty = True
        self.retry_at = {}
        self.continuations = {}
        self.matches: dict[str, dict] = {}
        self.todo: queue.Queue = WorkQueue()
        self.lock = threading.Lock()
        self.watches = []
        self.listening_since = 0.0
        self.last_read_ms = 0           # server time of the newest snapshot seen
        self.full_since = 0.0           # when the last full (whole window) listen started
        self.prune_posts = False        # the next posts snapshot is a full one: drop posts no longer in the window
        self.matched = 0
        self.notified = 0
        # Posts saved by app versions before the map-area codes and search words (v0.0.9) get them from here
        # (geocells.py, searchterms.py).
        self.geo_todo: queue.Queue = WorkQueue()
        self.geo_queued: dict[str, dict] = {}
        self.geo_versions = {}
        self.todo_refill = None
        self.geo_refill = None
        self.removed: queue.Queue = WorkQueue()
        self.geo_fixed = 0
        self.geo_failed = False
        self.orphan_cursor = None
        self.orphan_next = 0
        self.metrics = {"orphanReads": 0, "orphanDeletes": 0}

    # ---------- listeners ----------

    def since_ms(self) -> int:
        return int((self.now() - WINDOW_DAYS * 86400) * 1000)

    def start(self):
        try:
            self.load_embeddings()
        except Exception as e:  # matching still works, it just recomputes the vectors
            self.events.put(("log", f"Matching: couldn't load stored embeddings: {e}"))
        self.listen()

    def posts_query(self, changed_since: int | None = None):
        from google.cloud import firestore
        from google.cloud.firestore_v1.base_query import FieldFilter
        posts = self.db.collection("posts").where(filter=FieldFilter("hidden", "==", False))
        if changed_since is not None:
            # Served by the (hidden ASC, updatedAt DESC) index in firestore.indexes.json.
            return (posts.where(filter=FieldFilter("updatedAt", ">=", changed_since))
                    .order_by("updatedAt", direction=firestore.Query.DESCENDING))
        # Newest first: this is served by the feed's index (hidden ASC, createdAt DESC), which production has. Without
        # the order, the query needs an ascending index production doesn't have, and the listener silently stops.
        return (posts.where(filter=FieldFilter("createdAt", ">=", self.since_ms()))
                .order_by("createdAt", direction=firestore.Query.DESCENDING))

    def matches_query(self, changed_since: int | None = None):
        from google.cloud.firestore_v1.base_query import FieldFilter
        if changed_since is not None:
            return self.db.collection("matches").where(filter=FieldFilter("updatedAt", ">=", changed_since))
        # A match is never older than its two posts, so the posts' window is enough (and matches only ever grow).
        return self.db.collection("matches").where(filter=FieldFilter("createdAt", ">=", self.since_ms()))

    def listen(self, incremental: bool = False):
        since = self.last_read_ms - REFRESH_OVERLAP_MS if incremental and self.last_read_ms else None
        posts, matches = self.posts_query(since), self.matches_query(since)
        # A listener that fails (missing index, rules) dies without a word, so ask once first and say what's wrong.
        try:
            list(posts.limit(1).stream())
            list(matches.limit(1).stream())
        except Exception as e:
            if since is not None:
                self.events.put(("log", f"Matching: quick refresh not possible ({e}); reading the whole window instead. "
                                        "Deploy firestore.indexes.json to fix this."))
                return self.listen(incremental=False)
            self.events.put(("log", f"Matching can't read posts or matches, so no lost-pet matches will be made: {e}"))
        if since is None:
            self.full_since = time.time()
            self.prune_posts = True
        self.watches.append(matches.on_snapshot(self.on_matches))
        self.watches.append(posts.on_snapshot(self.on_posts))
        self.listening_since = time.time()

    def saw(self, read_time):
        """Remembers the server time of the newest data seen, where the next quick refresh starts."""
        try:
            self.last_read_ms = max(self.last_read_ms, int(read_time.timestamp() * 1000))
        except Exception:
            pass

    def healthy(self) -> bool:
        """Both live listeners are still running (they stop on errors such as an expired token or a missing index)."""
        return bool(self.watches) and all(getattr(w, "is_active", True) for w in self.watches)

    def check(self):
        """Called every minute by the worker: re-opens dead listeners, so matching never stops for good."""
        self.refill_overflow()
        if not self.healthy() and time.time() - self.listening_since > 30:
            self.events.put(("log", "Matching: the live listener stopped; reconnecting."))
            self.restart()

    def stop(self):
        for w in self.watches:
            try:
                w.unsubscribe()
            except Exception:
                pass
        self.watches.clear()

    def restart(self):
        """Re-opens the streams on a fresh sign-in token (called with the job listener's refresh). Only what changed
        since the last data seen is read again, except for the full read every [FULL_RESYNC_SECONDS]."""
        full = not self.last_read_ms or time.time() - self.full_since > FULL_RESYNC_SECONDS
        self.stop()
        self.listen(incremental=not full)

    def load_embeddings(self):
        from datetime import datetime, timezone
        from google.cloud.firestore_v1.base_query import FieldFilter
        query = self.db.collection("postEmbeddings").where(filter=FieldFilter("model", "==", recognition.EMBEDDING_MODEL))
        query = query.where(filter=FieldFilter("createdAt", ">=", datetime.fromtimestamp(self.since_ms()/1000, timezone.utc)))
        # A bounded warm cache; other current signatures are loaded lazily by gallery().
        stream = iter(query.order_by("createdAt", direction="DESCENDING").limit(2048).stream())
        while chunk := list(islice(stream, 100)):
            posts = {s.id: s for s in self.db.get_all([self.db.document(f"posts/{s.id}") for s in chunk],
                     field_paths=["hidden", "createdAt", "status"])}
            for snap in chunk:
                post = posts.get(snap.id)
                if post is None: continue
                if not post.exists:
                    snap.reference.delete()
                    self.galleries.pop(snap.id, None)
                    continue
                d, p = snap.to_dict() or {}, post.to_dict() or {}
                if (not p.get("hidden") and p.get("status") == "open" and int(p.get("createdAt") or 0) >= self.since_ms()
                    and d.get("model") == recognition.EMBEDDING_MODEL and d.get("vectors")):
                    self.cache_gallery(snap.id, Gallery(d.get("signature", ""), np.stack([recognition.from_blob(v) for v in d["vectors"]])))

    def sweep_orphans(self):
        # A rotating bounded page includes legacy vectors with no current model/date fields.
        if time.monotonic() < self.orphan_next: return
        self.orphan_next = time.monotonic() + 15 * 60
        try:
            query = self.db.collection("postEmbeddings").order_by("__name__").select([]).limit(25)
            if self.orphan_cursor: query = query.start_after(self.orphan_cursor)
            page = list(query.stream())
            self.orphan_cursor = page[-1] if page else None
            self.metrics["orphanReads"] += max(1, len(page))
            parents = {s.id: s for s in self.db.get_all([self.db.document(f"posts/{s.id}") for s in page], field_paths=["hidden"])} if page else {}
            self.metrics["orphanReads"] += len(parents)
            for item in page:
                if item.id in parents and not parents[item.id].exists:
                    item.reference.delete(); self.metrics["orphanDeletes"] += 1
        except Exception as error:
            self.events.put(("log", f"Embedding cleanup will retry: {type(error).__name__}"))

    def cache_gallery(self, pid, value):
        with self.lock:
            self.galleries.pop(pid, None)
            self.galleries[pid] = value
            while len(self.galleries) > 2048:
                self.galleries.pop(next(iter(self.galleries)))

    def on_matches(self, docs, changes, read_time):
        with self.lock:
            for c in changes:
                if c.type.name == "REMOVED":
                    self.matches.pop(c.document.id, None)
                else:
                    self.matches[c.document.id] = c.document.to_dict() or {}
        self.saw(read_time)

    def on_posts(self, docs, changes, read_time):
        with self.lock: self.spatial_dirty = True
        since = self.since_ms()
        if self.prune_posts:
            # A full snapshot lists everything in the window: forget posts that left it (hidden, deleted, too old).
            self.prune_posts = False
            keep = {d.id for d in docs}
            with self.lock:
                for gone in [pid for pid in self.posts if pid not in keep]:
                    self.spatial_dirty = True
                    self.continuations.pop(gone, None)
                    self.posts.pop(gone, None)
                    self.geo_queued.pop(gone, None); self.geo_versions.pop(gone, None)
                    self.galleries.pop(gone, None)
                    self.removed.put(gone)
        for c in changes:
            # A quick refresh also returns older posts that were edited; the window still decides what is matched.
            if c.type.name != "REMOVED" and int((c.document.to_dict() or {}).get("createdAt") or 0) < since:
                continue
            d = c.document.to_dict() or {}
            d["id"] = c.document.id
            with self.lock:
                if c.type.name == "REMOVED":
                    self.spatial_dirty = True
                    self.continuations.pop(d["id"], None)
                    self.posts.pop(d["id"], None)
                    self.geo_queued.pop(d["id"], None); self.geo_versions.pop(d["id"], None)
                    self.galleries.pop(d["id"], None)
                    self.removed.put(d["id"])
                    continue
                before = self.posts.get(d["id"])
                self.posts[d["id"]] = d
                self.spatial_dirty = True
                self.continuations.pop(d["id"], None)
                fill = {}
                if (cells := geocells.needed(d)) is not None:
                    fill["geoCells"] = cells
                if (terms := searchterms.needed_post(d)) is not None:
                    fill["searchTerms"] = terms
                if fill:
                    self.geo_queued[d["id"]] = fill
                    self.geo_versions[d["id"]] = c.document.update_time
                    self.geo_todo.put((d["id"], fill))
                else:
                    self.geo_queued.pop(d["id"], None); self.geo_versions.pop(d["id"], None)
            relevant = d.get("category") in LOST | SIGHTINGS
            # Only what changes a match is worth the work: new posts, new photos, reopening, place or time.
            changed = before is None or any(before.get(k) != d.get(k) for k in
                                            ("photoIds", "status", "lat", "lng", "lastSeenAt", "animalType", "category"))
            if relevant and changed:
                self.todo.put(d["id"])
        self.saw(read_time)

    # ---------- work (on the worker thread) ----------

    def refill_overflow(self):
        # Listener state already contains the authoritative current window. Reopening it
        # on every overflow would repeatedly bill the entire window when it exceeds capacity.
        with self.lock:
            if self.todo.overflow:
                self.todo.overflow = False
                self.todo_refill = iter(tuple(self.posts))
            if self.geo_todo.overflow:
                self.geo_todo.overflow = False
                self.geo_refill = iter(tuple(self.geo_queued))
            # Lost removal IDs are covered by the rotating projected orphan sweep.
            self.removed.overflow = False

    def refill_sources(self):
        with self.lock:
            for iterator_name, target in (("todo_refill", self.todo), ("geo_refill", self.geo_todo)):
                iterator = getattr(self, iterator_name)
                if iterator is None: continue
                slots = min(128, max(0, target.capacity - target.qsize()))
                for _ in range(slots):
                    try: pid = next(iterator)
                    except StopIteration: setattr(self, iterator_name, None); break
                    if iterator_name == "todo_refill":
                        if pid in self.posts: target.put(pid)
                    elif pid in self.geo_queued: target.put((pid, self.geo_queued[pid]))

    def pending(self) -> bool:
        return self.todo_refill is not None or self.geo_refill is not None or not self.todo.empty() or not self.geo_todo.empty() or not self.removed.empty()

    def clean_removed(self, deadline=None):
        # REMOVED also means hidden or outside the query window: confirm deletion on the worker thread.
        for _ in range(16):
            if deadline is not None and time.monotonic() >= deadline: return
            try:
                pid = self.removed.get_nowait()
            except queue.Empty:
                return
            if time.monotonic() < self.retry_at.get(pid, 0):
                self.removed.put(pid)
                return
            try:
                post = self.db.document(f"posts/{pid}").get()
                if not post.exists:
                    self.db.document(f"postEmbeddings/{pid}").delete()
                with self.lock:
                    if pid not in self.posts:
                        self.galleries.pop(pid, None)
            except Exception as e:
                if len(self.retry_at) >= 4096: self.retry_at.clear()
                self.retry_at[pid] = time.monotonic() + 30
                self.removed.put(pid)
                self.events.put(("log", f"Embedding cleanup {pid[:8]} failed: {e}"))

    def fill_geo_cells(self, deadline=None):
        """Writes missing map-area codes and search words (only those, never updatedAt, so nothing is re-read or
        re-matched)."""
        for _ in range(16):
            if deadline is not None and time.monotonic() >= deadline: return
            try:
                pid, fill = self.geo_todo.get_nowait()
            except queue.Empty:
                return
            with self.lock:
                if self.geo_queued.get(pid) != fill: continue
                revision = self.geo_versions.get(pid)
            key = 'geo:' + pid
            if time.monotonic() < self.retry_at.get(key, 0):
                with self.lock:
                    if pid in self.geo_queued: self.geo_todo.put((pid, self.geo_queued[pid]))
                return
            try:
                if revision is None: raise ValueError("Missing backfill source revision")
                self.db.document(f"posts/{pid}").update(fill, option=self.db.write_option(last_update_time=revision))
                self.geo_fixed += 1
                with self.lock:
                    if self.geo_versions.get(pid) == revision:
                        self.geo_queued.pop(pid, None); self.geo_versions.pop(pid, None)
            except Exception as e:  # e.g. the rules that allow it aren't deployed yet: say so once
                if len(self.retry_at) >= 4096: self.retry_at.clear()
                self.retry_at[key] = time.monotonic() + 30
                with self.lock:
                    if pid in self.geo_queued: self.geo_todo.put((pid, self.geo_queued[pid]))
                if not self.geo_failed:
                    self.geo_failed = True
                    self.events.put(("log", f"Older posts: couldn't add map-area codes and search words: {e}"))
                return

    def work(self, budget_s: float = 2.0):
        """Handles queued posts for up to [budget_s] seconds, so photo-check jobs from phones aren't kept waiting."""
        end = time.monotonic() + budget_s
        self.refill_overflow()
        self.refill_sources()
        self.sweep_orphans()
        self.clean_removed(end)
        self.fill_geo_cells(end)
        seen = set()
        while time.monotonic() < end:
            try:
                pid = self.todo.get_nowait()
            except queue.Empty:
                return
            if pid in seen:
                self.todo.put(pid)
                return
            if time.monotonic() < self.retry_at.get('match:' + pid, 0):
                self.todo.put(pid)
                return
            seen.add(pid)
            try:
                self.handle(pid, end)
            except Exception as e:  # one bad post must not stop the others
                if len(self.retry_at) >= 4096: self.retry_at.clear()
                self.retry_at['match:' + pid] = time.monotonic() + 30
                self.todo.put(pid)
                self.events.put(("log", f"Matching {pid[:8]} failed: {type(e).__name__}"))
                return

    def gallery(self, p: dict) -> Gallery | None:
        """The post's photo embeddings, computed once per set of photos."""
        sig = signature(p)
        g = self.galleries.get(p["id"])
        if g is not None and g.signature == sig:
            return g
        if not p.get("photoIds"):
            return None
        stored = self.db.document(f"postEmbeddings/{p['id']}").get()
        data = stored.to_dict() or {}
        if data.get("model") == recognition.EMBEDDING_MODEL and data.get("signature") == sig and data.get("vectors"):
            g = Gallery(sig, np.stack([recognition.from_blob(v) for v in data["vectors"]]))
            self.cache_gallery(p["id"], g)
            return g
        if self.rec is None: return None
        photos = []
        for pid in p["photoIds"][:6]:
            snap = self.db.document(f"images/{pid}").get()
            data = (snap.to_dict() or {}).get("data") if snap.exists else None
            if data:
                photos.append(bytes(data))
        if not photos:
            return None
        vectors, crops = self.rec.embed_photos(photos, p.get("animalType", ""))
        from google.cloud import firestore
        current = self.db.document(f"posts/{p['id']}").get()
        if not current.exists or (current.to_dict() or {}).get("hidden"):
            self.galleries.pop(p["id"], None)
            return None
        self.db.document(f"postEmbeddings/{p['id']}").set({
            "postId": p["id"], "ownerId": p.get("ownerId", ""), "animalType": p.get("animalType", ""),
            "model": recognition.EMBEDDING_MODEL, "signature": sig, "photoIds": p["photoIds"][:6],
            "vectors": [recognition.to_blob(v) for v in vectors],
            "detected": [c.detected for c in crops], "animals": [c.animals for c in crops],
            "lowQuality": [c.low_quality for c in crops], "createdAt": firestore.SERVER_TIMESTAMP,
        })
        g = Gallery(sig, vectors)
        self.cache_gallery(p["id"], g)
        self.events.put(("log", f"Indexed {p.get('category')} {p.get('name') or p.get('animalType')} ({len(photos)} photo(s), "
                                f"{sum(c.detected for c in crops)} animal(s) found)"))
        return g

    def candidates(self, p: dict) -> list[dict]:
        other = SIGHTINGS if p.get("category") in LOST else LOST
        with self.lock:
            if self.spatial_dirty:
                self.spatial = {}
                for value in self.posts.values():
                    key = (value.get("animalType"), math.floor(float(value.get("lat", 0)) * 2), math.floor(float(value.get("lng", 0)) * 2))
                    self.spatial.setdefault(key, []).append(value)
                self.spatial_dirty = False
            lat, lng = float(p.get("lat", 0)), float(p.get("lng", 0))
            # Bounding box is conservative at the latitude, then exact distance applies below.
            dy = PREFILTER_KM / 110.0
            dx = PREFILTER_KM / max(1.0, 110.0 * math.cos(math.radians(lat)))
            pool = [c for y in range(math.floor((lat-dy)*2), math.floor((lat+dy)*2)+1)
                      for x in range(math.floor((lng-dx)*2), math.floor((lng+dx)*2)+1)
                      for c in self.spatial.get((p.get("animalType"), y, x), [])]
        out = []
        for c in pool:
            if (c["id"] != p["id"] and c.get("category") in other and c.get("status") == "open" and not c.get("hidden")
                    and c.get("animalType") == p.get("animalType") and c.get("ownerId") != p.get("ownerId")
                    and matching.distance_km(p.get("lat", 0), p.get("lng", 0), c.get("lat", 0), c.get("lng", 0)) <= PREFILTER_KM):
                out.append(c)
        return out

    def handle(self, pid: str, deadline=None):
        with self.lock:
            p = self.posts.get(pid)
        if p is None:
            return
        g = self.gallery(p)
        if p.get("status") != "open":
            return  # a reunited or closed report is not matched any more
        state = self.continuations.get(pid)
        if state is None or state[0] != signature(p):
            state = (signature(p), iter(self.candidates(p)))
            self.continuations[pid] = state
        for c in state[1]:
            lost, sighting = (p, c) if p.get("category") in LOST else (c, p)
            gl, gs = (g, self.gallery(c)) if lost is p else (self.gallery(c), g)
            if gl is not None and gs is not None:
                r = matching.score(side(lost), side(sighting), "embedding", 0.5 * float((gl.vectors @ gs.vectors.T).max()) + 0.5 * float(gl.mean @ gs.mean), lost.get("animalType", "cat"))
            else:
                r = matching.score(side(lost), side(sighting), "details")
            mid = f"{lost['id']}__{sighting['id']}"
            r = matching.apply_feedback(r, self.like_rejected(lost, sighting), self.reporter_rejected(mid))
            self.upsert(lost, sighting, mid, r)
            if deadline is not None and time.monotonic() >= deadline:
                self.todo.put(pid)
                return
        self.continuations.pop(pid, None)

    def like_rejected(self, lost: dict, sighting: dict) -> bool:
        """The sighting looks like another sighting the owner already said was not their animal."""
        gs = self.galleries.get(sighting["id"])
        gl = self.galleries.get(lost["id"])
        if gs is None or gl is None:
            return False
        own = visual_similarity(gl.vectors, gs.vectors)
        with self.lock:
            rejected = [m["sightingPostId"] for m in self.matches.values()
                        if m.get("lostPostId") == lost["id"] and m.get("ownerState") == "not_mine" and m.get("sightingPostId") != sighting["id"]]
        for rid in rejected:
            gr = self.galleries.get(rid)
            if gr is not None and visual_similarity(gr.vectors, gs.vectors) >= max(matching.embed_thresholds(lost.get("animalType", ""))[0], own):
                return True
        return False

    def reporter_rejected(self, mid: str) -> bool:
        with self.lock:
            return (self.matches.get(mid) or {}).get("reporterState") == "not_same"

    def upsert(self, lost: dict, sighting: dict, mid: str, r: matching.Result):
        with self.lock:
            existing = self.matches.get(mid)
        fields = {"score": r.score, "visual": r.visual, "tier": r.tier, "method": r.method,
                  "model": recognition.EMBEDDING_MODEL if r.method == "embedding" else f"details/v{matching.SPEC_VERSION}",
                  "distanceKm": r.distance_km, "daysApart": r.days_apart, "reasons": r.reasons, "source": "worker",
                  "updatedAt": int(time.time() * 1000)}
        ref = self.db.document(f"matches/{mid}")
        if existing is None:
            if r.tier == "none":
                return  # nothing worth showing, and no document needed to remember it
            doc = {"lostPostId": lost["id"], "sightingPostId": sighting["id"], "lostOwnerId": lost.get("ownerId", ""),
                   "sightingOwnerId": sighting.get("ownerId", ""),
                   "members": [lost.get("ownerId", ""), sighting.get("ownerId", "")],
                   "animalType": lost.get("animalType", ""), "ownerState": "new", "reporterState": "new",
                   "createdBy": "worker", "createdAt": int(time.time() * 1000), "notified": False, **fields}
            ref.set(doc)
            with self.lock:
                self.matches[mid] = doc
            existing = doc
            self.matched += 1
        else:
            if existing.get("ownerState") == "not_mine":
                return  # the owner said no: never upgraded or announced again
            same = (existing.get("tier") == r.tier and existing.get("method") == r.method
                    and abs(float(existing.get("score", 0)) - r.score) < 0.01)
            if not same:
                ref.update(fields)
                with self.lock:
                    existing = {**existing, **fields}
                    self.matches[mid] = existing
        self.events.put(("match", {"lost": lost, "sighting": sighting, "result": r}))
        if r.tier in NOTIFY_TIERS and not existing.get("notified") and existing.get("ownerState") == "new" and self.top_for(lost["id"], mid):
            self.notify(lost, sighting, mid, r)
            ref.update({"notified": True})
            with self.lock:
                self.matches[mid] = {**self.matches.get(mid, existing), "notified": True}

    def top_for(self, lost_id: str, mid: str) -> bool:
        with self.lock:
            ranked = sorted((m for k, m in self.matches.items() if m.get("lostPostId") == lost_id and m.get("tier") in NOTIFY_TIERS),
                            key=lambda m: -float(m.get("score", 0)))
            ids = [f"{m['lostPostId']}__{m['sightingPostId']}" for m in ranked[:NOTIFY_TOP]]
        return mid in ids

    def notify(self, lost: dict, sighting: dict, mid: str, r: matching.Result):
        """One inbox alert for the owner and one for the person who posted the sighting. The text is written by each
        phone in its own language from these parameters; nothing private (exact place, contact) is included."""
        from google.api_core.exceptions import AlreadyExists, Conflict
        params = {"name": lost.get("name", ""), "type": lost.get("animalType", ""), "area": sighting.get("neighborhood", ""),
                  "lostArea": lost.get("neighborhood", ""), "tier": r.tier, "matchId": mid}
        now = int(time.time() * 1000)
        for uid, nid in ((lost.get("ownerId"), f"pm_{mid}"), (sighting.get("ownerId"), f"pr_{mid}")):
            if not uid:
                continue
            kind = "match_found" if nid.startswith("pm_") else "match_reporter"
            try:
                batch = self.db.batch()
                batch.create(self.db.document(f"users/{uid}/notifications/{nid}"), {
                    "type": kind, "params": params, "route": f"match/{mid}", "fromUid": self.uid, "sourceId": mid,
                    "read": False, "createdAt": now})
                batch.create(self.db.document(f"users/{uid}/notificationReceipts/{nid}"), {"fromUid": self.uid, "sourceId": mid, "createdAt": now})
                batch.commit()
            except (AlreadyExists, Conflict):
                pass
        self.notified += 1
