package app.utillock.android.model

import java.time.LocalDateTime
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class ScheduleEvaluatorTest {
    @Test
    fun regularScheduleOnlyMatchesSelectedDayAndWindow() {
        val schedule = BlockSchedule(name = "Work", startMinute = 9 * 60, endMinute = 17 * 60, days = setOf(1))
        assertTrue(ScheduleEvaluator.isActive(schedule, LocalDateTime.of(2026, 7, 20, 10, 0)))
        assertFalse(ScheduleEvaluator.isActive(schedule, LocalDateTime.of(2026, 7, 20, 18, 0)))
        assertFalse(ScheduleEvaluator.isActive(schedule, LocalDateTime.of(2026, 7, 21, 10, 0)))
    }

    @Test
    fun overnightScheduleCarriesIntoNextDay() {
        val schedule = BlockSchedule(name = "Night", startMinute = 22 * 60, endMinute = 6 * 60, days = setOf(1))
        assertTrue(ScheduleEvaluator.isActive(schedule, LocalDateTime.of(2026, 7, 20, 23, 0)))
        assertTrue(ScheduleEvaluator.isActive(schedule, LocalDateTime.of(2026, 7, 21, 5, 59)))
        assertFalse(ScheduleEvaluator.isActive(schedule, LocalDateTime.of(2026, 7, 21, 6, 0)))
    }

    @Test
    fun overlappingSchedulesUnionTargets() {
        val first = BlockSchedule(
            name = "A",
            startMinute = 0,
            endMinute = 0,
            days = setOf(1),
            packages = setOf("one.app"),
            domains = setOf("one.example"),
        )
        val second = first.copy(id = "second", packages = setOf("two.app"), domains = setOf("two.example"))
        val active = ScheduleEvaluator.activeProtection(
            ProtectionState(schedules = listOf(first, second)),
            LocalDateTime.of(2026, 7, 20, 10, 0),
            100,
        )
        assertTrue(active.active)
        assertEquals(setOf("one.app", "two.app"), active.packages)
        assertEquals(setOf("one.example", "two.example"), active.domains)
    }

    @Test
    fun quickBlockUsesConfiguredTargetsAndPauseWins() {
        val state = ProtectionState(
            blockedPackages = setOf("com.example.focus"),
            blockedDomains = setOf("example.com"),
            quickBlockUntilEpochMs = 10_000,
            pauseUntilEpochMs = 0,
        )

        val active = ScheduleEvaluator.activeProtection(
            state = state,
            now = LocalDateTime.of(2026, 7, 20, 10, 0),
            nowEpochMs = 1_000,
        )
        assertTrue(active.active)
        assertEquals(setOf("com.example.focus"), active.packages)
        assertEquals(setOf("example.com"), active.domains)

        val paused = ScheduleEvaluator.activeProtection(
            state = state.copy(pauseUntilEpochMs = 20_000),
            now = LocalDateTime.of(2026, 7, 20, 10, 0),
            nowEpochMs = 1_000,
        )
        assertFalse(paused.active)
    }

    @Test
    fun parseMinuteRejectsMalformedAndOutOfRangeInput() {
        assertEquals(9 * 60 + 5, parseMinute(" 09:05 "))
        assertEquals(23 * 60 + 59, parseMinute("23:59"))
        assertEquals(null, parseMinute("24:00"))
        assertEquals(null, parseMinute("09:60"))
        assertEquals(null, parseMinute("09"))
    }
}
