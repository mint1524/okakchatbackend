package club.okak.chat.routes

import club.okak.shared.db.Conversations
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID

@Serializable
data class CreateConversationRequest(
    val title: String = "New Chat",
    val modelId: String,
    val mode: String = "chat"
)

@Serializable
data class UpdateConversationRequest(
    val title: String? = null,
    val archived: Boolean? = null
)

fun Route.conversationRoutes() {
    route("/api/chat/conversations") {
        get {
            val userId = UUID.fromString(call.principal<JWTPrincipal>()!!.payload.subject)
            val archived = call.request.queryParameters["archived"]?.toBoolean() ?: false
            val list = transaction {
                Conversations.selectAll().where {
                    (Conversations.userId eq userId) and
                    if (archived) Conversations.archivedAt.isNotNull()
                    else Conversations.archivedAt.isNull()
                }.orderBy(Conversations.updatedAt, SortOrder.DESC).limit(50).map {
                    mapOf(
                        "id" to it[Conversations.id].toString(),
                        "title" to it[Conversations.title],
                        "modelId" to it[Conversations.modelId],
                        "mode" to it[Conversations.mode],
                        "createdAt" to it[Conversations.createdAt].toString(),
                        "updatedAt" to it[Conversations.updatedAt].toString(),
                        "archivedAt" to it[Conversations.archivedAt]?.toString()
                    )
                }
            }
            call.respond(list)
        }

        post {
            val userId = UUID.fromString(call.principal<JWTPrincipal>()!!.payload.subject)
            val req = call.receive<CreateConversationRequest>()
            val id = transaction {
                val now = Clock.System.now()
                Conversations.insert {
                    it[Conversations.userId] = userId
                    it[Conversations.title] = req.title
                    it[Conversations.modelId] = req.modelId
                    it[Conversations.mode] = req.mode
                    it[Conversations.createdAt] = now
                    it[Conversations.updatedAt] = now
                }[Conversations.id]
            }
            call.respond(HttpStatusCode.Created, mapOf("id" to id.toString()))
        }

        patch("/{id}") {
            val userId = UUID.fromString(call.principal<JWTPrincipal>()!!.payload.subject)
            val convId = UUID.fromString(
                call.parameters["id"] ?: return@patch call.respond(HttpStatusCode.BadRequest)
            )
            val req = call.receive<UpdateConversationRequest>()
            val updated = transaction {
                Conversations.selectAll().where {
                    (Conversations.id eq convId) and (Conversations.userId eq userId)
                }.singleOrNull() ?: return@transaction false
                Conversations.update({ Conversations.id eq convId }) {
                    req.title?.let { t -> it[Conversations.title] = t }
                    if (req.archived == true) it[Conversations.archivedAt] = Clock.System.now()
                    if (req.archived == false) it[Conversations.archivedAt] = null
                    it[Conversations.updatedAt] = Clock.System.now()
                }
                true
            }
            if (updated) call.respond(HttpStatusCode.NoContent)
            else call.respond(HttpStatusCode.NotFound)
        }

        delete("/{id}") {
            val userId = UUID.fromString(call.principal<JWTPrincipal>()!!.payload.subject)
            val convId = UUID.fromString(
                call.parameters["id"] ?: return@delete call.respond(HttpStatusCode.BadRequest)
            )
            transaction {
                Conversations.deleteWhere {
                    (Conversations.id eq convId) and (Conversations.userId eq userId)
                }
            }
            call.respond(HttpStatusCode.NoContent)
        }
    }
}
