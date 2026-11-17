package com.arthur.easypiano
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.pow
class PianoTest {
    @Test fun exactRangeAndOrder() {
        assertEquals((29..108).toList(), Piano.notes.map { it.midi })
        assertEquals(80, Piano.notes.size)
        assertEquals(47, Piano.whites.size)
        assertEquals(33, Piano.notes.count { it.black })
        assertEquals("F1", Piano.notes.first().name)
        assertEquals("C8", Piano.notes.last().name)
    }
    @Test fun equalTemperamentForEveryNote() {
        Piano.notes.forEach { assertEquals(440.0 * 2.0.pow((it.midi - 69) / 12.0), it.frequency, 1e-9) }
        assertEquals(43.6535289291, Piano.notes.first().frequency, 1e-8)
        assertEquals(4186.00904481, Piano.notes.last().frequency, 1e-7)
        assertEquals(440.0, Piano.notes.single { it.midi == 69 }.frequency, 0.0)
        Piano.notes.zipWithNext().forEach { (a,b) -> assertEquals(2.0.pow(1.0/12), b.frequency/a.frequency, 1e-12) }
    }
    @Test fun blackKeyHitWinsAndBottomIsWhite() {
        Piano.keys.forEach { key ->
            assertEquals(key.note, Piano.hit(key.left + key.width/2, if (key.note.black) .2f else .9f))
        }
        assertNull(Piano.hit(-.1f, .2f)); assertNull(Piano.hit(47f, .5f))
    }
    @Test fun labelsIncludeOctaveAndEnharmonics() {
        assertEquals("Dó4", PianoNote(60).label(Notation.SOLFEGE, Accidentals.SHARP))
        assertEquals("C#4/Db4", PianoNote(61).label(Notation.LETTERS, Accidentals.BOTH))
        assertFalse(PianoNote(61).visible(Labels.WHITE))
    }
    @Test fun viewportCannotLeaveKeyboard() {
        assertEquals(0f, Piano.clamp(-10f, 14f))
        assertEquals(33f, Piano.clamp(50f, 14f))
        assertEquals(0f, Piano.clamp(20f, 47f))
        assertEquals(18f, Piano.snap(17.9f, 14f, Snap.C))
    }
}
