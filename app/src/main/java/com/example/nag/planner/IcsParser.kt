package com.example.nag.planner

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Parses an ICS (RFC 5545) calendar feed into [PlannerEvent]s — enough of the spec to
 * read a TimeEdit-style class schedule: unfolded lines, VEVENT blocks with
 * UID/SUMMARY/DTSTART/DTEND/LOCATION, and UTC ("Z"), floating, TZID-qualified, and
 * whole-day (VALUE=DATE) date-times.
 *
 * There is no RRULE expansion. The feed is expected to already hand back one VEVENT
 * per concrete class session, which is how TimeEdit exports schedules — this parser
 * only turns each VEVENT into one occurrence.
 */
object IcsParser {

    private val dateTimeFormat = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
    private val dateFormat = DateTimeFormatter.ofPattern("yyyyMMdd")
    private val escapeSequence = Regex("""\\([\\;,nN])""")

    fun parse(icsText: String): List<PlannerEvent> {
        val events = mutableListOf<PlannerEvent>()
        var depth = 0
        var current: MutableMap<String, IcsProperty>? = null

        for (line in unfold(icsText)) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            when {
                trimmed.equals("BEGIN:VEVENT", ignoreCase = true) -> {
                    if (depth == 0) current = mutableMapOf()
                    depth++
                }
                trimmed.equals("END:VEVENT", ignoreCase = true) -> {
                    if (depth > 0) depth--
                    if (depth == 0) {
                        current?.let { toEvent(it)?.let(events::add) }
                        current = null
                    }
                }
                trimmed.startsWith("BEGIN:", ignoreCase = true) -> if (depth > 0) depth++
                trimmed.startsWith("END:", ignoreCase = true) -> if (depth > 0) depth--
                depth == 1 && current != null -> {
                    val property = parseProperty(trimmed) ?: continue
                    current[property.name.uppercase()] = property
                }
            }
        }

        return events
    }

    /** RFC 5545 line folding: a line starting with a space or tab continues the previous line. */
    private fun unfold(icsText: String): List<String> {
        val rawLines = icsText.split("\r\n", "\n")
        val result = mutableListOf<StringBuilder>()
        for (raw in rawLines) {
            if ((raw.startsWith(" ") || raw.startsWith("\t")) && result.isNotEmpty()) {
                result.last().append(raw.substring(1))
            } else {
                result.add(StringBuilder(raw))
            }
        }
        return result.map { it.toString() }
    }

    private data class IcsProperty(val name: String, val params: Map<String, String>, val value: String)

    private fun parseProperty(line: String): IcsProperty? {
        val colon = line.indexOf(':')
        if (colon < 0) return null
        val head = line.substring(0, colon)
        val value = line.substring(colon + 1)
        val parts = head.split(";")
        val name = parts.first()
        val params = parts.drop(1).mapNotNull {
            val eq = it.indexOf('=')
            if (eq < 0) null else it.substring(0, eq).uppercase() to it.substring(eq + 1)
        }.toMap()
        return IcsProperty(name, params, value)
    }

    private fun toEvent(props: Map<String, IcsProperty>): PlannerEvent? = runCatching {
        val dtStart = props["DTSTART"] ?: return@runCatching null
        val start = parseDateTime(dtStart)
        val end = props["DTEND"]?.let { parseDateTime(it) } ?: start.plusHours(1)
        val title = props["SUMMARY"]?.let { unescapeText(it.value) } ?: "Untitled"
        val location = props["LOCATION"]?.let { unescapeText(it.value) }?.takeIf { it.isNotBlank() }
        val id = props["UID"]?.value?.takeIf { it.isNotBlank() } ?: "$title@$start"
        PlannerEvent(id = id, title = title, start = start, end = end, location = location)
    }.getOrNull()

    private fun parseDateTime(property: IcsProperty): LocalDateTime {
        val value = property.value.trim()
        val isDateOnly = property.params["VALUE"] == "DATE" || !value.contains("T")

        if (isDateOnly) {
            return LocalDate.parse(value, dateFormat).atStartOfDay()
        }

        return when {
            value.endsWith("Z") -> {
                val utc = LocalDateTime.parse(value.dropLast(1), dateTimeFormat)
                utc.atZone(ZoneId.of("UTC")).withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime()
            }
            property.params.containsKey("TZID") -> {
                val zone = runCatching { ZoneId.of(property.params.getValue("TZID")) }
                    .getOrDefault(ZoneId.systemDefault())
                LocalDateTime.parse(value, dateTimeFormat).atZone(zone)
                    .withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime()
            }
            else -> LocalDateTime.parse(value, dateTimeFormat)
        }
    }

    /** Unescapes RFC 5545 TEXT values (SUMMARY, LOCATION) in a single pass. */
    private fun unescapeText(value: String): String = escapeSequence.replace(value) { match ->
        when (match.groupValues[1]) {
            "n", "N" -> "\n"
            else -> match.groupValues[1]
        }
    }
}
