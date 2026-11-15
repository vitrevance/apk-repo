package com.apkrepo.backend.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * OpenAPI metadata. The `basicAuth` scheme is declared here and referenced
 * from admin controllers via `@SecurityRequirement` so that Swagger UI can
 * authenticate against the admin endpoints.
 */
@Configuration
class OpenApiConfig {

    @Bean
    fun apkRepoOpenApi(): OpenAPI = OpenAPI()
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
}
