package club.okak.aiproxy.crypto

import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object ApiKeyCrypto {
    private const val ALGO = "AES/GCM/NoPadding"
    private const val TAG_LENGTH = 128

    fun encrypt(plaintext: String, secret: String): String {
        val key = SecretKeySpec(secret.toByteArray().copyOf(32), "AES")
        val iv = ByteArray(12).also { java.security.SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance(ALGO)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH, iv))
        val encrypted = cipher.doFinal(plaintext.toByteArray())
        return Base64.getEncoder().encodeToString(iv + encrypted)
    }

    fun decrypt(ciphertext: String, secret: String): String {
        val key = SecretKeySpec(secret.toByteArray().copyOf(32), "AES")
        val combined = Base64.getDecoder().decode(ciphertext)
        val iv = combined.copyOfRange(0, 12)
        val encrypted = combined.copyOfRange(12, combined.size)
        val cipher = Cipher.getInstance(ALGO)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH, iv))
        return String(cipher.doFinal(encrypted))
    }
}
