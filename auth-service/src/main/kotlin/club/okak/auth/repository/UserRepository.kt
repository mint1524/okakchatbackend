package club.okak.auth.repository

import club.okak.shared.db.*
import kotlinx.datetime.Clock
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.days

data class UserRow(
    val id: UUID,
    val email: String,
    val passwordHash: String,
    val displayName: String,
    val emailVerified: Boolean
)

object UserRepository {
    fun findByEmail(email: String): UserRow? = transaction {
        Users.selectAll().where { Users.email eq email }.singleOrNull()?.toUserRow()
    }

    fun findById(id: UUID): UserRow? = transaction {
        Users.selectAll().where { Users.id eq id }.singleOrNull()?.toUserRow()
    }

    fun create(email: String, passwordHash: String, displayName: String): UUID = transaction {
        val now = Clock.System.now()
        Users.insert {
            it[Users.email] = email
            it[Users.passwordHash] = passwordHash
            it[Users.displayName] = displayName
            it[Users.createdAt] = now
            it[Users.updatedAt] = now
        }[Users.id]
    }

    fun markEmailVerified(userId: UUID) = transaction {
        Users.update({ Users.id eq userId }) {
            it[emailVerified] = true
            it[updatedAt] = Clock.System.now()
        }
    }

    fun createVerificationCode(userId: UUID, code: String) = transaction {
        val expiresAt = Clock.System.now().plus(10.minutes)
        EmailVerifications.insert {
            it[EmailVerifications.userId] = userId
            it[EmailVerifications.code] = code
            it[EmailVerifications.expiresAt] = expiresAt
        }
    }

    fun verifyCode(userId: UUID, code: String): Boolean = transaction {
        val now = Clock.System.now()
        val row = EmailVerifications.selectAll().where {
            (EmailVerifications.userId eq userId) and
            (EmailVerifications.code eq code) and
            (EmailVerifications.expiresAt greater now) and
            (EmailVerifications.usedAt.isNull())
        }.singleOrNull() ?: return@transaction false

        EmailVerifications.update({ EmailVerifications.id eq row[EmailVerifications.id] }) {
            it[usedAt] = now
        }
        true
    }

    fun saveRefreshToken(userId: UUID, tokenHash: String, deviceInfo: String?) = transaction {
        val expiresAt = Clock.System.now().plus(30.days)
        RefreshTokens.insert {
            it[RefreshTokens.userId] = userId
            it[RefreshTokens.tokenHash] = tokenHash
            it[RefreshTokens.expiresAt] = expiresAt
            it[RefreshTokens.deviceInfo] = deviceInfo
        }
    }

    fun findRefreshToken(tokenHash: String): ResultRow? = transaction {
        val now = Clock.System.now()
        RefreshTokens.selectAll().where {
            (RefreshTokens.tokenHash eq tokenHash) and
            (RefreshTokens.expiresAt greater now) and
            (RefreshTokens.revokedAt.isNull())
        }.singleOrNull()
    }

    fun revokeRefreshToken(tokenHash: String) = transaction {
        RefreshTokens.update({ RefreshTokens.tokenHash eq tokenHash }) {
            it[revokedAt] = Clock.System.now()
        }
    }

    fun isAdmin(userId: UUID): Boolean = transaction {
        AdminRoles.selectAll().where { AdminRoles.userId eq userId }.count() > 0
    }

    private fun ResultRow.toUserRow() = UserRow(
        id = this[Users.id],
        email = this[Users.email],
        passwordHash = this[Users.passwordHash],
        displayName = this[Users.displayName],
        emailVerified = this[Users.emailVerified]
    )
}
