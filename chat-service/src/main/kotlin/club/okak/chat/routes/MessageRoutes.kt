package club.okak.chat.routes

import club.okak.chat.services.LimitService
import club.okak.shared.db.Conversations
import club.okak.shared.db.Messages
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID

@Serializable
data class AddMessageRequest(
    val role: String,
    val content: String,
    val tokensUsed: Int? = null
)

fun Route.messageRoutes() {
    route("/api/chat/conversations/{convId}/messages") {
        get {
            val userId = UUID.fromString(call.principal<JWTPrincipal>()!!.payload.subject)
            val convId = UUID.fromString(
                call.parameters["convId"] ?: return@get call.respond(HttpStatusCode.BadRequest)
            )
            val owns = transaction {
                Conversations.selectAll().where {
                    (Conversations.id eq convId) and (Conversations.userId eq userId)
                }.count() > 0
            }
            if (!owns) return@get call.respond(HttpStatusCode.Forbidden)
            val messages = transaction {
                Messages.selectAll()
                    .where { Messages.conversationId eq convId }
                    .orderBy(Messages.createdAt, SortOrder.ASC)
                    .map {
                        mapOf(
                            "id" to it[Messages.id].toString(),
                            "role" to it[Messages.role],
                            "content" to it[Messages.content],
                            "tokensUsed" to it[Messages.tokensUsed],
                            "createdAt" to it[Messages.createdAt].toString()
                        )
                    }
            }
            call.respond(messages)
        }

        post {
            val userId = UUID.fromString(call.principal<JWTPrincipal>()!!.payload.subject)
            val convId = UUID.fromString(
                call.parameters["convId"] ?: return@post call.respond(HttpStatusCode.BadRequest)
            )
            val owns = transaction {
                Conversations.selectAll().where {
                    (Conversations.id eq convId) and (Conversations.userId eq userId)
                }.count() > 0
            }
            if (!owns) return@post call.respond(HttpStatusCode.Forbidden)
            // Check daily message limit
            if (!LimitService.checkLimit(userId, "messages_per_day")) {
                return@post call.respond(
                    HttpStatusCode(429, "Too Many Requests"),
                    mapOf("error" to "Daily message limit exceeded")
                )
            }
            val req = call.receive<AddMessageRequest>()
            val id = transaction {
                val now = Clock.System.now()
                val msgId = Messages.insert {
                    it[Messages.conversationId] = convId
                    it[Messages.role] = req.role
                    it[Messages.content] = req.content
                    it[Messages.tokensUsed] = req.tokensUsed
                    it[Messages.createdAt] = now
                }[Messages.id]
                Conversations.update({ Conversations.id eq convId }) {
                    it[Conversations.updatedAt] = now
                }
                msgId
            }
            call.respond(HttpStatusCode.Created, mapOf("id" to id.toString()))
        }
    }
}
