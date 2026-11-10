package com.v2ray.ang.ui.main

import com.v2ray.ang.R

internal object AuthValidation {
    fun credentials(id: String, password: String, code: String?): Int? = when {
        id.isBlank() || password.isEmpty() || code?.isBlank() == true -> R.string.auth_required
        !Regex("[A-Za-z0-9_-]{1,128}").matches(id.trim()) -> R.string.auth_invalid_id
        password.length > 128 || (code != null && password.length < 12) -> R.string.auth_password_policy
        else -> null
    }
    fun passwordChange(current: String, newPassword: String): Int? = when {
        current.isEmpty() || newPassword.isEmpty() -> R.string.auth_required
        newPassword.length !in 12..128 -> R.string.auth_password_policy
        else -> null
    }
}
