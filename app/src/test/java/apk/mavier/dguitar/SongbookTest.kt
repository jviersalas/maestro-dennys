package apk.mavier.dguitar

import apk.mavier.dguitar.domain.model.Chords
import apk.mavier.dguitar.domain.model.Song
import apk.mavier.dguitar.domain.model.SongBar
import apk.mavier.dguitar.domain.model.SongChord
import apk.mavier.dguitar.domain.model.SongPart
import apk.mavier.dguitar.domain.model.SongPartType
import apk.mavier.dguitar.domain.model.Songbook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SongbookTest {

    private fun song(): Song = Song(
        id = "song-1",
        title = "Prueba",
        artist = "Artista",
        bpm = 90,
        parts = listOf(
            SongPart(
                name = "Verso 1",
                type = SongPartType.VERSO,
                bars = listOf(
                    SongBar(
                        listOf(
                            SongChord(9, Chords.MINOR),
                            SongChord(0, Chords.MAJOR)
                        )
                    )
                )
            ),
            SongPart(
                name = "Coro",
                type = SongPartType.CORO,
                bars = listOf(SongBar(listOf(SongChord(7, Chords.MAJOR))))
            )
        )
    )

    @Test
    fun stepsKeepPartAndChordOrder() {
        val steps = Songbook.steps(song())

        assertEquals(3, steps.size)
        assertEquals(listOf(0, 0, 1), steps.map { it.partIndex })
        assertEquals(listOf(0, 1, 0), steps.map { it.chordIndex })
        assertEquals(listOf("Verso 1", "Verso 1", "Coro"), steps.map { it.partName })
        assertEquals(listOf(9, 0, 7), steps.map { it.chord.rootPc })
        assertEquals(listOf(2.0, 2.0, 4.0), steps.map { it.durationBeats })
    }

    @Test
    fun firstStepOfPartCountsPreviousChords() {
        assertEquals(0, Songbook.firstStepOfPart(song(), 0))
        assertEquals(2, Songbook.firstStepOfPart(song(), 1))
        assertEquals(3, Songbook.firstStepOfPart(song(), 2))
    }

    @Test
    fun totalChordsAndSummaryCountEverything() {
        val song = song()

        assertEquals(3, Songbook.totalChords(song))
        assertEquals("2 partes · 3 acordes", Songbook.partSummary(song))
    }

    @Test
    fun chordLabelAndNotesUseTheRequestedSpelling() {
        val chord = SongChord(10, Chords.MINOR)

        assertEquals("Bbm", chord.label(useFlats = true))
        assertEquals("A#m", chord.label(useFlats = false))
        assertEquals(listOf("Bb", "Db", "F"), chord.notes(useFlats = true))
    }

    @Test
    fun slashChordKeepsTheBassInTheLabel() {
        val chord = SongChord(7, Chords.MAJOR, bassPc = 11)

        assertEquals("G/B", chord.label(useFlats = true))
        assertEquals("B", chord.bassLabel(useFlats = true))
    }

    @Test
    fun augmentedSharpFiveUsesTheRequestedSymbol() {
        val chord = SongChord(2, Chords.AUGMENTED_SHARP_5)

        assertEquals("D(#5)", chord.label(useFlats = true))
        assertEquals(listOf("D", "F#", "A#"), chord.notes(useFlats = true))
    }

    @Test
    fun newSongStartsWithOneEmptyVerse() {
        val song = Songbook.newSong("  Mi canción  ", "  Autor  ", 300)

        assertEquals("Mi canción", song.title)
        assertEquals("Autor", song.artist)
        assertEquals(Songbook.MAX_BPM, song.bpm)
        assertEquals(1, song.parts.size)
        assertEquals(SongPartType.VERSO, song.parts.first().type)
        assertTrue(song.parts.first().chords.isEmpty())
    }

    @Test
    fun fourChordsFillOneBarWithOneBeatEach() {
        val song = Song(
            id = "bar",
            title = "Compás",
            parts = listOf(
                SongPart(
                    name = "Coro",
                    type = SongPartType.CORO,
                    bars = listOf(
                        SongBar(
                            listOf(
                                SongChord(2, Chords.MAJOR),
                                SongChord(2, Chords.AUGMENTED_SHARP_5),
                                SongChord(7, Chords.MAJOR),
                                SongChord(9, Chords.MAJOR)
                            )
                        )
                    )
                )
            )
        )

        val steps = Songbook.steps(song)

        assertEquals(4, steps.size)
        assertEquals(listOf(1.0, 1.0, 1.0, 1.0), steps.map { it.durationBeats })
    }

    @Test
    fun oneChordBarLastsFourBeatsAndTwoChordBarSplitsTheBeats() {
        val song = Song(
            id = "bars",
            title = "Compases",
            parts = listOf(
                SongPart(
                    name = "Coro",
                    type = SongPartType.CORO,
                    bars = listOf(
                        SongBar(listOf(SongChord(5, Chords.MAJOR))),
                        SongBar(
                            listOf(
                                SongChord(0, Chords.MAJOR),
                                SongChord(7, Chords.MAJOR)
                            )
                        )
                    )
                )
            )
        )

        val steps = Songbook.steps(song)

        assertEquals(3, steps.size)
        assertEquals(listOf(4.0, 2.0, 2.0), steps.map { it.durationBeats })
        assertEquals(listOf(0, 1, 1), steps.map { it.barIndex })
        assertEquals(listOf(0, 0, 1), steps.map { it.indexInBar })
    }

    @Test
    fun repeatedPartExpandsPlaybackSteps() {
        val song = Song(
            id = "repeat",
            title = "Coro repetido",
            parts = listOf(
                SongPart(
                    name = "Coro",
                    type = SongPartType.CORO,
                    bars = listOf(
                        SongBar(
                            listOf(
                                SongChord(7, Chords.MAJOR),
                                SongChord(9, Chords.MAJOR)
                            )
                        )
                    ),
                    repeatCount = 3
                )
            )
        )

        val steps = Songbook.steps(song)

        assertEquals(6, steps.size)
        assertEquals(listOf(0, 0, 1, 1, 2, 2), steps.map { it.repeatIndex })
        assertEquals(6, Songbook.firstStepOfPart(song, 1))
    }

    @Test
    fun exampleSongIsIncludedAndUsesRepeat() {
        val example = Songbook.exampleSong()

        assertEquals("Atado a un Sentimiento", example.title)
        assertEquals("Miguel Mateos", example.artist)
        assertTrue(example.parts.size >= 10)
        assertTrue(example.parts.any { it.normalizedRepeatCount > 1 })
        assertTrue(example.parts.any { part -> part.chords.any { it.bassPc != null } })
    }

    @Test
    fun heyJudeExampleIsIncludedWithItsPartsAndChords() {
        val song = Songbook.heyJudeSong()

        assertEquals("Hey Jude (Remastered 2015)", song.title)
        assertEquals("The Beatles", song.artist)
        assertEquals(11, song.parts.size)
        assertEquals(131, song.parts.sumOf { it.bars.size })
        assertEquals(3, song.parts.count { it.name.startsWith("Na na na") })
        assertTrue(song.parts.any { part -> part.bars.any { it.chords.size == 2 } })
        assertTrue(song.parts.any { part -> part.chords.any { it.bassPc != null } })
    }

    @Test
    fun yesterdayExampleIsIncludedWithItsBarsAndSections() {
        val song = Songbook.yesterdaySong()

        assertEquals("Yesterday (Remastered 2015)", song.title)
        assertEquals("The Beatles", song.artist)
        assertEquals(7, song.parts.size)
        assertEquals(51, song.parts.sumOf { it.bars.size })
        assertTrue(song.parts.any { part -> part.bars.any { it.chords.size == 4 } })
        assertTrue(song.parts.any { part -> part.chords.any { it.bassPc != null } })
    }
}
