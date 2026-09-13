# LCARS Provisions — native Fold scanner

Companion Android app for the home pantry on **LCARS LXC 106**. Live camera + ML Kit, not a copy of the web UI.

The food database stays on the container:

- UI in the browser: http://192.168.68.123/#pantry
- This app: live scan → `POST /api/pantry/action`
- Inventory: `GET /api/pantry`

No Krónan token in the APK. Name lookup (Krónan → Open Food Facts) happens on the server.

## Download APK

Sideload this on the Fold (not on Play Store):

**https://github.com/bjarkimg/lcars-app/releases/latest**

File: `lcars-provisions-debug.apk`. Android will warn it is not from Play — allow install from the browser/Files app, then allow **camera**.

## Why this repo exists

`lcars-fold` is the LAN console (browser + Python bridge). Live camera needs a **secure context**; the site is `http://192.168.68.123`. This app is native so the Fold can point-and-beep on the cover screen.

## First run (Android Studio)

Needs JDK 17+ and Android SDK (Studio’s default is enough).

```bash
cd ~/.xo/projects/lcars-app
# or: File → Open this folder in Android Studio
```

1. Open the folder in Android Studio.
2. Let Gradle sync.
3. Plug in the Fold (USB debugging).
4. Run **app** (`io.starfleet.lcars.app`).
5. Allow **camera**.
6. Pick **Búr** (or another bay) **before** pointing at a barcode.
7. Hear **Scanning complete** — the row is on the LCARS container.

Same LAN as `192.168.68.123`. If you change the LCARS IP, edit `PantryApi.DEFAULT_BASE`.

## Scan flow

1. Pick location (sticky after first pick).
2. Point the back camera at an EAN/UPC.
3. Debounce 1.5s on the same code so it doesn’t double-count.
4. Server logs qty + looks up the name.
5. Inventory list updates; newest on top.

**INTAKE** adds, **USE** subtracts. No-barcode: type a name and Done.

## Build APK from CLI (when JDK is installed)

```bash
./gradlew :app:assembleDebug
# app/build/outputs/apk/debug/app-debug.apk
```

## Not in this repo

- LCARS web UI / Hue / TV / games (`bjarkimg/lcars-fold`)
- Krónan access tokens
- Pantry JSON (lives at `/var/lib/lcars/pantry/` on LXC 106)
