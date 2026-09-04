package com.sujon.remotlinkssm.ui

import android.Manifest
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.sujon.remotlinkssm.RemoteLinkApplication
import com.sujon.remotlinkssm.domain.model.DeviceRole
import com.sujon.remotlinkssm.pairing.*
import com.sujon.remotlinkssm.signaling.SignalingConfig
import com.sujon.remotlinkssm.webrtc.WebRtcSessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.webrtc.EglBase
import org.webrtc.PeerConnection
import org.webrtc.VideoTrack

private enum class PairingRoute { NONE, CAMERA_LINK, CONTROLLER_IDENTITY_QR }

@Composable
fun AppRoot(initialPairingUri: Uri? = null) {
    val context = LocalContext.current
    val database = (context.applicationContext as RemoteLinkApplication).database
    val repository = remember { PairingRepository(context.applicationContext, database.trustedDeviceDao()) }
    val trustedDevices by repository.observeTrustedDevices().collectAsState(initial = emptyList())
    val signalingConfig = remember { SignalingConfig(context.applicationContext) }
    val scope = rememberCoroutineScope()
    val eglBase = remember { EglBase.create() }
    DisposableEffect(Unit) { onDispose { eglBase.release() } }

    var role by remember(initialPairingUri) { mutableStateOf<DeviceRole?>(if (initialPairingUri != null) DeviceRole.CONTROLLER else null) }
    var pairingRoute by remember(initialPairingUri) { mutableStateOf(PairingRoute.NONE) }
    var permissionMessage by remember { mutableStateOf<String?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var endpoint by remember { mutableStateOf(signalingConfig.endpoint) }
    var invitation by remember { mutableStateOf<PairingLink?>(null) }
    var request by remember { mutableStateOf<Triple<String, String, String>?>(null) }
    var manager by remember { mutableStateOf<WebRtcSessionManager?>(null) }
    var connectionStatus by remember { mutableStateOf("Idle") }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        if (results[Manifest.permission.CAMERA] == true && results[Manifest.permission.RECORD_AUDIO] == true) {
            role = DeviceRole.CAMERA; permissionMessage = null
        } else permissionMessage = "Camera ও microphone permission না দিলে Camera mode চালু করা যাবে না।"
    }
    DisposableEffect(manager) { onDispose { manager?.end() } }

    fun newManager(roleValue: DeviceRole, device: com.sujon.remotlinkssm.data.local.TrustedDeviceEntity?, token: String?, onRequest: ((String, String, String) -> Unit)? = null): WebRtcSessionManager {
        return WebRtcSessionManager(context.applicationContext, roleValue, device, eglBase, object : WebRtcSessionManager.Listener {
            override fun onConnecting() { connectionStatus = if (roleValue == DeviceRole.CAMERA) "Listening…" else "Sending request…" }
            override fun onConnected() { connectionStatus = if (roleValue == DeviceRole.CAMERA) "Waiting for Controller…" else "Request sent — waiting for Camera approval…" }
            override fun onIncomingRequest(deviceId: String, deviceName: String, publicKey: String) { onRequest?.invoke(deviceId, deviceName, publicKey) }
            override fun onApprovalRequired() { connectionStatus = "Camera approval required" }
            override fun onRemoteVideoTrack(track: VideoTrack) { connectionStatus = "Remote video connected" }
            override fun onConnectionState(state: PeerConnection.PeerConnectionState) { connectionStatus = state.name }
            override fun onError(message: String) { statusMessage = message }
            override fun onEnded() { connectionStatus = "Connection ended"; request = null }
        }, endpoint, token)
    }

    when {
        initialPairingUri != null && role == DeviceRole.CONTROLLER -> ControllerPairingLinkScreen(
            initialPairingUri, repository,
            onBack = { role = null },
            onPaired = { paired ->
                signalingConfig.endpoint = paired.signalingEndpoint; endpoint = paired.signalingEndpoint; invitation = paired
                statusMessage = "Camera Device trusted. Press Connect to send a connection request."
                role = DeviceRole.CONTROLLER
            }
        )
        role == null -> RoleSelectionScreen(
            onController = { role = DeviceRole.CONTROLLER },
            onCamera = { permissionLauncher.launch(buildList { add(Manifest.permission.CAMERA); add(Manifest.permission.RECORD_AUDIO); if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS) }.toTypedArray()) },
            permissionMessage = permissionMessage
        )
        pairingRoute == PairingRoute.CAMERA_LINK -> CameraPairingLinkScreen(
            repository, endpoint,
            onBack = { pairingRoute = PairingRoute.NONE },
            onLinkCreated = { created ->
                invitation = created; manager?.end()
                manager = newManager(DeviceRole.CAMERA, null, created.token) { id, name, key -> request = Triple(id, name, key); connectionStatus = "Controller request received" }
                manager?.connect()
            }
        )
        request != null && role == DeviceRole.CAMERA -> {
            val current = request!!
            CameraConnectionRequestScreen(
                current.second, current.first,
                onAccept = {
                    scope.launch(Dispatchers.IO) { repository.trustIdentity(DeviceIdentityQr(current.first, current.second, current.third)) }
                    manager?.acceptCameraShare(); statusMessage = "Accepted — starting secure camera connection…"; request = null
                },
                onReject = { manager?.rejectOrEnd(); request = null; statusMessage = "Connection request rejected." },
                status = connectionStatus
            )
        }
        role == DeviceRole.CONTROLLER -> {
            Column(Modifier.fillMaxSize()) {
                SignalingEndpointCard(endpoint, { endpoint = it }) {
                    if (endpoint.trim().startsWith("wss://")) { endpoint = endpoint.trim(); signalingConfig.endpoint = endpoint; statusMessage = "Signaling endpoint saved." }
                    else statusMessage = "Endpoint must start with wss://"
                }
                TrustedDevicesScreen(
                    devices = trustedDevices,
                    onPair = { statusMessage = "Open the Camera invitation link, trust it, then press Connect." },
                    onIdentityQr = { pairingRoute = PairingRoute.CONTROLLER_IDENTITY_QR },
                    onRevoke = { device -> scope.launch(Dispatchers.IO) { repository.revoke(device.deviceId) }; statusMessage = "${device.deviceName} revoked." },
                    onConnect = { device ->
                        if (!endpoint.startsWith("wss://")) { statusMessage = "Set a wss:// signaling endpoint first."; return@TrustedDevicesScreen }
                        manager?.end(); manager = newManager(DeviceRole.CONTROLLER, device, invitation?.token); manager?.connect()
                        scope.launch(Dispatchers.IO) { repository.markConnected(device.deviceId) }
                    }
                )
                StatusMessage("$connectionStatus${statusMessage?.let { " — $it" } ?: ""}")
                OutlinedButton(onClick = { manager?.end(); manager = null; role = null; statusMessage = null }, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) { Text("Change Role") }
            }
        }
        else -> {
            Column(Modifier.fillMaxSize().padding(20.dp)) {
                Text("Camera Device", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(10.dp)); Text("Create an expiring invitation and keep RemoteLink open. Each Controller request appears here with Accept/Reject.")
                Spacer(Modifier.height(18.dp)); SignalingEndpointCard(endpoint, { endpoint = it }) {
                    if (endpoint.trim().startsWith("wss://")) { endpoint = endpoint.trim(); signalingConfig.endpoint = endpoint; statusMessage = "Signaling endpoint saved." }
                    else statusMessage = "Endpoint must start with wss://"
                }
                Spacer(Modifier.height(8.dp)); Button(onClick = { pairingRoute = PairingRoute.CAMERA_LINK }, modifier = Modifier.fillMaxWidth(), enabled = endpoint.startsWith("wss://")) { Text("Create & Share Pairing Link") }
                if (invitation != null) { Spacer(Modifier.height(12.dp)); Text("Invitation active — $connectionStatus") }
                Spacer(Modifier.height(12.dp)); OutlinedButton(onClick = { pairingRoute = PairingRoute.CONTROLLER_IDENTITY_QR }, modifier = Modifier.fillMaxWidth()) { Text("Controller Identity QR (fallback)") }
                Spacer(Modifier.weight(1f)); StatusMessage(statusMessage)
                OutlinedButton(onClick = { manager?.end(); manager = null; invitation = null; role = null }, modifier = Modifier.fillMaxWidth()) { Text("Change Role") }
            }
        }
    }
}

@Composable
private fun SignalingEndpointCard(endpoint: String, onEndpointChange: (String) -> Unit, onSave: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) { Column(Modifier.padding(16.dp)) {
        Text("Signaling server", style = MaterialTheme.typography.titleMedium); Spacer(Modifier.height(6.dp))
        Text("Only connection setup messages use this server. Camera/video/audio stay peer-to-peer.")
        Spacer(Modifier.height(10.dp)); OutlinedTextField(endpoint, onEndpointChange, label = { Text("wss://…") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp)); Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) { Text("Save endpoint") }
    }}
}

@Composable private fun StatusMessage(message: String?) { message?.let { Text(it, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) } }

@Composable
private fun RoleSelectionScreen(onController: () -> Unit, onCamera: () -> Unit, permissionMessage: String?) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text("RemoteLink", style = MaterialTheme.typography.headlineLarge); Spacer(Modifier.height(8.dp)); Text("Connect a trusted Controller to a Camera Device with explicit user approval."); Spacer(Modifier.height(28.dp))
        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(20.dp)) { Text("Controller", style = MaterialTheme.typography.titleLarge); Spacer(Modifier.height(6.dp)); Text("Open a RemoteLink invitation and send a connection request."); Spacer(Modifier.height(14.dp)); Button(onClick = onController, modifier = Modifier.fillMaxWidth()) { Text("Use as Controller") } } }
        Spacer(Modifier.height(16.dp))
        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(20.dp)) { Text("Camera Device", style = MaterialTheme.typography.titleLarge); Spacer(Modifier.height(6.dp)); Text("Create an expiring link and explicitly accept each connection request."); Spacer(Modifier.height(14.dp)); OutlinedButton(onClick = onCamera, modifier = Modifier.fillMaxWidth()) { Text("Use as Camera Device") } } }
        permissionMessage?.let { Spacer(Modifier.height(16.dp)); Text(it, color = MaterialTheme.colorScheme.error) }
    }
}
