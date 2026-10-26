"""CatMap AI Server: a desktop app on the owner's PC that checks new photos for the CatMap app: whether a photo shows
an animal at all (CLIP), whether a street animal is already on the map (pet-recognition-base), and, in the
background, whether a new sighting may be a lost pet (recovery.py: lost report <-> sighting matches).

How it works (no open ports, no cable, works on any network):
  1. When someone posts a street animal, the phone writes a job to Firestore `aiJobs/{uid}`: a crop of the new animal
     and crops of nearby street-animal posts (small JPEGs, kept in Firestore like every other CatMap image).
  2. Firestore pushes the new job to this app over a live listener (one outgoing streaming connection; nothing is
     polled). The app embeds the crops with pet-recognition-base (open-noodle, Apache-2.0: DINOv2-base + 512-d
     projection, ONNX, GPU through DirectML when available) and writes back the cosine similarity of each candidate
     with a verdict. The phone is listening to that document too and shows the answer.
  3. `config/aiWorker` says whether the PC is online: set when the app opens and closes, refreshed every 5 minutes in
     case the PC crashes, so phones skip the check at once when the PC is off.

It signs in as the AI worker account (custom claim catmapAiWorker, created by the CatMap project's
tools/demo/create-demo-accounts.mjs, password in worker.local.json in this folder). The security rules let that account read jobs and write only results; it can't touch
posts or users unless the owner separately grants the optional catmapMaintenance role and enables CATMAP_MAINTENANCE=1. That role can read private chat media to perform retention cleanup. The window pops to the front whenever an image arrives, showing the photo, the candidates and the scores.

    start.cmd                  (double-click; first run installs everything and downloads the models, ~950 MB)
    python server.py --emulator  local Firebase emulators, for testing
    start.cmd --review         also answer photo review jobs (review.py; deploy the photo review rules first)
"""
from __future__ import annotations

import argparse
from concurrent.futures import ThreadPoolExecutor
import logging
import io
import json
import os
import queue
import sys
import threading
import time
import tkinter as tk
from datetime import datetime, timedelta, timezone
from pathlib import Path
from tkinter import ttk

import numpy as np
import onnxruntime as ort
import requests
from PIL import Image, ImageTk

import matching
from model_manifest import download as pinned_download, identity
from recognition import APP_FORMATS, DETECTOR_NAME, MODEL_NAME, Detector, Model, Recognizer
from recovery import Recovery
from maintenance import Maintenance
from work_queue import LatestJobQueue

HERE = Path(__file__).resolve().parent
# The Firebase project settings: a copy in this folder if there is one, otherwise the CatMap project next to it.
GOOGLE_SERVICES = [HERE / "google-services.json", HERE.parent / "cat" / "app" / "google-services.json"]
# "Is this an animal?" check: OpenAI CLIP ViT-B/32 (MIT licence), ONNX export by Xenova. It compares the photo with
# short descriptions (zero-shot), so it can tell animals from people (selfies) and everyday objects, and name them.
CLIP_REPO = "Xenova/clip-vit-base-patch32"
CLIP_NAME = "clip-vit-base-patch32"
CLIP_MEAN = np.array([0.48145466, 0.4578275, 0.40821073], dtype=np.float32)
CLIP_STD = np.array([0.26862954, 0.26130258, 0.27577711], dtype=np.float32)
# A photo counts as an animal when the animal descriptions together get at least this share, or the biggest share.
ANIMAL_MIN = float(os.environ.get("ANIMAL_THRESHOLD", "0.40"))
# The object's name is only given when the best object description is at least this sure.
OBJECT_NAME_MIN = 0.15
# Grouped by the app's animal types (AnimalType keys); "other" covers every other animal.
ANIMAL_PROMPTS = {
    "cat": ["a cat", "a kitten", "a street cat", "a person holding a cat"],
    "dog": ["a dog", "a puppy", "a person holding a dog"],
    "bird": ["a bird", "a pigeon", "a parrot", "a falcon", "a crow", "a sparrow"],
    "rabbit": ["a rabbit"],
    "other": ["a horse", "a camel", "a goat", "a sheep", "a cow", "a donkey", "a turtle", "a tortoise", "a fish", "a lizard",
              "a snake", "a hamster", "a guinea pig", "a mouse", "a rat", "a fox", "a hedgehog", "a monkey", "a duck",
              "a chicken", "an owl", "a squirrel", "a frog", "an insect", "an animal"],
}
PERSON_PROMPTS = ["a selfie", "a person", "a man", "a woman", "a child", "a group of people", "a face"]
# Everyday things people might photograph by mistake. The phrase (with its article) is shown in the app's message.
OBJECT_PROMPTS = [
    "a phone", "a cup", "a coffee cup", "a bottle", "a glass", "a car", "a motorcycle", "a bicycle", "a bus", "a shoe",
    "a chair", "a table", "a sofa", "a bed", "a laptop", "a computer screen", "a keyboard", "a book", "a plate of food",
    "food", "a drink", "a building", "a house", "a street", "a road", "a tree", "a flower", "a plant", "a toy",
    "a stuffed toy", "a shirt", "clothes", "a bag", "a backpack", "a watch", "a clock", "a lamp", "a television", "a camera",
    "a rocket", "an airplane", "a boat", "a door", "a window", "a wall", "a floor", "the sky", "a beach", "the sea",
    "a desert", "a mountain", "a park", "a cake", "fruit", "a pen", "a paper document", "a screenshot", "money", "a key",
    "glasses", "a rubbish bin", "a box", "furniture", "a kitchen", "a room", "a car park", "a shop",
]

# Verdict thresholds on cosine similarity for the "already on the map?" check: the measured "strong" and "possible"
# levels of matching.py (cat/docs/AI_EVALUATION.md). SAME_THRESHOLD / MAYBE_THRESHOLD override them for experiments.
SAME = float(os.environ.get("SAME_THRESHOLD", str(matching.EMBED_STRONG)))
MAYBE = float(os.environ.get("MAYBE_THRESHOLD", str(matching.EMBED_POSSIBLE)))
HEARTBEAT_SECONDS = 300.0  # crash safeguard only; the app also reports online/offline when it opens and closes
RELISTEN_SECONDS = 50 * 60
MAX_IMAGES = 16
Image.MAX_IMAGE_PIXELS = 4096 * 4096


# ---------- models (pet-recognition-base and the DETR detector are in recognition.py) ----------

class Classifier:
    """Says what a photo shows: an animal (and which kind), a person, or an object, using CLIP zero-shot.

    The text side runs once: the descriptions' embeddings are cached in models/ and reused on later starts.
    """

    def __init__(self, cpu: bool):
        import hashlib

        get = lambda f: pinned_download(CLIP_REPO, f)
        wanted = ["CPUExecutionProvider"] if cpu else ["DmlExecutionProvider", "CPUExecutionProvider"]
        self.vision = ort.InferenceSession(get("onnx/vision_model.onnx"), providers=[p for p in wanted if p in ort.get_available_providers()])
        self.provider = self.vision.get_providers()[0]
        # (group, animal type or "", phrase)
        self.prompts = [("animal", kind, phrase) for kind, phrases in ANIMAL_PROMPTS.items() for phrase in phrases]
        self.prompts += [("person", "", phrase) for phrase in PERSON_PROMPTS]
        self.prompts += [("object", "", phrase) for phrase in OBJECT_PROMPTS]
        texts = [f"a photo of {phrase}" for _, _, phrase in self.prompts]
        key = hashlib.sha1((identity(CLIP_REPO) + repr(CLIP_MEAN.tolist()) + repr(CLIP_STD.tolist()) + "\n" + "\n".join(texts)).encode()).hexdigest()[:12]
        cache = HERE / "models" / f"clip-prompts-{key}.npy"
        if cache.exists():
            self.text = np.load(cache)
        else:
            from tokenizers import Tokenizer
            tokenizer = Tokenizer.from_file(get("tokenizer.json"))
            session = ort.InferenceSession(get("onnx/text_model.onnx"), providers=["CPUExecutionProvider"])
            rows = [session.run(None, {"input_ids": np.array([tokenizer.encode(t).ids], dtype=np.int64)})[0][0] for t in texts]
            self.text = np.stack(rows)
            self.text /= np.linalg.norm(self.text, axis=1, keepdims=True)
            np.save(cache, self.text)
        self.groups = np.array([g for g, _, _ in self.prompts])

    @staticmethod
    def preprocess(data: bytes) -> np.ndarray:
        # JPEG decoder only: see APP_FORMATS.
        with Image.open(io.BytesIO(data), formats=APP_FORMATS) as im:
            im = im.convert("RGB")
            w, h = im.size
            scale = 224 / min(w, h)
            im = im.resize((max(224, round(w * scale)), max(224, round(h * scale))), Image.BICUBIC)
            w, h = im.size
            left, top = (w - 224) // 2, (h - 224) // 2
            im = im.crop((left, top, left + 224, top + 224))
        return ((np.asarray(im, dtype=np.float32) / 255.0 - CLIP_MEAN) / CLIP_STD).transpose(2, 0, 1)

    def classify(self, images: list[bytes]) -> list[dict]:
        """One verdict per photo: kind (animal / person / object), a label, and for animals the app's animal type."""
        if not images:
            return []
        batch = np.stack([self.preprocess(b) for b in images]).astype(np.float32)
        emb = self.vision.run(None, {"pixel_values": batch})[0]
        emb /= np.linalg.norm(emb, axis=1, keepdims=True).clip(1e-9)
        logits = 100.0 * emb @ self.text.T
        probs = np.exp(logits - logits.max(axis=1, keepdims=True))
        probs /= probs.sum(axis=1, keepdims=True)
        out = []
        for p in probs:
            share = {g: float(p[self.groups == g].sum()) for g in ("animal", "person", "object")}
            best_group = max(share, key=share.get)
            if share["animal"] >= ANIMAL_MIN or best_group == "animal":
                # The type comes from the single best description (summing would favour "other", which has the most),
                # ignoring the catch-all "an animal" unless nothing more specific fits.
                specific = [i for i, (g, _, ph) in enumerate(self.prompts) if g == "animal" and ph != "an animal"]
                i = max(specific, key=lambda i: p[i])
                kind = self.prompts[i][1]
                phrase = self.prompts[i][2].removeprefix("a person holding ")
                out.append({"kind": "animal", "animalType": kind, "phrase": phrase, "score": round(share["animal"], 3)})
            elif best_group == "person":
                out.append({"kind": "person", "animalType": "", "phrase": "a selfie or a photo of a person", "score": round(share["person"], 3)})
            else:
                i = max((i for i, (g, _, _) in enumerate(self.prompts) if g == "object"), key=lambda i: p[i])
                phrase = self.prompts[i][2] if p[i] >= OBJECT_NAME_MIN else ""
                out.append({"kind": "object", "animalType": "", "phrase": phrase, "score": round(share["object"], 3)})
        return out


# ---------- Firestore (live listener) ----------

class Firestore:
    """A Firestore client signed in as the AI worker (rules apply), or the emulator owner with --emulator.

    Uses Google's Firestore client library, whose `on_snapshot` keeps one streaming connection open: Firestore pushes
    a message the moment a job arrives. Nothing is polled, and an idle listener costs no reads.
    """

    def __init__(self, emulator: bool):
        from google.auth.credentials import AnonymousCredentials
        from google.cloud import firestore
        from google.oauth2.credentials import Credentials

        self.emulator = emulator
        if emulator:
            from emulator_safety import require_emulator
            os.environ.setdefault("FIRESTORE_EMULATOR_HOST", "127.0.0.1:8080")
            require_emulator("demo-catmap", os.environ["FIRESTORE_EMULATOR_HOST"])
            self.who = "emulator owner"
            self.uid = "catmap-ai-worker"
            self.client = firestore.Client(project="demo-catmap", credentials=AnonymousCredentials())
            return
        try:
            found = next(f for f in GOOGLE_SERVICES if f.exists())
            gs = json.loads(found.read_text(encoding="utf-8"))
            self.api_key = gs["client"][0]["api_key"][0]["current_key"]
            project = gs["project_info"]["project_id"]
        except Exception:
            raise SystemExit("google-services.json wasn't found (it holds the Firebase project settings).\n\n"
                             f"Copy it from the CatMap project's app folder into {HERE}.")
        cred_file = HERE / "worker.local.json"
        if not cred_file.exists():
            raise SystemExit(f"worker.local.json is missing from {HERE}.\n\n"
                             "Create it by running, in the CatMap project folder:\nnode tools/demo/create-demo-accounts.mjs")
        creds = json.loads(cred_file.read_text(encoding="utf-8"))
        r = requests.post(f"https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key={self.api_key}",
                          json={"email": creds["email"], "password": creds["password"], "returnSecureToken": True}, timeout=20)
        if not r.ok:
            raise SystemExit(f"AI worker sign-in failed: {r.json().get('error', {}).get('message', r.status_code)}")
        self.who = creds["email"]
        self.uid = r.json()["localId"]
        self.refresh_token = r.json()["refreshToken"]
        # Firestore accepts the worker's Firebase ID token as a bearer token, so the security rules apply to it.
        # google-auth calls refresh_handler whenever the token is about to expire (hourly).
        self.client = firestore.Client(project=project, credentials=Credentials(token=None, refresh_handler=self.refresh))

    def refresh(self, request, scopes):
        r = requests.post(f"https://securetoken.googleapis.com/v1/token?key={self.api_key}",
                          data={"grant_type": "refresh_token", "refresh_token": self.refresh_token}, timeout=20)
        r.raise_for_status()
        j = r.json()
        self.refresh_token = j["refresh_token"]
        expiry = datetime.now(timezone.utc).replace(tzinfo=None) + timedelta(seconds=int(j.get("expires_in", 3600)) - 120)
        return j["id_token"], expiry


# ---------- worker ----------

class Worker:
    """Listens for pending jobs and answers them one at a time on its own thread."""

    def __init__(self, model: Model, classifier: Classifier, fs: Firestore, events: queue.Queue, recognizer: Recognizer | None = None,
                 reviewer=None):
        self.model, self.classifier, self.fs, self.events = model, classifier, fs, events
        # Photo review (review.py), only with --review. Phones send review jobs only when the heartbeat lists it.
        self.reviewer = reviewer
        self.recovery = Recovery(fs.client, recognizer, events, fs.uid) if recognizer is not None else None
        storage_delete = None
        if fs.emulator and os.environ.get("FIREBASE_STORAGE_EMULATOR_HOST"):
            from emulator_safety import storage_deleter
            storage_delete = storage_deleter("demo-catmap", os.environ["FIREBASE_STORAGE_EMULATOR_HOST"], "demo-catmap.appspot.com")
        self.maintenance = Maintenance(fs.client, fs.uid, events, storage_delete) if os.environ.get("CATMAP_MAINTENANCE") == "1" else None
        self.retry_attempts = {}
        self.jobs = LatestJobQueue(256)
        self.retry_jobs = {}
        self.refill_needed = False
        self.running = True
        self.background = ThreadPoolExecutor(max_workers=1, thread_name_prefix="recovery")
        self.background_job = None
        self.recovery_starting = False
        self.handled = 0
        self.names: dict[str, str] = {}
        self.answered: set[tuple[str, str, object]] = set()
        self.watch = None
        self.listening_since = 0.0

    # Heartbeat: written when the app starts and stops, and every few minutes in case the PC crashes.
    def heartbeat(self, online: bool):
        from google.cloud import firestore
        status = {"online": online, "lastSeen": firestore.SERVER_TIMESTAMP, "model": MODEL_NAME,
                  "provider": self.model.provider, "same": SAME, "maybe": MAYBE}
        # Only with --review: rules from before photo review refuse the extra key, so a plain start stays compatible.
        if self.reviewer is not None:
            from review import PROTOCOL
            status["capabilities"] = [PROTOCOL]
        self.fs.client.document("config/aiWorker").set(status)

    def listen(self):
        from google.cloud.firestore_v1.base_query import FieldFilter
        if self.watch is not None:
            self.watch.unsubscribe()
        query = self.fs.client.collection("aiJobs").where(filter=FieldFilter("status", "==", "pending"))
        self.watch = query.on_snapshot(self.on_snapshot)
        self.listening_since = time.time()

    def on_snapshot(self, docs, changes, read_time):
        # Called by Firestore's push stream; just queue the work so the stream isn't blocked.
        for change in changes:
            if change.type.name in ("ADDED", "MODIFIED"):
                doc = change.document
                try: self.jobs.put_nowait(doc)
                except queue.Full: self.refill_needed = True

    def start(self):
        self.heartbeat(True)
        self.listen()
        if self.recovery is not None:
            self.recovery_starting = True
            def start_recovery():
                try: self.recovery.start()
                finally: self.recovery_starting = False
            self.background_job = self.background.submit(start_recovery)
        threading.Thread(target=self.loop, daemon=True).start()

    def sweep_jobs(self):
        """Deletes answered jobs older than 10 minutes. Each person has one job document holding the photos they sent
        (up to about 1 MB), which would otherwise stay in Firestore's free 1 GB for good. Phones from version 0.1 delete
        their own job once answered; this catches older versions. Only the doneAt field is downloaded."""
        from datetime import datetime, timedelta, timezone
        from google.cloud.firestore_v1.base_query import FieldFilter
        from google.api_core.exceptions import FailedPrecondition, NotFound
        cutoff = datetime.now(timezone.utc) - timedelta(minutes=10)
        removed = 0
        query = self.fs.client.collection("aiJobs").where(filter=FieldFilter("status", "in", ["done", "error"])).where(filter=FieldFilter("doneAt", "<", cutoff)).order_by("doneAt").select(["doneAt"])
        for snap in query.limit(100).stream():
            done_at = (snap.to_dict() or {}).get("doneAt")
            if done_at is None or done_at < cutoff:
                try:
                    snap.reference.delete(option=self.fs.client.write_option(last_update_time=snap.update_time))
                    removed += 1
                except (FailedPrecondition, NotFound):
                    pass # A newer request replaced the answered snapshot; never delete its photos.
        if removed:
            self.events.put(("log", f"Removed {removed} old answered job(s) and their photos."))

    def loop(self):
        last_beat = last_check = time.time()
        self.last_sweep = 0.0
        while self.running:
            try:
                # Phones waiting for a photo check come first; background matching runs in the gaps.
                busy = self.recovery is not None and self.recovery.pending()
                doc = self.jobs.get(timeout=0.05 if busy else 5)
                self.handle(doc)
            except queue.Empty:
                pass
            except Exception as e:
                self.events.put(("log", f"Problem: {e}"))
            try:
                if self.background_job is None or self.background_job.done():
                    previous = self.background_job
                    self.background_job = self.background.submit(self.background_work)
                    if previous is not None: previous.result()
                for key, (due, retry_doc, attempts) in list(self.retry_jobs.items()):
                    if time.monotonic() >= due:
                        try:
                            self.jobs.put_nowait(retry_doc)
                            del self.retry_jobs[key]
                        except queue.Full:
                            break
                if self.refill_needed and self.jobs.empty():
                    self.refill_needed = False
                    self.listen()
                if time.time() - last_beat > HEARTBEAT_SECONDS:
                    self.heartbeat(True)
                    last_beat = time.time()
                # Listeners that stopped (network, token, index errors) are re-opened instead of staying dead.
                if time.time() - last_check > 60:
                    last_check = time.time()
                    if self.watch is not None and not getattr(self.watch, "is_active", True):
                        self.events.put(("log", "The photo-check listener stopped; reconnecting."))
                        self.listen()
                    self.recovery_check_due = True
                # Re-open the stream now and then so it always runs on a fresh sign-in token.
                if time.time() - self.listening_since > RELISTEN_SECONDS:
                    self.listen()
                    self.recovery_refresh_due = True
            except Exception as e:
                self.events.put(("log", f"Problem: {e}"))

    def background_work(self):
        if not self.running: return
        if self.recovery is not None:
            if getattr(self, 'recovery_refresh_due', False):
                self.recovery_refresh_due = False
                self.recovery.restart()
            if getattr(self, 'recovery_check_due', False):
                self.recovery_check_due = False
                self.recovery.check()
        if time.time() - self.last_sweep > 3600:
            self.last_sweep = time.time()
            self.sweep_jobs()
        if self.maintenance is not None:
            self.maintenance.work()
        if self.recovery is not None:
            self.recovery.work()

    def stop(self):
        self.running = False
        self.background.shutdown(wait=False, cancel_futures=True)
        try:
            if self.watch is not None:
                self.watch.unsubscribe()
            if self.recovery is not None:
                self.recovery.stop()
            self.heartbeat(False)
        except Exception:
            pass

    def username(self, uid: str) -> str:
        if uid not in self.names:
            if len(self.names) >= 2048: self.names.pop(next(iter(self.names)))
            snap = self.fs.client.document(f"users/{uid}").get()
            self.names[uid] = (snap.to_dict() or {}).get("username") or uid[:8]
        return self.names[uid]

    def read_cats(self, ids: list[str]) -> dict:
        refs = [self.fs.client.document(f"cats/{i}") for i in ids]
        return {s.id: (s.to_dict() if s.exists else None) for s in self.fs.client.get_all(refs)}

    def read_images(self, ids: list[str]) -> dict:
        # One batched read; a photo that is gone or can't be read is simply left out of that cat's photos. The rules
        # refuse a whole batch for one photo the worker may not read (an older photo without an audience), so a
        # refused batch is read again one photo at a time.
        from google.api_core.exceptions import PermissionDenied
        def data(s):
            value = (s.to_dict() or {}).get("data") if s.exists else None
            return bytes(value) if isinstance(value, (bytes, bytearray)) else None
        out = {}
        for start in range(0, len(ids), 100):
            refs = [self.fs.client.document(f"images/{i}") for i in ids[start:start + 100]]
            try:
                out.update({s.id: data(s) for s in self.fs.client.get_all(refs)})
            except PermissionDenied:
                for ref in refs:
                    try: out[ref.id] = data(ref.get())
                    except PermissionDenied: out[ref.id] = None
        return out

    def compare(self, query: bytes, images: list[bytes], ids: list[str]) -> list[dict]:
        vectors = self.model.embed([query] + images)
        sims = (vectors[1:] @ vectors[0]).tolist() if images else []
        return [{"id": ids[i] if i < len(ids) else str(i), "similarity": round(float(s), 4),
                 "verdict": "same" if s >= SAME else "maybe" if s >= MAYBE else "different"} for i, s in enumerate(sims)]

    def handle(self, doc):
        from google.cloud import firestore
        f = doc.to_dict() or {}
        request_id = str(f.get("requestId", ""))
        key = (doc.id, request_id, getattr(doc, 'update_time', None))
        if f.get("status") != "pending" or key in self.answered:
            return
        kind = f.get("kind", "compare")
        images = [bytes(b) for b in f.get("images", []) if isinstance(b, (bytes, bytearray))][:MAX_IMAGES]
        query = bytes(f["query"]) if isinstance(f.get("query"), (bytes, bytearray)) else None
        ids = [str(i) for i in f.get("ids", [])]
        labels = [str(i) for i in f.get("labels", [])]
        photos = [bytes(b) for b in f.get("photos", []) if isinstance(b, (bytes, bytearray))][:6]
        who = self.username(str(f.get("uid", "")))
        started = time.time()
        try:
            if kind == "embed":
                vectors = self.model.embed(images) if images else np.zeros((0, 512))
                out = {"status": "done", "embeddings": [{"v": [round(float(x), 5) for x in v]} for v in vectors]}
                self.events.put(("embed", {"who": who, "count": len(images)}))
            elif kind == "review":
                # The phone sends only its photo and cat ids; the cats' photos are read here, by id.
                if self.reviewer is None:
                    raise ValueError("photo review isn't switched on here")
                if images or photos:
                    raise ValueError("a review job carries no other photos")
                review, previews = self.reviewer.review(query, ids, self.read_cats, self.read_images)
                out = {"status": "done", "review": review}
                self.events.put(("review", {"who": who, "query": query, "review": review, "previews": previews,
                                             "labels": dict(zip(ids, labels)), "ms": int((time.time() - started) * 1000)}))
            elif kind == "check":
                # A new post's photos: what each one shows, and (street animals) whether the first one matches a
                # nearby street animal already on the map. One job, so the phone waits for one answer.
                classification = self.classifier.classify(photos)
                results = self.compare(query, images, ids) if query is not None and images else []
                out = {"status": "done", "classification": classification, "results": results}
                self.events.put(("check", {"who": who, "photos": photos, "classification": classification, "query": query,
                                            "images": images, "results": results, "labels": labels,
                                            "ms": int((time.time() - started) * 1000)}))
            else:
                if query is None:
                    raise ValueError("no query image")
                results = self.compare(query, images, ids)
                out = {"status": "done", "results": results}
                self.events.put(("compare", {"who": who, "query": query, "images": images, "results": results,
                                              "labels": labels, "ms": int((time.time() - started) * 1000)}))
        except Exception as e:
            out = {"status": "error", "error": str(e)[:200]}
            self.events.put(("log", f"Job from {who} failed: {e}"))
        model_name = {"check": f"{CLIP_NAME} + {MODEL_NAME}", "review": "review-candidate-v1"}.get(kind, MODEL_NAME)
        provider = self.reviewer.provider if kind == "review" and self.reviewer is not None else self.model.provider
        out.update({"model": model_name, "provider": provider, "ms": int((time.time() - started) * 1000),
                    "doneAt": firestore.SERVER_TIMESTAMP, "answeredRequestId": request_id})
        # Inference runs outside the transaction. Commit only if this is still the current
        # pending request; retries re-read the live document rather than overwrite a replacement.
        @firestore.transactional
        def save(transaction):
            current = doc.reference.get(transaction=transaction)
            live = current.to_dict() or {}
            if (live.get("requestId") != request_id or live.get("status") != "pending"
                or getattr(current, 'update_time', None) != getattr(doc, 'update_time', None)):
                return False
            transaction.update(doc.reference, out)
            return True
        try:
            committed = save(self.fs.client.transaction())
            if committed:
                self.handled += 1
            # A stale request is also finished, but never mark a failed commit as answered.
            if len(self.answered) >= 2048:
                self.answered.clear()
            self.answered.add(key)
            self.retry_jobs.pop(key, None)
            self.retry_attempts.pop(key, None)
        except Exception as e:
            attempts = self.retry_attempts.get(key, 0) + 1
            if len(self.retry_attempts) < 256 or key in self.retry_attempts:
                self.retry_attempts[key] = attempts
            if len(self.retry_jobs) < 256 or key in self.retry_jobs:
                self.retry_jobs[key] = (time.monotonic() + min(60, 2 ** min(attempts, 6)), doc, attempts)
            else:
                self.refill_needed = True
            self.events.put(("log", f"Couldn't save the answer for @{who}; queued retry: {type(e).__name__}"))



# ---------- window ----------

class App:
    def __init__(self, root: tk.Tk, worker: Worker, events: queue.Queue):
        self.root, self.worker, self.events = root, worker, events
        self.photos: list[ImageTk.PhotoImage] = []
        root.title("CatMap AI Server")
        root.geometry("860x620")
        root.configure(bg="#FFF8F3")
        style = ttk.Style()
        style.configure("Head.TLabel", font=("Segoe UI", 16, "bold"), background="#FFF8F3", foreground="#A9531A")
        style.configure("Body.TLabel", font=("Segoe UI", 10), background="#FFF8F3")
        style.configure("Card.TFrame", background="#FFFFFF")
        ttk.Label(root, text="CatMap AI Server", style="Head.TLabel").pack(anchor="w", padx=16, pady=(14, 0))
        self.status = ttk.Label(root, style="Body.TLabel")
        self.status.pack(anchor="w", padx=16)
        self.job_title = ttk.Label(root, text="Waiting for photos from the app...", style="Body.TLabel", font=("Segoe UI", 11, "bold"))
        self.job_title.pack(anchor="w", padx=16, pady=(14, 4))
        self.job = tk.Frame(root, bg="#FFFFFF", height=250)
        self.job.pack(fill="x", padx=16)
        ttk.Label(root, text="Activity", style="Body.TLabel", font=("Segoe UI", 10, "bold")).pack(anchor="w", padx=16, pady=(14, 2))
        self.log = tk.Listbox(root, height=10, font=("Consolas", 9), bd=0, highlightthickness=0)
        self.log.pack(fill="both", expand=True, padx=16, pady=(0, 14))
        self.add_log(f"Signed in as {worker.fs.who}. Models {MODEL_NAME}, {DETECTOR_NAME} and {CLIP_NAME} on {worker.model.provider}.")
        self.add_log(f"Thresholds: same >= {SAME}, maybe >= {MAYBE}, animal >= {ANIMAL_MIN}. Listening for photos (Firestore pushes them here).")
        root.protocol("WM_DELETE_WINDOW", self.close)
        self.tick()

    def add_log(self, text: str):
        self.log.insert(0, f"{datetime.now():%H:%M:%S}  {text}")

    def thumb(self, data: bytes, size: int) -> ImageTk.PhotoImage:
        im = Image.open(io.BytesIO(data), formats=APP_FORMATS).convert("RGB")
        im.thumbnail((size, size))
        p = ImageTk.PhotoImage(im)
        self.photos.append(p)
        return p

    def show_compare(self, e: dict):
        for w in self.job.winfo_children():
            w.destroy()
        self.photos.clear()
        self.job_title.config(text=f"Photo from @{e['who']}: compared with {len(e['images'])} nearby street animal(s) in {e['ms']} ms")
        left = tk.Frame(self.job, bg="#FFFFFF")
        left.pack(side="left", padx=12, pady=12)
        tk.Label(left, image=self.thumb(e["query"], 200), bg="#FFFFFF").pack()
        tk.Label(left, text="New photo", bg="#FFFFFF", font=("Segoe UI", 9, "bold")).pack()
        for i, (img, r) in enumerate(zip(e["images"], e["results"])):
            colour = {"same": "#2E7D32", "maybe": "#E68A00", "different": "#888888"}[r["verdict"]]
            cell = tk.Frame(self.job, bg="#FFFFFF")
            cell.pack(side="left", padx=6, pady=12)
            tk.Label(cell, image=self.thumb(img, 120), bg="#FFFFFF").pack()
            label = e["labels"][i] if i < len(e["labels"]) and e["labels"][i] else r["id"][:10]
            tk.Label(cell, text=label, bg="#FFFFFF", font=("Segoe UI", 9)).pack()
            tk.Label(cell, text=f"{r['verdict'].upper()}  {r['similarity'] * 100:.0f}%", fg=colour, bg="#FFFFFF",
                     font=("Segoe UI", 10, "bold")).pack()
        best = max(e["results"], key=lambda r: r["similarity"], default=None)
        summary = "no candidates" if best is None else f"best {best['verdict']} ({best['similarity'] * 100:.0f}%)"
        self.add_log(f"@{e['who']}: {len(e['images'])} candidate(s), {summary}, {e['ms']} ms")

    def show_check(self, e: dict):
        for w in self.job.winfo_children():
            w.destroy()
        self.photos.clear()
        verdicts = e["classification"]
        animals = sum(1 for c in verdicts if c["kind"] == "animal")
        title = f"Photos from @{e['who']}: {animals} of {len(verdicts)} show an animal"
        if e["images"]:
            title += f"; compared with {len(e['images'])} nearby street animal(s)"
        self.job_title.config(text=f"{title} ({e['ms']} ms)")
        row = tk.Frame(self.job, bg="#FFFFFF")
        row.pack(fill="x")
        for img, c in zip(e["photos"], verdicts):
            colour = "#2E7D32" if c["kind"] == "animal" else "#C62828"
            text = {"animal": f"ANIMAL: {c['animalType']}", "person": "PERSON / SELFIE"}.get(c["kind"], "NOT AN ANIMAL")
            cell = tk.Frame(row, bg="#FFFFFF")
            cell.pack(side="left", padx=6, pady=8)
            tk.Label(cell, image=self.thumb(img, 110), bg="#FFFFFF").pack()
            tk.Label(cell, text=f"{text}  {c['score'] * 100:.0f}%", fg=colour, bg="#FFFFFF", font=("Segoe UI", 9, "bold")).pack()
            tk.Label(cell, text=c["phrase"] or "-", bg="#FFFFFF", font=("Segoe UI", 8)).pack()
        if e["images"]:
            row = tk.Frame(self.job, bg="#FFFFFF")
            row.pack(fill="x")
            tk.Label(row, text="Nearby:", bg="#FFFFFF", font=("Segoe UI", 9, "bold")).pack(side="left", padx=6)
            for i, (img, r) in enumerate(zip(e["images"], e["results"])):
                colour = {"same": "#2E7D32", "maybe": "#E68A00", "different": "#888888"}[r["verdict"]]
                cell = tk.Frame(row, bg="#FFFFFF")
                cell.pack(side="left", padx=6, pady=6)
                tk.Label(cell, image=self.thumb(img, 80), bg="#FFFFFF").pack()
                label = e["labels"][i] if i < len(e["labels"]) and e["labels"][i] else r["id"][:10]
                tk.Label(cell, text=f"{label}: {r['verdict'].upper()} {r['similarity'] * 100:.0f}%", fg=colour, bg="#FFFFFF",
                         font=("Segoe UI", 8, "bold")).pack()
        names = ", ".join(c["phrase"] or c["kind"] for c in verdicts) or "no photos"
        best = max(e["results"], key=lambda r: r["similarity"], default=None)
        match = "" if best is None else f"; best match {best['verdict']} ({best['similarity'] * 100:.0f}%)"
        self.add_log(f"@{e['who']}: {names}{match}, {e['ms']} ms")

    def show_review(self, e: dict):
        """A sighting's photo and the nearby cats in the order sent back. No verdicts: the person decides."""
        for w in self.job.winfo_children():
            w.destroy()
        self.photos.clear()
        review = e["review"]
        self.job_title.config(text=f"Photo review for @{e['who']}: {len(review['ranked'])} cat(s) ranked, "
                                   f"{len(review['unavailable'])} unavailable ({e['ms']} ms)")
        left = tk.Frame(self.job, bg="#FFFFFF")
        left.pack(side="left", padx=12, pady=12)
        tk.Label(left, image=self.thumb(e["query"], 200), bg="#FFFFFF").pack()
        tk.Label(left, text="New photo", bg="#FFFFFF", font=("Segoe UI", 9, "bold")).pack()
        for place, r in enumerate(review["ranked"], 1):
            cell = tk.Frame(self.job, bg="#FFFFFF")
            cell.pack(side="left", padx=6, pady=12)
            preview = e["previews"].get(r["id"])
            if preview:
                tk.Label(cell, image=self.thumb(preview, 110), bg="#FFFFFF").pack()
            name = e["labels"].get(r["id"]) or r["id"][:10]
            tk.Label(cell, text=f"{place}. {name}", bg="#FFFFFF", font=("Segoe UI", 9, "bold")).pack()
            tk.Label(cell, text=f"{len(r['photoIds'])} photos, score {r['score']:.2f}", bg="#FFFFFF", font=("Segoe UI", 8)).pack()
        reasons = ", ".join(f"{u['id'][:8]}: {u['reason']}" for u in review["unavailable"])
        self.add_log(f"@{e['who']}: review of {len(review['ranked'])} cat(s){'; ' + reasons if reasons else ''}, {e['ms']} ms")

    def show_match(self, e: dict):
        """A lost report and a sighting scored by the background matcher, in the activity log."""
        r, lost, s = e["result"], e["lost"], e["sighting"]
        name = lost.get("name") or f"lost {lost.get('animalType', 'animal')}"
        self.add_log(f"Match: {name} <-> {s.get('category')} by @{s.get('ownerName', '?')}: {r.tier.upper()} "
                     f"(photo {r.visual:.2f}, {r.distance_km:.1f} km, {r.days_apart:+.1f} days, {r.method})")

    def pop_up(self):
        self.root.deiconify()
        self.root.lift()
        self.root.attributes("-topmost", True)
        self.root.after(1500, lambda: self.root.attributes("-topmost", False))
        self.root.bell()

    def tick(self):
        try:
            while True:
                kind, e = self.events.get_nowait()
                if kind == "compare":
                    self.show_compare(e)
                    self.pop_up()
                elif kind == "check":
                    self.show_check(e)
                    self.pop_up()
                elif kind == "review":
                    self.show_review(e)
                    self.pop_up()
                elif kind == "embed":
                    self.add_log(f"@{e['who']}: Recognition test, embedded {e['count']} photo(s)")
                elif kind == "match":
                    self.show_match(e)
                else:
                    self.add_log(e)
        except queue.Empty:
            pass
        rec = self.worker.recovery
        matched = (f"  |  watching {len(rec.posts)} posts{'' if rec.healthy() else ' (reconnecting)'}, matches: {rec.matched}, "
                   f"owners told: {rec.notified}") if rec else ""
        self.status.config(text=f"Online, listening  |  {MODEL_NAME} + {CLIP_NAME} on {self.worker.model.provider}  |  jobs answered: {self.worker.handled}{matched}")
        self.root.after(250, self.tick)

    def close(self):
        self.add_log("Going offline...")
        threading.Thread(target=self.worker.stop, daemon=True).start()
        self.root.after(300, self.root.destroy)


class ForwardErrors(logging.Handler):
    """Google's Firestore client reports listener failures only through logging; show them in the window."""

    def __init__(self, events: queue.Queue):
        super().__init__(logging.WARNING)
        self.events = events

    def emit(self, record):
        self.events.put(("log", f"Firestore: {record.getMessage()[:300]}"))


def single_instance():
    """Only one server at a time: two would both answer every job and both write the online status."""
    import msvcrt
    handle = open(HERE / ".server.lock", "w")
    try:
        msvcrt.locking(handle.fileno(), msvcrt.LK_NBLCK, 1)
    except OSError:
        return None
    return handle  # kept open (and locked) until the app exits


def main():
    p = argparse.ArgumentParser(description="CatMap AI Server")
    p.add_argument("--emulator", action="store_true", help="use the local Firebase emulators (testing)")
    p.add_argument("--cpu", action="store_true", help="don't use the GPU")
    p.add_argument("--no-matching", action="store_true", help="don't run the background lost/sighting matcher")
    p.add_argument("--review", action="store_true",
                   help="answer photo review jobs (experimental; deploy the photo review rules first)")
    args = p.parse_args()
    root = tk.Tk()
    root.withdraw()
    lock = single_instance()
    if lock is None:
        from tkinter import messagebox
        messagebox.showinfo("CatMap AI Server", "The CatMap AI Server is already running. Look for its window on the taskbar.")
        sys.exit(0)
    try:
        fs = Firestore(args.emulator)
        model = Model(args.cpu)
        classifier = Classifier(args.cpu)
        recognizer = None if args.no_matching else Recognizer(args.cpu, model, Detector(args.cpu))
    except SystemExit as e:
        from tkinter import messagebox
        messagebox.showerror("CatMap AI Server", str(e))
        sys.exit(1)
    events: queue.Queue = queue.Queue()
    logging.getLogger("google").addHandler(ForwardErrors(events))
    reviewer = None
    if args.review:
        # A missing or altered bundle only switches photo review off; everything else still starts.
        try:
            from review import Reviewer
            reviewer = Reviewer(args.cpu)
            events.put(("log", f"Photo review on ({reviewer.space}, {reviewer.provider})."))
        except Exception as e:
            events.put(("log", f"Photo review is off: {e}"))
    worker = Worker(model, classifier, fs, events, recognizer, reviewer)
    App(root, worker, events)
    root.deiconify()
    worker.start()
    root.mainloop()
    worker.stop()


if __name__ == "__main__":
    main()
