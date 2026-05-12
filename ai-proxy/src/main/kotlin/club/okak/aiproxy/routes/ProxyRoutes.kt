package club.okak.aiproxy.routes

import club.okak.aiproxy.services.AiProxyService
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.collect
import kotlinx.serialization.json.*
import java.util.UUID

fun Route.proxyRoutes(service: AiProxyService) {
    authenticate("auth-jwt") {
        webSocket("/api/ai/stream") {
            val principal = call.principal<JWTPrincipal>()
                ?: run { close(CloseReason(4001, "Unauthorized")); return@webSocket }
            val userId = UUID.fromString(principal.payload.subject)

            val frame = incoming.receive() as? Frame.Text
                ?: run { close(CloseReason(4000, "Expected text frame")); return@webSocket }

            val payload = try {
                Json.parseToJsonElement(frame.readText()).jsonObject
            } catch (e: Exception) {
                close(CloseReason(4000, "Invalid JSON"))
                return@webSocket
            }

            val modelId = payload["model"]?.jsonPrimitive?.contentOrNull
                ?: run { close(CloseReason(4000, "Missing model")); return@webSocket }
            val messages = payload["messages"]?.jsonArray
                ?: run { close(CloseReason(4000, "Missing messages")); return@webSocket }
            val tools = payload["tools"]?.jsonArray
            val convId = payload["conversationId"]?.jsonPrimitive?.contentOrNull
                ?.let { runCatching { UUID.fromString(it) }.getOrNull() }

            val providerConfig = service.getProvider(modelId)
                ?: run { close(CloseReason(4004, "Model not found")); return@webSocket }

            val startMs = System.currentTimeMillis()
            var promptTokens = 0
            var completionTokens = 0

            service.streamCompletion(providerConfig, modelId, messages, tools)
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
                .catch { e -> send(Frame.Text("""{"error":"${e.message}"}""")) }
                .collect()

            send(Frame.Text("""{"done":true}"""))
            close(CloseReason(CloseReason.Codes.NORMAL, "Done"))

            service.recordUsage(
                userId, convId, modelId, promptTokens, completionTokens,
                (System.currentTimeMillis() - startMs).toInt()
            )
        }
    }
}
