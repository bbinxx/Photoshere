package com.bbinxx.photoshere.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bbinxx.photoshere.PhotoSphereApplication
import com.bbinxx.photoshere.core.model.CaptureSettings
import com.bbinxx.photoshere.ui.components.GradientBackground
import com.bbinxx.photoshere.ui.theme.Aurora
import com.bbinxx.photoshere.ui.theme.Muted
import java.util.Locale

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onAbout: () -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as PhotoSphereApplication
    val viewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModel.Factory(app),
    )
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var confirmReset by remember { mutableStateOf(false) }

    GradientBackground {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "Capture, stitching and storage",
                        style = MaterialTheme.typography.labelSmall,
                        color = Muted,
                    )
                }
                IconButton(onClick = onAbout) {
                    Icon(Icons.Rounded.Info, contentDescription = "About", tint = Aurora)
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item {
                    SettingsSection("Capture guidance") {
                        SwitchRow(
                            title = "Automatic capture",
                            subtitle = "Take the photo when the target is aligned and steady",
                            checked = settings.autoCapture,
                            onCheckedChange = { value ->
                                viewModel.update { it.copy(autoCapture = value) }
                            },
                        )
                        SettingSlider(
                            title = "Alignment tolerance",
                            valueText = String.format(Locale.US, "%.0f°", settings.alignmentToleranceDeg),
                            value = settings.alignmentToleranceDeg,
                            range = 1f..8f,
                            steps = 6,
                            onValueChange = { value ->
                                viewModel.update { it.copy(alignmentToleranceDeg = value) }
                            },
                        )
                        SettingSlider(
                            title = "Stability hold",
                            valueText = "${settings.stabilityMs} ms",
                            value = settings.stabilityMs.toFloat(),
                            range = 200f..1500f,
                            steps = 12,
                            onValueChange = { value ->
                                viewModel.update {
                                    it.copy(stabilityMs = (value / 100f).toInt() * 100)
                                }
                            },
                        )
                        SettingSlider(
                            title = "Capture delay",
                            valueText = "${settings.captureDelayMs} ms",
                            value = settings.captureDelayMs.toFloat(),
                            range = 0f..800f,
                            steps = 15,
                            onValueChange = { value ->
                                viewModel.update {
                                    it.copy(captureDelayMs = (value / 50f).toInt() * 50)
                                }
                            },
                        )
                    }
                }
                item {
                    SettingsSection("Stitching") {
                        SettingSlider(
                            title = "Frame overlap",
                            valueText = "${settings.overlapPercent}%",
                            value = settings.overlapPercent.toFloat(),
                            range = 15f..60f,
                            steps = 8,
                            onValueChange = { value ->
                                viewModel.update {
                                    it.copy(overlapPercent = (value / 5f).toInt() * 5)
                                }
                            },
                        )
                        SettingSlider(
                            title = "Target density",
                            valueText = String.format(Locale.US, "%.1f×", settings.densityMultiplier),
                            value = settings.densityMultiplier,
                            range = 0.6f..1.6f,
                            steps = 9,
                            onValueChange = { value ->
                                viewModel.update {
                                    it.copy(
                                        densityMultiplier = (value * 10f).toInt() / 10f,
                                    )
                                }
                            },
                        )
                        OutputWidthRow(
                            selected = settings.outputWidth,
                            onSelect = { width ->
                                viewModel.update {
                                    it.copy(outputWidth = width, outputHeight = width / 2)
                                }
                            },
                        )
                        SettingSlider(
                            title = "Processing quality",
                            valueText = String.format(
                                Locale.US,
                                "%.1f×",
                                settings.processingQuality,
                            ),
                            value = settings.processingQuality,
                            range = 0.5f..1.5f,
                            steps = 9,
                            onValueChange = { value ->
                                viewModel.update {
                                    it.copy(processingQuality = (value * 10f).toInt() / 10f)
                                }
                            },
                        )
                    }
                }
                item {
                    SettingsSection("Camera locks") {
                        SwitchRow(
                            title = "Lock exposure",
                            subtitle = "Keep brightness consistent across the sphere",
                            checked = settings.exposureLock,
                            onCheckedChange = { value ->
                                viewModel.update { it.copy(exposureLock = value) }
                            },
                        )
                        SwitchRow(
                            title = "Lock focus",
                            subtitle = "Use a fixed focus distance after alignment",
                            checked = settings.focusLock,
                            onCheckedChange = { value ->
                                viewModel.update { it.copy(focusLock = value) }
                            },
                        )
                    }
                }
                item {
                    SettingsSection("Storage & export") {
                        SwitchRow(
                            title = "Keep original photos",
                            subtitle = "Preserves raw captures so a session can be resumed",
                            checked = settings.saveOriginals,
                            onCheckedChange = { value ->
                                viewModel.update { it.copy(saveOriginals = value) }
                            },
                        )
                        SwitchRow(
                            title = "Auto stitch",
                            subtitle = "Open the processing screen when capture finishes",
                            checked = settings.autoStitch,
                            onCheckedChange = { value ->
                                viewModel.update { it.copy(autoStitch = value) }
                            },
                        )
                        SwitchRow(
                            title = "Auto export",
                            subtitle = "Save finished spheres to Pictures/PhotoSphere",
                            checked = settings.autoExport,
                            onCheckedChange = { value ->
                                viewModel.update { it.copy(autoExport = value) }
                            },
                        )
                    }
                }
                item {
                    SettingsSection("Feedback") {
                        SwitchRow(
                            title = "Haptic feedback",
                            subtitle = "Vibrate when a photo is taken",
                            checked = settings.hapticFeedback,
                            onCheckedChange = { value ->
                                viewModel.update { it.copy(hapticFeedback = value) }
                            },
                        )
                        SwitchRow(
                            title = "Shutter sound",
                            subtitle = "Play a shutter click for each capture",
                            checked = settings.soundFeedback,
                            onCheckedChange = { value ->
                                viewModel.update { it.copy(soundFeedback = value) }
                            },
                        )
                    }
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(onClick = onAbout) { Text("About PhotoSphere") }
                        TextButton(onClick = { confirmReset = true }) { Text("Reset defaults") }
                    }
                }
                item { Spacer(Modifier.height(12.dp)) }
            }
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset settings?") },
            text = { Text("All capture and stitching options return to their defaults.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.reset()
                        confirmReset = false
                    },
                ) { Text("Reset") }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = Color.White.copy(alpha = 0.05f),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = Aurora,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            content()
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = Muted,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

@Composable
private fun SettingSlider(
    title: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit,
) {
    Column(Modifier.padding(vertical = 6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
            )
            Text(
                text = valueText,
                style = MaterialTheme.typography.labelMedium,
                color = Aurora,
            )
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onValueChange,
            valueRange = range,
            steps = steps,
        )
    }
}

@Composable
private fun OutputWidthRow(selected: Int, onSelect: (Int) -> Unit) {
    Column(Modifier.padding(top = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "Equirectangular width",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
            )
            Text(
                text = "$selected × ${selected / 2}",
                style = MaterialTheme.typography.labelMedium,
                color = Aurora,
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(2048, 3072, 4096).forEach { width ->
                FilterChip(
                    selected = selected == width,
                    onClick = { onSelect(width) },
                    label = { Text("$width") },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
