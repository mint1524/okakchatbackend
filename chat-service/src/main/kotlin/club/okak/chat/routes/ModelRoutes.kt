package club.okak.chat.routes

import club.okak.shared.db.ModelConfigs
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction

@Serializable
data class ModelDto(
    val id: String,
    val displayName: String,
    val contextWindow: Int,
    val supportsStreaming: Boolean
)

fun Route.modelRoutes() {
    get("/api/chat/models") {
        val models = transaction {
            ModelConfigs.selectAll().where { ModelConfigs.enabled eq true }.map {
                ModelDto(
                    id = it[ModelConfigs.id],
                    displayName = it[ModelConfigs.displayName],
                    contextWindow = it[ModelConfigs.contextWindow],
                    supportsStreaming = it[ModelConfigs.supportsStreaming]
                )
            }
        }
        call.respond(models)
    }
}
