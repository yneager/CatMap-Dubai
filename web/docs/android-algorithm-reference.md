# Android algorithms and write order reference

Research recorded on 2026-10-13. This is a source-derived implementation reference, not a claim that the web port has been tested. Sources are the Android files under `app/src/main/java/com/catmap/dubai/`: `ai/CatAnalyzer.kt`, `ai/PostAppearance.kt`, `ai/RecognitionEvaluation.kt`, `ui/posts/PostEditorViewModel.kt`, `ui/sighting/NewSightingViewModel.kt`, `util/ImageUtils.kt`, `util/PosterMaker.kt`, `util/PostLinks.kt`, `data/PostDraftStore.kt`, `data/CommunityAccountCleanup.kt`, and `data/UserDataExport.kt`. No Android implementation was edited for this research.

## Descriptor contracts

The two descriptors are distinct and must never be interchanged.

* `PostAppearance.SIZE = 27`: 18 folded colour histogram values followed by nine RGB layout values (three vertical columns, RGB for each column). It is stored on community **posts** as `fingerprint`.
* `CatAnalyzer.FEATURE_SIZE = 117`: 54 HSV histogram values at indices 0–53, 48 RGB layout values at 54–101 (4×4 row-major cells, RGB within each cell), eight orientation values at 102–109, edge density at 110, six colour family shares at 111–116. Family order is BLACK, WHITE, GRAY, ORANGE, CREAM, BROWN. It is stored on **cats/sightings** as `features`.
* Both validators require exactly the correct length and every value finite and in `[0,1]`; they do not require individual histogram sections to sum exactly to one.

## HSV conversion and bins

Android uses `Color.colorToHSV`. Implement normal RGB→HSV with channels divided by 255, hue in degrees `[0,360)`, saturation `[0,1]`, value `[0,1]`. Retain boundary operators below; the order of tests matters. Android HSV and many later calculations are float32; the web uses float64 unless it deliberately applies `Math.fround`. Four-decimal quantisation helps but does not eliminate differences near cutoffs.

`CatAnalyzer.histBin(h,s,v)`:

```text
if s < .18 or v < .15:
    return 48 + clamp(trunc(v * 6), 0, 5)
hBin = clamp(trunc(h / 360 * 12), 0, 11)
sBin = 0 if s < .5 else 1
vBin = 0 if v < .55 else 1
return hBin * 4 + sBin * 2 + vBin
```

Thus coloured pixels have 12 hues × two saturation groups × two brightness groups = 48 bins, followed by six gray-value bins. Even unsupported coat colours participate in the image histogram.

`classify(h,s,v)` chooses the first applicable family:

```text
v < .22                                      => BLACK
s < .14 and v > .78                          => WHITE
s < .18                                      => BLACK if v < .3 else GRAY
h < 50 or h > 345:
    s > .42 and v > .45                      => ORANGE
    else v > .68                            => CREAM
    else                                    => BROWN
h < 65                                      => CREAM if v > .6 else BROWN
180 <= h <= 260 and s < .3                   => WHITE if v > .75 else GRAY
otherwise                                   => null
```

Unsupported families (e.g. grass/sky hues) are excluded from colour-share totals, not histogram totals.

## Centre weights, Android segmentation, and crop

The web uses the centre-weight path. For every `(x,y)` with integer pixel coordinates:

```text
cx = width / 2; cy = height / 2
sx = width * .30; sy = height * .32
dx = (x - cx) / sx; dy = (y - cy) / sy
weight[y * width + x] = exp(-(dx² + dy²) / 2)
```

Android calls ML Kit labelling on the source bitmap at rotation 0. `catConfidence` is the maximum Cat/Kitten label confidence (case insensitive), default 0. `animalHint` means one of the exact labels Pet, Whiskers, Mammal, Fur is present. `catDetected = confidence >= .5 || (confidence > .2 && animalHint)`. The web cannot perform this check; show not checked and never prevent continuing based on it.

Android segmenter enables a foreground confidence mask and multiple subjects with individual confidence masks. It serializes calls through a mutex, attempts a 20,000 ms timeout, and catches failures. It skips segmentation when hardware is ranchu/goldfish or product begins sdk_gphone. If more than one subject exists, choose the mask with greatest raw confidence sum; clamp each confidence to `[0,1]`, drop values `<.35`, paste it at subject start coordinates within source bounds, and accept segmentation if total confidence `> width*height*.03`. If that fails, a sufficiently large global foreground mask is tried with the same confidence clamp/threshold and area criterion. If that fails, overwrite weights with the centre Gaussian. `subjectCount = max(1, subjects.size)`. On a failed individual mask path, the global mask overwrites all image pixels, so leftover individual weights do not affect its total.

`cropSubject` uses a different subject selection: maximum bounding-box `width*height`, not confidence sum. If its area is strictly greater than `imageArea/50` (integer division), margin x/y is `trunc(subjectWidth/10)` / `trunc(subjectHeight/10)`. Left/top is clamped to zero. Crop width/height is `min(subject dimension + 2*margin, image dimension - left/top)`. Android does not adjust the width for a margin lost at the top/left. If no qualifying subject exists (the web path), square side is `trunc(min(width,height) * .85f)`, positioned at `trunc((width-side)/2)`, `trunc((height-side)/2)`. Clamp final crop width/height to at least one, then scale its maximum dimension to 320 without upscaling.

`ImageUtils.scaleToMax`: return the same bitmap if max dimension is already within the limit; otherwise scale factor is `maxDim/max(width,height)` and destination dimensions are positive rounded integers, minimum one. Android uses filtered scaling and float32 factor.

## CatAnalyzer.describe

The analyser itself does not resize its argument. Normal photo processing supplies an upright bitmap scaled to max 512.

1. For each pixel with weight **strictly greater than .05**, convert RGB to HSV, add weight to its 54-bin histogram, and classify its family. If family is non-null, add weight to that family and to `catWeight`. Family shares are each family total / catWeight, or all zero when catWeight is zero. Normalize histogram by its sum, or divisor 1 when its sum is zero.
2. Compute grayscale float values `(R*.299 + G*.587 + B*.114)/255`. For interior pixels only (`x=1..w-2`, `y=1..h-2`) with weight **strictly greater than .3**, use two-pixel differences `gx=gray[i+1]-gray[i-1]`, `gy=gray[i+w]-gray[i-w]`, magnitude `sqrt(gx²+gy²)`. Count all such pixels into areaWeight. If magnitude **strictly greater than .08**, add pixel weight to edgeWeight and add `magnitude*weight` to one of eight orientation bins: `clamp(trunc((atan2(gy,gx)+π)/(2π)*8),0,7)`. Edge density is edgeWeight/areaWeight or zero. Normalize orientation by its sum, or divisor one if zero. These are signed full-circle orientations, not modulo-π unsigned orientations.
3. Colour layout bounds are the min/max coordinates where weight **strictly greater than .5**. If `maxX<=minX` or `maxY<=minY`, use the whole image inclusive bounds. Width/height are `max-min+1`. For every pixel inside bounds with weight **strictly greater than .2**, cell is `floor((y-minY)/bboxHeight*4)*4 + floor((x-minX)/bboxWidth*4)`, each coordinate clamped 0–3. Add weighted normalized RGB in row-major cells. Normalize each cell by its weight total; empty cells use `.5` for each RGB channel.
4. Classify coat, calculate stripeRate, classify pattern, and construct breed hints as below.
5. Concatenate histogram, layout, orientation, edge density, family shares in the above order. Each component is quantised as Java `Math.round(v*10000)/10000.0` (positive half ties round up). Do not change this to ties-to-even.

### Coat classifier

Define black B, white W, gray G, orange O, cream C, brown R and warm `T=O+.5*R`. `confidence(x)=clamp(x,.2,.97)`. The first matching branch wins:

```text
T>=.12 and B>=.12 and W>=.15                => CALICO, confidence(min(T,B,W)*3)
T>=.15 and B>=.18 and W<.15                 => TORTOISESHELL, confidence(min(T,B)*2.5)
C+W>=.55 and .08<=R+B<=.35 and O<.12        => COLORPOINT, confidence(.45)
W>=.2 and B>=.2                             => BLACK_WHITE, confidence(min(W,B)*2.2)
W>=.2 and O+.5*C>=.2                        => ORANGE_WHITE, confidence(min(W,O+.5*C)*2.2)
W>=.2 and G>=.2                             => GRAY_WHITE, confidence(min(W,G)*2.2)
otherwise choose greatest family share in family order:
    share<=0                              => UNKNOWN,0
    else corresponding solid family       => family coat,confidence(share)
```

Stable tie handling matters: family-map iteration follows BLACK, WHITE, GRAY, ORANGE, CREAM, BROWN.

### Stripe rate with hysteresis

Constants: `STRIPE_STEP=.07`, `TABBY_STRIPES=2.5`, `TABBY_EDGES=.30`. Scan every third row horizontally (`y=0,3,6...`) and every third column vertically (`x=0,3,6...`); the intersection pixels are counted in both scans. Each scan starts with `extreme=-1`, `rising=0`. `length` and `transitions` are global across scans.

```text
for each pixel in scan:
    if weight < .5:
        extreme=-1; rising=0; continue
    length++
    v=gray[pixel]
    if extreme < 0: extreme=v; continue
    if rising==0:
        if abs(v-extreme) > .07:
            rising=+1 if v>extreme else -1
            extreme=v
    else if rising>0:
        if v>extreme: extreme=v
        else if extreme-v > .07:
            transitions++; rising=-1; extreme=v
    else:
        if v<extreme: extreme=v
        else if v-extreme > .07:
            transitions++; rising=+1; extreme=v
rate = transitions * 100 / length if length>0 else 0
```

The first directional movement does not count as a transition; a reversal after exceeding hysteresis does. Weight equal to .5 participates. `isStriped = rate>2.5 || edgeDensity>.30`.

### Pattern classifier

```text
CALICO         => CALICO,.8
TORTOISESHELL  => TORTOISESHELL,.75
COLORPOINT     => COLORPOINT,.5
BLACK_WHITE    => TUXEDO,.65 if black > white*1.4; else BICOLOR,.7
ORANGE_WHITE or GRAY_WHITE => TABBY,.55 if striped; else BICOLOR,.7
BLACK or WHITE => SOLID,.75
UNKNOWN        => UNKNOWN,0
others         => TABBY,confidence(.5+(stripeRate-2.5)/10) if striped
                  else SOLID,confidence(.4+(2.5-stripeRate)/10)
```

An edge-density-triggered tabby may have stripe rate below 2.5; retain the confidence formula as written.

### Breed hints

Always add arabian_mau .65 and domestic_shorthair .55. Add colorpoint .5, tabby .6, or tuxedo .6 for those respective patterns. Add orange_male .8 for ORANGE/ORANGE_WHITE, tortie_female .95 for CALICO/TORTOISESHELL, white_deafness .4 for WHITE, gray .35 for GRAY. Return all hints sorted by descending confidence (stable for ties). These are heuristic traits, not a breed-identification model. Unknown hint keys display the domestic_shorthair label.

## Similarity for cat descriptors

Invalid vectors return colour=texture=layout=0; attribute equality still contributes to `score`, even with invalid descriptors. For valid vectors `a,b`:

```text
H = clamp(sum(min(a[i],b[i])),0,1), i=0..53
L = clamp(1 - sum(abs(a[i]-b[i])) / 48 / .35,0,1), i=54..101
cos = dot(A,B)/sqrt(sum(A²)*sum(B²)), orientation i=102..109
      or 0 when either squared norm is 0
E = 1 - min(1, abs(a[110]-b[110])*3)
texture = clamp(.6*cos+.4*E,0,1)
S = clamp(1 - sum(abs(a[i]-b[i]))/2,0,1), i=111..116
colour = clamp(.55*H+.45*S,0,1)
attributes = (.65 if coat equal else 0) + (.35 if pattern equal else 0)
visual = .6*colour+.25*texture+.15*L
```

Proximity returns null for nonfinite distance. Otherwise `effective=max(distance-max(uncertainty,0),0)`, `scale=1.5+min(max(daysApart,0)*.05,2.5)`, `proximity=exp(-effective/scale)`. Maximum scale is 4 km. With proximity, total `clamp(.65*visual+.2*attributes+.15*proximity,0,1)`; without, total `clamp(.77*visual+.23*attributes,0,1)`.

Strength is STRONG for total≥.80 and either no runner-up or lead≥.05; otherwise POSSIBLE for total≥.62; otherwise WEAK. No leading-candidate assumption exists in the function itself. Average ignores invalid vectors, returns [] if none, otherwise component arithmetic means quantised with Java Math.round to four decimals.

## PostAppearance

First scale the upright source to max 128 without upscaling. Use the same centre Gaussian but **double** arithmetic; do not drop any weight. For each pixel:

```text
bin = CatAnalyzer.histBin(hsv)
foldedBin = floor(bin/4) if bin<48 else 12+bin-48
histogram[foldedBin] += weight
total += weight
cell = clamp(trunc(x*3/width),0,2)
columnWeight[cell] += weight
RGB[cell] += normalizedRGB * weight
```

Normalize all 18 histogram values by `max(total,1e-9)` and each column's RGB by `max(columnWeight,1e-9)`. Quantise using **Kotlin `kotlin.math.round(v*10000)/10000`, which has ties-to-even**. PostAppearance averaging filters invalid vectors but does **not** quantise means. Similarity rejects invalid vectors with zero, otherwise `.8*clamp(histIntersection,0,1) + .2*clamp(1 - absoluteRGBDifference/9,0,1)`.

`PostAppearance.matches` filters candidates: different id, visible, status open, same animalType, and lost→found/spotted or found/spotted→lost. Distance must be finite and ≤10 km. `time=exp(-abs(createdAt difference)/(21*86400000))`, `proximity=exp(-distance/3)`. When both fingerprints valid, score `.6*photoSimilarity+.25*proximity+.15*time`; otherwise `.6*proximity+.4*time`. Sort descending and take six, without a separate minimum score.

## Post editor state and write order

Steps are TYPE, PHOTOS, DETAILS, PLACE, CONTACT, REVIEW (indices 0–5). Preset category starts at PHOTOS. Invalid preset categories/types fall back to lost/cat. Default contact is in-app, sex/age unknown, microchip unknown, radius zero. Approximate defaults true only for adoption. Editing loads existing fields and starts DETAILS. Photo ceiling comes from `PostRepository.MAX_PHOTOS` (six).

Continuation checks: DETAILS requires nonblank description or name or features; PLACE requires lat/lng and Dubai bounds; PHONE requires at least seven digits; EMAIL requires both `@` and `.`. Other steps allow continuing. Reward is not a continuation check.

New-post drafts load per account; no drafts for editing. Autosave observes readiness, pending restored draft, and form snapshot with a 500 ms debounce. Save only after draftReady, no pending restore prompt, same current account, and form has advanced or has name/description/photos. `saveDraftNow` also accepts features/location as evidence of work, skips draftPublished, editing, not-ready or pending restored draft, and captures current account before its background job. Cancel and join the autosave job before clearing after successful publish so a late save cannot resurrect it.

Snapshot string fields: step, category, animalType, name, description, color, breed, features, sex, ageGroup, lat, lng, radius, approximate, lastSeenAt, reward, contactMethod, phone, email, health, vaccinated, neutered, videoPath, microchip, fingerprint (comma-joined). Photos are ordered `local:<uuid>` / `remote:<id>`. Draft does not include notSameAnimal, sameAnimal results, removedRemote, or local video file bytes.

Continue draft validates category/type, finite lat/lng, clamps radius to 0–5000, validates microchip yes/no/unknown and fingerprint27, and clamps step to 0–5. Restored full JPEG creates thumbnail max360 JPEG quality72 and analysis max512. Missing/unreadable cached local photos are skipped; remote photos remain. Local objects get new UUIDs on restoration; next save must capture their bytes under those new IDs. Clear pending savedDraft prompt after restoration.

Street same-animal check applies only to a new STREET post with a local photo and location. Key memoizes first-local UUID+lat+lng+animalType. Fetch feed limit400, then filter STREET/open/nonhidden/same animalType/has photos. Keep finite distance≤1 km, nearest first, at most8. Query is cropped first-local analysis bitmap. Load candidate cover full image target640, crop it. Send candidate ids/crops and labels (name or `Street <animalType>`) to AI server. Server result retains same/maybe and sorts descending similarity. If server returns null, describe the first local image as PostAppearance and compare against **all nearby candidates' stored fingerprints**, keep score≥.85 sorted descending top3 with verdict maybe/byServer false. An empty result list from the server is authoritative (not null), so it does not trigger fallback. An exception clears results and reports an error. `unresolvedSameAnimal` is the first verdict same candidate not in notSameAnimal.

Publishing order:

1. Require location; set publishing true.
2. Compute average valid PostAppearance over local photos **plus existing fingerprint once**, or preparedFingerprint if no valid inputs; cache preparedFingerprint.
3. Sequentially map all photos to ids (remote unchanged, locals upload). After the entire pass succeeds, replace form photos with Remote ids. A retry after a later failure reuses these ids. A failure halfway through this upload pass can leave already-uploaded images because conversion happens after all succeed.
4. Upload chosen video if any. If replacement uploaded and old videoPath nonblank, delete old video. Cache final path; clear local video selection.
5. Fuzz coordinates only if approximate, seeded with concatenation `uid + lat + lng`; otherwise exact.
6. Build PostDraft. areaRadiusM is trunc(radius) for lost, else 0. Reward is first six digits from entered reward, parsed as integer or 0. Store only selected phone/email contact, blank the other. Include prepared fingerprint and microchip.
7. Create new post or update existing post through PostRepository.
8. Delete removed remote photos best effort, clear removal list.
9. For new post, mark draftPublished, cancel and join autosave, clear draft.
10. Return id; publishing false in finally.

`addToExisting`: require location, upload **first local photo only**, fuzz location if approximate, trim description/features and join nonempty text with newline, call PostRepository.addUpdate with kind SIGHTING and target id. Only after success mark published, cancel/join draft autosave, clear draft and return target id. No new post is created. This path does not replace the local photo with a remote reference, so a retry after an update failure may reupload.

## PostDraftStore storage contract

Android codec is version1 binary Base64; browser IndexedDB can use structured records while retaining account scope, bounds, photo ordering and persistence behaviour. Codec limits: ≤64 string fields, ≤6 photos; encoded input≤200,000 characters; each key≤60 characters and value≤20,000 characters; photo token regex `(local|remote):[A-Za-z0-9_-]{1,128}`; reject unsupported version, corrupt input, excess bounds, trailing bytes. Encode fields in sorted key order. Android `writeUTF` is Java modified UTF-8, so do not substitute ordinary UTF-8 if interoperating with codec bytes.

Android draft files live under cache drafts/SHA256(uid), not raw uid paths. `save` is mutex-protected: create directory, validate local id regex, write bytes only when file absent, delete unretained local JPEG files, **then** save encoded metadata. `clear` under same mutex removes metadata **before** deleting cached files. The web should use an atomic IndexedDB transaction where possible and serialize save/clear so stale writes cannot restore a published draft.

## NewSightingViewModel

Five photos maximum, match threshold .45. On photo add, process each image then analyze; failures in image processing yield err_photo and continue; analyser failure yields photo with null analysis. Recombine picks coat and hints from highest colorConfidence analysis, pattern from independently highest patternConfidence analysis, catDetected if any true, severalSubjects if any subjectCount>1, and averages valid117 features. Manual edit of either coat or pattern sets a single coatEdited flag preventing both automatic assignments. A preset cat's coat/pattern/size is loaded and it bypasses matching.

Each match search cancels the prior job and increments a version counter. Fetch all cats, retain nonempty features, score using **combined automatic coat/pattern**, optional location distance, uncertainty `(accuracy + HIGH privacy radius)/1000`, days `max(seenAt-lastSeen,0)/86400000` or 0 if unknown. Filter total≥.45, sort descending, take6. Strength for the first candidate compares second candidate; all other candidates compare first candidate. A later cancelled request cannot reset current matching state (version check in finally). No global distance cutoff exists for cat-profile matches.

GPS sets locationSource gps and supplied accuracy; manual location sets source manual, accuracy0; both rerun matching. Confirm removes from rejected; reject clears matching confirmation and uniquely records rejected id. Target prioritizes presetCat over confirmed.

Submit order: require active profile, Dubai bounds, and name only for a new cat; read current privacy settings; snapshot picked photos; upload sequentially with retry cache keyed `(uid, photo key)`; update progress `(index+1)/(photoCount+1)`. SightingDraft includes target, typed details, selected coat/pattern/size/sex, best-coat breed hint keys, photo ids, averaged117 features, exact chosen lat/lng/accuracy/source/time, health/behavior tags, earTip, care flags, notes, confirmed candidate score or0, displayed suggested ids, rejected ids. `aiSummary` is `coatKey:patternKey:coatConfidence` formatted two decimals Locale.US from combined automatic analysis. Call cats.submitSighting with privacyLevel and shareExactWithVolunteers; progress1 and return cat id. Clear submitting in finally; retain cached upload ids on error.

Suggested name lists: orange/orange_white Zafran/Mishmish/Asal/Burtuqala; black Qahwa/Layl/Fahm/Dukhoon; white Thalj/Lulu/Qamar/Haleeb; gray/gray_white Ramadi/Ghaim/Fidda/Dukhan; black_white Domino/Shatranj/Oreo/Bisbis; calico/tortoiseshell Fusaifisa/Kunafa/Zainab/Sadu; cream/colorpoint Luqaimat/Karak/Sukkar/Hareera; brown Tamr/Dallah/Bunni/Khubz; unknown Bissa/Najma/Sahm/Habibi.

## RecognitionEvaluation

Descriptor evaluation first filters only by features length117 (not complete vector validity), groups by identity and needs at least two identities and at least one identity with two photos. Every sample in a repeated identity is a probe; singleton identities remain as distractor galleries. Leave the exact probe object out of its own gallery. Average method calls Similarity.average and uses coat/pattern from the first remaining gallery sample. Best-photo method takes maximum Similarity.score against individual photos and their own attributes. Distance is null throughout.

Ranks are `1 + count(other identity score > genuine score)`; equal scores tie for rank1 rather than receiving arbitrary ordering. Record avg and best top1/top3. Genuine and impostor distributions for descriptor report use **average-gallery scores**. Unseen test excludes true identity, takes highest wrong score, counts any suggestion at passed matchThreshold (default.45), and applies strength with next-highest wrong score. Strong also increments possible. `threshold5` is sorted impostor element at `trunc((count-1)*.95)`; genuine retention uses **strictly > threshold**, not ≥. Means use all pair scores; analysis time averages filtered samples, including singleton distractors. Count skipped photos as original count minus length-valid count. Report displays suggestion threshold0.45 literally even if a nondefault matchThreshold was supplied.

Embedding evaluation filters only nonempty embeddings. It assumes consistent lengths and finite numeric values (a web input boundary can reject malformed server embeddings before evaluation). Cosine is dot divided by `max(sqrt(sum(a²))*sqrt(sum(b²)),1e-9)`. Gallery means are plain component arithmetic means, not quantised. Same leave-one-out grouping/ranks; report average top1, best top1/top3. Embedding genuine/impostor distributions use **best-photo scores**. knownSame counts genuine best≥same; unseenSame/unseenMaybe count greatest wrong score≥passed thresholds. Quantile and strict genuine retention follow same formula.

Report percentages format one decimal, scores three decimals using Locale.US; include dataset counts, chance-level1/identityCount for descriptors, metrics, means/quantile and a per-probe CSV (identity,file,rank_average,rank_best_photo,same_cat_score,best_other_score). Admin web labels centre analysis web analyser while preserving the actual protocol; server embedding report states model and supplied verdict thresholds.

## PosterMaker canvas geometry

Canvas 1080×1350, margin64. Background RGB255,251,247. Category gradient header from `(0,0)` to `(1080,230)`, category accent to accent RGB×.7 integer. Headline at x64,y34,width952,size92 bold/center/max1; localized category headline with animal name uppercased current locale. Help line at y34+measured headline height+4,size34 white alpha230 center/max1.

Photo rectangle `(64,260)`–`(1016,860)`, corner radius36. Center crop scale `max(destW/srcW,destH/srcH)`, source width/height `trunc(destDim/scale)`, source origin integer half remaining dimensions. Draw filtered. Missing photo uses accent lightened per channel `channel + trunc((255-channel)*3/4)` and animal icon in `(420,440)`–`(660,680)`.

Reward>0: localized integer reward text uppercased, measured at bold44; pill width text+64, rectangle `(1080-64-width-20,800)`–`(1080-64-20,880)`, radius40, fill RGB255,194,51, text RGB58,42,0 at left+32 and centerY+16.

Details start y900: name fallback category headline, font64 bold RGB34,26,21 max1; then facts microchip localized yes/no/unknown (always first), nonblank color/breed, known sex, joined ` · `, y+6,font38 RGB82,68,60 max1. Next last-seen neighborhood and timestamp (lastSeenAt if positive else createdAt), y+10,font38 accent bold max2. Details use features if nonblank else description, y+10,font34 RGB60,50,44,max3. Add measured text heights plus respective gaps, matching Android truncation rather than resizing font.

Footer rectangle y1170–1350 RGB34,26,21. Contact uses phone only if selected+nonblank, email only if selected+nonblank, otherwise in-app username. At `(64,1194)`,font32 white bold center/max2. Footer caption at `(64,1296)`,font30 white alpha200 center/max1. Footer width952 when no id, else772 leaving QR space. Nonblank id generates QR128×128 margin2, black/white, positioned `(888,1210)`–`(1016,1338)`. Android QR contains PostLinks.create; web uses public web address when configured, otherwise Android scheme.

Android StaticLayout wraps and ellipsizes END at max lines, returns measured height. Center uses ALIGN_CENTER; other text uses ALIGN_NORMAL. Detail text direction is forced RTL when locale.language==ar (even Latin name/breed aligns on same Arabic edge); otherwise FIRSTSTRONG_LTR. Header/footer text direction defaults FIRSTSTRONG_LTR. Browser text measurement/Arabic shaping can differ, so render and visually compare against `app/src/test/screenshots/poster.png`.

## PostLinks

Id regex exactly `[A-Za-z0-9_-]{1,128}`. create validates then returns `catmap://post/<id>`. Android parse uses java.net.URI and rejects everything unless exact case-sensitive scheme catmap, rawAuthority post, no raw query, no raw fragment; rawPath exactly `/`+validId after removing one initial slash. Consequently reject encoded chars/segments, userinfo, ports, additional slashes/segments, empty ids, queries/fragments, other schemes/hosts. Browser `URL` normalizes path traversal and decodes/normalizes some authority details: validate the **raw string components** before using URL so `catmap://post/../admin` cannot normalize into an accepted id. HTTPS web links should be accepted only for configured/same-origin host and raw `/post/<validId>` with the same rejection rules, not arbitrary remote hosts.

## CommunityAccountCleanup order

Run while the authenticated user's account/profile still exists; rules may require those. Operations are awaited sequentially. Chunked bulk deletes use200 records per batch.

1. Collection-group updates where byUid==uid, restricted to exact four-segment path posts/<id>/updates/<id>. For each, transaction reads fresh update and parent post **before any writes**; if update exists delete it, and if parent exists decrement updatesCount with floor0. Reading both first follows Firestore transaction ordering.
2. Query authored posts ownerId==uid. For each delete all nested updates in batches200, then parent post. Not recursive deletion of unrelated subcollections.
3. Delete adoptionRequests where requesterId==uid, then where ownerId==uid.
4. Preserve bookings: where ownerId==uid blank ownerName and notes; where sitterId==uid blank sitterName and sitterNote. Preserve ids/status/dates/history.
5. Preserve authored reviews: blank authorName and text; retain authorId/rating/history. Reviews about the user's sitter profile are not deleted here.
6. Query chats array-contains members uid. Delete only messages sent by uid, batches200. Blank names.<uid>, photos.<uid>, set unread.<uid>=integer0. If lastSender==uid also blank lastText and lastSender; retain other user's messages and chat membership/history.
7. Delete sitters/<uid> if exists; delete areaFollows/<uid>.
8. Delete nested users/<uid>/favorites, blocked, private in batches200. Caller performs further account cleanup separately; this class does not itself delete auth account, user profile, photos or cats/sightings.

Do not silently alter the above ownership/history policy if emulator rules reject a web operation; report the exact incompatibility and leave the rules unchanged.

## UserDataExport

Filename is `catmap-my-data-<first eight SHA256(uid) bytes as 16 lowercase hex chars>.json`. Root `{schemaVersion:1,userId:uid,exportedAt:epochMillis,records:{...}}`. Snapshot removes top-level data/tokens/password; recursive maps remove tokens/password (nested data is retained), Timestamp becomes epoch milliseconds, Blob becomes null, recursive lists clean each item, document id is added/overwrites id. Never serialize photo bytes or push credentials. Pure JSON writer escapes quote/backslash/newline/CR/tab/control chars; nonfinite numbers become null; unsupported values become strings.

Fetch profile and users/<uid>/private/account as one-element lists even if document absent (snapshot still has id). Fetch sitters/<uid>, areaFollows/<uid>, volunteerApplications/<uid> as singleton if existing else empty.

Query and deduplicate by document id across each collection's fields, retaining first insertion order:

```text
posts: ownerId
sightings: userId
rescues: reporterId
checkins: userId
privateLocations: ownerId
cats: createdBy
maintenance: userId
contentReports: reporterId
mergeRequests: requestedBy
images: ownerId              (metadata only; data removed)
medicalRecords: byUid
adoptionRequests: requesterId, ownerId
bookings: ownerId, sitterId
reviews: authorId, sitterId
```

These queries use Android pages() to collect full paginated datasets, not a capped feed. Then fetch user notifications/favorites/blocked, chats containing member uid, and **all messages in those chats** (not just authored), adding chatId to each exported message. Fetch authored collection-group updates byUid==uid restricted to posts/<id>/updates/<id>, records key postUpdates. Records keys chats/messages are flat lists. Build JSON with current timestamp after queries. Browser downloads this JSON with the hashed filename; Android writes a cache share file and sends a FileProvider Intent. `clear` deletes cached export file only; browser should revoke temporary object URLs after use.

## Reference fixture guidance and available evidence

Existing tests read during research:

* `ai/PostAppearanceTest.kt`: three synthetic-vector tests for bounded similarity/malformed vectors, lost/found visibility/species ranking, averaging malformed filtering. Does not analyze bitmaps.
* `ai/SimilarityTest.kt`: seven synthetic tests: identity similarity, coats, distance, malformed vectors/nonfinite, averaging, HSV boundaries. Does not analyze bitmaps.
* `ai/RecognitionEvaluationTest.kt`: six tests for distinct identities, lookalikes, minimum dataset, skipped photos, proximity forgiveness, strength lead. Does not analyze bitmaps or real server embeddings.
* `data/PostDraftCodecTest.kt`: roundtrip/order, corruption/version, unsafe paths.
* `data/UserDataExportTest.kt`: schema/user escaping and nonfinite null.
* `util/PostLinksTest.kt`: roundtrip and rejects traversal, encoded slash, query/fragment, other scheme, userinfo, port, missing/extra segments.

Existing Android build result XML is available under `app/build/test-results/testDebugUnitTest/`. Headers for PostAppearanceTest (3), SimilarityTest (7), RecognitionEvaluationTest (6) report zero failures/errors/skips, timestamp 2026-10-13 approximately13:11 UTC. This is pre-existing evidence, **not a fresh test invocation by this research task** and not bitmap parity proof. Existing Robolectric visual PNGs in `app/src/test/screenshots/` include poster, light/dark post card, grid card, detail header, filters and icons. Existing VisualRegressionTest uses RobolectricTestRunner, SDK35, Application::class, en-rUS-mdpi, GraphicsMode.NATIVE. Build declares Robolectric4.17 and JUnit4.13.2.

Available real inputs: `tools/testdata/orange_tabby_1.jpg`, `orange_tabby_2.jpg`, `tuxedo_1.jpg`; `tools/seed/photos/` contains full+thumbnail jpgs for cat_orange, cat_tuxedo, kitten_grey, kitten_calico, dog_husky, dog_saluki, rabbit_white, bird_parrot. No `web/tests/fixtures/android-fingerprints.json` was present at research time.

Allowed new Kotlin test should use Robolectric native graphics, decode fixture images, scale upright analysis bitmap to512, then call `PostAppearance.describe`. For117 descriptor, call the private `centreWeights(int,int,FloatArray)` and `describe(IntArray,FloatArray,int,int)` methods with reflection; this avoids invoking ML Kit labeler/segmenter and keeps Android production code unchanged. Get pixels row-major from bitmap. Record filename, original/analysis/post dimensions, post27, cat117, coat/pattern/confidences/shares/hints and crop rectangle (when useful), in `web/tests/fixtures/android-fingerprints.json`. Use project-root discovery robust to Gradle working directory rather than assuming `.`. Any Gradle invocation must be followed by gradlew.bat --stop.

Web fixture test should analyze exactly matching source images through the same max512 processing path and max128 post path. For each component record abs delta, maximum and mean per-image/per-descriptor delta, cosine. Suggested brief acceptance bounds `max(absDelta)<=.03` and cosine≥.98; report measured numbers, rather than only a pass assertion. Stable synthetic RGB images and known HSV cutoff pixels are useful additional fixtures to isolate decoder/scaler issues. Cross-check exact lengths and classifier order; test all strict inequalities at boundary values, float32 rounding, Java Math.round vs Kotlin round, RGB grid order and zero-gradient orientation case. The latter yields orientation norm0/cosine0, so an identical solid image does not automatically produce texture1.
