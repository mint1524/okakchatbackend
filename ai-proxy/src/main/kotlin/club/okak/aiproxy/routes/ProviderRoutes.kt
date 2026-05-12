package club.okak.aiproxy.routes

import club.okak.aiproxy.crypto.ApiKeyCrypto
import club.okak.shared.auth.JwtConfig
import club.okak.shared.auth.JwtUtils
import club.okak.shared.db.AiProviders
import club.okak.shared.db.ModelConfigs
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction

@Serializable
data class UpsertProviderRequest(
    val id: String,
    val baseUrl: String,
    val apiKey: String,
    val displayName: String,
    val enabled: Boolean = true
)

@Serializable
data class UpsertModelRequest(
    val id: String,
    val providerId: String,
    val displayName: String,
    val contextWindow: Int = 128000,
    val supportsStreaming: Boolean = true,
    val enabled: Boolean = true
)

@Serializable
data class ProviderDto(val id: String, val baseUrl: String, val displayName: String, val enabled: Boolean)

@Serializable
data class ModelDto(
    val id: String,
    val providerId: String,
    val displayName: String,
    val contextWindow: Int,
    val supportsStreaming: Boolean,
    val enabled: Boolean
)

private suspend fun RoutingContext.requireAdmin(): Boolean {
    val principal = call.principal<JWTPrincipal>()
    if (principal == null) {
        call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Unauthorized"))
        return false
    }
    val isAdmin = principal.payload.getClaim("admin").asBoolean() ?: false
    if (!isAdmin) {
        call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Forbidden"))
        return false
    }
    return true
}

fun Route.providerRoutes(encSecret: String, jwtConfig: JwtConfig) {
    authenticate("auth-jwt") {
        route("/api/ai/admin") {

            get("/providers") {
                if (!requireAdmin()) return@get
                val providers = transaction {
                    AiProviders.selectAll().map {
                        ProviderDto(
                            id = it[AiProviders.id],
                            baseUrl = it[AiProviders.baseUrl],
                            displayName = it[AiProviders.displayName],
                            enabled = it[AiProviders.enabled]
                        )
                    }
                }
                call.respond(providers)
            }

            put("/providers") {
                if (!requireAdmin()) return@put
                val req = call.receive<UpsertProviderRequest>()
                val encrypted = ApiKeyCrypto.encrypt(req.apiKey, encSecret)
                transaction {
                    AiProviders.upsert {
                        it[AiProviders.id] = req.id
                        it[AiProviders.baseUrl] = req.baseUrl
                        it[AiProviders.apiKeyEnc] = encrypted
                        it[AiProviders.displayName] = req.displayName
                        it[AiProviders.enabled] = req.enabled
                    }
                }
                call.respond(HttpStatusCode.OK, mapOf("ok" to true))
            }

            delete("/providers/{id}") {
                if (!requireAdmin()) return@delete
                val id = call.parameters["id"] ?: return@delete call.respond(HttpStatusCode.BadRequest)
                transaction { AiProviders.deleteWhere { AiProviders.id eq id } }
                call.respond(HttpStatusCode.NoContent)
            }

            get("/models") {
                if (!requireAdmin()) return@get
                val models = transaction {
                    ModelConfigs.selectAll().map {
                        ModelDto(
                            id = it[ModelConfigs.id],
                            providerId = it[ModelConfigs.providerId],
                            displayName = it[ModelConfigs.displayName],
                            contextWindow = it[ModelConfigs.contextWindow],
                            supportsStreaming = it[ModelConfigs.supportsStreaming],
                            enabled = it[ModelConfigs.enabled]
                        )
                    }
                }
                call.respond(models)
            }

            put("/models") {
                if (!requireAdmin()) return@put
                val req = call.receive<UpsertModelRequest>()
                transaction {
                    ModelConfigs.upsert {
                        it[ModelConfigs.id] = req.id
                        it[ModelConfigs.providerId] = req.providerId
                        it[ModelConfigs.displayName] = req.displayName
                        it[ModelConfigs.contextWindow] = req.contextWindow
                        it[ModelConfigs.supportsStreaming] = req.supportsStreaming
                        it[ModelConfigs.enabled] = req.enabled
                    }
                }
                call.respond(HttpStatusCode.OK, mapOf("ok" to true))
            }

            delete("/models/{id}") {
                if (!requireAdmin()) return@delete
                val id = call.parameters["id"] ?: return@delete call.respond(HttpStatusCode.BadRequest)
                transaction { ModelConfigs.deleteWhere { ModelConfigs.id eq id } }
                call.respond(HttpStatusCode.NoContent)
            }
        }
    }
}
