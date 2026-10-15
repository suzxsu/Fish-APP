package com.guanyji.app.feature.fish

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest
import com.guanyji.app.data.local.Photo
import com.guanyji.app.data.photo.ManagedPhotoFileStore
import com.guanyji.app.feature.overview.FishIcons

/**
 * 水族主题鱼只卡牌。
 *
 * 结构：外层卡牌容器 → 图片区（照片 + 遮罩 + 状态标签）→ 信息区（名称 / 鱼种 / 鱼缸 / 入缸天数 / 体长 / 今日喂食）。
 * 所有业务字段来自 [FishCardUiModel]，卡片本身不访问数据库，也不包含任何虚构等级或稀有度。
 */
@Composable
internal fun FishTradingCard(
    model: FishCardUiModel,
    photo: Photo?,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
    animateEntrance: Boolean = true,
    onEntranceShown: (String) -> Unit = {},
) {
    val palette = remember(model.status) { model.status.cardPalette() }
    var entered by remember(model.id) { mutableStateOf(!animateEntrance) }
    val entrance by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "fishCardEntrance",
    )
    LaunchedEffect(model.id) {
        if (!entered) {
            entered = true
            onEntranceShown(model.id)
        }
    }

    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.972f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "fishCardPress",
    )
    val borderAlpha by animateFloatAsState(
        targetValue = if (pressed) 0.95f else 0.45f,
        animationSpec = tween(durationMillis = 160),
        label = "fishCardBorder",
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
                alpha = entrance
                translationY = (1f - entrance) * 16.dp.toPx()
            }
            .shadow(
                elevation = FishCardTokens.CardElevation,
                shape = FishCardTokens.CardShape,
                clip = false,
                ambientColor = FishCardTokens.ShadowTint,
                spotColor = FishCardTokens.ShadowTint,
            )
            .clip(FishCardTokens.CardShape)
            .background(Brush.verticalGradient(listOf(FishCardTokens.CardTop, FishCardTokens.CardBottom)))
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    listOf(
                        palette.accent.copy(alpha = borderAlpha),
                        FishCardTokens.Border.copy(alpha = borderAlpha * 0.35f),
                        palette.accent.copy(alpha = borderAlpha * 0.55f),
                    ),
                ),
                shape = FishCardTokens.CardShape,
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClickLabel = "查看${model.name}详情",
                onClick = onClick,
            )
            .testTag("fish-card-${model.id}"),
    ) {
        Column {
            FishCardImage(photo = photo, name = model.name, statusLabel = model.statusLabel, palette = palette, onEdit = onEdit)
            FishCardInfo(model = model, palette = palette)
        }
    }
}

/** 照片区：优先使用数据库关联的本地照片，无照片时展示水族风格占位图。 */
@Composable
private fun FishCardImage(
    photo: Photo?,
    name: String,
    statusLabel: String,
    palette: FishCardStatusPalette,
    onEdit: () -> Unit,
) {
    val context = LocalContext.current
    val file = remember(photo?.id, photo?.localPath, photo?.thumbnailPath) {
        photo?.let { ManagedPhotoFileStore(context).fileFor(it, true) }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(FishCardTokens.ImageAspectRatio)
            .clip(FishCardTokens.ImageShape),
    ) {
        if (file == null) {
            FishCardPhotoPlaceholder("暂无照片")
        } else {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(context).data(file).crossfade(180).build(),
                contentDescription = "$name 的照片",
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
                loading = { FishCardPhotoPlaceholder("正在读取照片") },
                error = { FishCardPhotoPlaceholder("照片缺失或无法读取") },
                success = { SubcomposeAsyncImageContent() },
            )
        }
        // 渐变遮罩：让图片与信息区自然衔接，同时保证状态标签可读。
        Box(
            Modifier.matchParentSize().background(
                Brush.verticalGradient(
                    0.0f to Color(0x26102A44),
                    0.55f to Color(0x4D0C2540),
                    1.0f to FishCardTokens.CardTop,
                ),
            ),
        )
        // 光泽：左上角斜向反光，纯装饰。
        Box(
            Modifier.matchParentSize().background(
                Brush.linearGradient(
                    colors = listOf(FishCardTokens.Gloss, Color.Transparent),
                    start = Offset.Zero,
                    end = Offset(320f, 460f),
                ),
            ),
        )
        FishCardBubbles(Modifier.matchParentSize())
        FishCardStatus(label = statusLabel, palette = palette, modifier = Modifier.align(Alignment.TopStart).padding(start = 8.dp, top = 8.dp))
        FishCardEditButton(name, onEdit, Modifier.align(Alignment.TopEnd))
    }
}

/** 状态标签：展示在养、隔离、已转出、已故、已归档等真实状态。 */
@Composable
/** 状态标签：展示在养、隔离、已转出、已故、已归档等真实状态（列表卡与详情页共用）。 */
internal fun FishCardStatus(label: String, palette: FishCardStatusPalette, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(FishCardTokens.ChipShape)
            .background(palette.chipBackground)
            .border(1.dp, palette.accent.copy(alpha = 0.5f), FishCardTokens.ChipShape)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(palette.accent))
        Text(
            text = label,
            color = palette.chipText,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}

@Composable
private fun FishCardEditButton(name: String, onEdit: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(onClick = onEdit)
            .semantics {
                contentDescription = "编辑$name"
                role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(FishCardTokens.ControlSurface)
                .border(1.dp, FishCardTokens.Border.copy(alpha = 0.45f), CircleShape),
        )
        Icon(
            imageVector = Icons.Outlined.Edit,
            contentDescription = null,
            tint = Color(0xFFEAF4FB),
            modifier = Modifier.size(16.dp),
        )
    }
}

/** 信息区：名称、鱼种、鱼缸、入缸天数、最近体长与今日喂食摘要。 */
@Composable
private fun FishCardInfo(model: FishCardUiModel, palette: FishCardStatusPalette) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(FishCardTokens.PanelTop, FishCardTokens.PanelBottom))),
    ) {
        FishCardWaterLines(palette.accent, Modifier.matchParentSize())
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            // 顶部金色装饰细线，仅作装饰。
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(FishCardTokens.Gold.copy(alpha = 0.9f), FishCardTokens.Gold.copy(alpha = 0f)),
                        ),
                    ),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = model.name,
                    color = FishCardTokens.CardInk,
                    fontSize = 16.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = FishIcons.ArrowRight,
                    contentDescription = null,
                    tint = FishCardTokens.CardSecondaryInk,
                    modifier = Modifier.size(18.dp),
                )
            }
            FishCardLine(text = model.speciesLine, color = FishCardTokens.CardSecondaryInk)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(
                    imageVector = FishIcons.Aquarium,
                    contentDescription = null,
                    tint = FishCardTokens.CardSecondaryInk,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = model.tankLine,
                    color = FishCardTokens.CardSecondaryInk,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FishCardMetric(icon = FishIcons.Calendar, value = model.daysLabel, modifier = Modifier.weight(1f))
                FishCardMetric(icon = FishIcons.Ruler, value = model.lengthLabel, modifier = Modifier.weight(1f))
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(
                    imageVector = FishIcons.Fish,
                    contentDescription = null,
                    tint = FishCardTokens.CardMetricInk.copy(alpha = 0.85f),
            modifier = Modifier.size(14.dp),
                )
                Text(
                    text = "今日喂食",
                    color = FishCardTokens.CardSecondaryInk,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    maxLines = 1,
                )
                Text(
                    text = model.feedingLabel,
                    color = FishCardTokens.CardMetricInk,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun FishCardLine(text: String, color: Color) {
    Text(
        text = text,
        color = color,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun FishCardMetric(icon: ImageVector, value: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = FishCardTokens.CardMetricInk.copy(alpha = 0.85f),
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = value,
            color = FishCardTokens.CardMetricInk,
            fontSize = 11.sp,
            lineHeight = 15.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 无照片 / 读取中 / 读取失败时的占位层，保持卡牌视觉完整。 */
@Composable
private fun FishCardPhotoPlaceholder(label: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF123C60), Color(0xFF0B2A47)))),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(
                imageVector = FishIcons.Fish,
                contentDescription = null,
                tint = Color(0xB8CFE6F7),
                modifier = Modifier.size(30.dp),
            )
            Text(text = label, color = Color(0xFFE6F3FD), fontSize = 11.sp)
        }
    }
}

/** 气泡装饰：固定位置、静态绘制，不产生持续动画。 */
@Composable
private fun FishCardBubbles(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val spots = listOf(
            Triple(0.80f, 0.34f, 0.030f),
            Triple(0.89f, 0.21f, 0.017f),
            Triple(0.71f, 0.16f, 0.012f),
            Triple(0.94f, 0.47f, 0.021f),
        )
        spots.forEach { (fx, fy, fr) ->
            val radius = size.minDimension * fr
            val center = Offset(size.width * fx, size.height * fy)
            drawCircle(color = Color(0x33FFFFFF), radius = radius, center = center)
            drawCircle(color = Color(0x59FFFFFF), radius = radius, center = center, style = Stroke(width = radius * 0.4f))
        }
    }
}

/** 信息区底部的水波装饰线，纯装饰，不参与任何数据表达。 */
@Composable
private fun FishCardWaterLines(accent: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val waveTop = Path().apply {
            moveTo(0f, size.height * 0.62f)
            cubicTo(size.width * 0.28f, size.height * 0.44f, size.width * 0.58f, size.height * 0.80f, size.width, size.height * 0.56f)
        }
        val waveBottom = Path().apply {
            moveTo(0f, size.height * 0.80f)
            cubicTo(size.width * 0.30f, size.height * 0.62f, size.width * 0.62f, size.height * 0.96f, size.width, size.height * 0.74f)
        }
        drawPath(waveTop, color = accent.copy(alpha = 0.14f), style = Stroke(width = 1.4f * density))
        drawPath(waveBottom, color = accent.copy(alpha = 0.09f), style = Stroke(width = 1.4f * density))
    }
}
