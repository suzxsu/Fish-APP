package com.guanyji.app.feature.photo

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import com.guanyji.app.data.local.Photo
import com.guanyji.app.data.photo.ManagedPhotoFileStore

@Composable
fun PhotoPickerField(
    viewModel: PhotoViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showPreview by remember(state.photo?.id) { mutableStateOf(false) }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }

    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.import(uri)
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { captured ->
        (state.cameraCaptureUri ?: cameraUri)?.let { viewModel.cameraResult(it, captured) }
        cameraUri = null
    }
    LaunchedEffect(state.cameraCaptureUri) {
        state.cameraCaptureUri?.let { uri ->
            cameraUri = uri
            try {
                camera.launch(uri)
            } catch (_: Exception) {
                viewModel.cameraUnavailable(uri)
                cameraUri = null
            }
        }
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ManagedPhotoImage(
            photo = state.photo,
            thumbnail = true,
            modifier = Modifier.fillMaxWidth().aspectRatio(1.7f),
            onClick = { if (state.photo != null) showPreview = true },
        )
        val interactionBlocked = state.isBusy || state.cameraCaptureUri != null
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(
                enabled = !interactionBlocked,
                onClick = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            ) { Text(if (state.photo == null) "选择照片" else "替换照片") }
            OutlinedButton(enabled = !interactionBlocked, onClick = viewModel::prepareCameraCapture) { Text("拍照") }
            if (state.photo != null) TextButton(enabled = !interactionBlocked, onClick = viewModel::clearSelection) { Text("取消选择") }
            if (state.isBusy) CircularProgressIndicator(modifier = Modifier.padding(start = 4.dp), strokeWidth = 2.dp)
        }
        state.message?.let { message ->
            Text(
                text = message.label(),
                color = if (message == PhotoMessage.saved || message == PhotoMessage.selectionCancelled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }

    if (showPreview && state.photo != null) {
        Dialog(onDismissRequest = { showPreview = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface,
            ) {
                Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.End) {
                    ManagedPhotoImage(
                        photo = state.photo,
                        thumbnail = false,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 640.dp).aspectRatio(1f),
                    )
                    TextButton(onClick = { showPreview = false }) { Text("关闭") }
                }
            }
        }
    }
}

@Composable
fun ManagedPhotoImage(
    photo: Photo?,
    thumbnail: Boolean,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val file = remember(context, photo?.id, photo?.localPath, photo?.thumbnailPath, thumbnail) {
        photo?.let { ManagedPhotoFileStore(context).fileFor(it, thumbnail) }
    }
    if (file == null) {
        PhotoPlaceholder(modifier, if (photo == null) "尚未选择照片" else "照片路径无效")
        return
    }
    SubcomposeAsyncImage(
        model = file,
        contentDescription = "鱼只照片",
        contentScale = if (thumbnail) ContentScale.Crop else ContentScale.Fit,
        modifier = modifier.then(if (onClick == null) Modifier else Modifier.clickable(onClick = onClick)),
        loading = { PhotoPlaceholder(Modifier.fillMaxSize(), "正在读取照片", loading = true) },
        error = { PhotoPlaceholder(Modifier.fillMaxSize(), "照片缺失或无法读取") },
        success = { SubcomposeAsyncImageContent() },
    )
}

@Composable
private fun PhotoPlaceholder(modifier: Modifier, label: String, loading: Boolean = false) {
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant).padding(12.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) CircularProgressIndicator(strokeWidth = 2.dp)
        else Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
    }
}

internal fun PhotoMessage.label(): String = when (this) {
    PhotoMessage.saved -> "照片已保存到本机"
    PhotoMessage.selectionCancelled -> "已取消拍摄，原选择保留"
    PhotoMessage.cameraUnavailable -> "无法打开相机，请检查设备相机应用或权限"
    PhotoMessage.unreadable -> "无法读取所选图片，请重新选择"
    PhotoMessage.invalidImage -> "文件不是可读取的图片"
    PhotoMessage.noSpace -> "设备空间不足，照片未保存"
    PhotoMessage.accessDenied -> "无法访问该图片，请重新选择并授权"
    PhotoMessage.saveFailed -> "照片保存失败，原选择仍保留"
}
