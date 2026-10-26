package com.example.callmind.features.contacts

import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.callmind.ui.theme.contactAvatar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Small in-memory cache so scrolling a long list doesn't decode the same photo again and again. */
private val photoCache = object : LruCache<String, ImageBitmap>(200) {}

/** Contact photo when there is one, otherwise a grey circle with the initial (or a person icon). */
@Composable
fun ContactAvatar(
    name: String?,
    photoUri: String?,
    size: Dp = 36.dp,
    textSize: TextUnit = 17.sp
) {
    val context = LocalContext.current
    val photo by produceState<ImageBitmap?>(initialValue = photoUri?.let { photoCache.get(it) }, key1 = photoUri) {
        if (photoUri == null || value != null) return@produceState
        value = withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openInputStream(Uri.parse(photoUri))?.use { stream ->
                    BitmapFactory.decodeStream(stream)?.asImageBitmap()
                }?.also { photoCache.put(photoUri, it) }
            } catch (e: Exception) {
                null
            }
        }
    }

    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.contactAvatar),
        contentAlignment = Alignment.Center
    ) {
        val bitmap = photo
        val initial = name?.trim()?.firstOrNull()?.takeIf { it.isLetterOrDigit() }?.uppercaseChar()?.toString()
        when {
            bitmap != null -> Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size)
            )
            !initial.isNullOrEmpty() -> Text(
                text = initial,
                fontSize = textSize,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
            )
            else -> Icon(
                Icons.Default.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                modifier = Modifier.size(size * 0.55f)
            )
        }
    }
}
