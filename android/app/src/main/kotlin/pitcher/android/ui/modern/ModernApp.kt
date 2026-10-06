package pitcher.android.ui.modern

import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import pitcher.android.ui.PitcherViewModel

/**
 * The v2 interface: a touch-first pitch studio. The home screen is the Studio;
 * the Library and the sheets are secondary.
 */
@Composable
fun ModernApp(
    vm: PitcherViewModel = viewModel(),
    incomingUri: Uri? = null,
    onIncomingConsumed: () -> Unit = {},
) {
    val context = LocalContext.current

    LaunchedEffect(incomingUri) {
        val uri = incomingUri ?: return@LaunchedEffect
        vm.import(uri) {}
        onIncomingConsumed()
    }

    LaunchedEffect(vm.shareUri) {
        val uri = vm.shareUri ?: return@LaunchedEffect
        val send = Intent(Intent.ACTION_SEND).apply {
            type = vm.exportMime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Share pitched audio"))
        vm.consumeShareUri()
    }

    var showLibrary by remember { mutableStateOf(vm.current == null) }
    var showExport by remember { mutableStateOf(false) }
    var showPitches by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }

    if (showLibrary) {
        ModernLibraryScreen(
            vm = vm,
            onOpen = {
                showLibrary = false
            },
            onClose = { if (vm.current != null) showLibrary = false },
        )
    } else {
        StudioScreen(
            vm = vm,
            onOpenLibrary = { showLibrary = true },
            onOpenPitches = { showPitches = true },
            onOpenExport = { showExport = true },
            onOpenSettings = { showSettings = true },
        )
    }

    if (showExport) {
        ExportSheet(vm = vm, onDismiss = { showExport = false })
    }
    if (showPitches) {
        PitchesSheet(
            vm = vm,
            onDismiss = { showPitches = false },
            onOpenExport = {
                showPitches = false
                showExport = true
            },
        )
    }
    if (showSettings) {
        SettingsSheet(vm = vm, onDismiss = { showSettings = false })
    }
}
