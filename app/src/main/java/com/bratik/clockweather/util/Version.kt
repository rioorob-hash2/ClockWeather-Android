package com.bratik.clockweather.util

/** Сравнение версий «1.10» > «1.9» > «1.4». */
object Version {

    /** true, если [latest] новее [current]. Нечисловые части игнорируются. */
    fun isNewer(latest: String, current: String): Boolean {
        val a = parts(latest)
        val b = parts(current)
        val size = maxOf(a.size, b.size)
        for (i in 0 until size) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /** Убирает префиксы «v», «V», пробелы. */
    fun normalize(raw: String): String =
        raw.trim().removePrefix("v").removePrefix("V").trim()

    /** «1.10-beta» -> [1, 10] */
    fun parts(raw: String): List<Int> {
        val cleaned = normalize(raw).substringBefore('-').substringBefore('+')
        if (cleaned.isEmpty()) return emptyList()
        return cleaned.split('.').mapNotNull { chunk ->
            chunk.takeWhile { it.isDigit() }.takeIf { it.isNotEmpty() }?.toIntOrNull()
        }
    }
}
