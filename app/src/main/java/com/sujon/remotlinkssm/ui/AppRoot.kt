package com.sujon.remotlinkssm.ui

import android.Manifest
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.sujon.remotlinkssm.RemoteLinkApplication
import com.sujon.remotlinkssm.domain.model.DeviceRole
import com.sujon.remotlinkssm.pairing.CameraPairingLinkScreen
import com.sujon.remotlinkssm.pairing.ControllerIdentityQrScreen
import com.sujon.remotlinkssm.pairing.ControllerPairingLinkScreen
import com.sujon.remotlinkssm.pairing.PairingRepository
import com.sujon.remotlinkssm.pairing.TrustedDevicesScreen
import com.sujon.remotlinkssm.signaling.SignalingConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private enum class PairingRoute { NONE, CAMERA_LINK, CONTROLLER_LINK, CONTROLLER_IDENTITY_QR }

@Composable
fun AppRoot(initialPairingUri: Uri? = null) {
    val context = LocalContext.current
    val database = (context.applicationContext as RemoteLinkApplication).database
    val repository = remember { PairingRepository(context.applicationContext, database.trustedDeviceDao()) }
    val trustedDevices by repository.observeTrustedDevices().collectAsState(initial = emptyList())
    val signalingConfig = remember { SignalingConfig(context.applicationContext) }
    val scope = rememberCoroutineScope()

    var role by remember(initialPairingUri) { mutableStateOf<DeviceRole?>(if (initialPairingUri != null) DeviceRole.CONTROLLER else null) }
    var pairingRoute by remember(initialPairingUri) { mutableStateOf(if (initialPairingUri != null) PairingRoute.CONTROLLER_LINK else PairingRoute.NONE) }
    var permissionMessage by remember { mutableStateOf<String?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var endpoint by remember { mutableStateOf(signalingConfig.endpoint) }

    val cameraRolePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val cameraOk = results[Manifest.permission.CAMERA] == true
        val micOk = results[Manifest.permission.RECORD_AUDIO] == true
        if (cameraOk && micOk) {
            role = DeviceRole.CAMERA
            permissionMessage = null
        } else permissionMessage = "Camera ও microphone permission না দিলে Camera mode চালু করা যাবে না।"
    }

    when {
        pairingRoute == PairingRoute.CONTROLLER_LINK && initialPairingUri != null -> ControllerPairingLinkScreen(
            uri = initialPairingUri,
            repository = repository,
            onBack = { pairingRoute = PairingRoute.NONE; role = null },
            onPaired = { invitation ->
                signalingConfig.endpoint = invitation.signalingEndpoint
                endpoint = invitation.signalingEndpoint
                pairingRoute = PairingRoute.NONE
                statusMessage = "Camera Device trusted. You can now connect when the Camera user approves the request."
            }
        )

        role == null -> RoleSelectionScreen(
            onController = { role = DeviceRole.CONTROLLER },
            onCamera = {
                cameraRolePermissionLauncher.launch(
                    buildList {
                        add(Manifest.permission.CAMERA)
                        add(Manifest.permission.RECORD_AUDIO)
                        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
                    }.toTypedArray()
                )
            },
            permissionMessage = permissionMessage
        )

        pairingRoute == PairingRoute.CAMERA_LINK -> CameraPairingLinkScreen(
            repository = repository,
            signalingEndpoint = endpoint,
            onBack = { pairingRoute = PairingRoute.NONE }
        )

        pairingRoute == PairingRoute.CONTROLLER_IDENTITY_QR -> ControllerIdentityQrScreen(
            repository = repository,
            onBack = { pairingRoute = PairingRoute.NONE }
        )

        role == DeviceRole.CONTROLLER -> {
            Column(Modifier.fillMaxSize()) {
                SignalingEndpointCard(
                    endpoint = endpoint,
                    onEndpointChange = { endpoint = it },
                    onSave = {
                        if (endpoint.trim().startsWith("wss://")) {
                            signalingConfig.endpoint = endpoint.trim()
                            statusMessage = "Signaling endpoint saved."
                        } else statusMessage = "Endpoint must start with wss://"
                    }
                )
                TrustedDevicesScreen(
                    devices = trustedDevices,
                    onPair = { statusMessage = "Open the Camera Device and share its pairing link with this phone." },
                    onIdentityQr = { pairingRoute = PairingRoute.CONTROLLER_IDENTITY_QR },
                    onRevoke = { device ->
                        scope.launch(Dispatchers.IO) { repository.revoke(device.deviceId) }
                        statusMessage = "${device.deviceName} revoked."
                    },
                    onConnect = { device ->
                        scope.launch(Dispatchers.IO { repository.markConnected(device.deviceId) })
                        statusMessage = if (signalingConfig.endpoint.isBlank()) {
                            "Set a wss:// signaling endpoint before connecting."
                        } else "${device.deviceName} is trusted. WebRTC connection UI is next."
                    }
                )
                StatusMessage(statusMessage)
                OutlinedButton(
                    onClick = { role = null; statusMessage = null },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)
                ) { Text("Change Role") }
            }
        }

        else -> {
            Column(Modifier.fillMaxSize().padding(20.dp)) {
                Text("Camera Device", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(10.dp))
                Text("Share an expiring link. The Controller opens it with one tap; no 6-digit pairing code or QR scan is required.")
                Spacer(Modifier.height(18.dp))
                SignalingEndpointCard(
                    endpoint = endpoint,
                    onEndpointChange = { endpoint = it },
                    onSave = {
                        if (endpoint.trim().startsWith("wss://")) {
                            signalingConfig.endpoint = endpoint.trim()
                            statusMessage = "Signaling endpoint saved."
                        } else statusMessage = "Endpoint must start with wss://"
                    }
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { pairingRoute = PairingRoute.CAMERA_LINK },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = endpoint.startsWith("wss://")
                ) { Text("Create & Share Pairing Link") }
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { pairingRoute = PairingRoute.CONTROLLER_IDENTITY_QR },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Controller Identity QR (fallback)") }
                Spacer(Modifier.weight(1f))
                StatusMessage(statusMessage)
                OutlinedButton(onClick = { role = null }, modifier = Modifier.fillMaxWidth()) { Text("Change Role") }
            }
        }
    }
}

@Composable
private fun SignalingEndpointCard(endpoint: String, onEndpointChange: (String) -> Unit, onSave: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text("Signaling server", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text("Required for connection setup. Camera/video/audio do not pass through the signaling server.")
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(value = endpoint, onValueChange = onEndpointChange, label = { Text("wss://…") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) { Text("Save endpoint") }
        }
    }
}

@Composable
private fun StatusMessage(message: String?) { message?.let { Text(it, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) } }

@Composable
private fun RoleSelectionScreen(onController: () -> Unit, onCamera: () -> Unit, permissionMessage: String?) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text("RemoteLink", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(8.dp))
        Text("Connect a trusted Controller to a Camera Device with explicit user approval.")
        Spacer(Modifier.height(28.dp))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp)) {
                Text("Controller", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(6.dp))
                Text("Open a RemoteLink invitation link shared by the Camera Device.")
                Spacer(Modifier.height(14.dp))
                Button(onClick = onController, modifier = Modifier.fillMaxWidth()) { Text("Use as Controller") }
            }
        }
        Spacer(Modifier.height(16.dp))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp)) {
                Text("Camera Device", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(6.dp))
                Text("Create an expiring link and share it with the Controller.")
                Spacer(Modifier.height(14.dp))
                OutlinedButton(onClick = onCamera, modifier = Modifier.fillMaxWidth()) { Text("Use as Camera Device") }
            }
        }
        permissionMessage?.let { Spacer(Modifier.height(16.dp)); Text(it, color = MaterialTheme.colorScheme.error) }
    }
}
