"""CatMap AI Server: a desktop app on the owner's PC that checks whether a street animal is already on the map.

How it works (no open ports, no cable, works on any network):
  1. When someone posts a street animal, the phone writes a job to Firestore `aiJobs/{uid}`: a crop of the new animal
     and crops of nearby street-animal posts (small JPEGs, kept in Firestore like every other CatMap image).
  2. Firestore pushes the new job to this app over a live listener (one outgoing streaming connection; nothing is
     polled). The app embeds the crops with pet-recognition-base (open-noodle, Apache-2.0: DINOv2-base + 512-d
     projection, ONNX, GPU through DirectML when available) and writes back the cosine similarity of each candidate
     with a verdict. The phone is listening to that document too and shows the answer.
  3. `config/aiWorker` says whether the PC is online: set when the app opens and closes, refreshed every 5 minutes in
     case the PC crashes, so phones skip the check at once when the PC is off.

It signs in as the AI worker account (custom claim catmapAiWorker, created by tools/demo/create-demo-accounts.mjs,
password in worker.local.json). The security rules let that account read jobs and write only results; it can't touch
posts or users. The window pops to the front whenever an image arrives, showing the photo, the candidates and the scores.

    start.cmd                  (double-click; first run installs everything and downloads the model, ~350 MB)
    python server.py --emulator  local Firebase emulators, for testing
"""
from __future__ import annotations

import argparse
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

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
MODEL_REPO = "open-noodle/pet-recognition-base"
MODEL_FILE = "recognition/model.onnx"
MODEL_NAME = "pet-recognition-base"

# Verdict thresholds on cosine similarity. Provisional: the model's clustering sweep (metrics.json) suggests ~0.5, but
# uncropped photos of two different kittens scored 0.57 in testing, so the defaults are stricter. Measure them with
# Admin > Recognition test in the app and set SAME_THRESHOLD / MAYBE_THRESHOLD before starting.
SAME = float(os.environ.get("SAME_THRESHOLD", "0.75"))
MAYBE = float(os.environ.get("MAYBE_THRESHOLD", "0.60"))
HEARTBEAT_SECONDS = 300.0  # crash safeguard only; the app also reports online/offline when it opens and closes
RELISTEN_SECONDS = 50 * 60
MAX_IMAGES = 16
Image.MAX_IMAGE_PIXELS = 4096 * 4096
MEAN = np.array([0.485, 0.456, 0.406], dtype=np.float32)
STD = np.array([0.229, 0.224, 0.225], dtype=np.float32)


# ---------- model ----------

class Model:
    def __init__(self, cpu: bool):
        from huggingface_hub import hf_hub_download

        path = hf_hub_download(MODEL_REPO, MODEL_FILE, cache_dir=str(HERE / "models"))
        wanted = ["CPUExecutionProvider"] if cpu else ["DmlExecutionProvider", "CPUExecutionProvider"]
        self.session = ort.InferenceSession(path, providers=[p for p in wanted if p in ort.get_available_providers()])
        self.provider = self.session.get_providers()[0]

    @staticmethod
    def preprocess(data: bytes) -> np.ndarray:
        with Image.open(io.BytesIO(data)) as im:
            if im.format not in ("JPEG", "PNG", "WEBP"):
                raise ValueError("unsupported image type")
            rgb = im.convert("RGB").resize((224, 224), Image.BICUBIC)
        return ((np.asarray(rgb, dtype=np.float32) / 255.0 - MEAN) / STD).transpose(2, 0, 1)

    def embed(self, images: list[bytes]) -> np.ndarray:
        batch = np.stack([self.preprocess(b) for b in images]).astype(np.float32)
        out = self.session.run(None, {self.session.get_inputs()[0].name: batch})[0]
        return out / np.linalg.norm(out, axis=1, keepdims=True).clip(1e-9)


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
            os.environ["FIRESTORE_EMULATOR_HOST"] = "127.0.0.1:8080"
            self.who = "emulator owner"
            self.client = firestore.Client(project="demo-catmap", credentials=AnonymousCredentials())
            return
        try:
            gs = json.loads((ROOT / "app" / "google-services.json").read_text(encoding="utf-8"))
            self.api_key = gs["client"][0]["api_key"][0]["current_key"]
            project = gs["project_info"]["project_id"]
        except Exception:
            raise SystemExit("app/google-services.json is missing (it holds the Firebase project settings).")
        cred_file = HERE / "worker.local.json"
        if not cred_file.exists():
            raise SystemExit("tools/ai-server/worker.local.json is missing. Run: node tools/demo/create-demo-accounts.mjs")
        creds = json.loads(cred_file.read_text(encoding="utf-8"))
        r = requests.post(f"https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key={self.api_key}",
                          json={"email": creds["email"], "password": creds["password"], "returnSecureToken": True}, timeout=20)
        if not r.ok:
            raise SystemExit(f"AI worker sign-in failed: {r.json().get('error', {}).get('message', r.status_code)}")
        self.who = creds["email"]
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

    def __init__(self, model: Model, fs: Firestore, events: queue.Queue):
        self.model, self.fs, self.events = model, fs, events
        self.jobs: queue.Queue = queue.Queue()
        self.running = True
        self.handled = 0
        self.names: dict[str, str] = {}
        self.answered: set[str] = set()
        self.watch = None
        self.listening_since = 0.0

    # Heartbeat: written when the app starts and stops, and every few minutes in case the PC crashes.
    def heartbeat(self, online: bool):
        from google.cloud import firestore
        self.fs.client.document("config/aiWorker").set({
            "online": online, "lastSeen": firestore.SERVER_TIMESTAMP, "model": MODEL_NAME,
            "provider": self.model.provider, "same": SAME, "maybe": MAYBE,
        })

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
                self.jobs.put(change.document)

    def start(self):
        self.heartbeat(True)
        self.listen()
        threading.Thread(target=self.loop, daemon=True).start()

    def loop(self):
        last_beat = time.time()
        while self.running:
            try:
                doc = self.jobs.get(timeout=5)
                self.handle(doc)
            except queue.Empty:
                pass
            except Exception as e:
                self.events.put(("log", f"Problem: {e}"))
            try:
                if time.time() - last_beat > HEARTBEAT_SECONDS:
                    self.heartbeat(True)
                    last_beat = time.time()
                # Re-open the stream now and then so it always runs on a fresh sign-in token.
                if time.time() - self.listening_since > RELISTEN_SECONDS:
                    self.listen()
            except Exception as e:
                self.events.put(("log", f"Problem: {e}"))

    def stop(self):
        self.running = False
        try:
            if self.watch is not None:
                self.watch.unsubscribe()
            self.heartbeat(False)
        except Exception:
            pass

    def username(self, uid: str) -> str:
        if uid not in self.names:
            snap = self.fs.client.document(f"users/{uid}").get()
            self.names[uid] = (snap.to_dict() or {}).get("username") or uid[:8]
        return self.names[uid]

    def handle(self, doc):
        from google.cloud import firestore
        f = doc.to_dict() or {}
        request_id = str(f.get("requestId", ""))
        if f.get("status") != "pending" or request_id in self.answered:
            return
        kind = f.get("kind", "compare")
        images = [bytes(b) for b in f.get("images", []) if isinstance(b, (bytes, bytearray))][:MAX_IMAGES]
        query = bytes(f["query"]) if isinstance(f.get("query"), (bytes, bytearray)) else None
        ids = [str(i) for i in f.get("ids", [])]
        labels = [str(i) for i in f.get("labels", [])]
        who = self.username(str(f.get("uid", "")))
        started = time.time()
        try:
            if kind == "embed":
                vectors = self.model.embed(images) if images else np.zeros((0, 512))
                out = {"status": "done", "embeddings": [{"v": [round(float(x), 5) for x in v]} for v in vectors]}
                self.events.put(("embed", {"who": who, "count": len(images)}))
            else:
                if query is None:
                    raise ValueError("no query image")
                vectors = self.model.embed([query] + images)
                sims = (vectors[1:] @ vectors[0]).tolist() if images else []
                results = [{"id": ids[i] if i < len(ids) else str(i), "similarity": round(float(s), 4),
                            "verdict": "same" if s >= SAME else "maybe" if s >= MAYBE else "different"} for i, s in enumerate(sims)]
                out = {"status": "done", "results": results}
                self.events.put(("compare", {"who": who, "query": query, "images": images, "results": results,
                                              "labels": labels, "ms": int((time.time() - started) * 1000)}))
        except Exception as e:
            out = {"status": "error", "error": str(e)[:200]}
            self.events.put(("log", f"Job from {who} failed: {e}"))
        out.update({"model": MODEL_NAME, "provider": self.model.provider, "ms": int((time.time() - started) * 1000),
                    "doneAt": firestore.SERVER_TIMESTAMP, "answeredRequestId": request_id})
        # Each request is answered once. The answer names the request it answers; if the phone replaced the job in
        # the meantime, the phone ignores this answer and the new job arrives as its own message.
        self.answered.add(request_id)
        try:
            doc.reference.update(out)
            self.handled += 1
        except Exception as e:
            self.events.put(("log", f"Couldn't save the answer for @{who}: {e}"))


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
        self.add_log(f"Signed in as {worker.fs.who}. Model {MODEL_NAME} on {worker.model.provider}.")
        self.add_log(f"Thresholds: same >= {SAME}, maybe >= {MAYBE}. Listening for photos (Firestore pushes them here).")
        root.protocol("WM_DELETE_WINDOW", self.close)
        self.tick()

    def add_log(self, text: str):
        self.log.insert(0, f"{datetime.now():%H:%M:%S}  {text}")

    def thumb(self, data: bytes, size: int) -> ImageTk.PhotoImage:
        im = Image.open(io.BytesIO(data)).convert("RGB")
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
                elif kind == "embed":
                    self.add_log(f"@{e['who']}: Recognition test, embedded {e['count']} photo(s)")
                else:
                    self.add_log(e)
        except queue.Empty:
            pass
        self.status.config(text=f"Online, listening  |  {MODEL_NAME} on {self.worker.model.provider}  |  photos checked: {self.worker.handled}")
        self.root.after(250, self.tick)

    def close(self):
        self.add_log("Going offline...")
        threading.Thread(target=self.worker.stop, daemon=True).start()
        self.root.after(300, self.root.destroy)


def main():
    p = argparse.ArgumentParser(description="CatMap AI Server")
    p.add_argument("--emulator", action="store_true", help="use the local Firebase emulators (testing)")
    p.add_argument("--cpu", action="store_true", help="don't use the GPU")
    args = p.parse_args()
    root = tk.Tk()
    root.withdraw()
    try:
        fs = Firestore(args.emulator)
        model = Model(args.cpu)
    except SystemExit as e:
        from tkinter import messagebox
        messagebox.showerror("CatMap AI Server", str(e))
        sys.exit(1)
    events: queue.Queue = queue.Queue()
    worker = Worker(model, fs, events)
    App(root, worker, events)
    root.deiconify()
    worker.start()
    root.mainloop()
    worker.stop()


if __name__ == "__main__":
    main()
