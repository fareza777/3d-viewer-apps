# Store graphics — 3D Studio Pro

All images are generated locally from the app and from the HTML templates in this
folder. Nothing here is stock art or AI-generated imagery.

## Files

| File | Size | Use |
|---|---|---|
| `icon-512.png` | 512×512 | Play Console app icon (full-bleed, 32-bit, no alpha) |
| `feature-graphic-1024x500.png` | 1024×500 | Play Console feature graphic |
| `screenshots/hero.png` | 1080×1920 | Play phone screenshot 1 — Object mode, ATLAS |
| `screenshots/model.png` | 1080×1920 | Play phone screenshot 2 — Model mode, selected face |
| `screenshots/rig.png` | 1080×1920 | Play phone screenshot 3 — Rig mode, skeleton |
| `screenshots/animate.png` | 1080×1920 | Play phone screenshot 4 — Animate timeline |
| `raw/*.png` | 1080×2180 | Cropped phone captures used inside the cards |
| `icon.html` | source | Brand icon (isometric cube, orbit, joint gem) |
| `feature-graphic.html` | source | Feature graphic layout and copy |
| `screenshot-card.html` | source | Screenshot card with `?id=hero|model|rig|animate` |

The in-app adaptive icon is the vector pair
`app/src/main/res/drawable/ic_launcher_foreground.xml` +
`ic_launcher_background.xml`, plus `ic_launcher_monochrome.xml` for Android 13
themed icons and `ic_splash_mark.xml` for the launch screen. Keep those in sync
with `icon.html` when the mark changes.

## Regenerate the icon and feature graphic

```bash
CHROME="/c/Program Files/Google/Chrome/Application/chrome.exe"

"$CHROME" --headless=new --disable-gpu --hide-scrollbars \
  --window-size=512,512 \
  --screenshot="docs/store/icon-512.png" \
  "file:///.../docs/store/icon.html"

"$CHROME" --headless=new --disable-gpu --hide-scrollbars \
  --window-size=1024,500 \
  --screenshot="docs/store/feature-graphic-1024x500.png" \
  "file:///.../docs/store/feature-graphic.html"
```

## Regenerate the screenshots

1. Install the release APK on a phone, set the theme to Dark, and open the ATLAS
   demo.
2. Capture 1080×2400 frames with `adb exec-out screencap -p`:
   - Object mode, nothing selected (hero)
   - Model mode with a face selected
   - Rig mode with a bone selected
   - Animate mode with the timeline open
3. Crop the status bar with `build/qa/crop_store.ps1` (0,96 → 1080×2180) into
   `docs/store/raw/`.
4. Render each card:

```bash
for id in hero model rig animate; do
  "$CHROME" --headless=new --disable-gpu --hide-scrollbars \
    --allow-file-access-from-files --virtual-time-budget=2500 \
    --window-size=1080,1920 \
    --screenshot="docs/store/screenshots/$id.png" \
    "file:///.../docs/store/screenshot-card.html?id=$id"
done
```

The cards are exactly 9:16, which is inside Play's accepted 9:16–16:9 range.
Tablet screenshots are still optional and can reuse the same template at
1600×2560 once a tablet capture exists.
