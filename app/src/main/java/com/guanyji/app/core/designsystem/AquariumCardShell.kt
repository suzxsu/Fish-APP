package com.guanyji.app.core.designsystem

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
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
    /** 长按拖拽排序期间置为 true：卡片"被拿起"，投影拉长、放大，并与按压倾斜互斥。 */
    lifted: Boolean = false,
    /** 拖拽手势修饰符，由列表页注入；放在 clickable 之后，短按仍进详情。 */
    dragHandleModifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = when {
            lifted -> AquariumCardTokens.CardLiftedScale
            pressed -> AquariumCardTokens.CardPressedScale
            else -> 1f
        },
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "aquariumCardPress",
    )
    val borderAlpha by animateFloatAsState(
        targetValue = if (pressed || lifted) 1f else AquariumCardTokens.CardBorderIdleAlpha,
        animationSpec = tween(durationMillis = 160),
        label = "aquariumCardBorder",
    )
    // 按压时卡片沿底边轻微后仰、投影收紧，形成“压下去”的立体反馈；
    // 拖起时必须直立（抬起不是按下），否则会和"拿起卡片"的语义打架。
    val pressTilt by animateFloatAsState(
        targetValue = if (pressed && !lifted) AquariumCardTokens.CardPressTiltX else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "aquariumCardTilt",
    )
    val depthElevation by animateDpAsState(
        targetValue = when {
            lifted -> AquariumCardTokens.CardShadowElevationLifted
            pressed -> AquariumCardTokens.CardShadowElevationPressed
            else -> AquariumCardTokens.CardShadowElevation
        },
        animationSpec = tween(durationMillis = AquariumCardTokens.CardDepthAnimationMillis),
        label = "aquariumCardElevation",
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
                rotationX = pressTilt
                transformOrigin = TransformOrigin(0.5f, 1f)
                cameraDistance = AquariumCardTokens.CardCameraDistance * density
            }
            .cardOuterDepth(accent = accent, elevation = depthElevation)
            .cardContactShadow()
            .clip(AquariumCardTokens.CardShape)
            .background(Brush.verticalGradient(listOf(AquariumCardColors.CardTop, AquariumCardColors.CardBottom)))
            // 描边、内斜面与底部反光都画在内容之上，这样亮边能压住照片边缘，形成玻璃厚度。
            .cardEdgeLighting(accent = accent, borderAlpha = borderAlpha)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClickLabel = onClickLabel,
                onClick = onClick,
            )
            .then(dragHandleModifier),
    ) {
        Column(Modifier.fillMaxWidth()) {
            // 外层已按卡片圆角裁剪，图片区不再单独 clip，避免照片下沿出现圆角缺口。
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(imageAspectRatio)
                    .cardPhotoBottomShade(),
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
                    .cardPanelBevel(accent = accent),
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
