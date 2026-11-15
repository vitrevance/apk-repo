package com.apkrepo.backend.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityScheme
import io.swagger.v3.oas.models.servers.Server
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * OpenAPI metadata. The `basicAuth` scheme is declared here and referenced
 * from admin controllers via `@SecurityRequirement` so that Swagger UI can
 * authenticate against the admin endpoints.
 */
@Configuration
class OpenApiConfig(private val properties: ApkRepoProperties) {

    @Bean
    fun apkRepoOpenApi(): OpenAPI {
        val openApi = OpenAPI()
            .info(
                Info()
                    .title("APK Repo API")
                    .description(
                        "REST API for the self-hosted APK repository: public app catalog browsing, " +
                            "APK upload and release management (admin), and F-Droid repository information.",
                    )
                    .version("0.1.0"),
            )
            .components(
                Components().addSecuritySchemes(
                    "basicAuth",
                    SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("basic")
                        .description("Administrator credentials (single admin account)"),
                ),
            )

        // Behind a TLS-terminating reverse proxy the request reaches Spring as
        // plain HTTP, so springdoc's request-derived server URL would be
        // http://... and Swagger UI's "Try it out" breaks (mixed content on an
        // HTTPS page). When a real public base URL is configured, pin the
        // OpenAPI server to it; with the default placeholder (local dev without
        // configuration) springdoc's request-derived URL stays authoritative.
        val baseUrl = properties.repository.baseUrl.trimEnd('/')
        if (baseUrl != DEFAULT_BASE_URL_PLACEHOLDER) {
            openApi.servers(listOf(Server().url(baseUrl)))
        }
        return openApi
    }

    private companion object {
        const val DEFAULT_BASE_URL_PLACEHOLDER = "https://apk.example.com"
    }
}
