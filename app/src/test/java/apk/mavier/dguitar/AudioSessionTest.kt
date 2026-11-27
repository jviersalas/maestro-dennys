package apk.mavier.dguitar

import apk.mavier.dguitar.core.audio.AudioOutput
import apk.mavier.dguitar.core.audio.Sf2SoundFont
import apk.mavier.dguitar.domain.model.FretboardConfig
import apk.mavier.dguitar.domain.model.SoundKind
import apk.mavier.dguitar.domain.model.SoundProfile
import apk.mavier.dguitar.domain.repository.SettingsRepository
import apk.mavier.dguitar.domain.repository.SoundFontRepository
import apk.mavier.dguitar.presentation.common.AudioSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.InputStream

class AudioSessionTest {

    private class FakeOutput : AudioOutput {
        override var bassMode = false
        var started = false
        var stopped = false
        var font: Sf2SoundFont? = null
        val plucks = mutableListOf<Pair<Int, Int>>()

        override fun start() {
            started = true
        }

        override fun stop() {
            stopped = true
        }

        override fun pluck(stringIndex: Int, midi: Int) {
            plucks.add(stringIndex to midi)
        }

        override fun silence() = Unit

        override fun loadSoundFont(font: Sf2SoundFont?) {
            this.font = font
        }
    }

    private class FakeSettings(
        private var profile: SoundProfile
    ) : SettingsRepository {
        override fun load(): FretboardConfig = FretboardConfig()
        override fun save(config: FretboardConfig) = Unit
        override fun loadSoundProfile(): SoundProfile = profile
        override fun saveSoundProfile(profile: SoundProfile) {
            this.profile = profile
        }
        override fun hasCompletedOnboarding(): Boolean = true
        override fun setOnboardingCompleted(completed: Boolean) = Unit
    }

    private class FakeSoundFonts : SoundFontRepository {
        val requested = mutableListOf<SoundProfile>()
        val pending = mutableListOf<(Sf2SoundFont?) -> Unit>()

        override fun load(profile: SoundProfile, callback: (Sf2SoundFont?) -> Unit) {
            requested.add(profile)
            pending.add(callback)
        }

        override fun importFont(
            fileName: String,
            source: InputStream,
            callback: (Result<String>) -> Unit
        ) = Unit

        override fun importedNames(): List<String> = emptyList()
        override fun deleteImported(fileName: String): Boolean = false
    }

    private fun session(
        output: FakeOutput,
        settings: FakeSettings,
        fonts: FakeSoundFonts,
        guitar: Boolean = true,
        onFailed: () -> Unit = {}
    ) = AudioSession(
        output = output,
        settings = settings,
        soundFonts = fonts,
        guitar = { guitar },
        isActive = { true },
        onLoadFailed = onFailed
    )

    @Test
    fun startStartsTheEngineAndUsesInstrumentMode() {
        val output = FakeOutput()
        val settings = FakeSettings(SoundProfile.synthetic)
        val session = session(output, settings, FakeSoundFonts(), guitar = false)

        session.start()

        assertTrue(output.started)
        assertTrue(output.bassMode)
    }

    @Test
    fun bundledProfileIsResolvedForTheCurrentInstrument() {
        val output = FakeOutput()
        val settings = FakeSettings(SoundProfile.bundledGuitar)
        val fonts = FakeSoundFonts()
        val session = session(output, settings, fonts, guitar = false)

        session.refresh()

        assertEquals("sf2/bass_yr.sf2", fonts.requested.single().id)
        assertEquals("sf2/bass_yr.sf2", session.profile.id)
    }

    @Test
    fun syntheticProfileClearsAnyLoadedFont() {
        val output = FakeOutput()
        output.font = null
        val session = session(
            output,
            FakeSettings(SoundProfile.synthetic),
            FakeSoundFonts()
        )

        session.refresh()

        assertEquals(null, output.font)
    }

    @Test
    fun staleFontLoadIsIgnoredAfterSelectingAnotherProfile() {
        val output = FakeOutput()
        val settings = FakeSettings(SoundProfile.bundledGuitar)
        val fonts = FakeSoundFonts()
        var failed = false
        val session = session(output, settings, fonts, onFailed = { failed = true })

        session.refresh()
        assertEquals(1, fonts.pending.size)

        session.select(SoundProfile.synthetic)
        fonts.pending.first().invoke(null)

        assertFalse(failed)
        assertEquals(SoundProfile.synthetic, session.profile)
        assertEquals(SoundKind.SYNTHETIC, settings.loadSoundProfile().kind)
    }
}
