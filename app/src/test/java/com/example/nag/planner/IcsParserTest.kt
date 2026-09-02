package com.example.nag.planner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class IcsParserTest {

    @Test
    fun `parses a floating date-time event`() {
        val ics = """
            BEGIN:VCALENDAR
            BEGIN:VEVENT
            UID:class-1@kth
            SUMMARY:Databases
            DTSTART:20260901T083000
            DTEND:20260901T101500
            LOCATION:Room 4
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()

        val events = IcsParser.parse(ics)

        assertEquals(1, events.size)
        val event = events.single()
        assertEquals("class-1@kth", event.id)
        assertEquals("Databases", event.title)
        assertEquals("Room 4", event.location)
        assertEquals(LocalDateTime.of(2026, 9, 1, 8, 30), event.start)
        assertEquals(LocalDateTime.of(2026, 9, 1, 10, 15), event.end)
    }

    @Test
    fun `unfolds a summary split mid-word across a continuation line`() {
        // The fold lands inside "Distributed" itself: physical line 2's leading space
        // is purely the RFC 5545 fold marker and must be dropped, not kept as content,
        // or the word would come back as "Distrib uted" instead of "Distributed".
        val ics = "BEGIN:VCALENDAR\r\n" +
            "BEGIN:VEVENT\r\n" +
            "UID:class-2@kth\r\n" +
            "SUMMARY:Advanced Databases and Distrib\r\n" +
            " uted Systems\r\n" +
            "DTSTART:20260902T100000\r\n" +
            "DTEND:20260902T110000\r\n" +
            "END:VEVENT\r\n" +
            "END:VCALENDAR\r\n"

        val events = IcsParser.parse(ics)

        assertEquals("Advanced Databases and Distributed Systems", events.single().title)
    }

    @Test
    fun `converts a UTC date-time to the system default zone`() {
        val ics = """
            BEGIN:VCALENDAR
            BEGIN:VEVENT
            UID:class-3@kth
            SUMMARY:Exam
            DTSTART:20260901T083000Z
            DTEND:20260901T103000Z
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()

        val expectedStart = LocalDateTime.of(2026, 9, 1, 8, 30)
            .atZone(ZoneId.of("UTC"))
            .withZoneSameInstant(ZoneId.systemDefault())
            .toLocalDateTime()

        val event = IcsParser.parse(ics).single()

        assertEquals(expectedStart, event.start)
    }

    @Test
    fun `converts a TZID-qualified date-time to the system default zone`() {
        val ics = """
            BEGIN:VCALENDAR
            BEGIN:VEVENT
            UID:class-4@kth
            SUMMARY:Lecture
            DTSTART;TZID=Europe/Stockholm:20260901T083000
            DTEND;TZID=Europe/Stockholm:20260901T101500
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()

        val expectedStart = LocalDateTime.of(2026, 9, 1, 8, 30)
            .atZone(ZoneId.of("Europe/Stockholm"))
            .withZoneSameInstant(ZoneId.systemDefault())
            .toLocalDateTime()

        val event = IcsParser.parse(ics).single()

        assertEquals(expectedStart, event.start)
    }

    @Test
    fun `treats a VALUE=DATE event as starting at midnight`() {
        val ics = """
            BEGIN:VCALENDAR
            BEGIN:VEVENT
            UID:holiday-1@kth
            SUMMARY:No classes
            DTSTART;VALUE=DATE:20260906
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()

        val event = IcsParser.parse(ics).single()

        assertEquals(LocalDateTime.of(LocalDate.of(2026, 9, 6), LocalTime.MIDNIGHT), event.start)
    }

    @Test
    fun `unescapes commas semicolons and newlines in text fields`() {
        val ics = """
            BEGIN:VCALENDAR
            BEGIN:VEVENT
            UID:class-5@kth
            SUMMARY:Databases\, Part 1\; intro
            DTSTART:20260901T083000
            DTEND:20260901T093000
            LOCATION:Building A\nRoom 12
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()

        val event = IcsParser.parse(ics).single()

        assertEquals("Databases, Part 1; intro", event.title)
        assertEquals("Building A\nRoom 12", event.location)
    }

    @Test
    fun `defaults to a one hour duration when DTEND is missing`() {
        val ics = """
            BEGIN:VCALENDAR
            BEGIN:VEVENT
            UID:class-6@kth
            SUMMARY:Seminar
            DTSTART:20260901T083000
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()

        val event = IcsParser.parse(ics).single()

        assertEquals(LocalDateTime.of(2026, 9, 1, 9, 30), event.end)
    }

    @Test
    fun `skips an event with no DTSTART instead of throwing`() {
        val ics = """
            BEGIN:VCALENDAR
            BEGIN:VEVENT
            UID:broken@kth
            SUMMARY:Missing start time
            END:VEVENT
            BEGIN:VEVENT
            UID:class-7@kth
            SUMMARY:Fine
            DTSTART:20260901T083000
            DTEND:20260901T093000
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()

        val events = IcsParser.parse(ics)

        assertEquals(1, events.size)
        assertEquals("class-7@kth", events.single().id)
    }

    @Test
    fun `skips an event with an unparseable date instead of throwing`() {
        val ics = """
            BEGIN:VCALENDAR
            BEGIN:VEVENT
            UID:broken-2@kth
            SUMMARY:Garbage date
            DTSTART:not-a-date
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()

        val events = IcsParser.parse(ics)

        assertTrue(events.isEmpty())
    }

    @Test
    fun `ignores a SUMMARY belonging to a nested VALARM block`() {
        // If depth tracking were broken, this nested SUMMARY — encountered after the
        // event's own — would overwrite it in the property map.
        val ics = """
            BEGIN:VCALENDAR
            BEGIN:VEVENT
            UID:class-8@kth
            SUMMARY:Lecture
            DTSTART:20260901T083000
            DTEND:20260901T093000
            BEGIN:VALARM
            SUMMARY:Reminder
            END:VALARM
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()

        val event = IcsParser.parse(ics).single()

        assertEquals("Lecture", event.title)
    }

    @Test
    fun `parses multiple events in one feed`() {
        val ics = """
            BEGIN:VCALENDAR
            BEGIN:VEVENT
            UID:class-9@kth
            SUMMARY:Morning class
            DTSTART:20260901T083000
            DTEND:20260901T093000
            END:VEVENT
            BEGIN:VEVENT
            UID:class-10@kth
            SUMMARY:Afternoon class
            DTSTART:20260901T130000
            DTEND:20260901T140000
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()

        val events = IcsParser.parse(ics)

        assertEquals(2, events.size)
        assertNull(events[0].location)
    }
}
