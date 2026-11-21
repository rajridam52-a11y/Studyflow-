package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.ui.theme.AccentPalette
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.studyFlowDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "studyflow_preferences"
)

enum class DashboardCardType(val defaultTitle: String) {
    TODAY_TIMETABLE("Today's Timetable"),
    PENDING_HOMEWORK("Pending Homework"),
    UPCOMING_EXAMS("Upcoming Exams"),
    TODAY_REMINDERS("Today's Reminders"),
    DAILY_GOALS("Daily Goals"),
    STUDY_STATS("Study-Time & Progress"),
    REVISION_PROGRESS("Revision Progress")
}

data class AppPreferences(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val accentPalette: AccentPalette = AccentPalette.ROYAL_BLUE,
    val offlineMode: Boolean = false,
    val lastSyncMillis: Long = 0L,
    val syncStatusMessage: String = "Ready",
    val pinLockEnabled: Boolean = false,
    val pinHash: String = "",
    val pinSalt: String = "",
    val biometricEnabled: Boolean = false,
    val dashboardOrder: List<DashboardCardType> = DashboardCardType.entries,
    val hiddenDashboardCards: Set<DashboardCardType> = emptySet(),
    val aiRequestsToday: Int = 0,
    val aiRequestDayKey: String = ""
)

class PreferencesManager(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val ACCENT_PALETTE = stringPreferencesKey("accent_palette")
        val OFFLINE_MODE = booleanPreferencesKey("offline_mode")
        val LAST_SYNC_MILLIS = longPreferencesKey("last_sync_millis")
        val SYNC_STATUS = stringPreferencesKey("sync_status")
        val PIN_LOCK_ENABLED = booleanPreferencesKey("pin_lock_enabled")
        val PIN_HASH = stringPreferencesKey("pin_hash")
        val PIN_SALT = stringPreferencesKey("pin_salt")
        val BIOMETRIC_ENABLED = booleanPreferencesKey("biometric_enabled")
        val DASHBOARD_ORDER = stringPreferencesKey("dashboard_order")
        val HIDDEN_DASHBOARD_CARDS = stringPreferencesKey("hidden_dashboard_cards")
        val AI_REQUESTS_TODAY = stringPreferencesKey("ai_requests_today")
    }

    val preferencesFlow: Flow<AppPreferences> = context.studyFlowDataStore.data.map { prefs ->
        val orderRaw = prefs[Keys.DASHBOARD_ORDER]
        val orderList = if (orderRaw.isNullOrBlank()) {
            DashboardCardType.entries
        } else {
            val parsed = orderRaw.split(",").mapNotNull { name ->
                DashboardCardType.entries.firstOrNull { it.name == name }
            }
            val missing = DashboardCardType.entries.filter { it !in parsed }
            parsed + missing
        }
        val hiddenRaw = prefs[Keys.HIDDEN_DASHBOARD_CARDS].orEmpty()
        val hiddenSet = hiddenRaw.split(",")
            .mapNotNull { name -> DashboardCardType.entries.firstOrNull { it.name == name } }
            .toSet()

        val aiPair = prefs[Keys.AI_REQUESTS_TODAY].orEmpty().split("|")
        val aiDay = aiPair.getOrNull(0).orEmpty()
        val aiCount = aiPair.getOrNull(1)?.toIntOrNull() ?: 0

        AppPreferences(
            themeMode = ThemeMode.fromString(prefs[Keys.THEME_MODE]),
            accentPalette = AccentPalette.fromString(prefs[Keys.ACCENT_PALETTE]),
            offlineMode = prefs[Keys.OFFLINE_MODE] ?: false,
            lastSyncMillis = prefs[Keys.LAST_SYNC_MILLIS] ?: 0L,
            syncStatusMessage = prefs[Keys.SYNC_STATUS] ?: "Ready",
            pinLockEnabled = prefs[Keys.PIN_LOCK_ENABLED] ?: false,
            pinHash = prefs[Keys.PIN_HASH].orEmpty(),
            pinSalt = prefs[Keys.PIN_SALT].orEmpty(),
            biometricEnabled = prefs[Keys.BIOMETRIC_ENABLED] ?: false,
            dashboardOrder = orderList,
            hiddenDashboardCards = hiddenSet,
            aiRequestsToday = aiCount,
            aiRequestDayKey = aiDay
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.studyFlowDataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setAccentPalette(palette: AccentPalette) {
        context.studyFlowDataStore.edit { it[Keys.ACCENT_PALETTE] = palette.name }
    }

    suspend fun setOfflineMode(offline: Boolean) {
        context.studyFlowDataStore.edit {
            it[Keys.OFFLINE_MODE] = offline
            it[Keys.SYNC_STATUS] = if (offline) "Offline mode enabled" else "Online"
        }
    }

    suspend fun updateSyncStatus(timestampMillis: Long, message: String) {
        context.studyFlowDataStore.edit {
            if (timestampMillis > 0L) it[Keys.LAST_SYNC_MILLIS] = timestampMillis
            it[Keys.SYNC_STATUS] = message
        }
    }

    suspend fun setPinLock(enabled: Boolean, hash: String, salt: String) {
        context.studyFlowDataStore.edit {
            it[Keys.PIN_LOCK_ENABLED] = enabled
            it[Keys.PIN_HASH] = hash
            it[Keys.PIN_SALT] = salt
        }
    }

    suspend fun setBiometricEnabled(enabled: Boolean) {
        context.studyFlowDataStore.edit { it[Keys.BIOMETRIC_ENABLED] = enabled }
    }

    suspend fun saveDashboardLayout(
        order: List<DashboardCardType>,
        hidden: Set<DashboardCardType>
    ) {
        context.studyFlowDataStore.edit {
            it[Keys.DASHBOARD_ORDER] = order.joinToString(",") { c -> c.name }
            it[Keys.HIDDEN_DASHBOARD_CARDS] = hidden.joinToString(",") { c -> c.name }
        }
    }

    suspend fun incrementAiUsage(dayKey: String) {
        context.studyFlowDataStore.edit { prefs ->
            val current = prefs[Keys.AI_REQUESTS_TODAY].orEmpty().split("|")
            val prevDay = current.getOrNull(0).orEmpty()
            val prevCount = current.getOrNull(1)?.toIntOrNull() ?: 0
            val nextCount = if (prevDay == dayKey) prevCount + 1 else 1
            prefs[Keys.AI_REQUESTS_TODAY] = "$dayKey|$nextCount"
        }
    }

    suspend fun clearSecuritySettings() {
        context.studyFlowDataStore.edit {
            it[Keys.PIN_LOCK_ENABLED] = false
            it[Keys.PIN_HASH] = ""
            it[Keys.PIN_SALT] = ""
            it[Keys.BIOMETRIC_ENABLED] = false
        }
    }
}
