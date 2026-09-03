package com.sujon.remotlinkssm.pairing

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.WriterException

object QrCodeGenerator {
    fun createBitmap(content: String, size: Int = 720): Bitmap {
        val matrix = try {
            MultiFormatWriter().encode(content, BarcodeFormat.QR_CODE, size, size)
        } catch (e: WriterException) {
            throw IllegalArgumentException("Unable to generate QR code", e)
        }

        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bitmap.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }
        return bitmap
    }
}
