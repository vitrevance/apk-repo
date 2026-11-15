package com.apkrepo.backend.ingestion

import com.reandroid.apk.ApkModule
import com.reandroid.arsc.chunk.TableBlock
import com.reandroid.arsc.chunk.xml.AndroidManifestBlock
import com.reandroid.arsc.value.Entry
import com.reandroid.arsc.value.ValueType
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Resolves the application icon from the APK resource table (plan §5 step 5).
 *
 * Picks the highest-density entry whose value is a browser-friendly raster
 * file (png/webp/jpg) inside the archive. Adaptive/vector icons (XML entries)
 * and missing or unreadable icons are tolerated: the method returns null and
 * the catalog shows a placeholder — an otherwise valid release is never
 * blocked by icon problems. Rendering of adaptive icons is deferred to the
 * Phase 3 fdroidserver publisher.
 */
@Component
class IconExtractor {

    fun extract(module: ApkModule, manifest: AndroidManifestBlock): ExtractedIcon? {
        val iconResourceId = manifest.iconResourceId
        if (iconResourceId == 0) {
            return null
        }
        val table: TableBlock = module.tableBlock ?: return null
        val resourceEntry = table.getResource(iconResourceId) ?: return null

        var bestDensity = -1
        var bestPath: String? = null
        for (entry: Entry in resourceEntry) {
            try {
                if (entry.valueType != ValueType.STRING) continue
                val path = entry.getValueAsString() ?: continue
                if (!path.startsWith("res/")) continue
                val extension = normalizedExtension(path) ?: continue
                val density = entry.resConfig?.density?.flag ?: 0
                // Only real pixel densities rank; anydpi/nodpi (flags 0xfffe/0xffff) map to 0.
                val ranked = if (density in 1..MAX_DENSITY) density else 0
                if (bestPath == null || ranked > bestDensity) {
                    bestDensity = ranked
                    bestPath = path
                }
            } catch (_: Exception) {
                // Unreadable entries never block ingestion.
            }
        }

        val iconPath = bestPath ?: return null
        val extension = normalizedExtension(iconPath) ?: return null
        return try {
            val source = module.getInputSource(iconPath) ?: return null
            val bytes = source.openStream().use { it.readBytes() }
            if (bytes.isEmpty()) null else ExtractedIcon(
                data = bytes,
                mediaType = mediaTypeFor(extension),
                extension = extension,
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun normalizedExtension(path: String): String? {
        val ext = path.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "png" -> "png"
            "webp" -> "webp"
            "jpg", "jpeg" -> "jpg"
            else -> null // xml (adaptive/vector), gif and anything else unsupported
        }
    }

    private fun mediaTypeFor(extension: String): String = when (extension) {
        "png" -> "image/png"
        "webp" -> "image/webp"
        else -> "image/jpeg"
    }

    companion object {
        private val log = LoggerFactory.getLogger(IconExtractor::class.java)
        private const val MAX_DENSITY = 640 // xxxhdpi
    }
}
