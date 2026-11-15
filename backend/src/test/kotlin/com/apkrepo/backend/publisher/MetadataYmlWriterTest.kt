package com.apkrepo.backend.publisher

import com.apkrepo.backend.config.ApkRepoProperties
import com.apkrepo.backend.domain.App
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/**
 * Unit tests for the fdroidserver metadata yml rendering (plan §8:
 * unicode, multiline description, quoting, package name guard).
 */
class MetadataYmlWriterTest {

    @TempDir
    lateinit var tempDir: Path

    private fun writer(): MetadataYmlWriter =
        MetadataYmlWriter(FdroidWorkspace(properties()))

    private fun properties(): ApkRepoProperties = ApkRepoProperties(
        storage = ApkRepoProperties.Storage(root = tempDir.toString()),
    )

    private fun app(
        packageName: String = "com.example.alpha",
        displayName: String? = "Alpha App",
        description: String? = null,
    ) = App(packageName = packageName, displayName = displayName, description = description)

    @Test
    fun `renders name summary and license without description`() {
        val yml = writer().render(app())
        assertTrue(yml.startsWith("Name: Alpha App\n"))
        assertTrue(yml.contains("Summary: Alpha App\n"))
        assertFalse(yml.contains("Description:"))
        assertTrue(yml.contains("License: Unknown\n"))
    }

    @Test
    fun `summary uses first line of description`() {
        val yml = writer().render(app(description = "First line of the description.\nSecond line."))
        assertTrue(yml.contains("Summary: First line of the description.\n"))
        assertTrue(yml.contains("Description: |-\n"))
        assertTrue(yml.contains("  First line of the description.\n"))
        assertTrue(yml.contains("  Second line.\n"))
    }

    @Test
    fun `renders unicode labels`() {
        val yml = writer().render(app(displayName = "Älpümöpö"))
        assertTrue(yml.contains("Name: Älpümöpö\n"))
    }

    @Test
    fun `quotes values containing colon and hash`() {
        val yml = writer().render(app(displayName = "App: the #1 tool"))
        assertTrue(yml.contains("Name: \"App: the #1 tool\"\n"))
    }

    @Test
    fun `rejects invalid package names`() {
        val ex = assertThrows(IllegalArgumentException::class.java) {
            writer().render(app(packageName = "../evil"))
        }
        assertTrue(ex.message!!.contains("invalid package name"))
    }

    @Test
    fun `writeAll writes package-named yml files`() {
        val writer = writer()
        writer.writeAll(
            listOf(
                app(packageName = "com.example.one"),
                app(packageName = "com.example.two", displayName = "Two"),
            ),
        )
        assertTrue(Files.exists(tempDir.resolve("fdroid/metadata/com.example.one.yml")))
        assertTrue(Files.exists(tempDir.resolve("fdroid/metadata/com.example.two.yml")))
        assertEquals(
            "Name: Two\nSummary: Two\nLicense: Unknown\n",
            Files.readString(tempDir.resolve("fdroid/metadata/com.example.two.yml")),
        )
    }
}
