# ClosetOS Flutter

Cross-platform ClosetOS client for **Android** and **web**. Talks to the FastAPI backend in `../backend`.

## Prerequisites

- [Flutter SDK](https://docs.flutter.dev/get-started/install) (Dart 3.12+)
- Android Studio / Android SDK (for Android builds)
- A running backend — see [backend setup](../README.md#-backend-setup-python)

Verify your setup:

```bash
flutter doctor
```

## Setup

```bash
cd closetos_flutter
flutter pub get
```

### Local API URL

In debug mode the app calls your machine's backend. Update the IP in `lib/config/api_config.dart` to match your dev machine:

| Target | `baseUrl` in debug |
|--------|--------------------|
| Android emulator | `http://10.0.2.2:8000` |
| Physical Android device | `http://<your-lan-ip>:8000` (e.g. `http://192.168.29.193:8000`) |
| Chrome (web) | `http://localhost:8000` |

Start the backend from `../backend`:

```bash
cd ../backend
source venv/bin/activate   # if using a venv
python server.py
```

Release builds use `https://closet.adboardtools.com` automatically.

---

## Debug

### From the terminal

```bash
flutter run
```

Pick a device when prompted (Chrome, emulator, or connected phone). While the app is running:

| Key | Action |
|-----|--------|
| `r` | Hot reload |
| `R` | Hot restart |
| `q` | Quit |

Run on a specific device:

```bash
flutter devices                        # list targets
flutter run -d chrome                  # web
flutter run -d emulator-5554           # Android emulator
flutter run -d <device-id>               # physical device (USB or wireless ADB)
```

### From Cursor / VS Code

1. Open the `closetos_flutter` folder (or the repo root with the Flutter extension).
2. Select a device in the status bar.
3. Press **F5** or use **Run → Start Debugging**.

---

## Run

Quick commands for common targets:

```bash
# Android — USB or wireless ADB device
flutter run -d android

# Android emulator (start one from Android Studio first)
flutter emulators --launch <emulator_id>
flutter run -d emulator-5554

# Web (Chrome)
flutter run -d chrome
```

### Wireless Android debugging

On the phone: **Developer options → Wireless debugging → Pair device**.

On your Mac:

```bash
adb pair <ip>:<pairing-port> <pairing-code>
adb connect <ip>:<connect-port>
flutter devices    # confirm the phone appears
flutter run -d <device-id>
```

---

## Build

### Debug APK (fast, for local testing)

```bash
flutter build apk --debug
```

Output: `build/app/outputs/flutter-apk/app-debug.apk`

### Release APK

```bash
flutter build apk --release
```

Output: `build/app/outputs/flutter-apk/app-release.apk`

> Release builds currently sign with the debug keystore (`android/app/build.gradle.kts`). Configure a release keystore before shipping to production.

### App Bundle (Play Store)

```bash
flutter build appbundle --release
```

Output: `build/app/outputs/bundle/release/app-release.aab`

### Web

```bash
flutter build web --release
```

Output: `build/web/`

---

## Install / push to Android

### Option 1 — `flutter run` (recommended for dev)

Builds, installs, and launches in one step:

```bash
flutter run -d <device-id>
```

### Option 2 — `flutter install`

Install the last built APK onto a connected device:

```bash
flutter build apk --debug          # or --release
flutter install -d <device-id>
```

### Option 3 — `adb install`

```bash
adb devices
adb install -r build/app/outputs/flutter-apk/app-debug.apk
```

Use `-r` to replace an existing install. For release:

```bash
adb install -r build/app/outputs/flutter-apk/app-release.apk
```

### Troubleshooting

- **Device not listed** — enable USB debugging, accept the RSA prompt on the phone, or reconnect wireless ADB.
- **Cleartext HTTP blocked** — debug builds allow HTTP via `android:usesCleartextTraffic="true"` in `AndroidManifest.xml`.
- **Can't reach backend** — confirm `api_config.dart` uses the right IP and the backend is listening on `0.0.0.0:8000` (not only `127.0.0.1`).
- **Stale build** — `flutter clean && flutter pub get` then rebuild.
