# IPA Installer - iOS Wireless OTA Server for Android

A native Android application built with Kotlin and Jetpack Compose that acts as an automated local HTTPS server for installing pre-signed iOS applications (`.ipa`) directly onto an iPhone via Safari using Apple's iOS OTA (`itms-services://`) protocol.

## Features

- **Embedded Local HTTPS Server:**
  - Lightweight, high-performance embedded server running on port `8443`.
  - Byte-range streaming support for high-speed download and resume of large `.ipa` packages.
- **Dynamic TLS & iOS 13+ Compliance:**
  - Built-in dynamic X.509 Certificate Authority & Server Certificate generator powered by Bouncy Castle.
  - Generates 2048-bit RSA keys with SHA-256 signatures, `serverAuth` Extended Key Usage, and dynamic Subject Alternative Names (SAN) matching your Android device's local IP address and DNS name.
  - One-click Root CA installation via Apple Configuration Profile (`/ca.mobileconfig`) and standard X.509 certificate (`/ca.crt`).
- **Apple OTA Manifest Generator:**
  - Automatically parses `.ipa` ZIP archives to extract `CFBundleIdentifier`, `CFBundleShortVersionString`, and `CFBundleDisplayName` from `Payload/*.app/Info.plist`.
  - Emits 100% compliant Apple OTA XML Plist manifests (`/manifest.plist?app=<filename>`).
- **Modern Jetpack Compose UI:**
  - Real-time Wi-Fi and Hotspot network interface detector.
  - Server start/stop toggle with animated status indicators.
  - Integrated QR code display for instant iPhone camera scanning.
  - Native file picker to import `.ipa` files directly from device storage.
  - One-tap "Create Test IPA" generator to test the entire installation pipeline immediately.
  - Live server traffic console displaying HTTP requests, paths, and status codes.

## Endpoints

- `GET /` — Serves a responsive web landing page listing all available iOS apps with "Install on iPhone" buttons.
- `GET /manifest.plist?app=<filename>` — Returns the Apple OTA XML Plist configured with the dynamic IP, bundle ID, version, and download URL.
- `GET /download/<filename>` — Streams the pre-signed `.ipa` binary package with byte-range support.
- `GET /ca.mobileconfig` — Serves the Apple Configuration Profile for quick Root CA profile enrollment in iOS Settings.
- `GET /ca.crt` — Raw DER-encoded Root CA Certificate.
- `GET /icon.png` — Default app icon for the iOS installation prompt.

## iOS Setup (First Time)

1. Connect both Android and iPhone to the same Wi-Fi router or Android Hotspot.
2. In iPhone Safari, navigate to `https://<ANDROID_IP>:8443/` (or scan the on-screen QR Code).
3. Tap **Download CA Profile** &rarr; Tap **Allow**.
4. Open iPhone **Settings** &rarr; Tap **Profile Downloaded** &rarr; Tap **Install**.
5. Open iPhone **Settings** &rarr; **General** &rarr; **About** &rarr; **Certificate Trust Settings** &rarr; Enable full trust for **IPA Installer Local CA**.
6. Return to Safari and tap **Install on iPhone** to start the wireless installation.