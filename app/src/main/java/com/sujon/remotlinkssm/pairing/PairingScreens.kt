package com.sujon.remotlinkssm.pairing

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.sujon.remotlinkssm.data.local.TrustedDeviceEntity
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.Executors

@Composable
fun CameraPairingScreen(repository: PairingRepository, onBack: () -> Unit) {
    var session by remember { mutableStateOf<PairingSession?>(null) }
    var code by remember { mutableStateOf("") }
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(Unit) {
        val (newSession, newCode) = repository.newSession()
        session = newSession
        code = newCode
        qrBitmap = QrCodeGenerator.createBitmap(newSession.toQrPayload())
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Pair this Camera Device", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text("On the Controller phone, scan this QR and enter the 6-digit code shown below.")
        Spacer(Modifier.height(18.dp))
        qrBitmap?.let { Image(it.asImageBitmap(), "RemoteLink pairing QR", Modifier.size(280.dp)) }
        Spacer(Modifier.height(18.dp))
        Text("Pairing code", style = MaterialTheme.typography.labelLarge)
        Text(code, style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(8.dp))
        Text(session?.deviceName ?: "Preparing device…")
        Spacer(Modifier.weight(1f))
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
    }
}

@Composable
fun ControllerPairingScreen(
    repository: PairingRepository,
    onBack: () -> Unit,
    onPaired: () -> Unit
) {
    var scannedSession by remember { mutableStateOf<PairingSession?>(null) }
    var code by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var scanning by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    if (scannedSession == null && scanning) {
        QrScannerScreen(
            onResult = { raw ->
                try {
                    scannedSession = PairingSession.fromQrPayload(raw)
                    scanning = false
                    error = null
                } catch (e: Exception) {
                    error = e.message ?: "Invalid QR code"
                }
            },
            onBack = onBack
        )
        return
    }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("Confirm pairing", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(10.dp))
        Text("Verify the device name and enter the 6-digit code shown on the Camera Device.")
        Spacer(Modifier.height(20.dp))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp)) {
                Text(scannedSession?.deviceName ?: "Unknown device", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(6.dp))
                Text("Device ID: ${scannedSession?.deviceId ?: "—"}")
            }
        }
        Spacer(Modifier.height(18.dp))
        OutlinedTextField(
            value = code,
            onValueChange = { value ->
                if (value.length <= 6 && value.all(Char::isDigit)) code = value
                error = null
            },
            label = { Text("6-digit pairing code") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(18.dp))
        Button(
            onClick = {
                val current = scannedSession ?: return@Button
                if (PairingCode.sha256(code) != current.codeHash) {
                    error = "Pairing code does not match."
                } else {
                    scope.launch {
                        repository.trust(current)
                        onPaired()
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = scannedSession != null && code.length == 6
        ) { Text("Trust this device") }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = { scannedSession = null; code = ""; error = null; scanning = true },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Scan again") }
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
    }
}

@Composable
fun ControllerIdentityQrScreen(repository: PairingRepository, onBack: () -> Unit) {
    val identity = remember { repository.localIdentity() }
    val bitmap = remember(identity) { QrCodeGenerator.createBitmap(identity.toPayload()) }
    Column(
        Modifier.fillMaxSize().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Add this Controller to the Camera", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(10.dp))
        Text("On the Camera Device, choose “Scan Controller QR”. Only the public device identity is shared.")
        Spacer(Modifier.height(18.dp))
        Image(bitmap.asImageBitmap(), "Controller identity QR", Modifier.size(280.dp))
        Spacer(Modifier.height(16.dp))
        Text(identity.deviceName, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.weight(1f))
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Done") }
    }
}

@Composable
fun CameraControllerIdentityScanScreen(
    repository: PairingRepository,
    onBack: () -> Unit,
    onTrusted: () -> Unit
) {
    var scanned by remember { mutableStateOf<DeviceIdentityQr?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    if (scanned == null) {
        QrScannerScreen(
            onResult = { raw ->
                try {
                    scanned = DeviceIdentityQr.fromPayload(raw)
                    error = null
                } catch (e: Exception) {
                    error = e.message ?: "Invalid identity QR"
                }
            },
            onBack = onBack
        )
        return
    }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("Confirm Controller", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp)) {
                Text(scanned!!.deviceName, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(6.dp))
                Text("Device ID: ${scanned!!.deviceId}")
            }
        }
        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(18.dp))
        Button(
            onClick = {
                scope.launch {
                    runCatching { repository.trustIdentity(scanned!!) }
                        .onSuccess { onTrusted() }
                        .onFailure { error = it.message ?: "Unable to trust Controller" }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Trust Controller") }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
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
                Button(onClick = onPair) { Text("Pair") }
            }
        }
        Spacer(Modifier.height(12.dp))
        if (devices.isEmpty()) {
            Text("No trusted devices yet. Pair a Camera Device with QR + 6-digit verification.")
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

@Composable
private fun QrScannerScreen(onResult: (String) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val cameraAllowed = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    if (!cameraAllowed) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
            Text("Camera permission is required to scan the pairing QR.")
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = onBack) { Text("Back") }
        }
        return
    }

    AndroidView(
        factory = { ctx ->
            PreviewView(ctx).also { previewView ->
                val future = ProcessCameraProvider.getInstance(ctx)
                future.addListener({
                    val provider = future.get()
                    val preview = Preview.Builder().build().also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }
                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also { it.setAnalyzer(executor, QrScannerAnalyzer(onResult)) }
                    provider.unbindAll()
                    provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                }, ContextCompat.getMainExecutor(ctx))
            }
        },
        modifier = Modifier.fillMaxSize()
    )

    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.Bottom) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Scan RemoteLink QR", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(6.dp))
                Text("Align the QR code inside the camera view.")
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
            }
        }
    }
}
