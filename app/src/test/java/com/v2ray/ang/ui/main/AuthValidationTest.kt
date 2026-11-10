package com.v2ray.ang.ui.main

import com.v2ray.ang.R
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class AuthValidationTest {
    @Test fun credentials() {
        assertNull(AuthValidation.credentials("  example123  ", "password1234", null))
        assertNull(AuthValidation.credentials("example123", "password1234", "code"))
        assertNull(AuthValidation.credentials("mika", "password1234", null))
        assertNull(AuthValidation.credentials("mika", "password1234", "code"))
        assertEquals(R.string.auth_required, AuthValidation.credentials("", "password1234", null))
        assertEquals(R.string.auth_required, AuthValidation.credentials("example123", "", null))
        assertEquals(R.string.auth_required, AuthValidation.credentials("example123", "password1234", " "))
        assertEquals(R.string.auth_invalid_id, AuthValidation.credentials("../invalid", "password1234", null))
        assertEquals(R.string.auth_password_policy, AuthValidation.credentials("example123", "short", "code"))
        assertEquals(R.string.auth_password_policy, AuthValidation.credentials("example123", "x".repeat(129), null))
    }
    @Test fun passwordChange() {
        assertNull(AuthValidation.passwordChange("previous-password", "new-password"))
        assertEquals(R.string.auth_required, AuthValidation.passwordChange("", "new-password"))
        assertEquals(R.string.auth_required, AuthValidation.passwordChange("previous", ""))
        assertEquals(R.string.auth_password_policy, AuthValidation.passwordChange("previous", "short"))
        assertEquals(R.string.auth_password_policy, AuthValidation.passwordChange("previous", "x".repeat(129)))
    }
}
