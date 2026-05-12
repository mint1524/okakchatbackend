package club.okak.auth.services

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

class MailService(
    private val baseUrl: String,
    private val token: String,
    private val siteUrl: String
) {
    private val log = LoggerFactory.getLogger(MailService::class.java)
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) { json() }
    }

    suspend fun sendVerificationCode(email: String, code: String, displayName: String) {
        try {
            client.post("$baseUrl/internal/send") {
                header(HttpHeaders.Authorization, "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject {
                    put("to", buildJsonArray { add(email) })
                    put("subject", "Your OKAK verification code")
                    put("category", "transactional")
                    put("html", buildHtml(code, displayName))
                    put("text", "Your OKAK verification code: $code. Valid for 10 minutes.")
                    put("metadata", buildJsonObject { put("kind", "email_verification") })
                })
            }
        } catch (e: Exception) {
            log.error("Failed to send verification email to $email", e)
            throw e
        }
    }

    private fun buildHtml(code: String, displayName: String) = """
        <p>Hi $displayName,</p>
        <p>Your OKAK verification code is: <strong>$code</strong></p>
        <p>Valid for 10 minutes. If you did not request this, ignore this email.</p>
        <p>— <a href="$siteUrl">OKAK</a></p>
    """.trimIndent()
}
