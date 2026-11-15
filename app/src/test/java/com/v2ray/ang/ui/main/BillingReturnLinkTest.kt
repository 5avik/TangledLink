package com.v2ray.ang.ui.main

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class BillingReturnLinkTest {
    private val view="android.intent.action.VIEW"
    @Test fun onlyExactReturnDestinationsAreAccepted() {
        for(url in listOf("https://app.tngl.pro/billing/return",
            "https://app.tngl.pro/billing/return?invoiceId=untrusted&status=success&account=other",
            "tangledlink://billing/return")) assertTrue(BillingReturnLink.accepts(view,url),url)
        for(url in listOf(null,"","http://app.tngl.pro/billing/return",
            "https://app.tngl.pro.evil.example/billing/return","https://evil.example/billing/return",
            "https://app.tngl.pro:8443/billing/return","https://user@app.tngl.pro/billing/return",
            "https://app.tngl.pro/billing/return/extra","https://app.tngl.pro/billing/%72eturn",
            "https://app.tngl.pro/billing/return#other","tangledlink://other/return",
            "tangledlink://billing/other","intent://billing/return","invalid uri", "a".repeat(3000)))
            assertFalse(BillingReturnLink.accepts(view,url),url)
        assertFalse(BillingReturnLink.accepts("android.intent.action.SEND","https://app.tngl.pro/billing/return"))
        assertFalse(BillingReturnLink.accepts(null,"https://app.tngl.pro/billing/return"))
    }
}
