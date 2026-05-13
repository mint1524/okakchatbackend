package club.okak.aiproxy.routes

import club.okak.aiproxy.services.AiProxyService
import club.okak.shared.auth.JwtConfig
import club.okak.shared.auth.JwtUtils
import club.okak.shared.db.Limits
import club.okak.shared.db.Subscriptions
import club.okak.shared.db.UsageStats
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.datetime.Clock
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory
import java.util.UUID
import kotlin.time.Duration.Companion.days

private val proxyLog = LoggerFactory.getLogger("club.okak.aiproxy.routes.ProxyRoutes")

fun Route.proxyRoutes(service: AiProxyService, jwtConfig: JwtConfig) {
    webSocket("/api/ai/stream") {
        // Step 1: try to authenticate from the HTTP upgrade header
        var userId: UUID? = call.request.headers["Authorization"]
            ?.removePrefix("Bearer ")
            ?.let { token ->
                runCatching {
                    val payload = JwtUtils.verifier(jwtConfig).verify(token)
                    UUID.fromString(payload.subject)
                }.getOrNull()
            }

        // Step 2: receive first frame (always required — contains model, messages, etc.)
        val frame = incoming.receive() as? Frame.Text
            ?: run { close(CloseReason(4000, "Expected text frame")); return@webSocket }

        val payload = try {
            Json.parseToJsonElement(frame.readText()).jsonObject
        } catch (e: Exception) {
            close(CloseReason(4000, "Invalid JSON"))
            return@webSocket
        }

        // Step 3: if header auth failed, try token from first-frame payload (web fallback)
        if (userId == null) {
            userId = payload["_token"]?.jsonPrimitive?.contentOrNull
                ?.let { token ->
                    runCatching {
                        val jwtPayload = JwtUtils.verifier(jwtConfig).verify(token)
                        UUID.fromString(jwtPayload.subject)
                    }.getOrNull()
                }
        }

        if (userId == null) {
            close(CloseReason(4001, "Unauthorized"))
            return@webSocket
        }

        // Check token limit (tokens_per_month)
        val limitOk = transaction {
            val planId = Subscriptions.selectAll()
                .where { Subscriptions.userId eq userId!! }
                .singleOrNull()?.get(Subscriptions.planId) ?: "free"
            val limit = Limits.selectAll().where {
                (Limits.scope eq "user") and (Limits.scopeId eq userId!!.toString()) and (Limits.metric eq "tokens_per_month")
            }.singleOrNull()?.get(Limits.value)
                ?: Limits.selectAll().where {
                    (Limits.scope eq "plan") and (Limits.scopeId eq planId) and (Limits.metric eq "tokens_per_month")
                }.singleOrNull()?.get(Limits.value) ?: -1L
            if (limit == -1L) return@transaction true
            val used = UsageStats.selectAll().where {
                (UsageStats.userId eq userId!!) and
                (UsageStats.createdAt greater Clock.System.now().minus(30.days))
            }.sumOf { (it[UsageStats.promptTokens] + it[UsageStats.completionTokens]).toLong() }
            used < limit
        }
        if (!limitOk) {
            close(CloseReason(4029, "LIMIT_EXCEEDED:tokens_per_month"))
            return@webSocket
        }

        val modelId = payload["model"]?.jsonPrimitive?.contentOrNull
            ?: run { close(CloseReason(4000, "Missing model")); return@webSocket }
        val messages = payload["messages"]?.jsonArray
            ?: run { close(CloseReason(4000, "Missing messages")); return@webSocket }
        val tools        = payload["tools"]?.jsonArray
        val temperature  = payload["temperature"]?.jsonPrimitive?.doubleOrNull
        val systemPrompt = payload["systemPrompt"]?.jsonPrimitive?.contentOrNull
        val maxTokens    = payload["maxTokens"]?.jsonPrimitive?.intOrNull
        val convId = payload["conversationId"]?.jsonPrimitive?.contentOrNull
            ?.let { runCatching { UUID.fromString(it) }.getOrNull() }

        val providerConfig = service.getProvider(modelId)
            ?: run { close(CloseReason(4004, "Model not found")); return@webSocket }

        val startMs = System.currentTimeMillis()
        var promptTokens = 0
        var completionTokens = 0

        service.streamCompletion(
            providerConfig, modelId, messages, tools,
            temperature, systemPrompt, maxTokens
        )
            .onEach { chunk ->
                send(Frame.Text(chunk))
                try {
                    val obj = Json.parseToJsonElement(chunk).jsonObject
                    obj["usage"]?.jsonObject?.let { usage ->
                        promptTokens = usage["prompt_tokens"]?.jsonPrimitive?.int ?: 0
                        completionTokens = usage["completion_tokens"]?.jsonPrimitive?.int ?: 0
                    }
                } catch (_: Exception) {}
            }
            .catch { cause ->
                proxyLog.error("AI upstream stream failed", cause)
                send(Frame.Text("""{"error":"Upstream error"}"""))
            }
            .collect()

        send(Frame.Text("""{"done":true}"""))
        close(CloseReason(CloseReason.Codes.NORMAL, "Done"))

        service.recordUsage(
            userId!!, convId, modelId, promptTokens, completionTokens,
            (System.currentTimeMillis() - startMs).toInt()
        )
    }
}
