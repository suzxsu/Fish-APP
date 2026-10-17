package com.guanyji.app.core.designsystem

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import com.guanyji.app.ui.theme.AquariumCardColors
import com.guanyji.app.ui.theme.AquariumCardTokens

/**
 * 鱼只与鱼缸列表卡片共用的视觉外壳。
 *
 * 照片固定 4:3；信息面板紧贴照片下方，整卡高度随内容增长。
 */
@Composable
fun AquariumCardShell(
    accent: Color,
    onClick: () -> Unit,
    onClickLabel: String?,
    imageContent: @Composable BoxScope.() -> Unit,
    infoContent: @Composable BoxScope.() -> Unit,
    modifier: Modifier = Modifier,
    overlayContent: @Composable BoxScope.() -> Unit = {},
    imageAspectRatio: Float = AquariumCardTokens.ImageAspectRatio,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) AquariumCardTokens.CardPressedScale else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "aquariumCardPress",
    )
    val borderAlpha by animateFloatAsState(
        targetValue = if (pressed) 1f else AquariumCardTokens.CardBorderIdleAlpha,
        animationSpec = tween(durationMillis = 160),
        label = "aquariumCardBorder",
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .shadow(
                elevation = AquariumCardTokens.CardShadowElevation,
                shape = AquariumCardTokens.CardShape,
                clip = false,
                ambientColor = AquariumCardColors.ShadowTint,
                spotColor = AquariumCardColors.ShadowTint,
            )
            .clip(AquariumCardTokens.CardShape)
            .background(Brush.verticalGradient(listOf(AquariumCardColors.CardTop, AquariumCardColors.CardBottom)))
            .border(
                width = AquariumCardTokens.CardBorderWidth,
                brush = Brush.linearGradient(
                    listOf(
                        accent.copy(alpha = borderAlpha),
                        AquariumCardTokens.CardBorderColor.copy(alpha = borderAlpha * AquariumCardTokens.CardBorderMiddleAlpha),
                        accent.copy(alpha = borderAlpha * AquariumCardTokens.CardBorderEndAlpha),
                    ),
                ),
                shape = AquariumCardTokens.CardShape,
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClickLabel = onClickLabel,
                onClick = onClick,
            ),
    ) {
        Column(Modifier.fillMaxWidth()) {
            // 外层已按卡片圆角裁剪，图片区不再单独 clip，避免照片下沿出现圆角缺口。
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(imageAspectRatio),
            ) {
                imageContent()
                overlayContent()
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(AquariumCardColors.PanelTop, AquariumCardColors.PanelBottom),
                        ),
                    )
            ) {
                infoContent()
            }
        }
    }
}

/**
 * 淡水 / 海水类型标签。调用方只根据真实鱼缸类型传入文字与徽标色。
 *
 * [accent] 必须是不透明白字可读的徽标底色（见 `AquariumCardColors.*Badge`），
 * 半透明底会让白字对比度随照片内容浮动，无法保证可访问性。
 */
@Composable
fun AquariumTypeBadge(
    label: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(AquariumCardTokens.TypeBadgeShape)
            .background(accent)
            .border(
                AquariumCardTokens.TypeBadgeBorderWidth,
                AquariumCardTokens.TypeBadgeTextColor.copy(alpha = AquariumCardTokens.TypeBadgeBorderAlpha),
                AquariumCardTokens.TypeBadgeShape,
            )
            .padding(
                horizontal = AquariumCardTokens.TypeBadgeHorizontalInset,
                vertical = AquariumCardTokens.TypeBadgeVerticalInset,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AquariumCardTokens.TypeBadgeIconGap),
    ) {
        Box(
            Modifier
                .size(AquariumCardTokens.TypeBadgeMarkerSize)
                .clip(CircleShape)
                .background(AquariumCardTokens.TypeBadgeMarkerColor),
        )
        Text(
            text = label,
            color = AquariumCardTokens.TypeBadgeTextColor,
            fontSize = AquariumCardTokens.CardCaptionFontSize,
            lineHeight = AquariumCardTokens.CardCaptionLineHeight,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}
