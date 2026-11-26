package apk.mavier.dguitar.presentation.common

import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import apk.mavier.dguitar.R
import apk.mavier.dguitar.domain.model.SoundKind
import apk.mavier.dguitar.domain.model.SoundProfile

class SoundProfileDialogController(
    private val activity: AppCompatActivity,
    private val familyProvider: () -> SoundFontFamily,
    private val importedNames: () -> List<String>,
    private val onSelect: (SoundProfile) -> Unit,
    private val onImport: () -> Unit
) {

    fun show(current: SoundProfile) {
        val bundled = SoundFontCatalog.forFamily(familyProvider())
        val imported = importedNames()
        val profiles = buildList {
            add(SoundProfile.synthetic)
            bundled.forEach { add(SoundProfile(SoundKind.BUNDLED, it.assetPath)) }
            imported.forEach { add(SoundProfile.imported(it)) }
        }
        val labels = buildList {
            add(activity.getString(R.string.sound_synthetic))
            bundled.forEach { add(activity.getString(it.labelRes)) }
            imported.forEach {
                add(activity.getString(R.string.sound_imported, it.removeSuffix(".sf2")))
            }
        }.toTypedArray()
        val checked = profiles.indexOf(current)

        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.sound_title)
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                dialog.dismiss()
                profiles.getOrNull(which)?.let(onSelect)
            }
            .setPositiveButton(R.string.sound_import) { _, _ -> onImport() }
            .setNegativeButton(R.string.close, null)
            .show()
    }
}
