package com.v2ray.ang.handler

/** Compares numeric versions and prerelease identifiers, ignoring build metadata. */
internal fun compareReleaseVersions(first: String, second: String): Int {
    fun parts(value: String) = value.trim().removePrefix("v").substringBefore('+').split('-', limit = 2)
    val left = parts(first)
    val right = parts(second)
    val leftCore = left[0].split('.').map { it.toLongOrNull() ?: 0L }
    val rightCore = right[0].split('.').map { it.toLongOrNull() ?: 0L }
    for (index in 0 until maxOf(leftCore.size, rightCore.size)) {
        val compared = (leftCore.getOrNull(index) ?: 0L).compareTo(rightCore.getOrNull(index) ?: 0L)
        if (compared != 0) return compared
    }
    if (left.size == 1 && right.size == 1) return 0
    if (left.size == 1) return 1
    if (right.size == 1) return -1
    val leftPre = left[1].split('.')
    val rightPre = right[1].split('.')
    for (index in 0 until minOf(leftPre.size, rightPre.size)) {
        val a = leftPre[index]
        val b = rightPre[index]
        val numericA = a.toLongOrNull()
        val numericB = b.toLongOrNull()
        val compared = when {
            numericA != null && numericB != null -> numericA.compareTo(numericB)
            numericA != null -> -1
            numericB != null -> 1
            else -> a.compareTo(b)
        }
        if (compared != 0) return compared
    }
    return leftPre.size.compareTo(rightPre.size)
}
