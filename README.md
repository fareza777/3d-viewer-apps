# 3D Studio Pro

A mobile 3D viewer, modeller, rigging tool and animation studio for Android. It runs on-device: no account, no cloud sync and no telemetry. The only network traffic is ad serving (Google AdMob); scenes, rigs and exports never leave the phone.

- **Package:** `com.studio3dpro.app`
- **Version:** 1.0.0 (versionCode 1)
- **Minimum Android:** 8.0 / API 28, OpenGL ES 3.0 required
- **Target:** Android 16 / API 36
- **Artifacts:** `release/3DStudioPro-1.0.0.apk`, `release/3DStudioPro-1.0.0.aab`
- **Sample export:** `release/samples/ATLAS_Studio_Demo.glb` (24-bone skinned character, three clips)

## What is implemented

### App shell and brand
- Native Android 12+ splash (brand mark on ink) that hands over to an ink loading surface — no white flash on either theme.
- Three-page first-run tour with a live orbit/cube/rig/export illustration, Skip, and two real start choices: the rigged ATLAS demo or an empty scene. The choice is stored and survives process death.
- Settings sheet: appearance (System / Light / Dark with immediate, persisted switching — the 3D canvas, grid, axes and canvas overlay chips follow the theme), canvas overlays, autosave, haptics, export defaults, replay onboarding, reset demo, clear autosave, and an About sheet with build facts and version.
- Adaptive launcher icon (vector), round icon, monochrome layer, and matching in-app mark. Store assets live in `docs/store/`.

### Viewport and rendering
- OpenGL ES 3.0 renderer with GGX specular, hemispheric ambient, three-light studio rig, ACES tonemapping, emissive materials and base-colour textures.
- GPU linear-blend skinning, up to 48 bones per mesh, four influences per vertex.
- Perspective/orthographic camera, orbit/pan/pinch multitouch, double-tap framing, axis dial, gizmos and transform presets.
- Theme-aware canvas: the background is a shader gradient that renders ink in the dark theme and warm paper in the light theme, with matching grid and axis colours.
- Grid, contact shadow, wireframe overlay, skeleton overlay, bone heat-map overlay and viewport PNG capture.

### Modelling
- Object mode and vertex/edge/face edit mode with picking, marquee-free tap selection and X-ray wireframe.
- Move / rotate / scale gizmos; numeric transforms in the inspector.
- Extrude, inset, bevel, Catmull–Clark subdivide, delete, mirror, weld.
- Boolean union, difference and intersection (BSP CSG).
- Sculpt brushes: draw, inflate, smooth, flatten, pinch.
- Box, cylindrical and spherical UV projection; UV readout.
- PBR material editor with presets, opacity, metallic/roughness, emissive strength and texture import.
- Primitives, duplicate, rename, delete, visibility.

### Rigging
- One-tap 24-bone humanoid auto-rig fitted to the selected mesh (23 deform bones plus a Root control) using Unity-style `.L/.R` naming.
- Bone rest editor, FK posing, two-bone IK with draggable target, rotation limits and copy-rotation constraints.
- Automatic distance/falloff weights with Laplacian smoothing, four-influence compression, weight painting with heat map and normalisation.
- Pose library: T-pose capture, Wave preset and user poses.

### Animation
- Multiple clips per scene, play/pause/loop, scrub, frame-accurate playhead.
- Per-bone translation/rotation/scale keys, linear/step/Bezier interpolation.
- Timeline track rows with key diamonds and a draggable graph editor for value curves.
- Animation layers with weight, mute and additive blending.
- Procedural walk and run cycle generators plus an idle breathing clip, all editable afterwards.
- Pose-library blending, animation retargeting by bone-name matching (Mixamo, Unity, Unreal conventions) and BVH motion import.

### Files and workflow
- Import: GLB, glTF (`.gltf` + external/binary buffers), OBJ, STL, BVH, `.studio3d` projects, base-colour textures.
- Export: GLB (mesh, skeleton, skin, PBR materials, embedded textures, all clips), OBJ, binary STL, BVH, `.studio3d`, PNG.
- **FBX is not supported.** The bundled open-source stack has no FBX SDK; the import sheet says so explicitly instead of shipping a disabled fake button.
- Undo/redo for geometry and scene edits, autosave to app storage, crash-safe project snapshots, new/import/open flows.
- Adaptive layout: bottom mode dock on phones, left mode rail on tablets/landscape.

## Monetization

Ads are served by Google Mobile Ads (AdMob); purchases use Google Play Billing. All ids live in `app/ads/AdConfig.kt` and the manifest meta-data.

| Format | Where | Notes |
|---|---|---|
| Banner | Its own row directly **above** the bottom mode dock (and above the system navigation bar on tablet/landscape) | Reserved layout space, not an overlay, so it cannot cover the mode buttons, viewport controls or Android's navigation buttons. Hidden on the splash, tour and settings screens. |
| Interstitial | After a finished export only | 3-minute cooldown, skipped on the first action of a session, never during an editing gesture. |
| Rewarded | Settings → Ads & rewards, opt-in | Grants 30 minutes with no banner and no interstitial. |
| Remove ads | Settings → Ads & rewards, one-time IAP | Product id `remove_ads`, **$4.99**. Purchase is acknowledged immediately and restored from Play on launch. |

The shipped ids are Google's **test** units, so every format fills with a "Test Ad" creative and nothing is billed. Before publishing, replace the four ids in `AdConfig.kt` and `com.google.android.gms.ads.APPLICATION_ID` in `AndroidManifest.xml` with the real AdMob app + unit ids, and create the `remove_ads` in-app product in Play Console at $4.99. The price label is read from Play and falls back to `$4.99`.

Play Billing only exists for installs that Play recognises. On a sideloaded APK the purchase button reports that billing is unavailable and ads keep working; it does not fake a purchase. For EEA/UK traffic, set up a UMP consent message in the AdMob console before release.

## Architecture

```
app/     Jetpack Compose UI, OpenGL ES 3.0 renderer, view-model, SAF file I/O
engine/  Pure Kotlin scene graph, mesh/CSG/sculpt, skinning, IK, animation, importers/exporters
```

`engine` has no Android dependency, so the geometry, rigging, animation and file-format code is covered by fast JVM tests. The renderer lives in `app/render/SceneRenderer.kt`; the demo character is generated at runtime in `engine/DemoScene.kt`.

The only native code in the APK is AndroidX's `libandroidx.graphics.path.so` (about 10 KB per ABI), pulled in by Compose path rendering. All application logic is Kotlin/Java. The bundle contains `arm64-v8a`, `armeabi-v7a`, `x86` and `x86_64`; Play delivers only the matching slice.

The character skin is built from a signed-distance field and meshed with surface nets (`engine/mesh/ImplicitSurface.kt`). This replaced stacked primitive shells, which produced visible interpenetration fringes at the shoulders, waist and knees.

### Upstream F3D note

The brief named [F3D](https://github.com/f3d-app/f3d) / [f3d-android](https://github.com/f3d-app/f3d-android) as the rendering foundation. **This build does not embed libf3d.** f3d-android cross-compiles libf3d and its C++ dependency stack for Android inside Docker; that native toolchain was not available in this workspace, and adding an unbuilt native dependency would have produced an app that could not be verified or shipped. The shipped viewer/editor is therefore a self-contained Kotlin + OpenGL ES engine, not a libf3d wrapper. F3D remains the reference for a future native renderer module.

## Build

Requirements: JDK 17, Android SDK 36, Android build-tools 36, the Gradle wrapper.

```bash
export JAVA_HOME="/path/to/jdk-17"

./gradlew :engine:test                  # 35 JVM tests
./gradlew :app:assembleDebug            # app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:assembleRelease          # signed APK
./gradlew :app:bundleRelease            # signed AAB for Play
```

Release signing is configured through `keystore.properties` in the repository root:

```properties
storeFile=keystore/studio3dpro-release.jks
storePassword=…
keyAlias=studio3dpro
keyPassword=…
```

`keystore.properties` and `keystore/` are git-ignored. **Back up the keystore and password now.** Losing the upload key means you cannot update the listing unless Play App Signing key reset is approved. For a public release, prefer your own upload key and enroll in Play App Signing; the certificate currently used for the artifacts in `release/` has SHA-256 fingerprint `73:1b:88:b0:ec:5f:e5:9d:0d:a3:5e:4f:b5:bd:19:c8:23:de:73:4c:36:eb:47:04:d8:ad:60:07:75:e3:2f:b7`.

## Verification

| Check | Result |
|---|---|
| Engine unit tests (7 suites) | 36 passed, 0 failed (re-run 3 Nov after repairing a corrupted Gradle transform cache on the build machine) |
| Demo skin topology | 10,800 vertices / 10,798 quads, one component, 0 boundary edges, 0 non-manifold edges, consistent winding |
| GLB export (Khronos glTF Validator 2.0.0-dev.3.10) | 0 errors, 0 warnings, 9 informational (unused TEXCOORD) |
| GLB runtime load (three.js) | OK — 24 bones, 3 clips (Walk 1.05 s, Run 0.62 s, Idle 2.8 s) |
| GLB content | 28,080 triangles, 9 draw calls, 4 skin influences, materials + emissive extension |
| Release APK signature | `apksigner verify` OK, v1/v2/v3 scheme as built by AGP |
| Release UI | Splash, first-run tour (all three pages and both start choices), empty-scene start, demo start, theme switching and About exercised on a physical Redmi. A second sweep on 3 Nov drove every editor mode (Object/Model/Sculpt/Rig/Animate), the timeline, wiring overlays, all five sculpt brushes, the Add-geometry sheet, New project, the Import picker, the full export sheet (GLB/OBJ/STL/BVH/.studio3d/PNG + FBX notice), Save snapshot, Help, About, and Light/Dark switching; 47-61 FPS in the HUD |
| Release performance | 59–60 FPS at the default camera with ATLAS (41K triangles in the HUD); 52–60 FPS with the Animate timeline open |
| Store assets | 512 icon, 1024×500 feature graphic and four 1080×1920 screenshots in `docs/store/` |
| Monetization build | Release APK built with play-services-ads 23.6.0 + billing 7.1.1, installed over the previous release on a Redmi (Android 13). Banner renders in its own row above the dock with no overlap in portrait **and** in landscape (where the adaptive request needed a 320x50 fallback after AdMob returned `ERROR_CODE_INVALID_REQUEST`); interstitial and rewarded test creatives serve; the rewarded ad granted the 30-minute ad-free window and the banner disappeared while it was active; remove-ads row shows $4.99 and reports the missing Play listing on a sideloaded install |

The emulator used for early QA has software GL and reports 3–9 FPS; that number is not representative. Hardware frame times above were read from the in-app HUD on the Redmi.

## Known limits

- No FBX import or export.
- Skinning is capped at 48 bones; clips longer than 60 s are untested on low-end devices.
- glTF PBR subset only (base colour, metallic, roughness, emissive, opacity); no normal/ORM maps or morph targets yet.
- No cloud sync, collaboration or asset store.
- Ads use Google test unit ids until the publisher's AdMob ids are substituted; ad revenue does not accrue in this build.
- Undo/redo is wired for sculpt strokes, gizmo drags, weight painting and scene edits, but the 3 Nov automated sweep could not land a tap on the toolbar button reliably (headless `input tap` kept missing the 105 px icon row), so it was verified by code review only, not by a screenshot pair.
- After a sculpt stroke, choosing **New project → Rigged demo scene** can leave the sculpted mesh on screen until the process restarts. The scene data is replaced (a restarted app loads a clean demo), so the stale geometry is in the viewport/cache path rather than in the saved scene.
- The $4.99 remove-ads purchase can only be completed on an install delivered by Google Play (internal testing track or production); a sideloaded APK cannot reach the billing service.
- The demo character is procedural, not a production character asset.
- libf3d is not linked (see the upstream note above).

## Documentation

- [Play Store submission guide](docs/PLAY_STORE.md)
- [Privacy policy template](docs/PRIVACY_POLICY.md)
- [QA report](docs/QA_REPORT.md)
- [Store assets and regeneration](docs/store/README.md)
- [Third-party notices](THIRD_PARTY.md)
