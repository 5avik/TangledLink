package com.v2ray.ang.ui.checkupdate

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class UpdateDownloadUrlTest {
    @Test fun validatesReleaseDownloads() {
        assertTrue(isUpdateDownloadUrl("https://github.com/5avik/TangledLink/releases/download/v0.7/tngl.apk"))
        listOf("", "https://example.com/a.apk", "http://github.com/5avik/TangledLink/releases/download/v1/a.apk",
            "https://github.com/other/app/releases/download/v1/a.apk",
            "https://github.com/5avik/TangledLink/releases/download/v1/a.html",
            "https://github.com.evil.test/5avik/TangledLink/releases/download/v1/a.apk",
            "https://user@github.com/5avik/TangledLink/releases/download/v1/a.apk"
        ).forEach { assertFalse(isUpdateDownloadUrl(it), it) }
    }
}
