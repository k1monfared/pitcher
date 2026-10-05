package pitcher.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import pitcher.android.ui.PitcherApp
import pitcher.android.ui.theme.PitcherTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PitcherTheme {
                PitcherApp()
            }
        }
    }
}
