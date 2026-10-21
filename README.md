# CatMap Dubai

**A free community app that helps Dubai's animals: find lost pets, help street cats, and find homes for animals
that need one.** It works in English and Arabic, on Android, and on iPhone as a web app.


## What you can do with it

* **Report a lost pet, or an animal you found or spotted.** Your post goes on a live map of Dubai with photos, the
  area it was last seen in, and an optional reward. People who follow that area get an alert.
* **Get help finding your pet.** When someone posts a sighting, CatMap compares the photos with lost reports
  nearby and tells both of you if it might be the same animal. You can look at the photos side by side, chat, and
  mark your pet as reunited.
* **Look after street cats.** Record sightings of the cats in your neighbourhood. The app recognises cats it has
  seen before, keeps a profile for each one (health, neutering, where it was seen), and lists feeding stations you
  can check in at.
* **Ask for urgent help.** Report an injured or sick animal so volunteers and rescuers can pick it up.
* **Adopt or rehome.** Browse animals looking for a home and send an adoption request, or list one yourself.
* **Find a pet sitter.** Sitters list their services and rates; you book, and leave a review afterwards.
* **Search and explore.** Search every post by name, breed, colour or area, filter by animal and distance, or
  switch the map to a list.
* **Share.** Send a post to WhatsApp or print a "lost pet" poster with a QR code.

## Get the app

* **Android:** download `CatMap-<version>.apk` from the
  [latest release](https://github.com/yneager/CatMap-Dubai/releases/latest) and open it (your phone will ask you to
  allow installing apps from your browser or file manager). After that, the app updates itself: it checks for new
  versions and installs them when you agree.
* **iPhone:** open [catmap-dubai.web.app](https://catmap-dubai.web.app) in Safari, tap **Share → Add to Home
  Screen**, and open CatMap from your home screen.

You need an account with a verified email address to post, chat or see other people's posts.

## Your privacy

* Photos are re-saved by the app before upload, which removes the hidden location and camera details that phones
  add to pictures.
* You can choose to show only an approximate location on a post; the exact spot is never stored.
* Messages can only be read by the two people in the chat, and you can block or report anyone.
* You can download all your data, and delete your account and everything you posted, from **Settings**.
* Logging out clears your data from the phone.

## What's new in 0.1.0

A big update focused on safety and speed:

* Much stricter security rules on the server, so nobody can change other people's posts, points or cats, fake
  alerts, or upload anything except photos.
* The map loads only the part of Dubai you're looking at and remembers what it already has, so it opens faster and
  uses far less data.
* Search now covers every post, not only the newest ones, and lists load as you scroll.
* Safer updates: the app only installs updates signed by CatMap and downloaded from this project.
* Logging out now clears your data from the phone, and message previews are hidden on the lock screen.

## Free to run

CatMap runs on Firebase's free plan. Two extras are switched off in this version because they need the paid plan:
videos on posts, and instant push alerts while the app is closed (alerts still arrive within about 15 minutes, and
straight away while the app is open).

## For developers

* **Set up your own copy:** [SETUP.md](SETUP.md) (about 15 minutes, or try it on local emulators with demo data).
* **How the code is organised and the current state of the project:** [HANDOFF.md](HANDOFF.md).
* **Web app:** [web/README.md](web/README.md).
* **AI matching:** [docs/MATCHING.md](docs/MATCHING.md) (design) and [docs/AI_EVALUATION.md](docs/AI_EVALUATION.md)
  (measured accuracy).
* **Costs and privacy:** [docs/cost](docs/cost) and [docs/security](docs/security). Reporting a security problem:
  [SECURITY.md](SECURITY.md).

Built with Kotlin and Jetpack Compose, Firebase (Firestore and Authentication), MapLibre with OpenFreeMap maps, and
an optional AI server (`cat-server`, not in this repository) that runs the photo-recognition models.
