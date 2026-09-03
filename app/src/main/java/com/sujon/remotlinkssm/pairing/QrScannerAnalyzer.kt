package com.sujon.remotlinkssm.pairing

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage

class QrScannerAnalyzer(
    private val onResult: (String) -> Unit,
    private val onError: (Throwable) -> Unit = {}
) : ImageAnalysis.Analyzer {
    private val scanner = BarcodeScanning.getClient()
    private var delivered = false

    override fun analyze(image: ImageProxy) {
        if (delivered) {
            image.close()
            return
        }

        val mediaImage = image.image
        if (mediaImage == null) {
            image.close()
            return
        }

        val input = InputImage.fromMediaImage(mediaImage, image.imageInfo.rotationDegrees)
        scanner.process(input)
            .addOnSuccessListener { barcodes ->
                val value = barcodes.firstOrNull { it.rawValue?.isNotBlank() == true }?.rawValue
                if (!value.isNullOrBlank() && !delivered) {
                    delivered = true
                    onResult(value)
                }
            }
            .addOnFailureListener(onError)
            .addOnCompleteListener { image.close() }
    }
}
