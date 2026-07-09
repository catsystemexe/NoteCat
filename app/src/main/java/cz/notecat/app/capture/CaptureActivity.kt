package cz.notecat.app.capture

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import cz.notecat.app.ui.theme.NoteCatTheme

/**
 * Rychlá nahrávací obrazovka – cíl QS dlaždice a zkratky na ploše.
 * Po otevření okamžitě nahrává (žádné mezikroky, žádná hlasová výzva).
 */
class CaptureActivity : ComponentActivity() {

    private val viewModel: CaptureViewModel by viewModels()

    private var hasPermission by mutableStateOf(false)
    private var permissionRequested by mutableStateOf(false)

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            permissionRequested = true
            hasPermission = granted
            if (granted) viewModel.startRecording()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        hasPermission = granted

        if (granted) {
            if (savedInstanceState == null) viewModel.startRecording()
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }

        setContent {
            NoteCatTheme {
                CaptureScreen(
                    viewModel = viewModel,
                    hasPermission = hasPermission,
                    onRequestPermission = {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    },
                    onFinished = { finish() },
                )
            }
        }
    }
}
