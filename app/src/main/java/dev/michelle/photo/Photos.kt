package dev.michelle.photo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The photos: the camera app writes into cache/capture (through a FileProvider), then the picture is
 * turned upright, shrunk to 1600 px on its long edge and kept as a JPEG in files/photos (R11).
 */
class Photos(private val context: Context) {
    private val captures = File(context.cacheDir, "capture").apply { mkdirs() }
    private val photos = File(context.filesDir, "photos").apply { mkdirs() }

    /** A new file for the camera app to write into, and its content URI. */
    fun newCapture(): Pair<File, Uri> {
        val file = File(captures, "${UUID.randomUUID()}.jpg")
        return file to FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    }

    /** The captured picture, upright and at most 1600 px, kept as a photo; the capture is deleted. */
    fun keep(capture: File): File? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(capture.path, bounds)
        if (bounds.outWidth <= 0) null else {
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= LONG_EDGE) sample *= 2
            val decoded = BitmapFactory.decodeFile(capture.path, BitmapFactory.Options().apply { inSampleSize = sample })
            val upright = rotate(decoded, ExifInterface(capture.path).rotationDegrees)
            val scaled = shrink(upright)
            val out = File(photos, "${UUID.randomUUID()}.jpg")
            out.outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            out
        }
    } finally {
        capture.delete()
    }

    fun delete(path: String?) {
        if (path != null) File(path).delete()
    }

    private fun rotate(b: Bitmap, degrees: Int): Bitmap =
        if (degrees == 0) b else Bitmap.createBitmap(b, 0, 0, b.width, b.height, Matrix().apply { postRotate(degrees.toFloat()) }, true)

    private fun shrink(b: Bitmap): Bitmap {
        val long = max(b.width, b.height)
        if (long <= LONG_EDGE) return b
        val f = LONG_EDGE.toFloat() / long
        return Bitmap.createScaledBitmap(b, (b.width * f).roundToInt(), (b.height * f).roundToInt(), true)
    }

    companion object {
        const val LONG_EDGE = 1600
    }
}
