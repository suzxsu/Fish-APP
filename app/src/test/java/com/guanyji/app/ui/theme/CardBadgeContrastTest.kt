package com.guanyji.app.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 徽标与状态胶囊的文字对比度回归。
 *
 * 浅色强调色直接当底会让白字掉到 2:1 左右，肉眼在照片上几乎读不出来；
 * 这里把 WCAG AA 正文要求的 4.5:1 钉成断言，避免以后调色又把可读性调没。
 */
class CardBadgeContrastTest {
    @Test
    fun badgeLabelsMeetAaOnTheirBackgrounds() {
        val badges = mapOf(
            "淡水类型徽标" to AquariumCardColors.FreshwaterBadge,
            "海水类型徽标" to AquariumCardColors.SaltwaterBadge,
            "未知水体徽标" to AquariumCardColors.UnknownWaterBadge,
            "在养状态徽标" to AquariumCardColors.StatusActiveBadge,
            "已归档状态徽标" to AquariumCardColors.StatusArchivedBadge,
        )
        badges.forEach { (name, background) ->
            val ratio = contrastRatio(AquariumCardTokens.TypeBadgeTextColor, background)
            assertTrue("$name 白字对比度 ${round(ratio)} 低于 4.5:1", ratio >= 4.5)
        }
    }

    @Test
    fun fishStatusChipTextMeetsAaOnTheNeutralChip() {
        // 胶囊底是半透明白叠在深色信息面板上，必须按实际合成结果算对比度。
        val chipBackground = AquariumCardTokens.CardStatusBackground.compositeOver(AquariumCardColors.PanelTop)
        val ratio = contrastRatio(AquariumCardTokens.FishCardStatusText, chipBackground)
        assertTrue("状态胶囊文字对比度 ${round(ratio)} 低于 4.5:1", ratio >= 4.5)
    }

    private fun Color.compositeOver(background: Color): Color {
        val a = alpha
        return Color(
            red = red * a + background.red * (1f - a),
            green = green * a + background.green * (1f - a),
            blue = blue * a + background.blue * (1f - a),
        )
    }

    private fun contrastRatio(first: Color, second: Color): Double {
        val a = first.relativeLuminance()
        val b = second.relativeLuminance()
        return (maxOf(a, b) + 0.05) / (minOf(a, b) + 0.05)
    }

    private fun Color.relativeLuminance(): Double {
        fun linear(channel: Float): Double {
            val value = channel.toDouble()
            return if (value <= 0.03928) value / 12.92 else Math.pow((value + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * linear(red) + 0.7152 * linear(green) + 0.0722 * linear(blue)
    }

    private fun round(value: Double): String = String.format("%.2f", value)
}
