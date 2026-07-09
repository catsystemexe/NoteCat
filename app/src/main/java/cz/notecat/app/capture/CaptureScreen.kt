package cz.notecat.app.capture

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cz.notecat.app.R
import kotlinx.coroutines.delay

@Composable
fun CaptureScreen(
    viewModel: CaptureViewModel,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    onFinished: () -> Unit,
) {
    val state by viewModel.state.collectAsState()

    // Po uložení / zahození krátce ukázat potvrzení a zavřít
    LaunchedEffect(state) {
        when (state) {
            is CaptureUiState.Saved -> {
                delay(900)
                if (viewModel.closeAfterSave) onFinished()
            }
            is CaptureUiState.DiscardedEmpty -> {
                delay(1400)
                onFinished()
            }
            else -> Unit
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when {
            !hasPermission -> PermissionContent(onRequestPermission, onFinished)
            else -> when (val s = state) {
                is CaptureUiState.Preparing,
                is CaptureUiState.Recording -> RecordingContent(
                    elapsedMs = (s as? CaptureUiState.Recording)?.elapsedMs ?: 0L,
                    amplitude = (s as? CaptureUiState.Recording)?.amplitude ?: 0f,
                    onStop = { viewModel.finish(save = true) },
                    onCancel = { viewModel.finish(save = false) },
                )
                is CaptureUiState.Saving -> CenterMessage(text = "…")
                is CaptureUiState.Saved -> SavedContent()
                is CaptureUiState.DiscardedEmpty ->
                    CenterMessage(text = stringResource(R.string.capture_discarded_empty))
                is CaptureUiState.Error -> ErrorContent(s.message, onFinished)
            }
        }
    }
}

@Composable
private fun RecordingContent(
    elapsedMs: Long,
    amplitude: Float,
    onStop: () -> Unit,
    onCancel: () -> Unit,
) {
    val pulse by animateFloatAsState(
        targetValue = 1f + (amplitude.coerceIn(0f, 1f) * 0.35f),
        animationSpec = tween(120),
        label = "pulse",
    )
    val ringColor by animateColorAsState(
        targetValue = MaterialTheme.colorScheme.primary.copy(
            alpha = 0.25f + amplitude.coerceIn(0f, 1f) * 0.5f
        ),
        animationSpec = tween(120),
        label = "ring",
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(48.dp))
        Text(
            text = stringResource(R.string.capture_listening),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = formatElapsed(elapsedMs),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
        )

        Spacer(Modifier.weight(1f))

        // Pulzující indikátor mikrofonu
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(180.dp)
                    .scale(pulse)
                    .background(ringColor, CircleShape)
            )
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Mic,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(52.dp),
                )
            }
        }

        Spacer(Modifier.weight(1f))

        Text(
            text = stringResource(R.string.capture_hint_command),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))

        // Výrazné ukončovací tlačítko – vždy dostupná záloha hlasového povelu,
        // umístěné dole kvůli ovládání jednou rukou.
        Button(
            onClick = onStop,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
            ),
        ) {
            Icon(Icons.Filled.Check, contentDescription = null)
            Spacer(Modifier.size(10.dp))
            Text(stringResource(R.string.capture_stop), style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
            Text(
                stringResource(R.string.capture_cancel),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SavedContent() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .background(MaterialTheme.colorScheme.primary, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(48.dp),
            )
        }
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.capture_saved),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
private fun CenterMessage(text: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
private fun ErrorContent(message: String, onFinished: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.capture_error_mic),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onFinished) { Text(stringResource(R.string.back)) }
    }
}

@Composable
private fun PermissionContent(onRequestPermission: () -> Unit, onFinished: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Filled.Mic,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(64.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.capture_permission_needed),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onRequestPermission) {
            Text(stringResource(R.string.capture_permission_grant))
        }
        TextButton(onClick = onFinished) {
            Text(stringResource(R.string.back), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun formatElapsed(ms: Long): String {
    val totalSec = ms / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return "%d:%02d".format(min, sec)
}
