package com.apkrepo.backend.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import java.time.Instant
import java.util.UUID

/**
 * One uploaded APK belonging to an [App]. A release is immutable once
 * stored: the APK bytes, hash, signer and version identity never change;
 * only lifecycle status and error details are mutable.
 */
@Entity
@Table(name = "releases")
class Release(
    @Id
    val id: UUID = UUID.randomUUID(),

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "app_id", nullable = false)
    val app: App,

    @Column(name = "version_name", nullable = false, length = 255)
    val versionName: String,

    @Column(name = "version_code", nullable = false)
    val versionCode: Long,

    /** SHA-256 of the APK bytes; unique for identical uploaded binaries. */
    @Column(name = "apk_sha256", nullable = false, unique = true, length = 64)
    val apkSha256: String,

    /** SHA-256 fingerprint of the APK's signing certificate. */
    @Column(name = "apk_signer_sha256", length = 64)
    val apkSignerSha256: String? = null,

    @Column(name = "apk_path", nullable = false, length = 1024)
    val apkPath: String,

    @Column(name = "apk_size", nullable = false)
    val apkSize: Long,

    @Column(name = "min_sdk")
    val minSdk: Int? = null,

    @Column(name = "target_sdk")
    val targetSdk: Int? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    var status: ReleaseStatus = ReleaseStatus.PROCESSING,

    @Column(name = "error_message", columnDefinition = "text")
    var errorMessage: String? = null,
) {
    @CreationTimestamp
    @Column(name = "uploaded_at", nullable = false, updatable = false)
    var uploadedAt: Instant? = null
        private set

    @Column(name = "published_at")
    var publishedAt: Instant? = null
}
