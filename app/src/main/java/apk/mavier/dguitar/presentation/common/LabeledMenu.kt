package apk.mavier.dguitar.presentation.common

import android.content.Context
import android.view.Menu
import android.view.View
import android.widget.PopupMenu

fun <T> Context.showLabeledMenu(
    anchor: View,
    items: List<T>,
    label: (T) -> String,
    onPick: (T) -> Unit
) {
    val popup = PopupMenu(this, anchor)
    items.forEachIndexed { index, item ->
        popup.menu.add(Menu.NONE, index, index, label(item))
    }
    popup.setOnMenuItemClickListener { menuItem ->
        items.getOrNull(menuItem.itemId)?.let(onPick)
        true
    }
    popup.show()
}
