package com.v2ray.ang.handler

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class BillingPlan(val id: String, val amountKopecks: Int, val days: Int)
data class BillingOrder(val id: String, val account: String, val status: String, val expiresAt: Long?, val pendingSync: Boolean, val paymentUrl: String? = null)
data class BillingSnapshot(val plan: BillingPlan, val instructions: String, val order: BillingOrder?, val plans: List<BillingPlan> = listOf(plan))

interface BillingGateway {
    fun load(): BillingSnapshot
    fun create(planId: String): BillingOrder
    fun create(planId: String, email: String): BillingOrder = create(planId)
}

object TnglBillingClient : BillingGateway {
    // Payment links come only from an authenticated server order.

    private val client = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS).callTimeout(35, TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).build()

    private fun request(path: String, payload: JSONObject? = null): JSONObject {
        val token = TnglSessionStore.token() ?: throw TnglAuthException("session_expired")
        val builder = Request.Builder().url(TnglAuthClient.BASE_URL + path)
            .header("Authorization", "Bearer $token")
        if (payload != null) builder.post(payload.toString().toRequestBody("application/json".toMediaType()))
        return client.newCall(builder.build()).execute().use { response ->
            val data = runCatching { JSONObject(response.body.string()) }.getOrNull()
            if (response.code == 404 || response.code == 501) throw TnglAuthException("billing_unavailable")
            if (!response.isSuccessful) throw TnglAuthException(data?.optString("error") ?: "server_error")
            data ?: throw TnglAuthException("server_error")
        }
    }
    private fun order(data: JSONObject): BillingOrder = BillingOrder(
        data.getString("id"), data.getString("account"), data.getString("status"),
        if (data.isNull("expiresAt")) null else data.getLong("expiresAt"),
        data.optBoolean("pendingSync"), data.optString("paymentUrl").takeIf { it.startsWith("https://") }
    )
    override fun load(): BillingSnapshot {
        val data = request("/v1/billing")
        val plan = data.getJSONObject("plan")
        check(plan.getString("currency") == "RUB")
        return BillingSnapshot(
            BillingPlan(plan.getString("id"), plan.getInt("amountKopecks"), plan.getInt("days")),
            data.getString("instructions"), data.optJSONObject("order")?.let(::order),
            data.optJSONArray("plans")?.let { list -> (0 until list.length()).map { i ->
                val p = list.getJSONObject(i)
                BillingPlan(p.getString("id"), p.getInt("amountKopecks"), p.getInt("days"))
            } } ?: listOf(BillingPlan(plan.getString("id"), plan.getInt("amountKopecks"), plan.getInt("days")))
        )
    }
    override fun create(planId: String): BillingOrder = create(planId, "")
    override fun create(planId: String, email: String): BillingOrder =
        order(request("/v1/billing/orders", JSONObject().put("planId", planId).put("email", email)).getJSONObject("order"))
}
