package com.bratik.clockweather.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/** Форматирование времени и температур — без обращения к Context, чтобы гонять в юнит-тестах. */
object Format {

    /**
     * Форматтер создаём на каждый вызов: SimpleDateFormat запоминает TimeZone в момент создания,
     * а виджет должен переживать смену часового пояса без перезапуска процесса.
     */
    private fun newFmt(pattern: String, locale: Locale): SimpleDateFormat =
        SimpleDateFormat(pattern, locale)

    /** Время «20:08». Формат 24-часовой намеренно: так в оригинале и понятнее. */
    fun time(ms: Long): String = newFmt("HH:mm", Locale.getDefault()).format(Date(ms))

    /** Дата «сб, 14 ноября». */
    fun dateShort(ms: Long): String =
        newFmt("EEE, d MMMM", Locale("ru", "RU")).format(Date(ms)).replace(".", "")

    /** Короткий день недели для полосы прогноза: «2026-11-14» → «СБ». */
    fun dayShort(isoDate: String): String {
        return try {
            val date = newFmt("yyyy-MM-dd", Locale.US).parse(isoDate) ?: return ""
            val ru = Locale("ru", "RU")
            newFmt("EEE", ru).format(date).replace(".", "").uppercase(ru)
        } catch (e: Exception) {
            ""
        }
    }

    /** Подпись часа в ленте: «22», а для полуночи — день недели «ВС». */
    fun hourShort(hourIso: String): String {
        val time = hourIso.substringAfter("T", "")
        if (time.length < 2) return ""
        val hh = time.substring(0, 2)
        return if (hh == "00") dayShort(hourIso.take(10)) else hh
    }

    /** Температура «14°» (или «57°» по Фаренгейту). */
    fun temp(celsius: Double, fahrenheit: Boolean): String {
        val v = if (fahrenheit) celsius * 9.0 / 5.0 + 32.0 else celsius
        return "${v.roundToInt()}°"
    }

    /** «19° / 12°» — max / min. */
    fun minMax(minC: Double, maxC: Double, fahrenheit: Boolean): String =
        "${temp(maxC, fahrenheit)} / ${temp(minC, fahrenheit)}"

    /** Компактное «3.2 ГБ» из байт. */
    fun gb(bytes: Long): String = String.format(Locale.US, "%.1f ГБ", bytes / 1073741824.0)

    /** Возраст данных: «обновлено 5 мин назад». */
    fun updatedAgo(updatedAt: Long, now: Long): String {
        if (updatedAt <= 0L) return "нет данных"
        val min = ((now - updatedAt) / 60000L).coerceAtLeast(0L)
        return if (min < 1L) "только что" else "$min мин назад"
    }
}
