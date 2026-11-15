package com.apkrepo.backend

import com.apkrepo.backend.testutil.TestApkFactory
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.core.io.ByteArrayResource
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.util.LinkedMultiValueMap
import org.springframework.util.MultiValueMap
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.nio.file.Files
import java.security.MessageDigest
import java.util.Base64
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue

/**
 * Phase 2 end-to-end integration tests: the full upload pipeline against a
 * real PostgreSQL (Testcontainers), with programmatically generated APK
 * fixtures covering the plan's scenarios.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("docker")
@Testcontainers
class IngestionApiTests {

    @Autowired
    lateinit var restTemplate: TestRestTemplate

    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:17-alpine")

        private const val USERNAME = "admin"
        private const val PASSWORD = "admin-password"

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
            registry.add("apk-repo.admin.username") { USERNAME }
            registry.add("apk-repo.admin.password") { PASSWORD }
            registry.add("apk-repo.storage.root") { Files.createTempDirectory("apk-storage").toAbsolutePath().toString() }
            registry.add("apk-repo.publisher.enabled") { "false" }
            // The docker profile sets apk-repo.web.static-dir=/app/web, which
            // only exists inside the image; blank disables SPA serving
            // (WebMvcConfig treats blank as unset).
            registry.add("apk-repo.web.static-dir") { "" }
        }
    }

    private fun adminHeaders(): HttpHeaders = HttpHeaders().apply { setBasicAuth(USERNAME, PASSWORD) }

    private fun uploadEntity(bytes: ByteArray, fileName: String): HttpEntity<MultiValueMap<String, Any>> {
        val headers = HttpHeaders().apply {
            contentType = MediaType.MULTIPART_FORM_DATA
            setBasicAuth(USERNAME, PASSWORD)
        }
        val body = LinkedMultiValueMap<String, Any>()
        body.add("file", object : ByteArrayResource(bytes) {
            override fun getFilename() = fileName
        })
        return HttpEntity(body, headers)
    }

    private fun uploadAdmin(bytes: ByteArray, fileName: String = "app.apk"): ResponseEntity<String> =
        restTemplate.exchange(
            "/api/v1/admin/releases",
            HttpMethod.POST,
            uploadEntity(bytes, fileName),
            String::class.java,
        )

    @Test
    fun `scenario 1 - full upload pipeline extracts metadata and stores immutable apk`() {
        val apk = Files.createTempDirectory("tmp").resolve("alpha-1.0.0.apk")
        TestApkFactory.buildApk(apk)
        val bytes = Files.readAllBytes(apk)

        val upload = uploadAdmin(bytes, "alpha-1.0.0.apk")
        assertEquals(201, upload.statusCode.value(), upload.body)
        assertTrue(upload.body!!.contains("\"packageName\":\"com.example.alpha\""))
        assertTrue(upload.body!!.contains("\"versionName\":\"1.0.0\""))
        assertTrue(upload.body!!.contains("\"status\":\"READY\""))
        assertTrue(upload.body!!.contains("\"duplicate\":false"))
        assertTrue(upload.body!!.contains("\"isNewApp\":true"))

        // Appears in public catalog with the resolved label and icon URL.
        val catalog = restTemplate.getForEntity("/api/v1/apps?query=alpha", String::class.java)
        assertEquals(200, catalog.statusCode.value())
        assertTrue(catalog.body!!.contains("com.example.alpha"))
        assertTrue(catalog.body!!.contains("Alpha App"))
        assertTrue(catalog.body!!.contains("/api/v1/icons/"))
    }

    @Test
    fun `scenario 2 - byte-identical re-upload is idempotent`() {
        val apk = Files.createTempDirectory("tmp").resolve("beta.apk")
        TestApkFactory.buildApk(apk, packageName = "com.example.beta", versionCode = 10)
        val bytes = Files.readAllBytes(apk)

        val first = uploadAdmin(bytes, "beta.apk")
        assertEquals(201, first.statusCode.value(), first.body)

        val second = uploadAdmin(bytes, "beta.apk")
        assertEquals(200, second.statusCode.value(), second.body)
        assertTrue(second.body!!.contains("\"duplicate\":true"))
        assertTrue(second.body!!.contains("\"isNewApp\":false"))
    }

    @Test
    fun `scenario 3 - same package and version with different content is rejected`() {
        val dir = Files.createTempDirectory("tmp")
        val apk1 = dir.resolve("dup-1.apk")
        TestApkFactory.buildApk(apk1, packageName = "com.example.dup", versionCode = 3)
        assertEquals(201, uploadAdmin(Files.readAllBytes(apk1), "dup-1.apk").statusCode.value())

        val apk2 = dir.resolve("dup-2.apk")
        TestApkFactory.buildApk(
            apk2,
            packageName = "com.example.dup",
            versionCode = 3,
            contentSalt = byteArrayOf(1, 2, 3, 4, 5),
        )
        val conflict = uploadAdmin(Files.readAllBytes(apk2), "dup-2.apk")
        assertEquals(409, conflict.statusCode.value(), conflict.body)
        assertTrue(conflict.body!!.contains("DUPLICATE_VERSION"))
    }

    @Test
    fun `scenario 4 - signer mismatch for an existing package is rejected`() {
        val dir = Files.createTempDirectory("tmp")
        val apk1 = dir.resolve("gamma-1.apk")
        TestApkFactory.buildApk(apk1, packageName = "com.example.gamma", versionCode = 1)
        assertEquals(201, uploadAdmin(Files.readAllBytes(apk1), "gamma-1.apk").statusCode.value())

        val apk2 = dir.resolve("gamma-2.apk")
        TestApkFactory.buildApk(apk2, packageName = "com.example.gamma", versionCode = 2, signer = TestApkFactory.OTHER_SIGNER)
        val conflict = uploadAdmin(Files.readAllBytes(apk2), "gamma-2.apk")
        assertEquals(409, conflict.statusCode.value(), conflict.body)
        assertTrue(conflict.body!!.contains("SIGNER_MISMATCH"))
    }

    @Test
    fun `scenario 5 - malformed apks are rejected with 422 and specific codes`() {
        val notApk = uploadAdmin(TestApkFactory.notAnApk(), "broken.apk")
        assertEquals(422, notApk.statusCode.value(), notApk.body)
        assertTrue(notApk.body!!.contains("NOT_AN_APK"))

        val noManifest = uploadAdmin(TestApkFactory.zipWithoutManifest(), "plain.zip.apk")
        assertEquals(422, noManifest.statusCode.value(), noManifest.body)
        assertTrue(noManifest.body!!.contains("MISSING_MANIFEST"))

        val splitLike = uploadAdmin(TestApkFactory.zipWithoutDex(), "split.apk")
        assertEquals(422, splitLike.statusCode.value(), splitLike.body)
        assertTrue(splitLike.body!!.contains("MISSING_DEX"))

        val unsigned = Files.createTempDirectory("tmp").resolve("unsigned.apk")
        TestApkFactory.buildApk(unsigned, signer = null, packageName = "com.example.unsigned")
        val unsignedResp = uploadAdmin(Files.readAllBytes(unsigned), "unsigned.apk")
        assertEquals(422, unsignedResp.statusCode.value(), unsignedResp.body)
        assertTrue(unsignedResp.body!!.contains("UNSIGNED_APK"))

        // No release rows for any of the failures.
        val list = restTemplate.exchange(
            "/api/v1/admin/releases",
            HttpMethod.GET,
            HttpEntity<Void>(adminHeaders()),
            String::class.java,
        )
        assertEquals(200, list.statusCode.value(), list.body)
        assertFalse(list.body!!.contains("com.example.unsigned"))
    }

    @Test
    fun `scenario 6 - admin edits catalog metadata without touching identity`() {
        val apk = Files.createTempDirectory("tmp").resolve("editme.apk")
        TestApkFactory.buildApk(apk, packageName = "com.example.editme", versionCode = 5)
        assertEquals(201, uploadAdmin(Files.readAllBytes(apk), "editme.apk").statusCode.value())

        val headers = adminHeaders().apply { contentType = MediaType.APPLICATION_JSON }
        val patch = restTemplate.exchange(
            "/api/v1/admin/apps/com.example.editme",
            HttpMethod.PATCH,
            HttpEntity(mapOf("displayName" to "Edited Name", "description" to "A custom description"), headers),
            String::class.java,
        )
        assertEquals(200, patch.statusCode.value(), patch.body)
        assertTrue(patch.body!!.contains("Edited Name"))

        val detail = restTemplate.getForEntity("/api/v1/apps/com.example.editme", String::class.java)
        assertEquals(200, detail.statusCode.value())
        assertTrue(detail.body!!.contains("Edited Name"))
        assertTrue(detail.body!!.contains("A custom description"))
        assertTrue(detail.body!!.contains("com.example.editme"))
    }

    @Test
    fun `scenario 7 - download returns byte-identical apk`() {
        val apk = Files.createTempDirectory("tmp").resolve("download-me.apk")
        TestApkFactory.buildApk(apk, packageName = "com.example.download", versionCode = 7)
        val bytes = Files.readAllBytes(apk)
        val upload = uploadAdmin(bytes, "download-me.apk")
        assertEquals(201, upload.statusCode.value(), upload.body)
        val releaseId = Regex("\"id\":\"([a-f0-9-]{36})\"").find(upload.body!!)!!.groupValues[1]

        val download = restTemplate.getForEntity("/api/v1/releases/$releaseId/download", ByteArray::class.java)
        assertEquals(200, download.statusCode.value())
        assertEquals(bytes.size, download.body!!.size)
        assertTrue(MessageDigest.isEqual(bytes, download.body!!))
    }

    @Test
    fun `admin endpoints require authentication`() {
        val anonymousHeaders = HttpHeaders().apply { contentType = MediaType.MULTIPART_FORM_DATA }
        val body = LinkedMultiValueMap<String, Any>()
        body.add("file", object : ByteArrayResource(TestApkFactory.notAnApk()) {
            override fun getFilename() = "broken.apk"
        })
        val anonymous = restTemplate.postForEntity(
            "/api/v1/admin/releases",
            HttpEntity(body, anonymousHeaders),
            String::class.java,
        )
        assertEquals(401, anonymous.statusCode.value())
    }

    @Test
    fun `unpublish marks a release UNPUBLISHED and hides it from the catalog`() {
        val apk = Files.createTempDirectory("tmp").resolve("byebye.apk")
        TestApkFactory.buildApk(apk, packageName = "com.example.byebye", versionCode = 9)
        val upload = uploadAdmin(Files.readAllBytes(apk), "byebye.apk")
        assertEquals(201, upload.statusCode.value(), upload.body)
        val releaseId = Regex("\"id\":\"([a-f0-9-]{36})\"").find(upload.body!!)!!.groupValues[1]

        val unpublish = restTemplate.exchange(
            "/api/v1/admin/releases/$releaseId",
            HttpMethod.DELETE,
            HttpEntity<Void>(adminHeaders()),
            String::class.java,
        )
        assertEquals(204, unpublish.statusCode.value())

        val after = restTemplate.getForEntity("/api/v1/apps?query=byebye", String::class.java)
        assertEquals(200, after.statusCode.value())
        assertFalse(after.body!!.contains("com.example.byebye"))
    }
}
