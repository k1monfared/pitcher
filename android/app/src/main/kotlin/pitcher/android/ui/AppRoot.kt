package pitcher.android.ui

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import pitcher.android.ui.modern.ModernApp
import pitcher.android.ui.theme.ModernTheme

@Composable
fun AppRoot(
    vm: PitcherViewModel = viewModel(),
    incomingUri: Uri? = null,
    onIncomingConsumed: () -> Unit = {},
) {
    KeepScreenOn(vm.keepScreenOn)
    ModernTheme {
        ModernApp(vm, incomingUri, onIncomingConsumed)
    }
}
