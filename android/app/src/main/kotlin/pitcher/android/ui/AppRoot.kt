package pitcher.android.ui

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import pitcher.android.ui.classic.ClassicApp
import pitcher.android.ui.modern.ModernApp
import pitcher.android.ui.theme.ModernTheme
import pitcher.android.ui.theme.PitcherTheme

/**
 * Chooses the interface style. Both styles share the same view model, engine,
 * storage, and playback; only the screens differ. Classic is frozen and kept
 * as a fallback during the v2 transition.
 */
@Composable
fun AppRoot(
    vm: PitcherViewModel = viewModel(),
    incomingUri: Uri? = null,
    onIncomingConsumed: () -> Unit = {},
) {
    KeepScreenOn(vm.keepScreenOn)

    when (vm.uiStyle) {
        UiStyle.CLASSIC -> PitcherTheme {
            ClassicApp(vm, incomingUri, onIncomingConsumed)
        }
        UiStyle.MODERN -> ModernTheme {
            ModernApp(vm, incomingUri, onIncomingConsumed)
        }
    }
}
