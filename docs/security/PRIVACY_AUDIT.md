# Photos and privacy audit (Phase 6, 20 Oct 2026)

What was checked for `docs/done/MAJOR_UPDATE_PLAN.md` Phase 6, what was found, and what changed. "Verified" means it was
run and observed; "read" means it was checked in the code only.

## Photo location data (EXIF)

* **Android: verified.** A 3000 × 2000 JPEG with GPS coordinates (25°12'17" N, 55°16'14" E) and a camera tag was
  put on the emulator and chosen as a profile photo in the staging app. The stored full photo (1280 × 853, 182 KB)
  and thumbnail (360 × 240, 13 KB) were read back from Firestore: **no EXIF block at all**, so no GPS and no camera
  tag. The reason is `ImageUtils.process`: it decodes, turns the photo upright and re-encodes it with
  `Bitmap.compress`, which writes a bare JPEG. Every upload (posts, sightings, chat, profile, rescue) goes through it.
* **Web: read, not run.** `web/src/util/images.ts` re-encodes through a canvas to `image/jpeg`, which also drops
  EXIF. `WEB_TODO.txt` already asks for a test.
* **AI jobs:** the photos sent to the PC are re-encoded bitmaps too (`AiServer.jpeg`).

## Photo type and size (rules, new)

* `images/{id}` must be a JPEG (the bytes start with `FF D8 FF`): both apps always re-encode, so anything else
  didn't come from them. A full photo stays under 1 MB, a thumbnail (`{id}_t`) under 200 KB, and `createdAt` can't be
  in the future. Rules tests: a 999 KB JPEG passes (the check fits the rules budget), and a 1 MB file, a 250 KB
  thumbnail, a PNG, random bytes, a future date and a false owner are refused.

## Thumbnails first

* Lists, cards, map previews and search results use the 360 px thumbnail (`RemoteImage` defaults to it). The full
  photo is loaded only in the photo pager of an open post, the cat profile header, the photo viewer and the
  "reunited" card.
* **Changed:** the photo strips on rescue reports and sighting pages, and photos in chat bubbles, loaded the full
  photo at a thumbnail's size. They now show the thumbnail, and tapping opens the full photo (chat photos weren't
  tappable before).

## Location privacy

* **Approximate posts:** the position is moved to the centre of a grid cell (about 220 m, or 500 m for "high"
  privacy) plus a small fixed offset before it is saved (`LocationPrivacy.fuzz`), so the exact spot never reaches
  Firestore. The offset is worked out from the account id and the exact position; knowing it only reveals the cell,
  which the saved position already shows.
* **Derived data uses the saved (blurred) position:** map-area codes (`geoCells`), the map's saved copy on the phone,
  "Directions" and distances.
* **Shares and posters** (`Share.kt`, `PosterMaker.kt`) contain no coordinates. **Alerts** name the area, never a
  position.
* **Sightings:** the public sighting and the cat profile hold the blurred position. The exact one is in
  `privateLocations/{id}`, readable by its owner **and by volunteers** (by design, so they can find the cat).
* **Rescue reports** keep about 10 m precision, readable only by the reporter, volunteers and moderators.

## Messaging privacy

* Messages and chat previews (`lastText`) are readable only by the two members. The message alert (with a
  120-character preview) goes into the recipient's own inbox, which only they can read.
* **Blocking:** a blocked person's messages were already refused. **New:** they also can't write any alert into the
  blocker's inbox (rules), and alerts are now written one per person, so one blocker never stops an alert to
  everyone else (a lost-pet alert to 300 followers used to be one batch).
* Reports send the reported text to moderators, which is needed to judge them.
* **Lock screen (Phase 7):** CatMap notifications are private; a locked phone shows only "CatMap Dubai" and the
  channel name (for example "Messages"), never the sender or the text.

## Account lifecycle

* **Account deletion (read, `AuthRepository.deleteAccount` + `CommunityAccountCleanup`):** posts and their updates,
  updates on other posts, sightings with their exact locations, check-ins, photos (detached from cats first), the
  person's chat messages (and their name and photo in the chat), adoption requests, rescue reports, maintenance and
  content reports, merge requests, the volunteer application, sitter profile, area alerts, favourites, blocks,
  private documents, alerts, the profile and username. Bookings and reviews keep the other person's history with
  the name and text removed. Then the sign-in account, then local data (drafts, export files, delivered-alert ids,
  demo password, photo cache and, new in Phase 4, the map's saved copy).
* **Data export** (`UserDataExport.kt`) includes the posts, sightings, rescues, updates and profile data of the
  account.
* **Logout:** clears the demo password and (Phase 4) the map's saved copies. **Phase 7:** the app then restarts and
  clears Firestore's offline cache (chats, inbox and everything else the account had read), the photo cache, shared
  exports and camera captures, and removes CatMap's notifications from the tray (`data/LocalWipe.kt`; verified on the
  emulator). Drafts stay: they are stored per account and come back when you sign in again.

## AI matching cost (checked)

* Photos already embedded are not embedded again (the worker keeps one embedding per photo, versioned; e2e check
  "photos aren't embedded again").
* Candidates are narrowed before comparing: same animal type, within 45 km, within the 60-day window, open posts only.
* The models load once per server start.
* **Changed:** the crops a phone sends for the "same animal" check are now at most 320 px (the model looks at 224 ×
  224), instead of whatever size the crop had, so a job with nine crops stays far below Firestore's 1 MB per
  document.
* The PC and on-device fallbacks are unchanged.
