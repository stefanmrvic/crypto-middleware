package com.example.cryptomiddleman

import android.graphics.Bitmap
import android.graphics.Color
import io.nayuki.qrcodegen.QrCode

object QRCodeGenerator {

    fun generateQRCode(text: String, size: Int = 800): Bitmap {
        // Generate QR code
        val qr = QrCode.encodeText(text, QrCode.Ecc.MEDIUM)

        val border = 4
        val scale = size / (qr.size + border * 2)

        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        bitmap.eraseColor(Color.WHITE)

        for (y in 0 until qr.size) {
            for (x in 0 until qr.size) {
                if (qr.getModule(x, y)) {
                    val startX = (x + border) * scale
                    val startY = (y + border) * scale

                    for (dy in 0 until scale) {
                        for (dx in 0 until scale) {
                            val px = startX + dx
                            val py = startY + dy
                            if (px < size && py < size) {
                                bitmap.setPixel(px, py, Color.BLACK)
                            }
                        }
                    }
                }
            }
        }

        return bitmap
    }
}