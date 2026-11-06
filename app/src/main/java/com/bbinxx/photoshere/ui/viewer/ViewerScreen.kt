package com.bbinxx.photoshere.ui.viewer

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bbinxx.photoshere.PhotoSphereApplication
import com.bbinxx.photoshere.core.model.PhotoSphereRecord
import com.bbinxx.photoshere.core.model.SphereMetadata
import com.bbinxx.photoshere.ui.theme.Aurora
import com.bbinxx.photoshere.ui.theme.Mist
import com.bbinxx.photoshere.ui.theme.Muted
import com.bbinxx.photoshere.viewer.SphereView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

@Composable
fun ViewerScreen(recordId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as PhotoSphereApplication
    val gallery = app.container.gallery
    val records by gallery.records.collectAsStateWithLifecycle()
    var refreshed by remember { mutableStateOf(false) }

    LaunchedEffect(recordId) {
        runCatching { gallery.refresh() }
        refreshed = true
    }

    val record = records.firstOrNull { it.id == recordId }
    if (record == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            if (!refreshed) {
                CircularProgressIndicator(color = Aurora)
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Rounded.Public,
                        contentDescription = null,
                        tint = Muted,
                        modifier = Modifier.size(56.dp),
                    )
                    Spacer(Modifier.size(16.dp))
                    Text("Sphere not found", color = Color.White)
                    Spacer(Modifier.size(16.dp))
                    Button(onClick = onBack) { Text("Back") }
                }
            }
        }
        return
    }

    ViewerContent(record = record, onBack = onBack)
}

@Composable
private fun ViewerContent(record: PhotoSphereRecord, onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as PhotoSphereApplication
    val exporter = app.container.exporter
    val gallery = app.container.gallery
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val sphereView = remember { SphereView(context) }
    val hasGyroscope = remember { sphereView.hasGyroscope() }
    var gyroEnabled by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }

    val bitmap by produceState<Bitmap?>(initialValue = null, key1 = record.filePath) {
        value = withContext(Dispatchers.IO) { decodeForViewer(record.filePath) }
    }

    LaunchedEffect(bitmap) {
        bitmap?.let(sphereView::load)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> sphereView.onResume()
                Lifecycle.Event.ON_PAUSE -> sphereView.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            sphereView.onPause()
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { sphereView },
            modifier = Modifier.fillMaxSize(),
        )

        if (bitmap == null) {
            CircularProgressIndicator(
                color = Aurora,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent),
                    ),
                )
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = record.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${record.width} × ${record.height} · equirectangular",
                    style = MaterialTheme.typography.labelSmall,
                    color = Mist.copy(alpha = 0.8f),
                )
            }
            IconButton(onClick = { showInfo = true }) {
                Icon(Icons.Rounded.Info, contentDescription = "Details", tint = Color.White)
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.82f)),
                    ),
                )
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Drag to look around · pinch to zoom · double tap to reset",
                style = MaterialTheme.typography.labelSmall,
                color = Mist.copy(alpha = 0.75f),
            )
            Spacer(Modifier.size(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ViewerAction(
                    icon = Icons.Rounded.Public,
                    label = if (gyroEnabled) "Gyro on" else "Gyro",
                    enabled = hasGyroscope,
                    active = gyroEnabled,
                    onClick = {
                        val next = !gyroEnabled
                        val applied = sphereView.setGyroscopeEnabled(next)
                        gyroEnabled = applied && next
                        if (!applied) {
                            scope.launch {
                                snackbarHostState.showSnackbar("No rotation sensor available")
                            }
                        }
                    },
                )
                ViewerAction(
                    icon = Icons.Rounded.RestartAlt,
                    label = "Reset",
                    onClick = { sphereView.resetView() },
                )
                ViewerAction(
                    icon = Icons.Rounded.Share,
                    label = "Share",
                    onClick = {
                        runCatching {
                            val uri = gallery.shareUri(record)
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "image/jpeg"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                putExtra(Intent.EXTRA_SUBJECT, record.title)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(
                                Intent.createChooser(intent, "Share photo sphere"),
                            )
                        }.onFailure {
                            scope.launch {
                                snackbarHostState.showSnackbar("Unable to share this sphere")
                            }
                        }
                    },
                )
                ViewerAction(
                    icon = Icons.Rounded.Download,
                    label = "Save",
                    onClick = {
                        scope.launch {
                            val uri = withContext(Dispatchers.IO) {
                                runCatching {
                                    exporter.exportToGallery(
                                        source = File(record.filePath),
                                        metadata = SphereMetadata(
                                            width = record.width,
                                            height = record.height,
                                        ),
                                        displayName = record.title,
                                    )
                                }.getOrNull()
                            }
                            snackbarHostState.showSnackbar(
                                if (uri != null) {
                                    "Saved to Pictures/PhotoSphere"
                                } else {
                                    "Export failed"
                                },
                            )
                        }
                    },
                )
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 120.dp, start = 16.dp, end = 16.dp),
        )
    }

    if (showInfo) {
        AlertDialog(
            onDismissRequest = { showInfo = false },
            title = { Text("Sphere details") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(record.title, fontWeight = FontWeight.SemiBold)
                    Text("Resolution: ${record.width} × ${record.height}")
                    Text(
                        "File size: ${
                            String.format(
                                Locale.US,
                                "%.2f MB",
                                record.sizeBytes / (1024f * 1024f),
                            )
                        }",
                    )
                    Text(
                        "Created: ${
                            SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault())
                                .format(Date(record.createdAtMs))
                        }",
                    )
                    Text("Projection: equirectangular with GPano XMP metadata")
                    Text(
                        if (record.sessionId != null) {
                            "Source: captured in PhotoSphere"
                        } else {
                            "Source: imported JPEG"
                        },
                    )
                    Text(
                        "Rendering uses a sensor-pose spherical projection. Lens distortion " +
                            "correction and feature-based pose refinement are not applied.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Muted,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showInfo = false }) { Text("Close") }
            },
        )
    }
}

@Composable
private fun ViewerAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean = true,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            shape = CircleShape,
            color = if (active) Aurora.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.1f),
        ) {
            IconButton(onClick = onClick, enabled = enabled) {
                Icon(
                    icon,
                    contentDescription = label,
                    tint = when {
                        !enabled -> Muted.copy(alpha = 0.4f)
                        active -> Aurora
                        else -> Color.White
                    },
                )
            }
        }
        Spacer(Modifier.size(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (active) Aurora else Mist.copy(alpha = 0.8f),
        )
    }
}

private fun decodeForViewer(path: String): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    val maxTexture = 4096
    while (max(bounds.outWidth / sample, bounds.outHeight / sample) > maxTexture) {
        sample *= 2
    }
    return runCatching {
        BitmapFactory.decodeFile(
            path,
            BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            },
        )
    }.getOrNull()
}
