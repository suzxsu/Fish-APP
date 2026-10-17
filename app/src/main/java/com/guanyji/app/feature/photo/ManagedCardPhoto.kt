package com.guanyji.app.feature.photo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest
import coil.size.Scale
import com.guanyji.app.data.local.Photo
import com.guanyji.app.data.photo.ManagedPhotoFileStore
import com.guanyji.app.ui.theme.AquariumCardColors

/**
 * 列表卡片照片的统一加载层。
 *
 * 优先使用原图，原图缺失时回退缩略图，最后交给调用方展示占位图。
 * Coil 默认按 EXIF 方向解码原图；缩略图在生成时已经按 EXIF 旋转。
 */
@Composable
fun ManagedCardPhoto(
    photo: Photo?,
    contentDescription: String,
    modifier: Modifier = Modifier,
    placeholder: @Composable BoxScope.(label: String, loading: Boolean) -> Unit,
) {
    val context = LocalContext.current
    val fileStore = remember(context) { ManagedPhotoFileStore(context) }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val targetWidth = with(LocalDensity.current) { maxWidth.roundToPx() }.coerceAtLeast(1)
        val targetHeight = with(LocalDensity.current) { maxHeight.roundToPx() }.coerceAtLeast(1)
        val originalFile = remember(photo?.id, photo?.localPath, photo?.thumbnailPath, fileStore) {
            photo?.let { fileStore.fileFor(it, false) }
        }
        val thumbnailFile = remember(photo?.id, photo?.localPath, photo?.thumbnailPath, fileStore) {
            photo?.let { fileStore.fileFor(it, true) }
        }
        val file = originalFile ?: thumbnailFile

        if (file == null) {
            placeholder(if (photo == null) "暂无照片" else "照片缺失或无法读取", false)
        } else {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(context)
                    .data(file)
                    .size(targetWidth, targetHeight)
                    .scale(Scale.FILL)
                    .crossfade(180)
                    .build(),
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                alignment = BiasAlignment(0f, -0.35f),
                modifier = Modifier.matchParentSize(),
                loading = { placeholder("正在读取照片", true) },
                error = { placeholder("照片缺失或无法读取", false) },
                success = { SubcomposeAsyncImageContent() },
            )
        }

        // 顶部仅用于标签和编辑按钮可读性的淡渐变，不覆盖照片中部。
        CardTopScrim()
    }
}

@Composable
private fun BoxScope.CardTopScrim() {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .matchParentSize()
            .background(
                Brush.verticalGradient(
                    0f to AquariumCardColors.ImageTopScrim,
                    0.28f to Color.Transparent,
                    1f to Color.Transparent,
                ),
            ),
    )
}
