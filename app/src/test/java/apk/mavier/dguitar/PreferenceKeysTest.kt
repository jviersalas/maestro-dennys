package apk.mavier.dguitar

import apk.mavier.dguitar.data.preferences.PreferenceKeys
import org.junit.Assert.assertEquals
import org.junit.Test

class PreferenceKeysTest {

    @Test
    fun everyPreferenceKeyIsUnique() {
        val keys = PreferenceKeys::class.java.declaredFields
            .filter { it.type == String::class.java }
            .map { it.get(null) as String }

        assertEquals(keys.size, keys.distinct().size)
    }
}
