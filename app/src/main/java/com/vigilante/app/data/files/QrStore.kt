package com.vigilante.app.data.files

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.vigilante.app.core.AppFolders
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * QR files live at Vigilante/QR/VOL-000001.png and contain ONLY the
 * volunteer ID — no personal data (SRS ch. 8, final revision).
 * A missing QR file is recreated automatically (SRS ch. 48).
 */
@Singleton
class QrStore @Inject constructor(private val folders: AppFolders) {

    fun ensureQr(volunteerId: String): File {
        val file = folders.qrFile(volunteerId)
        if (!file.exists()) generate(volunteerId, file)
        return file
    }

    fun regenerate(volunteerId: String): File {
        val file = folders.qrFile(volunteerId)
        generate(volunteerId, file)
        return file
    }

    fun delete(volunteerId: String) {
        folders.qrFile(volunteerId).delete()
    }

    fun bitmapFor(volunteerId: String, size: Int = 512): Bitmap = render(volunteerId, size)

    private fun generate(volunteerId: String, target: File) {
        folders.ensureAll()
        val bitmap = render(volunteerId, 512)
        val tmp = File(target.parentFile, target.name + ".tmp")
        FileOutputStream(tmp).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        if (!tmp.renameTo(target)) {         // atomic file write
            tmp.copyTo(target, overwrite = true)
            tmp.delete()
        }
    }

    private fun render(content: String, size: Int): Bitmap {
        val hints = mapOf(
            EncodeHintType.MARGIN to 1,
            EncodeHintType.CHARACTER_SET to "UTF-8"
        )
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        for (x in 0 until size) for (y in 0 until size) {
            bitmap.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
        }
        return bitmap
    }
}
