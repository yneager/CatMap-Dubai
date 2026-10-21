# CatMap cost model: 100 to 10,000 daily users (Phase 9, 21 Oct 2026)

How much of Firebase's free **Spark** plan CatMap uses as it grows, which limit runs out first, and what the paid
**Blaze** plan would cost. The numbers come from `tools/cost/cost_model.py`; re-run it after changing any assumption
(each one is a named constant at the top of the script).

**Labels:** *measured* = from `docs/cost/BASELINE.md` (emulator replays of the real queries, "After Phase 5");
*assumed* = a guess about how people use the app; *price* = from Google's Firestore pricing page, us-central1,
checked 21 Oct 2026.

## What one daily user costs

| What | Reads a day | Source |
|---|---:|---|
| First open of the day (profile, chat list, inbox, map changes since last time, own-posts check) | 70 | measured per step; map copy saved on the phone |
| A second open later in the day | 40 | measured |
| Browsing: 2 pages of Explore, 5 posts with their updates, a search (half the users), a chat (30%) | 108 | measured per screen, use assumed |
| Photos not yet in the phone's 30-day cache: 15 thumbnails, 3 full photos | 18 | assumed |
| New users' first map (5% a day × 588) | 29 | measured (dense test data, so high) |
| Background inbox check on **2 signed-in phones per daily user** (~50 runs a day under Android's battery saving, 1–3 reads each) | 150 | measured per run; runs and phones assumed |
| The rules look up the writer's profile on each write | 8 | rules |
| **Total** | **about 424** | |

Writes: about **8 a day** (10% post something: post + stats + 2 photos × 2 documents + AI job; 30% send 5 messages:
message + chat preview + alert; reading marks; 1% lost reports alert 50 followers each). Stored data grows about
**59 KB a day** (almost all photos: 2 per post, 275 KB each). Downloads about **1.4 MB a day** (documents plus 15
thumbnails and 3 full photos).

Fixed, whatever the number of users: the PC worker reads about 2,000 documents a day and writes its heartbeat 288
times (estimate from Phase 3).

## Spark (free): which limit runs out first

Spark gives the whole project 50,000 reads, 20,000 writes and 20,000 deletes a day, 1 GiB of stored data, and
10 GiB of downloads a month. Going over a daily limit stops that service for **everyone** until the next day (midnight US Pacific
time, which is 11 am or noon in Dubai). Over the stored-data limit, new writes fail until something is deleted. Spark
never bills.

| Daily users | Reads a day | Writes a day | Downloads a month | Stored data grows | 1 GiB full after* | Fits Spark? |
|---:|---:|---:|---:|---:|---:|---|
| 100 | 44,000 | 1,100 | 3.9 GiB | 6 MB a day | about 160 days | **yes, just** (reads at 89%) |
| 500 | 214,000 | 4,400 | 20 GiB | 29 MB a day | 32 days | no: reads, downloads |
| 1,000 | 426,000 | 8,500 | 39 GiB | 58 MB a day | 16 days | no: reads, downloads |
| 5,000 | 2.1 million | 41,000 | 196 GiB | 290 MB a day | 3 days | no: reads, writes, downloads |
| 10,000 | 4.2 million | 82,000 | 393 GiB | 580 MB a day | 2 days | no: everything |

\* Assuming about 100 MB is already used.

**In plain words:**
1. **The first daily limit is reads, at about 110 daily users.** The background inbox check is about a third of
   that (it runs on every signed-in phone, opened or not). Busy days come first: an event or a viral post can use up
   a day's reads early.
2. **The first permanent wall is stored data (1 GiB), mostly photos.** At 100 daily users it fills in about 5
   months; at 500 in about a month. `docs/cost/PHOTO_STORAGE.md` has the plan: move photos to Cloud Storage when
   "Stored data" passes about 700 MB (that needs Blaze).
3. **Downloads (10 GiB a month) run out at about 250 daily users**, again mostly photos.
4. **Writes** are not a problem until a few thousand daily users.

So Spark comfortably serves the capstone demo and a pilot of a few dozen to about 100 active people a day. Beyond
that, the project needs Blaze, or less use per person (next section).

## Cheaper without paying (levers, for the owner to choose)

| Lever | Saves | Cost to users |
|---|---|---|
| Background inbox check every 30 min instead of 15 (`SystemNotifications.schedulePolling`) | up to 75 of 424 reads (≈18%) | alerts arrive up to 15 min later when the app is closed |
| Inbox window 15 instead of 30 (`NotificationRepository.WINDOW`) | about 30 reads | fewer old alerts shown before "Load more" |
| Full photos at 1080 px instead of 1280 px | about a third of photo storage and downloads | slightly less detail when zooming, AI checks a bit less sure |
| Fewer photos per post by default | proportional | less to see |

None of these is switched on; each is a one-line change if wanted.

## Blaze (pay as you go): what it would cost

Prices (Firestore Standard, us-central1, from cloud.google.com/firestore/pricing on 21 Oct 2026): **$0.03 per
100,000 reads, $0.09 per 100,000 writes, $0.01 per 100,000 deletes, about $0.15 per GiB of stored data a month**,
the same free amounts as Spark every day, and internet downloads above 10 GiB a month at about **$0.12 per GiB**
(Google Cloud network price; check the page). Multi-region locations such as `nam5` cost roughly twice as much per
operation; the Middle East regions have their own prices (check the location selector on the pricing page).
**Where is CatMap's database?** Firebase console → Firestore → the location is shown at the top of the Data tab (it
can't be changed later).

| Daily users | Reads | Writes | Stored data (after 6 months) | Downloads | **About per month** |
|---:|---:|---:|---:|---:|---:|
| 100 | free | free | free | free | **$0** |
| 500 | $1.5 | free | $0.6 | $1.2 | **$3** |
| 1,000 | $3.4 | free | $1.4 | $3.5 | **$8** |
| 5,000 | $19 | $0.6 | $7.5 | $22 | **$49** |
| 10,000 | $38 | $1.7 | $15 | $46 | **$100** |

Not included: Cloud Storage and a CDN for photos (cheaper than Firestore once on Blaze: `PHOTO_STORAGE.md`), the AI
server in the cloud (`AI_HOSTING.md`: $0 at 100 posts a day, about $5 a month at 1,000), push messages (FCM is free),
Play Integrity for App Check (free up to 10,000 checks a day).

**When Blaze is actually needed:** when daily reads regularly reach about 40,000 (80% of Spark), or stored data
passes about 700 MB, whichever comes first. Before switching, follow `docs/cost/BILLING_SAFETY.md`.

## How good is this model?

* The per-screen read counts are measured; how often people open each screen is assumed. Real numbers will differ,
  so check the Firebase console after the release: Firestore → **Usage** shows reads, writes and deletes per day,
  and the Data tab's **Stored data**. Divide the daily reads by a rough count of people who used the app that day
  and compare with 424.
* The "grown" test data is very dense (1,200 posts in 10 × 10 km), so map and nearby reads are on the high side.
* Android runs the 15-minute background check much less often when a phone is idle. A phone that's never idle
  costs up to 288 reads a day instead of 75.
