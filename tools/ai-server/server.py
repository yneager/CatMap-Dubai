"""CatMap AI server: same-animal check for street-animal posts, run on the owner's PC.

The app sends a cropped photo of the new animal plus crops of nearby street-animal posts. This server embeds them with
pet-recognition-base (open-noodle, Apache-2.0: DINOv2-base + 512-d projection, ONNX) and returns the cosine
similarity of each candidate with a verdict. It never touches Firestore data: the user confirms in the app, and the
app writes through the normal security rules, so a forged result can only change what that user's own phone shows.

Every /compare request must carry a Firebase ID token for the CatMap project, from a verified account. Requests are
rate limited per account and images are size-checked before decoding.

    python server.py              # listens on 127.0.0.1:8765 only
    python server.py --emulator   # local testing against the Firebase emulators (accepts emulator tokens)

The server only listens on this PC's loopback address. A phone reaches it over the USB cable with
`adb reverse tcp:8765 tcp:8765`, so nothing is exposed to the network.

See README.md for setup.
"""
from __future__ import annotations

import argparse
import hashlib
import io
import json
import os
import threading
import time
from collections import defaultdict, deque
from pathlib import Path

import numpy as np
import onnxruntime as ort
import uvicorn
from fastapi import FastAPI, File, Form, Header, HTTPException, UploadFile
from PIL import Image

HERE = Path(__file__).resolve().parent
MODEL_REPO = "open-noodle/pet-recognition-base"
MODEL_FILE = "recognition/model.onnx"
MODEL_NAME = "pet-recognition-base"

# Verdict thresholds on cosine similarity. Provisional: the model's clustering sweep (metrics.json) suggests ~0.5,
# but uncropped photos of two different kittens already scored 0.57 here, so the defaults are stricter. Measure
# them with Admin > Recognition test (it reports the AI model's scores too) and set SAME_THRESHOLD / MAYBE_THRESHOLD.
SAME = float(os.environ.get("SAME_THRESHOLD", "0.75"))
MAYBE = float(os.environ.get("MAYBE_THRESHOLD", "0.60"))
MAX_CANDIDATES = 10
MAX_BYTES = 1_500_000
RATE_LIMIT = (60, 600)  # requests per window (seconds) per account
Image.MAX_IMAGE_PIXELS = 4096 * 4096
MEAN = np.array([0.485, 0.456, 0.406], dtype=np.float32)
STD = np.array([0.229, 0.224, 0.225], dtype=np.float32)

args: argparse.Namespace
session: ort.InferenceSession
app = FastAPI(title="CatMap AI server", docs_url=None, redoc_url=None, openapi_url=None)
_calls: dict[str, deque] = defaultdict(deque)
_token_cache: dict[str, tuple[float, dict]] = {}
_lock = threading.Lock()


# ---------- model ----------

def load_model() -> ort.InferenceSession:
    from huggingface_hub import hf_hub_download

    path = hf_hub_download(MODEL_REPO, MODEL_FILE, cache_dir=str(HERE / "models"))
    wanted = ["DmlExecutionProvider", "CPUExecutionProvider"] if not args.cpu else ["CPUExecutionProvider"]
    providers = [p for p in wanted if p in ort.get_available_providers()]
    s = ort.InferenceSession(path, providers=providers)
    print(f"Model {MODEL_NAME} loaded with {s.get_providers()[0]}")
    return s


def preprocess(data: bytes) -> np.ndarray:
    if len(data) > MAX_BYTES:
        raise HTTPException(413, "image too large")
    try:
        with Image.open(io.BytesIO(data)) as im:
            if im.format not in ("JPEG", "PNG", "WEBP"):
                raise HTTPException(415, "unsupported image type")
            rgb = im.convert("RGB").resize((224, 224), Image.BICUBIC)
    except HTTPException:
        raise
    except Exception:
        raise HTTPException(400, "unreadable image")
    x = (np.asarray(rgb, dtype=np.float32) / 255.0 - MEAN) / STD
    return x.transpose(2, 0, 1)


def embed(batch: list[np.ndarray]) -> np.ndarray:
    out = session.run(None, {session.get_inputs()[0].name: np.stack(batch).astype(np.float32)})[0]
    return out / np.linalg.norm(out, axis=1, keepdims=True).clip(1e-9)


# ---------- auth ----------

def verify(authorization: str | None) -> str:
    if not authorization or not authorization.startswith("Bearer "):
        raise HTTPException(401, "sign in required")
    token = authorization[7:].strip()
    key = hashlib.sha256(token.encode()).hexdigest()
    now = time.time()
    cached = _token_cache.get(key)
    if cached and cached[0] > now:
        claims = cached[1]
    else:
        claims = emulator_claims(token) if args.emulator else google_claims(token)
        _token_cache[key] = (min(now + 300, claims.get("exp", now)), claims)
    if not claims.get("email_verified"):
        raise HTTPException(403, "verified account required")
    uid = claims.get("user_id") or claims.get("sub")
    with _lock:
        q = _calls[uid]
        while q and q[0] < now - RATE_LIMIT[1]:
            q.popleft()
        if len(q) >= RATE_LIMIT[0]:
            raise HTTPException(429, "too many requests")
        q.append(now)
    return uid


def google_claims(token: str) -> dict:
    from google.auth.transport import requests as grequests
    from google.oauth2 import id_token

    try:
        return id_token.verify_firebase_token(token, grequests.Request(), audience=args.project)
    except Exception:
        raise HTTPException(401, "invalid token")


def emulator_claims(token: str) -> dict:
    """Auth-emulator tokens are unsigned. Only accepted with --emulator, for a demo- project."""
    import base64

    try:
        payload = token.split(".")[1]
        claims = json.loads(base64.urlsafe_b64decode(payload + "=" * (-len(payload) % 4)))
    except Exception:
        raise HTTPException(401, "invalid token")
    if claims.get("aud") != args.project:
        raise HTTPException(401, "wrong project")
    return claims


# ---------- API ----------

@app.get("/health")
def health():
    return {"status": "ok", "model": MODEL_NAME, "provider": session.get_providers()[0], "same": SAME, "maybe": MAYBE}


@app.post("/compare")
async def compare(
    query: UploadFile = File(...),
    candidates: list[UploadFile] = File(default=[]),
    ids: str = Form("[]"),
    authorization: str | None = Header(default=None),
):
    verify(authorization)
    try:
        id_list = [str(i)[:128] for i in json.loads(ids)]
    except Exception:
        raise HTTPException(400, "bad ids")
    if len(candidates) > MAX_CANDIDATES or len(id_list) != len(candidates):
        raise HTTPException(400, "bad candidates")
    started = time.time()
    images = [preprocess(await query.read())] + [preprocess(await c.read()) for c in candidates]
    vectors = embed(images)
    sims = (vectors[1:] @ vectors[0]).tolist() if candidates else []
    results = [
        {"id": i, "similarity": round(float(s), 4), "verdict": "same" if s >= SAME else "maybe" if s >= MAYBE else "different"}
        for i, s in zip(id_list, sims)
    ]
    return {"model": MODEL_NAME, "results": results, "ms": int((time.time() - started) * 1000)}


@app.post("/embed")
async def embed_images(images: list[UploadFile] = File(...), authorization: str | None = Header(default=None)):
    """Embeddings for up to 16 crops, for the app's Recognition test (it compares them on the phone)."""
    verify(authorization)
    if not 1 <= len(images) <= 16:
        raise HTTPException(400, "send 1 to 16 images")
    vectors = embed([preprocess(await i.read()) for i in images])
    return {"model": MODEL_NAME, "embeddings": [[round(float(x), 5) for x in v] for v in vectors]}


def main() -> None:
    global args, session
    p = argparse.ArgumentParser(description="CatMap AI server")
    p.add_argument("--port", type=int, default=8765)
    p.add_argument("--project", default=os.environ.get("CATMAP_PROJECT", "catmap-dubai"))
    p.add_argument("--emulator", action="store_true", help="accept Auth-emulator tokens (local testing only)")
    p.add_argument("--cpu", action="store_true", help="don't try the GPU (DirectML)")
    args = p.parse_args()
    if args.emulator:
        if not args.project.startswith("demo-"):
            args.project = "demo-catmap"
        print("WARNING: emulator mode - tokens are NOT verified. For local testing only.")
    session = load_model()
    print(f"Serving on 127.0.0.1:{args.port}. Connect a phone with: adb reverse tcp:{args.port} tcp:{args.port}")
    uvicorn.run(app, host="127.0.0.1", port=args.port, log_level="warning")


if __name__ == "__main__":
    main()
