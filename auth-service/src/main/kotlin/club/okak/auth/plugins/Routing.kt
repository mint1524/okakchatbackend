package club.okak.auth.plugins

import club.okak.auth.routes.adminRoutes
import club.okak.auth.routes.authRoutes
import club.okak.auth.services.AuthService
import club.okak.auth.services.MailService
import club.okak.shared.auth.JwtConfig
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.configureRouting(jwtConfig: JwtConfig) {
    install(CORS) {
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Patch)
        allowMethod(HttpMethod.Delete)
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        anyHost()
    }

    val mailService = MailService(
        baseUrl = System.getenv("MAIL_SERVICE_BASE_URL") ?: "http://localhost:3035",
        token = System.getenv("MAIL_SERVICE_TOKEN") ?: "",
        siteUrl = System.getenv("MAIL_SITE_URL") ?: "https://okak.club"
    )
    val authService = AuthService(jwtConfig, mailService)

    routing {
        get("/health") { call.respond(mapOf("ok" to true)) }
        authRoutes(authService)
        adminRoutes()
    }
}
