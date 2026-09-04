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

## Phase 3 — WebRTC + signaling foundation

This branch (`phase-3-webrtc-signaling`) adds the real-time connection foundation:

- Current WebRTC Android SDK dependency from Maven Central
- WebRTC peer connection with Unified Plan
- Camera capture through Camera2 and a WebRTC video track
- Microphone capture through WebRTC audio track
- STUN configuration for initial ICE discovery
- SDP offer/answer handling
- ICE candidate handling
- Remote video-track callback for the Controller UI
- WebSocket signaling transport using OkHttp
- Versioned JSON signaling envelope
- Android Keystore-backed ECDSA signatures on signaling messages
- Public-key verification, recipient binding and short message-age validation
- Configurable `wss://` signaling endpoint; no signaling server is hard-coded

The signaling server is deliberately treated as a message relay. Live audio/video stays on the WebRTC media path and is not routed through the signaling service.

### Important Phase 3 limitation

The repository does not yet contain a deployed signaling backend, so this phase provides the Android WebRTC/signaling primitives but does not pretend that two phones are already connected. The next integration step is to wire the Controller and Camera Device flows to a real authenticated signaling server and then add TURN credentials for reliable connections across restrictive networks.

## Security principles

RemoteLink must never bypass Android permissions, secretly capture camera/microphone/screen content, or silently enable accessibility control. Every sensitive capability must be explicitly enabled by the device owner.

A trusted pairing removes the need to repeat the QR/code pairing step, but it cannot override Android OS permission revocation, MediaProjection consent requirements, app reinstall, or other OS security changes.

The QR + code flow is a pairing bootstrap, not a substitute for the authenticated network handshake. The signaling layer now has signed envelopes; the session integration must verify the stored public identity before accepting SDP/ICE messages.

## Build

Open the repository in Android Studio with JDK 17 and let Gradle sync the project. The project targets Android API 35 and supports Android 8.0 (API 26) and above.

## Roadmap

1. Foundation and secure local identity — complete
2. QR + 6-digit pairing and trusted-device workflow — complete
3. WebRTC camera/audio streaming and signaling foundation — current
4. Camera controls, photo/video capture and foreground service
5. Screen sharing and explicit remote-control service
6. Internet connectivity with TURN hardening and production signaling backend
7. Testing, performance, security review and release builds
