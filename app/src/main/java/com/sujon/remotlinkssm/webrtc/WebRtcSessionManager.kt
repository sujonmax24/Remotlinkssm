package com.sujon.remotlinkssm.webrtc

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import com.sujon.remotlinkssm.data.local.TrustedDeviceEntity
import com.sujon.remotlinkssm.domain.model.DeviceRole
import com.sujon.remotlinkssm.security.DeviceKeyManager
import com.sujon.remotlinkssm.service.CameraShareForegroundService
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

/** Coordinates authenticated signaling and one WebRTC session with explicit camera approval. */
class WebRtcSessionManager(
    private val context: Context,
    private val role: DeviceRole,
    private val trustedDevice: TrustedDeviceEntity?,
    private val eglBase: EglBase,
    private val listener: Listener,
    private val signalingEndpoint: String = SignalingConfig(context).endpoint,
    private val invitationToken: String? = null
) {
    interface Listener {
        fun onConnecting()
        fun onConnected()
        fun onIncomingRequest(deviceId: String, deviceName: String, publicKey: String)
        fun onApprovalRequired()
        fun onRemoteVideoTrack(track: VideoTrack)
        fun onConnectionState(state: PeerConnection.PeerConnectionState)
        fun onError(message: String)
        fun onEnded()
    }

    private val appContext = context.applicationContext
    private val localDeviceId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        ?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
    private val keyManager = DeviceKeyManager()
    private val signed = SignedSignaling(localDeviceId, keyManager)
    private var sessionId = UUID.randomUUID().toString()
    private var remoteDeviceId: String? = trustedDevice?.deviceId
    private var remotePublicKey: String? = trustedDevice?.publicKey
    private var remoteDescriptionSet = false
    private var accepted = false
    private var ended = false
    private var approvalRequested = false
    private var pendingOffer: String? = null
    private val pendingIce = mutableListOf<SignalingMessage>()

    private val signaling = SignalingClient(signalingEndpoint, object : SignalingListener {
        override fun onConnected() {
            listener.onConnected()
            if (role == DeviceRole.CONTROLLER) {
                sendHello()
                session.createOffer()
            }
        }

        override fun onMessage(message: SignalingMessage) {
            if (message.type == SignalingMessage.Type.HELLO && role == DeviceRole.CAMERA) {
                handleIncomingHello(message)
                return
            }
            val expectedDeviceId = remoteDeviceId ?: return
            if (message.senderDeviceId != expectedDeviceId || message.sessionId != sessionId) return
            val publicKey = remotePublicKey ?: return
            if (!signed.verify(message, publicKey)) {
                listener.onError("Rejected unauthenticated signaling message")
                return
            }
            handleMessage(message)
        }

        override fun onFailure(t: Throwable) = listener.onError(t.message ?: "Signaling connection failed")
        override fun onClosed() { if (!ended) listener.onEnded() }
    })

    private val session = WebRtcSession(context, eglBase, object : WebRtcSession.Listener {
        override fun onLocalDescription(description: SessionDescription) {
            val type = when (description.type) {
                SessionDescription.Type.OFFER -> SignalingMessage.Type.OFFER
                SessionDescription.Type.ANSWER -> SignalingMessage.Type.ANSWER
                else -> return
            }
            if (remoteDeviceId != null) send(type, WebRtcPayload.sdp(description))
        }
        override fun onLocalIceCandidate(candidate: PeerConnection.IceCandidate) {
            if (remoteDeviceId != null) send(SignalingMessage.Type.ICE, WebRtcPayload.ice(candidate))
        }
        override fun onRemoteVideoTrack(track: VideoTrack) = listener.onRemoteVideoTrack(track)
        override fun onConnectionState(state: PeerConnection.PeerConnectionState) = listener.onConnectionState(state)
        override fun onError(message: String) = listener.onError(message)
    })

    fun connect() {
        if (signalingEndpoint.isBlank()) return listener.onError("Configure a wss:// signaling endpoint before connecting")
        if (!signalingEndpoint.startsWith("wss://")) return listener.onError("Signaling endpoint must use wss://")
        if (role == DeviceRole.CONTROLLER && remoteDeviceId == null) return listener.onError("No Camera Device is selected")
        listener.onConnecting()
        signaling.connect()
    }

    /** Camera-side approval. Camera/mic capture is not started before this method is called. */
    fun acceptCameraShare() {
        if (role != DeviceRole.CAMERA || accepted) return
        if (remoteDeviceId == null || remotePublicKey == null) return listener.onError("No authenticated Controller request is pending")
        accepted = true
        sendHello()
        val offer = pendingOffer ?: return
        startSharingService()
        session.addLocalMedia(context)
        session.setRemoteDescription(SessionDescription.Type.OFFER, offer)
        remoteDescriptionSet = true
        flushPendingIce()
        session.createAnswer()
    }

    fun rejectOrEnd() {
        if (ended) return
        if (remoteDeviceId != null) send(SignalingMessage.Type.REJECT, "{\"reason\":\"user_rejected\"}")
        closeInternal()
    }

    fun end() {
        if (ended) return
        if (remoteDeviceId != null) send(SignalingMessage.Type.END, "{}")
        closeInternal()
    }

    private fun handleIncomingHello(message: SignalingMessage) {
        if (invitationToken.isNullOrBlank()) return
        val payload = runCatching { JSONObject(message.payload) }.getOrNull() ?: return
        if (payload.optString("token") != invitationToken) {
            listener.onError("Rejected connection request: invitation token mismatch")
            return
        }
        val publicKey = payload.optString("publicKey")
        val deviceName = payload.optString("deviceName").ifBlank { "Controller" }
        if (publicKey.isBlank()) return listener.onError("Rejected connection request: missing Controller identity")
        if (!signed.verify(message, publicKey)) return listener.onError("Rejected connection request: invalid Controller signature")
        remoteDeviceId = message.senderDeviceId
        remotePublicKey = publicKey
        sessionId = message.sessionId
        listener.onIncomingRequest(message.senderDeviceId, deviceName, publicKey)
        if (!approvalRequested) {
            approvalRequested = true
            listener.onApprovalRequired()
        }
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
                session.setRemoteDescription(SessionDescription.Type.ANSWER, JSONObject(message.payload).getString("sdp"))
                remoteDescriptionSet = true
                flushPendingIce()
            }
            SignalingMessage.Type.ICE -> if (!remoteDescriptionSet) pendingIce += message else applyIce(message)
            SignalingMessage.Type.REJECT, SignalingMessage.Type.END -> closeInternal()
        }
    }

    private fun sendHello() {
        val target = remoteDeviceId ?: return
        val payload = JSONObject()
            .put("token", invitationToken ?: "")
            .put("deviceName", localDeviceName())
            .put("publicKey", keyManager.publicKeyBase64())
            .toString()
        val message = signed.create(SignalingMessage.Type.HELLO, sessionId, target, payload)
        if (!signaling.send(message)) listener.onError("Unable to send HELLO signaling message")
    }

    private fun applyIce(message: SignalingMessage) {
        val json = JSONObject(message.payload)
        session.addRemoteIceCandidate(if (json.isNull("sdpMid")) null else json.getString("sdpMid"), json.getInt("sdpMLineIndex"), json.getString("candidate"))
    }

    private fun flushPendingIce() {
        pendingIce.toList().forEach(::applyIce)
        pendingIce.clear()
    }

    private fun send(type: SignalingMessage.Type, payload: String) {
        val target = remoteDeviceId ?: return
        val message = signed.create(type, sessionId, target, payload)
        if (!signaling.send(message)) listener.onError("Unable to send ${type.name} signaling message")
    }

    private fun startSharingService() {
        val intent = Intent(appContext, CameraShareForegroundService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) appContext.startForegroundService(intent) else appContext.startService(intent)
    }
    private fun stopSharingService() = appContext.stopService(Intent(appContext, CameraShareForegroundService::class.java))
    private fun localDeviceName() = listOf(Build.MANUFACTURER, Build.MODEL).joinToString(" ") { it.trim() }.trim().ifBlank { "Android Device" }

    private fun closeInternal() {
        if (ended) return
        ended = true
        stopSharingService()
        signaling.close()
        session.close()
        listener.onEnded()
    }
}
