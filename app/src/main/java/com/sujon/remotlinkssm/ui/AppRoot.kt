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
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sujon.remotlinkssm.domain.model.DeviceRole

@Composable
fun AppRoot() {
    var role by remember { mutableStateOf<DeviceRole?>(null) }
    var permissionMessage by remember { mutableStateOf<String?>(null) }

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

    if (role == null) {
        RoleSelectionScreen(
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
    } else {
        DeviceHomeScreen(role = role!!, onBack = { role = null })
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
        Text(
            "একটি ফোনকে অন্য ফোনের camera, audio ও screen-এর সাথে নিরাপদভাবে যুক্ত করুন।",
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(Modifier.height(28.dp))

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp)) {
                Text("Controller", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(6.dp))
                Text("অন্য ফোনের live view, photo, video, camera switch, flash ও zoom control করুন।")
                Spacer(Modifier.height(14.dp))
                Button(onClick = onController, modifier = Modifier.fillMaxWidth()) {
                    Text("Use as Controller")
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp)) {
                Text("Camera Device", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(6.dp))
                Text("এই ফোনের camera ও microphone অন্য trusted phone-কে share করার জন্য প্রস্তুত করুন।")
                Spacer(Modifier.height(14.dp))
                OutlinedButton(onClick = onCamera, modifier = Modifier.fillMaxWidth()) {
                    Text("Use as Camera Device")
                }
            }
        }

        permissionMessage?.let {
            Spacer(Modifier.height(16.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun DeviceHomeScreen(role: DeviceRole, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Text(
            if (role == DeviceRole.CONTROLLER) "Controller Dashboard" else "Camera Device",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(Modifier.height(12.dp))
        Text("Phase 1 foundation is ready. Pairing, WebRTC media and screen control are coming in the next phases.")
        Spacer(Modifier.height(24.dp))
        Text("Trusted devices", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text("No trusted devices yet. QR + 6-digit secure pairing will be added next.")
        Spacer(Modifier.weight(1f))
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("Change Role")
        }
    }
}
