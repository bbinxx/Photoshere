package com.bbinxx.photoshere.ui.processing

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bbinxx.photoshere.ui.components.GradientBackground
import com.bbinxx.photoshere.ui.components.StatRow
import com.bbinxx.photoshere.ui.theme.Amber
import com.bbinxx.photoshere.ui.theme.Aurora
import com.bbinxx.photoshere.ui.theme.Coral
import com.bbinxx.photoshere.ui.theme.Muted
import java.util.Locale

@Composable
fun ProcessingScreen(
    sessionId: String,
    onBackHome: () -> Unit,
    onOpenViewer: (String) -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val app = context.applicationContext as com.bbinxx.photoshere.PhotoSphereApplication
    val viewModel: ProcessingViewModel = viewModel(
        key = "processing-$sessionId",
        factory = ProcessingViewModel.Factory(app, sessionId),
    )
    val ui by viewModel.state.collectAsStateWithLifecycle()

    BackHandler(enabled = ui.running) {
        viewModel.cancel()
        onBackHome()
    }

    GradientBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(24.dp))
            Text(
                text = when {
                    ui.result != null -> "Your sphere is ready"
                    ui.error != null -> "Could not build sphere"
                    ui.cancelled -> "Processing cancelled"
                    else -> "Building your sphere"
                },
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = when {
                    ui.result != null -> "Rendered on this device. Nothing was uploaded."
                    ui.error != null -> ui.error.orEmpty()
                    ui.cancelled -> "You can retry at any time using the saved photos."
                    else -> "Projecting photos onto a spherical canvas using sensor poses."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (ui.error != null) Coral else Muted,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(36.dp))

            if (ui.error == null && !ui.cancelled) {
                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        progress = { ui.fraction.coerceIn(0f, 1f) },
                        modifier = Modifier.size(180.dp),
                        strokeWidth = 7.dp,
                        color = Aurora,
                        trackColor = Color.White.copy(alpha = 0.12f),
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${(ui.fraction * 100).toInt()}%",
                            style = MaterialTheme.typography.headlineMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = ui.stage,
                            style = MaterialTheme.typography.labelMedium,
                            color = Muted,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 12.dp),
                        )
                    }
                }
                Spacer(Modifier.height(28.dp))
                ProgressSteps(ui.fraction)
            }

            if (ui.result != null) {
                val result = ui.result!!
                Icon(
                    Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = Aurora,
                    modifier = Modifier.size(64.dp),
                )
                Spacer(Modifier.height(20.dp))
                StatRow(
                    "photos" to result.usedImages.toString(),
                    "coverage" to "${(result.coverage * 100).toInt()}%",
                    "size" to String.format(
                        Locale.US,
                        "%.1f MB",
                        result.record.sizeBytes / (1024f * 1024f),
                    ),
                    "time" to String.format(Locale.US, "%.1fs", result.elapsedMs / 1000f),
                )
                Spacer(Modifier.height(24.dp))
                ResultSummary(result)
            }

            if (ui.error != null) {
                Icon(
                    Icons.Rounded.ErrorOutline,
                    contentDescription = null,
                    tint = Coral,
                    modifier = Modifier.size(56.dp),
                )
            }
            if (ui.cancelled) {
                Icon(
                    Icons.Rounded.ErrorOutline,
                    contentDescription = null,
                    tint = Amber,
                    modifier = Modifier.size(56.dp),
                )
            }

            Spacer(Modifier.height(32.dp))

            when {
                ui.result != null -> {
                    Button(
                        onClick = { onOpenViewer(ui.result!!.record.id) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Rounded.Visibility, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("View sphere")
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(onClick = onBackHome, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.Home, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Back home")
                    }
                }
                ui.error != null || ui.cancelled -> {
                    Button(onClick = viewModel::start, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.Replay, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Retry stitching")
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(onClick = onBackHome, modifier = Modifier.fillMaxWidth()) {
                        Text("Back home")
                    }
                }
                else -> {
                    OutlinedButton(onClick = viewModel::cancel, modifier = Modifier.fillMaxWidth()) {
                        Text("Cancel")
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ProgressSteps(fraction: Float) {
    val steps = listOf(
        "Project" to 0.35f,
        "Blend" to 0.72f,
        "Fill gaps" to 0.86f,
        "Save" to 0.94f,
        "Export" to 1.0f,
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        steps.forEach { (label, threshold) ->
            val reached = fraction >= threshold - 0.05f
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                androidx.compose.foundation.Canvas(Modifier.size(10.dp)) {
                    drawCircle(
                        color = if (reached) Aurora else Color.White.copy(alpha = 0.25f),
                        radius = size.minDimension / 2f,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (reached) Color.White else Muted,
                )
            }
        }
    }
}

@Composable
private fun ResultSummary(result: ProcessingResult) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = result.record.title,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "${result.record.width} × ${result.record.height} · equirectangular · GPano metadata",
            style = MaterialTheme.typography.bodySmall,
            color = Muted,
        )
        if (result.exportedUri != null) {
            Text(
                text = "Saved to Pictures/PhotoSphere",
                style = MaterialTheme.typography.bodySmall,
                color = Aurora,
            )
        }
        result.warnings.forEach { warning ->
            androidx.compose.material3.Surface(
                shape = RoundedCornerShape(14.dp),
                color = Amber.copy(alpha = 0.12f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = warning,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = Amber,
                )
            }
        }
    }
}
