package apk.mavier.dguitar

import apk.mavier.dguitar.presentation.theme.ChordColorProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ChordColorTest {

    @Test
    fun aIsBlue() {
        assertEquals(0xFF42A5F5.toInt(), ChordColorProvider.baseColor(9))
    }

    @Test
    fun dIsGreen() {
        assertEquals(0xFF43A047.toInt(), ChordColorProvider.baseColor(2))
    }

    @Test
    fun rootKeepsBaseColor() {
        val base = ChordColorProvider.baseColor(9)
        assertEquals(base, ChordColorProvider.shadeForRole(base, 0))
    }

    @Test
    fun thirdAndFifthGetLighterShades() {
        val base = ChordColorProvider.baseColor(2)
        assertNotEquals(base, ChordColorProvider.shadeForRole(base, 4))
        assertNotEquals(base, ChordColorProvider.shadeForRole(base, 7))
    }
}
