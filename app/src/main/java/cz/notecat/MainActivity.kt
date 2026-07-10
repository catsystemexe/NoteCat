package cz.notecat

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.navigation.compose.*
import cz.notecat.ui.*

class MainActivity: ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}.launch(Manifest.permission.RECORD_AUDIO)
        val start = if (intent.getBooleanExtra("capture", false)) "capture" else "list"
        setContent { MaterialTheme { val nav = rememberNavController(); NavHost(nav, startDestination = start) {
            composable("list") { NoteListScreen(nav) }
            composable("capture") { CaptureScreen(nav) }
            composable("detail/{id}") { DetailScreen(nav, it.arguments!!.getString("id")!!) }
        } } }
    }
}
