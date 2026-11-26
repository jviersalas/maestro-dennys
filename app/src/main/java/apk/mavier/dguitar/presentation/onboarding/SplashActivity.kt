package apk.mavier.dguitar.presentation.onboarding

import apk.mavier.dguitar.R

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import apk.mavier.dguitar.domain.di.DependencyProvider
import apk.mavier.dguitar.domain.repository.SettingsRepository
import apk.mavier.dguitar.presentation.learning.LearningMenuActivity

/**
 * Branded splash screen. Shows the Dennys logo briefly, then opens the
 * onboarding the first time and the fretboard on later launches.
 */
class SplashActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var settingsRepository: SettingsRepository
    private var launched = false

    private val openMain = Runnable {
        if (launched || isFinishing || isDestroyed) return@Runnable
        launched = true
        val destination = if (settingsRepository.hasCompletedOnboarding()) {
            LearningMenuActivity::class.java
        } else {
            InstrumentActivity::class.java
        }
        startActivity(Intent(this, destination))
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)
        settingsRepository = (application as DependencyProvider).settingsRepository

        val logo = findViewById<ImageView>(R.id.logo)
        val progress = findViewById<ProgressBar>(R.id.progress)

        logo.alpha = 0f
        logo.scaleX = 0.92f
        logo.scaleY = 0.92f
        logo.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(500L)
            .setInterpolator(DecelerateInterpolator())
            .start()

        progress.animate()
            .alpha(1f)
            .setStartDelay(250L)
            .setDuration(400L)
            .start()

        handler.postDelayed(openMain, SPLASH_DURATION_MS)
    }

    override fun onDestroy() {
        handler.removeCallbacks(openMain)
        super.onDestroy()
    }

    private companion object {
        const val SPLASH_DURATION_MS = 1_200L
    }
}

