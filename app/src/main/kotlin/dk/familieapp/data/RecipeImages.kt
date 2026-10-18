package dk.familieapp.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Base64
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Billeder af retterne. Gemmes som komprimeret JPEG (base64) i familiens data, ét dokument pr. opskrift,
 * så der ikke skal sættes Firebase Storage op. Et billede fylder typisk 100–250 kB.
 */
object RecipeImages {
    private const val MAX_SIDE = 1024
    /** Firestore tillader højst 1 MB pr. dokument. */
    private const val MAX_BASE64 = 850_000

    suspend fun fromUri(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@runCatching null
            val rotation = context.contentResolver.openInputStream(uri)?.use { exifRotation(ExifInterface(it)) } ?: 0
            encode(decodeScaled(bytes) ?: return@runCatching null, rotation)
        }.getOrNull()
    }

    suspend fun fromUrl(url: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val connection = URL(url).openConnection() as HttpURLConnection
            val bytes = try {
                connection.connectTimeout = 10_000
                connection.readTimeout = 15_000
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) Familien-app")
                connection.inputStream.use { it.readBytes() }
            } finally {
                connection.disconnect()
            }
            encode(decodeScaled(bytes) ?: return@runCatching null, 0)
        }.getOrNull()
    }

    fun decode(base64: String): Bitmap? = runCatching {
        val bytes = Base64.decode(base64, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }.getOrNull()

    private fun exifRotation(exif: ExifInterface): Int = when (
        exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    ) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90
        ExifInterface.ORIENTATION_ROTATE_180 -> 180
        ExifInterface.ORIENTATION_ROTATE_270 -> 270
        else -> 0
    }

    /** Læser billedet i en passende lav opløsning, så store kamerabilleder ikke fylder hukommelsen. */
    private fun decodeScaled(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    private fun encode(source: Bitmap, rotation: Int): String? {
        val scale = MAX_SIDE.toFloat() / maxOf(source.width, source.height)
        val matrix = Matrix().apply {
            if (scale < 1f) postScale(scale, scale)
            if (rotation != 0) postRotate(rotation.toFloat())
        }
        val bitmap = Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
        for (quality in listOf(80, 65, 50, 35)) {
            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            val text = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
            if (text.length <= MAX_BASE64) return text
        }
        return null
    }
}
