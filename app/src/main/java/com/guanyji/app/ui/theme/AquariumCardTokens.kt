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

    // 立体光影：受光面高光、冷色反光与暗面。全部为低透明度叠加，
    // 不使用真实模糊（RenderEffect 需 API 31，本项目 minSdk 26）。
    val RimHighlight = Color(0xFFFFFFFF)
    val RimHighlightSoft = Color(0xFFBFE9FF)
    val RimShade = Color(0xFF04121F)
    val DepthShadowDeep = Color(0xFF06121F)
    val PanelInnerShade = Color(0xFF04121F)
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
    /** 卡片主圆角；外缘描边与内斜面共用同一数值，避免两层弧线错位。 */
    val CardCornerRadius = 16.dp
    val CardShape = RoundedCornerShape(CardCornerRadius)
    val ChipShape = RoundedCornerShape(999.dp)
    val FieldShape = RoundedCornerShape(14.dp)
    /** 照片固定 4:3；整卡高度随信息面板内容自适应。 */
    const val ImageAspectRatio = 4f / 3f
    /** 照片与信息面板交界：0.5dp 金色细线，只作品牌细节，不能压过交界高光。 */
    val PanelTopDividerHeight = 0.5.dp
    val PanelTopDividerStartAlpha = 0.5f
    val PanelTopDividerEndAlpha = 0f
    /** 外缘描边宽度：比纯色描边略粗，才能同时承载亮面与暗面两段渐变。 */
    val CardBorderWidth = 1.35.dp
    val CardBorderColor = Color(0xFF9FD3F0)
    const val CardBorderMiddleAlpha = 0.55f
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

    // ---------- 卡片立体光影 ----------
    // 依据 docs/产品设计/鱼缸卡片设计规范展示板.png 与设计文档 3.1「卡片立体规范」：
    // 立体感来自「受光描边 + 内斜面 + 底部反光 + 层间阴影 + 外发光」，不做真实模糊。

    /** 外缘描边受光渐变：左上亮白 → 中段主题色 → 右下压深，形成固定光源方向。 */
    const val RimTopAlpha = 0.72f
    const val RimAccentAlpha = 0.72f
    const val RimBottomAlpha = 0.52f

    /**
     * 描边光晕：在 1.35dp 实线外侧再垫一圈低透明的宽描边，
     * 让亮边像光透过玻璃渗出，而不是一条画上去的白线（无模糊可用的替代方案）。
     */
    val CardRimHaloWidth = 3.2.dp
    const val CardRimHaloAlphaFactor = 0.28f

    /** 顶部镜面反光：只在上边缘中段出现并两端淡出，避免整圈白线看起来像塑料描边。 */
    const val CardTopGlintAlpha = 0.55f
    const val CardTopGlintInsetFactor = 0.85f

    /** 内斜面：模拟玻璃切面厚度，内缩一层是“卡片有厚度”的关键。 */
    val CardInnerRimWidth = 1.dp
    val CardInnerRimInset = 2.25.dp
    const val InnerRimTopAlpha = 0.85f
    const val InnerRimSoftAlpha = 0.30f
    const val InnerRimBottomAlpha = 0.42f

    /** 底部反光边：贴底一条淡蓝亮线，模拟玻璃底面折射。 */
    val CardBottomReflectWidth = 0.9.dp
    val CardBottomReflectInset = 1.6.dp
    const val CardBottomReflectAlpha = 0.45f

    /** 外发光：着色阴影需 API 28+，低版本由 cardOuterDepth 自动跳过该层。 */
    val CardGlowElevation = 14.dp
    const val CardGlowAmbientAlpha = 0.08f
    const val CardGlowSpotAlpha = 0.15f

    /** 接地投影：系统 elevation 只做极淡的环境压暗，具体悬浮感交给接触阴影。 */
    val CardShadowElevationPressed = 2.dp
    const val CardDepthAmbientAlpha = 0.34f
    const val CardDepthSpotAlpha = 0.40f
    const val CardDepthAnimationMillis = 160

    /**
     * 接触阴影：Android 的 elevation 阴影方向固定且很淡，撑不起“离开页面”的观感，
     * 因此在卡片外侧自绘一圈向下偏移、向内加深的暗带（层叠描边模拟柔边）。
     */
    const val CardContactShadowLayers = 6
    val CardContactShadowStep = 0.5.dp
    val CardContactShadowOffset = 1.dp
    const val CardContactShadowLayerAlpha = 0.036f

    /** 按压 3D 倾斜：以底边为轴，正角度=顶边向后倾，符合“按下”的视觉预期。 */
    const val CardPressTiltX = 3f
    const val CardCameraDistance = 12f

    /** 拖拽排序时"拿起卡片"：直立、放大、投影拉长，与按下的收紧形成对比。 */
    const val CardLiftedScale = 1.045f
    val CardShadowElevationLifted = 22.dp

    /** 照片下沿暗带：让信息面板看起来“压在”照片上，形成 Z 轴分层。 */
    val PhotoBottomShadeHeight = 18.dp
    const val PhotoBottomShadeAlpha = 0.42f

    /** 面板交界高光 + 面板顶部内阴影。 */
    val PanelTopHairlineHeight = 1.dp
    const val PanelTopHairlineAlpha = 0.55f
    const val PanelTopHairlineAccentAlpha = 0.30f
    val PanelInnerShadeHeight = 6.dp
    const val PanelInnerShadeAlpha = 0.35f
}
