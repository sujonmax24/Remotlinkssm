# RemoteLink Architecture

## Roles

The same APK can operate as either:

- **Controller:** discovers trusted devices and sends camera/screen/control commands.
- **Camera Device:** exposes explicitly approved camera, microphone and screen capabilities.

## Media path

WebRTC is planned for real-time audio/video. Firebase or another signaling backend will only exchange connection metadata; live media should not be routed through Firestore.

STUN/TURN will be used so the two phones can connect across different networks. TURN credentials must be short-lived and scoped.

## Pairing

First connection:

1. Camera Device creates a pairing session.
2. Controller scans the QR code or enters the 6-digit code.
3. Both devices authenticate the pairing session.
4. Public-key device identities are exchanged.
5. The trusted device record is stored locally.

Later connections use the stored trusted identity instead of repeating QR/code pairing. OS permissions remain authoritative.

## Local security

The device identity private key is generated in Android Keystore. The Room database stores trusted-device metadata and public keys, not private keys.

## Screen sharing

Screen capture will use Android MediaProjection. The app must request the operating system's user consent for each capture session where Android requires it; trusted pairing cannot bypass that consent.

## Remote interaction

If remote interaction is implemented, it will use an explicitly user-enabled AccessibilityService and will never attempt to enable the service silently.

## Foreground service

Camera/microphone sharing will run through an appropriate foreground service with a visible notification and the required Android foreground-service permissions/types.
