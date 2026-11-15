package com.apkrepo.backend.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Application-wide configuration for the APK repository backend.
 *
 * Values can be overridden through environment variables, e.g.
 * `APKREPO_ADMIN_USERNAME`, `APKREPO_ADMIN_PASSWORD`,
 * `APKREPO_STORAGE_ROOT`, `APKREPO_REPOSITORY_NAME`,
 * `APKREPO_REPOSITORY_BASE_URL`, `APKREPO_REPOSITORY_FINGERPRINT`.
 */
@ConfigurationProperties(prefix = "apk-repo")
data class ApkRepoProperties(
    val admin: Admin = Admin(),
    val storage: Storage = Storage(),
    val upload: Upload = Upload(),
    val repository: Repository = Repository(),
    val publisher: Publisher = Publisher(),
    val web: Web = Web(),
) {
    data class Admin(
        val username: String = "admin",
        val password: String = "change-me-in-production",
    )

    data class Storage(
        val root: String = "./data/storage",
    )

    /** Upload limits; APKs are untrusted data and are validated against these. */
    data class Upload(
        val maxFileSizeBytes: Long = 250L * 1024 * 1024,
        val maxEntryCount: Int = 20_000,
        val maxTotalUncompressedBytes: Long = 1L * 1024 * 1024 * 1024,
        val maxEntryUncompressedBytes: Long = 512L * 1024 * 1024,
    )

    data class Repository(
        val name: String = "APK Repo",
        val baseUrl: String = "https://apk.example.com",
        val fingerprint: String? = null,
    )

    /**
     * F-Droid publisher worker. The worker shells out to the fdroidserver
     * CLI (fdroid init / fdroid update) inside the private workspace in
     * the storage root; disable it (e.g. in tests) with enabled=false.
     */
    data class Publisher(
        val enabled: Boolean = true,
        val pollIntervalMs: Long = 2_000,
        val fdroidCommand: String = "fdroid",
        val keyDname: String = "CN=APK Repo Repo Signing Key, O=apk-repo, C=US",
        val commandTimeoutSec: Long = 600,
    )

    /**
     * Static directory of the Compose Multiplatform web app (Kotlin/Wasm
     * build output). When set (docker profile: /app/web) the whole
     * application is served at the site root; null disables SPA serving
     * (local dev and tests).
     */
    data class Web(
        val staticDir: String? = null,
    )
}
