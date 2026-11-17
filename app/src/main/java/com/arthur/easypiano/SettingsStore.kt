package com.arthur.easypiano
import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
private val Context.store by preferencesDataStore("piano_settings")
class SettingsStore(private val context: Context) {
    private fun <T : Enum<T>> enumValue(index: Int?, values: Array<T>, default: T): T = values.getOrNull(index ?: -1) ?: default
    val settings = context.store.data.catch { if (it is IOException) emit(emptyPreferences()) else throw it }.map { p ->
        val zoom = (p[floatPreferencesKey("zoom")] ?: 14f).takeIf { it.isFinite() }?.coerceIn(7f,47f) ?: 14f
        Settings(
            Piano.clamp((p[floatPreferencesKey("position")] ?: 17f).takeIf { it.isFinite() } ?: 17f, zoom), zoom,
            (p[floatPreferencesKey("volume")] ?: .7f).takeIf { it.isFinite() }?.coerceIn(0f,1f) ?: .7f,
            enumValue(p[intPreferencesKey("labels")], Labels.entries.toTypedArray(), Labels.WHITE),
            enumValue(p[intPreferencesKey("notation")], Notation.entries.toTypedArray(), Notation.LETTERS),
            enumValue(p[intPreferencesKey("accidentals")], Accidentals.entries.toTypedArray(), Accidentals.SHARP),
            p[booleanPreferencesKey("locked")] ?: false,
            enumValue(p[intPreferencesKey("snap")], Snap.entries.toTypedArray(), Snap.NONE),
            p[booleanPreferencesKey("velocity")] ?: true, p[booleanPreferencesKey("pedalToggle")] ?: false
        )
    }
    suspend fun save(s: Settings) { context.store.edit { p ->
        p[floatPreferencesKey("position")] = s.position; p[floatPreferencesKey("zoom")] = s.zoom
        p[floatPreferencesKey("volume")] = s.volume; p[intPreferencesKey("labels")] = s.labels.ordinal
        p[intPreferencesKey("notation")] = s.notation.ordinal; p[intPreferencesKey("accidentals")] = s.accidentals.ordinal
        p[booleanPreferencesKey("locked")] = s.locked; p[intPreferencesKey("snap")] = s.snap.ordinal
        p[booleanPreferencesKey("velocity")] = s.velocity; p[booleanPreferencesKey("pedalToggle")] = s.pedalToggle
    } }
}
