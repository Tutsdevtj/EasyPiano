package com.arthur.easypiano

import kotlin.math.pow
import kotlin.math.round

enum class Labels { NONE, CS, WHITE, ALL }
enum class Notation { LETTERS, SOLFEGE }
enum class Accidentals { SHARP, FLAT, BOTH }
enum class Snap { NONE, C, F }
data class PianoNote(val midi: Int) {
    val pitchClass = midi % 12
    val octave = midi / 12 - 1
    val black = pitchClass in setOf(1, 3, 6, 8, 10)
    val frequency = 440.0 * 2.0.pow((midi - 69) / 12.0)
    val name get() = label(Notation.LETTERS, Accidentals.SHARP)
    fun label(notation: Notation, accidentals: Accidentals): String {
        val sharp = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
        val flat = arrayOf("C", "Db", "D", "Eb", "E", "F", "Gb", "G", "Ab", "A", "Bb", "B")
        val doSharp = arrayOf("Dó", "Dó#", "Ré", "Ré#", "Mi", "Fá", "Fá#", "Sol", "Sol#", "Lá", "Lá#", "Si")
        val doFlat = arrayOf("Dó", "Ré♭", "Ré", "Mi♭", "Mi", "Fá", "Sol♭", "Sol", "Lá♭", "Lá", "Si♭", "Si")
        val s = (if (notation == Notation.LETTERS) sharp else doSharp)[pitchClass]
        val f = (if (notation == Notation.LETTERS) flat else doFlat)[pitchClass]
        return when { !black || accidentals == Accidentals.SHARP -> "$s$octave"; accidentals == Accidentals.FLAT -> "$f$octave"; else -> "$s$octave/$f$octave" }
    }
    fun visible(labels: Labels) = when (labels) { Labels.NONE -> false; Labels.CS -> pitchClass == 0; Labels.WHITE -> !black; Labels.ALL -> true }
}
object Piano {
    val notes = (29..108).map(::PianoNote)
    val whites = notes.filterNot { it.black }
    // Black key centers in units of white key width. Offset follows the two/three-key groups.
    val keys: List<Key> = notes.map { note ->
        val previousWhites = whites.count { it.midi < note.midi }
        if (!note.black) Key(note, previousWhites.toFloat(), 1f)
        else {
            val shift = when (note.pitchClass) { 1, 6 -> -.08f; 3, 10 -> .08f; else -> 0f }
            Key(note, previousWhites + shift - .29f, .58f)
        }
    }
    fun hit(x: Float, y: Float): PianoNote? {
        if (x < 0 || x >= 47 || y < 0 || y > 1) return null
        return keys.lastOrNull { it.note.black && y <= .64f && x >= it.left && x < it.left + it.width }?.note
            ?: keys.firstOrNull { !it.note.black && x >= it.left && x < it.left + it.width }?.note
    }
    fun clamp(position: Float, zoom: Float) = position.coerceIn(0f, (47f - zoom.coerceIn(7f, 47f)).coerceAtLeast(0f))
    fun snap(position: Float, zoom: Float, mode: Snap): Float {
        if (mode == Snap.NONE) return clamp(position, zoom)
        val pc = if (mode == Snap.C) 0 else 5
        return clamp(whites.indices.filter { whites[it].pitchClass == pc }.minByOrNull { kotlin.math.abs(it - position) }?.toFloat() ?: round(position), zoom)
    }
}
data class Key(val note: PianoNote, val left: Float, val width: Float)
data class Settings(
    val position: Float = 17f, val zoom: Float = 14f, val volume: Float = .7f,
    val labels: Labels = Labels.WHITE, val notation: Notation = Notation.LETTERS,
    val accidentals: Accidentals = Accidentals.SHARP, val locked: Boolean = false,
    val snap: Snap = Snap.NONE, val velocity: Boolean = true, val pedalToggle: Boolean = false
)
