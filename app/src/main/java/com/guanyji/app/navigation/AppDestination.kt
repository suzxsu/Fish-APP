package com.guanyji.app.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.ui.graphics.vector.ImageVector
import com.guanyji.app.feature.overview.FishIcons

enum class AppDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val title: String,
    val description: String,
) {
    Overview("overview", "总览", Icons.Outlined.Home, "今日概览", "添加鱼缸后，这里会汇总你的养护安排。"),
    Fish("fish", "我的鱼", Icons.Outlined.Favorite, "我的鱼", "鱼只档案会保存在本机，目前还没有记录。"),
    Tanks("tanks", "鱼缸", Icons.Outlined.Home, "鱼缸", "创建鱼缸后，可在这里查看环境与养护记录。"),
    Care("care", "养护", Icons.Outlined.DateRange, "养护计划", "你的本地养护任务会显示在这里。"),
    Profile("profile", "我的", Icons.Outlined.Person, "我的", "统计、设置、备份与关于。"),
}

/**
 * 底部导航使用的水族主题图标。
 * 保留 [AppDestination.icon] 作为原有字段与默认图标，仅底部导航切换到统一线条风格的图标。
 */
internal fun AppDestination.navIcon(): ImageVector = when (this) {
    AppDestination.Overview -> Icons.Outlined.Home
    AppDestination.Fish -> FishIcons.Fish
    AppDestination.Tanks -> FishIcons.Aquarium
    AppDestination.Care -> FishIcons.Calendar
    AppDestination.Profile -> Icons.Outlined.Person
}
