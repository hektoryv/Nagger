package com.example.nag.planner

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime

/**
 * Turning [PlannerEvent]s into JSON and back, the same shape as [com.example.nag.data.Json].
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
}
