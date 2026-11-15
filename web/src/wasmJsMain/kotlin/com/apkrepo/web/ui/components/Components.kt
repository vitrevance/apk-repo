@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.apkrepo.web.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.apkrepo.web.api.ApiClient
import kotlinx.browser.window
import org.jetbrains.skia.Image

/**
 * Reusable building blocks shared by all screens.
 */

/** Fetches image bytes and decodes them with Skia (works for PNG/WebP/JPEG). */
@Composable
fun rememberNetworkImage(url: String?): ImageBitmap? {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, url) {
        value = url?.let { u ->
            ApiClient.fetchBytes(u)?.let { bytes ->
                runCatching { Image.makeFromEncoded(bytes).toComposeImageBitmap() }.getOrNull()
            }
        }
    }
    return bitmap
}

/** Square app icon with a letter placeholder fallback. */
@Composable
fun AppIcon(iconUrl: String?, name: String?, size: Dp = 56.dp, modifier: Modifier = Modifier) {
    val bitmap = rememberNetworkImage(iconUrl)
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = "$name icon",
                modifier = Modifier.size(size),
                contentScale = ContentScale.Crop,
            )
        } else {
            Text(
                text = (name ?: "?").take(1).uppercase(),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

/** Monospaced, scrollable value with a copy-to-clipboard button. */
@Composable
fun CopyField(label: String, value: String, modifier: Modifier = Modifier) {
    var copied by remember { mutableStateOf(false) }
    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                value,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .horizontalScroll(rememberScrollState()),
            )
            OutlinedButton(
                onClick = {
                    window.navigator.clipboard.writeText(value)
                    copied = true
                },
                modifier = Modifier.padding(start = 8.dp),
            ) {
                Text(if (copied) "Copied!" else "Copy")
            }
        }
    }
}

/** Centered progress indicator for loading states. */
@Composable
fun LoadingPane(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

/** Error message with an optional retry action. */
@Composable
fun ErrorPane(message: String, retry: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "Something went wrong",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error,
            )
            Text(message, style = MaterialTheme.typography.bodyMedium)
            if (retry != null) {
                OutlinedButton(onClick = retry) { Text("Retry") }
            }
        }
    }
}

/** Colored status chip for release/job states. */
@Composable
fun StatusChip(status: String) {
    val bg: androidx.compose.ui.graphics.Color
    val fg: androidx.compose.ui.graphics.Color
    when (status) {
        "PUBLISHED", "COMPLETED" -> {
            bg = MaterialTheme.colorScheme.primaryContainer
            fg = MaterialTheme.colorScheme.onPrimaryContainer
        }
        "READY", "RUNNING", "PENDING" -> {
            bg = MaterialTheme.colorScheme.tertiaryContainer
            fg = MaterialTheme.colorScheme.onTertiaryContainer
        }
        "FAILED", "UNPUBLISHED" -> {
            bg = MaterialTheme.colorScheme.errorContainer
            fg = MaterialTheme.colorScheme.onErrorContainer
        }
        else -> {
            bg = MaterialTheme.colorScheme.surfaceVariant
            fg = MaterialTheme.colorScheme.onSurfaceVariant
        }
    }
    Text(
        status,
        style = MaterialTheme.typography.labelSmall,
        color = fg,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .padding(PaddingValues(horizontal = 8.dp, vertical = 2.dp)),
    )
}

/** 1234567 -> "1.2 MB". */
fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "${kb.toInt()} KB"
    val mb = kb / 1024.0
    if (mb < 1024) {
        val tenths = (mb * 10 + 0.5).toInt()
        return "${tenths / 10}.${tenths % 10} MB"
    }
    val gb = mb / 1024.0
    val tenths = (gb * 10 + 0.5).toInt()
    return "${tenths / 10}.${tenths % 10} GB"
}

/** ISO-8601 instant string -> "2026-11-14 18:30 UTC" (best effort). */
fun formatInstant(iso: String?): String {
    if (iso == null) return "—"
    return runCatching {
        val idx = iso.indexOf('T')
        if (idx > 0) {
            iso.substring(0, idx) + " " + iso.substring(idx + 1).take(5) + " UTC"
        } else {
            iso
        }
    }.getOrDefault(iso)
}
