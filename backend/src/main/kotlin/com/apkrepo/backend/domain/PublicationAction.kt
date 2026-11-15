package com.apkrepo.backend.domain

/** Action requested from the F-Droid publisher worker. */
enum class PublicationAction {
    /** (Re)generate and sign repository indexes including the referenced release. */
    PUBLISH,

    /** Rebuild indexes after a release was unpublished or removed. */
    UNPUBLISH,

    /** Full rebuild of metadata and indexes (e.g. after catalog edits). */
    REBUILD,
}
