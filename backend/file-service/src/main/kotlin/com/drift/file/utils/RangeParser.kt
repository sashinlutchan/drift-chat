package com.drift.file.utils

import com.drift.file.exceptions.DomainError
import com.drift.file.models.ByteRange

object RangeParser {
    private val SINGLE = Regex("^bytes=(\\d*)-(\\d*)$")

    fun parse(
        header: String?,
        size: Long,
    ): ByteRange? {
        val match = header?.trim()?.let { SINGLE.matchEntire(it) } ?: return null
        val (from, to) = match.destructured
        if (from.isEmpty() && to.isEmpty()) return null
        val range =
            if (from.isEmpty()) {
                val suffix = to.toLong()
                if (suffix == 0L) throw notSatisfiable(size)
                ByteRange((size - suffix).coerceAtLeast(0), size - 1)
            } else {
                val start = from.toLong()
                val end = if (to.isEmpty()) size - 1 else minOf(to.toLong(), size - 1)
                ByteRange(start, end)
            }
        if (size == 0L || range.start >= size || range.end < range.start) throw notSatisfiable(size)
        return range
    }

    private fun notSatisfiable(size: Long) = DomainError.RangeNotSatisfiable("Range not satisfiable for a file of $size bytes.")
}
