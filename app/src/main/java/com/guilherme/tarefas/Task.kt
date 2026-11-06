package com.guilherme.tarefas

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth

enum class Repeat { ONCE, DAILY, WEEKLY, MONTHLY, YEARLY }

/** weekdays: 1 = segunda ... 7 = domingo. month: 1..12. */
data class Task(
    val id: Int = 0,
    val title: String = "",
    val notes: String = "",
    val repeat: Repeat = Repeat.ONCE,
    val hour: Int = 8,
    val minute: Int = 0,
    val date: LocalDate = LocalDate.now(),
    val weekdays: Set<Int> = setOf(1, 2, 3, 4, 5),
    val day: Int = 1,
    val month: Int = 1,
    val enabled: Boolean = true,
    val done: Boolean = false,
) {
    val active: Boolean get() = enabled && !done

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("notes", notes)
        put("repeat", repeat.name)
        put("hour", hour)
        put("minute", minute)
        put("date", date.toString())
        put("weekdays", JSONArray().apply { weekdays.sorted().forEach { put(it) } })
        put("day", day)
        put("month", month)
        put("enabled", enabled)
        put("done", done)
    }

    companion object {
        fun fromJson(o: JSONObject): Task {
            val wd = o.optJSONArray("weekdays")
            return Task(
                id = o.getInt("id"),
                title = o.optString("title"),
                notes = o.optString("notes"),
                repeat = runCatching { Repeat.valueOf(o.optString("repeat")) }.getOrDefault(Repeat.ONCE),
                hour = o.optInt("hour", 8),
                minute = o.optInt("minute", 0),
                date = runCatching { LocalDate.parse(o.optString("date")) }.getOrDefault(LocalDate.now()),
                weekdays = if (wd == null) emptySet() else (0 until wd.length()).map { wd.getInt(it) }.toSet(),
                day = o.optInt("day", 1),
                month = o.optInt("month", 1),
                enabled = o.optBoolean("enabled", true),
                done = o.optBoolean("done", false),
            )
        }
    }
}

object Recurrence {
    /** Próximo momento estritamente depois de [after], ou null se não houver (tarefa única que já passou). */
    fun next(t: Task, after: LocalDateTime): LocalDateTime? {
        val time = LocalTime.of(t.hour, t.minute)
        val today = after.toLocalDate()
        return when (t.repeat) {
            Repeat.ONCE -> LocalDateTime.of(t.date, time).takeIf { it.isAfter(after) }
            Repeat.DAILY -> {
                val dt = LocalDateTime.of(today, time)
                if (dt.isAfter(after)) dt else dt.plusDays(1)
            }
            Repeat.WEEKLY -> (0L..7L)
                .map { LocalDateTime.of(today.plusDays(it), time) }
                .firstOrNull { it.isAfter(after) && it.dayOfWeek.value in t.weekdays }
            Repeat.MONTHLY -> (0L..2L)
                .map {
                    val ym = YearMonth.from(today).plusMonths(it)
                    LocalDateTime.of(ym.atDay(minOf(t.day, ym.lengthOfMonth())), time)
                }
                .firstOrNull { it.isAfter(after) }
            Repeat.YEARLY -> (0..2)
                .map {
                    val ym = YearMonth.of(today.year + it, t.month)
                    LocalDateTime.of(ym.atDay(minOf(t.day, ym.lengthOfMonth())), time)
                }
                .firstOrNull { it.isAfter(after) }
        }
    }
}
