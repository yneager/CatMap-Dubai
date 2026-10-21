"""Speed and memory of the variant chosen by CATMAP_EMBEDDER / CATMAP_FLIP_TTA, same conditions for every variant:
model load time, process memory (RSS) and GPU dedicated memory with detector + embedder loaded, one photo
detect + crop + embed (median of 30, Oxford cat photos), embed only, and batched embedding (256 crops, batch 16)."""
import io
import json
import os
import subprocess
import sys
import time
from pathlib import Path

import numpy as np
import psutil
import pyarrow.parquet as pq

SERVER = Path(r"C:\Users\mohammad\Desktop\cat-server")
sys.path.insert(0, str(SERVER))
import recognition  # noqa: E402


def rss():
    return psutil.Process().memory_info().rss / 2**20


def gpu_mb():
    try:
        r = subprocess.run(["typeperf", rf"\GPU Process Memory(pid_{os.getpid()}_*)\Dedicated Usage", "-sc", "1"],
                           capture_output=True, text=True, timeout=30)
        vals = [l.split('","')[1:] for l in r.stdout.splitlines() if l.startswith('"') and "/" in l[:12]]
        return sum(float(x.strip('"')) for x in vals[0] if x.strip('"')) / 2**20 if vals else None
    except Exception as e:  # noqa: BLE001
        return f"n/a ({e})"


rows = pq.read_table(SERVER / "eval" / "data" / "oxford-raw" / "train.parquet", columns=["image", "label_cat_dog"]).to_pylist()
photos = [recognition.open_image(r["image"]["bytes"]) for r in rows if r["label_cat_dog"] == 0][:256]
m0, g0 = rss(), gpu_mb()
t = time.perf_counter()
det = recognition.Detector(False)
t_det = time.perf_counter() - t
m1 = rss()
t = time.perf_counter()
model = recognition.Model(False)
t_emb = time.perf_counter() - t
crops = [recognition.crop_animal(p, det, "cat").image for p in photos]
model.embed(crops[:16])
model.embed(crops[:1])
m2, g2 = rss(), gpu_mb()
one, emb_one = [], []
for p in photos[:30]:
    t = time.perf_counter()
    c = recognition.crop_animal(p, det, "cat")
    t1 = time.perf_counter()
    model.embed([c.image])
    t2 = time.perf_counter()
    one.append(t2 - t)
    emb_one.append(t2 - t1)
t = time.perf_counter()
model.embed(crops, batch=16)
batched = (time.perf_counter() - t) / len(crops)
print(json.dumps({"model": recognition.EMBEDDING_MODEL, "provider": model.provider,
                  "onnx_mb": Path(model.session._model_path).stat().st_size / 2**20 if hasattr(model.session, "_model_path") else None,
                  "load_embedder_s": t_emb, "load_detector_s": t_det,
                  "rss_start_mb": m0, "rss_detector_mb": m1, "rss_both_mb": m2, "rss_peak_mb": rss(),
                  "gpu_dedicated_start_mb": g0, "gpu_dedicated_loaded_mb": g2,
                  "one_photo_ms_median": 1000 * float(np.median(one)), "embed_one_ms_median": 1000 * float(np.median(emb_one)),
                  "embed_batched_ms_per_photo": 1000 * batched}, indent=1))
