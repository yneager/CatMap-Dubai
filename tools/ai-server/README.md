# CatMap AI server (same-animal check)

Runs **pet-recognition-base** (open-noodle, Apache-2.0; DINOv2-base backbone + 512-d projection, ONNX) on this PC.
When someone posts a **street animal**, the app finds open street-animal posts of the same kind within 1 km, crops the
largest animal in each photo, and asks this server how alike they are. If it says "same" or "maybe", the app asks
the user. Confirming adds the photo to the existing post as a sighting, so there's no second post of the same animal.

If the server can't be reached, the app falls back to its on-device colour comparison and says so. Posting never
waits on the PC.

## Start it (every time)

1. Plug the phone into the PC by USB, with **USB debugging** on. You turn this on yourself: Settings → About phone →
   tap Build number 7 times → Developer options → USB debugging.
2. In the project folder, run:

   ```powershell
   .\tools\ai-server\start.ps1
   ```

   * The first run creates `tools/ai-server/.venv` and downloads the model (about 350 MB) into
     `tools/ai-server/models`. Both are git-ignored.
   * It runs `adb reverse tcp:8765 tcp:8765`, so the phone's `127.0.0.1:8765` reaches this PC through the cable.
   * Re-run `adb reverse tcp:8765 tcp:8765` if you unplug the phone.
3. In the app, open **Admin → AI server → Test connection**. It should say
   `pet-recognition-base · DmlExecutionProvider` (your GPU) or `CPUExecutionProvider`.

`-Emulator` runs against the local Firebase emulators (it accepts the emulator's unsigned tokens). `-Cpu` skips the
GPU.

## Security

* The server listens on `127.0.0.1` only. Nothing on the network can reach it; the phone uses the USB cable.
* Every `/compare` and `/embed` call must carry a **Firebase ID token** for `catmap-dubai`, from a verified account.
  It's checked against Google's public keys, so no service-account key is needed.
* Requests are limited to 60 per 10 minutes per account. Images must be JPEG/PNG/WebP under 1.5 MB and at most
  10 candidates per call.
* The server never reads or writes Firestore. The user confirms in the app, and the app writes through the normal
  security rules, so a forged answer can only change what that user's own phone shows.
* The app allows plain HTTP to `127.0.0.1` / `localhost` only (`res/xml/network_security_config.xml`).

A public tunnel (to use the server without a cable) was **not** set up, because it would expose this PC to the
internet. The app can use any `https://` address set in Admin → AI server, if you decide to run one yourself later.

## Thresholds

Defaults: `same` ≥ 0.75 and `maybe` ≥ 0.60 cosine similarity. These are provisional.

* The model's own clustering sweep suggests about 0.5.
* Uncropped photos of two different kittens scored 0.57 in testing.

**Admin → Recognition test** measures the model on your labelled photos when the server is running. Use it to pick
the values, then set them before starting the server:

```powershell
$env:SAME_THRESHOLD="0.7"; $env:MAYBE_THRESHOLD="0.55"; .\tools\ai-server\start.ps1
```

## API

* `GET /health`: model, provider and thresholds.
* `POST /compare`: multipart `query` (one image), `candidates` (up to 10), and `ids` (a JSON list). Returns
  `{results:[{id, similarity, verdict}]}`.
* `POST /embed`: multipart `images` (1–16). Returns unit-vector embeddings; the Recognition test uses this.
