# Photo review v1: design for approval

Status: **approved (M1–M3 as recommended; Arabic deferred by the owner) and implemented locally on 26 Oct 2026**, see
HANDOFF.md. Gate G1 passed (369 and 370 out of 500 against 373); the real-Android-thumbnail part reached cosine
≥ 0.99 on 4 of 6 photos (lowest 0.979), so the app's figure is approximate. Not committed, not deployed. Written
26 Oct 2026 by Claude.
Inputs: Codex's research in `tools/matching_candidate/` (report `IMPROVEMENT_REPORT.md`) and its handoff
`CLAUDE_CAT_MATCHING_HANDOFF.md`.

## 1. In one paragraph

When someone reports a sighting, the PC compares their cover photo with the photos of up to 8 nearby cat profiles,
using Codex's new model, and the app shows those cats with 2–4 of their photos each, under **"Review these photos"**.
The person decides: "This is the cat", "Not this cat", or "None of these". The app shows no percentages, no
"likely" labels and never picks a cat by itself, because the model doesn't meet the reliability targets
(section 3). The phone sends only its own photo and the cat ids; **the PC fetches the cats' photos itself**. Both the
PC flag and the in-app flag are off by default, and when either is off the app works exactly as it does today.

## 2. Decisions for the owner

| # | Question | My recommendation |
|---|---|---|
| M1 | Which screen gets it first? | **New sighting** ("Is this one of these cats?"). Cat profiles already hold several photos per cat, which is what the model needs. Leave the new-post "same street animal" check and lost-pet matching as they are. |
| M2 | Who sees it in v1? | **Admins and moderators only**, through a switch in Settings, until we have real Dubai results (section 9). |
| M3 | Gate G1 pass line (section 8) | Using the app's stored thumbnails must still get **at least 363/500** right first (the research got 373/500). |
| M4 | Arabic for about 8 new strings | I write them, marked `AR-REVIEW` for a native check (as with D2). |

## 3. What the research shows (checked by Claude)

* **Better ranking:** the right cat comes first 373 times out of 500, against 288/500 for the baseline. Most of the
  gain comes from using 2–4 photos per cat, so cats with one photo can't take part.
* **Not good enough to suggest a cat by itself:** at a fixed threshold, 96.7% of accepted answers were right
  (lower bound 90.8%), against a target of 99%. 0.8% of cats that weren't in the gallery were wrongly accepted,
  against a target of 0.5%. All automatic-suggestion rules stay **null**.
* **Tested on other people's cat photos, not Dubai street photos.** These were mostly close-ups of pets. Street
  photos taken from further away may do worse. Only real use can tell (section 9).
* **Claude re-checked:**
  * The model files match their manifest hashes.
  * 22/22 of Codex's tests pass.
  * The example request (33 photos) ranks in 1.2 s on the GPU.
  * The live worker files match `tools/worker`.
* **Corrections to Codex's handoff:**
  1. `cat-server/recognition.py` (a one-line `bool()` change, no backup) and `cat-server/tests/run.py` (research
     tests added) differ from the git mirror. Before the worker work starts, I'll copy `recognition.py` into the
     mirror and keep the research tests out of the mirror's `run.py`.
  2. Codex suggested putting all 33 photos in the job document. This design has the PC fetch them instead
     (section 4). That keeps the document small and stops a phone from sending made-up "reference" photos.
  3. The handoff covers only Android. The web app gets the same feature in the same round (house rule).
  4. The research runtime rejects the **whole request** if any two photos are near-copies. In the app, a profile
     often holds the same photo twice (merges, re-uploads), so copies are dropped from that one cat instead
     (section 5).
  5. "Cropped versus whole photo": the research used whole photos (cat plus background, at most 320 px).
     The app's stored thumbnails are also whole photos, so v1 uses them and **does not crop**. Gate G1 measures
     the small difference that remains (360 px quality 72 thumbnails, scaled to 320).

## 4. Transport: one small job, the PC fetches the photos

The phone writes `aiJobs/{uid}` as it does today:

```
kind: 'review'
query: <the cover photo's thumbnail bytes: the same 360 px JPEG the app uploads as {id}_t>
ids: [catId, ...]          // 1 to 8 nearby cat profiles, nearest first
images: [], photos: [], labels: [catName, ...]
uid, status: 'pending', requestId, createdAt   // unchanged
```

* **Size:** app-style thumbnails measured at a median of 12 KB (p95 19 KB, 150 dataset photos), so a job is under
  25 KB. The existing 150 KB `query` cap stays.
* **The PC, for each id:**
  1. Reads `cats/{id}` (it may already: `member()` includes the worker).
  2. Skips cats that are hidden, merged or deleted.
  3. Picks photos: the cover first, then the newest, up to 4.
  4. Reads `images/{photoId}_t` by id.

  So the PC only ever uses real community photos of that cat. Never a client path, a client vector or a chat photo.
* **Reads per request:**
  * Cold (nothing cached): 8 cat reads + up to 32 thumbnail reads (about 400 KB).
  * Warm: 8 cat reads, because vectors are cached by photo id (section 5).

  The 50,000 reads/day Spark limit isn't at risk while M2 limits this to moderators.
* **Answer:** a new field `review` (old apps never read it):

```
review: {
  protocol: 'review-v1',
  space: 'native-train-whitened-raw/9719870ec99a37304d634212',
  ranked: [ { id, score, photoIds: [up to 4 ids used] } ],      // best first
  unavailable: [ { id, reason } ]   // 'hidden' | 'missing' | 'too_few_photos' | 'same_photo_as_query' | 'photo_unreadable'
}
```

* **`score` stays inside the protocol:** the app uses it for order only and never shows it as a number.
* **Version handling:**
  * The PC adds `capabilities: ['review-v1']` to `config/aiWorker`, and only when started with the review flag
    and the model passes its hash check.
  * The app sends `review` only when that capability is listed.
  * An older PC never sees a review job. If one gets one anyway, it answers with `results: []` and no `review`
    field, and the app treats that as "PC review unavailable", **never as "no match"**.
  * Older apps never send it.

## 5. PC worker adapter

* **New file `cat-server/review.py`, mirrored as `tools/worker/review.py`** (house protocol: `.bak` before every edit
  of an existing file, SHA-256 parity afterwards).
* **Loading and checking the model:**
  * The model folder comes from `CATMAP_REVIEW_BUNDLE`, by default
    `..\cat\tools\matching_candidate\artifacts\review_candidate_v1`.
  * At start-up the adapter checks every file against the folder's `manifest.json`. The same three hashes also go
    into `tools/worker/model-manifest.json`.
  * If anything fails, the PC logs it and doesn't advertise the capability. Everything else keeps running.
* **Uses the research code unchanged:** the adapter imports the frozen `review_candidate.py` from that folder
  (`CandidateEncoder`, `PhotoEmbedding`, `ReviewGallery`). Its maths isn't copied. The frozen `encode()` reads
  files from disk, so the adapter adds two in-memory steps, each with its own exactness test:
  * **`prepare(bytes)`:** JPEG only → upright → RGB → LANCZOS to at most 320 px → JPEG quality 85. These are the
    exact steps the research used to make its 320 px photos (`cat-server/eval/prepare_native_metric_calibration.py`, now in `Desktop\catmap-archive-2026-10-26`).
    *Test:* run on the research's stored sources (up to 1280 px), the output must be **byte-identical** to the
    prepared files, whose SHA-256s are recorded in `source_records.json`. This already holds for 2 sample photos
    in the current environment (checked 26 Oct).
  * **`encode_prepared(bytes)`:** the same steps as the frozen `encode()`, from bytes instead of a path.
    *Test:* on the 33 example photos it must give the same vectors as `encode(paths)` (within 1e-6).
* **Per-cat handling instead of failing the whole request:**
  * Duplicates and near-copies inside one cat are dropped.
  * A photo that is the same as the query is removed from that cat.
  * A cat left with fewer than 2 photos goes to `unavailable` with its reason.
  * Only the remaining cats are passed to `ReviewGallery`. If none remain, the answer is `ranked: []` with every
    reason listed.
* **Vector cache:**
  * In memory only: an LRU of 5,000 entries (about 15 MB), keyed by (model space, photo id).
  * Each entry is checked again before reuse: 768 floats, all finite, unit length.
  * Community photos can't be edited (rules allow create and delete only), so a photo id always means the same
    bytes. Deleted photos simply stop being listed by their cat.
  * Nothing is written to Firestore, so `postEmbeddings` and the existing 512-D vectors are never touched.
* **`server.py`:**
  * New `--review` flag (also `start.cmd --review`), off by default.
  * When the flag is on, it loads the model once at start-up and adds a `review` branch to `handle()`.
  * The branch uses the same request-id transaction as today, so a replaced job is never overwritten.
  * It also checks the request: 1–8 unique ids, a JPEG `query` of at most 150 KB, no images or photos. Anything
    else gets `status: 'error'` and nothing is cut off silently.
  * The PC window logs each review like the other jobs.
* **Unchanged:** compare, check, embed, recovery and the thresholds.

## 6. Firestore rules (small, all inline)

1. `aiJobs` create: add `'review'` to the allowed kinds, plus one inline condition for that kind only:
   `incoming().kind != 'review' || (incoming().images.size() == 0 && incoming().get('photos', []).size() == 0
   && incoming().ids.size() >= 1 && incoming().ids.size() <= 8 && incoming().query is bytes)`.
2. `aiJobs` worker update: add `'review'` to the result fields the worker may write.
3. `config/aiWorker`: allow `capabilities` (a list of at most 8 items).

**Tests (`tools/rulestest/test.mjs`):**

* *Allowed:*
  * a review job;
  * the worker writing `review`;
  * a heartbeat with `capabilities`.
* *Refused:*
  * a review job with images, with 9 ids, or without a query;
  * a member writing `review`;
  * a heartbeat with an unknown key.

Every existing case must still pass, including the old-client cases. No new `get()` calls, so the access-call
budget is unchanged. **The rules must be deployed before any app that sends a review job ships** (owner step).

## 7. Apps (Android is the reference, web follows in the same round)

* **`AiServer` (`AiServer.kt` / `AiServer.ts`):**
  * `review(query: thumbnail bytes, ids, labels): ReviewResult?` with a typed result
    (`ranked`, `unavailable`, `protocol`).
  * Null means "unavailable", never "different".
  * `health()` also reads `capabilities`.
  * A mutex around `run()` so a review can never replace a pending check job from the same phone.
  * Review timeout 20 s, because CPU-only PCs took about 8 s in the research.
* **New sighting (`NewSightingViewModel.kt` + screen, web `SightingScreens.tsx`):**
  * Review runs only when all three are true: the switch is on, the PC has `review-v1`, and a location is known.
  * The query is the first photo's thumbnail; the research used one query photo, and combining several was a
    failed experiment.
  * The candidates are the **8 nearest** cats with at least 2 photos, with no colour-score filter, so the colour
    match can't hide the right cat. This is also how the research's "nearest 8" test was built.
  * It runs once per (cover photo, location); adding or removing a later photo doesn't resend it.
  * While waiting, the current on-device suggestions show as usual. Review results replace them when they arrive.
* **UI, "Review these photos":**
  * One card per ranked cat: the name, its 2–4 photos (thumbnails, tap to enlarge) and the distance.
  * Buttons: "This is the cat" and "Not this cat", plus "None of these: it's a new cat" at the end.
  * No percentage and no strong/possible/weak label. A line under the header says: "Sorted by how alike the photos
    look. You decide."
  * Cats in `unavailable` aren't shown; a footnote says "N nearby cats have too few photos to compare."
  * Confirming and rejecting reuse today's `confirm()` / `reject()`, so the saved sighting is unchanged
    (`suggestedCatIds` = the ranked ids). The retrieval score is **not** stored.
* **Switch:** Settings → "Experimental photo review", shown only to admins and moderators (M2). It is stored on
  the device and off by default.
* **Strings:**
  * About 8 new keys in a new table `tools/strings/strings_k.py`; web-only text goes in `strings_web.py`.
  * Arabic marked `AR-REVIEW` (M4).
  * No emoji. Icons from `ic_cm_*`.
* **Out of scope for v1:** the post editor's same-animal card (still shows `similarity*100`%; untouched),
  lost↔sighting matches, the admin Recognition test.

## 8. Gates and tests

**Before any app code: G1, does the app's input match the research input?**

1. Build a **new** output folder: `tools/matching_candidate/artifacts/app_thumbnail_route_v1/`. Nothing is refitted
   and no completed folder is overwritten.
2. For the same 500 development identities and the same queries and references, produce each photo the way the
   app would: start from the research's stored source (up to 1280 px), make Android's thumbnail from it (360 px,
   quality 72), then run the adapter's `prepare` and `encode_prepared`.
3. Score rank-1. **Pass: ≥ 363/500** (M3), with the same counts of unavailable cases. If it fails, stop and report;
   don't tune anything.
4. Also check the exact bytes of real Android and web thumbnails of 10 photos made on the emulators against the
   simulation: cosine ≥ 0.99 per photo.

**Checks (exact counts reported, nothing marked passed unless it ran):**

* Worker:
  * the two exactness tests in section 5 (`prepare` byte-identical, `encode_prepared` against the frozen
    `encode`);
  * per-cat unavailable reasons (one photo, duplicates, same photo as the query, unreadable, hidden, merged);
  * request validation (9 ids, missing query, a PNG query);
  * the cache refusing wrong-space or broken vectors;
  * the stale-request transaction;
  * with the flag off, no capability is advertised and nothing loads;
  * the portable suite still passes;
  * a new emulator e2e `test_review_synthetic.py` (cats + thumbnails in the emulator → review job → answer).
* Rules: the full suite plus the new cases above.
* Android:
  * unit tests for parsing `ReviewResult`, the mutex and the candidate choice;
  * Roborazzi for the new card, recording new baselines only for the new screen;
  * lint and build.
* Web: unit tests, lint, typecheck, strings check, build, and a new e2e spec against the isolated emulators.
* **Whole path on the emulators:**
  * the worker with `--emulator --review` and a staging Android build on the local emulators;
  * measure cold and warm time from tap to answer, and the job document's size;
  * then turn the PC flag off and confirm the app falls back to today's suggestions.

## 9. Rollback, privacy, limits

* **Rollback:** start the PC without `--review`. The capability disappears at the next heartbeat (right away on a
  restart) and every app is back to today's behaviour. Nothing needs migrating or deleting, because the only new
  data is the short-lived job field.
* **Privacy:**
  * The PC reads only community photos listed on cat profiles.
  * The vector cache lives only in memory and is cleared when the PC restarts.
  * The phone's query photo is deleted with the job, as today.
* **Limits to state plainly:**
  * Not validated on Dubai street photos.
  * Cats with one photo can't be reviewed.
  * The order is a retrieval ranking, not an identification.
* **Before it could ever suggest a cat by itself:** a separate project, with owner-confirmed photos taken on
  different days plus look-alike cats, gathered with exactly this input route, and the targets in section 3 met on
  them.

## 10. Order of work after approval

Step 0, the mirror fix in section 3, goes first. Then each step is one local commit:

1. G1.
2. Worker adapter and tests.
3. Rules and tests.
4. Android.
5. Web.
6. Whole-path emulator run.
7. HANDOFF section and `docs/MATCHING.md` update.

**Owner steps** (I'll write them in HANDOFF; I don't run them):

1. Deploy the rules.
2. Start the PC with `start.cmd --review`.
3. Turn the switch on for moderators.
4. Release.
