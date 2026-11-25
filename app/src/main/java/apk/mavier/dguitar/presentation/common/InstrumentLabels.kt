package apk.mavier.dguitar.presentation.common

fun instrumentLabel(stringCount: Int): String = when (stringCount) {
    4 -> "Bajo 4"
    5 -> "Bajo 5"
    else -> "Guitarra"
}
