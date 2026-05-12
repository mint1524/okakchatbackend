package club.okak.auth.routes

import club.okak.auth.repository.UserRepository
import club.okak.auth.services.AuthService
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable data class RegisterRequest(val email: String, val password: String, val displayName: String)
@Serializable data class VerifyRequest(val userId: String, val code: String)
@Serializable data class LoginRequest(val email: String, val password: String, val deviceInfo: String? = null)
@Serializable data class RefreshRequest(val refreshToken: String, val deviceInfo: String? = null)
@Serializable data class LogoutRequest(val refreshToken: String)
@Serializable data class TokenResponse(val accessToken: String, val refreshToken: String)
@Serializable data class RegisterResponse(val userId: String, val message: String)
@Serializable data class MeResponse(val userId: String, val email: String, val displayName: String, val isAdmin: Boolean)

fun Route.authRoutes(authService: AuthService) {
    route("/api/auth") {
        post("/register") {
            val req = call.receive<RegisterRequest>()
            val userId = authService.register(req.email, req.password, req.displayName)
            call.respond(HttpStatusCode.Created, RegisterResponse(userId.toString(), "Verification code sent to ${req.email}"))
        }

        post("/verify") {
            val req = call.receive<VerifyRequest>()
            val pair = authService.verify(UUID.fromString(req.userId), req.code)
            call.respond(TokenResponse(pair.accessToken, pair.refreshToken))
        }

        post("/login") {
            val req = call.receive<LoginRequest>()
            val pair = authService.login(req.email, req.password, req.deviceInfo)
            call.respond(TokenResponse(pair.accessToken, pair.refreshToken))
        }

        post("/refresh") {
            val req = call.receive<RefreshRequest>()
            val pair = authService.refresh(req.refreshToken, req.deviceInfo)
            call.respond(TokenResponse(pair.accessToken, pair.refreshToken))
        }

        authenticate("auth-jwt") {
            post("/logout") {
                val req = call.receive<LogoutRequest>()
                authService.logout(req.refreshToken)
                call.respond(HttpStatusCode.NoContent)
            }

            get("/me") {
                val principal = call.principal<JWTPrincipal>()!!
                val userId = principal.payload.subject
                val email = principal.payload.getClaim("email").asString()
                val isAdmin = principal.payload.getClaim("admin").asBoolean() ?: false
                val user = UserRepository.findById(UUID.fromString(userId))
                call.respond(MeResponse(userId, email, user?.displayName ?: "", isAdmin))
            }
        }
    }
}
