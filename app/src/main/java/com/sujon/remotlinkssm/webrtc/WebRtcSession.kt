package com.sujon.remotlinkssm.webrtc

import android.content.Context
import org.json.JSONObject
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.EglBase
import org.webrtc.MediaConstraints
import org.webrtc.MediaStreamTrack
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoSource
import org.webrtc.VideoTrack

/** WebRTC media/session foundation. Signaling carries SDP/ICE only; media stays peer-to-peer. */
class WebRtcSession(
    context: Context,
    private val eglBase: EglBase,
    private val listener: Listener
) {
    interface Listener {
        fun onLocalDescription(description: SessionDescription)
        fun onLocalIceCandidate(candidate: PeerConnection.IceCandidate)
        fun onRemoteVideoTrack(track: VideoTrack)
        fun onConnectionState(state: PeerConnection.PeerConnectionState)
        fun onError(message: String)
    }

    private val factory: PeerConnectionFactory
    private val peerConnection: PeerConnection
    private var cameraCapturer: CameraVideoCapturer? = null
    private var cameraSource: VideoSource? = null
    private var audioSource: AudioSource? = null
    private var localAudioTrack: AudioTrack? = null
    private var localVideoTrack: VideoTrack? = null
    private var receiveTransceiversAdded = false
    private var closed = false

    init {
        val audioModule = org.webrtc.audio.JavaAudioDeviceModule.builder(context)
            .setUseHardwareAcousticEchoCanceler(true)
            .setUseHardwareNoiseSuppressor(true)
            .createAudioDeviceModule()

        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context)
                .setEnableInternalTracer(false)
                .createInitializationOptions()
        )

        factory = PeerConnectionFactory.builder()
            .setAudioDeviceModule(audioModule)
            .setVideoEncoderFactory(org.webrtc.DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true))
            .setVideoDecoderFactory(org.webrtc.DefaultVideoDecoderFactory(eglBase.eglBaseContext))
            .createPeerConnectionFactory()
        audioModule.release()

        val config = PeerConnection.RTCConfiguration(
            listOf(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer())
        ).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }

        peerConnection = requireNotNull(factory.createPeerConnection(config, observer)) {
            "Unable to create WebRTC peer connection"
        }
    }

    /** Controller-side: advertise that this peer wants to receive camera/microphone media. */
    fun prepareToReceiveRemoteMedia() {
        if (receiveTransceiversAdded) return
        peerConnection.addTransceiver(MediaStreamTrack.MediaType.MEDIA_TYPE_AUDIO)
        peerConnection.addTransceiver(MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO)
        receiveTransceiversAdded = true
    }

    fun addLocalMedia(context: Context) {
        if (cameraSource != null || audioSource != null || closed) return

        audioSource = factory.createAudioSource(MediaConstraints())
        localAudioTrack = factory.createAudioTrack("audio", audioSource)
        peerConnection.addTrack(localAudioTrack)

        val videoSource = factory.createVideoSource(false)
        cameraSource = videoSource
        val enumerator = Camera2Enumerator(context)
        val cameraName = enumerator.deviceNames.firstOrNull { enumerator.isFrontFacing(it) }
            ?: enumerator.deviceNames.firstOrNull()
            ?: run {
                listener.onError("No camera is available")
                return
            }
        val capturer = enumerator.createCapturer(cameraName, null)
            ?: run {
                listener.onError("Unable to create camera capturer")
                return
            }
        cameraCapturer = capturer
        val helper = SurfaceTextureHelper.create("RemoteLinkCamera", eglBase.eglBaseContext)
        capturer.initialize(helper, context, videoSource.capturerObserver)
        capturer.startCapture(1280, 720, 30)
        localVideoTrack = factory.createVideoTrack("video", videoSource)
        peerConnection.addTrack(localVideoTrack)
    }

    fun createOffer() {
        if (closed) return
        peerConnection.createOffer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(description: SessionDescription) {
                peerConnection.setLocalDescription(SimpleSdpObserver(), description)
                listener.onLocalDescription(description)
            }
            override fun onCreateFailure(error: String) = listener.onError("Offer failed: $error")
        }, MediaConstraints())
    }

    fun createAnswer() {
        if (closed) return
        peerConnection.createAnswer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(description: SessionDescription) {
                peerConnection.setLocalDescription(SimpleSdpObserver(), description)
                listener.onLocalDescription(description)
            }
            override fun onCreateFailure(error: String) = listener.onError("Answer failed: $error")
        }, MediaConstraints())
    }

    fun setRemoteDescription(type: SessionDescription.Type, sdp: String) {
        if (closed) return
        peerConnection.setRemoteDescription(
            object : SimpleSdpObserver() {
                override fun onSetFailure(error: String) = listener.onError("Remote SDP failed: $error")
            },
            SessionDescription(type, sdp)
        )
    }

    fun addRemoteIceCandidate(sdpMid: String?, sdpMLineIndex: Int, candidate: String) {
        if (!closed) peerConnection.addIceCandidate(PeerConnection.IceCandidate(sdpMid, sdpMLineIndex, candidate))
    }

    fun close() {
        if (closed) return
        closed = true
        runCatching { cameraCapturer?.stopCapture() }
        cameraCapturer?.dispose()
        cameraSource?.dispose()
        audioSource?.dispose()
        localVideoTrack?.dispose()
        localAudioTrack?.dispose()
        peerConnection.close()
        factory.dispose()
    }

    private val observer = object : PeerConnection.Observer {
        override fun onSignalingChange(state: PeerConnection.SignalingState) = Unit
        override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) = Unit
        override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit
        override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) = Unit
        override fun onIceCandidate(candidate: PeerConnection.IceCandidate) = listener.onLocalIceCandidate(candidate)
        override fun onIceCandidatesRemoved(candidates: Array<out PeerConnection.IceCandidate>) = Unit
        override fun onAddStream(stream: org.webrtc.MediaStream) = Unit
        override fun onRemoveStream(stream: org.webrtc.MediaStream) = Unit
        override fun onDataChannel(channel: org.webrtc.DataChannel) = Unit
        override fun onRenegotiationNeeded() = Unit
        override fun onAddTrack(receiver: org.webrtc.RtpReceiver, mediaStreams: Array<out org.webrtc.MediaStream>) {
            (receiver.track() as? VideoTrack)?.let(listener::onRemoteVideoTrack)
        }
        override fun onTrack(transceiver: org.webrtc.RtpTransceiver) {
            (transceiver.receiver.track() as? VideoTrack)?.let(listener::onRemoteVideoTrack)
        }
        override fun onConnectionChange(newState: PeerConnection.PeerConnectionState) = listener.onConnectionState(newState)
    }
}

private open class SimpleSdpObserver : org.webrtc.SdpObserver {
    override fun onCreateSuccess(description: SessionDescription) = Unit
    override fun onSetSuccess() = Unit
    override fun onCreateFailure(error: String) = Unit
    override fun onSetFailure(error: String) = Unit
}

object WebRtcPayload {
    fun sdp(description: SessionDescription): String = JSONObject()
        .put("type", description.type.canonicalForm())
        .put("sdp", description.description)
        .toString()

    fun ice(candidate: PeerConnection.IceCandidate): String = JSONObject()
        .put("sdpMid", candidate.sdpMid)
        .put("sdpMLineIndex", candidate.sdpMLineIndex)
        .put("candidate", candidate.sdp)
        .toString()
}
