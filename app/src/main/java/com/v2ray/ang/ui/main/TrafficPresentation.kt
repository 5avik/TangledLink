package com.v2ray.ang.ui.main

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.v2ray.ang.R

@Composable
internal fun formatTrafficSpeed(bytesPerSecond: Double): String {
    val units = listOf(R.string.premium_bytes_second, R.string.premium_kbytes_second,
        R.string.premium_mbytes_second, R.string.premium_gbytes_second)
    var value = bytesPerSecond.coerceAtLeast(0.0)
    var unit = 0
    while (value >= 1024.0 && unit < units.lastIndex) {
        value /= 1024.0
        unit++
    }
    return stringResource(R.string.premium_rate, value, stringResource(units[unit]))
}
