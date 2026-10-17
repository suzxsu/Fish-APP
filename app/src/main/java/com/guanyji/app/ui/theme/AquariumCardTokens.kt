package com.guanyji.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 水族卡片共用的颜色令牌。
 *
 * 卡片使用固定的深色卡面，避免浅色 / 深色主题切换时文字与照片对比度失真；
 * 页面背景仍由各功能页的既有主题令牌控制。
 */
object AquariumCardColors {
    val CardTop = Color(0xFF0D3355)
    val CardBottom = Color(0xFF0A1F35)
    val PanelTop = Color(0xFF14436B)
    val PanelBottom = Color(0xFF0A2440)

    val CardInk = Color(0xFFFFFFFF)
    val CardSecondaryInk = Color(0xFFA8C5DE)
    val CardMetricInk = Color(0xFFD8E8F5)

    val Gold = Color(0xFFE4C27C)
    val Border = Color(0xFF9FD3F0)
    val ShadowTint = Color(0xFF0A2440)
    val ControlSurface = Color(0x4D0C2E4E)
    val ControlInk = Color(0xFFEAF4FB)
    val ReefFallbackTop = Color(0xFF0C3A63)
    val ReefFallbackBottom = Color(0xFF124E77)
    val ImageTopScrim = Color(0x38000000)
    val PlaceholderTop = Color(0xFF123C60)
    val PlaceholderBottom = Color(0xFF0B2A47)
    val PlaceholderInk = Color(0xB8CFE6F7)
    val PlaceholderText = Color(0xFFE6F3FD)

    val FreshwaterAccent = Color(0xFF1FAF9A)
    val SaltwaterAccent = Color(0xFF2479DE)
    val UnknownWaterAccent = Color(0xFF7D9AB3)

    // 徽标底色：强调色直接当底会让白字对比度跌到 2:1 左右，因此另备一组压暗色，
    // 白字对比度均 ≥ 4.6:1（WCAG AA 正文 4.5:1），由 CardBadgeContrastTest 守护。
    val FreshwaterBadge = Color(0xFF178374)
    val SaltwaterBadge = Color(0xFF2374D5)
    val UnknownWaterBadge = Color(0xFF5D7796)
    val StatusActiveBadge = Color(0xFF278272)
    val StatusArchivedBadge = Color(0xFF5D7796)

    val StatusActiveAccent = Color(0xFF3FD0B6)
    val StatusActiveBackground = Color(0xD9148A76)
    val StatusActiveText = Color(0xFFEAFBF6)
    val StatusQuarantineAccent = Color(0xFFE2A03F)
    val StatusQuarantineBackground = Color(0xD9B8771E)
    val StatusQuarantineText = Color(0xFFFFF4E2)
    val StatusTransferredAccent = Color(0xFF8C7BEC)
    val StatusTransferredBackground = Color(0xD95A55C4)
    val StatusTransferredText = Color(0xFFEDEAFF)
    val StatusDeceasedAccent = Color(0xFF93A3B1)
    val StatusDeceasedBackground = Color(0xD93F4A57)
    val StatusDeceasedText = Color(0xFFE3EAF0)
    val StatusArchivedAccent = Color(0xFF6D8BB0)
    val StatusArchivedBackground = Color(0xD93A4A63)
    val StatusArchivedText = Color(0xFFE0EAF6)
}

object AquariumCardTokens {
    val CardShape = RoundedCornerShape(16.dp)
    val ChipShape = RoundedCornerShape(999.dp)
    val FieldShape = RoundedCornerShape(14.dp)
    /** 照片固定 4:3；整卡高度随信息面板内容自适应。 */
    const val ImageAspectRatio = 4f / 3f
    val PanelTopDividerHeight = 1.dp
    val PanelTopDividerStartAlpha = 0.9f
    val PanelTopDividerEndAlpha = 0f
    val CardBorderWidth = 1.2.dp
    val CardBorderColor = Color(0xFF9FD3F0)
    const val CardBorderMiddleAlpha = 0.55f
    const val CardBorderEndAlpha = 0.78f
    val CardBodyInset = 8.dp
    val CardBodyBottomInset = 8.dp
    val CardTextRowSpacing = 2.dp
    val FishCardLineGap = 5.dp
    val FishCardMetricIconGap = 3.dp
    val FishCardMetricItemGap = 6.dp
    val FishCardIconGap = 3.dp
    val WaveStrokeWidth = 1.4.dp
    const val WavePrimaryAlpha = 0.42f
    const val WaveSecondaryAlpha = 0.26f
    val WaveColor = Color(0xFFB9F0F7)
    val StatusDotSize = 5.dp
    val StatusIconSize = 11.dp
    val NeutralStatusText = Color(0xFFF4F8FB)
    val CardStatusDotTextGap = 3.dp
    val CardStatusBackground = Color(0x26FFFFFF)
    val CardStatusShape = RoundedCornerShape(5.dp)
    val FishCardStatusGap = 4.dp
    val FishCardStatusHorizontalPadding = 8.dp
    val FishCardStatusVerticalPadding = 3.dp
    /** 胶囊底色保持中性，状态差异由圆点颜色与异常状态图标承担。 */
    val FishCardStatusBackground = CardStatusBackground
    val FishCardStatusText = NeutralStatusText
    val FishCardStatusBorderAlpha = 0.45f
    val FishCardEditTouchTarget = 48.dp
    val FishCardEditSurfaceSize = 30.dp
    val FishCardEditIconSize = 16.dp
    val FishCardControlBorderWidth = 1.dp
    const val FishCardControlBorderAlpha = 0.45f
    val FishCardMetricIconSize = 15.dp
    val FishCardPlaceholderIconSize = 30.dp
    val FishCardPlaceholderGap = 4.dp
    val FishCardPlaceholderFontSize = 13.sp
    val FishCardPlaceholderLabelColor = Color(0xFFE6F3FD)
    val TypeBadgeHorizontalInset = 8.dp
    val TypeBadgeVerticalInset = 3.dp
    val TypeBadgeIconGap = 5.dp
    val TypeBadgeMarkerSize = 6.dp
    val TypeBadgeMarkerColor = Color(0xFFFFFFFF)
    val TypeBadgeBorderWidth = 1.dp
    val TypeBadgeBorderAlpha = 0.38f
    val TypeBadgeShape = RoundedCornerShape(999.dp)
    val TypeBadgeTextColor = Color(0xFFFFFFFF)
    val CardFishIconSize = 15.dp
    val CardArrowSize = 18.dp
    val CardTypeBadgeInset = 8.dp
    val CardTypeBadgeTopInset = 8.dp
    val CardCaptionFontSize = 13.sp
    val CardCaptionLineHeight = 17.sp
    val CardTitleFontSize = 18.sp
    val CardTitleLineHeight = 22.sp
    val CardSecondaryFontSize = 13.sp
    val CardSecondaryLineHeight = 17.sp
    val CardMetricFontSize = 13.sp
    val CardMetricLineHeight = 17.sp
    val CardMetricValueFontSize = 14.sp
    val CardMetricValueLineHeight = 18.sp
    val CardTankNameFontSize = 13.sp
    val CardTankNameLineHeight = 17.sp
    val CardShadowElevation = 8.dp
    val CardPressedScale = 0.972f
    const val CardBorderIdleAlpha = 0.68f
}
