package com.guanyji.app.feature.profile

import android.Manifest
import android.app.Application
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.guanyji.app.core.designsystem.InfoCard
import com.guanyji.app.core.designsystem.PageTitle
import com.guanyji.app.data.local.CareNotificationScheduler
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ProfileScreen(onStatistics: () -> Unit, onSettings: () -> Unit, onAbout: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PageTitle("我的", "观鱼记 · 本地养护记录")
        InfoCard("数据概览", content = {
            Text("鱼缸、鱼只和养护记录保存在本机。统计只基于你保存的记录。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            FilledTonalButton(onClick = onStatistics, modifier = Modifier.fillMaxWidth()) { Text("统计报表") }
        })
        ProfileEntry("数据与偏好", "单位、日期格式、主题、通知和数据备份", onSettings)
        ProfileEntry("关于和帮助", "版本信息、数据保存方式和使用说明", onAbout)
        Text("观鱼记  ·  第一阶段本地版", Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun SettingsScreen(onBack: () -> Unit, onAbout: () -> Unit, vm: ProfileViewModel = viewModel(factory = ProfileViewModel.factory(LocalContext.current.applicationContext as Application))) {
    val preferences by vm.preferences.collectAsStateWithLifecycle()
    val operation by vm.operation.collectAsStateWithLifecycle()
    val storage by vm.storage.collectAsStateWithLifecycle()
    val databaseStorage by vm.databaseStorage.collectAsStateWithLifecycle()
    val photoStorage by vm.photoStorage.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(operation) {
        if (operation is ProfileOperation.Restored) (context as? android.app.Activity)?.let { activity ->
            val restart = Intent(activity, com.guanyji.app.MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            activity.startActivity(restart)
            activity.finish()
        }
    }
    var showRestoreConfirmation by remember { mutableStateOf(false) }
    var pendingRestore by remember { mutableStateOf<android.net.Uri?>(null) }
    var permissionMessage by remember { mutableStateOf<String?>(null) }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) permissionMessage = "系统通知权限未开启；任务仍保留在养护列表，可在系统设置中允许提醒。"
    }
    val exportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri -> uri?.let(vm::export) }
    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::inspect) }
    val prefs = preferences
    Column(Modifier.fillMaxSize()) {
        PageTitle("设置", onBack = onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PreferenceGroup("展示单位") {
                ChoiceRow("温度", listOf("°C", "°F"), prefs.temperature) { vm.setPreference("unit.temperature", it) }
                ChoiceRow("长度", listOf("cm", "in"), prefs.length) { vm.setPreference("unit.length", it) }
                ChoiceRow("体积", listOf("L", "gal"), prefs.volume) { vm.setPreference("unit.volume", it) }
                ChoiceRow("重量", listOf("g", "kg"), prefs.weight) { vm.setPreference("unit.weight", it) }
                Text("单位只改变显示换算；历史记录保留录入时的数值和单位。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            PreferenceGroup("外观与提醒") {
                ChoiceRow("日期格式", listOf("yyyy-MM-dd", "MM/dd/yyyy", "dd/MM/yyyy"), prefs.dateFormat) { vm.setPreference("date.format", it) }
                ChoiceRow("主题", listOf("system", "light", "dark"), prefs.theme, mapOf("system" to "跟随系统", "light" to "浅色", "dark" to "深色")) { vm.setPreference("theme", it) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text("本地通知"); Text("只用于本机养护提醒", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    Switch(checked = prefs.notifications, onCheckedChange = { enabled ->
                        vm.setNotifications(enabled)
                        if (enabled && Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }, modifier = Modifier.padding(start = 8.dp))
                }
                permissionMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            }
            PreferenceGroup("存储与备份") {
                Text("照片 ${photoStorage.toSize()} · 数据库 ${databaseStorage.toSize()} · 总计 ${storage.toSize()}", style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { exportPicker.launch("观鱼记备份-${SimpleDateFormat("yyyyMMdd-HHmm", Locale.CHINA).format(Date())}.zip") }) { Text("导出备份") }
                    OutlinedButton(onClick = { importPicker.launch(arrayOf("application/zip", "application/octet-stream")) }) { Text("选择备份") }
                }
            }
            ProfileEntry("帮助与关于", "产品说明和本地数据说明", onAbout)
            when (val state = operation) {
                ProfileOperation.Idle -> Unit
                is ProfileOperation.Working -> Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp); Text(state.message) }
                is ProfileOperation.Done -> Text(state.message, color = MaterialTheme.colorScheme.primary)
                is ProfileOperation.Restored -> Text(state.message, color = MaterialTheme.colorScheme.primary)
                is ProfileOperation.Failed -> Text(state.message, color = MaterialTheme.colorScheme.error)
                is ProfileOperation.Ready -> {
                    val summary = state.summary
                    InfoCard("备份已校验", content = {
                        Text("备份日期：${summary.createdAt.formatDate()}")
                        Text("${summary.tanks} 个鱼缸 · ${summary.fish} 条鱼 · ${summary.growthRecords} 条成长记录")
                        Text("喂食 ${summary.feedingRecords} · 水质 ${summary.waterQualityRecords} · 换水 ${summary.waterChangeRecords} · 养护 ${summary.careRecords} · 照片 ${summary.photos}")
                        Text("恢复将替换当前鱼缸、鱼只、记录、任务、设置和照片。继续前会先创建当前数据安全副本。", color = MaterialTheme.colorScheme.error)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = vm::clearOperation) { Text("取消") }
                            Button(onClick = { pendingRestore = state.uri; showRestoreConfirmation = true }) { Text("继续恢复") }
                        }
                    })
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
    if (showRestoreConfirmation) AlertDialog(
        onDismissRequest = { showRestoreConfirmation = false }, title = { Text("覆盖本地数据？") },
        text = { Text("恢复会替换当前鱼缸、鱼只、所有养护记录、任务、设置和照片。系统会先备份当前数据；若验证或安装中断，将在下次启动时回滚。") },
        confirmButton = { TextButton(onClick = { showRestoreConfirmation = false; pendingRestore?.let(vm::restore); pendingRestore = null }) { Text("确认恢复") } },
        dismissButton = { TextButton(onClick = { showRestoreConfirmation = false; pendingRestore = null }) { Text("取消") } },
    )
}

@Composable
fun AboutHelpScreen(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PageTitle("关于和帮助", onBack = onBack)
        InfoCard("观鱼记", content = {
            Text("观鱼记第一阶段本地版", fontWeight = FontWeight.SemiBold)
            Text("所有鱼缸、鱼只、照片与养护记录保存在本机，不需要账号或网络。")
        })
        InfoCard("数据与单位", content = {
            Text("更改展示单位不会改写历史记录。统计按原始测量值及其单位换算展示；缺少的测量保持为空。")
        })
        InfoCard("通知", content = { Text("提醒由本地排程。系统通知权限关闭时，任务仍会保留在养护列表。") })
        InfoCard("备份", content = { Text("备份包包括本地数据库和照片。请将备份文件保存在安全的位置。恢复前会验证文件并提示覆盖现有记录。") })
        InfoCard("统计", content = { Text("任务完成率只使用实际执行记录和明确跳过的计划；没有安排任务的日期不视为失败。") })
    }
}

@Composable
private fun PreferenceGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold); content() } }
}

@Composable
private fun ChoiceRow(title: String, choices: List<String>, current: String, labels: Map<String, String> = emptyMap(), onChoose: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f))
        Box {
            OutlinedButton(onClick = { expanded = true }) { Text(labels[current] ?: current) }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                choices.forEach { value -> DropdownMenuItem(text = { Text(labels[value] ?: value) }, onClick = { onChoose(value); expanded = false }) }
            }
        }
    }
}

@Composable
private fun ProfileEntry(title: String, subtitle: String, onClick: () -> Unit) {
    OutlinedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.Medium); Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Text("›", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleLarge)
        }
    }
}

private fun Long.toSize(): String = when {
    this < 1024 -> "$this B"
    this < 1024 * 1024 -> String.format(Locale.getDefault(), "%.1f KB", this / 1024.0)
    else -> String.format(Locale.getDefault(), "%.1f MB", this / (1024.0 * 1024.0))
}

private fun Long.formatDate() = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date(this))
