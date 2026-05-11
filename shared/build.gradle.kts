plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(libs.exposed.core)
    api(libs.exposed.dao)
    api(libs.exposed.jdbc)
    api(libs.exposed.kotlin.datetime)
    api(libs.postgres)
    api(libs.hikari)
    api(libs.flyway.core)
    api(libs.flyway.postgres)
    api(libs.jwt)
    api(libs.kotlinx.serialization.json)
    implementation(libs.logback)
}
