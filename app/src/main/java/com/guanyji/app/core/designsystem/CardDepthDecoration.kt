package com.guanyji.app.core.designsystem

import android.os.Build
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import com.guanyji.app.ui.theme.AquariumCardColors
import com.guanyji.app.ui.theme.AquariumCardTokens

/**
 * 水族卡片立体光影的绘制修饰符。
 *
 * 立体感全部由「低透明度渐变 + 细描边」叠加而成，不依赖 GPU 模糊：
 * Compose 的真实模糊（`RenderEffect` / `Modifier.blur`）需要 API 31，本项目 minSdk 为 26，
 * 用模糊会让低版本卡片直接失去厚度。分层结构与参数依据
 * `docs/产品设计/鱼缸卡片设计规范展示板.png` 与设计文档 3.1「卡片立体规范」。
 *
 * 五个修饰符按使用位置分工：
 * - [cardOuterDepth]：卡片外侧（外发光 + 接地投影）
 * - [cardEdgeLighting]：卡片最上层（外缘受光描边 + 内斜面 + 底部反光边）
 * - [cardPhotoBottomShade]：照片区最上层（下沿暗带）
 * - [cardPanelBevel]：信息面板（顶部内阴影 + 交界高光）
 */

/**
 * 外发光 + 接地投影。
 *
 * 两层 elevation 阴影叠出「悬空玻璃」：外层是主题色冷光晕（范围更大，从深色投影外侧透出），
 * 内层是深色接地投影（按下时收紧到 [AquariumCardTokens.CardShadowElevationPressed]）。
 * 着色阴影只在 API 28+ 生效，低版本自动退化为单层深色投影。
 */
fun Modifier.cardOuterDepth(
    accent: Color,
    elevation: Dp,
): Modifier {
    val withGlow = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        shadow(
            elevation = AquariumCardTokens.CardGlowElevation,
            shape = AquariumCardTokens.CardShape,
            clip = false,
            ambientColor = accent.copy(alpha = AquariumCardTokens.CardGlowAmbientAlpha),
            spotColor = accent.copy(alpha = AquariumCardTokens.CardGlowSpotAlpha),
        )
    } else {
        this
    }
    return withGlow.shadow(
        elevation = elevation,
        shape = AquariumCardTokens.CardShape,
        clip = false,
        ambientColor = AquariumCardColors.DepthShadowDeep.copy(alpha = AquariumCardTokens.CardDepthAmbientAlpha),
        spotColor = AquariumCardColors.DepthShadowDeep.copy(alpha = AquariumCardTokens.CardDepthSpotAlpha),
    )
}

/**
 * 接触阴影：卡片外侧一圈向下偏移、向内加深的暗带。
 *
 * Android 的 elevation 阴影由系统绘制，方向固定且偏淡，单靠它撑不起“离开页面”的观感。
 * 这里用 [AquariumCardTokens.CardContactShadowLayers] 层逐渐外扩的圆角矩形叠出柔边
 * （无模糊可用的替代方案）：越靠近卡片叠加层数越多，因此自然形成由深到浅的过渡。
 * 调用位置必须在卡片 `clip` 之前，否则超出边界的部分会被裁掉。
 */
fun Modifier.cardContactShadow(): Modifier = drawWithCache {
    val radius = AquariumCardTokens.CardCornerRadius.toPx()
    val layers = AquariumCardTokens.CardContactShadowLayers
    val step = AquariumCardTokens.CardContactShadowStep.toPx()
    val offsetY = AquariumCardTokens.CardContactShadowOffset.toPx()
    val color = AquariumCardColors.DepthShadowDeep.copy(alpha = AquariumCardTokens.CardContactShadowLayerAlpha)

    onDrawWithContent {
        for (layer in layers downTo 1) {
            val grow = layer * step
            val shift = offsetY * (layer.toFloat() / layers)
            drawRoundRect(
                color = color,
                topLeft = Offset(-grow, -grow + shift),
                size = Size(size.width + grow * 2f, size.height + grow * 2f),
                cornerRadius = CornerRadius(radius + grow),
            )
        }
        drawContent()
    }
}

/**
 * 外缘受光描边 + 内斜面 + 底部反光边，绘制在卡片内容之上，所以能压住照片边缘。
 *
 * [borderAlpha] 用于按压态提亮，调用方传入 0.68（静置）到 1.0（按下）之间的动画值。
 */
fun Modifier.cardEdgeLighting(
    accent: Color,
    borderAlpha: Float,
): Modifier = drawWithCache {
    val width = size.width
    val height = size.height
    val radius = AquariumCardTokens.CardCornerRadius.toPx()
    val outerWidth = AquariumCardTokens.CardBorderWidth.toPx()
    val innerWidth = AquariumCardTokens.CardInnerRimWidth.toPx()
    val innerInset = AquariumCardTokens.CardInnerRimInset.toPx() + innerWidth / 2f
    val reflectWidth = AquariumCardTokens.CardBottomReflectWidth.toPx()
    val reflectInset = AquariumCardTokens.CardBottomReflectInset.toPx()

    // 光源固定在左上，因此描边沿「左上 → 右下」由亮白渐变到暗面。
    val outerBrush = Brush.linearGradient(
        colorStops = arrayOf(
            0f to AquariumCardColors.RimHighlight.copy(alpha = AquariumCardTokens.RimTopAlpha * borderAlpha),
            0.30f to accent.copy(alpha = AquariumCardTokens.RimAccentAlpha * borderAlpha),
            0.58f to AquariumCardTokens.CardBorderColor.copy(
                alpha = AquariumCardTokens.CardBorderMiddleAlpha * borderAlpha,
            ),
            1f to AquariumCardColors.RimShade.copy(alpha = AquariumCardTokens.RimBottomAlpha * borderAlpha),
        ),
        start = Offset(0f, 0f),
        end = Offset(width, height),
    )
    val innerBrush = Brush.linearGradient(
        colorStops = arrayOf(
            0f to AquariumCardColors.RimHighlight.copy(alpha = AquariumCardTokens.InnerRimTopAlpha * borderAlpha),
            0.32f to AquariumCardColors.RimHighlightSoft.copy(alpha = AquariumCardTokens.InnerRimSoftAlpha * borderAlpha),
            0.62f to Color.Transparent,
            1f to AquariumCardColors.RimShade.copy(alpha = AquariumCardTokens.InnerRimBottomAlpha * borderAlpha),
        ),
        start = Offset(0f, 0f),
        end = Offset(width * 0.9f, height),
    )
    val reflectBrush = Brush.horizontalGradient(
        colorStops = arrayOf(
            0f to Color.Transparent,
            0.24f to accent.copy(alpha = 0.10f * borderAlpha),
            0.50f to accent.copy(alpha = AquariumCardTokens.CardBottomReflectAlpha * borderAlpha),
            0.76f to accent.copy(alpha = 0.10f * borderAlpha),
            1f to Color.Transparent,
        ),
    )
    // 顶部镜面反光：两端淡出，只在上边缘中段出现，才像抛光玻璃而不是塑料描边。
    val glintBrush = Brush.horizontalGradient(
        colorStops = arrayOf(
            0f to Color.Transparent,
            0.22f to AquariumCardColors.RimHighlight.copy(alpha = 0.20f * borderAlpha),
            0.50f to AquariumCardColors.RimHighlight.copy(alpha = AquariumCardTokens.CardTopGlintAlpha * borderAlpha),
            0.78f to AquariumCardColors.RimHighlight.copy(alpha = 0.20f * borderAlpha),
            1f to Color.Transparent,
        ),
    )
    // 描边光晕：同色但整体压低透明度，垫在实线外侧，制造“光透过玻璃渗出”的柔和过渡。
    val haloWidth = AquariumCardTokens.CardRimHaloWidth.toPx()
    val haloFactor = AquariumCardTokens.CardRimHaloAlphaFactor
    val haloBrush = Brush.linearGradient(
        colorStops = arrayOf(
            0f to AquariumCardColors.RimHighlight.copy(
                alpha = AquariumCardTokens.RimTopAlpha * borderAlpha * haloFactor,
            ),
            0.30f to accent.copy(alpha = AquariumCardTokens.RimAccentAlpha * borderAlpha * haloFactor),
            0.58f to AquariumCardTokens.CardBorderColor.copy(
                alpha = AquariumCardTokens.CardBorderMiddleAlpha * borderAlpha * haloFactor,
            ),
            1f to AquariumCardColors.RimShade.copy(
                alpha = AquariumCardTokens.RimBottomAlpha * borderAlpha * haloFactor,
            ),
        ),
        start = Offset(0f, 0f),
        end = Offset(width, height),
    )

    onDrawWithContent {
        drawContent()
        // 光晕层先画，实线层后画，两层叠加成柔边。
        drawRoundRect(
            brush = haloBrush,
            topLeft = Offset(haloWidth / 2f, haloWidth / 2f),
            size = Size(width - haloWidth, height - haloWidth),
            cornerRadius = CornerRadius((radius - haloWidth / 2f).coerceAtLeast(0f)),
            style = Stroke(width = haloWidth),
        )
        // 外缘：描边中线内缩半个线宽，让亮边的外沿贴住卡片边界。
        drawRoundRect(
            brush = outerBrush,
            topLeft = Offset(outerWidth / 2f, outerWidth / 2f),
            size = Size(width - outerWidth, height - outerWidth),
            cornerRadius = CornerRadius((radius - outerWidth / 2f).coerceAtLeast(0f)),
            style = Stroke(width = outerWidth),
        )
        // 内斜面：内缩一圈，与描边之间留出 1dp 多的「玻璃厚度」。
        drawRoundRect(
            brush = innerBrush,
            topLeft = Offset(innerInset, innerInset),
            size = Size(
                (width - innerInset * 2f).coerceAtLeast(0f),
                (height - innerInset * 2f).coerceAtLeast(0f),
            ),
            cornerRadius = CornerRadius((radius - innerInset).coerceAtLeast(0f)),
            style = Stroke(width = innerWidth),
        )
        // 顶部镜面反光：叠在外缘描边上，两端淡出，形成一道渐隐光斑。
        drawLine(
            brush = glintBrush,
            start = Offset(radius * AquariumCardTokens.CardTopGlintInsetFactor, outerWidth / 2f),
            end = Offset(
                width - radius * AquariumCardTokens.CardTopGlintInsetFactor,
                outerWidth / 2f,
            ),
            strokeWidth = outerWidth * 0.75f,
            cap = StrokeCap.Round,
        )
        // 底部反光边：避开圆角，只在直线段出现，否则会在角上画出一道弧。
        drawLine(
            brush = reflectBrush,
            start = Offset(radius * 0.6f, height - reflectInset - reflectWidth / 2f),
            end = Offset(width - radius * 0.6f, height - reflectInset - reflectWidth / 2f),
            strokeWidth = reflectWidth,
            cap = StrokeCap.Round,
        )
    }
}

/**
 * 照片下沿暗带：让信息面板看起来压在照片之上。
 *
 * 绘制在照片区内容之上（含类型标签与编辑按钮），所以只影响下沿，不遮挡顶部控件。
 */
fun Modifier.cardPhotoBottomShade(): Modifier = drawWithCache {
    val shadeHeight = AquariumCardTokens.PhotoBottomShadeHeight.toPx().coerceAtMost(size.height)
    val shadeTop = size.height - shadeHeight
    val brush = Brush.verticalGradient(
        colorStops = arrayOf(
            0f to Color.Transparent,
            1f to AquariumCardColors.RimShade.copy(alpha = AquariumCardTokens.PhotoBottomShadeAlpha),
        ),
        startY = shadeTop,
        endY = size.height,
    )
    onDrawWithContent {
        drawContent()
        drawRect(brush = brush, topLeft = Offset(0f, shadeTop), size = Size(size.width, shadeHeight))
    }
}

/**
 * 信息面板的顶部内阴影与交界高光。
 *
 * 绘制在面板内容之下：内阴影压暗底色但不压暗文字，高光线只占 1dp，不与正文冲突。
 */
fun Modifier.cardPanelBevel(accent: Color): Modifier = drawWithCache {
    val hairlineHeight = AquariumCardTokens.PanelTopHairlineHeight.toPx()
    val shadeHeight = AquariumCardTokens.PanelInnerShadeHeight.toPx().coerceAtMost(size.height)
    val hairlineBrush = Brush.horizontalGradient(
        colorStops = arrayOf(
            0f to AquariumCardColors.RimHighlight.copy(alpha = AquariumCardTokens.PanelTopHairlineAlpha),
            0.42f to accent.copy(alpha = AquariumCardTokens.PanelTopHairlineAccentAlpha),
            1f to Color.Transparent,
        ),
    )
    val shadeBrush = Brush.verticalGradient(
        colorStops = arrayOf(
            0f to AquariumCardColors.PanelInnerShade.copy(alpha = AquariumCardTokens.PanelInnerShadeAlpha),
            1f to Color.Transparent,
        ),
        startY = 0f,
        endY = shadeHeight,
    )
    onDrawWithContent {
        drawRect(brush = shadeBrush, size = Size(size.width, shadeHeight))
        drawRect(brush = hairlineBrush, size = Size(size.width, hairlineHeight))
        drawContent()
    }
}
