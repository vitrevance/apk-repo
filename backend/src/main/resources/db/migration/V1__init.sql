-- Initial schema for the APK repository backend.
-- Tables mirror the data model in plan/apk-repo-plan.md section 4.

CREATE TABLE apps (
    id            UUID PRIMARY KEY,
    package_name  VARCHAR(255) NOT NULL,
    display_name  VARCHAR(255),
    description   TEXT,
    icon_path     VARCHAR(1024),
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_apps_package_name UNIQUE (package_name)
);

-- Catalog search by package name / display name.
CREATE INDEX idx_apps_package_name ON apps (package_name);

CREATE TABLE releases (
    id                UUID PRIMARY KEY,
    app_id            UUID NOT NULL,
    version_name      VARCHAR(255) NOT NULL,
    version_code      BIGINT NOT NULL,
    apk_sha256        VARCHAR(64) NOT NULL,
    apk_signer_sha256 VARCHAR(64),
    apk_path          VARCHAR(1024) NOT NULL,
    apk_size          BIGINT NOT NULL,
    min_sdk           INTEGER,
    target_sdk        INTEGER,
    uploaded_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    published_at      TIMESTAMP WITH TIME ZONE,
    status            VARCHAR(32) NOT NULL,
    error_message     TEXT,
    CONSTRAINT fk_releases_app FOREIGN KEY (app_id) REFERENCES apps (id) ON DELETE CASCADE,
    CONSTRAINT uq_releases_apk_sha256 UNIQUE (apk_sha256),
    CONSTRAINT uq_releases_app_version_code UNIQUE (app_id, version_code)
);

CREATE INDEX idx_releases_app_id ON releases (app_id);
CREATE INDEX idx_releases_status ON releases (status);

CREATE TABLE publication_jobs (
    id            UUID PRIMARY KEY,
    action        VARCHAR(64) NOT NULL,
    state         VARCHAR(32) NOT NULL,
    release_id    UUID,
    generation_id VARCHAR(64),
    retry_count   INTEGER NOT NULL DEFAULT 0,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    started_at    TIMESTAMP WITH TIME ZONE,
    finished_at   TIMESTAMP WITH TIME ZONE,
    error_message TEXT
);

CREATE INDEX idx_publication_jobs_state ON publication_jobs (state);
