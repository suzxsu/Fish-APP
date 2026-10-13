package com.guanyji.app.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.ui.graphics.vector.ImageVector

enum class AppDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val title: String,
    val description: String,
) {
    Overview("overview", "总览", Icons.Outlined.Home, "今日概览", "添加鱼缸后，这里会汇总你的养护安排。"),
    Fish("fish", "我的鱼", Icons.Outlined.Favorite, "我的鱼", "鱼只档案会保存在本机，目前还没有记录。"),
    Tanks("tanks", "鱼缸", Icons.Outlined.Home, "我的鱼缸", "创建鱼缸后，可在这里查看环境与养护记录。"),
    Care("care", "养护", Icons.Outlined.DateRange, "养护计划", "你的本地养护任务会显示在这里。"),
    Profile("profile", "我的", Icons.Outlined.Person, "个人设置", "单位、提醒和数据备份设置。"),
}
