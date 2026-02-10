// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    id("com.github.ben-manes.versions") version "0.53.0"
    id("nl.littlerobots.version-catalog-update") version "1.0.1"
}

// Run ./gradlew versionCatalogUpdate to update all dependencies
// Run ./gradlew versionCatalogUpdate --interactive for interactive mode
