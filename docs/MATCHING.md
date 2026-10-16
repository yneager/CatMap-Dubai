# Lost → Sighting → AI match → Chat → Reunion

How CatMap connects a lost pet with the people who see it. Android, the web/iPhone app and the PC worker share the
same Firestore documents and the same rules. AI evaluation: `docs/AI_EVALUATION.md`.

## 1. The journey

```
 User A (owner)                       Firestore                         User B (saw the animal)
 ──────────────                       ─────────                         ───────────────────────
 Lost report "Simba" ───────────►  posts/{L} category lost          ◄─── Animal Spotted / Found post
   (photos, last seen, area,          (status open)                         posts/{S} (photos, place, time)
    features, in-app contact)            │                                     │
                                         ▼                                     ▼
                   ┌──────── PC worker online? (config/aiWorker heartbeat) ────────┐
                   │ yes: cat-server/recovery.py watches new posts itself         │ no: the publishing phone,
                   │  1. DETR finds the animal, crops it                          │ and the owner's phone when
                   │  2. pet-recognition-base: one 512-d vector per photo         │ the report is opened, use
                   │     → postEmbeddings/{postId} (worker-only)                  │ MatchEngine with the colour
                   │  3. scores L↔S both ways (matching.py)                       │ fingerprint (never "strong")
                   └───────────────────────────┬──────────────────────────────────┘
                                               ▼
                         matches/{L}__{S}  (one document per pair)
                         tier: strong / possible / weak / none, reasons, model version
                                               │ possible or strong, first time only
                     ┌─────────────────────────┴─────────────────────────┐
                     ▼                                                   ▼
   users/A/notifications/pm_{L}__{S}                     users/B/notifications/pr_{L}__{S}
   "Possible sighting of Simba! …near Jumeirah"          "Your sighting may help find Simba"
                     │                                                   │
                     ▼                                                   ▼
   Lost report → Possible matches → "Compare the photos" (match/{id}): both photos side by side, details table,
   why it was suggested, distinctive features to check.
     A: This might be my animal · This is not my animal · Request more information · Message
     B: I think it's the same animal · It's a different animal · Message
                     │
                     ▼
   chats/{uidA_uidB} (the existing one-to-one chat, reused) + one "Possible match" card with both photos
                     │
                     ▼
   A: Mark as reunited → "Through a sighting on CatMap" (which one) / "Came home on their own" / "Another way"
      one transaction: posts/{L}.status = reunited, resolution, resolvedMatchId, resolvedAt
                       matches/{id}.ownerState = reunited
      → B thanked (ru_{matchId}), report shows a Reunited banner, a green "reunited" map marker for 7 days, stats,
        admin analytics.
```

Both orders work: a sighting posted **before** the lost report is found when the report is published (worker and
phone both look both ways).

## 2. Data

`matches/{lostPostId}__{sightingPostId}` (new). Nothing is copied from the posts; photos stay in `images/`.

| Field | Meaning |
|---|---|
| `lostPostId`, `sightingPostId`, `lostOwnerId`, `sightingOwnerId`, `members` | the pair and the two people (only they can read it) |
| `animalType` | must equal both posts' type |
| `tier` | strong / possible / weak / none ("none" = no reliable match, stored so a better check can replace it, never shown) |
| `score` | ranking only, 0..1, **not a probability**, never shown |
| `visual` | raw photo similarity (cosine, or fingerprint); -1 when none |
| `method`, `model`, `source` | embedding / fingerprint / details; model version (`pet-recognition-base+detr-resnet-50/v1`, `fingerprint27/v1`); worker / device |
| `distanceKm`, `daysApart`, `reasons` | the explanation shown in plain words |
| `ownerState` | new · maybe · not_mine · need_info · reunited (owner only) |
| `reporterState` | new · confirmed · not_same (reporter only) |
| `notified`, `createdBy`, `createdAt`, `updatedAt`, `reviewedAt` | bookkeeping |
| `verifiedBy`, `verifiedAt` | a moderator checked a reunion independently |

`postEmbeddings/{postId}` (new, written and read only by the worker and moderators): one float32 vector per photo
(2 KB each), the photo ids and a signature, so new photos re-embed and a model change recomputes instead of mixing
versions; detection info per photo (found, number of animals, low quality).

`posts`: two optional fields, `resolution` ('' / catmap / home / other) and `resolvedMatchId`. Older posts without
them read as ''. Messages: optional `matchId`, `matchPhotoId` (the chat card).

Notification types (text written on each phone in its language, only names and neighbourhood names, never a
position or a contact): `match_found`, `match_reporter`, `match_info`, `match_reunited`. Their ids are fixed
(`pm_`, `pr_`, `mi_`, `ru_` + match id), so each is sent at most once; they use the existing "post updates"
category, so notification settings and quiet hours apply.

## 3. Scoring (`cat-server/matching.py` = `ai/MatchEngine.kt` = `web/src/ai/MatchEngine.ts`)

1. **Photo evidence decides the label.** AI model: half the best photo pair + half the averaged photos (several
   reference photos per animal). Thresholds per species, calibrated in `AI_EVALUATION.md` §6 (cats strong ≥ 0.88,
   possible ≥ 0.79, weak ≥ 0.68; dogs 0.89 / 0.66 / 0.27). Colour fingerprint: at most "possible".
2. **Place and time are context:** each can move the label down one step, never remove it.
   * Uncertainty is subtracted first: GPS 2 × 0.15 km, 0.45 km for each blurred ("approximate") post, and the lost
     report's search area.
   * "Far" means beyond how far a lost pet plausibly is: 3 km + 1 km per day, at most 25 km.
   * A sighting more than 2 days before the pet went missing drops one step.
   * Colour-only matches also drop one step unless nearby (≤ 1.5 km).
3. **People's answers:** a pair the owner rejected is never re-scored upward or announced again; a sighting that
   looks like one the owner already rejected, or whose reporter says it's a different animal, drops one step.
4. Without usable photos: at most "weak", only if nearby, recent and the descriptions share a colour word.

The three copies are kept identical by `tools/matching/cases.json` (198 + 3 cases written by
`cat-server/tests/make_match_cases.py`) and tests in Kotlin (`MatchEngineTest`), TypeScript
(`web/tests/match-engine.test.ts`) and Python (`cat-server/tests/test_matching.py`).

Only the worker can write "strong" or "embedding" (rules). Notifications go out for "possible" and "strong" only,
and the worker announces at most the best 3 candidates of a report.

## 4. Security (`firestore.rules`, tested in `tools/rulestest/test.mjs`)

* A match can only be created for a real lost report and a real found/spotted/street post, of the same animal type,
  of two different people, with the deterministic id; by the worker, or by one of the two people (then never
  "strong", never "embedding").
* Only the two people (and moderators, and the worker) can read it; nobody can list everyone's matches.
* Only the owner can answer for the lost animal, only the reporter for the sighting, only the worker can change
  scores (never people's answers, never a rejected pair), only moderators can mark a reunion verified.
* "reunited" on a match only together with marking that report reunited through that match (same batch).
* Embeddings are worker-only, so nobody can plant a vector to force a match.
* The worker account can send only match alerts, only with their fixed ids and routes.
* Chats: unchanged rules (members only, blocked people can't message). Report-user is in the chat menu.
* Images are re-encoded on the phone before upload (EXIF, including GPS, is stripped).

## 5. When the PC is off or slow

Posting, sightings, chat and reunion never depend on the PC. The publishing phone checks the heartbeat; if the PC
is offline it pairs the post itself by colour fingerprint (labelled "compared by colour on a phone"). The owner's
phone re-checks when the report is opened. When the PC comes back, its startup catch-up embeds every recent post
it hasn't seen and re-scores the phones' matches with the AI model; deterministic ids and the `notified` flag
mean nothing is duplicated. A failed photo download leaves that post to details-only matching.

## 6. Demonstration for the supervisor

Uses the real app logic end to end. Local (no production data): start the emulators with the demo data, a staging
build pointed at them (HANDOFF "Run on the emulator") or the web app (`npm run dev` in `web`), and the PC worker in
emulator mode: `Desktop\cat-server\start.cmd --emulator`.

1. **User A** (`demo_mohammad`): **+ → Lost**, cat named **Simba**, photo of the cat, last seen near Jumeirah,
   features ("white mark near the left ear"), in-app contact. Publish. The report shows "Looking for matching
   sightings…".
2. **User B** (`demo_ali`, second phone or the web app): **+ → Animal Spotted** near Jumeirah, a *different photo of
   the same cat*. Publish. Within seconds the PC window logs `Match: Simba <-> spotted … STRONG/POSSIBLE`; B's post
   shows "Possible match: Simba — lost near Jumeirah".
3. **User A** gets "Possible sighting of Simba!" → opens it → compares the photos, reads why it was suggested →
   **This might be my animal** → **Message about this match** (the chat opens with the match card).
4. **User B** replies in the chat ("Yes, I noticed that marking…") and taps **I think it's the same animal**.
5. **User A**: **Mark as reunited** → "Through a sighting on CatMap", B's sighting → confirmation screen. The report
   shows the Reunited banner ("Confirmed by the person who saw them"), the map a green reunited marker, B gets
   "Simba is home!", Admin → dashboard → analytics counts the reunion.
6. **False-match example:** User C posts a different cat that looks similar. It appears at most as a weak or possible
   match ("Some resemblance in the photos, but many animals look alike"); A taps **This is not my animal**; it
   disappears from the list and is never suggested again.
7. **PC offline:** close the PC window and repeat step 2 with another account: the suggestion appears, labelled
   "compared by colour on a phone", never "strong".

Use real photos of real cats for steps 1–2 (two different photos of one cat, and one look-alike). The seed demo
photos are drawings, which the detector rightly doesn't treat as animals.
