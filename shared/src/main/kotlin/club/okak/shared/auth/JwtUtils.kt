package club.okak.shared.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import java.util.Date
import java.util.UUID

data class JwtConfig(
    val secret: String,
    val accessTtlMinutes: Long,
    val refreshTtlDays: Long,
    val issuer: String = "okakchat"
)

object JwtUtils {
    fun makeAccessToken(userId: UUID, email: String, isAdmin: Boolean, cfg: JwtConfig): String {
        val algorithm = Algorithm.HMAC256(cfg.secret)
        return JWT.create()
            .withIssuer(cfg.issuer)
            .withSubject(userId.toString())
            .withClaim("email", email)
            .withClaim("admin", isAdmin)
            .withExpiresAt(Date(System.currentTimeMillis() + cfg.accessTtlMinutes * 60 * 1000))
            .sign(algorithm)
    }

    fun makeRefreshToken(): String =
        UUID.randomUUID().toString() + "-" + UUID.randomUUID().toString()

    fun verifier(cfg: JwtConfig) =
        JWT.require(Algorithm.HMAC256(cfg.secret))
            .withIssuer(cfg.issuer)
            .build()
}
