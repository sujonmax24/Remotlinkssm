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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.sujon.remotlinkssm.RemoteLinkApplication
import com.sujon.remotlinkssm.data.local.TrustedDeviceEntity
import com.sujon.remotlinkssm.domain.model.DeviceRole
import com.sujon.remotlinkssm.pairing.CameraPairingScreen
import com.sujon.remotlinkssm.pairing.ControllerPairingScreen
import com.sujon.remotlinkssm.pairing.PairingRepository
import com.sujon.remotlinkssm.pairing.TrustedDevicesScreen

private enum class PairingRoute { NONE, CAMERA_QR, CONTROLLER_SCAN }

@Composable
fun AppRoot() {
    val context = LocalContext.current
    val database = (context.applicationContext as RemoteLinkApplication).database
    val repository = remember { PairingRepository(context.applicationContext, database.trustedDeviceDao()) }
    val trustedDevices by repository.observeTrustedDevices().collectAsState(initial = emptyList())

    var role by remember { mutableStateOf<DeviceRole?>(null) }
    var pairingRoute by remember { mutableStateOf(PairingRoute.NONE) }
    var permissionMessage by remember { mutableStateOf<String?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
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

    when {
        role == null -> RoleSelectionScreen(
            onController = { role = DeviceRole.CONTROLLER },
            onCamera = {
                permissionLauncher.launch(
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
                pairingRoute = PairingRoute.NONE
                statusMessage = "Device trusted. It will remain in Trusted devices until you revoke it."
            }
        )

        role == DeviceRole.CONTROLLER -> {
            Column(Modifier.fillMaxSize()) {
                TrustedDevicesScreen(
                    devices = trustedDevices,
                    onPair = { pairingRoute = PairingRoute.CONTROLLER_SCAN },
                    onRevoke = { device ->
                        statusMessage = "${device.deviceName} revoked."
                        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                            repository.revoke(device.deviceId)
                        }
                    },
                    onConnect = { device ->
                        statusMessage = "${device.deviceName} is trusted. Live connection will be enabled with the WebRTC phase."
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
                Text("This phone can create a secure first-time pairing QR. The controller must also enter the displayed 6-digit code.")
                Spacer(Modifier.height(24.dp))
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp)) {
                        Text("Pair a Controller", style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(8.dp))
                        Text("Show a QR code and one-time 6-digit verification code to the other phone.")
                        Spacer(Modifier.height(14.dp))
                        Button(onClick = { pairingRoute = PairingRoute.CAMERA_QR }, modifier = Modifier.fillMaxWidth()) {
                            Text("Show Pairing QR")
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                OutlinedButton(
                    onClick = { role = null },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Change Role") }
            }
        }
    }
}

@Composable
private fun StatusMessage(message: String?) {
    message?.let {
        Text(it, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
    }
}

@Composable
private fun RoleSelectionScreen(
    onController: () -> Unit,
    onCamera: () -> Unit,
    permissionMessage: String?
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
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
