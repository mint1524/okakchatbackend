package club.okak.aiproxy.services

import club.okak.aiproxy.crypto.ApiKeyCrypto
import club.okak.shared.db.AiProviders
import club.okak.shared.db.ModelConfigs
import club.okak.shared.db.UsageStats
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.Clock
import kotlinx.serialization.json.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID

class AiProxyService(private val encSecret: String) {
    private val client = HttpClient(CIO) {
        engine { requestTimeout = 120_000 }
    }

    data class ProviderConfig(val baseUrl: String, val apiKey: String)

    fun getProvider(modelId: String): ProviderConfig? = transaction {
        val model = ModelConfigs.selectAll()
            .where { (ModelConfigs.id eq modelId) and (ModelConfigs.enabled eq true) }
            .singleOrNull() ?: return@transaction null
        val provider = AiProviders.selectAll()
            .where { AiProviders.id eq model[ModelConfigs.providerId] }
            .singleOrNull() ?: return@transaction null
        ProviderConfig(
            baseUrl = provider[AiProviders.baseUrl],
            apiKey = ApiKeyCrypto.decrypt(provider[AiProviders.apiKeyEnc], encSecret)
        )
    }

    fun streamCompletion(
        providerConfig: ProviderConfig,
        modelId: String,
        messages: JsonArray,
        tools: JsonArray?
    ): Flow<String> = flow {
        val bodyObj = buildJsonObject {
            put("model", modelId)
            put("messages", messages)
            put("stream", true)
            tools?.let { put("tools", it) }
        }

        val response: HttpResponse = client.post("${providerConfig.baseUrl}/v1/chat/completions") {
            header(HttpHeaders.Authorization, "Bearer ${providerConfig.apiKey}")
            header(HttpHeaders.Accept, "text/event-stream")
            contentType(ContentType.Application.Json)
            setBody(bodyObj.toString())
        }

        val channel = response.bodyAsChannel()
        while (!channel.isClosedForRead) {
            val line = channel.readUTF8Line() ?: break
            if (line.startsWith("data: ")) {
                val data = line.removePrefix("data: ").trim()
                if (data != "[DONE]") emit(data)
            }
        }
    }

    fun recordUsage(
        userId: UUID,
        conversationId: UUID?,
        modelId: String,
        promptTokens: Int,
        completionTokens: Int,
        latencyMs: Int
    ) {
        transaction {
            UsageStats.insert {
                it[UsageStats.userId] = userId
                it[UsageStats.conversationId] = conversationId
                it[UsageStats.modelId] = modelId
                it[UsageStats.promptTokens] = promptTokens
                it[UsageStats.completionTokens] = completionTokens
                it[UsageStats.latencyMs] = latencyMs
                it[UsageStats.createdAt] = Clock.System.now()
            }
        }
    }
}
