# RemoteLink

RemoteLink is a consent-based Android app for securely connecting two phones so one phone can use the other phone as a remote camera device.

## Planned capabilities

- One APK with Controller and Camera Device roles
- Wi-Fi and mobile-internet connectivity
- QR code + 6-digit first-time pairing
- Persistent trusted-device identity for later connections
- Live camera + microphone via WebRTC
- Photo and video capture
- Front/rear camera switching
- Flash and zoom controls
- Screen sharing with Android MediaProjection consent
- Optional remote interaction through an explicitly user-enabled AccessibilityService
- Revoke access from either side
- Visible camera/microphone sharing state and foreground-service notification

## Phase 2 — pairing

This branch (`phase-2-pairing`) adds the first-time pairing bootstrap:

- SecureRandom-generated 6-digit pairing code
- QR payload containing a versioned pairing session and the Camera Device public identity
- QR scanning with CameraX + ML Kit Barcode Scanning
- Separate manual 6-digit verification after scanning
- Five-minute pairing-session expiry
- Persistent trusted-device records in Room
- Trusted-device list with Revoke and Connect actions
- Android Keystore-backed device identity carried into the pairing record

The trusted-device record is local to the Controller in this phase. The actual two-way network handshake, Controller approval on the Camera Device, and live connection are intentionally implemented with the signaling/WebRTC phase so the app never pretends a network connection exists when it does not.

## Security principles

RemoteLink must never bypass Android permissions, secretly capture camera/microphone/screen content, or silently enable accessibility control. Every sensitive capability must be explicitly enabled by the device owner.

A trusted pairing removes the need to repeat the QR/code pairing step, but it cannot override Android OS permission revocation, MediaProjection consent requirements, app reinstall, or other OS security changes.

The QR + code flow is a pairing bootstrap, not a substitute for the authenticated network handshake. The later signaling layer must mutually authenticate the stored public identities before allowing media/control sessions.

## Build

Open the repository in Android Studio with JDK 17 and let Gradle sync the project. The project targets Android API 35 and supports Android 8.0 (API 26) and above.

## Roadmap

1. Foundation and secure local identity — complete
2. QR + 6-digit pairing and trusted-device workflow — current
3. WebRTC camera/audio streaming and signaling
4. Remote camera controls and foreground service
5. Screen sharing and explicit remote-control service
6. Internet connectivity with STUN/TURN hardening
7. Testing, performance, security review and release builds
