# CatMap AI Server (same-animal check)

A desktop app for the owner's PC. It runs **pet-recognition-base** (open-noodle, Apache-2.0; DINOv2-base backbone +
512-d projection, ONNX), on the GPU through DirectML when available.

When someone posts a **street animal**, the app finds open street-animal posts of the same kind within 1 km. It
crops the largest animal in each photo and asks this server how alike they are. If the server says "same" or
"maybe", the app asks the user. Confirming adds the photo to the existing post as a sighting, so there's no second
post of the same animal.

## How the phone reaches the PC (online, no cable, no open ports)

Firestore is the mailbox:

1. The phone writes a job to `aiJobs/{its uid}`. It contains small JPEG crops: the new animal plus up to 8 nearby
   posts.
2. This app checks for pending jobs every 2 s over outgoing HTTPS, embeds the crops, and writes the answer into the
   same document. The answer is tagged with the request it answers.
3. The phone listens to that document and shows the answer, usually within 2–5 s.
4. Every 30 s the app updates `config/aiWorker` (a heartbeat). When the PC is off, phones skip the check at once and
   use their own on-device matching.

Nothing connects *into* the PC. It works on any network, behind any router, with the phone on mobile data.

## Set up once

1. Run `node tools/demo/create-demo-accounts.mjs` (see `HANDOFF.md`).
   * Besides the demo accounts, it creates the **AI worker** account the PC signs in with.
   * The worker's password is random, never shown, and saved only in `tools/ai-server/worker.local.json`
     (git-ignored).
2. Deploy the rules: `npx.cmd -y firebase-tools@latest deploy --only firestore`. They add `aiJobs` and
   `config/aiWorker`.

## Start it

Double-click **`tools/ai-server/start.cmd`**.

* The first run installs the Python packages into `.venv` and downloads the model (about 350 MB) into `models/`.
  Both are git-ignored.
* The **CatMap AI Server** window opens. Whenever a photo arrives, it pops to the front and shows:
  * who sent it;
  * the new photo and the nearby candidates;
  * each candidate's verdict and score (green SAME, orange MAYBE, grey different);
  * an activity log.
* Closing the window marks the server offline.

Options:
* `start.cmd --emulator` uses the local Firebase emulators (testing).
* `start.cmd --cpu` skips the GPU.
* In the app, **Admin → AI server** shows whether the PC is online.

## Security

* The PC signs in as the AI worker, with custom claim `catmapAiWorker`, which only `firebase auth:import` can set.
  The rules let that account read jobs, write only the result fields of a job, and write the heartbeat. It can't
  touch posts, users or anything else.
* Jobs are per user (document id = uid), from verified accounts only:
  * at most one new job a second;
  * the query image is limited to 150 KB;
  * at most 16 images per job;
  * no result fields may be set by the phone.
* The server never decides anything. The user confirms in the app, and the app writes through the normal rules.
* Images stay in Firestore, like every other CatMap photo. Each job overwrites the previous one, so nothing piles up.
* Firestore cost: one read per poll while the app runs (about 1,800 an hour), within the free Spark quota of
  50,000 reads a day for a demo. Close the app when you don't need it.

## Thresholds

Defaults: `same` ≥ 0.75 and `maybe` ≥ 0.60 cosine similarity. These are provisional.

* The model's own clustering sweep suggests about 0.5.
* Uncropped photos of two different kittens scored 0.57 in testing.

**Admin → Recognition test** measures the model on your labelled photos while the server runs. Use it to pick the
values, then start the server with them:

```bat
set SAME_THRESHOLD=0.7
set MAYBE_THRESHOLD=0.55
tools\ai-server\start.cmd
```
