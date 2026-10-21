# CatMap Dubai: Firestore usage baseline (before optimisation)

Written 20 Oct 2026, Phase 1 of `docs/done/MAJOR_UPDATE_PLAN.md`. This is the "before" picture that Phases 3–5 are
measured against. Nothing in the app was changed.

**Labels used in this file**
* **Measured:** produced by `tools/cost/baseline.mjs` on the Firestore emulator (raw numbers in
  `docs/cost/baseline-results.json`).
* **Estimated:** worked out from the code and Firebase's billing rules, not run.
* **Production:** nobody looked at production numbers (Claude can't read production). The Firebase console's
  Usage tab is the place to compare.

## How Firestore bills (the rules the numbers use)

* A query is billed **one read per document it returns**, and one read if it returns nothing.
* A live listener is billed for its first results, then one read per document that changes.
* If a listener is re-opened within ~30 minutes, the SDK's resume token means only the changes are billed. After
  longer than that (the app was closed, the phone was offline), it's billed **like a brand-new query**. The
  emulator doesn't simulate this, so the "app open" numbers below are for a **cold open** (first open in 30+
  minutes).
* A security rule that calls `get()`/`exists()` costs one read per document it looks at, per request. CatMap's
  rules look up `users/{uid}` on almost every write (`verified()`, `role()`), so writes cost +1 read.
* `count()` costs one read per 1,000 index entries (the admin dashboard uses it; it's cheap).
* **Spark free quota:** 50,000 reads, 20,000 writes and 20,000 deletes a day; 1 GiB stored; 10 GiB a month
  downloaded. Going over stops that service for the whole project until the quota resets.

## Measured: what each step reads

Method: synthetic data shaped exactly like the app's documents, at two sizes, read under the real
`firestore.rules` as a verified member. The script runs the same collection, filters, order and limit as the
Android code named in each row. "Data" is the documents' stored size (Firestore's size formula), which is close to
what's downloaded, before photos.

* **Today:** 30 users, 60 posts, 40 cats, 25 notifications, 4 chats, 40 messages.
* **Grown:** 300 users, 1,200 posts, 1,500 cats, 150 notifications, 30 chats, 400 messages. Every cap in the app is
  reached.

| Step | Android code | Today: reads | Today: data | Grown: reads | Grown: data |
|---|---|---:|---:|---:|---:|
| Profile | `Session.profile` | 1 | 0.4 KB | 1 | 0.4 KB |
| Own posts (stale-post reminder, every launch) | `PostRepository.remindStalePosts` | 3 | 3.4 KB | 20 | 22.7 KB |
| Chat list (unread badge, no limit) | `ChatRepository.chatsFor` | 4 | 0.9 KB | 30 | 6.6 KB |
| Inbox (bell, limit 100) | `NotificationRepository.notifications` | 25 | 4.8 KB | 100 | 19.3 KB |
| Posts for the map and feed (limit 400) | `PostRepository.feed` | 60 | 68.2 KB | 400 | 456.4 KB |
| Cats for the map (limit 800) | `CatRepository.allCats` | 40 | 36.4 KB | 800 | 738.0 KB |
| **App open, total (cold)** | | **133** | **114 KB** | **1,351** | **1,243 KB** |
| Background check (one run) | `NotificationWorker` → `latest()` | 25 | 4.8 KB | 100 | 19.3 KB |
| **Background, per phone per day** (96 runs) | WorkManager every 15 min | **2,400** | 460 KB | **9,600** | 1,850 KB |
| Open a chat (limitToLast 300) | `ChatRepository.messages` | 40 | 6.5 KB | 300 | 49.0 KB |
| Open a post (post + updates, limit 200) | `postFlow` + `updates` | 2 | 1.1 KB | 2 | 1.1 KB |

The earlier "about 1,200 reads per opening" is **confirmed**: 400 posts + 800 cats once the community grows, and
1,351 with the rest of the start-up reads.

## What this means on Spark

| Situation | Reads | How many fit in 50,000 a day |
|---|---|---|
| Cold app open, grown | 1,351 | about **37 opens a day for the whole app** |
| Cold app open, today | 133 | about 375 opens a day |
| One installed, signed-in phone in the background, today | up to 2,400 a day | about **20 phones** use the whole quota without opening the app |
| The same, grown | up to 9,600 a day | about **5 phones** |

**The background check is the biggest problem.** It re-downloads up to 100 notifications every 15 minutes, even when
nothing is new. Android often runs it less often when the phone is idle (Doze), so "up to" is the honest way to put
it. But every installed phone runs it, whether or not anyone opens the app.

**Downloads (estimated):** a grown cold open downloads about 1.2 MB of documents before any photo. 10 GiB a month
is about 340 MB a day, so roughly 270 grown opens a day before photos. Downloads become a limit too, but after reads.

## Other readers (estimated from the code)

* **PC AI worker (`cat-server`)** while it's running:
  * **At start:** one read per stored embedding (all of `postEmbeddings`, about 12 KB each), plus a live listener on
    every visible post from the last 60 days and on **all** of `matches`.
  * **Every 50 minutes** (`RELISTEN_SECONDS`), both listeners are closed and re-opened. The Python client has no
    resume token across new listeners, so **every re-open reads all those documents again**: about 29 times a day.
    * Today: maybe 60 posts and a handful of matches, so roughly 2,000 reads a day.
    * Grown: 600 posts in 60 days and 1,000 matches, so roughly 46,000 reads a day, almost the whole quota.
    * `matches` only ever grows.
  * **Heartbeat:** about 290 writes a day (every 5 minutes), plus +1 read each through the rules.
  * A dead listener is revived every minute (`check()`). A listener that keeps failing would re-read everything each
    time.
* **The other "load everything" screens**, each one a full read when opened cold:
  * Cats list, Search and Station screens (`allCats`, 800);
  * Explore, Adoption and My lists (`communityFeed`, 400);
  * Admin: users 1,000, matches 5,000 (analytics), all posts 600, sitters 800, bookings 500, mod log 300, all
    rescues (no limit).
* **No limit at all:**
  * sightings of a cat, pending sightings, my cats, cats I contributed to, new cats since a date;
  * matches I'm in, adoption requests, bookings, reviews of a sitter;
  * the check-ins of a station (all of them downloaded, then 50 kept), maintenance reports;
  * users with a role, help directory, stations.
* **Writes:**
  * A lost post notifies up to 300 followers of the area: up to 300 writes, written by the phone.
  * Every post or sighting also updates the user's stats (+1 write).
* **Photos:**
  * Every photo is two Firestore documents (full ≤ 650 KB, and a 360 px thumbnail). Each view not in the phone's
    cache costs 1 read plus the photo's size.
  * The phone's photo cache (`ImageDiskCache`) throws photos away after **24 hours**, although a photo id never
    changes. So the same photos are downloaded again every day.
  * Map pins are drawn on the phone and cost nothing.
* **Storage (estimated):** a real 1280 px phone photo at the app's settings is probably 200–350 KB, plus about
  20 KB for the thumbnail. 1 GiB holds roughly **3,000–5,000 photos**. That's the first storage limit (the seed
  photos are synthetic, so this isn't measured).

## The web app

`web/src/data` copies the Android queries with the same limits: feed 400, cats 800, inbox 100, messages 300, admin
users 1,000. So a cold web open costs the same as Android. The web app has no background check.

## After Phase 3 (20 Oct 2026)

| What | Before | After | How it was measured |
|---|---:|---:|---|
| Background check, per phone per day (busy inbox: an alert every 30 min) | 9,600 (grown), 2,400 (today) | **288** | *Measured* (`workerPollAfter`, 3 reads per run × 96) |
| The same with no new alerts | up to 2,400–9,600 | **96** | One read per run for an empty result (Firestore billing rule) |
| Stale-post check (reads all your posts) | every app launch | at most every 12 hours | Code (`AppNavHost`) |
| Photos re-downloaded | after 24 hours | after 30 days, and a photo you view stays cached | Unit tests (`ImageDiskCacheTest`) |
| Admin analytics: matches | 1 read per match (up to 5,000) | 8 count queries (1 read per 1,000 matches each) | Code; count billing per Firebase docs |
| AI job photos left in Firestore | up to ~1 MB per person, for good | deleted once answered; the PC sweeps old ones hourly | Rules tests + code |
| PC worker re-open every 50 min | all posts from 60 days + every match ever | only what changed since the last data (full re-read every 12 h; matches limited to the 60-day window) | Worker e2e test "a refresh doesn't re-read the whole window" |

**Background check, explained:** the phone remembers when it last checked and asks only for alerts created since then,
with one hour of overlap in case a sender's clock is slow. Alerts it already showed are filtered out by their ids.

**The PC worker, estimated:** a grown community with 600 posts in 60 days and 1,000 matches drops from roughly
46,000 reads a day to roughly 2 × 1,600 (two full reads a day) plus a few changed documents per refresh, so about
3,500 a day.

**Owner step needed:** deploy `firestore.indexes.json` (two new indexes). Until then the worker falls back to full
re-reads and says so in its log, and the admin "verified reunions" number stays empty. Both work again once the
indexes are built.

## After Phase 4 (20 Oct 2026)

All numbers *measured* on the emulator with `tools/cost/baseline.mjs` (same data as above; the synthetic posts and cats
now carry `geoCells`). The phone is assumed to show about 400 × 800 dp.

| What | Before | After (today) | After (grown) |
|---|---:|---:|---:|
| Map, first open with nothing saved (zoom 11.2 over the busy area) | 1,200 (grown), 100 (today) | 107 | **588** |
| Map opened again, nothing changed since the last sync | 1,200 / 100 | **2** | **2** |
| Map opened again an hour later, 5 posts changed | 1,200 / 100 | 8 | 24 |
| Zoom to street level inside the busy area | 0 (already loaded) | 22 | 135 |
| **Cold app open, total** (profile, chat badge, inbox, map) | 1,351 / 133 | 137 | **719** |

* **Why the first open still costs something:** the screen's cells are asked for their newest posts and cats, about
  300 of each per screen. The old map downloaded the newest 400 posts and 800 cats wherever they were. "Today" costs
  slightly more than before (107 vs 100) because every query bills at least one read, even for an empty cell.
* **Why later opens are nearly free:** the phone keeps its copy (`files/map/<uid>.json`) and only asks for what
  changed since the last sync. A cell is asked for again after 24 hours, so deleted and hidden posts disappear.
* **Zooming in** costs reads only where the coarse cell was cut at its limit, so the older posts of a busy area
  appear as you get closer.
* **Not changed yet:** Explore, Search, Adoption and the lists still read the newest 400 posts when opened, and the
  inbox reads 100 alerts. That's Phase 5.
* **Owner step:** the area queries need the two new indexes (`firebase deploy --only firestore`). Until they're
  built, the app falls back to the old 400 + 800 queries, so the map is never empty.

## After Phase 5 (20 Oct 2026)

All *measured* on the emulator with `tools/cost/baseline.mjs` (same data as above; synthetic posts now also carry
`searchTerms`).

| What | Before | After (today) | After (grown) |
|---|---:|---:|---:|
| Explore tab, first screen | 400 (the feed, grown) | 25 | **25** |
| Search for a word (posts + cats) | 400 posts + 800 cats | 26 | **26** |
| Cats directory, first screen | 800 | 25 | **25** |
| Favourites (10 saved) | 400 (the feed) | 10 | **10** |
| New street post: duplicate + same-animal checks | 700 (300 + 400) | 96 | **240** |
| Phone's backup matching (PC off), one post | 400 | 8 | 150 |
| Inbox / bell | 100 | 25 | **30** |
| Open a chat | 300 | 40 | **50** |
| **Cold app open, total** (profile, chat badge, inbox, map) | 1,351 / 133 | 137 | **649** |

* **Pages:** each list reads about 25 at a time and the next page only when you scroll near the end. Filters that
  can't be a query (for example reward or distance) are checked on the phone, and the list keeps reading pages until
  the screen is full (at most 150 items by itself, then "Load more").
* **The synthetic "grown" data is very dense** (1,200 posts and 1,500 cats inside about 10 × 10 km), so the nearby
  checks hit their limits (30 posts per area cell). Real posts spread over Dubai cost less.
* **Live listeners kept:** the inbox (newest 30), an open chat (newest 50), an open post's updates (newest 30), the
  map's "changes since". Older items are read once, on request.
* **Still the same:** the heatmap (600 recent sightings, only when switched on), sitters on the map (when the
  "Sitting" layer is on), the chat list, your own posts. These are small or opt-in.
* **Owner step:** deploy the new indexes with the rules. Until they're built, search, the category chips and the
  neighbourhood filter fail; the map falls back to the old queries but these screens don't.

## What Phases 3–5 aim at

| What | Now (grown) | Where it's fixed |
|---|---|---|
| Background reads per phone per day | up to 9,600 | Phase 3: only new notifications, about 0–1 per check |
| Cold app open | 1,351 | Phase 4: only the area on screen, then only changes (done: 719 first time, map 2 reads after) |
| Feed and lists | 400–800 per screen | Phase 5: pages of 25 (done: 25 per screen) |
| PC worker re-opens | about 29 full re-reads a day | Phase 3: a smaller window and no full re-read on token refresh |
| Photo re-downloads | every 24 h | Phase 3: keep photos much longer (ids never change) |

The same script (`tools/cost/baseline.mjs`) will be run again after each phase, to fill in the "after" column.
