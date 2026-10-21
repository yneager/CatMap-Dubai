# What the AI server would cost in the cloud (Phase 6, 20 Oct 2026)

**A document only.** Today the AI runs free on the owner's PC (`cat-server`), and phones fall back to on-device
checks when it's off. This estimates what keeping it always on in Google Cloud would cost.

## What the AI does per new post

| Step | Model | When |
|---|---|---|
| Photo check: animal, person or object | CLIP ViT-B/32 | every new photo |
| Find the animal and describe it (for matching) | DETR + pet-recognition-base | lost reports and sightings (each photo) |
| "Same animal?" for street posts | pet-recognition-base | new street posts (up to 8 crops) |

## Measured speed

| | PC GPU (RX 9070 XT, `docs/AI_EVALUATION.md` §5.5) | PC CPU only (*measured* 20 Oct 2026, `onnxruntime` CPU) |
|---|---:|---:|
| Load both models | about 1.4 s | 2.6 s |
| Find + describe one photo | 205 ms | **1,354 ms** |
| Photo check, one photo | — | **186 ms** |

A cloud CPU core is usually slower than a desktop CPU using all its cores, so the estimate below assumes **about
twice the CPU time** on a 2-vCPU instance.

## Cloud Run estimate (CPU only, Tier 1 region, request-based billing)

Prices as listed on the Cloud Run pricing page (Tier 1): $0.000024 per vCPU-second and $0.0000025 per GiB-second
above a free amount of about 180,000 vCPU-seconds and 360,000 GiB-seconds a month (the page also shows 240,000 /
450,000 for always-allocated CPU; **check the official page**).

Per post with 2 photos on a 2 vCPU / 4 GiB instance: photo check 2 × 0.4 s + describe 2 × 2.7 s ≈ **6 s**, so
12 vCPU-seconds and 24 GiB-seconds.

| New posts a day | vCPU-s / month | GiB-s / month | Monthly cost |
|---:|---:|---:|---:|
| 100 | 36,000 | 72,000 | **$0** (inside the free amount) |
| 1,000 | 360,000 | 720,000 | about $4.3 + $0.9 ≈ **$5** |
| 5,000 | 1,800,000 | 3,600,000 | about $39 + $8 ≈ **$47** |

Plus: cold starts (about 5–10 s to load the models when an instance starts; keeping one instance always warm costs
far more than the rest at CatMap's size), the container image (about 1.5 GB in Artifact Registry, cents a month), and
**the Blaze plan**.

## Why it isn't a drop-in move

* The PC listens to Firestore all the time (`on_snapshot`). Cloud Run is paid per request, so jobs would have to
  arrive as requests: a Cloud Function or Eventarc trigger on `aiJobs` and new posts (Blaze), which then calls the AI
  service.
* A GPU instance (for example an NVIDIA L4 on Cloud Run) is much faster but is billed per second while running, and
  its on-demand price wasn't confirmed here. At CatMap's volume, CPU is enough.

## Recommendation

Keep the PC worker and the on-device fallback. If the PC can't stay on, the cheapest always-on option is Cloud Run on
CPU with no minimum instances: free at today's volume, a few dollars a month at 1,000 posts a day. It needs Blaze
with budget alerts (Phase 9) and a trigger to start each job.
