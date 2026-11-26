package apk.mavier.dguitar.presentation.learning

import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import apk.mavier.dguitar.databinding.ItemLearningModuleFlipBinding

class ModuleFlipCard(
    private val binding: ItemLearningModuleFlipBinding,
    private val enabled: Boolean = true,
    private val onOpen: () -> Unit,
    private val onDisabled: () -> Unit = {},
    private val onRevealed: (ModuleFlipCard) -> Unit = {}
) {

    private var showingBack = false
    private var animating = false

    init {
        val distance = CAMERA_DISTANCE_DP * binding.root.resources.displayMetrics.density
        binding.cardFront.clipToOutline = true
        binding.cardFront.cameraDistance = distance
        binding.cardBack.cameraDistance = distance
        binding.root.alpha = if (enabled) 1f else DISABLED_ALPHA
        binding.root.setOnClickListener {
            if (enabled) toggle() else onDisabled()
        }
        binding.openButton.setOnClickListener {
            when {
                !enabled -> onDisabled()
                !animating -> onOpen()
            }
        }
    }

    fun isShowingBack(): Boolean = showingBack

    fun collapse() {
        if (!showingBack || animating) return
        animating = true
        flipToFront()
    }

    private fun toggle() {
        if (animating) return
        animating = true
        if (showingBack) flipToFront() else flipToBack()
    }

    private fun flipToBack() {
        showingBack = true
        onRevealed(this)
        binding.cardFront.animate()
            .rotationY(-HALF_TURN)
            .setDuration(HALF_DURATION_MS)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .withEndAction {
                binding.cardFront.visibility = View.GONE
                binding.cardBack.rotationY = HALF_TURN
                binding.cardBack.visibility = View.VISIBLE
                binding.cardBack.animate()
                    .rotationY(0f)
                    .setDuration(HALF_DURATION_MS)
                    .setInterpolator(AccelerateDecelerateInterpolator())
                    .withEndAction { animating = false }
                    .start()
            }
            .start()
    }

    private fun flipToFront() {
        showingBack = false
        binding.cardBack.animate()
            .rotationY(HALF_TURN)
            .setDuration(HALF_DURATION_MS)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .withEndAction {
                binding.cardBack.visibility = View.GONE
                binding.cardFront.rotationY = -HALF_TURN
                binding.cardFront.visibility = View.VISIBLE
                binding.cardFront.animate()
                    .rotationY(0f)
                    .setDuration(HALF_DURATION_MS)
                    .setInterpolator(AccelerateDecelerateInterpolator())
                    .withEndAction { animating = false }
                    .start()
            }
            .start()
    }

    private companion object {
        const val HALF_TURN = 90f
        const val HALF_DURATION_MS = 180L
        const val CAMERA_DISTANCE_DP = 8000f
        const val DISABLED_ALPHA = 0.55f
    }
}
