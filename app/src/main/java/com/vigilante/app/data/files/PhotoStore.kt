package com.vigilante.app.data.files

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.vigilante.app.core.AppFolders
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

/**
 * Photos live at Vigilante/Photos/VOL-000001.jpg (SRS ch. 47).
 * On save the image is downscaled, recompressed and re-encoded — re-encoding
 * drops all EXIF metadata (location etc.) as the SRS requires.
 */
@Singleton
class PhotoStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val folders: AppFolders
) {
    fun photoFile(volunteerId: String): File = folders.photoFile(volunteerId)

    fun hasPhoto(volunteerId: String): Boolean = photoFile(volunteerId).exists()

    /** Returns the stored relative path ("Photos/VOL-000001.jpg"). */
    fun saveFromUri(volunteerId: String, uri: Uri): String {
        folders.ensureAll()
        val source = context.contentResolver.openInputStream(uri)
            ?: error("تعذر فتح الصورة")
        val original = source.use { BitmapFactory.decodeStream(it) }
            ?: error("ملف الصورة غير صالح")

        val scaled = scaleDown(original, MAX_DIMENSION)
        val target = photoFile(volunteerId)
        val tmp = File(target.parentFile, target.name + ".tmp")
        FileOutputStream(tmp).use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        }
        if (!tmp.renameTo(target)) {
            tmp.copyTo(target, overwrite = true)
            tmp.delete()
        }
        if (scaled !== original) original.recycle()
        return "Photos/$volunteerId.jpg"
    }

    fun delete(volunteerId: String) {
        photoFile(volunteerId).delete()
    }

    private fun scaleDown(bitmap: Bitmap, maxDim: Int): Bitmap {
        val largest = max(bitmap.width, bitmap.height)
        if (largest <= maxDim) return bitmap
        val scale = maxDim.toFloat() / largest
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).toInt(),
            (bitmap.height * scale).toInt(),
            true
        )
    }

    companion object {
        private const val MAX_DIMENSION = 1024
        private const val JPEG_QUALITY = 82
    }
}
