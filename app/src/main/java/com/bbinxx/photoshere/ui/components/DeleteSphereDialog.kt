package com.bbinxx.photoshere.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

@Composable
fun DeleteSphereDialog(title: String, deleting: Boolean = false, error: String? = null,
    onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = { if (!deleting) onDismiss() },
        title = { Text("Delete sphere?") },
        text = { Text(error ?: "Remove “$title” from the app? Original capture photos and exported copies are kept.") },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !deleting) {
                Text(if (deleting) "Deleting…" else "Delete")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !deleting) { Text("Cancel") } },
    )
}
