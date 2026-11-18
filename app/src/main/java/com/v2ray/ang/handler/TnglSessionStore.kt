package com.v2ray.ang.handler

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import com.v2ray.ang.AngApplication
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Session tokens are encrypted with a non-exportable Android Keystore key, outside backups. */
object TnglSessionStore {
    private const val ALIAS = "tngl_auth_session"
    private val file get() = AtomicFile(File(AngApplication.application.noBackupFilesDir, "auth-session"))
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    @Synchronized fun token(): String? = runCatching {
        val bytes = file.readFully()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        val json = JSONObject(String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8))
        if (json.getLong("expiresAt") <= System.currentTimeMillis() / 1000) null else json.getString("token")
    }.getOrNull()

    @Synchronized fun save(token: String, expiresAt: Long) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val plaintext = JSONObject().put("token", token).put("expiresAt", expiresAt).toString()
        val bytes = cipher.iv + cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val atomic = file
        val output = atomic.startWrite()
        try { output.write(bytes); atomic.finishWrite(output) }
        catch (error: Exception) { atomic.failWrite(output); throw error }
    }
    @Synchronized fun clear() { file.delete() }
}
