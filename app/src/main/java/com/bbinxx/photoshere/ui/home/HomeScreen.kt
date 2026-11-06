package com.bbinxx.photoshere.ui.home

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bbinxx.photoshere.PhotoSphereApplication
import com.bbinxx.photoshere.core.model.PhotoSphereRecord
import com.bbinxx.photoshere.core.model.SessionMetadata
import com.bbinxx.photoshere.ui.components.GradientBackground
import com.bbinxx.photoshere.ui.theme.Amber
import com.bbinxx.photoshere.ui.theme.Aurora
import com.bbinxx.photoshere.ui.theme.InkSurface
import com.bbinxx.photoshere.ui.theme.Mist
import com.bbinxx.photoshere.ui.theme.Muted
import com.bbinxx.photoshere.ui.theme.Sky
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

@Composable
fun HomeScreen(
    onNewCapture: () -> Unit,
    onResumeSession: (String) -> Unit,
    onOpenGallery: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSphere: (String) -> Unit,
    onAbout: () -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as PhotoSphereApplication
    val container = app.container
    val records by container.gallery.records.collectAsStateWithLifecycle()
    var sessions by remember { mutableStateOf<List<SessionMetadata>>(emptyList()) }

    LaunchedEffect(Unit) {
        sessions = withContext(Dispatchers.IO) {
            runCatching { container.gallery.refresh() }
            container.sessionStore.listSessions()
        }
    }

    val resumable = remember(sessions, records) {
        sessions.filter { session ->
            val accepted = session.images.count { it.accepted }
            accepted > 0 && records.none { it.sessionId == session.id }
        }
    }
    val recent = records.take(8)

    GradientBackground {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding(),
            contentPadding = PaddingValues(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(start = 20.dp, end = 8.dp, top = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "PhotoSphere",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                        Text(
                            text = "360° capture · offline · private",
                            style = MaterialTheme.typography.labelMedium,
                            color = Muted,
                        )
                    }
                    IconButton(onClick = onOpenGallery) {
                        Icon(
                            Icons.Rounded.PhotoLibrary,
                            contentDescription = "Gallery",
                            tint = Mist,
                        )
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Rounded.Settings, contentDescription = "Settings", tint = Mist)
                    }
                }
            }

            item {
                HeroCard(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    onNewCapture = onNewCapture,
                    onOpenGallery = onOpenGallery,
                )
            }

            item {
                StatsRow(
                    spheres = records.size,
                    pending = resumable.size,
                    totalBytes = records.sumOf { it.sizeBytes },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            if (resumable.isNotEmpty()) {
                item {
                    SectionHeading(
                        title = "Continue capturing",
                        actionLabel = null,
                        onAction = null,
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                }
                items(resumable, key = { it.id }) { session ->
                    ResumeRow(
                        session = session,
                        onResume = { onResumeSession(session.id) },
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }

            item {
                SectionHeading(
                    title = "Recent spheres",
                    actionLabel = if (records.isNotEmpty()) "See all" else null,
                    onAction = onOpenGallery,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }

            if (recent.isEmpty()) {
                item {
                    EmptyRecent(
                        onNewCapture = onNewCapture,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            } else {
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(recent, key = { it.id }) { record ->
                            RecentCard(record = record, onClick = { onOpenSphere(record.id) })
                        }
                    }
                }
            }

            item {
                OfflineNote(modifier = Modifier.padding(horizontal = 16.dp))
            }
            item {
                TextButton(
                    onClick = onAbout,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("About, licenses and limitations", color = Muted)
                }
            }
        }
    }
}

@Composable
private fun HeroCard(
    modifier: Modifier = Modifier,
    onNewCapture: () -> Unit,
    onOpenGallery: () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        Aurora.copy(alpha = 0.24f),
                        Sky.copy(alpha = 0.12f),
                        Color.Transparent,
                    ),
                ),
            )
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.1f),
                shape = RoundedCornerShape(28.dp),
            )
            .padding(22.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Aurora.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.Public,
                        contentDescription = null,
                        tint = Aurora,
                        modifier = Modifier.size(28.dp),
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(
                        text = "Capture your surroundings",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "Guided 360° sphere in about a minute",
                        style = MaterialTheme.typography.bodySmall,
                        color = Muted,
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            Text(
                text = "Follow the moving dot, hold steady, and PhotoSphere captures overlapping " +
                    "photos automatically before stitching them into an equirectangular JPEG.",
                style = MaterialTheme.typography.bodyMedium,
                color = Mist.copy(alpha = 0.9f),
            )
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onNewCapture, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("New photo sphere")
                }
                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color.White.copy(alpha = 0.08f),
                    modifier = Modifier.clickable(onClick = onOpenGallery),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Rounded.PhotoLibrary,
                            contentDescription = null,
                            tint = Mist,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Gallery", color = Mist)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatsRow(spheres: Int, pending: Int, totalBytes: Long, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        StatCard("Spheres", spheres.toString(), Modifier.weight(1f))
        StatCard("Unfinished", pending.toString(), Modifier.weight(1f))
        StatCard("Storage", formatSize(totalBytes), Modifier.weight(1f))
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = Color.White.copy(alpha = 0.05f),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = Aurora,
                fontWeight = FontWeight.SemiBold,
            )
            Text(label, style = MaterialTheme.typography.labelSmall, color = Muted)
        }
    }
}

@Composable
private fun SectionHeading(
    title: String,
    actionLabel: String?,
    onAction: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            modifier = Modifier.weight(1f),
        )
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) {
                Text(actionLabel, color = Sky)
                Icon(
                    Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = Sky,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun ResumeRow(
    session: SessionMetadata,
    onResume: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val captured = session.images.count { it.accepted }
    val total = session.targets.size.coerceAtLeast(1)
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = Amber.copy(alpha = 0.08f),
        onClick = onResume,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Amber.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    tint = Amber,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = sessionLabel(session),
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "$captured of $total positions · tap to continue",
                    style = MaterialTheme.typography.labelSmall,
                    color = Amber,
                )
            }
            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = Muted)
        }
    }
}

@Composable
private fun RecentCard(record: PhotoSphereRecord, onClick: () -> Unit) {
    val bitmap by produceState<Bitmap?>(initialValue = null, key1 = record.filePath) {
        value = withContext(Dispatchers.IO) { decodeThumbnail(record.filePath) }
    }
    Column(
        modifier = Modifier
            .width(176.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(118.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(InkSurface),
        ) {
            val image = bitmap
            if (image != null) {
                Image(
                    bitmap = image.asImageBitmap(),
                    contentDescription = record.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    Icons.Rounded.Public,
                    contentDescription = null,
                    tint = Muted,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(32.dp),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = record.title,
            style = MaterialTheme.typography.bodySmall,
            color = Mist,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = SimpleDateFormat("d MMM yyyy", Locale.getDefault())
                .format(Date(record.createdAtMs)),
            style = MaterialTheme.typography.labelSmall,
            color = Muted,
        )
    }
}

@Composable
private fun EmptyRecent(onNewCapture: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = Color.White.copy(alpha = 0.04f),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Your spheres will appear here",
                style = MaterialTheme.typography.bodyMedium,
                color = Mist,
            )
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onNewCapture) { Text("Capture the first one", color = Aurora) }
        }
    }
}

@Composable
private fun OfflineNote(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Rounded.CloudOff,
            contentDescription = null,
            tint = Muted,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "Photos stay on this device. No account, no upload, no analytics.",
            style = MaterialTheme.typography.labelSmall,
            color = Muted,
        )
    }
}

private fun sessionLabel(session: SessionMetadata): String {
    return "Photo Sphere " + SimpleDateFormat("d MMM, HH:mm", Locale.getDefault())
        .format(Date(session.createdAtMs))
}

private fun formatSize(bytes: Long): String {
    val mb = bytes / (1024f * 1024f)
    return if (mb >= 1024f) {
        String.format(Locale.US, "%.1f GB", mb / 1024f)
    } else {
        String.format(Locale.US, "%.1f MB", mb)
    }
}

private fun decodeThumbnail(path: String): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    val target = 480
    while (max(bounds.outWidth / sample, bounds.outHeight / sample) > target * 2) {
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
