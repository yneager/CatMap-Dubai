# CatMap Dubai: presentation notes

Short key points to build slides from. Each section is roughly one slide. Numbers marked *measured* were produced by
the project's own test tools; the others are design facts.

---

## 1. The problem

* Lost pets, street animals and rescue cases in Dubai are reported in scattered WhatsApp and Instagram groups.
* Reports get buried, there's no map, and nobody can tell whether two posts are about the same animal.
* People who want to help (feed, rescue, adopt, pet-sit) have no single place to find each other.

## 2. The solution: CatMap Dubai

* One community app for all of Dubai's animals: **lost, found, spotted, needs help, street animals and adoption**.
* A **live map** of every report, with filters, clusters and a 3D modern style.
* **Messaging**, **alerts** for lost animals near you, **pet sitting**, **rescue reports**, **feeding stations**,
  **badges**, and an **admin dashboard**.
* Fully in **English and Arabic** (right-to-left layout).
* Two apps on the same data: **Android**, plus a **web app for iPhone** (installable from Safari).

## 3. The headline feature: from lost to reunited

1. An owner posts a **lost** report with photos.
2. Anyone who sees the animal posts a **sighting**.
3. **AI compares the photos** and finds likely matches automatically, in both directions.
4. Both people get one alert, see the two photos **side by side**, and answer "might be mine / not mine".
5. They chat in the app, and the owner marks the pet **reunited**, crediting the person who helped.

* Nothing is merged or confirmed automatically: **a person always decides**.
* Labels are "strong possible / possible / weak", never a scary percentage.

## 4. The AI, in plain words

* **Is it an animal?** Every new photo is checked (CLIP model), and photos of people or objects are blocked.
* **Same animal?** A pet-recognition model (DINOv2 based) turns each photo into a "fingerprint" of 512 numbers, and
  similar fingerprints mean the same animal.
* It runs on the **owner's own PC** (free, uses its graphics card), with Firestore acting as a mailbox. When the PC
  is off, the phone falls back to a simpler on-device colour comparison.
* **Measured** on public pet datasets: the AI picks the right animal first about **95% of the time**, against
  **18%** for the simple colour method.
* Honest limit: accuracy on real CatMap photos isn't measured yet (no real users yet).

## 5. How it's built

* **Android:** Kotlin + Jetpack Compose. **Web:** React + TypeScript (PWA).
* **Backend:** Firebase (sign-in + Firestore database) on the **free plan**. No paid servers.
* **Maps:** MapLibre with free OpenFreeMap tiles.
* **Security** lives in the database rules, not in the app, so a modified app can't bypass it.
* **Updates:** signed APKs on GitHub Releases. The app checks the signature before installing an update.
* **Quality:** about 360 automatic security-rule checks, Android unit and screenshot tests, and web tests in two
  browsers.

## 6. The major update: security and cost (in progress)

**Why:** the app must be safe for strangers to use, and must stay **free** as it grows.

**Phase 1, the audit:** a full check of the code, the database rules, GitHub and the release process.
* No passwords or keys ever leaked in the public code history (all commits scanned).
* Real weaknesses were found and **proved with test attacks** on a test copy of the database: 13 out of 13 attacks
  worked.

**Phase 2, the fixes:** the database rules were rewritten.
* **11 of 13 attacks now blocked** *(measured)*. The other two are handled after the release, or are low risk.
* Examples: a suspended user could unsuspend themself; anyone could send fake "moderation" alerts; bots could
  download everything; one malformed post could crash the map for everyone. All fixed.
* Every fix has an automatic test, so it can't silently come back.
* Old app versions keep working: all of the original rule tests still pass.

**Phase 3, quick cost wins:** done (see the next section).

**Phases 4–10 (next):** make the map load only what's on screen, page the feeds, harden the Android app and the
release pipeline, then a full final review.

## 7. Cost: keeping it free

* The free plan allows **50,000 database reads a day** for the whole app.
* *Measured* before optimisation:
  * opening the app reads **133 documents today**, and about **1,350** once the community grows;
  * a hidden background check reads up to **2,400–9,600 per phone per day**;
  * so about 20 installed phones could use the whole free quota without anyone opening the app.
* **Phase 3, done:** the background check now asks only for new alerts.
  * **9,600 → 288 reads per phone per day** *(measured, busy inbox)*, about 97% less.
  * Photos stay saved on the phone for 30 days instead of 1, so they aren't downloaded again.
  * The AI server re-reads only what changed, instead of everything, every 50 minutes.
* **Next:** the map will load only the area on screen. That's the 1,350 reads per app open.

## 8. Privacy by design

* Photos are re-saved on the phone, which **removes GPS and camera data** before upload.
* Public locations can be **approximate**; exact sighting locations are visible only to volunteers.
* Email addresses and alert-area locations are **private** to their owner.
* Sign-in doesn't reveal whether an email has an account.
* "Download my data" and "Delete my account" are built in.

## 9. Demo plan (5 minutes)

1. Open the **map**: filters, the modern 3D style, and tapping a marker.
2. Post a **lost** report with a photo, and show the photo check blocking a non-animal photo.
3. Switch to a second demo account (Settings → Demo accounts) and post a **sighting** of the same cat.
4. Show the **"possible match"** alert, the side-by-side review and the chat.
5. Mark the pet **reunited**, and show the reunion on the map and in admin analytics.
6. Show **Arabic** in one tap.

Tip: start the AI server on the PC first (`cat-server\start.cmd`) and check that the app shows it online.

## 10. Likely questions and short answers

* **Why Firebase?** It's free to start, gives sign-in and a real-time database, and security is enforced on the
  server by rules.
* **What if the AI is wrong?** It only suggests. People confirm, and rejected pairs are never suggested again.
* **Can someone hack it with a modified app?** The rules check every read and write on the server, and the test
  attacks prove the main ones are blocked.
* **How does it scale?** Measured usage, a cost model for 100 to 10,000 daily users, and the optimisation phases
  (in progress).
* **Why not Google Play?** Direct signed releases from GitHub keep it free and fast to update. The app still checks
  every update's signature.
* **What's next?** Finishing the cost phases, real-phone testing, measuring the AI on real cat photos, and a full
  Arabic translation review.
