package com.sujon.remotlinkssm.pairing

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.sujon.remotlinkssm.data.local.TrustedDeviceEntity
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@Composable
fun CameraPairingLinkScreen(
    repository: PairingRepository,
    signalingEndpoint: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var link by remember { mutableStateOf<PairingLink?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Invite a Controller", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(10.dp))
        Text("Create a secure, expiring link. The Controller taps the link to open RemoteLink and request access. No pairing code is required.")
        Spacer(Modifier.height(18.dp))

        Button(
            onClick = {
                runCatching { repository.newPairingLink(signalingEndpoint) }
                    .onSuccess { link = it; error = null }
                    .onFailure { error = it.message ?: "Unable to create pairing link" }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = signalingEndpoint.startsWith("wss://")
        ) { Text("Create Pairing Link") }

        link?.let { invitation ->
            Spacer(Modifier.height(18.dp))
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp)) {
                    Text(invitation.deviceName, style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(6.dp))
                    Text("Link expires in about 10 minutes.")
                    Spacer(Modifier.height(12.dp))
                    Text(invitation.toUri().toString(), style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = {
                            context.startActivity(Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, invitation.toShareText())
                            }.let { Intent.createChooser(it, "Share RemoteLink invitation") })
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Share Link") }
                }
            }
        }

        error?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.weight(1f))
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
    }
}

@Composable
fun ControllerPairingLinkScreen(
    uri: Uri,
    repository: PairingRepository,
    onBack: () -> Unit,
    onPaired: (PairingLink) -> Unit
) {
    var invitation by remember(uri) { mutableStateOf<PairingLink?>(null) }
    var error by remember(uri) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    if (invitation == null && error == null) {
        runCatching { PairingLink.fromUri(uri) }
            .onSuccess { invitation = it }
            .onFailure { error = it.message ?: "Invalid RemoteLink invitation" }
    }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("Controller access request", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        if (invitation != null) {
            val current = invitation!!
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp)) {
                    Text(current.deviceName, style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(6.dp))
                    Text("This link requests permission to connect to this Camera Device.")
                    Spacer(Modifier.height(6.dp))
                    Text("Camera and microphone are not accessed merely by opening the link. The Camera Device must explicitly approve the connection.")
                }
            }
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = {
                    scope.launch {
                        runCatching { repository.trustLink(current) }
                            .onSuccess { onPaired(current) }
                            .onFailure { error = it.message ?: "Unable to trust Camera Device" }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Allow & Trust Camera Device") }
        }
        error?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
    }
}

@Composable
fun ControllerIdentityQrScreen(repository: PairingRepository, onBack: () -> Unit) {
    val identity = remember { repository.localIdentity() }
    val bitmap = remember(identity) { QrCodeGenerator.createBitmap(identity.toPayload()) }
    Column(Modifier.fillMaxSize().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Add this Controller to the Camera", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(10.dp))
        Text("Optional fallback: the Camera can scan this public Controller identity.")
        Spacer(Modifier.height(18.dp))
        Image(bitmap.asImageBitmap(), "Controller identity QR", Modifier.size(280.dp))
        Spacer(Modifier.height(16.dp))
        Text(identity.deviceName, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.weight(1f))
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Done") }
    }
}

@Composable
fun TrustedDevicesScreen(
    devices: List<TrustedDeviceEntity>,
    onRevoke: (TrustedDeviceEntity) -> Unit,
    onConnect: (TrustedDeviceEntity) -> Unit,
    onPair: () -> Unit,
    onIdentityQr: (() -> Unit)? = null
) {
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Trusted devices", style = MaterialTheme.typography.headlineSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                onIdentityQr?.let { OutlinedButton(onClick = it) { Text("My QR") } }
                Button(onClick = onPair) { Text("Pair by Link") }
            }
        }
        Spacer(Modifier.height(12.dp))
        if (devices.isEmpty()) {
            Text("No trusted devices yet. Open a RemoteLink invitation to add a Camera Device.")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(devices, key = { it.deviceId }) { device ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text(device.deviceName, style = MaterialTheme.typography.titleMedium)
                            Text("Paired ${DateFormat.getDateTimeInstance().format(Date(device.pairedAt))}")
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { onConnect(device) }) { Text("Connect") }
                                OutlinedButton(onClick = { onRevoke(device) }) { Text("Revoke") }
                            }
                        }
                    }
                }
            }
        }
    }
}
