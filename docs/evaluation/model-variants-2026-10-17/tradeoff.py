"""For the variant chosen by CATMAP_EMBEDDER / CATMAP_FLIP_TTA: the strict labels lookalikes.py implies (CatMap's rule:
strong = 99.9 % and possible = 95 % of same-breed different animals, weak = 99 % of other-breed animals, whole-body
crops), and how many true same-dog DogFaceNet sightings (C'', test half, as evaluate.py) still reach each label.
Reads only the caches/outputs of evaluate.py and lookalikes.py. Prints JSON."""
import json
import random
import sys
from pathlib import Path

import numpy as np

SERVER = Path(r"C:\Users\mohammad\Desktop\cat-server")
sys.path.insert(0, str(SERVER))
sys.path.insert(0, str(SERVER / "eval"))
import evaluate  # noqa: E402
import recognition  # noqa: E402

v = recognition.VARIANT
out = {"variant": v or "-base", "model": recognition.EMBEDDING_MODEL}
th = {}
for sp in ("cat", "dog"):
    r = json.loads((SERVER / "eval" / "out" / f"oxford-{sp}s{v}" / "results.json").read_text())
    c = r["crop"]
    th[sp] = {"strong": c["same_breed_quantiles"]["0.999"], "possible": c["same_breed_quantiles"]["0.95"],
              "weak": c["other_breed_quantiles"]["0.99"]}
    out[sp] = {"same_breed_mean": c["same_breed_mean"], "other_breed_mean": c["other_breed_mean"],
               "same_breed_q": c["same_breed_quantiles"], "other_breed_q99": c["other_breed_quantiles"]["0.99"],
               "labels": th[sp], "edited_copy": r.get("edited_copy_cosine"), "detect_ms": r["detect_crop_ms_per_photo"]}

data = SERVER / "eval" / "data" / "dogfacenet"
ids = evaluate.load_dataset(data)
files = [f for n in sorted(ids) for f in ids[n]]
index, i = {}, 0
for n in sorted(ids):
    index[n] = list(range(i, i + len(ids[n])))
    i += len(ids[n])
# evaluate.py was run with a relative --data path; the cache key uses the path strings as given.
files_rel = [Path("eval") / f.relative_to(SERVER / "eval") for f in files]
fp, emb, info, timing = evaluate.features(files_rel, None, False, "dog", SERVER / "eval" / "cache")
tune, test, unseen = evaluate.split(ids, 7, 0.2)
random.Random(7)  # the tuning build is not needed; the test build uses seed + 1 as in evaluate.main
gallery, probes = evaluate.build(test, index, 3, random.Random(8))
rows = [p[1] for p in probes]
names = list(gallery)
truth = np.array([names.index(p[0]) for p in probes])
table = evaluate.score_table("C-both", gallery, rows, fp, emb)
pos = table[np.arange(len(truth)), truth]
mask = np.ones_like(table, dtype=bool)
mask[np.arange(len(truth)), truth] = False
neg = table[mask]
out["dogfacenet_same_dog_mean"] = float(pos.mean())
out["dogfacenet_reaching"] = {sp: {t: {"same_dog": float((pos >= x).mean()), "different_dog": float((neg >= x).mean())}
                                   for t, x in th[sp].items()} for sp in th}
print(json.dumps(out, indent=1))
