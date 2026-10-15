package com.guanyji.app.core.designsystem

import androidx.activity.compose.BackHandler
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue

@Composable
fun rememberLeaveFormHandler(hasUnsavedChanges: Boolean, onLeave: () -> Unit): () -> Unit {
    var confirmDiscard by remember { mutableStateOf(false) }
    val currentOnLeave by rememberUpdatedState(onLeave)
    BackHandler(enabled = hasUnsavedChanges) { confirmDiscard = true }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("放弃更改？") },
            text = { Text("当前表单有尚未保存的内容。") },
            confirmButton = { TextButton(onClick = { confirmDiscard = false; currentOnLeave() }) { Text("放弃更改") } },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("继续编辑") } },
        )
    }

    return remember(hasUnsavedChanges) {
        { if (hasUnsavedChanges) confirmDiscard = true else currentOnLeave() }
    }
}
