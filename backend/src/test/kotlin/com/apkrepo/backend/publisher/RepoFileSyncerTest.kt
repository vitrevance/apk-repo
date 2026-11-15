package com.apkrepo.backend.publisher

import com.apkrepo.backend.config.ApkRepoProperties
import com.apkrepo.backend.storage.StorageService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

/**
 * Unit tests for the repo directory sync (plan §8): hardlink creation,
 * stale file removal, index files untouched, copy fallback.
 */
class RepoFileSyncerTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var syncer: RepoFileSyncer
    private lateinit var storageService: StorageService

    private fun setUp() {
        val properties = ApkRepoProperties(
            storage = ApkRepoProperties.Storage(root = tempDir.toString()),
        )
        storageService = StorageService(properties)
        syncer = RepoFileSyncer(FdroidWorkspace(properties), storageService)
    }

    private fun storeApk(sha256: String, bytes: ByteArray): Path {
        val staged = Files.createTempFile(tempDir, "staged-", ".part")
        Files.write(staged, bytes)
        return storageService.promoteApkToStore(staged, sha256)
    }

    private fun entry(packageName: String, versionCode: Long, sha256: String) =
        RepoFileSyncer.SyncEntry(
            releaseId = UUID.randomUUID(),
            packageName = packageName,
            versionCode = versionCode,
            apkSha256 = sha256,
        )

    private fun repoDir(): Path = tempDir.resolve("fdroid/repo")

    @Test
    fun `links publishable releases into the repo with identical content`() {
        setUp()
        val bytes = "fake-apk-bytes-1".toByteArray()
        storeApk("aa11", bytes)

        val report = syncer.sync(listOf(entry("com.example.alpha", 42, "aa11")))

        val linked = repoDir().resolve("com.example.alpha_42.apk")
        assertTrue(Files.exists(linked))
        assertArrayEquals(bytes, Files.readAllBytes(linked))
        assertEquals(listOf(report.publishableReleaseIds.single()), report.publishableReleaseIds)
        assertEquals(setOf("com.example.alpha"), report.publishablePackageNames)
    }

    @Test
    fun `removes repo apks for unpublished releases but keeps index files`() {
        setUp()
        storeApk("aa11", "one".toByteArray())
        storeApk("bb22", "two".toByteArray())

        // First sync publishes both.
        syncer.sync(
            listOf(
                entry("com.example.alpha", 1, "aa11"),
                entry("com.example.beta", 2, "bb22"),
            ),
        )
        // fdroidserver-owned output that must never be touched.
        val entryJar = repoDir().resolve("entry.jar")
        val indexV2 = repoDir().resolve("index-v2.json")
        Files.write(entryJar, "signed".toByteArray())
        Files.write(indexV2, "{}".toByteArray())

        // Second sync unpublishes beta.
        val report = syncer.sync(listOf(entry("com.example.alpha", 1, "aa11")))

        assertFalse(Files.exists(repoDir().resolve("com.example.beta_2.apk")))
        assertTrue(Files.exists(repoDir().resolve("com.example.alpha_1.apk")))
        assertTrue(report.removedRepoFiles.contains("com.example.beta_2.apk"))
        assertTrue(Files.exists(entryJar))
        assertTrue(Files.exists(indexV2))
    }

    @Test
    fun `removes stale metadata yml for packages without releases`() {
        setUp()
        val metadataDir = tempDir.resolve("fdroid/metadata")
        Files.createDirectories(metadataDir)
        Files.writeString(metadataDir.resolve("com.example.gone.yml"), "Name: Gone\n")

        val report = syncer.sync(emptyList())

        assertFalse(Files.exists(metadataDir.resolve("com.example.gone.yml")))
        assertTrue(report.removedMetadataFiles.contains("com.example.gone.yml"))
    }

    @Test
    fun `falls back to copying when hardlinks are unavailable`() {
        setUp()
        val bytes = "fallback-copy".toByteArray()
        storeApk("cc33", bytes)
        syncer.linkStrategy = { _, _ -> false }

        syncer.sync(listOf(entry("com.example.gamma", 7, "cc33")))

        val copied = repoDir().resolve("com.example.gamma_7.apk")
        assertTrue(Files.exists(copied))
        assertArrayEquals(bytes, Files.readAllBytes(copied))
    }

    @Test
    fun `fails when the stored apk is missing`() {
        setUp()
        val ex = org.junit.jupiter.api.Assertions.assertThrows(PublisherException::class.java) {
            syncer.sync(listOf(entry("com.example.delta", 9, "dd44")))
        }
        assertTrue(ex.message!!.contains("missing from the store"))
    }

    private fun assertArrayEquals(expected: ByteArray, actual: ByteArray) {
        assertTrue(expected.contentEquals(actual), "expected ${String(expected)} but got ${String(actual)}")
    }
}
