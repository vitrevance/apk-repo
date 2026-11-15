package com.apkrepo.backend.config

import org.springframework.context.annotation.Configuration
import org.springframework.http.CacheControl
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration

/**
 * Static file serving:
 *
 * 1. The generated F-Droid repository: the publisher writes entry.jar,
 *    index-v2.json, APKs, icons and the QR code into the fdroid workspace's
 *    repo directory inside the storage root, and this mapping serves that
 *    directory at the /fdroid/repo path exactly as an F-Droid client
 *    expects. Only the repo subdirectory is exposed; the workspace root
 *    (config.yml with keystore passwords, keystore) stays private.
 *
 * 2. The Compose Multiplatform web app (Kotlin/Wasm): when
 *    apk-repo.web.static-dir is configured (docker image: /app/web) the
 *    built SPA is served at the site root. Controller mappings (/api,
 *    /swagger-ui, /actuator) always take precedence over resource
 *    handlers, so the API surface is unaffected. The web app uses hash
 *    routing, so no server-side route fallback is needed.
 */
@Configuration
class WebMvcConfig(properties: ApkRepoProperties) : WebMvcConfigurer {

    private val repoDir: Path = Path.of(properties.storage.root)
        .toAbsolutePath().normalize()
        .resolve("fdroid")
        .resolve("repo")

    private val webStaticDir: Path? = properties.web.staticDir
        ?.takeIf { it.isNotBlank() }
        ?.let { Path.of(it).toAbsolutePath().normalize() }

    override fun addResourceHandlers(registry: ResourceHandlerRegistry) {
        registry.addResourceHandler("/fdroid/repo/**")
            .addResourceLocations("file:$repoDir/")
            .setCacheControl(CacheControl.maxAge(Duration.ofHours(1)))

        webStaticDir?.let { dir ->
            if (!Files.isDirectory(dir)) {
                throw IllegalStateException("apk-repo.web.static-dir does not exist: $dir")
            }
            registry.addResourceHandler("/**")
                .addResourceLocations("file:$dir/")
                .setCacheControl(CacheControl.maxAge(Duration.ofHours(1)))
            // Note: .wasm is served as application/wasm out of the box —
            // Spring 6.2's MediaTypeFactory knows the extension.
        }
    }
}
