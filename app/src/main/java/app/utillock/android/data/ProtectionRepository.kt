package app.utillock.android.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.utillock.android.model.BlockSchedule
import app.utillock.android.model.ProtectionState
import app.utillock.android.model.UTILLOCK_PACKAGE_NAME
import app.utillock.android.model.excludingUtilLock
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

private val Context.utilLockDataStore by preferencesDataStore(name = "protection")

class ProtectionRepository(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val stateKey = stringPreferencesKey("state_json_v1")
    private val current = AtomicReference(ProtectionState())
    private val mutableState = MutableStateFlow(ProtectionState())
    val state: StateFlow<ProtectionState> = mutableState.asStateFlow()
    private val writeMutex = Mutex()

    init {
        scope.launch {
            context.utilLockDataStore.data
                .map { preferences -> preferences[stateKey]?.let(::decode) ?: ProtectionState() }
                .collect { loaded ->
                    val sanitized = sanitize(loaded)
                    current.set(sanitized)
                    mutableState.value = sanitized
                }
        }
    }

    fun snapshot(): ProtectionState = sanitize(current.get())

    suspend fun awaitLoaded(): ProtectionState {
        val preferences = context.utilLockDataStore.data.first()
        val loaded = preferences[stateKey]?.let(::decode) ?: ProtectionState()
        val sanitized = sanitize(loaded)
        current.set(sanitized)
        mutableState.value = sanitized
        return sanitized
    }

    fun update(transform: (ProtectionState) -> ProtectionState) {
        scope.launch {
            writeMutex.withLock {
                val next = sanitize(transform(current.get()))
                // Publish immediately so the accessibility service sees a newly
                // started quick block without waiting for DataStore's collector.
                current.set(next)
                mutableState.value = next
                context.utilLockDataStore.edit { preferences ->
                    preferences[stateKey] = encode(next)
                }
            }
        }
    }

    fun setQuickBlock(durationMinutes: Int) = update {
        it.copy(quickBlockUntilEpochMs = System.currentTimeMillis() + durationMinutes * 60_000L)
    }

    fun stopQuickBlock() = update { it.copy(quickBlockUntilEpochMs = 0) }

    fun grantPause(durationMinutes: Int) = update {
        it.copy(pauseUntilEpochMs = System.currentTimeMillis() + durationMinutes * 60_000L)
    }

    fun togglePackage(packageName: String) {
        if (packageName == context.packageName || packageName == UTILLOCK_PACKAGE_NAME) return
        update { state ->
            val next = state.blockedPackages.toMutableSet().apply {
                if (!add(packageName)) remove(packageName)
            }
            state.copy(blockedPackages = next)
        }
    }

    fun addDomain(domain: String) = update { state ->
        val normalized = app.utillock.android.model.DomainMatcher.normalize(domain)
        if (normalized.isBlank()) state else state.copy(blockedDomains = state.blockedDomains + normalized)
    }

    fun removeDomain(domain: String) = update { it.copy(blockedDomains = it.blockedDomains - domain) }
    fun setAdultFilter(enabled: Boolean) = update { it.copy(adultFilterEnabled = enabled) }
    fun setDnsVpn(enabled: Boolean) = update { it.copy(dnsVpnEnabled = enabled) }
    fun setUsageMonitor(enabled: Boolean) = update { it.copy(usageMonitorEnabled = enabled) }
    fun addSchedule(schedule: BlockSchedule) = update {
        it.copy(schedules = it.schedules + schedule.copy(
            packages = schedule.packages.excludingUtilLock(context.packageName),
        ))
    }
    fun removeSchedule(id: String) = update { it.copy(schedules = it.schedules.filterNot { item -> item.id == id }) }
    fun setScheduleEnabled(id: String, enabled: Boolean) = update {
        it.copy(schedules = it.schedules.map { item -> if (item.id == id) item.copy(enabled = enabled) else item })
    }
    fun setPremium(enabled: Boolean) = update { it.copy(premium = enabled) }
    fun recordAiGrant() = update { it.copy(freeAiGrantsUsed = it.freeAiGrantsUsed + 1) }
    fun completeOnboarding() = update { it.copy(onboardingComplete = true) }
    fun reset() = update { ProtectionState() }

    private fun sanitize(state: ProtectionState): ProtectionState = state.copy(
        blockedPackages = state.blockedPackages.excludingUtilLock(context.packageName),
        schedules = state.schedules.map { schedule ->
            schedule.copy(packages = schedule.packages.excludingUtilLock(context.packageName))
        },
    )

    private fun encode(state: ProtectionState): String = JSONObject().apply {
        put("onboardingComplete", state.onboardingComplete)
        put("blockedPackages", JSONArray(state.blockedPackages.toList()))
        put("blockedDomains", JSONArray(state.blockedDomains.toList()))
        put("blockedKeywords", JSONArray(state.blockedKeywords.toList()))
        put("adultFilterEnabled", state.adultFilterEnabled)
        put("quickBlockUntilEpochMs", state.quickBlockUntilEpochMs)
        put("pauseUntilEpochMs", state.pauseUntilEpochMs)
        put("premium", state.premium)
        put("freeAiGrantsUsed", state.freeAiGrantsUsed)
        put("dnsVpnEnabled", state.dnsVpnEnabled)
        put("usageMonitorEnabled", state.usageMonitorEnabled)
        put("schedules", JSONArray().apply {
            state.schedules.forEach { schedule ->
                put(JSONObject().apply {
                    put("id", schedule.id)
                    put("name", schedule.name)
                    put("enabled", schedule.enabled)
                    put("startMinute", schedule.startMinute)
                    put("endMinute", schedule.endMinute)
                    put("days", JSONArray(schedule.days.toList()))
                    put("packages", JSONArray(schedule.packages.toList()))
                    put("domains", JSONArray(schedule.domains.toList()))
                    put("blockAdultContent", schedule.blockAdultContent)
                })
            }
        })
    }.toString()

    private fun decode(value: String): ProtectionState = runCatching {
        val json = JSONObject(value)
        ProtectionState(
            onboardingComplete = json.optBoolean("onboardingComplete"),
            blockedPackages = json.optJSONArray("blockedPackages").toStringSet(),
            blockedDomains = json.optJSONArray("blockedDomains").toStringSet(),
            blockedKeywords = json.optJSONArray("blockedKeywords").toStringSet().ifEmpty {
                setOf("porn", "xxx", "hentai")
            },
            adultFilterEnabled = json.optBoolean("adultFilterEnabled", true),
            quickBlockUntilEpochMs = json.optLong("quickBlockUntilEpochMs"),
            pauseUntilEpochMs = json.optLong("pauseUntilEpochMs"),
            premium = json.optBoolean("premium"),
            freeAiGrantsUsed = json.optInt("freeAiGrantsUsed"),
            dnsVpnEnabled = json.optBoolean("dnsVpnEnabled"),
            usageMonitorEnabled = json.optBoolean("usageMonitorEnabled"),
            schedules = json.optJSONArray("schedules").toSchedules(),
        )
    }.getOrElse { ProtectionState() }

    private fun JSONArray?.toStringSet(): Set<String> = buildSet {
        if (this@toStringSet != null) {
            for (index in 0 until length()) optString(index).takeIf(String::isNotBlank)?.let(::add)
        }
    }

    private fun JSONArray?.toIntSet(): Set<Int> = buildSet {
        if (this@toIntSet != null) for (index in 0 until length()) add(optInt(index))
    }

    private fun JSONArray?.toSchedules(): List<BlockSchedule> = buildList {
        if (this@toSchedules != null) for (index in 0 until length()) {
            val item = optJSONObject(index) ?: continue
            add(
                BlockSchedule(
                    id = item.optString("id"),
                    name = item.optString("name"),
                    enabled = item.optBoolean("enabled", true),
                    startMinute = item.optInt("startMinute"),
                    endMinute = item.optInt("endMinute"),
                    days = item.optJSONArray("days").toIntSet(),
                    packages = item.optJSONArray("packages").toStringSet(),
                    domains = item.optJSONArray("domains").toStringSet(),
                    blockAdultContent = item.optBoolean("blockAdultContent"),
                ),
            )
        }
    }
}
