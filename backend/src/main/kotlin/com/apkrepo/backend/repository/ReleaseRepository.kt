package com.apkrepo.backend.repository

import com.apkrepo.backend.domain.Release
import com.apkrepo.backend.domain.ReleaseStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional
import java.util.UUID

interface ReleaseRepository : JpaRepository<Release, UUID> {

    /** Idempotency check: a byte-identical upload already exists. */
    fun findByApkSha256(apkSha256: String): Optional<Release>

    fun findByAppIdAndVersionCode(appId: UUID, versionCode: Long): Optional<Release>

    fun findByAppId(appId: UUID): List<Release>

    fun findByAppIdAndStatusOrderByVersionCodeDesc(appId: UUID, status: ReleaseStatus): List<Release>

    fun findFirstByAppIdAndStatusOrderByVersionCodeDesc(appId: UUID, status: ReleaseStatus): Release?

    /** Newest release visible in the public catalog (READY or PUBLISHED). */
    fun findFirstByAppIdAndStatusInOrderByVersionCodeDesc(
        appId: UUID,
        statuses: Collection<ReleaseStatus>,
    ): Release?

    fun findFirstByAppIdOrderByVersionCodeDesc(appId: UUID): Release?

    fun findByStatus(status: ReleaseStatus, pageable: Pageable): Page<Release>

    fun findByAppIdAndStatusInOrderByVersionCodeDesc(
        appId: UUID,
        statuses: Collection<ReleaseStatus>,
    ): List<Release>

    fun countByAppId(appId: UUID): Long
}
