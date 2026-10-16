# CatMap Dubai – handoff

Read this first in a new chat. It covers the current state, how to build and test, the decisions already made,
and what's left. **Last updated: 16 Oct 2026 (lost-to-reunited workflow with AI matching on Android, web and the PC worker; committed locally as `c7918a6`, not pushed, nothing deployed. **Deploy the rules before releasing** (see below). Last release v0.0.8 at `2b07278`; next is v0.0.9).**

## 16 October 2026 - lost → sighting → AI match → chat → reunion (Claude)

The owner gave a prompt (`Desktop\prompt5.txt`) for advanced animal recognition and the complete lost-to-reunited
workflow. Full design: **`docs/MATCHING.md`**. Measured AI results: **`docs/AI_EVALUATION.md`**.

**What it does**
* Every new lost report and sighting (found / Animal Spotted / street) is matched **in both directions** (a sighting
  posted first is found when the report comes). Results are `matches/{lostId}__{sightingId}` documents: one per
  pair, seen only by the two people, labelled Strong possible / Possible / Weak (never a percentage), with plain
  reasons (photo similarity, distance, time).
* The owner gets "Possible sighting of Simba!", the reporter "Your sighting may help find Simba" (fixed ids, once
  per pair, possible/strong only, top 3; existing notification settings and quiet hours apply).
* Review screen (`match/{id}`): both photos side by side (tap to enlarge), details table, why, features to check.
  Owner: might be mine / not mine / request more info / message. Reporter: same animal / different / message.
  Rejected pairs are remembered and never suggested again. Nothing is ever merged or confirmed automatically.
* Chat: the existing one-to-one chat is reused, with one "Possible match" card (both photos) per match.
* "I posted a sighting of this animal" on someone's lost report links your own sighting.
* **Mark as reunited** asks how (CatMap sighting → which one / came home on its own / another way), one
  transaction, once; reporter thanked; Reunited banner shows "reported by the owner" / "confirmed by the person who
  saw them" / "checked by a moderator" (new moderator button). Confirmation screen with privacy-safe "Share the good
  news". Recently reunited lost pets show 7 days on the map as a green marker with a tick. Report history section.
  Admin analytics: match counts by strength, owners' answers, reunions credited/confirmed/verified.
* Reopening a report clears `resolution`; the old Android "possible match" broadcast to every lost owner within
  5 km was replaced by this.

**AI (PC worker, `Desktop\cat-server`, not in git)**
* `recovery.py` (new): live listener on recent posts; DETR ResNet-50 (`Xenova/detr-resnet-50`, Apache-2.0, ~170 MB,
  downloaded by `start.cmd`) finds and crops the animal; pet-recognition-base embeds **each photo** (stored in
  `postEmbeddings/{postId}`, versioned); scores with `matching.py`; writes matches and alerts; startup catch-up.
  `recognition.py` (model + detector, shared with the evaluation), `fingerprint.py` (the phone's fingerprint, exact).
* Thresholds are now measured, per species (cats strong ≥ 0.88, possible ≥ 0.79, weak ≥ 0.68; dogs 0.89/0.66/0.27).
  The "already on the map?" street check uses the cat strong/possible values (was 0.75/0.60).
* Key findings: AI top-1 0.95 vs colour fingerprint 0.18 on DogFaceNet (unseen dogs); several photos per animal
  help; colour fusion doesn't (weight 0); **same-breed cats look alike to the model** (mean 0.53), so cat labels are
  strict and **cat recall is unmeasured** (no labelled cat set). PetFace not used (non-commercial licence).
* `--no-matching` turns the background matcher off. Window logs each match.

**PC offline:** the publishing phone (and the owner's phone when the report is opened) pairs posts by colour
fingerprint with the same rules (`ai/MatchEngine.kt`, `web/src/ai/MatchEngine.ts`), never "strong"; the PC
re-scores them when it's back.

**Files**
* Android: `ai/MatchEngine.kt`, `data/MatchRepository.kt`, `ui/matches/MatchUi.kt`, `ui/matches/MatchReviewScreen.kt`
  (new); `data/model/Community.kt` (PostMatch, MatchTier, Resolution, Post.resolution/resolvedMatchId,
  Message.matchId/matchPhotoId), `PostRepository.kt`, `ChatRepository.kt`, `NotificationRepository.kt`,
  `AdminRepository.kt`, `AdminAnalytics.kt`, `notifications/Notifications.kt`, `ui/posts/PostDetailScreen.kt`,
  `PostEditorViewModel.kt` (afterPublish), `ui/messages/MessagesScreens.kt`, `ui/map/MapScreen.kt`, `MapMarkers.kt`,
  `ui/explore/Filtering.kt`, `ui/admin/AnalyticsPanel.kt`, navigation, `AppContainer.kt`.
* Web: `src/ai/MatchEngine.ts`, `src/data/MatchRepository.ts`, `src/ui/matches/MatchScreens.tsx` (new); detail,
  editor, chat, notifications, map, filter, theme CSS, regenerated models; `e2e/reunion.spec.ts` (new),
  `e2e/flows.spec.ts` (new reunite dialog).
* Shared: `firestore.rules` (matches, postEmbeddings, worker match alerts, post resolution fields),
  `tools/rulestest/test.mjs` (+48 checks), `tools/strings/strings_j.py` (106 strings, **Arabic written by Claude,
  marked AR-REVIEW**), `tools/matching/cases.json`, `docs/MATCHING.md`, `docs/AI_EVALUATION.md`,
  `docs/evaluation/` (result JSONs, mistake images with attribution).
* PC: `recovery.py`, `recognition.py`, `matching.py`, `fingerprint.py`, `server.py`, `start.cmd`, `eval/`
  (`evaluate.py`, `lookalikes.py`, `prepare_dogfacenet.py`, `requirements-eval.txt`), `tests/`.

**Verified (all local)**
* Rules suite **325/325** (was 277). Android `MatchEngineTest` 4/4 (all 201 shared cases equal to Python).
* PC end-to-end on the Firestore emulator with real photos (`tests/test_recovery_e2e.py`) **20/20**: match + alerts,
  look-alike never strong, rejection sticks, sighting-first order, reunited report not matched, restart without
  duplicates, photo-less old posts. Python unit tests (matching, fingerprint exact to Android).
* Web: typecheck, lint, **42/42** unit tests, strings check; e2e **36 passed, 8 skipped (by design)** in Chromium +
  WebKit, including the new two-account `reunion.spec.ts` (review, reject, chat card reused, reply, reporter
  confirms, reunion credited once, banner).
* Android: `testDebugUnitTest` **78/78**, `verifyRoborazziDebug` passed, `lintDebug` 0 errors (one new warning,
  LogNotTimber, same category as before), `assembleDebug` OK. Web `npm run build` OK.
* Worker e2e scores: the same dog in two photos 0.95 → strong; the look-alike dog 0.51 → weak, no alert.

**Not done / limits**
* Not run on a real phone or against production; Android UI not driven on the emulator this time (web e2e covers
  the same flow and documents).
* Cat recall unmeasured (`docs/AI_EVALUATION.md` §8 says how to measure with ~30 local cats).
* Community `cats` profiles (v1 sightings) are not part of the matching; only posts.
* Arabic for the new strings needs a native check (AR-REVIEW).

**Owner's next steps**
1. **Deploy the rules first** (`cat-server\setup.cmd`, or `npx.cmd -y firebase-tools@latest deploy --only firestore`):
   new Android/web builds write `resolution`/`resolvedMatchId` on every new post, which the current production rules
   refuse.
2. Restart `cat-server\start.cmd` once (downloads the detector, ~170 MB).
3. Review, then OK the commit/push and the v0.0.9 release; `web\publish.cmd` for the web app.
4. Collect a small labelled cat set and re-run `eval\evaluate.py` to set the cat thresholds from real recall.


## 14 October 2026 - web map fix and Codex brief, round 2 (Claude)

* **Map blank on phones (live site).** Cause:
  * MapLibre's CSS is a lazy chunk (`CommunityMap-*.css`) that loads after `index-*.css` in production builds.
  * Its `.maplibregl-map { position: relative }` beat our `.map-canvas { position: absolute; inset: 0 }`, so the map
    was 0 px tall.
  * Dev mode orders the CSS differently, which is why e2e missed it.

  Fix: `.community-map > .map-canvas` in `web/src/ui/theme/theme.css`. Verified in a `--mode emulators` production
  build at phone size: the map, tiles and markers draw. **Not deployed:** the owner runs `web\publish.cmd`.
* **`CLAUDE_TO_CODEX.txt` now holds the round-2 brief.** Round 1 is at `git show 801d9b9:CLAUDE_TO_CODEX.txt`. It
  covers:
  * the 3 `WEB_TODO.txt` items;
  * an install gate (the web app runs only when installed to the home screen);
  * removing website behaviour (zoom, pull-to-refresh, selection, callouts, swipe-back);
  * a production-build map regression test.
* **Codex round 2 done; Claude reviewed it** (report: `work done.txt`). New parts:
  * `web/src/platform/`: the install gate (`InstallRoot`, `install.ts`), app feel (`appFeel.ts`/`.css`) and updates;
  * `PostSteps.ts`, `usePhotoCheck.ts`, `ai/PhotoCheck.ts`;
  * `AiServer.check`;
  * splash images;
  * e2e tests: `preview-map`, `install-gate`, `photo-check` (with a fake worker).

  Claude re-ran:
  * lint, typecheck, unit tests (39/39), the string checks and the production build;
  * the production guard: `VITE_INSTALL_GATE=off` is refused;
  * the gate in a production build at phone size: install screen only, Firebase not loaded;
  * the new e2e specs (12/12, Chromium and WebKit).

  `WEB_TODO.txt` is empty again. Minor, not fixed: on the Photos step a street post can show both "checking photo"
  and "checking same animal" at once. Not verified: a real iPhone. **Uncommitted and not deployed.**

## 14 October 2026 - photo check, location-first posting, "Animal Spotted" (Claude)

The owner asked for four things:
* Street animal renamed "Animal Spotted" and moved first, in the + menu only.
* Location first, then photo, so a duplicate street animal is caught before anyone types details.
* No Contact step for street animals.
* Posting stopped unless the photo shows an animal, naming what it shows instead, including selfies, using the AI
  on the PC.

**Android:**
* **Menu:** `PostLists.kt` puts STREET first with the string `chooser_street` ("Animal Spotted"). Pills, filters and
  details still say "Street animal".
* **Steps:**
  * New order: `PostStep { TYPE, PLACE, PHOTOS, DETAILS, CONTACT, REVIEW }`. The menu opens on PLACE.
  * `visibleSteps` drops CONTACT for street posts, and `stepAfter()` moves between visible steps. Progress shows
    "Step x of 5" for street posts.
  * Drafts save `stepName`; `restoredStep()` maps old drafts' positions through the 0.0.7 order (tested, and checked
    on the emulator with a real old draft).
* **Photo check (`PostEditorViewModel.checkPhotos`):**
  * Runs when photos are added, removed or re-ordered, and when entering Photos or later steps.
  * One `aiServer.check()` job per batch (`kind: "check"`, `photos` ≤ 6 at 320 px), plus the street-animal
    comparison in the same job: nearby street posts of any type, filtered by the resolved type afterwards.
  * **Results:** a `person` or `object` verdict blocks Next and Publish. The photo gets a red outline and a card
    reading "This looks like a coffee cup, not an animal. Remove it and add a photo of the animal to continue." An
    `animal` verdict fills the animal type unless it was picked (`pickAnimalType`).
  * The "Already on the map?" card is now on the Photos step, and Next waits for it. Publish still asks if a "same"
    match is unresolved.
* **PC offline:** `PhotoCheck.fromLabels` on ML Kit labels blocks only at ≥ 0.7 confidence with no animal label;
  anything unclear is allowed. A failed check never traps the person.
* **Rules:**
  * `aiJobs` accepts `kind: "check"` and the owner key `photos` (a list of at most 6); the worker may write
    `classification`.
  * 7 new tests; the suite is **277/277**.
  * **Production needs the rules deployed** (`cat-server\setup.cmd` or `deploy --only firestore`). Until then
    0.0.8's check jobs are refused, and phones quietly fall back to ML Kit.

**PC server (`Desktop\cat-server`):**
* **New model:** a `Classifier` running CLIP ViT-B/32 (`Xenova/clip-vit-base-patch32`, MIT, about 600 MB) zero-shot
  over grouped descriptions: animals by app type, people/selfie, and about 70 everyday objects. The text embeddings
  are cached in `models\clip-prompts-*.npy`.
* **Decision rule:** a photo is an animal when the animal share is ≥ 0.40 or the largest share (`ANIMAL_THRESHOLD`).
  The type comes from the best single animal description. Objects are named only when the best one is ≥ 0.15.
* **Answers:** `kind: "check"` jobs get `classification` + `results`. The window shows each photo as ANIMAL /
  PERSON / NOT AN ANIMAL. 0.0.7 phones' `compare` jobs still work.
* **Startup:** `start.cmd` now reinstalls packages whenever `requirements.txt` changes (new: `tokenizers==0.21.1`)
  and downloads any missing model on start.

**Verified:**
* **Sample photos:** 18 on the PC; every person and object was blocked and every animal allowed, about 0.4 s for 18 on
  the GPU.
* **Android checks:** unit tests 74/74, lint 0 errors, assembleDebug.
* **Emulator, with the PC server in emulator mode:**
  * the menu shows Animal Spotted first, and Animal Spotted starts at Where & when;
  * the coffee cup was blocked ("a coffee cup", Next disabled), and so was the portrait ("a selfie or a photo of a
    person");
  * the orange cat was accepted, and "Already on the map? Simba, Likely the same 99%" appeared on the Photos step.
    The PC answered in 96 ms with both models;
  * Review had no Contact; the published post is street/cat/in_app;
  * with the PC stopped and marked offline, ML Kit on the phone blocked the coffee photo ("a cup").

**Not done:** real-phone and production testing; Arabic for the 5 new strings (TODO-AR); the web app (listed in
`WEB_TODO.txt`).

## 14 October 2026 - CatMap Web local implementation (Codex)

`web/` now contains the React/strict TypeScript/Vite PWA, Firebase modular repositories, persistent Auth/Firestore/IndexedDB storage, shared feed, route guards, phone layout and self-hosted fonts. Map and admin are lazy chunks. Android models/enums/neighbourhoods are generated from Kotlin; strings and VectorDrawables share generators. Web-only Arabic text remains English with TODO-AR as requested.

Accounts, six-step posting, matching/drafts/photos, details/updates/posters, messages, adoption/sitting, profile/settings/export/deletion, community cats/sightings/rescue/stations/help and administration are implemented. The existing PC AI job protocol and descriptor math are ported; the real PC worker was not touched or exercised. Browser-specific behavior follows the brief: install hint, service-worker reload prompt, downloads/Web Share, inbox/toasts, and no push/background poll/ML Kit.

Local verification includes 22 passing browser scenarios (11 Chromium, 11 WebKit), 34 unit tests, unchanged quick/full security rules suites at 270/270 each, the Kotlin fingerprint fixture test and browser image/schema checks. Four fixtures stay within delta 0.03 and cosine 0.98: native RGBA analysis is exact; worst WebKit JPEG post delta 0.0247 and cosine 0.99986072. Production offline startup passed in Chromium and WebKit with the test-owned origin stopped. Lighthouse login audit measured performance 82, accessibility 100, best practices 100 and SEO 92; Lighthouse 13.5 has no PWA score. npm audit reported zero vulnerabilities. See `work done.txt` for the final browser/screenshot results and bundle measurement.

Final captures passed all three variants (214 PNGs); production offline checks passed both engines. The final initial JS/CSS gzip size is 341.02 KiB, excluding HTML/fonts/lazy routes. A default build without owner configuration showed setup instructions with zero external requests. Task-owned test services were stopped afterwards.

Final browser checks use the SDK's normal transport detection. Firebase clients are retained as a shared singleton across development module re-evaluation, and generated browser artifacts are excluded from reload/lint. Auth verification and start-chat callbacks ignore results after leaving their screen, avoiding delayed redirects; the browser suite covers delayed chat success and failure. A complete isolated demo suite avoids interfering with shared emulator services. The map worker is bundled explicitly, including its shared module. Safari poster font waits are bounded with a system-font fallback.

Visual comparisons and remaining differences are in `web/docs/parity.md`. This is a close CSS implementation, not a claim of pixel-identical Compose rendering: browser input/dialog/font behavior, some spacing and map tiles differ. No real iPhone or running Android emulator was available, so real install/share/camera/location and Android reading web records remain unverified. Optional video/satellite, production hosting, real PC worker and deployment CSP need owner testing.

Read `work done.txt` first, then the parity report and changed repositories, before publishing. `web/README.md` and SETUP contain owner-only configuration/deployment steps. No commits, pushes, tags, releases, Firebase console changes or deployments were made. Rules, indexes, Android build/source and release workflow are unchanged; the single added Android file is a JVM fixture test.

## 13 October 2026 - web app (iPhone) planned (Claude)

The owner wants an iPhone version as a web app. It lives in **`web/`** in this repo, because it shares the Firebase
project, `firestore.rules`, the string tables and these notes with the Android app. For now `web/` has only a README
and a `.gitignore`. Codex will build it from the brief in **`CLAUDE_TO_CODEX.txt`**, with Claude as architect and
reviewer.

The brief specifies:
* **Stack:** a Vite + React + TypeScript PWA with the Firebase JS SDK and MapLibre GL JS, hosted on Firebase Hosting.
* **Data:** documents identical to Android's, with no rules changes, plus a shape-parity test.
* **Shared assets:** strings generated from `tools/strings` (the Android XML must stay byte-identical) and icons
  converted from the VectorDrawables.
* **Matching:** the analyser maths ported, with a fingerprint parity test, and the AI check through `aiJobs` exactly
  as `AiServer.kt` does it.
* **Testing:** Playwright WebKit as the iPhone stand-in.

Codex reports in `work done.txt`; review it with the `review-codex-work` skill.

## 13 October 2026 - AI server moved out of the repo (Claude)

At the owner's request, the PC app now lives in its own folder, **`C:\Users\mohammad\Desktop\cat-server`**, outside
this repository; `tools/ai-server` is gone. That folder holds `start.cmd`, `server.py`, `requirements.txt`, its
README, the model (`models\`), its own Python (`.venv\`) and `worker.local.json`. It is not under version control.
* `create-demo-accounts.mjs` writes `worker.local.json` to `../cat-server` next to the repo, or to
  `CATMAP_SERVER_DIR`.
* The server reads `google-services.json` from its own folder first, then from `..\cat\app\`. Copying the file
  there was blocked as credential handling, so by default it uses the project's copy.
* Older sections below that mention `tools/ai-server` now mean the `cat-server` folder.

* **Demo-accounts script fix:** the script crashed on Windows on its second password prompt (a libuv assertion in
  Node 24). It now asks both questions on one readline. `--worker-only` creates just the AI worker account, with
  nothing to type.
* **One-click setup:** `Desktop\cat-server\setup.cmd` uploads the rules, creates the worker account, offers to create
  the demo accounts, then starts the server. The owner runs it, because Claude's deploy was blocked again as
  "Production Deploy".
* **Skill for new chats:** the `catmap` user skill (`C:\Users\mohammad\.claude\skills\catmap\SKILL.md`) summarises
  how everything works, the owner's rules, and how to build, test, release and hand off. Keep it in step with this
  file.

**Owner steps:** double-click `Desktop\cat-server\setup.cmd`.

## 13 October 2026 - live listener instead of polling (Claude)

The owner asked why the PC checked every 2 s. `tools/ai-server/server.py` now uses Google's Firestore client
(`google-cloud-firestore==2.20.1`) with `on_snapshot` on `aiJobs where status == pending`. Firestore pushes each
job over one streaming connection; an idle listener costs nothing.
* **Auth:** the worker's Firebase ID token is the bearer token, through `google.oauth2.credentials.Credentials`
  with a `refresh_handler` that uses the refresh token. The rules apply. The stream is reopened every 50 minutes.
  This was verified only against the emulator, which doesn't check tokens. The first production run will show any
  auth problem in the window's activity log.
* **Heartbeat:** `config/aiWorker` is written on open and close, and every 5 minutes as a crash safeguard (it was
  every 30 s). On the phone, `AiServer.ONLINE_WINDOW_MS` is 6.5 min and the answer timeout is 15 s.
  * **v0.0.6 phones** still use a 90 s window, so they'd treat the PC as offline between status writes. Phones need
    v0.0.7.
* **Verified on the emulator:**
  * status online on start;
  * the review card "Likely the same" about 3 s after reaching Review;
  * the window showing "Online, listening", SAME 94%, 121 ms;
  * closing the window wrote `online: false`.
* The model is the owner's `open-noodle/pet-recognition-base` (`recognition/model.onnx`), unchanged.

## 13 October 2026 - v0.0.6: AI check online through Firestore, desktop app (Claude)

The owner wanted the AI check to work online without a cable, with a window on the PC showing what's happening.

**Transport: Firestore as the mailbox. This replaces the USB/HTTP design in the section below.**
* The phone writes `aiJobs/{uid}`: crops as Blobs, ids, labels and a `requestId` (`data/AiServer.kt`).
* The PC app polls pending jobs over outgoing HTTPS, embeds the crops, and writes `results` / `embeddings` with
  `answeredRequestId`. The phone accepts only an answer to its own request.
* A heartbeat in `config/aiWorker` (every 30 s, online if seen within 90 s) lets phones skip the check at once when
  the PC is off.
* No inbound connection, tunnel or open port. The earlier tunnel attempt had been blocked as "External Ingress
  Tunnel". The loopback cleartext network config was removed again.
* **Worker identity:** `ai-worker@demo.catmap.test` (uid `catmap-ai-worker`, claim `catmapAiWorker`), created by
  `tools/demo/create-demo-accounts.mjs`. Its random password is saved only in `tools/ai-server/worker.local.json`
  (git-ignored) and kept on re-runs.
* **Rules:** `aiJobs` (owner-only; verified; 1 job/s; query ≤ 150 KB; ≤ 16 images; the worker may only set result
  fields) and `config/aiWorker` (the worker writes it, members read it). `config/ai` (the URL setting) is gone.
  17 new rules tests; suite **270/270**.

**Desktop app** (`tools/ai-server/server.py`, Tkinter; double-click `start.cmd`, since PowerShell scripts are
blocked on this PC):
* It pops to the front on each photo. It shows the requester, the new photo, the candidate thumbnails with names,
  the verdict and % (green/orange/grey), the timing and an activity log.
* Closing it writes `online: false`. A failed save isn't retried every poll.

**Verified on the emulator, with `adb reverse` removed:**
* the heartbeat appeared;
* a street post next to Simba got "Likely the same";
* the PC window showed "SAME 94%" in 67 ms on the GPU.
* The emulator rejected the REST `currentDocument.updateTime` precondition, which is why answers carry
  `answeredRequestId` instead.

**Owner steps:**
1. `npx.cmd -y firebase-tools@latest deploy --only firestore`
2. `node tools/demo/create-demo-accounts.mjs`
3. Double-click `tools/ai-server/start.cmd`
4. In the app, check Admin > AI server.

## 13 October 2026 - v0.0.5, + menu, street-animal AI check on the PC (Claude)

> Superseded for transport: the USB / `adb reverse` / HTTP design below was replaced by the Firestore mailbox in v0.0.6.

**Released v0.0.5** at `755c80e` (demo admin, Recognition test, List view, icons). CI and the release workflow passed.

**+ menu** (`ui/posts/PostLists.kt`) now has seven options:
* Lost, Found, Needs help, Street animal, Adoption, Report an emergency, and **Pet sitting**.
* Pet sitting opens a second step: **Offer pet sitting** (sitter profile) or **Request pet sitting** (sitter directory).
* Removed: Spotted, Other and Cat sighting (AI). Existing posts of those categories still display, and the
  sighting screen is still reachable from cat profiles.

**Street-animal same-animal check** (the owner chose to run pet-recognition-base on the PC):
* Flow (`PostEditorViewModel.checkSameAnimal`):
  1. It runs when a new Street-animal post reaches Review.
  2. It finds open street posts of the same animal type within 1 km.
  3. It crops the largest animal (`CatAnalyzer.cropSubject`).
  4. It sends the crops to `tools/ai-server` (`data/AiServer.kt`).
  5. A **Same animal** answer adds the photo to that post as a sighting update instead of creating a new post.
  6. **Publish** waits for the check. If the AI said "same", Publish asks "Add to that post / Post anyway".
* **Fallback:** if the server can't be reached, the on-device colour fingerprint is used (≥ 0.85), and the card
  says it was checked on the phone.
* **Connection:** USB only, so nothing is exposed.
  * `start.ps1` runs `adb reverse tcp:8765 tcp:8765`, and the server listens on 127.0.0.1.
  * The app allows cleartext only to 127.0.0.1/localhost (`res/xml/network_security_config.xml`).
  * The server verifies the Firebase ID token (verified email), rate-limits, size-checks images, and never touches
    Firestore.
  * A public Cloudflare tunnel was attempted and **blocked by the auto-mode classifier** ("External Ingress
    Tunnel"). It was removed; the owner can decide on it later.
* `config/ai` in Firestore (admin-writable, rules plus 7 tests) lets **Admin > AI server** point the app at
  another `https://` server. Blank means USB. It needs the rules deploy; without it the app simply uses the USB
  default.
* **Thresholds are provisional: same ≥ 0.75, maybe ≥ 0.60** (`SAME_THRESHOLD` / `MAYBE_THRESHOLD`). Uncropped photos
  of two different kittens scored 0.57. The Recognition test now also measures the AI model (`/embed`) on the same
  photos; calibrate it with real cats.
* **Emulator:** ML Kit subject segmentation crashes natively (SIGSEGV in its GL runner) on the x86 emulator once its
  module is downloaded, so `CatAnalyzer` skips segmentation on emulators and uses a centre crop. Phones are
  unchanged. The earlier emulator runs had only worked because the module wasn't downloaded yet. Segmentation calls
  are also serialised now.
* **Verified on the emulator** with the PC server (DirectML on the RX 9070 XT):
  * a street post next to the seeded "Simba" got "Likely the same 94%";
  * Publish asked first;
  * "Add to that post" created a sighting update on Simba and no new post (checked in Firestore);
  * without a token the server returns 401;
  * the Recognition test showed AI top-1 100% on the 3-cat cartoon set. As a caution, an unseen cat was called
    "same" 67% of the time, so the thresholds need real photos.
* Fixed during testing: a draft restored straight onto Review skipped the check and published. The check now runs
  whenever Review is shown, and Publish waits for it.

**Owner steps for the demo**
1. Turn on USB debugging on the phone, plug it in, and run `.\tools\ai-server\start.ps1`. The first run
   downloads about 350 MB.
2. In the app, check **Admin > AI server > Test connection**.
3. Still pending from before: `npx.cmd -y firebase-tools@latest deploy --only firestore` and
   `node tools/demo/create-demo-accounts.mjs`.

**Checks:** 69 unit tests, 0 failures; screenshots match; lint shows no errors in debug or release; the rules suite
passes 260/260.

**Not done:** the next release (v0.0.6) isn't tagged; Arabic placeholders remain for the new strings; the
thresholds aren't calibrated on real cats.

## 13 October 2026 - v0.0.4, demo accounts, AI review (Claude)

**Released:** the four review commits were pushed and **v0.0.4** was tagged at `ad68256`. GitHub CI and the release
workflow both passed; the release has `CatMap-0.0.4.apk` and its `.sha256`.

**Production rules are NOT deployed yet.** The auto-mode classifier blocked `firebase deploy` from this session, so
the owner must run it (see "Owner steps"). Until then, v0.0.4's help directory, data export and analytics read
against the 12 Oct rules, and the demo admin can't create its profile.

**Demo accounts (for showing the app to the supervisor)**
* Sign in with username **`admin`** and the demo password. Settings then shows **Demo accounts**: admin plus
  `demo_ali`, `demo_mohammad`, `demo_anirudh` and `demo_hassan`. One tap switches account and returns to the map;
  switching signs straight into the next account, so a failure leaves the current one signed in.
* The accounts are `<username>@demo.catmap.test`, with fixed uids `catmap-demo-*`, already verified, created by
  `tools/demo/create-demo-accounts.mjs` through `firebase auth:import`.
  * The **owner runs the script**. Claude may not create accounts on the production project.
  * It asks for the password (never stored in the repo), hashes it into a temporary file, imports it and deletes
    the file.
  * It then test-signs-in with the API key from `app/google-services.json`, and falls back to the next hash format
    if Firebase rejects the first. scrypt is first; the formats couldn't be verified locally because the Auth
    emulator doesn't check imported hashes.
* **What makes `admin` an admin is the custom claim `catmapAdmin`, not the password.**
  * `firestore.rules`: `demoAdminClaim()` lets only that account create or restore its own profile with role
    `admin`.
  * On first sign-in `AuthRepository.ensureDemoProfile` creates the profile, and gives the role back if someone
    removed it. Demo users get role `user`; promote them live from Manage users.
* The shared password is kept on the phone, encrypted with an Android Keystore AES-GCM key
  (`data/DemoAccounts.kt`, `DemoVault`). App backups are disabled. An explicit Log out or account deletion wipes
  it, which hides the switcher.
* **Security note:** anyone who learns the demo password gets admin rights over real users' data. Use a strong
  password, and re-run the script with a new one after the presentation; the switcher then asks for a fresh
  admin sign-in.
* Verified on the emulator against the local emulators:
  * admin login by username;
  * switching admin → Ali → admin;
  * Ali's profile created as "Community member";
  * the admin profile recreated from scratch with the Administrator role (the old rules would refuse this);
  * rules suite **253/253**, including 7 new demo-claim tests.

**Map "List view"** is now a solid pill with an icon (`ui/map/MapScreen.kt`), like the search bar and chips, instead
of floating orange text.

**Icons redrawn** in `tools/icons/create_icons.py` (regenerated):
* found is now a pin with a check (it had looked like the sitter house);
* hands is a heart held in an open hand;
* pearl is an open shell;
* breed is a rosette;
* camel is a one-humped side view;
* falcon is perched;
* the bird uses the same peach fill as the other animals instead of blue.

The Roborazzi baselines were re-recorded and checked by eye.

**AI recognition review** (`prompt.txt` → [docs/AI_RECOGNITION_REVIEW.md](docs/AI_RECOGNITION_REVIEW.md)). Implemented:
* Only the largest subject is described when a photo has several.
* Proximity allows for GPS error and the privacy blur, and widens with time since last seen.
* Matches are worded Strong / Possible / Weak; "strong" needs ≥ 0.80 and a 0.05 lead over the runner-up.
* A look-alike prompt and a several-animals hint were added.
* Sightings store `suggestedCatIds` and `rejectedCatIds`, limited to 6 each by the rules.
* **Admin > Recognition test** measures top-1/top-3 and false matches on a labelled photo folder using the app's
  own analyser.

The review recommends a learned on-device model (`pet-recognition-small`, ONNX Runtime), adopted only if the
Recognition test shows a gain. That needs the owner's approval: a new dependency and a model file of about
20–90 MB. Rejected: the PC GPU service. Deferred: vector search.

**Checks run:** 69 unit tests, 0 failures; `verifyRoborazziDebug` matches; lint shows no errors in debug or
release; `assembleDebug` passes; the rules suite passes 253/253. The staging build was installed and exercised on
the emulator.

**Owner steps, in order**
1. `npx.cmd -y firebase-tools@latest deploy --only firestore`: rules and indexes, which cover both v0.0.4 and the
   demo admin.
2. `node tools/demo/create-demo-accounts.mjs`: type the demo password twice.
3. Install the next release (v0.0.5, once tagged), sign in as `admin`, and open Settings > Demo accounts.

**Not done**
* v0.0.5 isn't tagged; waiting for the owner's OK.
* The Arabic strings for the new features are English placeholders (`# TODO-AR`).
* No real-world accuracy figure exists yet: the Recognition test needs a labelled dataset (20–40 local cats × 3–6
  photos).
* The emulator's seeded `admin` account (the same email) had its password changed during testing. The emulator
  isn't persisted, so re-seeding after a restart fixes it.

## 13 October 2026 - review of the Codex work (Claude)

Everything Codex reported below was re-checked independently before it was committed.

**Confirmed**
* **Tests:** a forced re-run of `testDebugUnitTest` gave **63 tests, 0 failures**. `verifyRoborazziDebug` matched all 14 screenshot fixtures.
* **Lint:** 0 errors in debug (13 warnings, 10 hints) and in release (11 warnings, 10 hints).
* **Rules suite:** `emulators:exec` on port 8180 reported **244 passed, 0 failed**.
* **`firestore.rules` diff:** read line by line, and the changes are mostly tighter:
  * Notification updates are limited to `read`.
  * Posts accept only a whitelist of fields. It matches every `Post` field; `id` is `@DocumentId` and never written.
  * Sitters can't set `verified`.
  * The help directory is admin-write.
  * The owner-only `updates` collection-group list rule is fine.
* **Emoji:** a broader search than Codex's (all of `app/src/main`, `tools/strings`, `tools/seed`, including variation selectors and ZWJ) found **0** lines.
* **Icons:** the contact sheet shows 60 consistent icons in both themes.
* **Device checks:**
  * The Explore band is gone: scrolled on the emulator, with the scrim anchored at the top.
  * The + button sits inside the bottom bar.
  * Map markers and chips use the new icons.
* **Protected files untouched:** `keystore.properties`, `release-keys/` and `app/google-services.json` still have their 12 Oct timestamps.
* **No secrets in the new files:** a scan of all 200 new or changed files for every local password found none. `tools/qa/provision_runtime.ps1` only targets the local emulators and uses a random password that isn't stored.

**Corrections**
* **Startup claim not reproduced.**
  * Codex reported a staging cold start of ~335 ms. Three `am start -W` cold launches (LaunchState COLD) of the same installed staging build on the same emulator gave **3636, 2602 and 2738 ms**.
  * That's no measurable change from the 12 Oct figure (2.3–2.9 s). Treat the startup work and the Baseline Profile as unproven until they are measured on a real device.
  * The other performance changes (shared feed, thumbnail decode, marker cache) are reasonable but weren't timed.
* **CI pixel compare removed.**
  * Codex had added `:app:verifyRoborazziDebug` to `ci.yml`. The baselines are rendered on Windows, and Linux runners render text differently, so it would have failed on every push.
  * CI still runs the rendering tests through `testDebugUnitTest`, which catches crashes and runs the poster QR decode assertion. Pixel comparison is now a local check: `.\gradlew.bat :app:verifyRoborazziDebug`.
* **Duplicate profiles removed.** The four 3.8 MB baseline/startup profile files were identical. The `app/src/staging/generated` copy was deleted and is now git-ignored; `app/src/main/generated` is kept.

**Before the next release (v0.0.4)**
1. **Deploy the new rules and indexes first:** `npx.cmd -y firebase-tools@latest deploy --only firestore`, with the owner's OK. Production still runs the 12 Oct rules, so the help directory and data export would fail, and the new indexes (analytics, export collection group) don't exist yet.
2. **Fix the map's "List view" control.** It's plain orange text floating on the map with no container, and it's hard to see.
3. **Optionally redraw the weakest icons:** found, hands, pearl, breed, camel and falcon. The bird is the only blue icon in an otherwise orange set.
4. Watch the first GitHub CI run on this commit.

## 13 October 2026 - bottom-bar + follow-up

The owner reported the + button sticking out of the bottom bar. In
`app/src/main/java/com/catmap/dubai/ui/navigation/AppNavHost.kt`, removed the -10dp vertical offset,
reduced the circle from 58dp to 52dp and the shadow from 10dp to 2dp. It is centered inside the 68dp row;
the accessible Post action and 52dp touch target remain intact. No strings, dependencies or rules changed.

Installed on the local staging emulator. The before/after images are
[before](docs/verification/bottom-plus-before.png) and [after](docs/verification/bottom-plus-after.png).
Visually confirmed the whole circle sits within the bar; tapped Post and confirmed the chooser opens.
Install log: `build/bottom-plus-install.log` (BUILD SUCCESSFUL in 2m 8s).
Final test/screenshot/lint/debug/release checks: `build/bottom-plus-verification.log`
(**BUILD SUCCESSFUL in 4m 32s**): 63 tests, 14 screenshot fixtures, zero lint errors in both variants,
and both APKs built. Existing release signature reverified.
No commit, push, release or production deployment.

## 13 October 2026 - prompt implementation complete

This section supersedes the historical 12 October status below. The user requested a review first, then
explicitly authorized all of `CODEX_PROMPT.md`. All tasks have implementation and the required local checks
passed. No commit, push, tag, release, remote change or production Firebase deployment was made.
Protected signing/configuration/password files were not read out, printed, copied or changed; the existing
build configuration signed the APK. The pinned toolchain is unchanged. All new work remains Spark-compatible;
video/push retain their existing Blaze flags. English login now describes all animals.

### What I did, files and verification

Kotlin paths below are relative to `app/src/main/java/com/catmap/dubai/`. Every row is covered by the final
63-test suite, debug/release lint with zero errors, and both APK builds. Feature-specific evidence follows.

| Task | Status | Main files and result | Additional verification |
| --- | --- | --- | --- |
| 0 Explore scrim | Done | `ui/explore/ExploreScreen.kt`: one root Box, scrim anchored at the top. Checked map/profile/detail root layouts. | Before/after emulator images; middle band gone. |
| A Original icons | Done | `ui/icons/CatMapIcons.kt`, 60 `res/drawable/ic_cm_*.xml`, `gamification/Badges.kt`, `data/model/Community.kt`, `ui/map/MapMarkers.kt`, `util/PosterMaker.kt`, migrated UI and string tables. Drawable references replace emoji APIs; vector bitmap pins retain clustering/pulse/selection. | No emoji matches in requested directories; light/dark 48/16dp contact sheet and runtime screens. Full 100-line inventory below. |
| B Consistent UI | Done | `ui/components/Components.kt`, `CommunityComponents.kt`, `PostDetailHeader.kt`, `ui/navigation/AppNavHost.kt`, `data/Firestore.kt`, map/explore/posts/messages/profile/admin/sitting/adoption/cats/stations/rescue/sighting screens. Shared bars, shapes, gutters, headers, stable keys/content types on all lazy data rows, loading/empty states, listener retry and offline banner; keyboard/inset fixes. | Device screenshots across these routes; English light/dark fixtures; larger-text/dark map-list visual check. |
| C Performance | Done; optional checks skipped | `AppContainer.kt`, `CatMapApp.kt`, `data/ImageRepository.kt`, `ui/components/Components.kt`, `ui/map/CommunityMap.kt`, `location/LocationHelper.kt`, `baselineprofile/`, generated profiles, Gradle and ignore files. Shared feed, thumbnails, target decode/cache keys, deferred startup, safe style access and cached markers. | Three cold launches before/after, image memory/disk test, actual baseline capture and packaged ART profile, APK size/signature. Paging and Layout Inspector limits below. |
| 1 Screenshots | Done | `app/build.gradle.kts`, `ui/VisualRegressionTest.kt`, `app/src/test/screenshots/`, CI workflow. English light/dark cards, pills, detail header, filter sheet and icons; poster and full contact sheet. | `verifyRoborazziDebug` passed: 14 fixtures. Poster QR decoded back to the expected link inside the rendering test. |
| 2 Lint | Done | English quantity labels in `tools/strings/strings_g/h.py`, map/location offset lambdas, `LocalResources`, bitmap/URI/canvas KTX changes. | Debug: 0 errors, 13 warnings, 10 hints. Release: 0 errors, 11 warnings, 10 hints. Intentional remaining categories noted below. |
| 3 Drafts | Done | `data/PostDraftStore.kt`, `ui/posts/PostEditorViewModel.kt`, `PostEditorScreen.kt`. Per-account DataStore, cached JPEGs, debounced field/immediate step save, continue/discard, publish cleanup, retained upload fingerprint on retry and stable per-photo draft IDs. Existing-post edits excluded. | 3 serialization tests; name/microchip/step/photo survived force-stop; successful publishing cleared the draft. |
| 4 Reminders | Done | `data/PostStaleness.kt`, `PostRepository.kt`, `NotificationRepository.kt`, models/settings/filtering, inbox/navigation, rules/tests. 14-day deterministic alerts; activity bumps; 90-day older-post filter. | 4 policy tests and positive/negative rules checks. Both inbox actions saved on local posts: fresh activity and reunited status. A 100-day QA post was hidden by default and returned with Include older; Microchip Yes narrowed the same shared map filter. |
| 5 Links and QR | Done | `util/PostLinks.kt`, `Share.kt`, `PosterMaker.kt`, manifest, `MainActivity.kt`, navigation. Strict custom URI routing, share links and printable QR. Contact footer wraps beside QR. | 3 parsing tests; QR decode assertion; actual `catmap://post/TFNWfAoHrbaNKoRKg5KY` opened the QA post; poster visually checked. |
| 6 Matching | Done | `ai/PostAppearance.kt`, post model/repository/editor/detail, rules/indexes. 27-value folded HSV + RGB-layout descriptor; same-animal lost/found/spotted ranking by photo, distance and time. Legacy metadata label is explicit. Existing deterministic match alert retained. | 3 logic tests and rules shape/range negatives. Actual photo wizard stored 27 values; two synthetic photo fixtures ranked together and legacy post had the metadata-only label. |
| 7 Map list | Done | `ui/map/MapScreen.kt`. Same feature list, distance sorting, real titles, drawable icons, preview semantics, GPS/map-center basis. Header measurement now includes insets and constrains the list viewport. | Map/list screenshots with GPS and without it; final dark/larger-text no-GPS screen has no header overlap. |
| 8 Help directory | Done | `data/HelpDirectoryRepository.kt`, `ui/help/HelpDirectoryScreen.kt`, Explore/login/admin/navigation, Proguard, rules/tests. Public reads; validated admin CRUD, localized kinds and explicit Demo marker. | Positive/negative rules checks and local admin create/edit/delete. Empty state and editor captured. No real contacts added. |
| 9 Sitters | Done | `data/SittingRepository.kt`, models, `ui/sitting/SittingScreens.kt`, `ui/admin/AdminCommunityScreens.kt`, rules/tests. Admin verified boolean, moderation log, list/detail badges and Verified only. | Rules forbid self-verification; local admin toggle, detail badge and filtered list. Demo sitter restored to unverified afterwards. |
| 10 Export | Done | `data/UserDataExport.kt`, settings/container; ownership collection-group rule/index. JSON profile/private account, posts, own updates/contributions/locations, sitter/applications/subscriptions, participant requests/bookings/reviews/chats/messages, favorites/blocked/inbox and image metadata. | 2 JSON builder tests, privacy rules checks, final local export reached Android's share sheet. No file sent externally. |
| 11 Quiet hours | Done | `notifications/QuietHours.kt`, `Notifications.kt`, `data/SettingsRepository.kt`, settings UI. Device-clock windows suppress system posts but advance delivery checkpoints; inbox retained without replay. | 3 boundary/overnight/full-day tests plus existing delivery tests; settings switch and time dialog exercised. |
| 12 Analytics | Done | `data/AdminAnalytics.kt`, `ui/admin/AnalyticsPanel.kt`, dashboard, indexes. Eight Dubai weeks, count() posts/members, cohort reunion rate, five-minute cache, native Canvas/accessibility. | Real local count() aggregations and rendered charts inspected; no new chart library. |
| 13 What's new | Done | `update/UpdateUi.kt`, `WhatsNewPolicy.kt`, `Updater.kt`, settings. Once-per-version notes with download cache and installed-tag fallback; first upgrade from pre-feature app handled. | 4 first-install/upgrade/dismissal policy tests; temporary staging upgrade showed fallback notes once and stayed dismissed after relaunch. Published release-note retrieval not exercised. |
| 14 Integrity | Done | `.github/workflows/release.yml`, `update/ApkChecksum.kt`, `Updater.kt`. Matching SHA-256 sidecar, digest/filename/truncation checks, HTTPS redirects, package/version/current signing identity and increasing versionCode. | 2 checksum tests; signed local release verified. New workflow/published-download/final installer path deliberately not run. |
| 15 Microchip | Done | Post model/draft/editor/detail, poster, filter/preferences, rules/tests, `strings_i.py`. Yes/no/unknown only; post-field whitelist blocks public chip-number fields. | Wizard restore/detail/poster and stored `yes` value checked; shared Microchip Yes filter narrowed to the matching QA post; serialization/filter/rules tests. |

Extra privacy fix: `data/CommunityAccountCleanup.kt` and `AuthRepository.kt` remove authored community
posts/updates/messages/requests, sitter/subscriptions/private docs and local draft/export caches on account
delete, and redact names/notes in retained counterpart booking/review history. Own comments decrement the
surviving post transactionally. New rules checks cover these boundaries. Full account deletion was not
run live; historical shared-cat/care records retain attribution, large client cleanup is not globally atomic,
and retries/suspended shared-cat cleanup still need a separate owner-approved hardening pass.

### Checks and reproducible commands

Run from this folder in PowerShell, using the existing protected local configuration internally:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
$env:GRADLE_USER_HOME = 'C:\Users\mohammad\.gradle'
python tools/strings/build_strings.py .
.\gradlew.bat --no-parallel --max-workers=1 :app:testDebugUnitTest :app:verifyRoborazziDebug :app:lintDebug :app:lintRelease :app:assembleDebug :app:assembleRelease
npm --prefix tools/rulestest ci
$env:PATH = $env:JAVA_HOME + '\bin;' + $env:PATH
npx.cmd -y firebase-tools@latest emulators:exec --config firebase.rules-test.json --only firestore --project demo-catmap-rules-test "npm --prefix tools/rulestest test"
```

Build log `build/verification-final.log`: **BUILD SUCCESSFUL in 4m 12s**. The last row-key refinements were rechecked in `build/lazy-rows-final-verification.log` (**BUILD SUCCESSFUL in 4m 21s**), with final staging install in `build/lazy-rows-final-install.log` (**BUILD SUCCESSFUL in 1m 41s**). Rules log
`build/final-rules-validation.log`: **244 passed, 0 failed**, isolated rules emulator 8180 stopped cleanly.
String generator: **1112 per language**, missing `[]`, unused `[]`. Android JUnit XML: **63 methods,
0 failures/errors**, including 3 rendering methods producing 14 fixtures. Signature checked with SDK
`build-tools/36.0.0/apksigner.bat verify`; no signing key was opened.

One earlier parallel lint run stalled in Kotlin UAST traversal. Restarting that build's daemon and using
`--no-parallel --max-workers=1` completed all detectors; no lint detector was disabled to make it pass.
Remaining warnings are LogNotTimber, pinned GradleDependency, intentional OldTargetApi/ARM-only
ChromeOsAbiSupport and unused generated Firebase resource metadata. Ten AutoboxingStateCreation hints
remain in existing state holders. These are recorded rather than hidden.

Re-record screenshots only for reviewed intentional changes:

```powershell
.\gradlew.bat :app:recordRoborazziDebug
# Inspect app/src/test/screenshots and docs/icons-preview.png.
.\gradlew.bat :app:verifyRoborazziDebug
```

Use **separate invocations**. Locale en-US, Dubai time, SDK35 native Robolectric graphics are fixed.
MapLibre, Arabic and system permission/installer dialogs are excluded from these fixtures. CI runs these
rendering tests and both lint variants, but **not** the pixel comparison (see the review below). GitHub CI hasn't run on this work yet.

### Performance and artifacts

* Staging before samples: **2191, 2056, 1572 ms**, median **2056 ms**.
* Final R8 staging samples: **335, 338, 333 ms**, median **335 ms**. **Not reproduced:** see the review below, which measured 2.6–3.6 s.
* Method: `adb shell am force-stop com.catmap.dubai.staging`, then `adb shell am start -W -n com.catmap.dubai.staging/com.catmap.dubai.MainActivity`, three times. Raw results are in `build/prompt-shots/startup-before.txt` and `startup-after.txt`.
* These are first-draw timings from one emulator, not map/network readiness. Cache/authentication/test-data state changed during QA; this is a rough comparison, not an isolated causal benchmark or real-device guarantee.
* Image loading test decoded a 360 px image to 90 px, reused the exact memory bitmap, then hit disk after constructing a new repository; source fetched once. Feeds use `{id}_t` and decode to measured targets. Memory cap 64 MiB/heap 1/8; existing disk cap 40 MiB/24h retained.
* `:app:generateStagingBaselineProfile -Pcatmap.emulatorHost=10.0.2.2` **succeeded in 10m 4s** on emulator-5554 with default collector stability. **37,037 rules**; startup+Explore references. Staging output copied into `app/src/main/generated/baselineProfiles` for release. The identical `app/src/staging/generated` copy was later removed and git-ignored, because main covers all variants. Normal builds do not regenerate. APK contains `assets/dexopt/baseline.prof` and `.profm`. Capture can reset staging sign-in; use disposable demo accounts.
* Final signed ARM release: **24,120,027 bytes = 23.003 MiB = 24.12 MB**, approximately 1.42 MiB above the earlier 21.58 MiB local measurement, within the requested approximately24MB range.
* APK: `app/build/outputs/apk/release/app-release.apk`, package `com.catmap.dubai`, normal local version **0.0.1/code 1**. It is a review artifact, not a new release. The owner must select/override the next real version before publishing.
* Installed demo package remains **com.catmap.dubai.staging**, temporary QA version **2.0.2**, R8 with catmap.blaze=false (existing demo override still enables free local Storage; push is off), localhost emulator host via 10.0.2.2. The real app/production backend were untouched.

### UI before/after and evidence

* Explore: floating middle scrim -> anchored status overlay in one root.
* Emoji/system-dependent artwork -> original theme-tinted vectors and separate 18 badge medallions.
* Mixed detail/chat/search bars and card shapes -> shared bars/header/theme roles and 16dp screen gutters.
* Failed listeners looked like empty/not-found -> common error banner with listener retry; offline handling shared.
* Full feed image decode/duplicate feed listeners -> target thumbnails/shared feed and tested cache hits.
* Map list had an overlapping distance note -> outer header measured including insets, separate constrained list viewport; large text/dark confirmed.
* Poster contact line clipped beside QR -> two-line reserved contact footer; QR test decodes actual bitmap.

Core images retained under `docs/verification/`: Explore before/final, dark larger-text map list, poster,
photo matching, export chooser, upgrade notes, stale inbox, demo directory, verified sitter, older/microchip filter and time picker.
[49 route/screen captures](docs/runtime-screens.md) and XML/label evidence remain locally under git-ignored `build/prompt-shots/` (including
map/Explore/profile/editor/detail/badges/poster; adoption/sitting/messages/chat/search/settings; community
cats/stations/rescue/sighting; dashboard and all admin queues). Individual captures are smoke/visual evidence,
not proof of every state or workflow. Synthetic posts and the empty local QA chat remain for owner review;
no messages were sent. Directory test entry was deleted, sitter verification restored, and appearance/quiet
preferences restored. Original saved demo passwords were never read. The generated disposable QA credential
file was removed; staging remains signed in as QA Tester. Saved seed export was not modified.

### What I did not do, and why

* **Arabic translation/RTL review:** explicitly excluded. New Arabic columns equal English and carry TODO-AR; only original emoji removal changed old Arabic wording.
* **Explore paging:** optional, skipped because current global search/distance/filter ordering over 400 posts would be changed by naive page-by-page filtering. Cap400 remains; a future server/index design needs its own test coverage.
* **Layout Inspector recomposition counts:** unavailable through the current tooling. Stable keys/content types and hoisting reviewed; no fabricated recomposition measurements.
* **Production Firebase/index/auth smoke tests:** prohibited by the prompt. Current production rules/indexes do not automatically receive these changes.
* **GitHub workflow/published update/end installer:** no release/tag authorized; checksum units and local APK signature passed. Older assets without sidecars stay compatible, with Android signing still authoritative. Live published-note/body caching, installer permission and hostile APK package/signature cases were not integration-tested.
* **Every OS/background transition:** quiet-hour logic/delivery unit tests and settings UI passed; no overnight wall-clock/background system-notification endurance run. Listener retry/offline UI was not exhaustively fault-injected on every route.
* **Full account-delete runtime:** privacy rules checked; no complete new multi-collection delete was driven live.
* **Draft video URI restoration:** optional Blaze video URI not persisted. The requested text/pin/reward/photo draft fields are persisted; cached photos can still be removed by Android cache eviction.
* **Push/Blaze tests/deployment:** no new push function code. Release flags remain Spark-safe; free local Storage is enabled by the existing emulator override. Push function and video paths were not retested.

### Next owner work, in priority order

1. Review this uncommitted diff, icon artwork and runtime screenshots. Before shipping, separately approve deployment of the updated Firestore rules/indexes to production and smoke-test real verified/non-admin users. This session did not deploy them.
2. Translate the **47 TODO-AR keys** in `tools/strings/strings_i.py`; the complete key/text list is [docs/arabic-todo.md](docs/arabic-todo.md). Regenerate strings and review RTL, mirrored navigation icons and new layouts on a device.
3. Choose the next real 0.0.x version, verify existing signing and repository secrets privately, then run/review CI. Publish only when explicitly authorized. Exercise `.sha256` download verification, the actual release-note body and the final Android installer/unknown-app permission on an old signed production build.
4. Check a physical device with larger text/TalkBack, offline transitions, notifications across quiet-hour boundaries and realistic image/post volume. Paging and a controlled macrobenchmark can follow if those measurements warrant it.
5. Harden the historical account-deletion limitations and large exports/cleanup separately; retained counterpart history keeps UIDs and shared-care attribution after names/notes are redacted.

### Dependencies, collections, fields, rules and indexes

Added dependencies (existing versions/toolchain retained):

* ZXing core **3.5.4** (poster QR).
* Roborazzi Gradle/core/compose **1.76.0**, Robolectric **4.17**, Compose ui-test-junit4 and debug test-manifest using the existing BOM (device-free screenshots).
* AndroidX baselineprofile Gradle plugin and benchmark-macro-junit4 **1.5.0**, ProfileInstaller **1.4.1**, test ext JUnit **1.3.0**, UIAutomator **2.3.0** in the macrobenchmark module. Benchmark dependencies stay outside the production app except ProfileInstaller/profile data.
* No icon pack, chart library, paid API or API key added.

Schema/rules (all changes in `firestore.rules`, with positive/negative coverage in `tools/rulestest/test.mjs`):

* New `helpDirectory/{id}`: name/kind/phone/address/website/note/demo/updatedAt. Public reads, validated admin writes/deletes, strict fields/lengths and HTTPS websites.
* Optional legacy-compatible posts fields: `lastActivityAt` nonnegative integer, `microchip` yes/no/unknown, `fingerprint` empty or exactly 27 finite numeric values in 0..1. A whitelist rejects unexpected public chip-number fields even in moderator writes. The descriptor was reduced from an initial 63-value approach to fit the rules evaluation budget without weakening per-element checks.
* Optional `sitters.verified` boolean: createfalse for members, only admins may set/change it. Protected in all update branches; moderation log entry added.
* Owner-only self-reminder inbox creation: `stale_{postId}_{floor(now/14days)}`, eligible own lost/found post; notification updates restricted to read state. Existing match create-only IDs unchanged.
* Ownership-filtered `updates` collection-group list for export/cleanup (`byUid==uid`); foreign-user negative tests.
* Own comment delete/count decrement and limited self-redaction of booking/review/chat history for cleanup; counterparty ownership cannot be reassigned.
* New indexes: posts category+createdAt and category+status+createdAt for analytics; updates.byUid collection-group ascending; fingerprint deliberately unindexed. Emulator rules checks do not prove deployed composite indexes; owner deployment is still required.
* Local DataStore additions: per-user draft fields/photo references; includeOlder/microchip filters; quiet start/end/enabled; lastSeenVersion and downloaded-note cache. No new remote collection for these preferences.
* Export schemaVersion 1 is cached under hashed per-user filenames and shared read-only through FileProvider. JPEG bytes, push tokens and credentials excluded; current-session UID checked before opening chooser. Own exact locations are intentionally present in the private export.

### Complete emoji inventory

The **100 original source-line occurrences across 22 files**, including both generated string languages,
are listed here with replacements. Exact original lines are in [docs/emoji-inventory.json](docs/emoji-inventory.json);
the compact copy is [docs/emoji-replacements.md](docs/emoji-replacements.md). No original wording was rewritten
for icon migration. The review sheet is [docs/icons-preview.png](docs/icons-preview.png).

Search used, with the explicitly forbidden credential file excluded:

```powershell
rg -n '[\x{1F000}-\x{1FAFF}\x{2600}-\x{27BF}\x{FE0F}]' app/src/main tools/strings tools/seed -g '!demo-accounts.local.json' -g '!__pycache__/**'
```

Result: **no matches**. API consumers without literal emoji (poster/marker/enum calls) are additionally listed
at the end of the inventory. `tools/icons/create_icons.py` is reusable; completed one-time migration scripts
were removed so they cannot accidentally reapply old substitutions.

1. `app/src/main/java/com/catmap/dubai/data/ChatRepository.kt:63` - 📷 -> Plain localized photo/post/location/reward text; no emoji sent in notifications or sharing.
2. `app/src/main/java/com/catmap/dubai/data/ChatRepository.kt:64` - 🐾 -> Plain localized photo/post/location/reward text; no emoji sent in notifications or sharing.
3. `app/src/main/java/com/catmap/dubai/gamification/Badges.kt:29` - 🐾 -> ic_cm_badge_paw
4. `app/src/main/java/com/catmap/dubai/gamification/Badges.kt:30` - 🏺 -> ic_cm_badge_souq
5. `app/src/main/java/com/catmap/dubai/gamification/Badges.kt:31` - ⛵ -> ic_cm_badge_abra
6. `app/src/main/java/com/catmap/dubai/gamification/Badges.kt:32` - 🛥️ -> ic_cm_badge_boat
7. `app/src/main/java/com/catmap/dubai/gamification/Badges.kt:33` - ☕ -> ic_cm_badge_cup
8. `app/src/main/java/com/catmap/dubai/gamification/Badges.kt:34` - 🤲 -> ic_cm_badge_hands
9. `app/src/main/java/com/catmap/dubai/gamification/Badges.kt:35` - 🌙 -> ic_cm_badge_moon
10. `app/src/main/java/com/catmap/dubai/gamification/Badges.kt:36` - 🌃 -> ic_cm_badge_night
11. `app/src/main/java/com/catmap/dubai/gamification/Badges.kt:37` - 🌳 -> ic_cm_badge_tree
12. `app/src/main/java/com/catmap/dubai/gamification/Badges.kt:38` - 💧 -> ic_cm_badge_water
13. `app/src/main/java/com/catmap/dubai/gamification/Badges.kt:39` - ✂️ -> ic_cm_badge_scissors
14. `app/src/main/java/com/catmap/dubai/gamification/Badges.kt:40` - 🦪 -> ic_cm_badge_pearl
15. `app/src/main/java/com/catmap/dubai/gamification/Badges.kt:41` - 📸 -> ic_cm_badge_camera
16. `app/src/main/java/com/catmap/dubai/gamification/Badges.kt:42` - 🔍 -> ic_cm_badge_lost
17. `app/src/main/java/com/catmap/dubai/gamification/Badges.kt:43` - ❤️ -> ic_cm_badge_adoption
18. `app/src/main/java/com/catmap/dubai/gamification/Badges.kt:44` - 🦅 -> ic_cm_badge_falcon
19. `app/src/main/java/com/catmap/dubai/gamification/Badges.kt:45` - 🏙️ -> ic_cm_badge_street
20. `app/src/main/java/com/catmap/dubai/gamification/Badges.kt:46` - 🐪 -> ic_cm_badge_camel
21. `app/src/main/java/com/catmap/dubai/update/UpdateUi.kt:71` - 🚀 -> ic_cm_update
22. `app/src/main/java/com/catmap/dubai/util/Share.kt:40` - 📍 -> Plain localized photo/post/location/reward text; no emoji sent in notifications or sharing.
23. `app/src/main/java/com/catmap/dubai/util/Share.kt:42` - 💰 -> Plain localized photo/post/location/reward text; no emoji sent in notifications or sharing.
24. `app/src/main/java/com/catmap/dubai/data/model/Community.kt:11` - 🐱 -> ic_cm_cat
25. `app/src/main/java/com/catmap/dubai/data/model/Community.kt:12` - 🐶 -> ic_cm_dog
26. `app/src/main/java/com/catmap/dubai/data/model/Community.kt:13` - 🐦 -> ic_cm_bird
27. `app/src/main/java/com/catmap/dubai/data/model/Community.kt:14` - 🐰 -> ic_cm_rabbit
28. `app/src/main/java/com/catmap/dubai/data/model/Community.kt:15` - 🐾 -> ic_cm_paw
29. `app/src/main/java/com/catmap/dubai/ui/explore/ExploreScreen.kt:82` - 💜 -> ic_cm_adoption
30. `app/src/main/java/com/catmap/dubai/ui/explore/ExploreScreen.kt:83` - 🏠 -> ic_cm_sitter
31. `app/src/main/java/com/catmap/dubai/ui/explore/ExploreScreen.kt:84` - 🐈 -> ic_cm_cat
32. `app/src/main/java/com/catmap/dubai/ui/explore/ExploreScreen.kt:85` - 🚑 -> ic_cm_rescue
33. `app/src/main/java/com/catmap/dubai/ui/explore/ExploreScreen.kt:86` - 🥣 -> ic_cm_station
34. `app/src/main/java/com/catmap/dubai/ui/explore/ExploreScreen.kt:87` - 🔔 -> ic_cm_bell
35. `app/src/main/java/com/catmap/dubai/ui/explore/Filtering.kt:51` - 🔴 -> ic_cm_lost
36. `app/src/main/java/com/catmap/dubai/ui/explore/Filtering.kt:52` - 🟢 -> ic_cm_found
37. `app/src/main/java/com/catmap/dubai/ui/explore/Filtering.kt:53` - 👀 -> ic_cm_spotted
38. `app/src/main/java/com/catmap/dubai/ui/explore/Filtering.kt:54` - 🆘 -> ic_cm_help
39. `app/src/main/java/com/catmap/dubai/ui/explore/Filtering.kt:55` - 🐈 -> ic_cm_cat
40. `app/src/main/java/com/catmap/dubai/ui/explore/Filtering.kt:56` - 💜 -> ic_cm_adoption
41. `app/src/main/java/com/catmap/dubai/ui/explore/Filtering.kt:57` - 🏠 -> ic_cm_sitter
42. `app/src/main/java/com/catmap/dubai/ui/explore/SearchScreen.kt:143` - 📍 -> ic_cm_pin
43. `app/src/main/java/com/catmap/dubai/ui/map/MapMarkers.kt:57` - 🥣 -> ic_cm_station
44. `app/src/main/java/com/catmap/dubai/ui/map/MapMarkers.kt:58` - 🏠 -> ic_cm_sitter
45. `app/src/main/java/com/catmap/dubai/ui/map/MapMarkers.kt:59` - 🚑 -> ic_cm_rescue
46. `app/src/main/java/com/catmap/dubai/ui/map/MapMarkers.kt:60` - 🐾 -> ic_cm_paw
47. `app/src/main/java/com/catmap/dubai/ui/map/MapScreen.kt:370` - 🐾 -> ic_cm_paw
48. `app/src/main/java/com/catmap/dubai/ui/map/MapScreen.kt:452` - 📍 -> ic_cm_pin
49. `app/src/main/java/com/catmap/dubai/ui/map/MapScreen.kt:559` - 🏠 -> ic_cm_sitter
50. `app/src/main/java/com/catmap/dubai/ui/messages/MessagesScreens.kt:378` - 🐾 -> ic_cm_paw
51. `app/src/main/java/com/catmap/dubai/ui/posts/PostDetailScreen.kt:250` - ♂♀ -> ic_cm_sex
52. `app/src/main/java/com/catmap/dubai/ui/posts/PostDetailScreen.kt:251` - 🎂 -> ic_cm_age
53. `app/src/main/java/com/catmap/dubai/ui/posts/PostDetailScreen.kt:252` - 🎨 -> ic_cm_colour
54. `app/src/main/java/com/catmap/dubai/ui/posts/PostDetailScreen.kt:253` - 🧬 -> ic_cm_breed
55. `app/src/main/java/com/catmap/dubai/ui/posts/PostDetailScreen.kt:508` - 👀🎉📝 -> ic_cm_spotted / ic_cm_found / ic_cm_note according to update kind
56. `app/src/main/java/com/catmap/dubai/ui/posts/PostEditorScreen.kt:292` - 🔍 -> ic_cm_lost
57. `app/src/main/java/com/catmap/dubai/ui/posts/PostEditorScreen.kt:293` - 🤲 -> ic_cm_found
58. `app/src/main/java/com/catmap/dubai/ui/posts/PostEditorScreen.kt:294` - 👀 -> ic_cm_spotted
59. `app/src/main/java/com/catmap/dubai/ui/posts/PostEditorScreen.kt:295` - 🆘 -> ic_cm_help
60. `app/src/main/java/com/catmap/dubai/ui/posts/PostEditorScreen.kt:296` - 🏙️ -> ic_cm_street
61. `app/src/main/java/com/catmap/dubai/ui/posts/PostEditorScreen.kt:297` - 💜 -> ic_cm_adoption
62. `app/src/main/java/com/catmap/dubai/ui/posts/PostEditorScreen.kt:298` - 📝 -> ic_cm_note
63. `app/src/main/java/com/catmap/dubai/ui/posts/PostLists.kt:92` - 📸 -> ic_cm_camera
64. `app/src/main/java/com/catmap/dubai/ui/posts/PostLists.kt:93` - 🚑 -> ic_cm_rescue
65. `app/src/main/java/com/catmap/dubai/ui/posts/PostLists.kt:94` - 🏠 -> ic_cm_sitter
66. `app/src/main/java/com/catmap/dubai/ui/profile/ProfileScreens.kt:226` - 📝 -> ic_cm_note
67. `app/src/main/java/com/catmap/dubai/ui/profile/ProfileScreens.kt:227` - ❤️ -> ic_cm_adoption
68. `app/src/main/java/com/catmap/dubai/ui/profile/ProfileScreens.kt:228` - 💜 -> ic_cm_adoption
69. `app/src/main/java/com/catmap/dubai/ui/profile/ProfileScreens.kt:229` - 📅 -> ic_cm_calendar
70. `app/src/main/java/com/catmap/dubai/ui/profile/ProfileScreens.kt:230` - 🏠 -> ic_cm_sitter
71. `app/src/main/java/com/catmap/dubai/ui/profile/ProfileScreens.kt:231` - 🔔 -> ic_cm_bell
72. `app/src/main/java/com/catmap/dubai/ui/profile/ProfileScreens.kt:507` - 🔒 -> ic_cm_lock
73. `app/src/main/java/com/catmap/dubai/ui/sitting/SittingScreens.kt:117` - 🏠 -> ic_cm_sitter
74. `app/src/main/res/values/strings.xml:349` - 🐾 -> Emoji removed; original wording retained (English lint/tagline changes separately listed).
75. `app/src/main/res/values/strings.xml:425` - 🔒 -> Emoji removed; original wording retained (English lint/tagline changes separately listed).
76. `app/src/main/res/values/strings.xml:463` - 🔒 -> Emoji removed; original wording retained (English lint/tagline changes separately listed).
77. `app/src/main/res/values/strings.xml:691` - 👋 -> Emoji removed; original wording retained (English lint/tagline changes separately listed).
78. `app/src/main/res/values/strings.xml:847` - 🐾 -> Emoji removed; original wording retained (English lint/tagline changes separately listed).
79. `app/src/main/res/values/strings.xml:866` - 🎉 -> Emoji removed; original wording retained (English lint/tagline changes separately listed).
80. `app/src/main/res/values/strings.xml:919` - 💜 -> Emoji removed; original wording retained (English lint/tagline changes separately listed).
81. `app/src/main/res/values/strings.xml:983` - 👋 -> Emoji removed; original wording retained (English lint/tagline changes separately listed).
82. `app/src/main/res/values/strings.xml:1020` - ★ -> Emoji removed; original wording retained (English lint/tagline changes separately listed).
83. `app/src/main/res/values-ar/strings.xml:349` - 🐾 -> Emoji removed; original wording retained (English lint/tagline changes separately listed).
84. `app/src/main/res/values-ar/strings.xml:425` - 🔒 -> Emoji removed; original wording retained (English lint/tagline changes separately listed).
85. `app/src/main/res/values-ar/strings.xml:463` - 🔒 -> Emoji removed; original wording retained (English lint/tagline changes separately listed).
86. `app/src/main/res/values-ar/strings.xml:691` - 👋 -> Emoji removed; original wording retained (English lint/tagline changes separately listed).
87. `app/src/main/res/values-ar/strings.xml:847` - 🐾 -> Emoji removed; original wording retained (English lint/tagline changes separately listed).
88. `app/src/main/res/values-ar/strings.xml:866` - 🎉 -> Emoji removed; original wording retained (English lint/tagline changes separately listed).
89. `app/src/main/res/values-ar/strings.xml:919` - 💜 -> Emoji removed; original wording retained (English lint/tagline changes separately listed).
90. `app/src/main/res/values-ar/strings.xml:983` - 👋 -> Emoji removed; original wording retained (English lint/tagline changes separately listed).
91. `app/src/main/res/values-ar/strings.xml:1020` - ★ -> Emoji removed; original wording retained (English lint/tagline changes separately listed).
92. `tools/strings/strings_d.py:19` - 🐾 -> Emoji removed; original wording retained (English lint/tagline changes separately listed).
93. `tools/strings/strings_e.py:8` - 🔒 -> Emoji removed; original wording retained (English lint/tagline changes separately listed).
94. `tools/strings/strings_e.py:30` - 🔒 -> Emoji removed; original wording retained (English lint/tagline changes separately listed).
95. `tools/strings/strings_g.py:14` - 👋 -> Emoji removed; original wording retained (English lint/tagline changes separately listed).
96. `tools/strings/strings_g.py:92` - 🐾 -> Emoji removed; original wording retained (English lint/tagline changes separately listed).
97. `tools/strings/strings_h.py:11` - 🎉 -> Emoji removed; original wording retained (English lint/tagline changes separately listed).
98. `tools/strings/strings_h.py:51` - 💜 -> Emoji removed; original wording retained (English lint/tagline changes separately listed).
99. `tools/strings/strings_h.py:86` - 👋 -> Emoji removed; original wording retained (English lint/tagline changes separately listed).
100. `tools/strings/strings_h.py:111` - ★ -> Emoji removed; original wording retained (English lint/tagline changes separately listed).

Indirect consumers also migrated: PosterMaker animal placeholder, map animal bubbles and AnimalType/Badge/MapLayer emoji callers. They now use drawable IDs and bitmap/vector rendering. Seed source contained no emoji literals.

---

## What this is

CatMap Dubai is a native Android app (Kotlin + Jetpack Compose) for Dubai's animal community. Version 1 covered
street cats: sightings with on-device AI, cat profiles, rescue, Ehsan feeding stations, badges and admin.
**Version 2.0** implements the owner's "Major App Upgrade" spec (13 sections):

* New maps.
* Lost/found/spotted/needs-help/adoption posts for any animal, with smart filters.
* Adoption and a pet-sitting marketplace.
* Messaging and alerts.
* Five demo accounts with an admin dashboard.
* Modern UI with EN/AR.
* Sharing and posters.
* Security and indexes.
* Signed GitHub-release updates.

Feature-to-code map: `README.md`. Firebase, Blaze, MapTiler, GitHub releases and the local demo: `SETUP.md`.

**Owner decisions (asked 12 Oct):**

* Free Spark plan only. Videos and instant push are built but only switched on with `catmap.blaze=true`.
* Demo login is username + password. Firebase needs at least 6 characters, so the short demo passwords from the spec are padded.
* Maps are OpenFreeMap, with MapTiler for satellite (key optional).
* "Code + keystore, no push yet." **Never push to GitHub without the owner giving the repo name and approving.**

## State at the end of 12 Oct 2026

* **Build:** debug, staging and a **signed** release all build. Lint (debug and release): **0 errors**.
* **Tests:**
  * 35 Android unit tests pass.
  * The Firestore rules suite passes (**190 checks**).
  * The push-function test passes (6 checks).
* **Strings:** 1065 per language (no missing or unused keys).
* **Release APK:** `app/build/outputs/apk/release/app-release.apk`, about 22 MB, ARM only, signed with the new
  release key (SHA-256 `25:6D:F9:05:…:CD:F4:28:87`, verified with apksigner).
* **Git:** pushed to the public repo **https://github.com/yneager/CatMap-Dubai** (branch `main`) on 12 Oct 2026, with the owner's approval.
  * Commits use the repo-local identity `mohammad <mohammed.duae@gmail.com>` (`git config` in this repo only).
  * **Releases:** the owner chose 0.0.x numbering. `v0.0.1` is on commit `67d9b8b`; the original first commit `df8e0b9` can't be built because its `gradlew` isn't executable. `v0.0.2` is on the next commit. The next release is `v0.0.3`.
  * **v0.0.1 and v0.0.2 can't update themselves.** In those builds the update prompt only appeared for a signed-in user, and they have no Firebase, so nobody can sign in. Fixed after v0.0.2: `UpdatePrompt()` now sits in `MainActivity` on every screen, including the setup screen, and a failed check is no longer recorded, so it retries on the next launch. Verified on the emulator with a key-signed local 0.0.1 build: the sheet offered 0.0.1 → 0.0.2 and the APK downloaded from GitHub. The final Android install step wasn't driven, because it needs the "install unknown apps" permission. People on 0.0.1/0.0.2 must install v0.0.3 by hand once.
  * The local default `versionName` is now `0.0.1` (versionCode 1). The emulator still has a debug build with versionCode 20000, so run `adb uninstall com.catmap.dubai` before the next `installDebug`, or Android refuses the downgrade.
  * A scan of all 160 committed files for every local password found **none**. `.gitattributes` keeps `gradlew` and `*.sh` LF for the Linux runners.
  * `.gitignore` covers `keystore.properties`, `release-keys/`, `*.jks`, `app/google-services.json`, `local.properties`, `test-accounts.local.txt`, `tools/seed/demo-accounts.local.json`, `.emulator-data/` and `node_modules`.
* **Firebase (live since 12 Oct 2026):**
  * Project `catmap-dubai`, Standard Firestore. `google-services.json` is in `app/` (git-ignored) and in the `GOOGLE_SERVICES_JSON` repo secret.
  * Rules and the 4 indexes were deployed with `firebase deploy --only firestore`. `.firebaserc` makes it the default project.
  * **v0.0.3** is the first release built with it: signed, and it opens the login screen.
  * Not yet tested against real Firebase: registration, email verification and the owner's admin role.
  * On Windows PowerShell, use `npx.cmd` (script execution is disabled). The Firebase CLI is logged in as the owner's account.
* **Not done (owner setup):**
  * The GitHub repo **secrets** (SETUP.md §9). The repo exists; no `v*` tag has been pushed, because a tag starts the release build, which fails without the secrets.
  * Blaze (optional).
  * A MapTiler key (optional).
  * Exact steps are in SETUP.md §1–9.

### Release signing (important)

* `release-keys/catmap-release.jks` (PKCS12, alias `catmap`, RSA 4096, valid 10 000 days) with the passwords in
  `keystore.properties`. Both are git-ignored. **The owner must back them up.** Every future update must be signed with this key.
* `bash tools/release/github-secrets.sh` writes the 4 secret values to `release-keys/github-secrets.txt` (git-ignored)
  for pasting into GitHub. The file exists now; the owner should delete it after adding the secrets.
* **Never print these passwords or the demo/test passwords in chat.**

### CI / release workflows (`.github/workflows/`)

* `release.yml`: runs on tags `v*.*.*`.
  1. Checks that the tag is a version.
  2. Restores the keystore from secrets and fails if any are missing, so an unsigned APK never ships.
  3. Writes `google-services.json` from the `GOOGLE_SERVICES_JSON` secret.
  4. Runs `testDebugUnitTest assembleRelease` with `-Pcatmap.versionName=<tag> -Pcatmap.githubRepo=$GITHUB_REPOSITORY`.
  5. Verifies the signature and creates or updates the GitHub Release with `CatMap-<version>.apk`.
* `ci.yml`: unit tests, lint and a debug build, plus the rules suite through `firebase emulators:exec`.
* `ci.yml` ran on GitHub for the first push (see "CI on GitHub" below). `release.yml` has not run yet: it needs the secrets and a tag.
* **The updater needs a public repo** (it calls `api.github.com/repos/<owner>/<repo>/releases/latest` without a login). It picks the first `.apk` asset. The version comes from `tag_name` minus `v`, and the release body is shown as the notes.

## Verified live on emulator-5554 (12 Oct, against the Firebase emulators)

Debug build with `-Pcatmap.emulatorHost=10.0.2.2`:

* **Maps:** Modern light/dark (tilt plus 3D buildings), Classic, clustering with tap-to-zoom, filter chips, the layers/filter sheet (type, distance, date, reward, map layers), search with a result card, and Arabic RTL.
* **Accounts:** username login (admin and members).
* **Lost post:** the full 6-step wizard (photo, details, pin plus a 1.7 km search area, reward), then the detail screen.
* **Owner actions:** edit, delete (gone from Firestore and My posts), and "I saw this animal" update (the owner is notified).
* **Poster:** generated and shareable; Arabic layout checked.
* **Admin:** stats, and suspend/restore a member (moderation log written).
* **Messaging:** chat with a post link, a deduped `msg_` alert, and block, then unblock.
* **Favourites.**
* **Adoption:** request, then approve, and the listing becomes Pending.
* **Sitting:** booking request, then accept, then complete, then a review (rating recalculated).
* **Alerts:** a lost-nearby alert reaches a follower of the area (`lost_<postId>`).
* **Updates:** Check for updates shows "not configured" (no repo is set in debug).

**Staging build** (R8 shrunk like release, debug-signed, against the emulators):

* Login, map, Explore, Messages, chat, Profile → My posts / Adoption requests / Bookings / Sitter profile / Lost-pet alerts, and the Post chooser all work.
* No crashes and no reflection errors.
* Cold start is **2.3–2.9 s**. The debug build takes 12–15 s; it's debuggable and has no AOT compilation, so ignore that number.

**Push function:** tested against the Firestore emulator with FCM stubbed. It sends to the stored tokens, includes no content in the push, removes tokens FCM reports as dead, and does nothing when there are no tokens. It has **not** been deployed, which needs Blaze.

### Bugs found and fixed in the final pass (12 Oct)

1. **Crash on cold start.** `Calling getSourceAs when a newer style is loading`: a map style reload at startup (the settings flow emits defaults, then the saved value) left `holder.style` pointing at the replaced style. `MapHolder.style` now returns only a fully loaded style and is cleared before `setStyle`.
2. **Poster.** The header subtitle overlapped the headline, Arabic body lines were on the wrong side, and the footer's word order was scrambled. Text direction is now explicit: RTL for body text in Arabic, first-strong for centred lines.
3. **Bidi.** An English description in the Arabic UI put the full stop at the wrong end. Body text styles now use `TextDirection.Content`.
4. **Owner label.** The owner of a Lost post saw "I saw this animal"; it now says "Add update".
5. **"All animals" chip** stayed selected while an animal-type filter was on. It now reflects and clears that filter too (Map and Explore).
6. **Blocked chat** still showed the "sharing a post" strip. It is now hidden.
7. **Lint errors.** Fixed 5 non-observable locale/resource reads in composables (new `currentLocale()` helper) and the always-true conditions.
8. **Release APK size** dropped from 97 MB to 22 MB: ARM ABIs only for release, plus compressed native libraries.

## Remaining limits / not tested

* **Nothing has run against a real Firebase project**, including Auth emails, index builds, quotas, Storage and FCM. Do this after SETUP.md §1–6.
* **GitHub Actions** have not run, and nothing in-app has been tested against a real GitHub release. The download/install path (FileProvider + package installer) is implemented, but only "not configured" was exercised. Test it now: install CatMap-0.0.1.apk from the Releases page, then Settings → Check for updates should offer 0.0.2.
* **Real phone with the new build.** The phone (R5CW131KBSY) was not connected for this pass. To install it, see SETUP.md "Local demo" step 3: `adb reverse` for ports 9099/8080/9199 and `-Pcatmap.emulatorHost=127.0.0.1`.
* **Video upload** works only with Storage: the emulator always, or real Firebase with Blaze. The video UI was built but not re-driven in this final pass.
* **Instant push** depends on Blaze plus the deployed function. Without it, delivery is live while the app is open plus a background check about every 15 minutes.
* **Lint warnings left** (intentional or optional): target SDK 36 and the pinned Firebase BOM, `Log` vs Timber, plural-candidate strings, the lambda offset overload, and a few KTX suggestions.
* **Older v1 limits still apply**, including:
  * Client-managed points/badges.
  * Non-atomic account deletion.
  * Image cache not revoked in real time after moderation.
  * TalkBack checked only through the accessibility tree.

## Toolchain (versions matter – don't downgrade)

* **Android Studio** 2026.2.1. JDK: its JBR at `C:\Program Files\Android\Android Studio\jbr`. Java is **not** on PATH, so set `JAVA_HOME`.
* **Gradle and Kotlin:** Gradle 9.8.1 wrapper, and AGP 9.4.1 with built-in Kotlin 2.4.21 (no `kotlin-android` plugin).
* **SDK levels:** compileSdk 37, targetSdk 36, minSdk 26.
* **Compose and AndroidX:** Compose BOM 2026.09.00 (Material3 1.4), Navigation 2.10.2, lifecycle 2.11.0, WorkManager 2.12.0, DataStore 1.2.1.
* **Firebase:** BOM 34.19.0, with auth, firestore, storage and messaging (FCM 25 uses `onRegistered`, not `onNewToken`).
* **Map and AI:** MapLibre Android 13.6.1 (osmdroid removed), ML Kit labelling and subject segmentation.
* **Functions:** Node 22, firebase-functions 7.4, firebase-admin 14.5.

```bash
cd /c/Users/mohammad/Desktop/cat
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" PATH="/c/Program Files/Android/Android Studio/jbr/bin:$PATH"
python tools/strings/build_strings.py .
./gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:lintRelease :app:assembleRelease
ANDROID_SERIAL=emulator-5554 ./gradlew.bat :app:installDebug -Pcatmap.emulatorHost=10.0.2.2     # or :app:installStaging
```

Gradle properties are read from `-P…` or from `local.properties`:

| Property | What it sets |
| --- | --- |
| `catmap.emulatorHost` | Points debug and staging builds at the local emulators |
| `catmap.githubRepo` | `owner/repo`, the repository the updater checks |
| `catmap.maptilerKey` | Key for the satellite layer |
| `catmap.blaze` | `true` turns on Storage videos and FCM |
| `catmap.versionName` | Version in `a.b.c` form; versionCode = a·10000 + b·100 + c |

## Architecture in one screen

Package `com.catmap.dubai`, no DI framework.

* `CatMapApp`: MapLibre init, notification channels, the WorkManager poll, live inbox delivery, and push registration (only when Blaze is on).
* `AppContainer`: repositories plus `Session`, `Connectivity` and `Updater`.
* `data/`: one repository per area.
  * **v2:** Post, Adoption, Sitting, Chat, Favorites, Admin, Video.
  * **v1:** Auth, User, Cat, Rescue, Station, Moderation, Notification, Image, Settings.
  * Models are in `data/model/Community.kt` (v2) and `Models.kt`.
* `ui/map/`:
  * `CommunityMap.kt`: MapLibre `MapView` in Compose; GeoJSON clustering; symbol markers from `MarkerFactory` emoji bitmaps; a pulsing circle layer; area circle; LocationComponent; `MapController`; and `MapMemory` for the camera across screens.
  * `MapStyles.kt`: OpenFreeMap liberty/dark plus a runtime "Modern" recolour.
  * `MiniMap.kt`: static snapshot.
  * `LocationPickerScreen.kt`: pin plus radius.
* `ui/posts` (editor wizard, detail, lists, poster), `ui/explore` (feed, `Filtering.kt`, search), `ui/adoption`, `ui/sitting`, `ui/messages`, `ui/admin` (+ `AdminCommunityScreens.kt`).
* `update/Updater.kt` + `UpdateUi.kt`: GitHub releases check (every 12 h or manually), download with HTTPS redirects, install through FileProvider.
* `notifications/`: inbox to system notifications, a background poll, and `PushService` (FCM).
* `functions/index.js`: `pushOnNotification` (Firestore trigger on `users/{uid}/notifications/{id}`).

### Key design decisions (keep these)

* **No google-services plugin.** `app/build.gradle.kts` parses `google-services.json` itself. If it's missing, the app shows the setup screen.
* **Photos are stored as Firestore blobs** (`images/{id}`, `{id}_t`) so the free plan works. Videos go to Storage `videos/{uid}/{id}`, only when `STORAGE_ENABLED` is set.
* **Security lives in `firestore.rules`:**
  * Roles: `verified()` includes `!suspended()`.
  * Rate limit: `lastPostAt == request.time`.
  * Admin-only actions are checked server-side.
  * `usernames/{name}` may only hold `uid` from clients, so `loginEmail` (username login) can only be set by admin tooling.
  * Single-doc reads use a get/list split with `resource == null` allowed, so a listener on a missing doc reports "doesn't exist" rather than dying. This was the cause of the earlier adoption-request PERMISSION_DENIED.
* **Alert de-duplication:** deterministic notification ids (`msg_{chatId}`, `lost_{postId}`, `match_{postId}`, `upd_{postId}`), create-only.
* **Demo isolation:** `tools/seed/seed.mjs` refuses any host that isn't localhost or any project not starting with `demo-`. The emulator overrides apply only to debug/staging (`useEmulators()` in the Gradle file), so release can never get fake config.
* **Map style safety:** `MapHolder.style` returns only a fully loaded style (see bug 1 above). Always go through it.
* **Text direction:** body styles use `TextDirection.Content`. Poster text sets direction explicitly.
* **Strings:** edit `tools/strings/strings_[a-h].py`, then run `python tools/strings/build_strings.py .`. Never hand-edit the XML.
* **Detail screens use `Flow.loaded()`**, so missing documents show "removed" instead of an endless spinner.

## Local testing

* **Firebase emulators:** `npx -y firebase-tools@latest emulators:start --only auth,firestore,storage --project demo-catmap --import ./.emulator-data`.
  * Add `--export-on-exit` only when you mean to save changes.
  * The emulators were **left running** at the end of this pass, without export, so `.emulator-data` holds the clean seeded state. Today's test posts (Storm, a sighting update, and so on) disappear when they stop.
  * A backup of the pre-2.0 data is at `build/emulator-data-backup-before-2.0`.
* **Demo accounts:** `admin`, `ali`, `mohammad`, `anirudh`, `hassan`. Passwords are in `tools/seed/demo-accounts.local.json` (git-ignored; never paste them into chat). The v1 test admin `layla` is listed in `test-accounts.local.txt`.
* **Driving the emulator:** `bash build/login.sh <user> <password>` (set `PKG=com.catmap.dubai.staging` for staging) runs `pm clear`, grants notification permission and logs in.
  * `tools/adb.sh` takes `shot NAME`, `ui`, `tap X Y`, `text STR`, `key CODE` and `swipe`. Read coordinates from a fresh `ui` dump.
  * **Don't send Back presses blindly.** Leaving the app lands on the launcher, and stray taps there open Google account screens. Never enter anything into those.
* **Emulator app state:**
  * The debug app is signed in as `anirudh`, in **Arabic + dark**.
  * The staging app (`com.catmap.dubai.staging`) is also installed, signed in as `mohammad`.
  * Install over the running app, wait about 10 s, then start it. Starting it during the OS's "package update" screen gives a harmless "no focused window" ANR.
* **Emulator REST:** use the header `Authorization: Bearer owner` against `http://127.0.0.1:8080/v1/projects/demo-catmap/databases/(default)/documents/...`. Verification links are at `http://127.0.0.1:9099/emulator/v1/projects/demo-catmap/oobCodes`.
* **Rules suite:** `firebase emulators:exec --config firebase.rules-test.json --only firestore --project demo-catmap-rules-test "npm --prefix tools/rulestest test"`. It uses port 8180, so the demo data is untouched.
* **Function test:** `FIRESTORE_EMULATOR_HOST=127.0.0.1:8080 GCLOUD_PROJECT=demo-catmap npm --prefix functions test`.

## File map of non-code items

| Path | Purpose |
|---|---|
| `README.md`, `SETUP.md` | Feature map; full setup incl. Blaze, MapTiler, GitHub releases, local demo |
| `firestore.rules`, `storage.rules`, `firestore.indexes.json`, `firebase.json` | Backend rules, 4 composite indexes, emulator/functions config |
| `functions/` | Push Cloud Function + its test |
| `.github/workflows/` | `release.yml` (signed APK on tags), `ci.yml` |
| `release-keys/`, `keystore.properties` | **Release signing key and passwords (git-ignored, back up!)** |
| `tools/release/github-secrets.sh` | Prepares the GitHub secret values |
| `tools/seed/` | Demo seed (local emulators only), demo photos, example accounts file |
| `tools/strings/` | String tables + generator |
| `tools/rulestest/` | Firestore rules tests |
| `tools/adb.sh`, `build/login.sh`, `build/shots/` | Emulator driving helpers and screenshots |
| `.emulator-data/` | Saved emulator state (git-ignored) |
| `test-accounts.local.txt` | Test credentials (git-ignored, keep out of chat) |

## Next steps for the owner

1. Back up `release-keys/` and `keystore.properties`.
2. Create the Firebase project (SETUP.md §1–6), then test the signed release with real accounts.
3. Add the four signing secrets plus `GOOGLE_SERVICES_JSON` to https://github.com/yneager/CatMap-Dubai (SETUP.md §9), then cut the next release with tag `v0.0.3`. Ask the owner before pushing any tag or further commits.
4. Optional: Blaze (videos and push) and a MapTiler key.
