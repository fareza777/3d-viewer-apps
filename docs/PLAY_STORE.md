# Play Store submission — 3D Studio Pro 1.0.0

This is the release runbook for `com.studio3dpro.app`. The signed artifacts in `release/` are ready to upload; the remaining work is Play Console configuration, store graphics and a physical-device pass.

## 1. Artifact facts

| Field | Value |
|---|---|
| Package | `com.studio3dpro.app` |
| Version name / code | `1.0.0` / `1` |
| Min SDK | 28 (Android 8.0) |
| Target SDK | 36 (Android 16) |
| APK | `release/3DStudioPro-1.0.0.apk` (3.4 MB, universal) |
| AAB | `release/3DStudioPro-1.0.0.aab` (4.2 MB) |
| Native ABIs | `arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64` — only AndroidX `libandroidx.graphics.path.so`; all app logic is Kotlin/Java |
| Required hardware | OpenGL ES 3.0 (`<uses-feature android:glEsVersion="0x00030000" android:required="true">`) |
| Permissions | INTERNET, ACCESS_NETWORK_STATE (ads and billing), VIBRATE (haptics); no storage, camera or location |
| Signing cert SHA-256 | `73:1b:88:b0:ec:5f:e5:9d:0d:a3:5e:4f:b5:bd:19:c8:23:de:73:4c:36:eb:47:04:d8:ad:60:07:75:e3:2f:b7` |

Verify before every upload:

```bash
export JAVA_HOME="/path/to/jdk-17"
./gradlew :engine:test :app:assembleRelease :app:bundleRelease
apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk
```

If the certificate changes, that is a different upload key. Do not upload a build signed by an unknown key over an existing listing.

## 2. Signing and key custody

The local key was generated for this release:

- Keystore: `keystore/studio3dpro-release.jks` (PKCS#12, RSA 4096, alias `studio3dpro`, validity 10,000 days)
- Credentials: `keystore.properties` (root, git-ignored)
- `.gitignore` excludes both `keystore.properties` and `keystore/`

**Back up both files to a password manager and an offline encrypted archive.** This key is the upload key. Enroll in **Play App Signing** so Google holds the app-signing key and this keystore only authorizes uploads. If you lose the upload key, request an upload-key reset through Play Console support; if you have not enrolled in Play App Signing, losing it can make the listing unupdatable.

For future releases, keep the same key and increment `versionCode` in `app/build.gradle.kts`.

## 3. Console setup, in order

1. **Create app:** name “3D Studio Pro: 3D Model & Rig”, default language English (or Indonesian), app, free.
2. **App content:**
   - Privacy policy URL: host `docs/PRIVACY_POLICY.md` at a public HTTPS URL (GitHub Pages, Notion, your site). Play rejects a missing URL even for offline apps.
   - Ads: **Yes** — AdMob banner (under the viewport, above the mode dock), an interstitial after a finished export, and an opt-in rewarded ad in Settings.
   - App access: all functionality is available without login; no credentials to provide.
   - Content rating: complete the questionnaire. Answers: no violence, no sexual content, no profanity, no gambling, no user-generated content sharing, no location sharing, contains in-app purchases (one-time “Remove ads”). Expected rating: Everyone / 3+.
   - Target audience: 13+ (avoids Families policy for a professional creation tool).
   - Data safety: declare the AdMob SDK (device/advertising identifiers, approximate location from IP, ad interactions — collected for advertising, not shared with third parties by the developer) and the Play Billing purchase state. User files selected with the system picker are read and written on-device only and are never uploaded. Autosave stays in app-private storage.
   - Government app: No. News app: No. COVID-19: No.
   - Financial features: none.
3. **Store listing:** copy from section 4. Upload the icon, feature graphic and screenshots (section 5).
3b. **Monetization:** create an AdMob app + three ad units (banner, interstitial, rewarded) and paste the ids into `ads/AdConfig.kt` and the `com.google.android.gms.ads.APPLICATION_ID` meta-data; then add the in-app product with id `remove_ads`, type one-time, price **$4.99**, and activate it. A test-unit build fills with “Test Ad” creatives and cannot complete the purchase.
4. **Testing:** Internal testing → add your account → upload the AAB → install from the opt-in link on a physical device → run the smoke test in section 6. Then Closed testing, then Production.
5. **Pre-launch report:** review the device matrix. The GLES 3.0 requirement filters unsupported devices; if the report shows a `eglCreateContext` failure on a listed device, mark it unsupported rather than removing the feature flag.
6. **Publishing:** staged rollout 10% → 50% → 100%. Watch Android vitals for ANRs during first launch on low-end devices.

## 4. Store listing copy

### ASO naming

The launcher label stays short — **3D Studio Pro** (13 characters) — so it never truncates under an icon. The Play listing title adds the two highest-intent search terms within the 30-character limit:

**Play title:** `3D Studio Pro: 3D Model & Rig` (29 / 30 characters)

Search terms covered by the title, short description and first paragraph: *3D model, 3D modeling app, rig, rigging, animate, animation, character, glTF, GLB, Unity, Unreal, sculpt*. Keep those words in the first 160 characters of the full description; Play indexes the listing text, not in-app strings.

**What's new (1.0.0) — en-US:**

```
First release.
• Model, sculpt and UV-edit with PBR materials
• One-tap 24-bone humanoid auto-rig and weight painting
• Timeline, graph editor, layers, cycles and retargeting
• Export game-ready GLB with skeleton, skin and every clip
• New splash screen, first-run tour and light/dark themes
```

### Indonesian (id-ID)

**Nama aplikasi:** 3D Studio Pro: 3D Model & Rig

**Deskripsi singkat (≤80 karakter):**
`Studio 3D saku: model, rig, animasi, dan ekspor GLB untuk game.`

**Deskripsi lengkap:**

```
3D Studio Pro adalah studio 3D lengkap di Android — untuk melihat, memodelkan, merig, dan menganimasikan karakter tanpa perlu membuka laptop.

MODELING
• Edit vertex, edge, dan face dengan gizmo presisi
• Extrude, inset, bevel, subdivide, mirror, dan boolean
• Sculpt brush: draw, inflate, smooth, flatten, pinch
• Proyeksi UV box, silinder, dan bola
• Material PBR: metallic, roughness, emissive, opacity, dan tekstur

RIGGING
• Auto-rig humanoid 24 tulang sekali ketuk
• Weight painting dengan heat map dan normalisasi
• FK, IK dua-tulang, limit rotation, dan copy rotation
• Pose library untuk pose dasar dan pose buatan Anda

ANIMASI
• Timeline, keyframe, dan graph editor
• Layer animasi dengan weight dan additive blending
• Generator walk/run cycle dan idle yang bisa diedit
• Retargeting animasi dari rig Mixamo, Unity, atau Unreal
• Impor BVH untuk motion capture

FILE
• Impor GLB/glTF, OBJ, STL, BVH, dan proyek .studio3d
• Ekspor GLB siap Unity/Unreal: mesh, skeleton, skin, material, dan semua klip
• Ekspor OBJ, STL, BVH, dan PNG
• Undo/redo dan autosave

Dirancang untuk layar sentuh: orbit satu jari, pan dua jari, pinch untuk zoom, dan tata letak adaptif untuk ponsel maupun tablet. Semua diproses offline — tidak ada akun, iklan, atau pelacakan.

Catatan: FBX belum didukung. Gunakan GLB untuk memindahkan rig dan animasi ke Unity/Unreal.
```

### English (en-US)

**App name:** 3D Studio Pro: 3D Model & Rig

**Short description:**
`Pocket 3D studio: model, rig, animate, export game-ready GLB.`

**Full description:**

```
3D Studio Pro is a complete 3D studio for Android. View, model, rig and animate characters without opening a laptop.

MODELING
• Vertex, edge and face editing with precise gizmos
• Extrude, inset, bevel, subdivide, mirror and boolean operations
• Sculpt brushes: draw, inflate, smooth, flatten, pinch
• Box, cylindrical and spherical UV projection
• PBR materials: metallic, roughness, emissive, opacity and textures

RIGGING
• One-tap 24-bone humanoid auto-rig
• Weight painting with a heat map and normalisation
• FK, two-bone IK, rotation limits and copy-rotation constraints
• Pose library for stock and custom poses

ANIMATION
• Timeline, keyframes and a curve graph editor
• Animation layers with weight and additive blending
• Editable walk/run cycle and idle generators
• Retarget motion from Mixamo, Unity or Unreal rigs
• BVH import for motion capture

FILES
• Import GLB/glTF, OBJ, STL, BVH and .studio3d projects
• Export game-ready GLB for Unity/Unreal: mesh, skeleton, skin, materials and every clip
• Export OBJ, STL, BVH and PNG
• Undo/redo and autosave

Built for touch: one-finger orbit, two-finger pan, pinch to zoom, and a layout that adapts to phones and tablets. Everything runs offline — no account, ads or tracking.

Note: FBX is not supported. Use GLB to move rigs and animation into Unity/Unreal.
```

**Category:** Art & Design  
**Tags:** 3D modeling, animation, rigging, game development  
**Contact email:** your support address (must be monitored)  
**Website / privacy policy:** public HTTPS URL to the hosted policy

## 5. Graphics checklist

| Asset | Requirement | Status |
|---|---|---|
| App icon | 512×512 PNG, 32-bit, no transparency | `docs/store/icon-512.png` — generated from `icon.html` |
| Feature graphic | 1024×500 PNG/JPEG, no transparency | `docs/store/feature-graphic-1024x500.png` — generated from `feature-graphic.html` |
| Phone screenshots | 2–8, 320–3840 px, 16:9 to 9:16, no alpha | `docs/store/screenshots/` — 1080×1920 composed cards |
| 7" tablet screenshots | up to 8, same rules | Recommended; layout supports it |
| 10" tablet screenshots | up to 8 | Recommended |
| Promo video | optional YouTube URL | Not required |

Store screenshots are composed at 1080×1920 from real phone captures, with a headline above the phone frame. The raw captures are 1080×2400; they are scaled and cropped inside the card so the uploaded file sits inside Play's 9:16 limit. Regenerate with:

```bash
# capture on the connected phone, then
chrome --headless=new --window-size=1080,1920 \
  --screenshot=docs/store/screenshots/01-hero.png \
  "file:///.../screenshot-card.html?img=hero.png&kicker=...&title=..."
```

Suggested set: hero character in Object mode, Model mode with wireframe, Rig mode with skeleton overlay, Animate timeline, graph editor, Settings with theme tiles.

## 6. Physical-device smoke test before production

Install the AAB through the internal-testing link (not a sideloaded debug APK).

1. Cold start the app; confirm the studio appears and the demo rig is not distorted.
2. Orbit, pan, pinch and double-tap frame. Confirm 60 fps on the performance overlay at the default camera.
3. Object → Model: select a face, extrude, undo, redo.
4. Sculpt: draw and smooth, then undo.
5. Rig: tap Auto-Rig, select a bone, drag the IK target, paint weights for one bone.
6. Animate: play the Walk clip, scrub, insert a key, open the graph editor.
7. Import a GLB character from Downloads, then export GLB to Downloads.
8. Validate the exported file on a desktop with the Khronos glTF Validator and import it into Unity (confirm 24 bones, SkinnedMeshRenderer and three clips).
9. Rotate the device; confirm the tablet rail/phone dock layout and that the project survives.
10. Force-stop during an edit, relaunch, and confirm autosave recovery.

## 7. Known limitations to disclose in release notes

- FBX is not supported; use GLB for Unity/Unreal interchange.
- FBX-related buttons are intentionally absent; the import sheet explains the limitation.
- glTF PBR subset only: no normal/ORM textures, no morph targets.
- 48-bone skinning cap.
- The shipped renderer is a custom Kotlin/OpenGL engine, not libf3d (see README).

## 8. Versioning for the next release

1. Increment `versionCode`, update `versionName` in `app/build.gradle.kts`.
2. Re-run `:engine:test`, `:app:assembleRelease`, `:app:bundleRelease`.
3. Save new artifacts under `release/` and regenerate `SHA256SUMS.txt`.
4. Keep the same upload key; never commit `keystore.properties`.
