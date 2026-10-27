# How many people the free (Spark) plan carries - 27 Oct 2026

An estimate, not a measurement of production billing. It combines the read counts measured on the isolated web
suite (round 3: a cold map open read 62-83 documents in 8 area queries), the limits in the code (map windows,
inbox window, page sizes) and production's size on 27 Oct: 9 users, 4 posts, 24 photos.

## The free limits that matter

| Limit | Free each day (month) | First to run out? |
|---|---|---|
| Firestore document reads | 50,000 a day | **Yes.** It resets at midnight Pacific time (11:00 or 12:00 in Dubai); until then the app can't load anything. |
| Firestore network egress | 10 GiB a month (about 345 MB a day) | Second |
| Firestore writes / deletes | 20,000 / 20,000 a day | No (needs about 800 daily users) |
| Firestore stored data | 1 GiB in total | **The long-term limit:** photos are stored in Firestore |
| Hosting transfer (web app) | 360 MB a day | No (about 3 MB per first visit) |

## Reads per person

| Who | Reads a day | Why |
|---|---|---|
| An installed Android phone, app closed | about 6-10 | The inbox check every 4 hours (`SystemNotifications.POLL_HOURS`) reads 1 document when nothing is new. It was every 15 minutes: 96+ reads a day per phone. |
| A web (iPhone) user who doesn't open it | 0 | No background check on the web |
| Someone who opens the app about twice a day | about 350 now, up to about 1,400 when the map is full | Each cold open (30+ minutes since the last): inbox 30, map up to 6 areas × 2 queries × 50 (about 80 today, 600 at most), profile and chats about 20. Plus about 45 photo documents (photos are Firestore documents; Android caches them, the web doesn't) and about 60 for browsing posts and chats. |
| The PC AI worker | about 1,000 now, about 4,000 grown | A heartbeat every 5 minutes (288 writes, each with a rules read), incremental listeners, and a full re-read of recent posts and matches every 12 hours |

## So how many people?

Daily active users (DAU) are people who open the app that day. Assume 4 installs for each daily user.

| Situation | Daily users | Installed phones |
|---|---|---|
| **Now, small community** (a few hundred posts in total) | **about 120** | about 500 |
| Grown: the areas people look at hold 50+ posts and cats each | about 30-60 | about 120-250 |
| The same small community with the old 15-minute check | about 65 | about 260 |

Egress gives a similar ceiling: about 1.8 MB per daily user (thumbnails 13 KB, full photos about 180 KB, documents),
so about 190 daily users now and about 130 grown. Reads run out first.

**Storage:** one photo is about 200 KB (full size plus thumbnail), so 1 GiB holds about **5,000 photos** in total,
counting post, cat, chat and profile photos. Today there are 24. With 100 daily users adding 20-40 photos a day,
that's 4-8 months. Deleting posts frees space, but nothing else does.

## What to do as it grows

* Past about 80 daily users, or 3,000 photos: move to the Blaze plan with a budget alert (`BILLING_SAFETY.md`).
  Then switch alerts to push (FCM via a Cloud Function), which removes the background check altogether, and move
  photos to Cloud Storage (`PHOTO_STORAGE.md`).
* Watch it: Firebase console → Firestore → **Usage** shows reads, writes and deletes per day. Spark simply stops when
  a limit is reached (no bill), so the first sign is the app failing to load late in a Dubai day.
* Rough Blaze cost: at 1,000 daily users, about 40 million reads a month. Check Firebase's pricing page for the
  database's region. At the usual rates of a few US cents per 100,000 reads, that's tens of dollars a month, not
  hundreds.
