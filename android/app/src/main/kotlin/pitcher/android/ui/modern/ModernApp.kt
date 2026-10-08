package pitcher.android.ui.modern

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    var showSettings by remember { mutableStateOf(false) }
    var showTuner by remember { mutableStateOf(false) }
    var showTimeline by remember { mutableStateOf(false) }
    var libraryIntroDone by remember { mutableStateOf(false) }
    var onboardingStep by remember { mutableStateOf<String?>(null) }
    val onboarding = rememberOnboardingTargets()

    Box(modifier = Modifier.fillMaxSize()) {
        if (showLibrary) {
            ModernLibraryScreen(
                vm = vm,
                onOpen = {
                    showLibrary = false
                },
                onClose = { if (vm.current != null) showLibrary = false },
                onboarding = onboarding,
            )
        } else {
            StudioScreen(
                vm = vm,
                onOpenLibrary = { showLibrary = true },
                onOpenExport = { showExport = true },
                onOpenSettings = { showSettings = true },
                onOpenTuner = { showTuner = true },
                onOpenTimeline = { showTimeline = true },
                onboarding = onboarding,
            )
        }

        if (vm.onboardingActive) {
            when {
                showLibrary && !libraryIntroDone -> OnboardingOverlay(
                    targets = onboarding,
                    steps = LIBRARY_TOUR,
                    finalLabel = "Next",
                    onFinish = { libraryIntroDone = true },
                    onSkip = { vm.finishOnboarding() },
                    onStep = { onboardingStep = it.targetKey },
                )
                !showLibrary && vm.current != null -> OnboardingOverlay(
                    targets = onboarding,
                    onFinish = { vm.finishOnboarding() },
                    onStep = { onboardingStep = it.targetKey },
                )
            }
        }

        if (vm.onboardingActive && onboardingStep == "speed") {
            SpeedHud(
                tempo = vm.tempo,
                accent = pitcher.android.ui.theme.PitchHues.forCents(vm.faderCents),
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }

    if (showExport) {
        ExportSheet(vm = vm, onDismiss = { showExport = false }, onOpenTimeline = { showTimeline = true })
    }
    if (showSettings) {
        SettingsSheet(vm = vm, onDismiss = { showSettings = false })
    }
    if (showTuner) {
        TunerModal(vm = vm, onDismiss = { showTuner = false })
    }
    if (showTimeline) {
        TimelineSheet(vm = vm, onDismiss = { showTimeline = false })
    }
}
