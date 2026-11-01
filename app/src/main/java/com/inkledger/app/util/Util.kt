package com.inkledger.app.util

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

fun nowMillis(): Long = System.currentTimeMillis()

fun Long.toLocalDate(): LocalDate =
    Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate()

fun Long.toLocalDateTime(): LocalDateTime =
    Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDateTime()

fun LocalDate.toMillis(): Long =
    atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

/** 以「月初日」归一化的月份周期起点，startDay 取值 1..28 */
fun LocalDate.monthPeriodStart(startDay: Int): LocalDate {
    val d = startDay.coerceIn(1, 28)
    return if (dayOfMonth >= d) withDayOfMonth(d) else minusMonths(1).withDayOfMonth(d)
}

fun LocalDate.prevMonthPeriod(startDay: Int): LocalDate = monthPeriodStart(startDay).minusMonths(1)

fun LocalDate.nextMonthPeriod(startDay: Int): LocalDate = monthPeriodStart(startDay).plusMonths(1)

fun daysBetween(from: LocalDate, to: LocalDate): Long = ChronoUnit.DAYS.between(from, to)

fun dayLabel(ld: LocalDate): String {
    val today = LocalDate.now()
    return when (ld) {
        today -> "今天"
        today.minusDays(1) -> "昨天"
        today.minusDays(2) -> "前天"
        else -> ld.format(
            DateTimeFormatter.ofPattern(
                if (ld.year == today.year) "M月d日 EEEE" else "yyyy年M月d日 EEEE",
                Locale.CHINA
            )
        )
    }
}

fun monthTitle(periodStart: LocalDate, startDay: Int): String {
    // 自定义周期：紧凑数字区间（10.15 - 11.14），跨年带年份
    if (startDay > 1) {
        val end = periodStart.nextMonthPeriod(startDay).minusDays(1)
        val pattern = if (periodStart.year == end.year) "M.d" else "yyyy.M.d"
        return periodStart.format(DateTimeFormatter.ofPattern(pattern, Locale.CHINA)) +
            " - " + end.format(DateTimeFormatter.ofPattern("M.d", Locale.CHINA))
    }
    val today = LocalDate.now().monthPeriodStart(startDay)
    return when (periodStart) {
        today -> "本月"
        today.plusMonths(1) -> "下月"
        today.minusMonths(1) -> "上月"
        else -> periodStart.format(DateTimeFormatter.ofPattern("yyyy年M月", Locale.CHINA))
    }
}

/** 分 → 带千分位的金额字符串，如 234850 -> ¥2,348.50 */
fun formatMoney(minor: Long, currency: String = "¥"): String {
    val neg = minor < 0
    val a = kotlin.math.abs(minor)
    val body = String.format(Locale.US, "%,d.%02d", a / 100, a % 100)
    return (if (neg) "-" else "") + currency + body
}

/** 分 → 紧凑金额：千分位分隔，整数隐藏 .00，数值永远精确（¥12,500 不会变成 ¥1.3万） */
fun formatCompact(minor: Long, currency: String = "¥"): String {
    val neg = minor < 0
    val a = kotlin.math.abs(minor)
    val body = if (a % 100 == 0L) String.format(Locale.US, "%,d", a / 100)
    else String.format(Locale.US, "%,d.%02d", a / 100, a % 100)
    return (if (neg) "-" else "") + currency + body
}

/** 图表柱顶标签：<1万精确到分；1万级两位小数万；≥10万一位小数万。大单位一律截尾不进位，显示值永远 ≤ 真实值 */
fun formatChart(minor: Long): String {
    val a = kotlin.math.abs(minor)
    val yuan = a / 100.0
    val sign = if (minor < 0) "-" else ""
    return when {
        yuan >= 100_000 -> String.format(Locale.US, "%s%.1f万", sign, (yuan / 10_000 * 10).toLong() / 10.0)
        yuan >= 10_000 -> String.format(Locale.US, "%s%.2f万", sign, (yuan / 10_000 * 100).toLong() / 100.0)
        a % 100 == 0L -> String.format(Locale.US, "%s%,d", sign, a / 100)
        else -> String.format(Locale.US, "%s%,d.%02d", sign, a / 100, a % 100)
    }
}

/** 键盘输入 "1234.5" -> 123450 分；非法返回 null */
fun keypadToMinor(raw: String): Long? {
    val s = raw.trim().trimStart('0').ifEmpty { return if (raw.isNotEmpty()) 0L else null }
    if (!Regex("""^\d{1,7}(\.\d{0,2})?$""").matches(s)) return null
    val v = s.toDoubleOrNull() ?: return null
    return Math.round(v * 100)
}

/** 金额输入字符串显示格式化（键盘态直接展示，仅限制长度） */
fun formatKeypadDisplay(raw: String): String {
    var s = raw
    val i = s.indexOf('.')
    if (i >= 0 && s.length - i - 1 > 2) s = s.substring(0, i + 3)
    if (s.replace(".", "").length > 8) s = s.take(8)
    return s
}
