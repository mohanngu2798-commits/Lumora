# Vannamayam — a free, private photo editor for Android

Working name; rename freely (see "Renaming" below). Every tool is free, there are no ads,
and the app has no internet permission: all editing happens on the device's GPU.

## Stack

| Layer | Choice | Why |
|---|---|---|
| Language / UI | Kotlin + Jetpack Compose (Material 3) | Modern Android default, fast to iterate |
| Rendering | OpenGL ES 3 fragment shaders | Real-time preview at 60fps, same pipeline renders full-res export |
| Edits | Non-destructive `EditParams` + undo history | Original is never touched; every change is undoable |
| Storage | Photo Picker in, MediaStore out | No storage permissions needed (minSdk 29 / Android 10+) |

## What works now (v0.1)

- Open from the photo picker, or "Share / Edit with Vannamayam" from any gallery app
- **Looks:** 10 original one-tap presets
- **Adjust:** exposure, contrast, highlights, shadows, saturation, vibrance, warmth, tint, fade, sharpen, vignette, grain
- **Curves:** master + R/G/B with draggable points (monotone cubic interpolation)
- **Crop:** aspect presets (original, 1:1, 4:5, 3:2, 16:9, 9:16), rotate 90°, flip
- Press-and-hold compare, unlimited-ish undo/redo (100 steps)
- Full-resolution export (up to the GPU limit, max 8192px) as JPEG to `Pictures/Vannamayam`

## Roadmap

1. **v0.2** Free-form crop with drag handles and straighten; look thumbnails; HSL per-colour mixer; save/reload edit sessions
2. **v0.3** Selective edits: radial and linear gradient masks, brush masks (mask texture fed to the same shader)
3. **v0.4** Healing / object removal (patch-based inpainting first; optional on-device ML model later), clarity / structure / dehaze (multi-pass blur shaders)
4. **v0.5** Text and overlays, frames, double exposure, RAW (DNG) support, export as PNG/WebP and quality settings
5. **Later** Layers, AI subject/sky selection (on-device TFLite/MediaPipe), batch editing

## Build and run

1. Install [Android Studio](https://developer.android.com/studio) (latest stable).
2. File → Open → this folder. Let it sync; accept any suggested AGP / dependency upgrades.
3. Plug in a phone with USB debugging on (or start an emulator) and press Run.

No Android Studio? Push this folder to GitHub: the included workflow (`.github/workflows/android.yml`)
builds a debug APK on every push and attaches it to the run.

## Publishing on Google Play — checklist

- [ ] **Developer account:** one-time $25 at <https://play.google.com/console>. New *personal* accounts must run a
      closed test with at least 12 testers for 14 days before production access.
- [ ] **Package name:** change `app.lumora.editor` in `app/build.gradle.kts` to a domain you own. It can't change after the first upload.
- [ ] **App name check:** search the Play Store and trademarks for your final name.
- [ ] **Signing:** create an upload key (Build → Generate Signed App Bundle) and enrol in Play App Signing. Back up the key; never commit it.
- [ ] **Build an AAB:** `./gradlew bundleRelease` (Play requires `.aab`, not `.apk`).
- [ ] **Target SDK:** keep `targetSdk` at the level Play currently requires (currently set to 36).
- [ ] **Privacy policy:** fill in `PRIVACY.md` and host it at a public URL (e.g. GitHub Pages).
- [ ] **Data safety form:** answer "no data collected or shared".
- [ ] **Content rating questionnaire** and **target audience**.
- [ ] **Store listing:** 512px icon, 1024×500 feature graphic, 2–8 phone screenshots, short and full description.

## Renaming

Change `app_name` in `res/values/strings.xml`, `rootProject.name` in `settings.gradle.kts`, the
`namespace`/`applicationId`, and the package folders under `app/src/main/java`.

## Code map

- `model/` edit description (`EditParams`, `Curves`, `Looks`) — plain Kotlin, unit-testable
- `gl/` shader pipeline (`Shaders`, `ImageRenderer`), live `PreviewView`, off-screen `Exporter`
- `data/PhotoStore` decode and save
- `ui/editor/` Compose screens, panels, curve editor, `EditorViewModel` (state + undo)
