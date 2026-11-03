# 3D Studio Pro — release artifacts

Build: **1.0.0 (versionCode 1)** · package `com.studio3dpro.app` · minSdk 28 · targetSdk 36

| File | Purpose |
|---|---|
| `3DStudioPro-1.0.0.apk` | Signed universal APK for direct install and device testing (3.2 MB) |
| `3DStudioPro-1.0.0.aab` | Signed Android App Bundle for Google Play (4.1 MB) |
| `samples/ATLAS_Studio_Demo.glb` | Validated 24-bone, 3-clip sample export for Unity/three.js |
| `SHA256SUMS.txt` | Integrity checksums for the three files above |

## Install the APK

```bash
adb install -r release/3DStudioPro-1.0.0.apk
```

Or copy the APK to the device and open it (allow install from unknown sources). The app requires an OpenGL ES 3.0 device running Android 8.0 or newer.

## Upload the AAB

Play Console → your app → Testing or Production → Create new release → upload `3DStudioPro-1.0.0.aab`. Enroll in Play App Signing and keep the local upload key safe.

## Verify

```bash
apksigner verify --print-certs release/3DStudioPro-1.0.0.apk
sha256sum -c release/SHA256SUMS.txt
```

Expected signing certificate SHA-256:

```
731b88b0ec5fe59d0da35e4fb5bd19c823de734c36eb4704d8ad600775e32fb7
```

## Sample GLB

`ATLAS_Studio_Demo.glb` validates with 0 errors and 0 warnings. Import it into Unity to confirm:

- a skinned mesh with a 24-bone armature;
- three clips (Walk Cycle 1.05 s, Run Cycle 0.62 s, Idle 2.8 s);
- PBR materials, including the emissive core and visor;
- no textures embedded (materials are factor-based).

The GLB is a demonstration export; the editable source scene is generated inside the app.

## Important

- **Back up** `keystore/studio3dpro-release.jks` and `keystore.properties` from the project root. Losing the upload key blocks future updates unless Play App Signing support resets it.
- Do not share the keystore or its password.
- FBX is not supported; the app discloses this in the import sheet and help.
