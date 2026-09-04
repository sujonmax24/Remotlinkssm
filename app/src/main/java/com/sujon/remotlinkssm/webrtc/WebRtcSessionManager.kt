package com.sujon.remotlinkssm.webrtc

import android.content.Context
import android.provider.Settings
import com.sujon.remotlinkssm.data.local.TrustedDeviceEntity
import com.sujon.remotlinkssm.domain.model.DeviceRole
import com.sujon.remotlinkssm.security.DeviceKeyManager
import com.sujon.remotlinkssm.signaling.SignalingClient
import com.sujon.remotlinkssm.signaling.SignalingConfig
import com.sujon.remotlinkssm.signaling.SignalingListener
import com.sujon.remotlinkssm.signaling.SignalingMessage
import com.sujon.remotlinkssm.signaling.SignedSignaling
import org.json.JSONObject
import org.webrtc.EglBase
import org.webrtc.PeerConnection
import org.webrtc.SessionDescription
import org.webrtc.VideoTrack
import java.util.UUID

/** Coordinates authenticated signaling and one WebRTC session. */
class WebRtcSessionManager(
    private val context: Context,
    private val role: DeviceRole,
    private val trustedDevice: TrustedDeviceEntity,
    private val eglBase: EglBase,
    private val listener: Listener,
    private val signalingEndpoint: String = SignalingConfig(context).endpoint
) {
    interface Listener {
        fun onConnecting()
        fun onConnected()
        fun onApprovalRequired()
        fun onRemoteVideoTrack(track: VideoTrack)
        fun onConnectionState(state: PeerConnection.PeerConnectionState)
        fun onError(message: String)
        fun onEnded()
    }

    private val localDeviceId = Settings.Secure.getString(
        context.contentResolver, Settings.Secure.ANDROID_ID
    )?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
    private val keyManager = DeviceKeyManager()
    private val signed = SignedSignaling(localDeviceId, keyManager)
    private var sessionId = UUID.randomUUID().toString()
    private var remoteDescriptionSet = false
    private var accepted = false
    private var ended = false
    private var pendingOffer: String? = null
    private val pendingIce = mutableListOf<SignalingMessage>()

    private val signaling = SignalingClient(signalingEndpoint, object : SignalingListener {
        override fun onConnected() {
            listener.onConnected()
            send(SignalingMessage.Type.HELLO, "{}", sessionId)
            if (role == DeviceRole.CONTROLLER) session.createOffer()
        }

        override fun onMessage(message: SignalingMessage) {
            if (message.senderDeviceId != trustedDevice.deviceId) return
            // HELLO is bootstrap registration and can carry the sender's own session ID.
            if (message.type != SignalingMessage.Type.HELLO && message.sessionId != sessionId) return
            if (!signed.verify(message, trustedDevice.publicKey)) {
                listener.onError("Rejected unauthenticated signaling message")
                return
            }
            if (message.type == SignalingMessage.Type.OFFER && role == DeviceRole.CAMERA) {
                // The controller's offer establishes the shared WebRTC session ID.
                sessionId = message.sessionId
            }
            handleMessage(message)
        }

        override fun onFailure(t: Throwable) {
            listener.onError(t.message ?: "Signaling connection failed")
        }

        override fun onClosed() {
            if (!ended) listener.onEnded()
        }
    })

    private val session = WebRtcSession(context, eglBase, object : WebRtcSession.Listener {
        override fun onLocalDescription(description: SessionDescription) {
            val type = when (description.type) {
                SessionDescription.Type.OFFER -> SignalingMessage.Type.OFFER
                SessionDescription.Type.ANSWER -> SignalingMessage.Type.ANSWER
                else -> return
            }
            send(type, WebRtcPayload.sdp(description))
        }

        override fun onLocalIceCandidate(candidate: PeerConnection.IceCandidate) {
            send(SignalingMessage.Type.ICE, WebRtcPayload.ice(candidate))
        }

        override fun onRemoteVideoTrack(track: VideoTrack) = listener.onRemoteVideoTrack(track)
        override fun onConnectionState(state: PeerConnection.PeerConnectionState) = listener.onConnectionState(state)
        override fun onError(message: String) = listener.onError(message)
    })

    fun connect() {
        if (signalingEndpoint.isBlank()) {
            listener.onError("Configure a wss:// signaling endpoint before connecting")
            return
        }
        if (!signalingEndpoint.startsWith("wss://")) {
            listener.onError("Signaling endpoint must use wss://")
            return
        }
        listener.onConnecting()
        signaling.connect()
    }

    /** Camera-side explicit approval. Capture begins only after user approval. */
    fun acceptCameraShare() {
        if (role != DeviceRole.CAMERA || accepted) return
        val offer = pendingOffer ?: run {
            listener.onError("No pending offer")
            return
        }
        accepted = true
        session.addLocalMedia(context)
        session.setRemoteDescription(SessionDescription.Type.OFFER, offer)
        remoteDescriptionSet = true
        flushPendingIce()
        session.createAnswer()
    }

    fun rejectOrEnd() {
        if (ended) return
        send(SignalingMessage.Type.REJECT, "{\"reason\":\"user_rejected\"}")
        closeInternal()
    }

    fun end() {
        if (ended) return
        send(SignalingMessage.Type.END, "{}")
        closeInternal()
    }

    private fun handleMessage(message: SignalingMessage) {
        when (message.type) {
            SignalingMessage.Type.HELLO -> Unit
            SignalingMessage.Type.OFFER -> {
                if (role != DeviceRole.CAMERA || accepted) return
                pendingOffer = JSONObject(message.payload).getString("sdp")
                listener.onApprovalRequired()
            }
            SignalingMessage.Type.ANSWER -> {
                if (role != DeviceRole.CONTROLLER) return
                val sdp = JSONObject(message.payload).getString("sdp")
                session.setRemoteDescription(SessionDescription.Type.ANSWER, sdp)
                remoteDescriptionSet = true
                flushPendingIce()
            }
            SignalingMessage.Type.ICE -> {
                if (!remoteDescriptionSet) pendingIce += message else applyIce(message)
            }
            SignalingMessage.Type.REJECT, SignalingMessage.Type.END -> closeInternal()
        }
    }

    private fun applyIce(message: SignalingMessage) {
        val json = JSONObject(message.payload)
        session.addRemoteIceCandidate(
            if (json.isNull("sdpMid")) null else json.getString("sdpMid"),
            json.getInt("sdpMLineIndex"),
            json.getString("candidate")
        )
    }

    private fun flushPendingIce() {
        pendingIce.toList().forEach(::applyIce)
        pendingIce.clear()
    }

    private fun send(type: SignalingMessage.Type, payload: String, targetSessionId: String = sessionId) {
        val message = signed.create(type, targetSessionId, trustedDevice.deviceId, payload)
        if (!signaling.send(message)) listener.onError("Unable to send ${type.name} signaling message")
    }

    private fun closeInternal() {
        if (ended) return
        ended = true
        signaling.close()
        session.close()
        listener.onEnded()
    }
}
