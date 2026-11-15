# NOTE: This Dockerfile expects the repository ROOT as build context
# (docker build -f Dockerfile . from the repo root), because it copies
# web/ and backend/ from it.

# Build stage: compile the Compose Multiplatform web app (Kotlin/Wasm).
# Independent Gradle build with its own Kotlin (2.3.21) + Compose
# Multiplatform (1.12.2) versions; produces static files in
# build/dist/wasmJs/productionExecutable.
# libatomic1 is required by the Node.js runtime the Kotlin Gradle plugin
# downloads for webpack (not present in the gradle base image).
FROM gradle:8.14.3-jdk21 AS webbuild
USER root
RUN apt-get update \
    && apt-get install -y --no-install-recommends libatomic1 \
    && rm -rf /var/lib/apt/lists/*
# WORKDIR-created directories are root-owned, which stops the gradle user
# from creating its project cache (<dir>/.gradle). Pre-create and chown so
# the build works with any builder (incl. the legacy non-BuildKit one).
RUN mkdir -p /webworkspace && chown gradle:gradle /webworkspace
USER gradle
WORKDIR /webworkspace
COPY web/ .
RUN gradle wasmJsBrowserDistribution --no-daemon

# Build stage: compile the Spring Boot Kotlin application with Gradle.
FROM gradle:8.14.3-jdk21 AS build
USER root
# See the webbuild stage: root-owned WORKDIR dirs break the gradle user.
RUN mkdir -p /workspace && chown gradle:gradle /workspace
USER gradle
WORKDIR /workspace
COPY backend/ .
RUN gradle bootJar --no-daemon

# Runtime stage: JDK base (fdroidserver's keystore generation and index
# signing shell out to keytool/jarsigner, which only ship in a JDK) with
# Python and fdroidserver installed in a venv. git is required because
# fdroidserver imports GitPython at startup (it is never used on this
# project's own files — the fdroid workspace is not a git checkout).
FROM eclipse-temurin:21-jdk-jammy

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        python3 python3-pip python3-venv \
        git curl ca-certificates unzip apksigner \
    && rm -rf /var/lib/apt/lists/*

# fdroidserver: repository index generation and signing (fdroid init /
# fdroid update), bringing androguard, Pillow, qrcode etc. transitively.
RUN python3 -m venv /opt/fdroid \
    && /opt/fdroid/bin/pip install --no-cache-dir fdroidserver==2.4.5
ENV PATH=/opt/fdroid/bin:$PATH

RUN useradd --system --uid 1001 --create-home appuser
WORKDIR /app
COPY --from=build /workspace/build/libs/*.jar app.jar
# Kotlin/Wasm web app distribution (index.html, app.js, .wasm), served at /
# by WebMvcConfig when apk-repo.web.static-dir is set (docker profile).
COPY --from=webbuild /webworkspace/build/dist/wasmJs/productionExecutable /app/web
# Pre-create the storage root with appuser ownership so that bind/named
# volumes mounted here inherit writable permissions (see compose.yaml).
RUN mkdir -p /var/lib/apk-repo/storage && chown -R appuser:appuser /var/lib/apk-repo
USER appuser
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
