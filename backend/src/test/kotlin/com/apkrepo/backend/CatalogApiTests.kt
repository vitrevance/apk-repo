package com.apkrepo.backend

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpStatus
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.net.HttpURLConnection
import java.net.URL
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue

/**
 * Phase 1 acceptance tests: the service boots against a real PostgreSQL,
 * Flyway migrations run, public catalog endpoints respond, error payloads
 * are consistent, Swagger/OpenAPI are served, and admin endpoints reject
 * anonymous access while accepting valid admin credentials.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class CatalogApiTests {

    companion object {
        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:17-alpine")

        @JvmStatic
        @DynamicPropertySource
        fun datasource(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { postgres.jdbcUrl }
            registry.add("spring.datasource.username") { postgres.username }
            registry.add("spring.datasource.password") { postgres.password }
            // No fdroidserver binary exists in the test JVM.
            registry.add("apk-repo.publisher.enabled") { "false" }
        }
    }

    @LocalServerPort
    var port: Int = 0

    @Autowired
    lateinit var restTemplate: TestRestTemplate

    private fun url(path: String) = "http://localhost:$port$path"

    private fun assertContains(actual: String, expected: String) =
        assertTrue(expected in actual, "Expected '$expected' to be contained in: $actual")

    @Test
    fun `empty catalog returns an empty page`() {
        val entity = restTemplate.getForEntity(url("/api/v1/apps"), String::class.java)
        assertEquals(HttpStatus.OK, entity.statusCode)
        val body = entity.body.orEmpty()
        assertContains(body, "\"content\":[]")
        assertContains(body, "\"totalElements\":0")
    }

    @Test
    fun `unknown app returns a structured 404 error with request id`() {
        val entity = restTemplate.getForEntity(url("/api/v1/apps/com.example.missing"), String::class.java)
        assertEquals(HttpStatus.NOT_FOUND, entity.statusCode)
        val body = entity.body.orEmpty()
        assertContains(body, "\"code\":\"NOT_FOUND\"")
        assertContains(body, "requestId")
        assertEquals(true, entity.headers.getFirst("X-Request-Id") != null)
    }

    @Test
    fun `catalog search query parameter is accepted`() {
        val entity = restTemplate.getForEntity(url("/api/v1/apps?query=example"), String::class.java)
        assertEquals(HttpStatus.OK, entity.statusCode)
        assertContains(entity.body.orEmpty(), "\"content\":[]")
    }

    @Test
    fun `invalid pagination is rejected with 400`() {
        val entity = restTemplate.getForEntity(url("/api/v1/apps?page=-1"), String::class.java)
        assertEquals(HttpStatus.BAD_REQUEST, entity.statusCode)
        assertContains(entity.body.orEmpty(), "\"code\":\"VALIDATION_ERROR\"")
    }

    @Test
    fun `repository info is public`() {
        val entity = restTemplate.getForEntity(url("/api/v1/repository"), String::class.java)
        assertEquals(HttpStatus.OK, entity.statusCode)
        assertContains(entity.body.orEmpty(), "/fdroid/repo/")
    }

    @Test
    fun `health endpoint is public and reports up`() {
        val entity = restTemplate.getForEntity(url("/actuator/health"), String::class.java)
        assertEquals(HttpStatus.OK, entity.statusCode)
        assertContains(entity.body.orEmpty(), "\"status\":\"UP\"")
    }

    @Test
    fun `admin endpoint rejects anonymous access`() {
        val entity = restTemplate.getForEntity(url("/api/v1/admin/stats"), String::class.java)
        assertEquals(HttpStatus.UNAUTHORIZED, entity.statusCode)
    }

    @Test
    fun `admin endpoint rejects wrong credentials`() {
        val client = restTemplate.withBasicAuth("admin", "wrong-password")
        val entity = client.getForEntity(url("/api/v1/admin/stats"), String::class.java)
        assertEquals(HttpStatus.UNAUTHORIZED, entity.statusCode)
    }

    @Test
    fun `admin endpoint accepts admin credentials`() {
        val client = restTemplate.withBasicAuth("admin", "change-me-in-production")
        val entity = client.getForEntity(url("/api/v1/admin/stats"), String::class.java)
        assertEquals(HttpStatus.OK, entity.statusCode)
        assertContains(entity.body.orEmpty(), "\"appCount\":0")
    }

    @Test
    fun `openapi document is available`() {
        val entity = restTemplate.getForEntity(url("/v3/api-docs"), String::class.java)
        assertEquals(HttpStatus.OK, entity.statusCode)
        assertContains(entity.body.orEmpty(), "APK Repo API")
    }

    @Test
    fun `swagger ui is available`() {
        // /swagger-ui.html redirects to /swagger-ui/index.html; follow manually.
        val connection = URL(url("/swagger-ui.html")).openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = false
        connection.connect()
        val status = connection.responseCode
        assertTrue(status == HttpStatus.FOUND.value() || status == HttpStatus.OK.value())
        assertEquals(true, connection.getHeaderField("Location") != null || status == HttpStatus.OK.value())
        connection.disconnect()

        val entity = restTemplate.getForEntity(url("/swagger-ui/index.html"), String::class.java)
        assertEquals(HttpStatus.OK, entity.statusCode)
    }
}
