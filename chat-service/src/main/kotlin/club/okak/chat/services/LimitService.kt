package club.okak.chat.services

import club.okak.shared.db.Limits
import club.okak.shared.db.Subscriptions
import club.okak.shared.db.UsageStats
import kotlinx.datetime.Clock
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID
import kotlin.time.Duration.Companion.days

object LimitService {
    fun checkLimit(userId: UUID, metric: String): Boolean = transaction {
        val planId = Subscriptions.selectAll()
            .where { Subscriptions.userId eq userId }
            .singleOrNull()
            ?.get(Subscriptions.planId) ?: "free"

        // user override takes precedence over plan default
        val limit = Limits.selectAll().where {
            (Limits.scope eq "user") and
            (Limits.scopeId eq userId.toString()) and
            (Limits.metric eq metric)
        }.singleOrNull()?.get(Limits.value)
            ?: Limits.selectAll().where {
                (Limits.scope eq "plan") and
                (Limits.scopeId eq planId) and
                (Limits.metric eq metric)
            }.singleOrNull()?.get(Limits.value)
            ?: -1L

        if (limit == -1L) return@transaction true

        val used: Long = when (metric) {
            "messages_per_day" -> UsageStats.selectAll().where {
                (UsageStats.userId eq userId) and
                (UsageStats.createdAt greater Clock.System.now().minus(1.days))
            }.count()
            "tokens_per_month" -> {
                val rows = UsageStats.selectAll().where {
                    (UsageStats.userId eq userId) and
                    (UsageStats.createdAt greater Clock.System.now().minus(30.days))
                }
                rows.sumOf { (it[UsageStats.promptTokens] + it[UsageStats.completionTokens]).toLong() }
            }
            else -> 0L
        }
        used < limit
    }
}
