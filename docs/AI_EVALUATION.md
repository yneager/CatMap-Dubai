# CatMap Dubai: individual-animal recognition, evaluation report

October 2026. Everything below was **measured on this PC** (`Desktop\cat-server\eval`) unless it is marked
**[model card]**, which means it is the model authors' claim, not ours. Raw results are in `docs/evaluation/`.

## 1. Question

Given a new sighting photo, can CatMap tell whether it shows the **same individual animal** as a lost report, well
enough to suggest it to the owner, without telling owners that a look-alike is their pet?

## 2. What was compared

| Id | Method | Where it runs |
|---|---|---|
| A | Colour fingerprint: 27 values, centre-weighted HSV histogram + colour layout (`PostAppearance`). | Phone (the offline fallback) |
| B | `open-noodle/pet-recognition-base` (frozen DINOv2-base + 512-d projection, Apache-2.0), **one** reference photo per animal. | PC worker |
| C | Same model, **several** reference photos per animal: best single pair. | PC worker |
| C' | Same, averaged embedding of the reference photos. | – |
| **C''** | **Half best pair + half averaged** (what CatMap uses). | PC worker |
| F | C + colour fingerprint, fusion weight fitted on the tuning half. | – |
| D | Face + body fusion. **Not run**, see section 7. | – |

Before embedding, the PC finds the animal with **DETR ResNet-50** (COCO, Apache-2.0), takes the main animal of the
post's type (a photo with two cats gives two boxes; the largest confident one is used) and crops it with a 10 %
margin. Embeddings are L2-normalised, compared by cosine, stored per photo as float32 and versioned
(`pet-recognition-base+detr-resnet-50/v1`).

## 3. Data

| Set | What it is | Used for | Licence |
|---|---|---|---|
| **DogFaceNet** | 8,363 aligned dog-face photos of 1,393 dogs. The model's authors used it for evaluation only, never training **[model card]**. | Identification, verification, unseen animals, A vs B vs C | MIT |
| **Oxford-IIIT Pet** (train split) | 1,188 cat photos of 12 breeds, 2,492 dog photos of 25 breeds; every photo a different animal. | False matches between **look-alikes** (same breed), whole-body photos with the detector crop; calibration of the strength labels | CC BY-SA 4.0 |

**No labelled set of individual cats was available** (the Cat Individual Images set needs a Kaggle login and is the
model's own training source; PetFace needs an institutional request and is research-only). Section 8 lists what
therefore can't be claimed.

## 4. Method (no leakage)

* **DogFaceNet:** animals (not photos) are split in half with seed 7. Thresholds and the fusion weight are chosen on
  the **tuning half only**; every number below comes from the other half. In the test half, 80 % of the dogs are
  "registered" (3 reference photos each, like a lost report) and 20 % are never registered (**unseen** animals).
  Every other photo is a new sighting compared with every registered dog, and also with 20 random registered dogs
  (the live situation: the worker only compares nearby open reports of the same type).
* **Oxford-IIIT Pet:** all same-breed pairs of different animals (58,231 cat pairs; exact duplicate photos in the set,
  8 pairs, removed) give the score distribution of look-alikes.
* Reproduce:
  ```
  cd Desktop\cat-server
  .venv\Scripts\python.exe -m pip install -r eval\requirements-eval.txt
  .venv\Scripts\python.exe eval\prepare_dogfacenet.py
  .venv\Scripts\python.exe eval\evaluate.py --data eval\data\dogfacenet --name dogfacenet --no-crop --animal-type dog
  .venv\Scripts\python.exe eval\lookalikes.py cat
  .venv\Scripts\python.exe eval\lookalikes.py dog
  ```
  (download commands are at the top of each script). `evaluate.py` also runs on your own photos: one folder per
  animal, e.g. `--data D:\my-cats --name my-cats --animal-type cat`.

## 5. Results

### 5.1 Identification and verification (DogFaceNet test half, unseen identities)

| Method | Top-1 | Top-3 | AUC | EER |
|---|---|---|---|---|
| A. Colour fingerprint (phone, offline) | 0.179 | 0.270 | 0.857 | 0.227 |
| B. AI model, 1 reference photo | 0.852 | 0.927 | 0.993 | 0.033 |
| C. AI model, best of 3 photos | 0.933 | 0.979 | 0.998 | 0.017 |
| C'. AI model, averaged 3 photos | 0.950 | 0.984 | 0.999 | 0.014 |
| **C''. Half best + half averaged (CatMap)** | **0.954** | **0.986** | 0.998 | **0.014** |
| F. C + colour | same as C: the fitted colour weight was **0** | | | |

Findings:
* The AI model is far better than the colour fingerprint (top-1 0.95 vs 0.18). The colour fingerprint is only a
  fallback; the app says so ("compared by colour only, which can't tell look-alikes apart") and never shows it as
  "strong".
* **Several reference photos help** (top-1 0.85 → 0.93–0.95), so CatMap stores one embedding per photo.
* Keeping every photo ("best pair") vs averaging: averaging was slightly better on these aligned faces; the
  combination was best, so the worker uses it.
* Adding colour to the AI score did not help (weight 0 on the tuning half), so **colour is not fused**.
* For reference **[model card]**: DogFaceNet top-1 0.943, EER 0.031 on a different protocol; Cat Individual Images
  top-1 0.916, EER 0.045. Our DogFaceNet numbers are in line with the card.

### 5.2 The live situation (each sighting against 20 nearby reports, labels from the tuning half)

| Method | Right animal on top, "possible" or better | Right animal on top, "strong" | Wrong animal on top, "possible"+ | Unseen animal: "strong" shown |
|---|---|---|---|---|
| A. Colour (offline) | 0.259 | 0.107 | 0.100 | 0.014 |
| C''. AI model (CatMap) | 0.979 | 0.910 | 0.002 | 0.011 |

### 5.3 Look-alikes: whole-body photos of different animals of the same breed (Oxford-IIIT Pet)

| | Cats | Dogs |
|---|---|---|
| Animals found by the detector | 99.1 % | 99.6 % |
| Photos with several animals | 12 % | 9 % |
| Mean score, same breed (different animals) | **0.53** | 0.34 |
| Mean score, other breeds | 0.07 | 0.06 |
| 95 / 99 / 99.9 % of same-breed pairs score below | 0.79 / 0.84 / 0.88 | 0.66 / 0.76 / 0.89 |
| Colour fingerprint, 99 / 99.9 % of same-breed pairs below | 0.83 / 0.90 | 0.82 / 0.90 |

**This is the most important finding.** To the model, two different cats of the same breed often look alike (mean
0.53; 1 % above 0.84), much more than dogs. Thresholds tuned on dog faces (strong ≥ 0.49) would call most same-breed
look-alike cats "strong". Plain-coated cats (Bombay, Russian Blue) were the hardest; see
`docs/evaluation/oxford-lookalike-cats.png` (the top 3 pairs there are exact duplicates in the dataset, which were
then removed).

### 5.4 Robustness: a cat photo against an edited copy of itself (cosine, mean / 5th percentile)

Other crop 0.96 / 0.92 · mirrored 0.98 / 0.95 · darker 0.99 / 0.97 · partly covered 0.91 / 0.70 · low resolution
0.85 / 0.64 · blurred 0.82 / 0.54. Lighting and angle of crop are handled well; **blur, low resolution and occlusion
cost the most**. (This is the same photo, so it is an upper bound, not a same-cat result.)

### 5.5 Speed and memory (RX 9070 XT via DirectML; Intel CPU)

Embedder load 1.4 s; one photo detect + crop + embed about 60–110 ms; batched embedding 17.6 ms/photo; colour
fingerprint 2.7 ms/photo. Process memory 64 MB → 186 MB with the embedder loaded, 221 MB peak during the run.
With the PC off, the phone's fallback costs only the fingerprint (no model, no network).

## 6. The strength labels CatMap uses (calibrated, per species)

| Label | Rule | Cats | Dogs |
|---|---|---|---|
| **Strong possible match** | reached by only 0.1 % of different same-breed animals | ≥ 0.88 | ≥ 0.89 |
| **Possible match** | reached by 5 % of same-breed look-alikes | ≥ 0.79 | ≥ 0.66 |
| **Weak match** | reached by 1 % of different animals of other breeds | ≥ 0.68 | ≥ 0.27 |
| No reliable match | below | | |

Other animal types use the cat values. The colour fingerprint gives at most "possible" (≥ 0.90) or "weak" (≥ 0.83).
Place and time then move a label down one step each (sighting far beyond how far a lost pet goes, or before it went
missing), never rule it out; the owner's rejections move similar sightings down one step.

**The trade-off, stated honestly** (`docs/evaluation/dogfacenet-threshold-tradeoff.json`): with these strict labels,
the share of same-dog DogFaceNet pairs reaching "strong" falls from 91 % (face-tuned) to 5 %, and "possible" from
98 % to 33 %; "weak" keeps 99 %. CatMap chose this deliberately: a false "strong" gives an owner false hope, while a
true match labelled "weak" is still listed, and the owner decides. No percentage is ever shown, because these
scores are not calibrated probabilities.

## 7. Face recognition (PetFace) — not integrated

PetFace (Shinoda & Shiohara, ECCV 2024): ArcFace-style face embeddings, needs five-keypoint face alignment
(AnyFace). Its dataset, code and weights are **"for non-commercial research purpose only"**; the dataset needs a
form approved for institutional e-mail addresses. CatMap is a public community app, so it is not included. A
licence-compatible substitute was considered: pet-recognition-base itself scores well on aligned dog faces
(section 5.1), but CatMap has no licence-compatible pet *face detector*, and no data with both face and body
photos of the same cats, so a fusion experiment (Model D) could not be run. Per the brief, no fusion was enabled
without evidence.

## 8. What these results can't establish

* **Cat recall (same cat, different photos) is unmeasured.** The cat thresholds come from false matches only.
  How often the same street cat reaches "possible" or "strong" from two different phones and days is unknown until
  a labelled cat set exists. To measure it: 20–40 cats × 3–6 photos each, on different days, angles and light,
  including look-alikes, one folder per cat; run `evaluate.py --animal-type cat`, then set the labels from its
  tuning half (`matching.py`, `MatchEngine.kt`, `MatchEngine.ts`, regenerate `tools/matching/cases.json`).
* DogFaceNet is faces, aligned; real sightings are whole bodies at odd angles. Section 5.1 is an upper bound for
  real phone photos.
* Oxford-IIIT Pet are pedigree, studio-like photos; Dubai street cats are more varied, so the look-alike rates are
  pessimistic (safer).
* Location and time weights could not be fitted: no dataset has true positions. They are deliberately small
  (one step) and documented in `docs/MATCHING.md`.
* Real-world precision on CatMap's own data: none yet (no production use).

## 9. Mistakes

* `docs/evaluation/dogfacenet-lookalikes.png`: the 8 highest-scoring "different dogs" are visibly the same photo
  filed under two ids, i.e. **labelling errors in DogFaceNet**; they make our false-match rate slightly pessimistic.
* `docs/evaluation/dogfacenet-missed.png`: the lowest-scoring same-dog pairs (0.13–0.22): strong pose change, partial
  face, blur.
* `docs/evaluation/oxford-lookalike-cats.png`: plain black and plain tawny cats of one breed.

Image credits: DogFaceNet © 2018 Guillaume Mougeot (MIT). Oxford-IIIT Pet © Parkhi, Vedaldi, Zisserman, Jawahar
2012 (CC BY-SA 4.0); the montage is a derivative under the same licence.
