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
    @Test fun registration() {
        assertNull(AuthValidation.registration(" new_user-1 ", "password1234"))
        assertEquals(R.string.auth_required, AuthValidation.registration("", "password1234"))
        assertEquals(R.string.auth_required, AuthValidation.registration("user", ""))
        for (id in listOf("ABCD", "ab", "a".repeat(33), "../user", "a@b"))
            assertEquals(R.string.registration_id_rule, AuthValidation.registration(id,"password1234"))
        assertEquals(R.string.auth_password_policy, AuthValidation.registration("user","short"))
        assertEquals(R.string.auth_password_policy, AuthValidation.registration("user","a".repeat(129)))
    }
    @Test fun passwordChange() {
        assertNull(AuthValidation.passwordChange("previous-password", "new-password"))
        assertEquals(R.string.auth_required, AuthValidation.passwordChange("", "new-password"))
        assertEquals(R.string.auth_required, AuthValidation.passwordChange("previous", ""))
        assertEquals(R.string.auth_password_policy, AuthValidation.passwordChange("previous", "short"))
        assertEquals(R.string.auth_password_policy, AuthValidation.passwordChange("previous", "x".repeat(129)))
    }
}
