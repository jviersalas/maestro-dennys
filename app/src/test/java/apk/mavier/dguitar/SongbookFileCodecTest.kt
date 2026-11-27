package apk.mavier.dguitar

import apk.mavier.dguitar.data.format.SongbookFileCodec
import apk.mavier.dguitar.domain.model.Chords
import apk.mavier.dguitar.domain.model.Song
import apk.mavier.dguitar.domain.model.SongBar
import apk.mavier.dguitar.domain.model.SongChord
import apk.mavier.dguitar.domain.model.SongPart
import apk.mavier.dguitar.domain.model.SongPartType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SongbookFileCodecTest {

    @Test
    fun roundTripPreservesSongStructure() {
        val song = Song(
            id = "original",
            title = "Canción de prueba",
            artist = "Artista",
            bpm = 96,
            parts = listOf(
                SongPart(
                    name = "Coro",
                    type = SongPartType.CORO,
                    lyrics = "Una letra",
                    repeatCount = 3,
                    bars = listOf(
                        SongBar(listOf(SongChord(5, Chords.MAJOR))),
                        SongBar(
                            listOf(
                                SongChord(0, Chords.MAJOR),
                                SongChord(9, Chords.MINOR, bassPc = 4)
                            )
                        )
                    )
                )
            )
        )

        val decoded = SongbookFileCodec.decode(
            SongbookFileCodec.encode(listOf(song))
        )

        assertEquals(1, decoded.size)
        val result = decoded.first()
        assertEquals(song.title, result.title)
        assertEquals(song.artist, result.artist)
        assertEquals(song.bpm, result.bpm)
        assertEquals(1, result.parts.size)
        val part = result.parts.first()
        assertEquals("Coro", part.name)
        assertEquals(SongPartType.CORO, part.type)
        assertEquals("Una letra", part.lyrics)
        assertEquals(3, part.repeatCount)
        assertEquals(2, part.bars.size)
        assertEquals(listOf(5), part.bars[0].chords.map { it.rootPc })
        assertEquals(listOf(0, 9), part.bars[1].chords.map { it.rootPc })
        assertEquals(4, part.bars[1].chords[1].bassPc)
    }

    @Test
    fun rejectsFilesFromAnotherFormat() {
        assertThrows(IllegalArgumentException::class.java) {
            SongbookFileCodec.decode("""{"format":"Otro","version":1,"songs":[]}""")
        }
    }
}
