package com.apkrepo.backend.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.Instant
import java.util.UUID

/**
 * A published Android application, identified by its package name.
 * The package name is the app's identity; display name, description and
 * icon are editable catalog metadata.
 */
@Entity
@Table(name = "apps")
class App(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "package_name", nullable = false, unique = true, length = 255)
    val packageName: String,

    @Column(name = "display_name", length = 255)
    var displayName: String? = null,

    @Column(name = "description", columnDefinition = "text")
    var description: String? = null,

    @Column(name = "icon_path", length = 1024)
    var iconPath: String? = null,
) {
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant? = null
        private set

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant? = null
        private set
}
