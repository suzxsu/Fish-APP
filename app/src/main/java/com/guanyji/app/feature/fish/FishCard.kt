package com.guanyji.app.feature.fish

import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.guanyji.app.core.designsystem.AquariumCardShell
import com.guanyji.app.core.designsystem.AquariumTypeBadge
import com.guanyji.app.data.local.FishStatus
import com.guanyji.app.data.local.Photo
import com.guanyji.app.feature.overview.FishIcons
import com.guanyji.app.feature.photo.ManagedCardPhoto
import com.guanyji.app.ui.theme.AquariumCardColors
import com.guanyji.app.ui.theme.AquariumCardTokens

/**
 * 水族主题鱼只卡牌。
 *
 * 结构：共用卡片外壳 → 4:3 照片 + 下方五行信息面板。
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
    dragging: Boolean = false,
    dragHandleModifier: Modifier = Modifier,
) {
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

    val cardAccent = model.tankType?.cardAccent() ?: AquariumCardColors.UnknownWaterAccent
    val badgeAccent = model.tankType?.cardBadgeAccent() ?: AquariumCardColors.UnknownWaterBadge
    AquariumCardShell(
        accent = cardAccent,
        onClick = onClick,
        onClickLabel = "查看${model.name}详情",
        modifier = modifier
            .graphicsLayer {
                alpha = entrance
                translationY = (1f - entrance) * 16.dp.toPx()
            }
            .testTag("fish-card-${model.id}"),
        imageContent = { FishCardImageContent(photo = photo, name = model.name) },
        overlayContent = {
            model.tankTypeLabel?.let { label ->
                AquariumTypeBadge(
                    label = label,
                    accent = badgeAccent,
                    modifier = Modifier.align(Alignment.TopStart).padding(
                        start = AquariumCardTokens.CardTypeBadgeInset,
                        top = AquariumCardTokens.CardTypeBadgeTopInset,
                    ),
                )
            }
            FishCardEditButton(model.name, onEdit, Modifier.align(Alignment.TopEnd))
        },
        infoContent = { FishCardInfo(model = model) },
        lifted = dragging,
        dragHandleModifier = dragHandleModifier,
    )
}

/** 照片区：优先使用数据库关联的本地照片，无照片时展示水族风格占位图。 */
@Composable
private fun FishCardImageContent(
    photo: Photo?,
    name: String,
) {
    ManagedCardPhoto(
        photo = photo,
        contentDescription = "$name 的照片",
        modifier = Modifier.fillMaxSize(),
        placeholder = { label, _ -> FishCardPhotoPlaceholder(label) },
    )
}

/**
 * 状态标记：中性底色 + 状态色圆点，异常状态额外配图标。
 * 中性底保证任何状态下的文字对比度；状态差异靠圆点与图标，不靠改变底色。
 */
@Composable
internal fun FishCardStatus(status: FishStatus, modifier: Modifier = Modifier) {
    val palette = remember(status) { status.cardPalette() }
    Row(
        modifier = modifier
            .clip(AquariumCardTokens.CardStatusShape)
            .background(AquariumCardTokens.FishCardStatusBackground)
            .border(
                AquariumCardTokens.FishCardControlBorderWidth,
                palette.accent.copy(alpha = AquariumCardTokens.FishCardStatusBorderAlpha),
                AquariumCardTokens.CardStatusShape,
            )
            .padding(
                horizontal = AquariumCardTokens.FishCardStatusHorizontalPadding,
                vertical = AquariumCardTokens.FishCardStatusVerticalPadding,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AquariumCardTokens.CardStatusDotTextGap),
    ) {
        Box(Modifier.size(AquariumCardTokens.StatusDotSize).clip(CircleShape).background(palette.accent))
        if (status != FishStatus.active) {
            Icon(
                imageVector = FishIcons.Alert,
                contentDescription = null,
                tint = palette.accent,
                modifier = Modifier.size(AquariumCardTokens.StatusIconSize),
            )
        }
        Text(
            text = status.label(),
            color = AquariumCardTokens.FishCardStatusText,
            fontSize = AquariumCardTokens.CardSecondaryFontSize,
            lineHeight = AquariumCardTokens.CardSecondaryLineHeight,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}

@Composable
private fun FishCardEditButton(name: String, onEdit: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(AquariumCardTokens.FishCardEditTouchTarget)
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
                .size(AquariumCardTokens.FishCardEditSurfaceSize)
                .clip(CircleShape)
                .background(FishCardTokens.ControlSurface)
                .border(
                    AquariumCardTokens.FishCardControlBorderWidth,
                    FishCardTokens.Border.copy(alpha = AquariumCardTokens.FishCardControlBorderAlpha),
                    CircleShape,
                ),
        )
        Icon(
            imageVector = Icons.Outlined.Edit,
            contentDescription = null,
            tint = AquariumCardColors.ControlInk,
            modifier = Modifier.size(AquariumCardTokens.FishCardEditIconSize),
        )
    }
}

/** 五行信息区：名称；鱼种与状态；缸名；两个指标；今日喂食。 */
@Composable
private fun FishCardInfo(model: FishCardUiModel) {
    Box(
        Modifier
            .fillMaxWidth(),
    ) {
        FishCardWaterLines(Modifier.matchParentSize())
        Column(
            modifier = Modifier.fillMaxWidth().padding(
                start = AquariumCardTokens.CardBodyInset,
                top = AquariumCardTokens.CardBodyInset,
                end = AquariumCardTokens.CardBodyInset,
                bottom = AquariumCardTokens.CardBodyBottomInset,
            ),
            verticalArrangement = Arrangement.spacedBy(AquariumCardTokens.CardTextRowSpacing),
        ) {
            Box(
                Modifier.fillMaxWidth().height(AquariumCardTokens.PanelTopDividerHeight).background(
                    Brush.horizontalGradient(
                        listOf(
                            AquariumCardColors.Gold.copy(alpha = AquariumCardTokens.PanelTopDividerStartAlpha),
                            AquariumCardColors.Gold.copy(alpha = AquariumCardTokens.PanelTopDividerEndAlpha),
                        ),
                    ),
                ),
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AquariumCardTokens.FishCardLineGap)) {
                Text(
                    text = model.name,
                    color = FishCardTokens.CardInk,
                    fontSize = AquariumCardTokens.CardTitleFontSize,
                    lineHeight = AquariumCardTokens.CardTitleLineHeight,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = FishIcons.ArrowRight,
                    contentDescription = null,
                    tint = FishCardTokens.CardSecondaryInk,
                    modifier = Modifier.size(AquariumCardTokens.CardArrowSize),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AquariumCardTokens.FishCardStatusGap)) {
                Text(
                    text = model.speciesLine,
                    color = FishCardTokens.CardSecondaryInk,
                    fontSize = AquariumCardTokens.CardSecondaryFontSize,
                    lineHeight = AquariumCardTokens.CardSecondaryLineHeight,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                FishCardStatus(status = model.status)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AquariumCardTokens.FishCardIconGap)) {
                Icon(
                    imageVector = FishIcons.Aquarium,
                    contentDescription = null,
                    tint = AquariumCardColors.CardSecondaryInk,
                    modifier = Modifier.size(AquariumCardTokens.FishCardMetricIconSize),
                )
                Text(
                    text = model.tankLine,
                    color = AquariumCardColors.CardSecondaryInk,
                    fontSize = AquariumCardTokens.CardTankNameFontSize,
                    lineHeight = AquariumCardTokens.CardTankNameLineHeight,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AquariumCardTokens.FishCardMetricItemGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FishCardMetric(icon = FishIcons.Clock, description = "已养", value = model.daysLabel, modifier = Modifier.weight(1f))
                FishCardMetric(icon = FishIcons.Ruler, description = "体长", value = model.lengthLabel, modifier = Modifier.weight(1f))
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AquariumCardTokens.FishCardIconGap)) {
                Icon(
                    imageVector = FishIcons.Fish,
                    contentDescription = null,
                    tint = AquariumCardColors.CardMetricInk.copy(alpha = 0.85f),
                    modifier = Modifier.size(AquariumCardTokens.CardFishIconSize),
                )
                Text(
                    text = "今日喂食",
                    color = FishCardTokens.CardSecondaryInk,
                    fontSize = AquariumCardTokens.CardMetricFontSize,
                    lineHeight = AquariumCardTokens.CardMetricLineHeight,
                    maxLines = 1,
                )
                Text(
                    text = model.feedingLabel,
                    color = FishCardTokens.CardMetricInk,
                    fontSize = AquariumCardTokens.CardMetricValueFontSize,
                    lineHeight = AquariumCardTokens.CardMetricValueLineHeight,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun FishCardMetric(icon: ImageVector, description: String, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.semantics { contentDescription = "$description $value" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AquariumCardTokens.FishCardMetricIconGap),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = FishCardTokens.CardMetricInk.copy(alpha = 0.85f),
            modifier = Modifier.size(AquariumCardTokens.FishCardMetricIconSize),
        )
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                text = description,
                color = FishCardTokens.CardSecondaryInk,
                fontSize = AquariumCardTokens.CardMetricFontSize,
                lineHeight = AquariumCardTokens.CardMetricLineHeight,
                maxLines = 1,
            )
            Text(
                text = value,
                color = FishCardTokens.CardMetricInk,
                fontSize = AquariumCardTokens.CardMetricValueFontSize,
                lineHeight = AquariumCardTokens.CardMetricValueLineHeight,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Clip,
            )
        }
    }
}

/** 无照片 / 读取中 / 读取失败时的占位层，保持卡牌视觉完整。 */
@Composable
private fun FishCardPhotoPlaceholder(label: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(AquariumCardColors.PlaceholderTop, AquariumCardColors.PlaceholderBottom))),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(AquariumCardTokens.FishCardPlaceholderGap)) {
            Icon(
                imageVector = FishIcons.Fish,
                contentDescription = null,
                tint = AquariumCardColors.PlaceholderInk,
                modifier = Modifier.size(AquariumCardTokens.FishCardPlaceholderIconSize),
            )
            Text(text = label, color = AquariumCardTokens.FishCardPlaceholderLabelColor, fontSize = AquariumCardTokens.FishCardPlaceholderFontSize)
        }
    }
}

/** 信息面板背景的水波装饰线，铺满面板并衬在正文下方。 */
@Composable
private fun FishCardWaterLines(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val waveTop = Path().apply {
            moveTo(0f, size.height * 0.62f)
            cubicTo(size.width * 0.28f, size.height * 0.44f, size.width * 0.58f, size.height * 0.80f, size.width, size.height * 0.56f)
        }
        val waveBottom = Path().apply {
            moveTo(0f, size.height * 0.80f)
            cubicTo(size.width * 0.30f, size.height * 0.62f, size.width * 0.62f, size.height * 0.96f, size.width, size.height * 0.74f)
        }
        drawPath(
            waveTop,
            color = AquariumCardTokens.WaveColor.copy(alpha = AquariumCardTokens.WavePrimaryAlpha),
            style = Stroke(width = AquariumCardTokens.WaveStrokeWidth.toPx()),
        )
        drawPath(
            waveBottom,
            color = AquariumCardTokens.WaveColor.copy(alpha = AquariumCardTokens.WaveSecondaryAlpha),
            style = Stroke(width = AquariumCardTokens.WaveStrokeWidth.toPx()),
        )
    }
}
