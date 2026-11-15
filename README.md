# APK Repo

Self-hosted Android application repository with a web catalog, administration
panel, REST API, and an F-Droid (index v2) compatible repository.

An administrator uploads a signed `.apk`; the system extracts the app's
identity (package, name, version, icon), stores it immutably, and publishes a
downloadable release to both the website and the F-Droid repository.

## Status

Phases 1–4 complete (backend, APK ingestion, F-Droid publisher, Compose web UI).
See `plan/apk-repo-plan.md` for the full implementation plan and `memory-bank/`
for current project context.

## Layout

```
backend/    Kotlin + Spring Boot REST API (PostgreSQL, Flyway, springdoc/Swagger)
            + built-in F-Droid publisher worker (fdroidserver subprocesses)
web/        Compose Multiplatform web app (Kotlin/Wasm) — catalog, admin, onboarding
deploy/     Docker Compose deployment + verify-repo.sh repository verifier
plan/       Implementation plan
```

## Running locally (development)

Requirements: Java 21, Docker.

```bash
cd deploy
docker compose up -d postgres      # starts PostgreSQL on localhost:5432

cd ../backend
./gradlew bootRun                  # runs the API on http://localhost:8080
```

Swagger UI: http://localhost:8080/swagger-ui.html
Health: http://localhost:8080/actuator/health

Default development admin credentials: `admin` / `change-me-in-production`
(override with `APKREPO_ADMIN_USERNAME` / `APKREPO_ADMIN_PASSWORD`).

## Running the full stack

```bash
cd deploy
docker compose up --build
```

The API image contains the full F-Droid toolchain (fdroidserver, apksigner,
keytool/jarsigner), so a single `docker compose up` yields a working
F-Droid-compatible repository — no external build server or Android SDK setup.

Set `APKREPO_REPOSITORY_BASE_URL` to the public base URL of your deployment
(ideally HTTPS); it is baked into the generated index and QR code and must be
reachable from the Android devices using the repository.

## Web UI

The Compose Multiplatform (Kotlin/Wasm) app is built into the same image
(the Dockerfile's `webbuild` stage runs `gradle wasmJsBrowserDistribution`)
and served at the site root — the same origin as the API, so no CORS setup.
Open `http://localhost:8080/` after `docker compose up --build`:

- **Catalog** (`#/`): browse/search apps, detail pages, download APKs
- **Repo onboarding** (`#/repo`): repository URL, fingerprint, QR code
- **Admin area** (`#/admin`): login (same credentials as the API's Basic
  auth), dashboard, APK upload, app management, publication jobs,
  repository status/rebuild

Note: the app targets browsers with Wasm GC support (Chrome/Edge 119+,
Firefox 120+, Safari 18.2+); other browsers show a warning page.

Working on the web app locally:

```bash
cd web
./gradlew wasmJsBrowserDevelopmentRun   # dev server with hot reload
./gradlew wasmJsBrowserDistribution     # production build (build/dist/wasmJs)
```

The dev server proxies nothing — point it at a running backend by serving
the built files from it (docker profile sets `apk-repo.web.static-dir=/app/web`).

## F-Droid repository

Every upload (and unpublish / metadata edit) enqueues a publication job; a
serial worker regenerates the repository: it mirrors the database state into
the repo directory, rewrites the per-app metadata from the database, and runs
`fdroid update`, which signs `entry.jar` / `index-v1.jar` and writes
`index-v2.json`, icons and the QR code (`index.png`).

- Repository root (for F-Droid clients): `{base}/fdroid/repo/`
- Repository info incl. signing fingerprint: `GET /api/v1/repository`
- Job monitoring: `GET /api/v1/admin/publication-jobs`,
  `POST /api/v1/admin/repository/rebuild`

### Adding the repository to an F-Droid client (manual test)

1. Get the fingerprint: `curl -s https://your-host/api/v1/repository`
2. In the F-Droid client: Settings → Repositories → Add (`+`) and enter
   `https://your-host/fdroid/repo/?fingerprint=<fingerprint>` (the QR code
   at `{base}/fdroid/repo/index.png` encodes the same URL).
3. The catalog appears after the index downloads; install an app.

### Programmatic verification

```bash
cd deploy
docker compose exec api /app/verify-repo.sh
```

Checks jarsigner signature validity, the entry.json→index-v2.json hash
chain, index-vs-database consistency, HTTP-served bytes and the fingerprint
against the keystore.

### Keystore backup warning

The repository signing key is generated on first publication and stored at
`{storage}/fdroid/keystore.p12` on the `apk-storage` volume. **Back this file
up** (together with the `keystorepass` in `{storage}/fdroid/config.yml`):
losing it means every F-Droid client that ever added the repository will
refuse the new key and must re-add the repo manually.

## Testing

```bash
cd backend
./gradlew test    # integration tests using Testcontainers (needs Docker)
```
