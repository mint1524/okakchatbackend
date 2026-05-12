package club.okak.chat

import club.okak.chat.routes.conversationRoutes
import club.okak.chat.routes.messageRoutes
import club.okak.chat.routes.modelRoutes
import club.okak.shared.auth.JwtConfig
import club.okak.shared.auth.JwtUtils
import club.okak.shared.db.DatabaseFactory
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json

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

    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true; encodeDefaults = true })
    }
    install(CORS) {
        anyHost()
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Patch)
        allowMethod(HttpMethod.Delete)
    }
    install(StatusPages) {
        exception<IllegalArgumentException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to (cause.message ?: "Bad request")))
        }
        exception<Throwable> { call, cause ->
            call.application.log.error("Unhandled error", cause)
            call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Internal server error"))
        }
    }
    install(Authentication) {
        jwt("auth-jwt") {
            verifier(JwtUtils.verifier(jwtConfig))
            validate { cred ->
                if (cred.payload.subject != null) JWTPrincipal(cred.payload) else null
            }
        }
    }

    routing {
        get("/health") { call.respond(mapOf("ok" to true)) }
        authenticate("auth-jwt") {
            conversationRoutes()
            messageRoutes()
            modelRoutes()
        }
    }
}
