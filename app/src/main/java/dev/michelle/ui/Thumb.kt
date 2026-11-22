package dev.michelle.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val cache = LruCache<String, Bitmap>(64)

/** A photo from the app's files, decoded at about [px] pixels on its short edge, cached. */
@Composable
fun Photo(path: String?, px: Int, modifier: Modifier = Modifier, crop: Boolean = true) {
    val key = "$path@$px"
    val bitmap by produceState(cache.get(key), key) {
        if (value == null && path != null) value = withContext(Dispatchers.IO) { decode(path, px) }?.also { cache.put(key, it) }
    }
    val b = bitmap
    if (b == null) Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant))
    else Image(b.asImageBitmap(), null, modifier, contentScale = if (crop) ContentScale.Crop else ContentScale.Fit)
}

private fun decode(path: String, px: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0) return null
    var sample = 1
    while (minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= px) sample *= 2
    return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
}
