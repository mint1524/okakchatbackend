package club.okak.chat.routes

import club.okak.shared.db.ModelConfigs
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction

fun Route.modelRoutes() {
    get("/api/chat/models") {
        val models = transaction {
            ModelConfigs.selectAll().where { ModelConfigs.enabled eq true }.map {
                mapOf(
                    "id" to it[ModelConfigs.id],
                    "displayName" to it[ModelConfigs.displayName],
                    "contextWindow" to it[ModelConfigs.contextWindow],
                    "supportsStreaming" to it[ModelConfigs.supportsStreaming]
                )
            }
        }
        call.respond(models)
    }
}
