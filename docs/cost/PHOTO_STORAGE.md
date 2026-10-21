# Where photos are kept: Firestore blobs vs Cloud Storage (Phase 6, 20 Oct 2026)

**This is a plan for later only. Nothing moves: the app stays on the free Spark plan.**

## How photos are stored today

Every photo is two Firestore documents: `images/{id}` (the full photo, longest side 1280 px, JPEG, at most about
650 KB and under 1 MB by the rules) and `images/{id}_t` (the thumbnail, 360 px). Lists show thumbnails, and the full
photo loads only when a photo is opened (see `docs/security/PRIVACY_AUDIT.md`). Phones keep viewed photos for 30 days
(Phase 3), so a second view costs nothing.

**Sizes.** *Measured* on one test upload: full 182 KB, thumbnail 13 KB (a synthetic drawing, which compresses well).
*Estimated* for real phone photos at 1280 px and JPEG quality 85: about 150–350 KB full and 15–35 KB thumbnail. The
figures below use **250 KB + 25 KB = 275 KB per photo**.

## Spark limits that photos use up (official, firebase.google.com/pricing)

| Spark quota | Per photo | What it allows |
|---|---|---|
| **1 GiB of Firestore data, for everything** | 275 KB | about **3,800 photos**, before counting posts, cats and indexes |
| 10 GiB of network downloads a month | 25 KB per thumbnail view, 250 KB per full view | about 400,000 thumbnail views **or** 40,000 full views a month |
| 50,000 document reads a day | 1 read per photo view (not cached) | shared with every other read in the app |

**The first limit photos reach is the 1 GiB of stored data**, long before reads or downloads. At about 2 photos per
post, that is roughly 1,500–1,900 posts with photos. When it's full, new writes fail (Spark doesn't bill, it stops).

**What to watch:** Firebase console → Firestore → Usage → "Stored data". Plan the move at about 700 MB.

## The options

| | Firestore blobs (today) | Cloud Storage for Firebase | Cloud Storage + a CDN |
|---|---|---|---|
| Plan needed | Spark (free) | **Blaze**: Cloud Storage is "Not applicable" on Spark | Blaze |
| Free storage | 1 GiB, shared with all data | 5 GB-months (`*.firebasestorage.app` bucket in us-central1, us-west1 or us-east1) | as Cloud Storage |
| Free downloads | 10 GiB/month (shared) | 100 GB/month (same buckets) | first cache fill only |
| Free operations | 50k reads/day (shared) | 5,000 uploads and 50,000 downloads a month | — |
| Above the free amount | Google Cloud Firestore prices (storage reported as about $0.15–0.18 per GiB-month; check the official page) | Cloud Storage prices; legacy `*.appspot.com` buckets: $0.026/GB stored, $0.12/GB downloaded | CDN egress prices (not checked) |
| Max photo size | 1 MiB per document | 5 TB | 5 TB |
| Rules | `firestore.rules` | `storage.rules` (already in the repo, unused on Spark) | Storage rules, or signed URLs |

**Watch out:** the free Cloud Storage amounts apply **only** to buckets in us-central1, us-west1 and us-east1. A
bucket near Dubai (for example me-central1) pays from the first byte. The 50,000 free downloads a month is about
1,600 a day, fewer than one busy day of photo views; after that each 10,000 downloads costs cents.

## Worked example: 10,000 photos, 1,000 daily users

Assumptions: 10,000 photos (2.75 GB), each user opens 30 thumbnails and 5 full photos a day, half already cached.

| | Firestore blobs | Cloud Storage (us-central1) |
|---|---|---|
| Storage | 2.75 GB > 1 GiB: **doesn't fit on Spark**; on Blaze about 1.75 GiB above the free amount ≈ $0.3/month | 2.75 GB < 5 GB free → $0 |
| Downloads | 1,000 × (15 × 25 KB + 2.5 × 250 KB) ≈ 1 GB/day ≈ 30 GB/month: 20 GB above the free 10 GiB ≈ $2.4/month | 30 GB < 100 GB free → $0 |
| Operations | 17,500 reads/day, a third of Spark's 50,000 for photos alone | 525,000 downloads/month: 475,000 above the free 50,000 ≈ $0.2/month |
| **Monthly** | **about $3 on Blaze (doesn't fit on Spark)** | **under $1** |

*Estimates*: the user behaviour is assumed; storage and download prices are from the official Firebase pricing page
except where marked "check".

## Recommendation (for later)

1. **Now:** stay on Spark with Firestore blobs. Watch "Stored data".
2. **At about 700 MB, or when the owner moves to Blaze for another reason:** move photos to a
   `*.firebasestorage.app` bucket in **us-central1** (for the free amounts), with budget alerts set first (Phase 9's
   billing document). Write new photos to Storage, keep reading old ones from Firestore, and copy the old ones with a
   one-off script, so nothing breaks for older app versions.
3. A CDN is only worth it with far more traffic than CatMap has.

Cheaper on Spark without moving: a smaller full photo (for example 1080 px instead of 1280 px saves roughly a third),
at some cost to the AI check and to zooming in.
