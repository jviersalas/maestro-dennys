package apk.mavier.dguitar

import apk.mavier.dguitar.presentation.learning.LearningDestination
import apk.mavier.dguitar.presentation.learning.LearningModuleCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LearningModuleCatalogTest {

    @Test
    fun songbookComesImmediatelyBeforeTuner() {
        val destinations = LearningModuleCatalog.modules(guitar = true)
            .map { it.destination }

        val songbook = destinations.indexOf(LearningDestination.SONGBOOK)
        val tuner = destinations.indexOf(LearningDestination.TUNER)

        assertTrue(songbook >= 0)
        assertEquals(songbook + 1, tuner)
    }

    @Test
    fun chordsAreDisabledOnBass() {
        val chords = LearningModuleCatalog.modules(guitar = false)
            .first { it.destination == LearningDestination.MAIN_CHORDS }

        assertFalse(chords.enabled)
    }
}
