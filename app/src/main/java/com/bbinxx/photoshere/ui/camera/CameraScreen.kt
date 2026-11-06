package com.bbinxx.photoshere.ui.camera

import android.media.MediaActionSound
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FlashAuto
import androidx.compose.material.icons.rounded.FlashOff
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material.icons.rounded.ZoomOut
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bbinxx.photoshere.PhotoSphereApplication
import com.bbinxx.photoshere.capture.CaptureEvent
import com.bbinxx.photoshere.capture.CaptureUiState
import com.bbinxx.photoshere.capture.CaptureViewModel
import com.bbinxx.photoshere.core.model.CaptureState
import com.bbinxx.photoshere.core.model.FlashSetting
import com.bbinxx.photoshere.core.model.TargetStatus
import com.bbinxx.photoshere.ui.permissions.CameraPermissionGate
import com.bbinxx.photoshere.ui.theme.Amber
import com.bbinxx.photoshere.ui.theme.Aurora
import com.bbinxx.photoshere.ui.theme.Coral
import com.bbinxx.photoshere.ui.theme.Ink
import com.bbinxx.photoshere.ui.theme.Muted
import com.bbinxx.photoshere.ui.theme.Sky
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

@Composable
fun CameraScreen(
    sessionId: String?,
    onClose: () -> Unit,
    onSettings: () -> Unit,
    onProcessing: (String) -> Unit,
) {
    CameraPermissionGate {
        val context = LocalContext.current
        val app = context.applicationContext as PhotoSphereApplication
        val viewModel: CaptureViewModel = viewModel(
            key = "capture-${sessionId ?: "new"}",
            factory = CaptureViewModel.Factory(app, sessionId),
        )
        val ui by viewModel.state.collectAsStateWithLifecycle()
        val lifecycleOwner = LocalLifecycleOwner.current
        val configuration = LocalConfiguration.current
        val haptic = LocalHapticFeedback.current
        val snackbarHostState = remember { SnackbarHostState() }
        val sound = remember { MediaActionSound().apply { load(MediaActionSound.SHUTTER_CLICK) } }
        val feedback by rememberUpdatedState(
            ui.hapticFeedback to ui.soundFeedback,
        )
        val previewView = remember {
            PreviewView(context).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            }
        }

        DisposableEffect(Unit) {
            onDispose { sound.release() }
        }

        DisposableEffect(lifecycleOwner, previewView) {
            viewModel.attachPreview(previewView, lifecycleOwner)
            onDispose { viewModel.detachPreview() }
        }

        LaunchedEffect(configuration.orientation) {
            delay(200)
            previewView.display?.rotation?.let(viewModel::refreshRotation)
        }

        LaunchedEffect(Unit) {
            viewModel.events.collect { event ->
                val (hapticsEnabled, soundEnabled) = feedback
                when (event) {
                    CaptureEvent.Shutter -> {
                        if (soundEnabled) sound.play(MediaActionSound.SHUTTER_CLICK)
                        if (hapticsEnabled) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                    }
                    CaptureEvent.Success, CaptureEvent.Failure, CaptureEvent.Completed -> {
                        if (hapticsEnabled) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                    }
                }
            }
        }

        LaunchedEffect(ui.error) {
            ui.error?.let {
                snackbarHostState.showSnackbar(it)
                viewModel.clearError()
            }
        }
        LaunchedEffect(ui.notice) {
            ui.notice?.let {
                snackbarHostState.showSnackbar(it)
                viewModel.clearError()
            }
        }
        LaunchedEffect(ui.completedSessionId) {
            val id = ui.completedSessionId
            if (id != null) {
                viewModel.consumeCompletion()
                onProcessing(id)
            }
        }

        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AndroidView(
                factory = { previewView },
                modifier = Modifier.fillMaxSize(),
            )

            CaptureOverlay(ui)

            CameraTopBar(
                ui = ui,
                onClose = {
                    viewModel.cancelCapture()
                    onClose()
                },
                onSettings = onSettings,
                onFlash = {
                    val next = when (ui.camera.flash) {
                        FlashSetting.OFF -> FlashSetting.AUTO
                        FlashSetting.AUTO -> FlashSetting.ON
                        FlashSetting.ON -> FlashSetting.TORCH
                        FlashSetting.TORCH -> FlashSetting.OFF
                    }
                    viewModel.setFlash(next)
                },
                onLensSelected = viewModel::selectLens,
                onZoom = { delta ->
                    viewModel.setZoom((ui.camera.zoomRatio + delta).coerceIn(
                        ui.camera.minZoom,
                        ui.camera.maxZoom,
                    ))
                },
            )

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 18.dp),
            ) {
                Box(Modifier.fillMaxWidth()) {
                    AutoCaptureChip(
                        enabled = ui.autoCapture,
                        onToggle = { viewModel.toggleAutoCapture(!ui.autoCapture) },
                        modifier = Modifier.align(Alignment.CenterStart),
                    )
                    ShutterButton(
                        capturing = ui.state == CaptureState.CAPTURING ||
                            ui.state == CaptureState.VALIDATING_IMAGE,
                        enabled = ui.state != CaptureState.COMPLETED &&
                            ui.state != CaptureState.CANCELLED,
                        onClick = viewModel::requestManualCapture,
                        modifier = Modifier.align(Alignment.Center),
                    )
                    FinishControls(
                        ui = ui,
                        onFinish = viewModel::finishCapture,
                        modifier = Modifier.align(Alignment.CenterEnd),
                    )
                }
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 132.dp, start = 16.dp, end = 16.dp),
            )
        }
    }
}

@Composable
private fun CaptureOverlay(ui: CaptureUiState) {
    val density = LocalDensity.current
    val aligned = ui.alignment.aligned
    val glow by animateFloatAsState(
        targetValue = if (aligned) 1f else 0f,
        animationSpec = tween(180),
        label = "alignmentGlow",
    )
    val stability by animateFloatAsState(
        targetValue = ui.alignment.stabilityProgress,
        animationSpec = tween(120),
        label = "stability",
    )

    Canvas(Modifier.fillMaxSize()) {
        val minSide = min(size.width, size.height)
        val center = Offset(size.width / 2f, size.height / 2f)
        val ringRadius = minSide * 0.33f
        val progressRingRadius = ringRadius
        val total = ui.totalCount.coerceAtLeast(1)
        val captured = ui.capturedCount
        val ringStroke = with(density) { 2.dp.toPx() }

        drawCircle(
            color = Color.White.copy(alpha = 0.18f),
            radius = progressRingRadius,
            center = center,
            style = Stroke(width = ringStroke),
        )
        if (captured > 0) {
            drawArc(
                color = Aurora.copy(alpha = 0.9f),
                startAngle = -90f,
                sweepAngle = 360f * (captured.toFloat() / total),
                useCenter = false,
                topLeft = Offset(center.x - progressRingRadius, center.y - progressRingRadius),
                size = Size(progressRingRadius * 2, progressRingRadius * 2),
                style = Stroke(width = with(density) { 4.dp.toPx() }, cap = StrokeCap.Round),
            )
        }

        ui.targets.forEachIndexed { index, target ->
            val angle = Math.toRadians((-90f + index * 360f / total).toDouble())
            val position = Offset(
                center.x + (cos(angle) * progressRingRadius).toFloat(),
                center.y + (sin(angle) * progressRingRadius).toFloat(),
            )
            val isCurrent = target.id == ui.currentTarget?.id
            val color = when (target.status) {
                TargetStatus.CAPTURED -> Aurora
                TargetStatus.CAPTURING -> Sky
                TargetStatus.FAILED -> Coral
                TargetStatus.CURRENT -> Amber
                else -> Color.White.copy(alpha = if (isCurrent) 0.9f else 0.35f)
            }
            val radius = with(density) { if (isCurrent) 5.dp.toPx() else 2.4.dp.toPx() }
            drawCircle(color = color, radius = radius, center = position)
            if (isCurrent) {
                drawCircle(
                    color = Amber.copy(alpha = 0.35f),
                    radius = radius * 2.4f,
                    center = position,
                    style = Stroke(width = with(density) { 1.5.dp.toPx() }),
                )
            }
        }

        val targetRadius = with(density) { 34.dp.toPx() }
        val targetColor = if (aligned) Aurora else Color.White.copy(alpha = 0.85f)
        if (glow > 0f) {
            drawCircle(
                color = Aurora.copy(alpha = 0.16f * glow),
                radius = targetRadius * (1.35f + 0.1f * glow),
                center = center,
            )
            drawCircle(
                color = Aurora.copy(alpha = 0.35f * glow),
                radius = targetRadius * 1.15f,
                center = center,
                style = Stroke(width = with(density) { 2.dp.toPx() }),
            )
        }
        drawCircle(
            color = targetColor,
            radius = targetRadius,
            center = center,
            style = Stroke(width = with(density) { 2.dp.toPx() }),
        )
        drawCircle(color = targetColor, radius = with(density) { 4.dp.toPx() }, center = center)

        if (aligned && stability < 1f) {
            drawArc(
                color = Amber,
                startAngle = -90f,
                sweepAngle = 360f * stability,
                useCenter = false,
                topLeft = Offset(center.x - targetRadius - 8.dp.toPx(), center.y - targetRadius - 8.dp.toPx()),
                size = Size(
                    (targetRadius + 8.dp.toPx()) * 2,
                    (targetRadius + 8.dp.toPx()) * 2,
                ),
                style = Stroke(
                    width = with(density) { 3.dp.toPx() },
                    cap = StrokeCap.Round,
                ),
            )
        }

        if (ui.totalCount > 0 && ui.currentTarget != null) {
            val scale = minSide * 0.0036f
            val yaw = ui.alignment.yawErrorDeg.coerceIn(-70f, 70f)
            val pitch = ui.alignment.pitchErrorDeg.coerceIn(-70f, 70f)
            val pitchRadians = Math.toRadians(ui.orientation.pitchDeg.toDouble())
            val rawDx = (yaw * cos(pitchRadians).toFloat()) * scale
            val rawDy = -pitch * scale
            val length = kotlin.math.sqrt(rawDx * rawDx + rawDy * rawDy)
            val maxLength = ringRadius * 0.92f
            val factor = if (length > maxLength && length > 0f) maxLength / length else 1f
            val dotCenter = if (aligned) {
                center
            } else {
                Offset(center.x + rawDx * factor, center.y + rawDy * factor)
            }

            if (!aligned) {
                drawLine(
                    color = Color.White.copy(alpha = 0.18f),
                    start = center,
                    end = dotCenter,
                    strokeWidth = with(density) { 1.dp.toPx() },
                )
            }
            drawCircle(
                color = Color.Black.copy(alpha = 0.35f),
                radius = with(density) { 12.dp.toPx() },
                center = dotCenter,
            )
            drawCircle(
                color = if (aligned) Aurora else Color.White,
                radius = with(density) { 8.dp.toPx() },
                center = dotCenter,
            )
            drawCircle(
                color = if (aligned) Ink else Amber,
                radius = with(density) { 3.dp.toPx() },
                center = dotCenter,
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(top = 64.dp, start = 24.dp, end = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            shape = RoundedCornerShape(50),
            color = Color.Black.copy(alpha = 0.42f),
        ) {
            Text(
                text = ui.instruction,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 9.dp),
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            )
        }
        if (ui.sensorDegraded) {
            Spacer(Modifier.height(10.dp))
            Surface(
                shape = RoundedCornerShape(50),
                color = Amber.copy(alpha = 0.18f),
            ) {
                Text(
                    text = "Motion sensor degraded — spherical guidance may be inaccurate",
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                    color = Amber,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
        if (ui.totalCount > 0) {
            Spacer(Modifier.height(14.dp))
            Text(
                text = "${ui.capturedCount} / ${ui.totalCount} positions  ·  " +
                    "${(100f * ui.capturedCount / ui.totalCount).toInt()}%",
                color = Color.White.copy(alpha = 0.82f),
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
private fun CameraTopBar(
    ui: CaptureUiState,
    onClose: () -> Unit,
    onSettings: () -> Unit,
    onFlash: () -> Unit,
    onLensSelected: (String) -> Unit,
    onZoom: (Float) -> Unit,
) {
    var lensMenuOpen by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Rounded.Close, contentDescription = "Close capture", tint = Color.White)
            }
            Spacer(Modifier.weight(1f))
            Box {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color.Black.copy(alpha = 0.4f),
                    modifier = Modifier.clickable(enabled = ui.canSwitchLens) { lensMenuOpen = true },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Rounded.CameraAlt,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = ui.camera.lenses.firstOrNull {
                                it.id == ui.camera.selectedCameraId
                            }?.label ?: "Camera",
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
                DropdownMenu(
                    expanded = lensMenuOpen,
                    onDismissRequest = { lensMenuOpen = false },
                ) {
                    ui.camera.lenses.forEach { lens ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    "${lens.label}  ·  ${lens.horizontalFovDeg.toInt()}°",
                                    color = if (lens.id == ui.camera.selectedCameraId) {
                                        Aurora
                                    } else {
                                        Color.Unspecified
                                    },
                                )
                            },
                            onClick = {
                                lensMenuOpen = false
                                onLensSelected(lens.id)
                            },
                        )
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = onFlash) {
                val icon = when (ui.camera.flash) {
                    FlashSetting.OFF -> Icons.Rounded.FlashOff
                    FlashSetting.AUTO -> Icons.Rounded.FlashAuto
                    FlashSetting.ON -> Icons.Rounded.FlashOn
                    FlashSetting.TORCH -> Icons.Rounded.FlashlightOn
                }
                Icon(icon, contentDescription = "Flash mode", tint = Color.White)
            }
            IconButton(onClick = onSettings) {
                Icon(Icons.Rounded.Settings, contentDescription = "Capture settings", tint = Color.White)
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .navigationBarsPadding()
                .padding(end = 10.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Surface(shape = CircleShape, color = Color.Black.copy(alpha = 0.4f)) {
                IconButton(onClick = { onZoom(0.5f) }) {
                    Icon(Icons.Rounded.ZoomIn, contentDescription = "Zoom in", tint = Color.White)
                }
            }
            Surface(shape = CircleShape, color = Color.Black.copy(alpha = 0.4f)) {
                IconButton(onClick = { onZoom(-0.5f) }) {
                    Icon(Icons.Rounded.ZoomOut, contentDescription = "Zoom out", tint = Color.White)
                }
            }
            Text(
                text = String.format(java.util.Locale.US, "%.1fx", ui.camera.zoomRatio),
                color = Color.White.copy(alpha = 0.75f),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

@Composable
private fun AutoCaptureChip(enabled: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.clickable(onClick = onToggle),
        shape = RoundedCornerShape(50),
        color = if (enabled) Aurora.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.08f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (enabled) Aurora.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.18f),
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Rounded.AutoAwesome,
                contentDescription = null,
                tint = if (enabled) Aurora else Muted,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = if (enabled) "AUTO" else "MANUAL",
                color = if (enabled) Aurora else Muted,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun ShutterButton(
    capturing: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val inner by animateFloatAsState(
        targetValue = if (capturing) 0.55f else 1f,
        animationSpec = tween(160),
        label = "shutterInner",
    )
    Box(
        modifier = modifier
            .size(78.dp)
            .clip(CircleShape)
            .border(3.dp, Color.White.copy(alpha = if (enabled) 0.95f else 0.3f), CircleShape)
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size((56 * inner).dp)
                .clip(CircleShape)
                .background(
                    if (enabled) Color.White else Color.White.copy(alpha = 0.4f),
                ),
        )
    }
}

@Composable
private fun FinishControls(
    ui: CaptureUiState,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val canFinish = ui.capturedCount >= 3
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            shape = CircleShape,
            color = if (canFinish) Aurora.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.08f),
        ) {
            IconButton(onClick = onFinish, enabled = canFinish) {
                Icon(
                    Icons.Rounded.Check,
                    contentDescription = "Finish and stitch",
                    tint = if (canFinish) Aurora else Muted,
                )
            }
        }
        Text(
            text = if (canFinish) "Finish" else "${3 - ui.capturedCount} min",
            color = if (canFinish) Aurora else Muted,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}
