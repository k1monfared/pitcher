package pitcher.android

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import pitcher.android.ui.AppRoot
import pitcher.android.ui.PitcherViewModel

class MainActivity : ComponentActivity() {
    private val incoming = mutableStateOf<Uri?>(null)
    private val viewModel: PitcherViewModel by viewModels()
    private var askedNotifications = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        incoming.value = extractUri(intent)
        setContent {
            LaunchedEffect(viewModel.isPlaying) {
                if (viewModel.isPlaying) requestNotificationPermissionIfNeeded()
            }
            AppRoot(
                vm = viewModel,
                incomingUri = incoming.value,
                onIncomingConsumed = { incoming.value = null },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        incoming.value = extractUri(intent)
    }

    private fun extractUri(intent: Intent?): Uri? {
        if (intent == null) return null
        return when (intent.action) {
            Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(
                intent,
                Intent.EXTRA_STREAM,
                Uri::class.java,
            )
            Intent.ACTION_VIEW -> intent.data
            else -> null
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (askedNotifications) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        askedNotifications = true
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
        }
    }
}
