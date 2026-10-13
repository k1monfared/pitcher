package pitcher.android.ui.modern

internal fun songCountLabel(count: Int): String =
    if (count == 1) "1 song" else "$count songs"
