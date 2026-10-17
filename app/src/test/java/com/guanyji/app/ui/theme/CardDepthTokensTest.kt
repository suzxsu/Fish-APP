package com.guanyji.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 卡片立体光影令牌的回归测试。
 *
 * 立体感完全由「描边亮度 + 内斜面厚度 + 层间暗带 + 投影」几层低透明度叠加构成，
 * 任何一个参数被误调（例如暗面比亮面还亮、按下时投影反而变大）都会让厚度感消失，
 * 因此把这些关系钉成断言，而不是只靠肉眼验收。
 */
class CardDepthTokensTest {
    @Test
    fun everyAlphaTokenStaysInsideValidRange() {
        val alphas = mapOf(
            "RimTopAlpha" to AquariumCardTokens.RimTopAlpha,
            "RimAccentAlpha" to AquariumCardTokens.RimAccentAlpha,
            "RimBottomAlpha" to AquariumCardTokens.RimBottomAlpha,
            "RimHaloAlphaFactor" to AquariumCardTokens.CardRimHaloAlphaFactor,
            "InnerRimTopAlpha" to AquariumCardTokens.InnerRimTopAlpha,
            "InnerRimSoftAlpha" to AquariumCardTokens.InnerRimSoftAlpha,
            "InnerRimBottomAlpha" to AquariumCardTokens.InnerRimBottomAlpha,
            "CardBottomReflectAlpha" to AquariumCardTokens.CardBottomReflectAlpha,
            "CardGlowAmbientAlpha" to AquariumCardTokens.CardGlowAmbientAlpha,
            "CardGlowSpotAlpha" to AquariumCardTokens.CardGlowSpotAlpha,
            "CardDepthAmbientAlpha" to AquariumCardTokens.CardDepthAmbientAlpha,
            "CardDepthSpotAlpha" to AquariumCardTokens.CardDepthSpotAlpha,
            "CardContactShadowLayerAlpha" to AquariumCardTokens.CardContactShadowLayerAlpha,
            "PhotoBottomShadeAlpha" to AquariumCardTokens.PhotoBottomShadeAlpha,
            "PanelTopHairlineAlpha" to AquariumCardTokens.PanelTopHairlineAlpha,
            "PanelInnerShadeAlpha" to AquariumCardTokens.PanelInnerShadeAlpha,
        )
        alphas.forEach { (name, value) ->
            assertTrue("$name=$value 必须是 (0,1] 之间的透明度", value > 0f && value <= 1f)
        }
    }

    @Test
    fun litSideOfTheRimIsBrighterThanTheShadedSide() {
        // 光源固定在左上：上缘合成色必须比下缘合成色更亮，否则厚度方向就反了。
        val topEdge = AquariumCardColors.RimHighlight.compositeOver(
            AquariumCardColors.CardTop,
            AquariumCardTokens.RimTopAlpha,
        )
        val bottomEdge = AquariumCardColors.RimShade.compositeOver(
            AquariumCardColors.CardBottom,
            AquariumCardTokens.RimBottomAlpha,
        )
        assertTrue(
            "上缘亮度 ${topEdge.relativeLuminance()} 未高于下缘 ${bottomEdge.relativeLuminance()}",
            topEdge.relativeLuminance() > bottomEdge.relativeLuminance(),
        )
    }

    @Test
    fun rimLayersAreOrderedFromOutsideIn() {
        // 光晕在最外、实线居中、内斜面更靠内：顺序错了就会变成两层重叠的粗描边。
        assertTrue(
            "光晕宽度应大于实线宽度",
            AquariumCardTokens.CardRimHaloWidth > AquariumCardTokens.CardBorderWidth,
        )
        assertTrue(
            "内斜面内缩距离应大于实线宽度",
            AquariumCardTokens.CardInnerRimInset > AquariumCardTokens.CardBorderWidth,
        )
        assertTrue(
            "内斜面不应吞掉卡面内容",
            AquariumCardTokens.CardInnerRimInset + AquariumCardTokens.CardRimHaloWidth < 6.dp(),
        )
    }

    @Test
    fun pressingTightensTheShadowInsteadOfGrowingIt() {
        assertTrue(
            "按下态投影必须小于静置态投影，否则按压看起来像弹起",
            AquariumCardTokens.CardShadowElevationPressed < AquariumCardTokens.CardShadowElevation,
        )
        assertTrue(
            "按压缩放必须在 (0,1) 之间",
            AquariumCardTokens.CardPressedScale > 0f && AquariumCardTokens.CardPressedScale < 1f,
        )
        assertTrue(
            "按压倾角必须小于 8 度，否则列表里会出现明显的 3D 畸变",
            kotlin.math.abs(AquariumCardTokens.CardPressTiltX) < 8f,
        )
    }

    @Test
    fun contactShadowStaysSubtleEnoughToNotReadAsDirt() {
        // 层数 × 单层透明度是卡片最外圈的累计压暗量，超过 0.3 就会像脏污而不是阴影。
        val accumulated = AquariumCardTokens.CardContactShadowLayers *
            AquariumCardTokens.CardContactShadowLayerAlpha
        assertTrue("接触阴影累计透明度 $accumulated 过重", accumulated <= 0.3f)
    }

    private fun Color.compositeOver(background: Color, alpha: Float): Color = Color(
        red = red * alpha + background.red * (1f - alpha),
        green = green * alpha + background.green * (1f - alpha),
        blue = blue * alpha + background.blue * (1f - alpha),
    )

    private fun Color.relativeLuminance(): Double {
        fun linear(channel: Float): Double {
            val value = channel.toDouble()
            return if (value <= 0.03928) value / 12.92 else Math.pow((value + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * linear(red) + 0.7152 * linear(green) + 0.0722 * linear(blue)
    }

    private fun Int.dp(): Dp = Dp(this.toFloat())
}
