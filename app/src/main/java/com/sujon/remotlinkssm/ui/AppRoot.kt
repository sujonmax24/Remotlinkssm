package com.sujon.remotlinkssm.ui

import android.Manifest
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
import com.sujon.remotlinkssm.pairing.CameraControllerIdentityScanScreen
import com.sujon.remotlinkssm.pairing.CameraPairingScreen
import com.sujon.remotlinkssm.pairing.ControllerIdentityQrScreen
import com.sujon.remotlinkssm.pairing.ControllerPairingScreen
import com.sujon.remotlinkssm.pairing.PairingRepository
import com.sujon.remotlinkssm.pairing.TrustedDevicesScreen
import com.sujon.remotlinkssm.signaling.SignalingConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private enum class PairingRoute { NONE, CAMERA_QR, CONTROLLER_SCAN, CONTROLLER_IDENTITY_QR, CAMERA_SCAN_CONTROLLER }

@Composable
fun AppRoot() {
    val context = LocalContext.current
    val database = (context.applicationContext as RemoteLinkApplication).database
    val repository = remember { PairingRepository(context.applicationContext, database.trustedDeviceDao()) }
    val trustedDevices by repository.observeTrustedDevices().collectAsState(initial = emptyList())
    val signalingConfig = remember { SignalingConfig(context.applicationContext) }
    val scope = rememberCoroutineScope()

    var role by remember { mutableStateOf<DeviceRole?>(null) }
    var pairingRoute by remember { mutableStateOf(PairingRoute.NONE) }
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
        } else {
            permissionMessage = "Camera ও microphone permission না দিলে Camera mode চালু করা যাবে না।"
        }
    }

    val controllerCameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            role = DeviceRole.CONTROLLER
            pairingRoute = PairingRoute.CONTROLLER_SCAN
            permissionMessage = null
        } else {
            permissionMessage = "QR scan করার জন্য Controller-কে Camera permission দিতে হবে।"
        }
    }

    when {
        role == null -> RoleSelectionScreen(
            onController = { controllerCameraPermissionLauncher.launch(Manifest.permission.CAMERA) },
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

        pairingRoute == PairingRoute.CAMERA_QR -> CameraPairingScreen(
            repository = repository,
            onBack = { pairingRoute = PairingRoute.NONE }
        )

        pairingRoute == PairingRoute.CONTROLLER_SCAN -> ControllerPairingScreen(
            repository = repository,
            onBack = { pairingRoute = PairingRoute.NONE },
            onPaired = {
                pairingRoute = PairingRoute.CONTROLLER_IDENTITY_QR
                statusMessage = "Camera trusted. Now add this Controller identity on the Camera Device."
            }
        )

        pairingRoute == PairingRoute.CONTROLLER_IDENTITY_QR -> ControllerIdentityQrScreen(
            repository = repository,
            onBack = { pairingRoute = PairingRoute.NONE }
        )

        pairingRoute == PairingRoute.CAMERA_SCAN_CONTROLLER -> CameraControllerIdentityScanScreen(
            repository = repository,
            onBack = { pairingRoute = PairingRoute.NONE },
            onTrusted = {
                pairingRoute = PairingRoute.NONE
                statusMessage = "Controller trusted. Both devices now have each other's public identity."
            }
        )

        role == DeviceRole.CONTROLLER -> {
            Column(Modifier.fillMaxSize()) {
                SignalingEndpointCard(
                    endpoint = endpoint,
                    onEndpointChange = { endpoint = it },
                    onSave = {
                        val value = endpoint.trim()
                        if (value.startsWith("wss://")) {
                            signalingConfig.endpoint = value
                            statusMessage = "Signaling endpoint saved."
                        } else {
                            statusMessage = "Endpoint must start with wss://"
                        }
                    }
                )
                TrustedDevicesScreen(
                    devices = trustedDevices,
                    onPair = { pairingRoute = PairingRoute.CONTROLLER_SCAN },
                    onIdentityQr = { pairingRoute = PairingRoute.CONTROLLER_IDENTITY_QR },
                    onRevoke = { device ->
                        scope.launch(Dispatchers.IO) { repository.revoke(device.deviceId) }
                        statusMessage = "${device.deviceName} revoked."
                    },
                    onConnect = { device ->
                        scope.launch(Dispatchers.IO { repository.markConnected(device.deviceId) })
                        statusMessage = if (signalingConfig.endpoint.isBlank()) {
                            "Set a wss:// signaling endpoint before connecting."
                        } else {
                            "${device.deviceName} is trusted. WebRTC session wiring is next."
                        }
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
                Text("Pair a Controller once. Later connections use the saved device identity, subject to Android permissions and your approval.")
                Spacer(Modifier.height(24.dp))
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp)) {
                        Text("Pair a Controller", style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(8.dp))
                        Text("First, let the Controller scan this Camera QR + 6-digit code.")
                        Spacer(Modifier.height(14.dp))
                        Button(onClick = { pairingRoute = PairingRoute.CAMERA_QR }, modifier = Modifier.fillMaxWidth()) {
                            Text("Show Pairing QR")
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { pairingRoute = PairingRoute.CAMERA_SCAN_CONTROLLER },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Scan Controller QR") }
                Spacer(Modifier.weight(1f))
                StatusMessage(statusMessage)
                OutlinedButton(onClick = { role = null }, modifier = Modifier.fillMaxWidth()) {
                    Text("Change Role")
                }
            }
        }
    }
}

@Composable
private fun SignalingEndpointCard(endpoint: String, onEndpointChange: (String) -> Unit, onSave: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(20.dp, 12.dp, 20.dp, 4.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text("Signaling server", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text("Required for SDP/ICE exchange. Use a TLS WebSocket endpoint; media does not pass through it.")
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = endpoint,
                onValueChange = onEndpointChange,
                label = { Text("wss://…") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) { Text("Save endpoint") }
        }
    }
}

@Composable
private fun StatusMessage(message: String?) {
    message?.let { Text(it, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) }
}

@Composable
private fun RoleSelectionScreen(
    onController: () -> Unit,
    onCamera: () -> Unit,
    permissionMessage: String?
) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text("RemoteLink", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(8.dp))
        Text("Securely pair two phones so a trusted Controller can later connect to a Camera Device.")
        Spacer(Modifier.height(28.dp))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp)) {
                Text("Controller", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(6.dp))
                Text("Scan the Camera Device QR and verify its 6-digit pairing code.")
                Spacer(Modifier.height(14.dp))
                Button(onClick = onController, modifier = Modifier.fillMaxWidth()) { Text("Use as Controller") }
            }
        }
        Spacer(Modifier.height(16.dp))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp)) {
                Text("Camera Device", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(6.dp))
                Text("Create a QR + 6-digit pairing session for a Controller phone.")
                Spacer(Modifier.height(14.dp))
                OutlinedButton(onClick = onCamera, modifier = Modifier.fillMaxWidth()) { Text("Use as Camera Device") }
            }
        }
        permissionMessage?.let {
            Spacer(Modifier.height(16.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }
    }
}
