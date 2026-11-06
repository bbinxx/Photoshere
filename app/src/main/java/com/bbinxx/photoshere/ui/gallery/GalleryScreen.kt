package com.bbinxx.photoshere.ui.gallery

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bbinxx.photoshere.PhotoSphereApplication
import com.bbinxx.photoshere.core.model.PhotoSphereRecord
import com.bbinxx.photoshere.ui.components.GradientBackground
import com.bbinxx.photoshere.ui.theme.Aurora
import com.bbinxx.photoshere.ui.theme.Coral
import com.bbinxx.photoshere.ui.theme.InkSurface
import com.bbinxx.photoshere.ui.theme.Muted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

@Composable
fun GalleryScreen(
    onBack: () -> Unit,
    onOpenSphere: (String) -> Unit,
    onNewCapture: () -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as PhotoSphereApplication
    val viewModel: GalleryViewModel = viewModel(
        factory = GalleryViewModel.Factory(app),
    )
    val records by viewModel.records.collectAsStateWithLifecycle()
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var pendingDelete by remember { mutableStateOf<PhotoSphereRecord?>(null) }
    var pendingRename by remember { mutableStateOf<PhotoSphereRecord?>(null) }
    var pendingExport by remember { mutableStateOf<PhotoSphereRecord?>(null) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(viewModel::import) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("image/jpeg"),
    ) { uri ->
        val record = pendingExport
        pendingExport = null
        if (uri != null && record != null) viewModel.exportToUri(record, uri)
    }

    LaunchedEffect(ui.message) {
        ui.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeFeedback()
        }
    }
    LaunchedEffect(ui.error) {
        ui.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeFeedback()
        }
    }

    GradientBackground {
        Box(Modifier.fillMaxSize()) {
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
                            text = "Gallery",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "${records.size} photo sphere${if (records.size == 1) "" else "s"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Muted,
                        )
                    }
                    IconButton(onClick = { importLauncher.launch(arrayOf("image/*")) }) {
                        Icon(
                            Icons.Rounded.AddPhotoAlternate,
                            contentDescription = "Import sphere",
                            tint = Aurora,
                        )
                    }
                }

                if (records.isEmpty()) {
                    EmptyGallery(
                        onNewCapture = onNewCapture,
                        onImport = { importLauncher.launch(arrayOf("image/*")) },
                    )
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(150.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .navigationBarsPadding(),
                        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(records, key = { it.id }) { record ->
                            GalleryCard(
                                record = record,
                                onClick = { onOpenSphere(record.id) },
                                onShare = {
                                    runCatching {
                                        val uri = viewModel.shareUri(record)
                                        val intent = Intent(Intent.ACTION_SEND).apply {
                                            type = "image/jpeg"
                                            putExtra(Intent.EXTRA_STREAM, uri)
                                            putExtra(Intent.EXTRA_SUBJECT, record.title)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(
                                            Intent.createChooser(intent, "Share photo sphere"),
                                        )
                                    }
                                },
                                onSaveToPhotos = { viewModel.exportToPhotos(record) },
                                onExportFile = {
                                    pendingExport = record
                                    exportLauncher.launch("${record.title}.jpg")
                                },
                                onRename = { pendingRename = record },
                                onDelete = { pendingDelete = record },
                            )
                        }
                    }
                }
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(16.dp),
            )
        }
    }

    pendingDelete?.let { record ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete sphere?") },
            text = { Text("“${record.title}” will be permanently removed from this device.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.delete(record)
                        pendingDelete = null
                    },
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            },
        )
    }

    pendingRename?.let { record ->
        RenameDialog(
            initial = record.title,
            onDismiss = { pendingRename = null },
            onConfirm = { title ->
                viewModel.rename(record, title)
                pendingRename = null
            },
        )
    }
}

@Composable
private fun EmptyGallery(onNewCapture: () -> Unit, onImport: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Rounded.PhotoLibrary,
            contentDescription = null,
            tint = Aurora.copy(alpha = 0.7f),
            modifier = Modifier.size(72.dp),
        )
        Spacer(Modifier.height(20.dp))
        Text(
            text = "No spheres yet",
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Capture your first 360° photo sphere or import an existing equirectangular JPEG.",
            style = MaterialTheme.typography.bodyMedium,
            color = Muted,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onNewCapture, modifier = Modifier.fillMaxWidth()) {
            Text("Start a photo sphere")
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) {
            Text("Import JPEG")
        }
    }
}

@Composable
private fun GalleryCard(
    record: PhotoSphereRecord,
    onClick: () -> Unit,
    onShare: () -> Unit,
    onSaveToPhotos: () -> Unit,
    onExportFile: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val thumbnail by produceState<Bitmap?>(initialValue = null, key1 = record.filePath) {
        value = withContext(Dispatchers.IO) { decodeThumbnail(record.filePath) }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(20.dp))
            .background(InkSurface)
            .clickable(onClick = onClick),
    ) {
        val bitmap = thumbnail
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = record.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                Icons.Rounded.PhotoLibrary,
                contentDescription = null,
                tint = Muted,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(40.dp),
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.45f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.82f),
                    ),
                ),
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 12.dp, end = 48.dp, bottom = 10.dp),
        ) {
            Text(
                text = record.title,
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = formatMeta(record),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(Modifier.align(Alignment.TopEnd)) {
            IconButton(
                onClick = { menuOpen = true },
                modifier = Modifier
                    .padding(6.dp)
                    .size(32.dp)
                    .background(Color.Black.copy(alpha = 0.45f), CircleShape),
            ) {
                Icon(
                    Icons.Rounded.MoreVert,
                    contentDescription = "Sphere actions",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Open") },
                    leadingIcon = { Icon(Icons.Rounded.Visibility, contentDescription = null) },
                    onClick = {
                        menuOpen = false
                        onClick()
                    },
                )
                DropdownMenuItem(
                    text = { Text("Share") },
                    leadingIcon = { Icon(Icons.Rounded.Share, contentDescription = null) },
                    onClick = {
                        menuOpen = false
                        onShare()
                    },
                )
                DropdownMenuItem(
                    text = { Text("Save to Photos") },
                    leadingIcon = { Icon(Icons.Rounded.Download, contentDescription = null) },
                    onClick = {
                        menuOpen = false
                        onSaveToPhotos()
                    },
                )
                DropdownMenuItem(
                    text = { Text("Export file…") },
                    leadingIcon = { Icon(Icons.Rounded.Download, contentDescription = null) },
                    onClick = {
                        menuOpen = false
                        onExportFile()
                    },
                )
                DropdownMenuItem(
                    text = { Text("Rename") },
                    leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                    onClick = {
                        menuOpen = false
                        onRename()
                    },
                )
                DropdownMenuItem(
                    text = { Text("Delete") },
                    leadingIcon = {
                        Icon(Icons.Rounded.Delete, contentDescription = null, tint = Coral)
                    },
                    onClick = {
                        menuOpen = false
                        onDelete()
                    },
                )
            }
        }
    }
}

@Composable
private fun RenameDialog(
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename sphere") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                label = { Text("Title") },
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }, enabled = text.isNotBlank()) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

private fun decodeThumbnail(path: String): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    val target = 640
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

private fun formatMeta(record: PhotoSphereRecord): String {
    val date = SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(record.createdAtMs))
    val size = record.sizeBytes / (1024f * 1024f)
    return "$date · ${record.width}×${record.height} · ${String.format(Locale.US, "%.1f MB", size)}"
}
