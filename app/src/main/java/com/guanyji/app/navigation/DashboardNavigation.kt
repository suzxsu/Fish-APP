package com.guanyji.app.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.guanyji.app.feature.overview.DashboardTokens

/**
 * 总览改造后的底部导航栏。
 * 保留原有五个入口、路由与切换行为，只调整视觉样式（浅色背景、圆角选中态、统一图标尺寸）。
 */
@Composable
internal fun DashboardBottomNavigation(current: AppDestination, onSelect: (AppDestination) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DashboardTokens.SurfaceCard)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .drawBehind {
                drawRect(
                    color = Color(0x140C3A5E),
                    topLeft = Offset(0f, 0f),
                    size = Size(size.width, 1.dp.toPx()),
                )
            }
            .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppDestination.entries.forEach { destination ->
            val selected = destination == current
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("destination-${destination.route}")
                    .clip(RoundedCornerShape(18.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClickLabel = "切换到${destination.label}",
                        onClick = { onSelect(destination) },
                    )
                    .padding(vertical = 6.dp)
                    .semantics { this.selected = selected },
            ) {
                Box(
                    modifier = Modifier
                        .width(52.dp)
                        .heightIn(min = 30.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (selected) DashboardTokens.NavSelection else Color.Transparent),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = destination.navIcon(),
                        contentDescription = destination.label,
                        tint = if (selected) DashboardTokens.DeepBlue else DashboardTokens.NavIdle,
                        modifier = Modifier.size(23.dp),
                    )
                }
                Text(
                    text = destination.label,
                    color = if (selected) DashboardTokens.DeepBlue else DashboardTokens.NavIdle,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                )
            }
        }
    }
}
