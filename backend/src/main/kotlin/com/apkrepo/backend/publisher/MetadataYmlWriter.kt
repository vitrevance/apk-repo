package com.apkrepo.backend.publisher

import com.apkrepo.backend.domain.App
import org.springframework.stereotype.Component
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * Renders fdroidserver binary-repo metadata files (metadata/package.yml)
 * from the App/Release database rows. The database is the source of
 * truth; files are regenerated from it before every fdroid update, so
 * admin edits to display name/description flow into the published index.
 */
@Component
class MetadataYmlWriter(private val workspace: FdroidWorkspace) {

    /** Renders the yml document for one app (pure; no filesystem access). */
    fun render(app: App): String {
        require(PACKAGE_NAME_REGEX.matches(app.packageName)) {
            "Refusing to write metadata for invalid package name: ${app.packageName}"
        }
        val name = app.displayName ?: app.packageName
        val summary = app.description
            ?.lineSequence()
            ?.firstOrNull { it.isNotBlank() }
            ?.take(SUMMARY_MAX_LENGTH)
            ?: name

        val sb = StringBuilder()
        appendValue(sb, "Name", name)
        appendValue(sb, "Summary", summary)
        val description = app.description
        if (!description.isNullOrBlank()) {
            sb.append("Description: |-\n")
            description.lines().forEach { line -> sb.append("  ").append(line).append('\n') }
        }
        // fdroidserver requires a License key for binary repos; the admin
        // has not provided one anywhere in the MVP data model.
        sb.append("License: Unknown\n")
        return sb.toString()
    }

    /** Writes metadata/packageName.yml for every app (atomically). */
    fun writeAll(apps: List<App>) {
        Files.createDirectories(workspace.metadataDir)
        apps.forEach { app ->
            val target = workspace.metadataDir.resolve("${app.packageName}.yml")
            writeAtomically(target, render(app))
        }
    }

    /**
     * Emits "key: value", double-quoting the value when it contains
     * characters that would break a YAML plain scalar (":", "#", quotes,
     * leading indicators, leading/trailing whitespace).
     */
    private fun appendValue(sb: StringBuilder, key: String, value: String) {
        sb.append(key).append(": ")
        if (needsQuoting(value)) {
            sb.append('"')
                .append(value.replace("\\", "\\\\").replace("\"", "\\\""))
                .append('"')
        } else {
            sb.append(value)
        }
        sb.append('\n')
    }

    private fun needsQuoting(value: String): Boolean {
        if (value.isEmpty()) return true
        if (value != value.trim()) return true
        if (value.contains(':') || value.contains('#') || value.contains('"')) return true
        if (value.contains('\\')) return true
        val first = value.first()
        if (first in INDICATOR_CHARS) return true
        return false
    }

    private fun writeAtomically(target: Path, content: String) {
        val temp = Files.createTempFile(target.parent, target.fileName.toString(), ".tmp")
        Files.writeString(temp, content)
        try {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    companion object {
        /** Android package name: dot-separated Java identifier segments. */
        val PACKAGE_NAME_REGEX = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+$")

        private const val SUMMARY_MAX_LENGTH = 80

        /** Characters that start a YAML indicator construct. */
        private const val INDICATOR_CHARS = "-?&*!|>%@`{}[],'"
    }
}
