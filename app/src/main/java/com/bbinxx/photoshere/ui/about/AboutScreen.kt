package com.bbinxx.photoshere.ui.about

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bbinxx.photoshere.ui.components.GradientBackground
import com.bbinxx.photoshere.ui.theme.Amber
import com.bbinxx.photoshere.ui.theme.Aurora
import com.bbinxx.photoshere.ui.theme.Mist
import com.bbinxx.photoshere.ui.theme.Muted

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val version = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull().orEmpty()

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
                Column {
                    Text(
                        text = "About",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (version.isNotBlank()) {
                        Text(
                            text = "Version $version",
                            style = MaterialTheme.typography.labelSmall,
                            color = Muted,
                        )
                    }
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.Public,
                            contentDescription = null,
                            tint = Aurora,
                            modifier = Modifier.height(40.dp),
                        )
                        Spacer(Modifier.height(12.dp))
                        Column(Modifier.padding(start = 12.dp)) {
                            Text(
                                text = "PhotoSphere",
                                style = MaterialTheme.typography.headlineSmall,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = "A private, fully offline 360° camera",
                                style = MaterialTheme.typography.bodySmall,
                                color = Mist,
                            )
                        }
                    }
                }

                item {
                    AboutSection("How it works") {
                        Text(
                            "PhotoSphere reads the rotation vector sensor to know where the " +
                                "camera is pointing. A spherical target plan is generated from the " +
                                "measured field of view, and the moving dot guides you between " +
                                "overlapping positions. Each accepted photo stores its orientation. " +
                                "Stitching projects every photo onto an equirectangular canvas with " +
                                "sensor poses, blends overlaps with edge feathering, normalises " +
                                "exposure, fills gaps, and injects GPano XMP metadata so viewers " +
                                "recognise the image as a photo sphere.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Mist,
                        )
                    }
                }

                item {
                    AboutSection("Accuracy and limitations") {
                        Bullet(
                            "Camera pose comes from the fused rotation vector. Gyroscope drift or " +
                                "magnetic disturbance can shift frames; there is no feature-based " +
                                "bundle adjustment yet.",
                        )
                        Bullet(
                            "Lens distortion is not corrected unless the device reports a usable " +
                                "calibration. Wide-angle edges may show seams.",
                        )
                        Bullet(
                            "Moving subjects can ghost because no temporal de-ghosting is applied.",
                        )
                        Bullet(
                            "A working rotation sensor is required. The app falls back to " +
                                "accelerometer and magnetometer data, but accuracy is reduced.",
                        )
                        Bullet(
                            "Stitching time and output quality depend on the device CPU and the " +
                                "selected result width.",
                        )
                    }
                }

                item {
                    AboutSection("Privacy") {
                        Text(
                            "Photos, sessions and gallery indexes are stored in the app's private " +
                                "storage and, when you ask, exported to your device gallery. There is " +
                                "no account, analytics, telemetry or network permission. Nothing " +
                                "leaves the device unless you explicitly share or export it.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Mist,
                        )
                    }
                }

                item {
                    AboutSection("Open source") {
                        Text(
                            "Built with Kotlin, Jetpack Compose, AndroidX CameraX, Kotlin " +
                                "Coroutines and OpenCV. These libraries are used under the Apache " +
                                "License 2.0. OpenCV is provided by the OpenCV project and is " +
                                "licensed under Apache 2.0. Full license texts are available in " +
                                "the project repository's THIRD_PARTY_LICENSES.md.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Mist,
                        )
                    }
                }

                item {
                    Surface(
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
                        color = Amber.copy(alpha = 0.1f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = "No feature is claimed as working until it has been verified on " +
                                "a physical device. Camera, sensor guidance and stitching should be " +
                                "tested on your specific hardware.",
                            modifier = Modifier.padding(14.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = Amber,
                        )
                    }
                }
                item { Spacer(Modifier.height(12.dp)) }
            }
        }
    }
}

@Composable
private fun AboutSection(title: String, content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
        color = Color.White.copy(alpha = 0.05f),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = Aurora,
                fontWeight = FontWeight.SemiBold,
            )
            content()
        }
    }
}

@Composable
private fun Bullet(text: String) {
    Row {
        Text("•", color = Aurora, style = MaterialTheme.typography.bodyMedium)
        Text(
            text = text,
            modifier = Modifier.padding(start = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = Mist,
        )
    }
}
