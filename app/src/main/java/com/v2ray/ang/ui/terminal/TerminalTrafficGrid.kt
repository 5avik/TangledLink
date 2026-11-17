package com.v2ray.ang.ui.terminal

import kotlin.math.ceil

/** A relative RX+TX history; empty/zero readings never create decorative activity. */
internal fun terminalTrafficGrid(samples: List<Float>, columns: Int, rows: Int = 4): List<String> {
    require(columns in 1..80 && rows in 1..8)
    val values = samples.takeLast(60).map { if (it.isFinite() && it > 0f) it.toDouble() else 0.0 }
    val peak = values.maxOrNull() ?: 0.0
    val buckets = if (values.size > columns) {
        List(columns) { index ->
            values.subList(index * values.size / columns, (index + 1) * values.size / columns).max()
        }
    } else List(columns - values.size) { 0.0 } + values
    val heights = buckets.map { if (peak == 0.0) 0 else ceil(it / peak * rows * 2).toInt() }
    return List(rows) { row ->
        heights.joinToString("") { height ->
            val floor = (rows - row - 1) * 2
            when {
                height >= floor + 2 -> "█"
                height == floor + 1 -> "▄"
                else -> " "
            }
        }
    }
}
