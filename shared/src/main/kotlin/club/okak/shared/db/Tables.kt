package club.okak.shared.db

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

// auth schema
object Users : Table("users") {
    val id = uuid("id").autoGenerate()
    val email = varchar("email", 320).uniqueIndex()
    val passwordHash = varchar("password_hash", 72)
    val displayName = varchar("display_name", 100)
    val avatarUrl = varchar("avatar_url", 2048).nullable()
    val emailVerified = bool("email_verified").default(false)
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
    override val primaryKey = PrimaryKey(id)
}

object EmailVerifications : Table("email_verifications") {
    val id = uuid("id").autoGenerate()
    val userId = uuid("user_id").references(Users.id)
    val code = varchar("code", 6)
    val expiresAt = timestamp("expires_at")
    val usedAt = timestamp("used_at").nullable()
    override val primaryKey = PrimaryKey(id)
}

object RefreshTokens : Table("refresh_tokens") {
    val id = uuid("id").autoGenerate()
    val userId = uuid("user_id").references(Users.id)
    val tokenHash = varchar("token_hash", 128)
    val expiresAt = timestamp("expires_at")
    val revokedAt = timestamp("revoked_at").nullable()
    val deviceInfo = varchar("device_info", 500).nullable()
    override val primaryKey = PrimaryKey(id)
}

object UserMeta : Table("user_meta") {
    val userId = uuid("user_id").references(Users.id)
    val key = varchar("key", 100)
    val value = text("value")
    val updatedAt = timestamp("updated_at")
    override val primaryKey = PrimaryKey(userId, key)
}

object Subscriptions : Table("subscriptions") {
    val id = uuid("id").autoGenerate()
    val userId = uuid("user_id").references(Users.id).uniqueIndex()
    val planId = varchar("plan_id", 50)
    val grantedBy = uuid("granted_by").references(Users.id).nullable()
    val startsAt = timestamp("starts_at")
    val expiresAt = timestamp("expires_at").nullable()
    val note = text("note").nullable()
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
    override val primaryKey = PrimaryKey(id)
}

object Plans : Table("plans") {
    val id = varchar("id", 50)
    val displayName = varchar("display_name", 100)
    val isDefault = bool("is_default").default(false)
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
    override val primaryKey = PrimaryKey(id)
}

object Limits : Table("limits") {
    val id = uuid("id").autoGenerate()
    val scope = varchar("scope", 10)
    val scopeId = varchar("scope_id", 50)
    val metric = varchar("metric", 100)
    val value = long("value")
    val updatedAt = timestamp("updated_at")
    val updatedBy = uuid("updated_by").references(Users.id).nullable()
    override val primaryKey = PrimaryKey(id)
}

object AdminRoles : Table("admin_roles") {
    val userId = uuid("user_id").references(Users.id)
    val role = varchar("role", 20)
    val grantedAt = timestamp("granted_at")
    override val primaryKey = PrimaryKey(userId)
}

object AdminAuditLog : Table("admin_audit_log") {
    val id = uuid("id").autoGenerate()
    val adminId = uuid("admin_id").references(Users.id)
    val action = varchar("action", 100)
    val targetType = varchar("target_type", 50)
    val targetId = varchar("target_id", 100)
    val payload = text("payload")
    val createdAt = timestamp("created_at")
    override val primaryKey = PrimaryKey(id)
}

// chat schema
object Conversations : Table("conversations") {
    val id = uuid("id").autoGenerate()
    val userId = uuid("user_id").references(Users.id)
    val title = varchar("title", 500)
    val modelId = varchar("model_id", 100)
    val mode = varchar("mode", 20).default("chat")
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
    val archivedAt = timestamp("archived_at").nullable()
    override val primaryKey = PrimaryKey(id)
}

object Messages : Table("messages") {
    val id = uuid("id").autoGenerate()
    val conversationId = uuid("conversation_id").references(Conversations.id)
    val role = varchar("role", 20)
    val content = text("content")
    val tokensUsed = integer("tokens_used").nullable()
    val createdAt = timestamp("created_at")
    override val primaryKey = PrimaryKey(id)
}

// ai-proxy schema
object UsageStats : Table("usage_stats") {
    val id = uuid("id").autoGenerate()
    val userId = uuid("user_id").references(Users.id)
    val conversationId = uuid("conversation_id").references(Conversations.id).nullable()
    val modelId = varchar("model_id", 100)
    val promptTokens = integer("prompt_tokens").default(0)
    val completionTokens = integer("completion_tokens").default(0)
    val latencyMs = integer("latency_ms").nullable()
    val createdAt = timestamp("created_at")
    override val primaryKey = PrimaryKey(id)
}

object AiProviders : Table("ai_providers") {
    val id = varchar("id", 50)
    val baseUrl = varchar("base_url", 2048)
    val apiKeyEnc = text("api_key_enc")
    val displayName = varchar("display_name", 100)
    val enabled = bool("enabled").default(true)
    override val primaryKey = PrimaryKey(id)
}

object ModelConfigs : Table("model_configs") {
    val id = varchar("id", 100)
    val providerId = varchar("provider_id", 50).references(AiProviders.id)
    val displayName = varchar("display_name", 100)
    val contextWindow = integer("context_window").default(128000)
    val supportsStreaming = bool("supports_streaming").default(true)
    val enabled = bool("enabled").default(true)
    override val primaryKey = PrimaryKey(id)
}
