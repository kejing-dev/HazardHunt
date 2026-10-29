package io.embodit.hazardhunt.data

import android.content.Context
import io.embodit.hazardhunt.domain.model.LanguagePref
import io.embodit.hazardhunt.domain.model.LastRound
import io.embodit.hazardhunt.domain.model.Settings

/**
 * Local persistence (design `persistence`): settings, "coach seen", and the last submitted round only. No network,
 * no positions. SharedPreferences, main thread.
 */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("hazard_hunt", Context.MODE_PRIVATE)

    fun load(): Settings {
        val d = Settings()
        return Settings(
            language = prefs.getString("language", null)?.let { s -> LanguagePref.entries.firstOrNull { it.name == s } } ?: d.language,
            soundOn = prefs.getBoolean("soundOn", d.soundOn),
            floorOffsetCm = prefs.getInt("floorOffsetCm", d.floorOffsetCm),
            timerOn = prefs.getBoolean("timerOn", d.timerOn),
            walkMode = prefs.getBoolean("walkMode", d.walkMode),
            coachSeen = prefs.getBoolean("coachSeen", d.coachSeen),
        )
    }

    fun save(s: Settings) {
        prefs.edit()
            .putString("language", s.language.name)
            .putBoolean("soundOn", s.soundOn)
            .putInt("floorOffsetCm", s.floorOffsetCm)
            .putBoolean("timerOn", s.timerOn)
            .putBoolean("walkMode", s.walkMode)
            .putBoolean("coachSeen", s.coachSeen)
            .apply()
    }

    fun loadLast(): LastRound? {
        if (!prefs.getBoolean("last.has", false)) return null
        return LastRound(
            atMs = prefs.getLong("last.at", 0L),
            score = prefs.getInt("last.score", 0),
            max = prefs.getInt("last.max", 24),
            found = prefs.getInt("last.found", 0),
            typed = prefs.getInt("last.typed", 0),
            fixed = prefs.getInt("last.fixed", 0),
            falseAlarms = prefs.getInt("last.false", 0),
            elapsedMs = prefs.getLong("last.elapsed", 0L),
            seed = prefs.getLong("last.seed", 1L),
            walk = prefs.getBoolean("last.walk", false),
        )
    }

    fun saveLast(r: LastRound?) {
        val e = prefs.edit()
        if (r == null) {
            e.putBoolean("last.has", false).apply()
            return
        }
        e.putBoolean("last.has", true)
            .putLong("last.at", r.atMs)
            .putInt("last.score", r.score)
            .putInt("last.max", r.max)
            .putInt("last.found", r.found)
            .putInt("last.typed", r.typed)
            .putInt("last.fixed", r.fixed)
            .putInt("last.false", r.falseAlarms)
            .putLong("last.elapsed", r.elapsedMs)
            .putLong("last.seed", r.seed)
            .putBoolean("last.walk", r.walk)
            .apply()
    }
}
