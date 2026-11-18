package com.v2ray.ang.handler

import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class TnglAuthException(val errorCode: String, val retryAfterSeconds: Long? = null) : IOException(errorCode)

data class AccountAccess(val managed:Boolean=false,val subscribed:Boolean=false,val trialAvailable:Boolean=false)

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
            if (!response.isSuccessful) throw TnglAuthException(json?.optString("error") ?: "server_error",
                (json?.optLong("retryAfterSeconds", -1)?.takeIf { it>=0 } ?: response.header("Retry-After")?.toLongOrNull()))
            return json ?: throw IOException("Invalid authentication response")
        }
    }
    fun emailAuthentication(id:String,password:String,email:String?,registration:Boolean,activation:String?,code:String?): EmailAuthResult {
        val body=JSONObject().put("id",id).put("password",password)
        email?.let{body.put("email",it)}
        code?.let{body.put("emailCode",it)}
        activation?.let{body.put("code",it)}
        val response=post(if(activation!=null) "/v1/activate" else if(registration) "/v1/mobile-auth/register" else "/v1/mobile-auth/login",body)
        if(response.optBoolean("emailRequired")) return EmailAuthResult(required=true,retrySeconds=response.optLong("retryAfterSeconds",60))
        return EmailAuthResult(token=response.getString("token"),expiresAt=response.getLong("expiresAt"))
    }
    fun acceptEmailSession(result:EmailAuthResult) {
        TnglSessionStore.save(requireNotNull(result.token),result.expiresAt)
    }
    fun bindEmail(email:String,code:String?,token:String): EmailAuthResult {
        val body=JSONObject().put("email",email)
        code?.let{body.put("emailCode",it)}
        val response=post("/v1/mobile-auth/bind",body,token)
        return EmailAuthResult(required=response.optBoolean("emailRequired"),retrySeconds=response.optLong("retryAfterSeconds",60))
    }
    fun recoverPassword(id:String, email:String?, invite:String?, code:String?, password:String?): JSONObject {
        val body=JSONObject().put("id",id).put("email",email).put("inviteCode",invite)
            .put("emailCode",code).put("password",password)
        return post("/v1/mobile-auth/"+if(invite==null) "recover" else "recover-first",body)
    }
    fun verifiedEmail(token:String):String? {
        val request=Request.Builder().url(BASE_URL+"/v1/mobile-auth/email").header("Authorization","Bearer "+token).build()
        client.newCall(request).execute().use {response->
            val data=runCatching{JSONObject(response.body.string())}.getOrNull()
            if(!response.isSuccessful)throw TnglAuthException(data?.optString("error")?:"server_error")
            return if(data==null || data.isNull("email"))null else data.getString("email")
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
    fun register(id: String, password: String) {
        val response = post("/v1/register", JSONObject().put("id",id).put("password",password))
        TnglSessionStore.save(response.getString("token"),response.getLong("expiresAt"))
    }
    fun accountStatus(token: String = TnglSessionStore.token() ?: throw TnglAuthException("session_expired")): AccountAccess {
        val request = Request.Builder().url(BASE_URL + "/v1/account").header("Authorization","Bearer $token").build()
        client.newCall(request).execute().use { response ->
            // Older backend keeps the legacy client behavior until server deployment.
            if (response.code == 404) return AccountAccess()
            val data = runCatching { JSONObject(response.body?.string().orEmpty()) }.getOrNull()
            if (!response.isSuccessful) throw TnglAuthException(data?.optString("error") ?: "server_error")
            return AccountAccess(data?.optBoolean("managed") == true,
                data?.optBoolean("subscribed") == true,data?.optBoolean("trialAvailable") == true)
        }
    }
    fun startTrial(token: String = TnglSessionStore.token() ?: throw TnglAuthException("session_expired")) {
        post("/v1/trial",JSONObject(),token)
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
