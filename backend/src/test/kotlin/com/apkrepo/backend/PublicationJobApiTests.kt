package com.apkrepo.backend

import com.apkrepo.backend.testutil.TestApkFactory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
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

/**
 * Phase 3 integration tests: publication job enqueueing, listing and
 * rebuild, plus the /fdroid/repo static route. The publisher worker is
 * DISABLED (there is no fdroidserver in the test JVM) — these tests
 * exercise the job machinery and the enqueue integration points, not the
 * actual index generation, which is verified live against the Docker
 * deployment.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("docker")
@Testcontainers
class PublicationJobApiTests {

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

    private fun uploadAdmin(bytes: ByteArray, fileName: String): ResponseEntity<String> =
        restTemplate.exchange(
            "/api/v1/admin/releases",
            HttpMethod.POST,
            uploadEntity(bytes, fileName),
            String::class.java,
        )

    private fun jobs(): ResponseEntity<String> =
        restTemplate.exchange(
            "/api/v1/admin/publication-jobs",
            HttpMethod.GET,
            HttpEntity<Void>(adminHeaders()),
            String::class.java,
        )

    private fun extractReleaseId(uploadBody: String): String {
        val marker = "\"id\":\""
        val start = uploadBody.indexOf(marker) + marker.length
        return uploadBody.substring(start, uploadBody.indexOf('"', start))
    }

    private fun occurrences(haystack: String, needle: String): Int =
        haystack.split(needle).size - 1

    @Test
    fun `anonymous job listing is rejected`() {
        val anonymous = restTemplate.getForEntity("/api/v1/admin/publication-jobs", String::class.java)
        assertEquals(401, anonymous.statusCode.value())
    }

    @Test
    fun `job listing works with admin credentials`() {
        val listing = jobs()
        assertEquals(200, listing.statusCode.value())
        assertTrue(listing.body!!.contains("\"totalElements\""), listing.body)
    }

    @Test
    fun `upload enqueues a pending publish job and duplicate upload does not`() {
        val apk = Files.createTempDirectory("tmp").resolve("pub-1.apk")
        TestApkFactory.buildApk(apk, packageName = "com.example.pub", versionCode = 1)
        val bytes = Files.readAllBytes(apk)

        val upload = uploadAdmin(bytes, "pub-1.apk")
        assertEquals(201, upload.statusCode.value(), upload.body)

        val listing = jobs()
        assertTrue(listing.body!!.contains("\"action\":\"PUBLISH\""), listing.body)
        assertTrue(listing.body!!.contains("\"state\":\"PENDING\""), listing.body)

        // Byte-identical re-upload enqueues nothing new.
        uploadAdmin(bytes, "pub-1.apk")
        val afterDuplicate = jobs()
        assertEquals(1, occurrences(afterDuplicate.body!!, "\"action\":\"PUBLISH\""), afterDuplicate.body)

        // A different version of the same app enqueues a second job.
        val apk2 = Files.createTempDirectory("tmp").resolve("pub-2.apk")
        TestApkFactory.buildApk(apk2, packageName = "com.example.pub", versionCode = 2)
        uploadAdmin(Files.readAllBytes(apk2), "pub-2.apk")
        val afterSecond = jobs()
        assertEquals(2, occurrences(afterSecond.body!!, "\"action\":\"PUBLISH\""), afterSecond.body)
    }

    @Test
    fun `rebuild enqueues a rebuild job`() {
        val anonymous = restTemplate.postForEntity("/api/v1/admin/repository/rebuild", null, String::class.java)
        assertEquals(401, anonymous.statusCode.value())

        val rebuild = restTemplate.exchange(
            "/api/v1/admin/repository/rebuild",
            HttpMethod.POST,
            HttpEntity<Void>(adminHeaders()),
            String::class.java,
        )
        assertEquals(202, rebuild.statusCode.value(), rebuild.body)
        assertTrue(rebuild.body!!.contains("\"action\":\"REBUILD\""), rebuild.body)
    }

    @Test
    fun `unpublish enqueues an unpublish job`() {
        val apk = Files.createTempDirectory("tmp").resolve("unpub.apk")
        TestApkFactory.buildApk(apk, packageName = "com.example.unpub", versionCode = 1)
        val upload = uploadAdmin(Files.readAllBytes(apk), "unpub.apk")
        assertEquals(201, upload.statusCode.value(), upload.body)
        val releaseId = extractReleaseId(upload.body!!)

        val unpublish = restTemplate.exchange(
            "/api/v1/admin/releases/$releaseId",
            HttpMethod.DELETE,
            HttpEntity<Void>(adminHeaders()),
            String::class.java,
        )
        assertEquals(204, unpublish.statusCode.value())

        val listing = jobs()
        assertTrue(listing.body!!.contains("\"action\":\"UNPUBLISH\""), listing.body)
    }

    @Test
    fun `app metadata edit enqueues a rebuild job`() {
        val apk = Files.createTempDirectory("tmp").resolve("meta.apk")
        TestApkFactory.buildApk(apk, packageName = "com.example.meta", versionCode = 1)
        val upload = uploadAdmin(Files.readAllBytes(apk), "meta.apk")
        assertEquals(201, upload.statusCode.value(), upload.body)

        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            setBasicAuth(USERNAME, PASSWORD)
        }
        val patch = restTemplate.exchange(
            "/api/v1/admin/apps/com.example.meta",
            HttpMethod.PATCH,
            HttpEntity("""{"description":"Edited description"}""", headers),
            String::class.java,
        )
        assertEquals(200, patch.statusCode.value(), patch.body)

        val listing = jobs()
        assertTrue(listing.body!!.contains("\"action\":\"REBUILD\""), listing.body)
    }

    @Test
    fun `repository info has no fingerprint before the publisher ever ran`() {
        val info = restTemplate.getForEntity("/api/v1/repository", String::class.java)
        assertEquals(200, info.statusCode.value())
        assertTrue(info.body!!.contains("/fdroid/repo/\""), info.body)
        assertTrue(info.body!!.contains("\"fingerprint\":null"), info.body)
    }

    @Test
    fun `fdroid repo route is public and returns 404 before first publish`() {
        val entry = restTemplate.getForEntity("/fdroid/repo/entry.jar", String::class.java)
        assertEquals(404, entry.statusCode.value())
    }
}
