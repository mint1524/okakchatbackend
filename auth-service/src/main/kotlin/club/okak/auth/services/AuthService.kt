package club.okak.auth.services

import club.okak.auth.repository.UserRepository
import club.okak.shared.auth.JwtConfig
import club.okak.shared.auth.JwtUtils
import club.okak.shared.db.RefreshTokens
import org.mindrot.jbcrypt.BCrypt
import org.slf4j.LoggerFactory
import java.security.MessageDigest
import java.util.UUID

data class TokenPair(val accessToken: String, val refreshToken: String)

class AuthService(
    private val jwtConfig: JwtConfig,
    private val mailService: MailService
) {
    private val log = LoggerFactory.getLogger(AuthService::class.java)

    suspend fun register(email: String, password: String, displayName: String): UUID {
        require(email.contains("@") && email.contains(".")) { "Invalid email" }
        require(password.length >= 8) { "Password must be at least 8 characters" }
        require(UserRepository.findByEmail(email) == null) { "Email already registered" }

        val hash = BCrypt.hashpw(password, BCrypt.gensalt(12))
        val userId = UserRepository.create(email, hash, displayName)
        val code = (100000..999999).random().toString()
        UserRepository.createVerificationCode(userId, code)

        // Mail failure doesn't block registration — user can request resend later
        try {
            mailService.sendVerificationCode(email, code, displayName)
        } catch (e: Exception) {
            log.error("Failed to send verification email to $email (code=$code)", e)
        }

        return userId
    }

    fun verify(userId: UUID, code: String): TokenPair {
        val user = UserRepository.findById(userId) ?: error("User not found")
        require(UserRepository.verifyCode(userId, code)) { "Invalid or expired code" }
        UserRepository.markEmailVerified(userId)
        val isAdmin = UserRepository.isAdmin(userId)
        return issueTokens(userId, user.email, isAdmin, null)
    }

    fun login(email: String, password: String, deviceInfo: String?): TokenPair {
        val user = UserRepository.findByEmail(email)
            ?: throw IllegalArgumentException("Invalid credentials")
        require(BCrypt.checkpw(password, user.passwordHash)) { "Invalid credentials" }
        require(user.emailVerified) { "Email not verified" }
        val isAdmin = UserRepository.isAdmin(user.id)
        return issueTokens(user.id, user.email, isAdmin, deviceInfo)
    }

    fun refresh(rawToken: String, deviceInfo: String?): TokenPair {
        val hash = sha256(rawToken)
        val row = UserRepository.findRefreshToken(hash)
            ?: throw IllegalArgumentException("Invalid refresh token")
        val userId = row[RefreshTokens.userId]
        val user = UserRepository.findById(userId) ?: error("User not found")
        UserRepository.revokeRefreshToken(hash)
        val isAdmin = UserRepository.isAdmin(userId)
        return issueTokens(userId, user.email, isAdmin, deviceInfo)
    }

    fun logout(rawToken: String) {
        UserRepository.revokeRefreshToken(sha256(rawToken))
    }

    private fun issueTokens(userId: UUID, email: String, isAdmin: Boolean, deviceInfo: String?): TokenPair {
        val accessToken = JwtUtils.makeAccessToken(userId, email, isAdmin, jwtConfig)
        val rawRefresh = JwtUtils.makeRefreshToken()
        UserRepository.saveRefreshToken(userId, sha256(rawRefresh), deviceInfo)
        return TokenPair(accessToken, rawRefresh)
    }

    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}
