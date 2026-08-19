package app.utillock.android.model

import java.time.DayOfWeek
import java.time.LocalDateTime
import java.util.UUID

data class InstalledApp(
    val packageName: String,
    val label: String,
)

data class BlockSchedule(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val enabled: Boolean = true,
    val startMinute: Int,
    val endMinute: Int,
    val days: Set<Int> = (1..7).toSet(),
    val packages: Set<String> = emptySet(),
    val domains: Set<String> = emptySet(),
    val blockAdultContent: Boolean = false,
)

data class ProtectionState(
    val onboardingComplete: Boolean = false,
    val blockedPackages: Set<String> = emptySet(),
    val blockedDomains: Set<String> = emptySet(),
    val blockedKeywords: Set<String> = setOf("porn", "xxx", "hentai"),
    val adultFilterEnabled: Boolean = true,
    val quickBlockUntilEpochMs: Long = 0,
    val pauseUntilEpochMs: Long = 0,
    val schedules: List<BlockSchedule> = emptyList(),
    val premium: Boolean = false,
    val freeAiGrantsUsed: Int = 0,
    val dnsVpnEnabled: Boolean = false,
    val usageMonitorEnabled: Boolean = false,
) {
    fun isPaused(nowEpochMs: Long = System.currentTimeMillis()) = pauseUntilEpochMs > nowEpochMs
    fun isQuickBlockActive(nowEpochMs: Long = System.currentTimeMillis()) = quickBlockUntilEpochMs > nowEpochMs
}

data class ActiveProtection(
    val active: Boolean,
    val packages: Set<String>,
    val domains: Set<String>,
    val keywords: Set<String>,
    val adultFilter: Boolean,
    val reason: String,
)

data class LogicChallenge(
    val id: String,
    val title: String,
    val pseudocode: String,
    val expectedAnswer: String? = null,
    val expiresAtEpochMs: Long,
    val remote: Boolean,
    val attemptsRemaining: Int = 3,
    val localAvailableAtEpochMs: Long = 0,
)

data class ChallengeVerdict(
    val accepted: Boolean,
    val feedback: String,
    val attemptsRemaining: Int,
)

object ScheduleEvaluator {
    fun isActive(schedule: BlockSchedule, at: LocalDateTime): Boolean {
        if (!schedule.enabled) return false
        val minute = at.hour * 60 + at.minute
        val today = at.dayOfWeek.value
        return if (schedule.startMinute == schedule.endMinute) {
            today in schedule.days
        } else if (schedule.startMinute < schedule.endMinute) {
            today in schedule.days && minute in schedule.startMinute until schedule.endMinute
        } else {
            val yesterday = if (today == DayOfWeek.MONDAY.value) 7 else today - 1
            (today in schedule.days && minute >= schedule.startMinute) ||
                (yesterday in schedule.days && minute < schedule.endMinute)
        }
    }

    fun activeProtection(
        state: ProtectionState,
        now: LocalDateTime = LocalDateTime.now(),
        nowEpochMs: Long = System.currentTimeMillis(),
    ): ActiveProtection {
        if (state.isPaused(nowEpochMs)) {
            return ActiveProtection(false, emptySet(), emptySet(), emptySet(), false, "paused")
        }

        val activeSchedules = state.schedules.filter { isActive(it, now) }
        val quick = state.isQuickBlockActive(nowEpochMs)
        if (!quick && activeSchedules.isEmpty()) {
            return ActiveProtection(false, emptySet(), emptySet(), emptySet(), false, "inactive")
        }

        val packages = (if (quick) state.blockedPackages else activeSchedules.flatMap { it.packages }.toSet())
            .excludingUtilLock()
        val domains = buildSet {
            if (quick) addAll(state.blockedDomains)
            activeSchedules.forEach { addAll(it.domains) }
        }
        val adult = (quick && state.adultFilterEnabled) || activeSchedules.any { it.blockAdultContent }
        return ActiveProtection(
            active = true,
            packages = packages,
            domains = domains,
            keywords = state.blockedKeywords,
            adultFilter = adult,
            reason = if (quick) "quick" else "schedule",
        )
    }
}

object DomainMatcher {
    fun normalize(raw: String): String = raw
        .trim()
        .lowercase()
        .removePrefix("http://")
        .removePrefix("https://")
        .substringBefore('/')
        .substringBefore(':')
        .trim('.')

    fun matches(hostOrUrl: String, blockedDomains: Set<String>, keywords: Set<String>): Boolean {
        val host = normalize(hostOrUrl)
        if (host.isBlank()) return false
        return blockedDomains.map(::normalize).any { blocked ->
            blocked.isNotBlank() && (host == blocked || host.endsWith(".$blocked"))
        } || keywords.any { keyword -> keyword.isNotBlank() && host.contains(keyword.lowercase()) }
    }
}
