package club.okak.aiproxy

import club.okak.aiproxy.routes.proxyRoutes
import club.okak.aiproxy.routes.providerRoutes
import club.okak.aiproxy.services.AiProxyService
import club.okak.shared.auth.JwtConfig
import club.okak.shared.auth.JwtUtils
import club.okak.shared.db.DatabaseFactory
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import kotlin.time.Duration.Companion.seconds

fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

fun Application.module() {
    DatabaseFactory.init(
        host = System.getenv("POSTGRES_HOST") ?: "localhost",
        port = System.getenv("POSTGRES_PORT")?.toInt() ?: 5432,
        db = System.getenv("POSTGRES_DB") ?: "okakchat",
        user = System.getenv("POSTGRES_USER") ?: "okak",
        password = System.getenv("POSTGRES_PASSWORD") ?: "changeme",
        migrationLocations = listOf("classpath:db/migration")
    )

    val jwtConfig = JwtConfig(
        secret = System.getenv("JWT_SECRET") ?: error("JWT_SECRET required"),
        accessTtlMinutes = 15,
        refreshTtlDays = 30
    )
    val encSecret = System.getenv("AI_API_KEY_ENCRYPTION_SECRET")
        ?: error("AI_API_KEY_ENCRYPTION_SECRET required")

    install(Authentication) {
        jwt("auth-jwt") {
            verifier(JwtUtils.verifier(jwtConfig))
            validate { cred ->
                if (cred.payload.subject != null) JWTPrincipal(cred.payload) else null
            }
        }
    }
    install(WebSockets) {
        pingPeriod = 30.seconds
        timeout = 120.seconds
    }
    install(CORS) {
        anyHost()
        allowHeader(HttpHeaders.Authorization)
    }
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.application.log.error("Unhandled error", cause)
            call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Internal server error"))
        }
    }

    val proxyService = AiProxyService(encSecret)

    routing {
        get("/health") { call.respond(mapOf("ok" to true)) }
        proxyRoutes(proxyService, jwtConfig)
        providerRoutes(encSecret, jwtConfig)
    }
}
