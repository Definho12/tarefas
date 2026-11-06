package com.guilherme.tarefas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class RecurrenceTest {
    private fun at(s: String) = LocalDateTime.parse(s)

    @Test fun diariaAntesEDepoisDoHorario() {
        val t = Task(repeat = Repeat.DAILY, hour = 8, minute = 0)
        assertEquals(at("2026-11-06T08:00"), Recurrence.next(t, at("2026-11-06T07:59")))
        assertEquals(at("2026-11-07T08:00"), Recurrence.next(t, at("2026-11-06T08:00")))
        assertEquals(at("2026-11-07T08:00"), Recurrence.next(t, at("2026-11-06T22:00")))
    }

    @Test fun semanal() {
        // 06/11/2026 é sexta. Segunda e quarta.
        val t = Task(repeat = Repeat.WEEKLY, hour = 9, minute = 30, weekdays = setOf(1, 3))
        assertEquals(at("2026-11-09T09:30"), Recurrence.next(t, at("2026-11-06T10:00")))
        assertEquals(at("2026-11-11T09:30"), Recurrence.next(t, at("2026-11-09T09:30")))
        val sex = Task(repeat = Repeat.WEEKLY, hour = 9, minute = 0, weekdays = setOf(5))
        assertEquals(at("2026-11-13T09:00"), Recurrence.next(sex, at("2026-11-06T09:00")))
        assertNull(Recurrence.next(t.copy(weekdays = emptySet()), at("2026-11-06T10:00")))
    }

    @Test fun mensalComDia31() {
        val t = Task(repeat = Repeat.MONTHLY, hour = 12, minute = 0, day = 31)
        assertEquals(at("2026-11-30T12:00"), Recurrence.next(t, at("2026-11-06T10:00")))
        assertEquals(at("2026-12-31T12:00"), Recurrence.next(t, at("2026-11-30T12:00")))
        assertEquals(at("2027-02-28T12:00"), Recurrence.next(t, at("2027-01-31T13:00")))
    }

    @Test fun anual() {
        val t = Task(repeat = Repeat.YEARLY, hour = 7, minute = 0, day = 15, month = 3)
        assertEquals(at("2027-03-15T07:00"), Recurrence.next(t, at("2026-11-06T10:00")))
        val bis = Task(repeat = Repeat.YEARLY, hour = 7, minute = 0, day = 29, month = 2)
        assertEquals(at("2027-02-28T07:00"), Recurrence.next(bis, at("2026-11-06T10:00")))
        assertEquals(at("2028-02-29T07:00"), Recurrence.next(bis, at("2027-03-01T10:00")))
    }

    @Test fun umaVez() {
        val t = Task(repeat = Repeat.ONCE, hour = 18, minute = 0, date = LocalDate.parse("2026-11-06"))
        assertEquals(at("2026-11-06T18:00"), Recurrence.next(t, at("2026-11-06T17:00")))
        assertNull(Recurrence.next(t, at("2026-11-06T18:00")))
    }
}
