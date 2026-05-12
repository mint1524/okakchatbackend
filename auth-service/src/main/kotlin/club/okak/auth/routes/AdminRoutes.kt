package club.okak.auth.routes

import club.okak.shared.db.*
import io.ktor.http.*
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID

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

@Serializable data class AdminUserDto(
    val id: String,
    val email: String,
    val displayName: String,
    val emailVerified: Boolean
)

@Serializable data class GrantSubscriptionRequest(
    val planId: String,
    val expiresAt: String? = null,
    val note: String? = null
)
@Serializable data class SetLimitRequest(val metric: String, val value: Long)
@Serializable data class GrantAdminRequest(val role: String = "moderator")

fun Route.adminRoutes() {
    authenticate("auth-jwt") {
        route("/api/admin") {

            get("/users") {
                if (!requireAdmin()) return@get
                val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 0
                val q = call.request.queryParameters["q"] ?: ""
                val users = transaction {
                    var query = Users.selectAll()
                    if (q.isNotBlank()) query = query.where { Users.email like "%$q%" }
                    query.limit(20).offset((page * 20).toLong()).map {
                        AdminUserDto(
                            id = it[Users.id].toString(),
                            email = it[Users.email],
                            displayName = it[Users.displayName],
                            emailVerified = it[Users.emailVerified]
                        )
                    }
                }
                call.respond(users)
            }

            post("/users/{userId}/subscription") {
                if (!requireAdmin()) return@post
                val adminId = UUID.fromString(call.principal<JWTPrincipal>()!!.payload.subject)
                val userId = UUID.fromString(
                    call.parameters["userId"] ?: return@post call.respond(HttpStatusCode.BadRequest)
                )
                val req = call.receive<GrantSubscriptionRequest>()
                val expiresAt = req.expiresAt?.let { Instant.parse(it) }
                transaction {
                    val existing = Subscriptions.selectAll().where { Subscriptions.userId eq userId }.singleOrNull()
                    if (existing != null) {
                        Subscriptions.update({ Subscriptions.userId eq userId }) {
                            it[Subscriptions.planId] = req.planId
                            it[Subscriptions.grantedBy] = adminId
                            it[Subscriptions.startsAt] = Clock.System.now()
                            it[Subscriptions.expiresAt] = expiresAt
                            it[Subscriptions.note] = req.note
                            it[Subscriptions.updatedAt] = Clock.System.now()
                        }
                    } else {
                        Subscriptions.insert {
                            it[Subscriptions.userId] = userId
                            it[Subscriptions.planId] = req.planId
                            it[Subscriptions.grantedBy] = adminId
                            it[Subscriptions.startsAt] = Clock.System.now()
                            it[Subscriptions.expiresAt] = expiresAt
                            it[Subscriptions.note] = req.note
                            it[Subscriptions.createdAt] = Clock.System.now()
                            it[Subscriptions.updatedAt] = Clock.System.now()
                        }
                    }
                    AdminAuditLog.insert {
                        it[AdminAuditLog.adminId] = adminId
                        it[AdminAuditLog.action] = "grant_subscription"
                        it[AdminAuditLog.targetType] = "user"
                        it[AdminAuditLog.targetId] = userId.toString()
                        it[AdminAuditLog.payload] = buildJsonObject {
                            put("planId", req.planId)
                            put("expiresAt", req.expiresAt ?: "")
                        }.toString()
                        it[AdminAuditLog.createdAt] = Clock.System.now()
                    }
                }
                call.respond(HttpStatusCode.OK, mapOf("ok" to true))
            }

            post("/users/{userId}/admin") {
                if (!requireAdmin()) return@post
                val adminId = UUID.fromString(call.principal<JWTPrincipal>()!!.payload.subject)
                val targetId = UUID.fromString(
                    call.parameters["userId"] ?: return@post call.respond(HttpStatusCode.BadRequest)
                )
                val req = call.receive<GrantAdminRequest>()
                transaction {
                    val existing = AdminRoles.selectAll().where { AdminRoles.userId eq targetId }.singleOrNull()
                    if (existing != null) {
                        AdminRoles.update({ AdminRoles.userId eq targetId }) {
                            it[AdminRoles.role] = req.role
                            it[AdminRoles.grantedAt] = Clock.System.now()
                        }
                    } else {
                        AdminRoles.insert {
                            it[AdminRoles.userId] = targetId
                            it[AdminRoles.role] = req.role
                            it[AdminRoles.grantedAt] = Clock.System.now()
                        }
                    }
                    AdminAuditLog.insert {
                        it[AdminAuditLog.adminId] = adminId
                        it[AdminAuditLog.action] = "grant_admin"
                        it[AdminAuditLog.targetType] = "user"
                        it[AdminAuditLog.targetId] = targetId.toString()
                        it[AdminAuditLog.payload] = buildJsonObject {
                            put("role", req.role)
                        }.toString()
                        it[AdminAuditLog.createdAt] = Clock.System.now()
                    }
                }
                call.respond(HttpStatusCode.OK, mapOf("ok" to true))
            }

            post("/plans/{planId}/limits") {
                if (!requireAdmin()) return@post
                val adminId = UUID.fromString(call.principal<JWTPrincipal>()!!.payload.subject)
                val planId = call.parameters["planId"] ?: return@post call.respond(HttpStatusCode.BadRequest)
                val req = call.receive<SetLimitRequest>()
                transaction {
                    val existing = Limits.selectAll().where {
                        (Limits.scope eq "plan") and (Limits.scopeId eq planId) and (Limits.metric eq req.metric)
                    }.singleOrNull()
                    if (existing != null) {
                        Limits.update({ (Limits.scope eq "plan") and (Limits.scopeId eq planId) and (Limits.metric eq req.metric) }) {
                            it[Limits.value] = req.value
                            it[Limits.updatedAt] = Clock.System.now()
                            it[Limits.updatedBy] = adminId
                        }
                    } else {
                        Limits.insert {
                            it[Limits.scope] = "plan"
                            it[Limits.scopeId] = planId
                            it[Limits.metric] = req.metric
                            it[Limits.value] = req.value
                            it[Limits.updatedAt] = Clock.System.now()
                            it[Limits.updatedBy] = adminId
                        }
                    }
                }
                call.respond(HttpStatusCode.OK, mapOf("ok" to true))
            }

            post("/users/{userId}/limits") {
                if (!requireAdmin()) return@post
                val adminId = UUID.fromString(call.principal<JWTPrincipal>()!!.payload.subject)
                val userId = call.parameters["userId"] ?: return@post call.respond(HttpStatusCode.BadRequest)
                val req = call.receive<SetLimitRequest>()
                transaction {
                    val existing = Limits.selectAll().where {
                        (Limits.scope eq "user") and (Limits.scopeId eq userId) and (Limits.metric eq req.metric)
                    }.singleOrNull()
                    if (existing != null) {
                        Limits.update({ (Limits.scope eq "user") and (Limits.scopeId eq userId) and (Limits.metric eq req.metric) }) {
                            it[Limits.value] = req.value
                            it[Limits.updatedAt] = Clock.System.now()
                            it[Limits.updatedBy] = adminId
                        }
                    } else {
                        Limits.insert {
                            it[Limits.scope] = "user"
                            it[Limits.scopeId] = userId
                            it[Limits.metric] = req.metric
                            it[Limits.value] = req.value
                            it[Limits.updatedAt] = Clock.System.now()
                            it[Limits.updatedBy] = adminId
                        }
                    }
                }
                call.respond(HttpStatusCode.OK, mapOf("ok" to true))
            }

            get("/audit") {
                if (!requireAdmin()) return@get
                val logs = transaction {
                    AdminAuditLog.selectAll()
                        .orderBy(AdminAuditLog.createdAt, SortOrder.DESC)
                        .limit(100)
                        .map {
                            mapOf(
                                "id" to it[AdminAuditLog.id].toString(),
                                "adminId" to it[AdminAuditLog.adminId].toString(),
                                "action" to it[AdminAuditLog.action],
                                "targetType" to it[AdminAuditLog.targetType],
                                "targetId" to it[AdminAuditLog.targetId],
                                "createdAt" to it[AdminAuditLog.createdAt].toString()
                            )
                        }
                }
                call.respond(logs)
            }
        }
    }
}
