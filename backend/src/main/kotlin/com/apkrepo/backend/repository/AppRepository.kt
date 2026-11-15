package com.apkrepo.backend.repository

import com.apkrepo.backend.domain.App
import com.apkrepo.backend.domain.ReleaseStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.Optional
import java.util.UUID

interface AppRepository : JpaRepository<App, UUID> {

    fun findByPackageName(packageName: String): Optional<App>

    fun existsByPackageName(packageName: String): Boolean

    /** Apps with at least one release in the given (publicly visible) states. */
    @Query(
        """
        SELECT a FROM App a
        WHERE EXISTS (
            SELECT r FROM Release r
            WHERE r.app = a AND r.status IN :statuses
        )
        """,
    )
    fun findAllWithAvailableReleases(
        @Param("statuses") statuses: Collection<ReleaseStatus>,
        pageable: Pageable,
    ): Page<App>

    /** Catalog search by package name or display name, case-insensitive. */
    @Query(
        """
        SELECT a FROM App a
        WHERE (LOWER(a.packageName) LIKE LOWER(CONCAT('%', :query, '%'))
            OR LOWER(a.displayName) LIKE LOWER(CONCAT('%', :query, '%')))
          AND EXISTS (
            SELECT r FROM Release r
            WHERE r.app = a AND r.status IN :statuses
          )
        """,
    )
    fun search(
        @Param("query") query: String,
        @Param("statuses") statuses: Collection<ReleaseStatus>,
        pageable: Pageable,
    ): Page<App>
}
