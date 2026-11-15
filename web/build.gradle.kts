plugins {
    // Compose Multiplatform 1.12.2 requires Kotlin >= 2.3.20 for wasmJs;
    // 2.3.21 is the latest 2.3.x patch. This build is independent of the
    // backend module (Kotlin 2.1.20 / Spring Boot).
    kotlin("multiplatform") version "2.3.21"
    kotlin("plugin.serialization") version "2.3.21"
    // Compose compiler plugin ships with Kotlin (not CMP) since Kotlin 2.0.
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.21"
    id("org.jetbrains.compose") version "1.12.2"
}

repositories {
    mavenCentral()
    google()
}

kotlin {
    wasmJs {
        browser {
            commonWebpackConfig {
                outputFileName = "app.js"
            }
        }
        binaries.executable()
    }

    sourceSets {
        // Direct coordinates (the compose.* accessors are deprecated in CMP 1.12).
        val wasmJsMain by getting {
            dependencies {
                implementation("org.jetbrains.compose.runtime:runtime:1.12.2")
                implementation("org.jetbrains.compose.ui:ui:1.12.2")
                implementation("org.jetbrains.compose.foundation:foundation:1.12.2")
                implementation("org.jetbrains.compose.material3:material3:1.12.0-alpha03")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
            }
        }
    }
}
