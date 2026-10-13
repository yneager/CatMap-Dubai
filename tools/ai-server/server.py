"""CatMap AI Server: a desktop app on the owner's PC that checks whether a street animal is already on the map.

How it works (no open ports, no cable, works on any network):
  1. When someone posts a street animal, the phone writes a job to Firestore `aiJobs/{uid}`: a crop of the new animal
     and crops of nearby street-animal posts (small JPEGs, kept in Firestore like every other CatMap image).
  2. This app polls for pending jobs (outgoing HTTPS only), embeds the crops with pet-recognition-base (open-noodle,
     Apache-2.0: DINOv2-base + 512-d projection, ONNX, GPU through DirectML when available) and writes back the cosine
     similarity of each candidate with a verdict. The phone is listening to that document and shows the answer.
  3. Every 30 s it updates `config/aiWorker` (a heartbeat), so phones skip the check instantly when the PC is off.

It signs in as the AI worker account (custom claim catmapAiWorker, created by tools/demo/create-demo-accounts.mjs,
password in worker.local.json). The security rules let that account read jobs and write only results; it can't touch
posts or users. The window pops to the front whenever an image arrives, showing the photo, the candidates and the scores.

    start.cmd                  (double-click; first run installs everything and downloads the model, ~350 MB)
    python server.py --emulator  local Firebase emulators, for testing
"""
from __future__ import annotations

import argparse
import base64
import io
import json
import os
import queue
import sys
import threading
import time
import tkinter as tk
from datetime import datetime, timezone
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
POLL_SECONDS = 2.0
HEARTBEAT_SECONDS = 30.0
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


# ---------- Firestore over REST ----------

def to_value(v):
    if isinstance(v, bool):
        return {"booleanValue": v}
    if isinstance(v, int):
        return {"integerValue": str(v)}
    if isinstance(v, float):
        return {"doubleValue": v}
    if isinstance(v, str):
        return {"stringValue": v}
    if isinstance(v, datetime):
        return {"timestampValue": v.isoformat().replace("+00:00", "Z")}
    if isinstance(v, list):
        return {"arrayValue": {"values": [to_value(x) for x in v]}}
    if isinstance(v, dict):
        return {"mapValue": {"fields": {k: to_value(x) for k, x in v.items()}}}
    raise TypeError(type(v))


def from_value(v):
    if "bytesValue" in v:
        return base64.b64decode(v["bytesValue"])
    if "arrayValue" in v:
        return [from_value(x) for x in v["arrayValue"].get("values", [])]
    if "mapValue" in v:
        return {k: from_value(x) for k, x in v["mapValue"].get("fields", {}).items()}
    for key in ("stringValue", "booleanValue", "doubleValue", "timestampValue"):
        if key in v:
            return v[key]
    if "integerValue" in v:
        return int(v["integerValue"])
    return None


class Firestore:
    """Signed in as the AI worker (rules apply), or as the emulator owner with --emulator."""

    def __init__(self, emulator: bool):
        self.emulator = emulator
        if emulator:
            self.project = "demo-catmap"
            self.base = f"http://127.0.0.1:8080/v1/projects/{self.project}/databases/(default)/documents"
            self.who = "emulator owner"
            return
        try:
            gs = json.loads((ROOT / "app" / "google-services.json").read_text(encoding="utf-8"))
            self.api_key = gs["client"][0]["api_key"][0]["current_key"]
            self.project = gs["project_info"]["project_id"]
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
        j = r.json()
        self.who = creds["email"]
        self.id_token, self.refresh_token = j["idToken"], j["refreshToken"]
        self.expires = time.time() + int(j.get("expiresIn", 3600)) - 120
        self.base = f"https://firestore.googleapis.com/v1/projects/{self.project}/databases/(default)/documents"

    def headers(self) -> dict:
        if self.emulator:
            return {"Authorization": "Bearer owner"}
        if time.time() > self.expires:
            r = requests.post(f"https://securetoken.googleapis.com/v1/token?key={self.api_key}",
                              data={"grant_type": "refresh_token", "refresh_token": self.refresh_token}, timeout=20)
            r.raise_for_status()
            j = r.json()
            self.id_token, self.refresh_token = j["id_token"], j["refresh_token"]
            self.expires = time.time() + int(j.get("expires_in", 3600)) - 120
        return {"Authorization": f"Bearer {self.id_token}"}

    def pending_jobs(self) -> list[dict]:
        query = {"structuredQuery": {
            "from": [{"collectionId": "aiJobs"}],
            "where": {"fieldFilter": {"field": {"fieldPath": "status"}, "op": "EQUAL", "value": {"stringValue": "pending"}}},
            "limit": 5,
        }}
        r = requests.post(f"{self.base}:runQuery", headers=self.headers(), json=query, timeout=20)
        r.raise_for_status()
        return [x["document"] for x in r.json() if "document" in x]

    def get(self, path: str) -> dict | None:
        r = requests.get(f"{self.base}/{path}", headers=self.headers(), timeout=20)
        return {k: from_value(v) for k, v in r.json().get("fields", {}).items()} if r.ok else None

    def patch(self, name_or_path: str, fields: dict, update_time: str | None = None) -> bool:
        url = name_or_path if name_or_path.startswith("projects/") else None
        url = (f"{self.base.split('/v1/')[0]}/v1/{url}" if url else f"{self.base}/{name_or_path}")
        params = [("updateMask.fieldPaths", k) for k in fields]
        if update_time:
            params.append(("currentDocument.updateTime", update_time))
        r = requests.patch(url, headers=self.headers(), params=params,
                           json={"fields": {k: to_value(v) for k, v in fields.items()}}, timeout=30)
        return r.ok


# ---------- worker ----------

class Worker(threading.Thread):
    def __init__(self, model: Model, fs: Firestore, events: queue.Queue):
        super().__init__(daemon=True)
        self.model, self.fs, self.events = model, fs, events
        self.running = True
        self.handled = 0
        self.names: dict[str, str] = {}
        self.given_up: set[str] = set()  # requests whose answer couldn't be saved; not retried every poll

    def heartbeat(self, online: bool):
        self.fs.patch("config/aiWorker", {
            "online": online, "lastSeen": datetime.now(timezone.utc), "model": MODEL_NAME,
            "provider": self.model.provider, "same": SAME, "maybe": MAYBE,
        })

    def username(self, uid: str) -> str:
        if uid not in self.names:
            profile = self.fs.get(f"users/{uid}") or {}
            self.names[uid] = profile.get("username") or uid[:8]
        return self.names[uid]

    def run(self):
        last_beat = 0.0
        while self.running:
            try:
                if time.time() - last_beat > HEARTBEAT_SECONDS:
                    self.heartbeat(True)
                    last_beat = time.time()
                for doc in self.fs.pending_jobs():
                    self.handle(doc)
            except Exception as e:  # network hiccups: keep going
                self.events.put(("log", f"Problem: {e}"))
            time.sleep(POLL_SECONDS)
        try:
            self.heartbeat(False)
        except Exception:
            pass

    def handle(self, doc: dict):
        f = {k: from_value(v) for k, v in doc.get("fields", {}).items()}
        name = doc["name"]
        request_id = str(f.get("requestId", ""))
        if request_id in self.given_up:
            return
        kind = f.get("kind", "compare")
        images = [b for b in f.get("images", []) if isinstance(b, bytes)][:MAX_IMAGES]
        query = f.get("query") if isinstance(f.get("query"), bytes) else None
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
                    "doneAt": datetime.now(timezone.utc), "answeredRequestId": request_id})
        # The answer names the request it answers; if the phone replaced the job meanwhile, the phone ignores it.
        if self.fs.patch(name, out):
            self.handled += 1
        else:
            self.given_up.add(request_id)
            self.events.put(("log", f"Couldn't save the answer for @{who}"))


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
        self.add_log(f"Thresholds: same >= {SAME}, maybe >= {MAYBE}. Checking for jobs every {POLL_SECONDS:g} s.")
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
        self.status.config(text=f"Online  |  {MODEL_NAME} on {self.worker.model.provider}  |  photos checked: {self.worker.handled}")
        self.root.after(250, self.tick)

    def close(self):
        self.worker.running = False
        self.add_log("Going offline...")
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
    worker.running = False
    worker.join(timeout=POLL_SECONDS + 5)


if __name__ == "__main__":
    main()
