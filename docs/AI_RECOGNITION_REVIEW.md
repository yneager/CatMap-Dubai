# CatMap Dubai – AI recognition review

> **Update, 13 Oct 2026:** the owner decided to use the PC (proposal B) for the demo, scoped to **street-animal
> posts**. It runs pet-recognition-base on the RX 9070 XT, reached over **USB only** (`adb reverse`). Details are
> in `tools/ai-server/README.md`. The concerns below still apply to running it without a cable: availability,
> exposure, and calibration. The app falls back to on-device matching whenever the PC is unavailable.

Answer to `prompt.txt` (13 Oct 2026, Claude). The owner then asked for the review to be implemented. This
file records the assessment, what was built from it, and the decisions still needing the owner.
"Unverified" marks anything not checked first-hand.

## Summary

| Proposal | Decision | Reason (one line) | Effort |
|---|---|---|---|
| A. Stronger individual recognition | **Approve with changes**: on-device learned model, gated on a measured win | Handcrafted colour/texture descriptors can't separate look-alike cats; a re-ID embedding is the one change that can | Medium |
| B. Local GPU service on the PC | **Reject** | PC is not 24/7, exposes a home machine, needs Admin credentials; on-device gives the same model without it | Large |
| C. Vector search | **Defer** | 800 profiles compare in milliseconds on the phone; an index pays off only at tens of thousands | – |
| D. Better match suggestions | **Approve with changes** (done) | Percent-only scores mislead; look-alikes need wording; rejections weren't recorded | Small |
| E. Location-assisted matching | **Approve with changes** (done) | Location ignored blur/GPS error and time; weighting itself (15%) is sensible | Small |
| F. Availability when PC is off | **Not needed** | Follows from rejecting B; on-device works offline | – |
| G. Several cats in one photo | **Approve with changes** (done) | Segmenter merged all subjects into one descriptor; picking the largest fixes it without UI | Small |
| H. Security of a PC service | **Not needed** | Follows from rejecting B; no new attack surface added | – |
| I. Accuracy evaluation | **Approve** (done) | Capstone claims need measured numbers from this app, not model cards | Medium |

## A. Audit of the current implementation

All "existing features" in prompt section 3 were confirmed in the local code. The relevant ones:

* **Detection, segmentation, coat colour and pattern:** `ai/CatAnalyzer.kt`. ML Kit image labelling; ML Kit subject segmentation with a centre-weighted fallback; colour-family shares; edge and stripe texture; breed *hints* (trait notes, not breed classification).
* **Descriptor and similarity:** `CatAnalyzer.describe`, 125 values: a 54-bin HSV histogram, 4×4 colour layout, orientation histogram, edge density and colour shares. `Similarity.score` gives 0.65 visual + 0.20 coat/pattern agreement + 0.15 proximity (0.77/0.23 without location).
* **Search:** `NewSightingViewModel.findMatches` compares against `CatRepository.allCatsOnce()` (latest 800 cats). Each cat holds one running-average descriptor (`CatRepository.blendFeatures`). Suggestions need ≥ 0.45; the top 6 are shown.
* **Confirmation:** "Same cat" / "Not this cat" in `NewSightingScreen.kt`. The AI never merges; merges go through `suggestMerge` → moderator `approveMerge`/`rejectMerge`.
* **Posts (any animal):** `ai/PostAppearance.kt` is a separate 27-value centre-weighted colour fingerprint with no segmentation. `PostAppearance.matches` ranks lost↔found posts by 0.6 photo + 0.25 distance + 0.15 time, within 10 km.
* **Images:** Firestore JPEG + thumbnail documents, EXIF stripped by re-encoding (`util/ImageUtils.kt`), disk cache (`data/ImageDiskCache.kt`). Nothing in this review changes them.

Weaknesses found in the code (not in the prompt):

1. **Two subjects become one cat.** The foreground mask covers every subject, so a photo with two cats produced a blended descriptor.
2. **Location ignored its own error.** Public cat positions are blurred by up to ~400 m (`LocationPrivacy.fuzz`) and GPS adds accuracy error, but distance was used raw with a fixed 1.5 km decay.
3. **The percentage overstates certainty.** Histogram intersection gives different cats high scores. A first run of the new Recognition test on 3 cats (synthetic variants of the demo photos, so indicative only): different cats averaged **0.60**, against the 0.45 floor. An unknown cat got a suggestion **100%** of the time, and 78% of those were labelled "possible".
4. **Rejections were thrown away.** "Not this cat" lived only in screen state, so no evaluation data accumulated.
5. **Averaging blurs.** One mean descriptor per cat mixes poses and lighting. Whether per-photo comparison helps is now measured (A/B in the Recognition test) rather than assumed.

Not verifiable: the real-world accuracy of the current matcher, because no labelled Dubai cat dataset exists yet (see I).

## B. Proposal decisions

**A – Approve with changes.** The handcrafted descriptor can say "orange tabby" but not "*this* orange tabby". A re-identification embedding is designed for exactly that gap.

* **Model.** `open-noodle/pet-recognition-small` (Hugging Face) is a frozen DINOv2-small backbone (~22M parameters) plus a 512-dimension projection, as ONNX, Apache-2.0. It was trained on Dogs-World (CC0) and Cat Individual Images (CC BY). The card reports cats top-1 0.913 and EER 0.065 on held-out identities; the base model reports 0.916 with 86M parameters.
  * Those are the *authors'* numbers on *their* test split, **not CatMap's accuracy**.
  * The small model loses almost nothing on cats, so it's the right size for a phone.
* **Run it on the phone (option 4), not on the PC.** Use ONNX Runtime Mobile on the segmentation crop from `CatAnalyzer`.
* **Complement the existing descriptor, don't replace it.** Keep coat colour, pattern and breed hints (they drive the UI and filters). Blend in the embedding cosine as the main visual term once the Recognition test shows it wins.
* **Unverified:** ONNX file size, on-device latency on a mid-range phone, and the APK size cost (ONNX Runtime adds roughly 10–15 MB per ABI; check before committing).

**B – Reject.** Strictly worse than running the same model on the phone:

* It's only up while the PC is on.
* It needs a publicly reachable endpoint on a home machine (tunnel, TLS, token verification, rate limiting).
* It needs Firebase Admin credentials on that PC.
* It adds latency: an image round-trip plus a Firestore read, which is unverified but likely 1–3 s on mobile data.
* It needs a pending-work queue (F) and a security review (H).

The RX 9070 XT is valuable for *offline* work instead: running the evaluation, or fine-tuning/exporting a model. Windows ROCm support for RDNA 4 is unverified; ONNX Runtime DirectML is the likely route, and CPU is enough for evaluation.

**C – Defer.** Brute-force cosine over 800 × 512 floats is about 0.4M multiply-adds, under 5 ms on a phone. A vector index matters around 50k+ profiles. If needed later:

* Firestore `findNearest` exists (KNN, ≤ 2048 dimensions, index entries billed as reads, 1 read per 100 entries). No page found says it is unavailable on Spark, but that isn't confirmed. **Unverified**; test on the project before relying on it.
* Store a cat's embedding beside its existing descriptor, as one average plus up to 5 per-photo vectors. Version it with an `embeddingModel` field, so a model change re-embeds lazily instead of mixing incompatible vectors.
* Merges already move sightings and photos, so embeddings follow if recomputed on merge.

**D – Approve with changes (implemented):**

* Each suggestion is worded **Strong / Possible / Weak**. "Strong" needs ≥ 0.80 *and* a 0.05 lead over the runner-up; the percentage stays as small print.
* Non-strong matches carry a prompt to compare ear tips, face and tail before confirming.
* The sighting now stores `suggestedCatIds` and `rejectedCatIds` (≤ 6 each, rules-enforced), giving real feedback data for later evaluation.
* The confirmation screen, profiles and moderator merges are unchanged.
* Merge safety: merges are moderator-only and logged. They are not automatically reversible, which is acceptable because the AI never triggers one.

**E – Approve with changes (implemented):**

* `Similarity.proximity` subtracts position uncertainty (GPS accuracy + the "high" privacy radius) before decaying.
* The decay widens with time since the cat was last seen (1.5 km → at most 4 km), so a cat that moved neighbourhood isn't penalised as hard.
* Location stays 15% of the score. A test checks that an identical-looking cat 25 km away still scores above 0.8.
* Privacy is unchanged: only public, already-blurred positions are used.

**F, H – Not needed** once B is rejected. Nothing becomes asynchronous, and no service or credential is added.

**G – Approve with changes (implemented):**

* ML Kit's multi-subject mode is enabled; when it finds more than one subject, only the largest is described.
* The match step shows a hint to crop the photo.
* No detector (YOLO) or picker UI. Those are worth adding only if the evaluation shows multi-animal photos are common.

**I – Approve (implemented as Admin > Recognition test):**

* The admin picks a folder on the phone with one sub-folder per cat. Every photo goes through the app's own analyser.
* `ai/RecognitionEvaluation.kt` reports:
  * **known cat** (leave-one-out): top-1 and top-3, for the averaged descriptor and for best-single-photo;
  * **unseen cat**: how often a wrong suggestion, a "possible" or a "strong" appears;
  * same-cat vs different-cat score means, the score that only 5% of different-cat pairs reach, and analysis time.
* The full report, with per-photo CSV, is shareable.
* Location is excluded on purpose, because dataset photos have no trustworthy positions.

## C. Architecture comparison

| | 1. Keep on-device handcrafted | 2. PC model only | 3. On-device + optional PC | 4. Learned model on the phone |
|---|---|---|---|---|
| Accuracy on look-alikes | Low | High (when up) | High when PC up, low otherwise | High (per model card; to be measured) |
| Reliability | High | Low (PC off) | Medium, two code paths | High |
| Complexity | None | High | Highest | Medium (one model file, one runtime) |
| Security | Unchanged | New public endpoint and Admin key | Same as 2 | Unchanged |
| Offline | Works | Fails | Degrades | Works |
| Cost | Free | Free, but the PC runs | Free | Free (APK bigger) |
| Performance | ~0.8 s/photo measured on emulator | + network round-trip | Mixed | + model inference (unverified, likely 0.2–1 s) |
| Capstone value | Low | Medium | Medium | **High**: measurable before/after, no infrastructure |

**Recommendation: option 4,** introduced behind the Recognition test. Run the same dataset with the current descriptor and with the embedding, and switch the live score only if top-1 rises and unseen-cat "strong" matches stay near 0%. Later, the same embedding can also replace the 27-value `PostAppearance` fingerprint, so lost/found posts for dogs and cats share one approach. The model was trained on both species, but its dog numbers are weaker (top-1 0.54).

## D. Approved changes: details

Implemented in this commit:

| Change | Files |
|---|---|
| Largest-subject segmentation | `ai/CatAnalyzer.kt` (`subjectCount`) |
| Uncertainty- and time-aware proximity; `Strength` bands | `ai/CatAnalyzer.kt` (`Similarity`) |
| Strength wording, look-alike prompt, several-subjects hint | `ui/sighting/NewSightingScreen.kt`, `NewSightingViewModel.kt` |
| Suggested/rejected ids on sightings | `data/CatRepository.kt`, `data/model/Models.kt`, `firestore.rules` (size ≤ 6) |
| Recognition test | `ai/RecognitionEvaluation.kt`, `ui/admin/RecognitionTestScreen.kt`, `util/ImageUtils.analysisBitmap`, admin dashboard tile |
| Tests | `RecognitionEvaluationTest` (6 tests), rules suite +2 sighting tests |

No new dependency, no new service, no change to images, IDs, merges or permissions.

Proposed next (option 4, needs approval):

* Add `com.microsoft.onnxruntime:onnxruntime-android` and the ONNX file as an asset, or a first-use download.
* Add `ai/PetEmbedder.kt`: crop to the largest-subject box, resize to 224, apply ImageNet normalisation, and return 512 floats.
* Add `embedding` + `embeddingModel` on sightings and cats.
* Change `Similarity.score` to blend in the embedding cosine.
* Run `RecognitionEvaluation` with both scorers side by side.
* Integration risks: APK size, cold-start load time (load lazily on the sighting screen), and old cats without embeddings (fall back to the current descriptor until a new sighting adds one).

## E. Priorities

Essential (done):
1. **I** – evaluation. Without it no accuracy claim is defensible.
2. **D** – honest wording and feedback capture.
3. **G** and **E** – small correctness fixes.

Optional, in order:
1. **A** – the on-device embedding. Highest benefit and academic value; medium effort and risk; needs approval.
2. Use the same embedding for lost/found posts.
3. Per-photo embeddings per cat. Decide with the Recognition test's best-photo column.

## F. Final recommendation

* **Already implemented (no further work):** everything in prompt section 3, now confirmed in code.
* **Implemented for the capstone in this change:** D, E, G, I.
* **Recommended next:** A, as option 4 (`pet-recognition-small` on the phone via ONNX Runtime), adopted only on a measured improvement.
* **Rejected or deferred:** B (PC service), C (vector index), F and H (not needed without B).
* **Decisions requiring the owner's approval:**
  1. Adding ONNX Runtime and a model file of roughly 20–90 MB (APK size; bundled or downloaded on first use).
  2. Collecting a labelled dataset. Realistic: 20–40 local cats × 3–6 photos each, taken on different days, angles and lighting, including look-alike pairs. Optionally add the public Cat Individual Images set (CC BY), but report it separately: it is the model's own training source and would flatter it.
  3. Whether to use the PC for offline evaluation or fine-tuning (no runtime role).

Sources checked: the Hugging Face model cards for `open-noodle/pet-recognition-base` and `-small`, and the Firebase docs on Firestore vector search and pricing.
