package com.guanyji.app.feature.overview

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.guanyji.app.core.designsystem.EmptyState
import com.guanyji.app.core.designsystem.InfoCard
import com.guanyji.app.data.local.*
import com.guanyji.app.feature.photo.ManagedPhotoImage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun OverviewScreen(
    onAddTank: () -> Unit,
    onAddFish: () -> Unit,
    onFeeding: () -> Unit,
    onWater: (String?) -> Unit,
    onTank: (String) -> Unit,
    onFish: (String) -> Unit,
    onCare: (String?) -> Unit,
    onMessage: (String) -> Unit,
    vm: OverviewViewModel = viewModel(factory = OverviewViewModel.factory(LocalContext.current.applicationContext as Application)),
) {
    val overview by vm.state.collectAsStateWithLifecycle()
    val tanks = overview.tanks
    val tasks = overview.tasks
    val fishRows = overview.fish
    val fish = fishRows.filter { it.fish.status in setOf(FishStatus.active, FishStatus.quarantine) }
        .sortedByDescending { it.lastMeasuredAt ?: it.fish.addedAt }.take(8)
    val fishCountByTank = remember(fishRows) { fishRows.filter { it.fish.status in setOf(FishStatus.active, FishStatus.quarantine) }.groupingBy { it.fish.tankId }.eachCount() }
    var completing by remember { mutableStateOf<CareTask?>(null) }
    var saving by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(SimpleDateFormat("M月d日 · EEEE", Locale.CHINA).format(Date()), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("今日总览", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            }
            IconButton(onClick = { onCare(null) }) { BadgedBox(badge = { if (tasks.isNotEmpty()) Badge { Text(tasks.size.toString()) } }) { Icon(Icons.Outlined.Notifications, contentDescription = "养护提醒") } }
        }
        SectionTitle("我的鱼缸", "${tanks.size} 个")
        if (tanks.isEmpty()) EmptyAction("还没有鱼缸", "创建鱼缸后，这里会显示环境和养护概况。", "添加鱼缸", onAddTank)
        else Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            tanks.forEach { row ->
                val photoFlow = remember(row.tank.photoId) { vm.photo(row.tank.photoId) }
                val photo by (photoFlow?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(null) })
                ElevatedCard(Modifier.width(260.dp).clickable { onTank(row.tank.id) }) {
                    Column {
                        if (photo != null) ManagedPhotoImage(photo, true, Modifier.fillMaxWidth().height(130.dp))
                        else Surface(Modifier.fillMaxWidth().height(130.dp), color = MaterialTheme.colorScheme.surfaceVariant) { Box(contentAlignment = Alignment.Center) { Text("尚未添加鱼缸照片", style = MaterialTheme.typography.bodySmall) } }
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(row.tank.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold); Text(if (row.tank.type == TankType.freshwater) "淡水" else "海水", color = MaterialTheme.colorScheme.primary) }
                            Text("在养鱼 ${fishCountByTank[row.tank.id] ?: 0} 条 · 待办 ${tasks.count { it.tankId == row.tank.id || it.tankId == null && it.fishId?.let { id -> fishRows.firstOrNull { summary -> summary.fish.id == id }?.fish?.tankId } == row.tank.id }} 项", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }

        SectionTitle("今日任务", "${tasks.size} 项")
        if (tasks.isEmpty()) EmptyAction("今天暂无计划任务", "未安排的任务不会被计为已完成。", "查看养护", { onCare(null) })
        else tasks.take(5).forEach { task ->
            val tankName = tanks.firstOrNull { it.tank.id == task.tankId }?.tank?.name
            ElevatedCard(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(task.displayTitleForOverview(), style = MaterialTheme.typography.titleSmall)
                        Text("${time(task.plannedAt)} · ${tankName ?: "未指定鱼缸"} · ${if (task.status == CareTaskStatus.overdue) "已逾期" else "待完成"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = {
                        if (task.type == CareTaskType.feeding) onFeeding()
                        else if (task.type == CareTaskType.water_change || task.type == CareTaskType.water_test || task.type == CareTaskType.salinity_test) onWater(task.tankId)
                        else completing = task
                    }) { Text(if (task.type == CareTaskType.feeding || task.type == CareTaskType.water_change || task.type == CareTaskType.water_test || task.type == CareTaskType.salinity_test) "记录" else "完成") }
                    if (task.type == CareTaskType.maintenance || task.type == CareTaskType.custom) {
                        IconButton(enabled = !saving, onClick = { completing = task }) { Icon(Icons.Outlined.Check, contentDescription = "完成任务") }
                    }
                }
            }
        }

        SectionTitle("最近水质", null)
        if (tanks.isEmpty()) EmptyAction("暂无可查看的鱼缸", "创建鱼缸后，最近测量摘要会显示在这里。", "添加鱼缸", onAddTank)
        else tanks.forEach { row ->
            val tests by remember(row.tank.id) { vm.waterTests(row.tank.id) }.collectAsStateWithLifecycle()
            val latest = tests.firstOrNull()
            InfoCard(row.tank.name, content = {
                if (latest == null) Text("尚无测量数据", color = MaterialTheme.colorScheme.onSurfaceVariant)
                else {
                    val age = System.currentTimeMillis() - latest.test.measuredAt
                    if (age > 7L * 24 * 60 * 60 * 1000) Text("测量已超过 7 天 · 建议复测", color = MaterialTheme.colorScheme.error)
                    Text("测量时间：${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(latest.test.measuredAt))}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    latest.values.filter { it.value != null }.take(4).forEach { value ->
                        Text("${parameterName(value.parameterCode)}  ${value.value!!.pretty()} ${value.unit}")
                    }
                    if (latest.values.none { it.value != null }) Text("本次记录没有有效测量值", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = { onWater(row.tank.id) }) { Text("记录水质") }
            }, modifier = Modifier.padding(bottom = 8.dp))
        }

        SectionTitle("最近关注的鱼", null)
        if (fish.isEmpty()) EmptyAction("还没有鱼只", "添加鱼只档案后，可查看入缸时长与最近体长。", "添加鱼", onAddFish)
        else fish.forEach { item ->
            val photoFlow = remember(item.fish.coverPhotoId) { vm.photo(item.fish.coverPhotoId) }
            val photo by (photoFlow?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(null) })
            ElevatedCard(Modifier.fillMaxWidth().padding(bottom = 8.dp).clickable { onFish(item.fish.id) }) {
                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (photo != null) ManagedPhotoImage(photo, true, Modifier.size(64.dp)) else Surface(Modifier.size(64.dp), shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant) { Box(contentAlignment = Alignment.Center) { Text("鱼") } }
                    Column(Modifier.weight(1f)) {
                        Text(item.fish.name, style = MaterialTheme.typography.titleSmall)
                        Text("入缸 ${duration(item.fish.addedAt)}", style = MaterialTheme.typography.bodySmall)
                        Text(item.lastLength?.let { "最近体长 ${it.pretty()} ${item.lastLengthUnit ?: ""}" } ?: "尚无体长记录", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        SectionTitle("快捷入口", null)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Shortcut("添加鱼缸", Icons.Outlined.Add, onAddTank, Modifier.weight(1f))
            Shortcut("添加鱼", Icons.Outlined.Add, onAddFish, Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Shortcut("记录喂食", Icons.Outlined.Add, onFeeding, Modifier.weight(1f))
            Shortcut("记录水质", Icons.Outlined.Check, { if (tanks.isEmpty()) onMessage("请先添加鱼缸，再记录对应鱼缸的水质。") else onWater(tanks.first().tank.id) }, Modifier.weight(1f))
        }
    }
    completing?.let { task -> AlertDialog(
        onDismissRequest = { if (!saving) completing = null },
        title = { Text("完成${task.displayTitleForOverview()}") },
        text = { Text("确认已完成这项任务？此操作会写入养护记录。") },
        confirmButton = { TextButton(enabled = !saving, onClick = { saving = true; vm.completeTask(task, { saving = false; completing = null }, { saving = false; message = it }) }) { Text(if (saving) "保存中…" else "确认完成") } },
        dismissButton = { TextButton(enabled = !saving, onClick = { completing = null }) { Text("取消") } },
    ) }
    message?.let { text -> AlertDialog(onDismissRequest = { message = null }, text = { Text(text) }, confirmButton = { TextButton(onClick = { message = null }) { Text("知道了") } }) }
}

@Composable private fun SectionTitle(title: String, trailing: String?) {
    Row(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        trailing?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable private fun EmptyAction(title: String, description: String, action: String, onClick: () -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { EmptyState(title, description); TextButton(onClick = onClick) { Text(action) } } }
}

@Composable private fun Shortcut(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(onClick = onClick, modifier = modifier.heightIn(min = 50.dp)) { Icon(icon, contentDescription = null); Spacer(Modifier.width(6.dp)); Text(title) }
}

private fun time(value: Long) = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(value))
private fun duration(value: Long): String {
    val start = java.time.Instant.ofEpochMilli(value).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
    val days = java.time.temporal.ChronoUnit.DAYS.between(start, java.time.LocalDate.now())
    return if (days < 0) "日期待核对" else "$days 天"
}
private fun Double.pretty() = if (this % 1.0 == 0.0) toLong().toString() else "%.1f".format(Locale.getDefault(), this)
private fun CareTask.displayTitleForOverview() = when (type) {
    CareTaskType.feeding -> "喂食"
    CareTaskType.water_change -> "换水"
    CareTaskType.water_test -> "水质检测"
    CareTaskType.salinity_test -> "盐度检测"
    CareTaskType.maintenance -> "设备维护"
    CareTaskType.custom -> customTitle ?: "自定义任务"
}
private fun parameterName(code: String) = when (code) {
    "temperature" -> "水温"
    "ph" -> "pH"
    "ammonia" -> "氨/铵"
    "nitrite" -> "亚硝酸盐"
    "nitrate" -> "硝酸盐"
    "salinity" -> "盐度"
    "specific_gravity" -> "比重"
    "alkalinity" -> "碱度"
    else -> code
}
