package com.example.nag.planner

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Turning [PlannerEvent]s and [PlannerTask]s into JSON and back, the same shape as
 * [com.example.nag.data.Json].
 *
 * No unit tests here, matching that file: this project's unit test setup has no
 * testOptions/org.json override, and Android's android.jar stubs org.json's real
 * implementation on the plain-JVM test classpath, so a test exercising these methods
 * would fail for a reason unrelated to correctness. Verified only by compiling (CI) and
 * on-device, same caveat as the rest of this app's Android-facing storage code.
 */
object PlannerJson {

    fun eventToJson(event: PlannerEvent): JSONObject = JSONObject().apply {
        put("id", event.id)
        put("title", event.title)
        put("start", event.start.toString())
        put("end", event.end.toString())
        put("location", event.location ?: JSONObject.NULL)
    }

    fun eventFromJson(o: JSONObject): PlannerEvent? = runCatching {
        PlannerEvent(
            id = o.getString("id"),
            title = o.getString("title"),
            start = LocalDateTime.parse(o.getString("start")),
            end = LocalDateTime.parse(o.getString("end")),
            location = if (o.isNull("location")) null else o.optString("location").ifBlank { null }
        )
    }.getOrNull()

    fun eventsToString(events: List<PlannerEvent>): String =
        JSONArray().apply { events.forEach { put(eventToJson(it)) } }.toString()

    fun eventsFromString(raw: String): List<PlannerEvent> = runCatching {
        val array = JSONArray(raw)
        (0 until array.length()).mapNotNull { eventFromJson(array.getJSONObject(it)) }
    }.getOrDefault(emptyList())

    fun taskToJson(task: PlannerTask): JSONObject = JSONObject().apply {
        put("id", task.id)
        put("title", task.title)
        put("durationMinutes", task.durationMinutes)
        put("deadline", task.deadline?.toString() ?: JSONObject.NULL)
        put("recurrence", recurrenceToJson(task.recurrence))
    }

    fun taskFromJson(o: JSONObject): PlannerTask? = runCatching {
        PlannerTask(
            id = o.getString("id"),
            title = o.getString("title"),
            durationMinutes = o.getInt("durationMinutes"),
            deadline = if (o.isNull("deadline")) null else o.optString("deadline").ifBlank { null }?.let(LocalDate::parse),
            recurrence = recurrenceFromJson(o.optJSONObject("recurrence"))
        )
    }.getOrNull()

    fun tasksToString(tasks: List<PlannerTask>): String =
        JSONArray().apply { tasks.forEach { put(taskToJson(it)) } }.toString()

    fun tasksFromString(raw: String): List<PlannerTask> = runCatching {
        val array = JSONArray(raw)
        (0 until array.length()).mapNotNull { taskFromJson(array.getJSONObject(it)) }
    }.getOrDefault(emptyList())

    private fun recurrenceToJson(recurrence: TaskRecurrence): JSONObject = JSONObject().apply {
        when (recurrence) {
            TaskRecurrence.None -> put("kind", "NONE")
            TaskRecurrence.Daily -> put("kind", "DAILY")
            is TaskRecurrence.TimesPerWeek -> {
                put("kind", "TIMES_PER_WEEK")
                put("count", recurrence.count)
            }
        }
    }

    private fun recurrenceFromJson(o: JSONObject?): TaskRecurrence {
        if (o == null) return TaskRecurrence.None
        return when (o.optString("kind")) {
            "DAILY" -> TaskRecurrence.Daily
            "TIMES_PER_WEEK" -> TaskRecurrence.TimesPerWeek(o.optInt("count", 1).coerceIn(1, 7))
            else -> TaskRecurrence.None
        }
    }
}
