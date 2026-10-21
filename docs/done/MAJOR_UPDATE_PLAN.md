# Major update plan: security + cost (written 20 Oct 2026)

This plan merges the owner's two briefs, `Desktop\security.txt` (security hardening) and `Desktop\updates.txt`
(cost and Firebase efficiency), into one project of 10 phases.

**Owner's rules for this project**
* **Nothing is pushed, tagged or deployed until all 10 phases are done.** It all ships as one major release.
  Local commits are fine.
* The repo stays **public**, and the app stays on the free **Spark** plan. No paid services, no Blaze.
* Never deploy to production Firebase, change live data, rotate keys, rewrite git history or replace the signing key
  without the owner's OK. Claude prepares scripts and the owner runs them.
* Keep every feature working, keep existing accounts, posts and messages, and keep in-app updates working for
  people on v0.0.9.
* The web app (`web/`) is changed by Codex. Every phase adds its web items to `WEB_TODO.txt`.

**Facts the plan is built on (checked in the code on 20 Oct 2026)**
* Opening the map downloads up to 400 posts (`PostRepository.feed`) and 800 cats (`CatRepository.allCats`). That's
  up to 1,200 reads per opening.
* The background job (`NotificationWorker`, every 15 min) re-downloads the latest 100 notifications every time.
  That's up to about 9,600 reads per user per day, even with the app closed.
* Spark allows 50,000 reads a day for the whole project. When the limit is hit, the app stops working for everyone
  until the daily reset. There's no bill on Spark, so the risk is an outage, not a cost.
* Photos are Firestore blobs (`images/{id}`, `{id}_t`) and count toward Spark's 1 GiB storage limit.
* Cloud Storage, Cloud Functions and server-side scheduled jobs all need Blaze. On Spark, server-side protection
  has to come from `firestore.rules` alone.
* App Check on Android uses Play Integrity. APKs installed from GitHub may fail it, so App Check is **monitored,
  not enforced**, unless a test proves real installs pass.
* **Old installs:** v0.0.9 phones ask for 400 posts and 800 cats at once. Any rule that rejects large queries would
  leave their map empty. Rules that old versions can't handle are prepared now but deployed only later
  (see Phase 10).

**Progress**
* Phase 1: done (20 Oct 2026). See `docs/security/THREAT_MODEL.md` and `docs/cost/BASELINE.md`.
* Phase 2: done (20 Oct 2026). Rules hardened, see the threat model status section and `docs/security/LATER_RULES.md`.
* Phase 3: done (20 Oct 2026). See "After Phase 3" in `docs/cost/BASELINE.md`. Admin list paging moved to Phase 5.
* Phase 4: done (20 Oct 2026). Design below ("Phase 4 design, as built"); numbers in "After Phase 4" in
  `docs/cost/BASELINE.md`.
* Phase 5: done (20 Oct 2026). Owner chose (a) keyword fields for search. Design below; numbers in "After Phase 5"
  in `docs/cost/BASELINE.md`.
* Phase 6: done (20 Oct 2026). See `docs/security/PRIVACY_AUDIT.md`, `docs/cost/PHOTO_STORAGE.md`,
  `docs/cost/AI_HOSTING.md`.
* Phase 7: done (21 Oct 2026). See the threat model "Status after Phase 7" (S10, S12, S14 fixed; lock-screen text).
* Phase 8: done (21 Oct 2026). Threat model "Status after Phase 8"; owner click-steps in
  `docs/security/GITHUB_SETTINGS.md`.
* Phase 9: done (21 Oct 2026). `docs/security/ABUSE_LIMITS.md`, `docs/security/APP_CHECK.md`,
  `docs/cost/COST_MODEL.md` (`tools/cost/cost_model.py`), `docs/cost/BILLING_SAFETY.md`.
* Phase 10: done (21 Oct 2026). Final report `docs/MAJOR_UPDATE_REPORT.md`; web brief `CLAUDE_TO_CODEX.txt` (round 3).
  Waiting for the owner: version number, push/release OK, later-rules timing.

---

## Phase 1: Audit and measured baseline (no app changes)

* **Threat model** (`docs/security/THREAT_MODEL.md`): malicious users, modified APKs, bots, a stolen GitHub or
  Firebase login, bad dependencies, compromised CI, fake admins, abusive uploads, privacy leaks. Findings are
  ranked by how exploitable they are, how bad, and how likely.
* **Secret scan** of the full git history, all branches and tags (gitleaks or similar, run locally). Never print
  secret values. Classify every hit, and recommend rotation only for confirmed leaks.
* **Dependency scan:** Gradle, npm (`web/`, `tools/`), GitHub Actions, and Python (`cat-server`).
* **Firestore usage baseline:**
  * Trace every query and listener on Android, web, the PC worker and the tools.
  * Count reads per app open, per map move, per feed visit and per background day, on the emulator with seeded data
    (instrumented counters, plus Query Explain where useful).
  * Write the numbers in `docs/cost/BASELINE.md`, clearly marked as *measured on the emulator* or *estimated*.
* **Done when:** both documents exist, with a ranked list of findings that the later phases fix.

## Phase 2: Firestore rules and sign-in hardening

* **Go through `firestore.rules` collection by collection.** Every write gets an explicit field allowlist, an
  ownership check and an allowed state transition, so nobody can:
  * edit protected fields (role, points, badges, verified, suspended, ownerId, createdAt);
  * forge notifications, AI-worker results or match documents;
  * read chats they aren't in, or reach other users' private profile fields;
  * read exact coordinates where the app promises an approximate location;
  * get around time limits with a fake client clock (use `request.time`).
* **The AI worker can only do its job:** read jobs, write result fields, write `config/aiWorker`, `matches` and
  `postEmbeddings`.
* **Admin checks** stay on the custom claims (`catmapAdmin`). Check what a demo account can and can't do.
* **Sign-in:**
  * account enumeration on login, register and forgot;
  * re-entering the password before email change or account deletion;
  * a suspended account is blocked everywhere;
  * email verification is enforced in the rules, not only in the app.
* **Tests:** every fix gets attack tests in `tools/rulestest/test.mjs`: unauthenticated, unverified, suspended,
  user, volunteer, moderator and admin, cross-user reads and writes, forged fields, invalid transitions.
* **Compatibility:** everything here must keep v0.0.9 working. Anything that wouldn't goes into the "later rules"
  file for Phase 10.

## Phase 3: Quick cost wins (nothing changes for users)

* **Background job:** ask only for notifications newer than the last one seen. That's about 0–1 reads per check
  instead of up to 100.
* **Admin screens:** page users (now 1,000 at once), matches (5,000), posts (600), moderation log, sitters and
  bookings.
* **Listeners:** stop screen listeners when the screen is closed, remove duplicates, and avoid re-reading on
  reconnect.
* **Photo cache:** check that `ImageDiskCache` really stops repeat downloads, and that thumbnails are used in lists
  and the full photo only when opened.
* **PC worker:** cheaper start-up catch-up, and tidy old `aiJobs` image blobs so they don't fill storage.
* **Measure** the before and after with the Phase 1 counters.

## Phase 4: Map loads only the area on screen

* **New fields on posts and cats:**
  * a geohash, for area queries;
  * a small "marker summary": the fields a map pin needs, so whole records aren't downloaded just to draw a pin.
* **Loading:** query the visible area only, at a precision that depends on the zoom level.
  * Wait until the user stops panning before loading, and cancel outdated loads.
  * Merge overlapping results, and keep areas already loaded.
  * Cluster markers.
* **Existing records:** a backfill script adds geohashes to existing data. It's tested on the emulator first, and
  the **owner runs it on production**.
* **Old fields stay,** so v0.0.9 keeps working and the change can be rolled back.
* **Delta sync (added 20 Oct 2026, the owner's idea):** show the phone's saved copy at once, then ask only for posts
  and cats changed since the last sync (`updatedAt > lastSync`, overlap for clock drift). A full refresh about once a
  day drops hidden and deleted ones (the same pattern as the PC worker's quick refresh in Phase 3). Combine it with
  the visible-area loading, so the first open is cheap and later opens are nearly free. Measure both with
  `tools/cost/baseline.mjs`.
* **Keep working:** filters, map modes, live updates for the visible area, and the privacy rules (approximate
  locations stay approximate in the summary too).

### Phase 4 design, as built (decided 20 Oct 2026)

* **Why no separate "marker summary" collection:** the Firestore phone SDKs can't download part of a document, so a
  summary field inside the post saves nothing, and a second summary document per post would double the writes and
  wouldn't be kept up to date by v0.0.9 phones. The savings come from asking for fewer documents instead. The phone's
  saved copy holds only the fields a pin and its preview card need.
* **`geoCells`** on posts and cats: the geohash of the stored (already blurred) position at precision 4, 5 and 6,
  for example `["thrr", "thrr3", "thrr3x"]`. One list field means one index per collection:
  posts `(hidden, geoCells array-contains, updatedAt desc)`, cats `(geoCells array-contains, updatedAt desc)`.
* **Clustering** already existed (`ui/map/CommunityMap.kt`), nothing changed there.
* **Area loading** (`data/MapRepository.kt`, logic in `data/MapArea.kt`): after the camera stops (debounced), pick
  the finest precision (6, 5, then 4) that covers the screen in at most 6 cells, and ask each cell not already
  loaded for its newest posts and cats. A budget of about 300 of each per screen is split between the cells, so a
  city-wide view never downloads everything; zooming in loads the rest of a busy cell.
* **Delta sync:** one listener per collection on `updatedAt > last sync - 1 h` (newest first, limit 300). It gives
  the changes since last time and then live updates. If it hits the limit, the loaded cells are treated as stale.
* **Saved copy:** `files/map/<uid>.json`, shown immediately on open. Cells are re-asked after 24 h (drops deleted and
  hidden posts); entries not confirmed for 7 days are dropped. Deleted on logout and account deletion.
* **Old posts and v0.0.9 phones:** v0.0.9 writes no `geoCells`. The PC worker fills `geoCells` (only that field) on
  posts it reads, and `cat-server\backfill.cmd` fills every post and cat once (owner runs it; emulator tested).
  New rule branch: the AI worker may change only `geoCells`. A later rule (Phase 10) can require `geoCells` on create.
* **Fallback:** if an area query fails because the index isn't built yet, the map loads the 0.0.9 way (400 + 800).
* **Known limit:** with a filter on (for example "Lost" only) a city-wide view still asks for each cell's newest posts
  of every kind, so older lost posts appear only when zooming in. Filter-aware queries come with Phase 5.

## Phase 5: Feeds, search and paging

* **Cursor paging** (never offset): Explore, My posts, favourites, messages, the notification list, post updates,
  sightings and admin lists. Page sizes match what fits on screen, with smooth infinite scroll.
* **Search: owner decision needed.** Firestore has no text search. The options:
  * **(a) Recommended:** keyword fields on each post (`searchTerms`, written when the post is saved), so search
    covers all posts. It needs the backfill too.
  * **(b)** Search only what's loaded.
* **Filters** run as Firestore queries where possible, so they don't miss posts on unloaded pages.
* Live updates stay only where they matter: chat, an open post, your own notifications.

### Phase 5 design, as built (decided 20 Oct 2026)

* **Search field `searchTerms`** (posts and cats): every word (lower case, as in `keywords`) plus its first 3–7
  letters for the first 25 and last 6 words (name, colour, breed, … and the area names at the end), at most 200
  terms. Posts take it from `keywords`; cats from their name and aliases (`data/SearchTerms.kt`, same code in
  `cat-server/searchterms.py`). A search asks `searchTerms array-contains-any [word, first 7 letters]` for the
  longest word typed, newest first, 25 at a time; the other words, chips and "include closed" are checked on the
  phone. Animal and category words ("dog", "lost", "كلب") become filters instead of search words.
* **`keywords` is no longer indexed** (nothing queries it), which saves about as much index storage as
  `searchTerms` adds.
* **Paging** (`data/Pager.kt`, `ui/components/Paging.kt`): one-shot pages with a cursor (never offsets), loading
  the next page near the end of the list and refilling when filters hide most of a page. Pull to refresh starts
  again. Explore: newest first, the category chips run as a query (index `hidden, category, createdAt desc`);
  "Nearest" uses the map's area data around you instead.
* **Nearby instead of "the newest 400"** (`PostRepository.near`, `CatRepository.near`, using Phase 4's `geoCells`):
  the post form's duplicate and same-animal checks, the phone's backup matching, the new-sighting cat matcher, cats
  near a feeding station, merge suggestions.
* **By id instead of everything:** favourites, followed cats, the merge queue.
* **Live only where it matters:** chat (newest 50 live, older on demand), an open post's updates, your
  notifications (newest 30 live, older on demand). Lists and feeds are pages that refresh on pull.
* **By id, one at a time for posts:** the rules refuse an `id in [...]` query on posts as soon as one of them was
  deleted, so favourites read each post on its own (same cost). Cats have no such rule and use `id in`.
* **Index fallback:** lists whose query needs a new index fall back to the plain newest-first list if the index
  isn't built yet (`Pager` `fallback`).
* **Admin lists** page through users, posts, moderation log, sitters, bookings and rescues.
* **Old posts and v0.0.9:** the PC worker fills `searchTerms` (from `keywords`) like `geoCells`, and
  `backfill.cmd` fills both for every post and cat.

## Phase 6: Photos, privacy and AI cost

* **Photos:**
  * Check that location data (EXIF) is removed before upload, on Android and web.
  * Size and type limits in the rules.
  * Show thumbnails first, and load the full photo only when it's opened.
* **Location privacy:** exact coordinates never appear in public documents, shares, posters or notifications when
  the post says approximate.
* **Messaging privacy:** blocking, reporting and notification previews don't leak message text where they
  shouldn't.
* **Account lifecycle:** check that deletion, data export and logout clear what they should.
* **Storage comparison** (`docs/cost/PHOTO_STORAGE.md`), with numbers: Firestore blobs vs Cloud Storage (needs
  Blaze) vs Cloud Storage + CDN. **No migration**: Spark stays. It's a plan for later only.
* **AI matching:**
  * Skip photos already embedded, and narrow the candidates before comparing (animal type, distance, time).
  * Load each model once, and send fewer and smaller images.
  * Keep the PC and on-device fallback.
  * A cloud-hosting cost estimate, as a document only.

## Phase 7: Android app hardening

* **Manifest:**
  * exported components, deep links and input checks;
  * PendingIntent flags, FileProvider paths;
  * backup rules (no tokens or private data in backups);
  * no private text in logs or notifications.
* **Network:** a network security config that blocks plain HTTP in release builds. Release builds can't point at
  the emulators.
* **Local data:** account data and caches are cleared on logout. The demo vault stays on the Keystore.
* **Release build:** check R8, no debug features, no test logins.
* **Updater** (`update/`):
  * The downloaded APK must have the same package name and the **same signing certificate** as the installed app,
    and a higher version.
  * Download links may only point at GitHub hosts. Size limits apply. Failed downloads are deleted.
  * Release notes are shown as plain text.
  * The `.sha256` alone isn't trusted.
* **Tests:** unit tests for the updater checks and the deep-link parsing.

## Phase 8: GitHub and release pipeline

* **Workflows:**
  * pin every action to a commit SHA, with least-privilege `permissions:`;
  * no secrets in pull-request runs;
  * add CodeQL and dependency review;
  * add Dependabot config (`.github/dependabot.yml`).
* **`release.yml`:** fail if signing secrets or the Firebase config are missing.
  * Check the APK's package name, version and **signing-certificate fingerprint** against the known release
    certificate.
  * Never overwrite an existing release, and run tests before publishing.
  * Remove the keystore from the runner afterwards.
  * Add an SBOM, and a build attestation if it's simple.
* **Settings only the owner can click** (written as exact steps in `docs/security/GITHUB_SETTINGS.md`): branch
  protection, tag protection, secret scanning and push protection, Dependabot alerts, immutable releases, and
  Actions permissions.

## Phase 9: Abuse limits, App Check (monitoring only) and the cost model

* **Rule-based limits** (Spark has no server code):
  * per-user "last write" timestamps that slow down spam posts, messages, reports and AI jobs;
  * caps on document and array sizes.
  * Real reporting and rescue activity must never be blocked.
* **App Check in code:** Play Integrity plus a debug provider for emulators on Android, reCAPTCHA on the web.
  **Monitoring only.** The owner's console steps and the GitHub-APK risk are written down.
* **Cost model** (`docs/cost/COST_MODEL.md`):
  * 100, 500, 1,000, 5,000 and 10,000 daily users, using the measured per-action reads;
  * which Spark limit runs out first, and when Blaze would actually be needed;
  * Blaze prices for the project's region.
* **Billing safety document:** on Spark there's nothing to bill. If the project ever moves to Blaze: budget
  alerts, the fact that alerts don't stop spending, and an emergency checklist.

## Phase 10: Web brief, full check, self-review and release prep

* **Web:** finish `WEB_TODO.txt` and write the Codex brief, covering everything from Phases 2–9 that touches the
  web (paging, geohash, search fields, rules, CSP and service-worker cache, App Check web). Then review Codex's
  work.
* **Run everything:**
  * unit, screenshot and lint, plus debug and release builds;
  * the rules suite and the web tests;
  * a secret scan and a final read of the workflows;
  * updater compatibility from v0.0.9.
* **Second review:** a skeptical review of all the changes (`/code-review`). Fix what it finds and re-run.
* **Final report** (`docs/MAJOR_UPDATE_REPORT.md`): what was fixed, findings with severity, a before/after table
  (measured where possible), remaining risks, and everything the owner must do by hand.
* **Release steps for the owner, in order:**
  1. Deploy the compatible rules.
  2. Run the backfill script.
  3. Release the APK and publish the web app.
  4. Later, once most phones have updated, deploy the "later rules": query size caps and anything else v0.0.9
     can't handle.
* Then ask the owner: "push and release vX?"

---

## Decisions the owner needs to make (Claude asks at that phase)

1. **Search (Phase 5):** keyword fields (recommended) or search what's loaded.
2. **Version number** for the major release (for example v0.1.0 instead of v0.0.10).
3. **When to deploy the "later rules"** (Phase 10), and whether to show "please update" to old versions.
