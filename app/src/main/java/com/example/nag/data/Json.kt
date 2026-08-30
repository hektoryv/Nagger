package com.example.nag.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Turning habits and entries into JSON and back. Deliberately free of Android types
 * so the format can be exercised on its own.
 */
object Json {

    fun habitToJson(h: Habit): JSONObject = JSONObject().apply {
        put("id", h.id)
        put("title", h.title)
        put("kind", h.kind.name)
        put("intervalDays", h.intervalDays)
        put("weekdays", JSONArray().apply { h.weekdays.sortedBy { it.value }.forEach { put(it.value) } })
        put("timesPerWeek", h.timesPerWeek)
        put("startDate", h.startDate.toString())
        put("missPolicy", h.missPolicy.name)
        put("colorIndex", h.colorIndex)
        put("tracksAmount", h.tracksAmount)
        put("unit", h.unit)
        put("paused", h.paused)
        put("finished", h.finished)
    }

    fun habitFromJson(o: JSONObject): Habit {
        // v2 called this "type" and only knew two values.
        val kind = when {
            o.has("kind") -> runCatching { ScheduleKind.valueOf(o.getString("kind")) }
                .getOrDefault(ScheduleKind.EVERY_N_DAYS)

            o.optString("type", "") == "UNTIL_DONE" -> ScheduleKind.UNTIL_DONE
            else -> ScheduleKind.EVERY_N_DAYS
        }

        val weekdayArray = o.optJSONArray("weekdays")
        val weekdays = if (weekdayArray == null) emptySet() else
            (0 until weekdayArray.length())
                .mapNotNull { runCatching { DayOfWeek.of(weekdayArray.getInt(it)) }.getOrNull() }
                .toSet()

        return Habit(
            id = o.getString("id"),
            title = o.getString("title"),
            kind = kind,
            intervalDays = o.optInt("intervalDays", 1).coerceAtLeast(1),
            weekdays = weekdays,
            timesPerWeek = o.optInt("timesPerWeek", 3).coerceIn(1, 7),
            startDate = parseDate(
                o.optString("startDate", "").ifEmpty {
                    // v1 files carried no start date, only a next-due or last-done.
                    o.optString("lastDone", "").takeIf { it.isNotEmpty() && it != "null" }
                        ?: o.optString("nextDue", "")
                }
            ) ?: LocalDate.now(),
            missPolicy = runCatching { MissPolicy.valueOf(o.getString("missPolicy")) }
                .getOrDefault(MissPolicy.ROLL_OVER),
            colorIndex = o.optInt("colorIndex", 0),
            tracksAmount = o.optBoolean("tracksAmount", false),
            unit = o.optString("unit", "reps").ifEmpty { "reps" },
            paused = o.optBoolean("paused", false),
            finished = o.optBoolean("finished", false)
        )
    }

    fun entryToJson(e: Entry): JSONObject = JSONObject().apply {
        put("habitId", e.habitId)
        put("date", e.date.toString())
        put("amount", e.amount ?: JSONObject.NULL)
    }

    fun entryFromJson(o: JSONObject): Entry? {
        val date = parseDate(o.optString("date", "")) ?: return null
        return Entry(
            habitId = o.optString("habitId", ""),
            date = date,
            amount = if (o.isNull("amount")) null else o.optInt("amount")
        )
    }

    fun habitsToString(habits: List<Habit>): String =
        JSONArray().apply { habits.forEach { put(habitToJson(it)) } }.toString()

    fun habitsFromString(raw: String): List<Habit> = runCatching {
        val array = JSONArray(raw)
        (0 until array.length()).mapNotNull {
            runCatching { habitFromJson(array.getJSONObject(it)) }.getOrNull()
        }
    }.getOrDefault(emptyList())

    fun logToString(log: List<Entry>): String =
        JSONArray().apply { log.forEach { put(entryToJson(it)) } }.toString()

    fun logFromString(raw: String): List<Entry> = runCatching {
        val array = JSONArray(raw)
        (0 until array.length()).mapNotNull {
            runCatching { entryFromJson(array.getJSONObject(it)) }.getOrNull()
        }
    }.getOrDefault(emptyList())

    /** True if this looks like data written before habits had a start date. */
    fun looksLikeV1(raw: String): Boolean = runCatching {
        val array = JSONArray(raw)
        (0 until array.length()).any { !array.getJSONObject(it).has("startDate") }
    }.getOrDefault(false)

    /** v1 stored a single "lastDone" per habit rather than a log. Rescue those ticks. */
    fun v1Entries(raw: String): List<Entry> = runCatching {
        val array = JSONArray(raw)
        (0 until array.length()).mapNotNull { i ->
            val o = array.getJSONObject(i)
            val date = parseDate(o.optString("lastDone", "")) ?: return@mapNotNull null
            Entry(o.getString("id"), date, null)
        }
    }.getOrDefault(emptyList())

    private fun parseDate(text: String): LocalDate? {
        if (text.isEmpty() || text == "null") return null
        return runCatching { LocalDate.parse(text) }.getOrNull()
    }
}
