package com.v2ray.ang.handler

import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class TnglAuthException(val errorCode: String) : IOException(errorCode)

object TnglAuthClient {
    const val BASE_URL = "https://auth.tngl.pro:8443"
    const val SUBSCRIPTION_URL = "$BASE_URL/v1/subscription"
    private val client = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS).callTimeout(30, TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).build()

    private fun post(path: String, body: JSONObject, token: String? = null): JSONObject {
        val builder = Request.Builder().url(BASE_URL + path)
            .post(body.toString().toRequestBody("application/json".toMediaType()))
        token?.let { builder.header("Authorization", "Bearer $it") }
        client.newCall(builder.build()).execute().use { response ->
            val json = runCatching { JSONObject(response.body?.string().orEmpty()) }.getOrNull()
            if (!response.isSuccessful) throw TnglAuthException(json?.optString("error") ?: "server_error")
            return json ?: throw IOException("Invalid authentication response")
        }
    }
    fun login(id: String, password: String, activationCode: String?) {
        val body = JSONObject().put("id", id).put("password", password)
        val path = if (activationCode != null) {
            body.put("code", activationCode)
            "/v1/activate"
        } else "/v1/login"
        val response = post(path, body)
        TnglSessionStore.save(response.getString("token"), response.getLong("expiresAt"))
    }
    fun changePassword(current: String, newPassword: String) {
        val token = TnglSessionStore.token() ?: throw TnglAuthException("session_expired")
        post("/v1/password", JSONObject().put("currentPassword", current).put("newPassword", newPassword), token)
    }
    fun logout() {
        val token = TnglSessionStore.token()
        TnglSessionStore.clear()
        if (token != null) runCatching { post("/v1/logout", JSONObject(), token) }
    }
    fun subscription(onHeaders: ((Headers) -> Unit)?): String {
        val token = TnglSessionStore.token() ?: throw TnglAuthException("session_expired")
        val request = Request.Builder().url(SUBSCRIPTION_URL).header("Authorization", "Bearer $token").build()
        client.newCall(request).execute().use { response ->
            if (response.code == 401) {
                TnglSessionStore.clear()
                throw TnglAuthException("session_expired")
            }
            if (!response.isSuccessful) throw TnglAuthException(
                if (response.code == 403) "subscription_inactive" else "subscription_unavailable")
            onHeaders?.invoke(response.headers)
            return response.body?.string() ?: throw IOException("Empty subscription")
        }
    }
}
