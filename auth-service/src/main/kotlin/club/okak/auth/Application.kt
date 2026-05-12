package club.okak.auth

import club.okak.auth.plugins.configureSerialization
import club.okak.auth.plugins.configureRouting
import club.okak.shared.auth.JwtConfig
import club.okak.shared.auth.JwtUtils
import club.okak.shared.db.DatabaseFactory
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*

fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

fun Application.module() {
    val dbHost = System.getenv("POSTGRES_HOST") ?: "localhost"
    val dbPort = System.getenv("POSTGRES_PORT")?.toInt() ?: 5432
    val dbName = System.getenv("POSTGRES_DB") ?: "okakchat"
    val dbUser = System.getenv("POSTGRES_USER") ?: "okak"
    val dbPass = System.getenv("POSTGRES_PASSWORD") ?: "changeme"

    DatabaseFactory.init(
        host = dbHost, port = dbPort, db = dbName, user = dbUser, password = dbPass,
        migrationLocations = listOf("classpath:db/migration")
    )

    val jwtConfig = JwtConfig(
        secret = System.getenv("JWT_SECRET") ?: error("JWT_SECRET required"),
        accessTtlMinutes = System.getenv("JWT_ACCESS_TTL_MINUTES")?.toLong() ?: 15L,
        refreshTtlDays = System.getenv("JWT_REFRESH_TTL_DAYS")?.toLong() ?: 30L
    )

    install(Authentication) {
        jwt("auth-jwt") {
            verifier(JwtUtils.verifier(jwtConfig))
            validate { credential ->
                if (credential.payload.subject != null) JWTPrincipal(credential.payload) else null
            }
        }
    }

    configureSerialization()
    configureRouting(jwtConfig)
}
