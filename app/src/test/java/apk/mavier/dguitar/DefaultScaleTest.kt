package apk.mavier.dguitar

import apk.mavier.dguitar.domain.model.Scales
import org.junit.Assert.assertEquals
import org.junit.Test

class DefaultScaleTest {

    @Test
    fun newUsersStartOnAPentatonicScale() {
        assertEquals(Scales.PENTATONIC_MINOR, Scales.default)
    }
}
