package com.apkrepo.backend.api

import io.swagger.v3.oas.annotations.Operation
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping

/**
 * Root of the site: serves the Compose Multiplatform web app. The static
 * resource handler in WebMvcConfig serves the whole dist directory, but
 * the bare "/" path needs an explicit forward to the index file (Spring
 * does not apply welcome-page logic to file: resource locations).
 */
@Controller
class WebUiController {

    @GetMapping("/")
    @Operation(summary = "Web app", description = "The Compose Multiplatform web application")
    fun index(): String = "forward:/index.html"
}
