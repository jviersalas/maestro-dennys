package apk.mavier.dguitar.presentation.ui

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

fun View.applySystemBarInsets() {
    val baseLeft = paddingLeft
    val baseTop = paddingTop
    val baseRight = paddingRight
    val baseBottom = paddingBottom
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        val bars = insets.getInsets(
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
        )
        view.setPadding(
            baseLeft + bars.left,
            baseTop + bars.top,
            baseRight + bars.right,
            baseBottom + bars.bottom
        )
        insets
    }
    ViewCompat.requestApplyInsets(this)
}
