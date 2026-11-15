# APK Repo

Self-hosted Android app repository with a web catalog, admin panel, and a
standards-compliant **F-Droid (index-v2) repository**. Upload a signed APK
through the web UI or REST API; the server extracts its identity (package,
name, version, icon), stores it, and publishes a signed, discoverable release
to both the website and F-Droid clients — no fdroidserver or Android SDK
knowledge required.

## Features

- Web catalog: browse, search, app details, version history, direct APK download
- Admin area: login, APK upload with automatic metadata extraction, app
  management, publication jobs, repository rebuild
- Built-in F-Droid publisher: signed `index-v2` repository with QR code and
  fingerprint for easy client onboarding
- Single container: the image bundles the backend, web UI, and the complete
  F-Droid toolchain

## Requirements

- Docker (any host)
- PostgreSQL 17 (see the example below)

## Quick start

Create a `docker-compose.yml`:

```yaml
services:
  postgres:
    image: postgres:17-alpine
    environment:
      POSTGRES_DB: apkrepo
      POSTGRES_USER: apkrepo
      POSTGRES_PASSWORD: apkrepo
    volumes:
      - db-data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U apkrepo -d apkrepo"]
      interval: 5s
      timeout: 5s
      retries: 12

  apk-repo:
    image: ghcr.io/vitrevance/apk-repo:latest   # or build locally
    depends_on:
      postgres:
        condition: service_healthy
    environment:
      SPRING_PROFILES_ACTIVE: docker
      APKREPO_ADMIN_USERNAME: admin
      APKREPO_ADMIN_PASSWORD: change-me-in-production
      APKREPO_REPOSITORY_BASE_URL: https://your-domain.example.com
    ports:
      - "8080:8080"
    volumes:
      - apk-storage:/var/lib/apk-repo/storage

volumes:
  db-data:
  apk-storage:
```

Then:

```bash
docker compose up -d
```

The web UI is served at `http://your-host:8080/`. Sign in via **Admin** with
the credentials configured above.

To build the image from source instead of pulling it:

```bash
./build.sh apk-repo:latest
# or: docker build -t apk-repo:latest .
```

## Configuration

All configuration is done through environment variables.

### Required in production

| Variable | Meaning | Default |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | Must be `docker` when running the container | — |
| `APKREPO_ADMIN_USERNAME` | Admin login (web UI + REST API Basic auth) | `admin` |
| `APKREPO_ADMIN_PASSWORD` | Admin password — **change this** | `change-me-in-production` |
| `APKREPO_REPOSITORY_BASE_URL` | Public base URL (ideally HTTPS) baked into the F-Droid index and QR code; must be reachable from the Android devices using the repository | `https://apk.example.com` |

### Optional

| Variable | Meaning | Default |
|---|---|---|
| `APKREPO_REPOSITORY_NAME` | Repository display name | `APK Repo` |
| `APKREPO_STORAGE_ROOT` | Storage path inside the container | `/var/lib/apk-repo/storage` |
| `SPRING_DATASOURCE_URL` | JDBC URL — override if your database is not a service named `postgres` with the default credentials | `jdbc:postgresql://postgres:5432/apkrepo` |
| `SPRING_DATASOURCE_USERNAME` / `SPRING_DATASOURCE_PASSWORD` | Database credentials | `apkrepo` / `apkrepo` |
| `APKREPO_PUBLISHER_ENABLED` | Set `false` to stop processing publication jobs | `true` |

### Ports and volumes

- **Port `8080`** — web UI, REST API, and F-Droid repository, all on one origin.
- **`/var/lib/apk-repo/storage`** — APKs, icons, the F-Droid workspace, and the
  repository signing keystore. **Must be a persistent volume.**

### Keystore backup warning

The repository signing key is generated on first publication and stored at
`{storage}/fdroid/keystore.p12`. **Back this file up** (together with the
`keystorepass` in `{storage}/fdroid/config.yml`): losing it means every F-Droid
client that ever added the repository will refuse the new key and must re-add
the repo manually.

## Using the web UI

- **Catalog** — browse and search apps, open details, download APKs
- **Repository** (top bar) — repository URL, fingerprint, and QR code for
  F-Droid client onboarding
- **Admin** — login with the admin credentials, then: dashboard, APK upload,
  manage apps, publication jobs, repository status and rebuild

## Adding the repository to an F-Droid client

1. Open `https://your-domain.example.com/#/repo` (or call
   `GET /api/v1/repository`) and copy the repository URL with fingerprint.
2. In the F-Droid client: Settings → Repositories → Add (`+`), paste
   `https://your-domain.example.com/fdroid/repo/?fingerprint=<fingerprint>`
   (the QR code on the onboarding page encodes the same URL).
3. The catalog appears once the index downloads; install apps as usual and
   receive later versions as normal updates.

## Browser support

The web UI targets browsers with Wasm GC support: Chrome/Edge 119+,
Firefox 120+, Safari 18.2+. Other browsers show a warning page.

## Prebuilt images

GitHub Actions builds and publishes the image to
`ghcr.io/vitrevance/apk-repo` whenever a `v*` tag is pushed
(`v1.2.3` → tags `1.2.3`, `1.2`, `latest`).
