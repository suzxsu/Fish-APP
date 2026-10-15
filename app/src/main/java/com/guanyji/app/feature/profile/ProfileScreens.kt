package com.guanyji.app.feature.profile

import android.Manifest
import android.app.Application
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.guanyji.app.R
import com.guanyji.app.core.designsystem.InfoCard
import com.guanyji.app.core.designsystem.PageTitle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen(
    onStatistics: () -> Unit,
    onSettings: (String?) -> Unit,
    onAbout: (String) -> Unit,
    vm: ProfileViewModel = viewModel(factory = ProfileViewModel.factory(LocalContext.current.applicationContext as Application)),
) {
    val stats by vm.overview.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    Scaffold(snackbarHost = { SnackbarHost(snackbar) }, containerColor = Color(0xFFF1F8FC)) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ProfileHeader(onEdit = { scope.launch { snackbar.showSnackbar("资料编辑暂不可用") } })
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                when (val state = stats) {
                    ProfileOverviewState.Loading -> Card { Box(Modifier.fillMaxWidth().height(148.dp), Alignment.Center) { CircularProgressIndicator() } }
                    is ProfileOverviewState.Failed -> Card { Text("统计暂时无法加载：${state.message}", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error) }
                    is ProfileOverviewState.Ready -> StatisticsOverviewCard(state, onStatistics)
                }
                SettingsOverviewCard(onSettings)
                AboutHelpSection(onAbout)
                Text("观鱼记 · 第一阶段本地版", Modifier.align(Alignment.CenterHorizontally).padding(bottom = 12.dp), style = MaterialTheme.typography.bodySmall, color = Color(0xFF71879A))
            }
        }
    }
}

@Composable
private fun ProfileHeader(onEdit: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(222.dp)) {
        Image(painter = androidx.compose.ui.res.painterResource(R.drawable.care_header), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x55052D50), Color(0xC6074773)))))
        Row(Modifier.align(Alignment.CenterStart).padding(horizontal = 22.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.size(78.dp).clip(CircleShape).background(Color.White.copy(alpha = .2f)).border(2.dp, Color.White, CircleShape).clickable(onClick = onEdit).semantics { contentDescription = "编辑个人资料" }, contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Person, null, Modifier.size(46.dp), tint = Color.White)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("鱼友", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color.White)
                    IconButton(onClick = onEdit, modifier = Modifier.size(40.dp)) { Icon(Icons.Outlined.Edit, "编辑个人资料", tint = Color.White) }
                }
                Text("记录每一条鱼的成长", style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = .92f))
                Surface(color = Color.White.copy(alpha = .18f), shape = RoundedCornerShape(20.dp)) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Outlined.Star, null, Modifier.size(16.dp), tint = Color(0xFFB7F2EE)); Text("养鱼新手", color = Color.White, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatisticsOverviewCard(state: ProfileOverviewState.Ready, onStatistics: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("数据概览", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color(0xFF123B60))
                    Text("本地养护数据实时汇总", style = MaterialTheme.typography.bodySmall, color = Color(0xFF66819A))
                }
                FilledTonalButton(onClick = onStatistics, contentPadding = PaddingValues(horizontal = 12.dp)) {
                    Icon(Icons.Outlined.DateRange, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("统计报表")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatisticTile("鱼缸总数", state.tanks, Icons.Outlined.Home, Color(0xFFE5F3FF), Color(0xFF1989E8), Modifier.weight(1f))
                StatisticTile("鱼只总数", state.fish, Icons.Outlined.Favorite, Color(0xFFE2F8F3), Color(0xFF14A98D), Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatisticTile("待办任务", state.tasks, Icons.Outlined.DateRange, Color(0xFFF0EAFE), Color(0xFF8555DF), Modifier.weight(1f))
                StatisticTile("水质异常", state.waterAlerts, Icons.Outlined.Info, Color(0xFFE7F5FF), Color(0xFF138FC5), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun StatisticTile(title: String, count: Int, icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, accent: Color, modifier: Modifier) {
    Row(modifier.fillMaxWidth().heightIn(min = 76.dp).clip(RoundedCornerShape(14.dp)).background(tint).padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(38.dp).clip(CircleShape).background(Color.White.copy(alpha = .75f)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = accent, modifier = Modifier.size(22.dp)) }
        Column {
            Text(count.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color(0xFF123B60))
            Text(title, style = MaterialTheme.typography.labelMedium, color = Color(0xFF56728D), maxLines = 1)
        }
    }
}

@Composable
private fun SettingsOverviewCard(onSettings: (String?) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(Modifier.fillMaxWidth().padding(bottom = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Settings, null, Modifier.size(32.dp), tint = Color(0xFF168BE4)); Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) { Text("数据与偏好", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color(0xFF123B60)); Text("个性化设置你的养鱼体验", style = MaterialTheme.typography.bodySmall, color = Color(0xFF71879A)) }
            IconButton(onClick = { onSettings(null) }) { Icon(Icons.AutoMirrored.Outlined.ArrowForward, "打开设置") }
            }
            PreferenceLink("展示单位", "设置长度、重量、温度等显示单位", Icons.Outlined.Edit, Color(0xFF168BE4)) { onSettings("units") }
            PreferenceLink("外观与提醒", "主题风格、日期格式、本地通知提醒", Icons.Outlined.Star, Color(0xFF8D55E8)) { onSettings("appearance") }
            PreferenceLink("存储与备份", "数据导出、备份与恢复数据", Icons.Outlined.Info, Color(0xFF12A98D)) { onSettings("storage") }
        }
    }
}

@Composable
private fun PreferenceLink(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, accent: Color, onClick: () -> Unit) {
    Surface(onClick = onClick, color = Color(0xFFF4F9FC), shape = RoundedCornerShape(14.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 68.dp).padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(42.dp).clip(CircleShape).background(accent.copy(alpha = .13f)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = accent) }
            Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = Color(0xFF183D61)); Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color(0xFF71879A)) }
            Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, tint = Color(0xFF849BB0))
        }
    }
}

@Composable
private fun AboutHelpSection(onAbout: (String) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            PreferenceLink("关于", "了解应用版本、本地数据与功能", Icons.Outlined.Info, Color(0xFF168BE4)) { onAbout("about") }
            PreferenceLink("帮助", "常见问题与使用指南", Icons.Outlined.Info, Color(0xFF8D55E8)) { onAbout("help") }
        }
    }
}

@Composable
fun SettingsScreen(onBack: () -> Unit, onAbout: () -> Unit, initialSection: String? = null, vm: ProfileViewModel = viewModel(factory = ProfileViewModel.factory(LocalContext.current.applicationContext as Application))) {
    val preferences by vm.preferences.collectAsStateWithLifecycle()
    val operation by vm.operation.collectAsStateWithLifecycle()
    val storage by vm.storage.collectAsStateWithLifecycle()
    val databaseStorage by vm.databaseStorage.collectAsStateWithLifecycle()
    val photoStorage by vm.photoStorage.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(operation) {
        if (operation is ProfileOperation.Restored) (context as? android.app.Activity)?.let { activity ->
            val restart = Intent(activity, com.guanyji.app.MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            activity.startActivity(restart); activity.finish()
        }
    }
    var showRestoreConfirmation by remember { mutableStateOf(false) }
    var pendingRestore by remember { mutableStateOf<android.net.Uri?>(null) }
    var permissionMessage by remember { mutableStateOf<String?>(null) }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (!granted) permissionMessage = "系统通知权限未开启；任务仍保留在养护列表，可在系统设置中允许提醒。" }
    val exportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri -> uri?.let(vm::export) }
    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::inspect) }
    val prefs = preferences
    val unitsRequester = remember { BringIntoViewRequester() }
    val appearanceRequester = remember { BringIntoViewRequester() }
    val storageRequester = remember { BringIntoViewRequester() }
    LaunchedEffect(initialSection) {
        when (initialSection) {
            "units" -> unitsRequester.bringIntoView()
            "appearance" -> appearanceRequester.bringIntoView()
            "storage" -> storageRequester.bringIntoView()
        }
    }
    Column(Modifier.fillMaxSize()) {
        PageTitle("设置", onBack = onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (initialSection != null) Text("已定位到${mapOf("units" to "展示单位", "appearance" to "外观与提醒", "storage" to "存储与备份")[initialSection] ?: "设置"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            PreferenceGroup("展示单位", Modifier.bringIntoViewRequester(unitsRequester)) {
                ChoiceRow("温度", listOf("°C", "°F"), prefs.temperature) { vm.setPreference("unit.temperature", it) }
                ChoiceRow("长度", listOf("cm", "in"), prefs.length) { vm.setPreference("unit.length", it) }
                ChoiceRow("体积", listOf("L", "gal"), prefs.volume) { vm.setPreference("unit.volume", it) }
                ChoiceRow("重量", listOf("g", "kg"), prefs.weight) { vm.setPreference("unit.weight", it) }
                Text("单位只改变显示换算；历史记录保留录入时的数值和单位。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            PreferenceGroup("外观与提醒", Modifier.bringIntoViewRequester(appearanceRequester)) {
                ChoiceRow("日期格式", listOf("yyyy-MM-dd", "MM/dd/yyyy", "dd/MM/yyyy"), prefs.dateFormat) { vm.setPreference("date.format", it) }
                ChoiceRow("主题", listOf("system", "light", "dark"), prefs.theme, mapOf("system" to "跟随系统", "light" to "浅色", "dark" to "深色")) { vm.setPreference("theme", it) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text("本地通知"); Text("只用于本机养护提醒", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    Switch(checked = prefs.notifications, onCheckedChange = { enabled ->
                        vm.setNotifications(enabled)
                        if (enabled && Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }, modifier = Modifier.padding(start = 8.dp))
                }
                permissionMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            }
            PreferenceGroup("存储与备份", Modifier.bringIntoViewRequester(storageRequester)) {
                Text("照片 ${photoStorage.toSize()} · 数据库 ${databaseStorage.toSize()} · 总计 ${storage.toSize()}", style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { exportPicker.launch("观鱼记备份-${SimpleDateFormat("yyyyMMdd-HHmm", Locale.CHINA).format(Date())}.zip") }) { Text("导出备份") }
                    OutlinedButton(onClick = { importPicker.launch(arrayOf("application/zip", "application/octet-stream")) }) { Text("选择备份") }
                }
            }
            ProfileEntry("帮助与关于", "产品说明和本地数据说明", onAbout)
            when (val state = operation) {
                ProfileOperation.Idle -> Unit
                is ProfileOperation.Working -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp); Text(state.message) }
                is ProfileOperation.Done -> Text(state.message, color = MaterialTheme.colorScheme.primary)
                is ProfileOperation.Restored -> Text(state.message, color = MaterialTheme.colorScheme.primary)
                is ProfileOperation.Failed -> Text(state.message, color = MaterialTheme.colorScheme.error)
                is ProfileOperation.Ready -> {
                    val summary = state.summary
                    InfoCard("备份已校验", content = {
                        Text("备份日期：${summary.createdAt.formatDate()}"); Text("${summary.tanks} 个鱼缸 · ${summary.fish} 条鱼 · ${summary.growthRecords} 条成长记录")
                        Text("喂食 ${summary.feedingRecords} · 水质 ${summary.waterQualityRecords} · 换水 ${summary.waterChangeRecords} · 养护 ${summary.careRecords} · 照片 ${summary.photos}")
                        Text("恢复将替换当前鱼缸、鱼只、记录、任务、设置和照片。继续前会先创建当前数据安全副本。", color = MaterialTheme.colorScheme.error)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { TextButton(onClick = vm::clearOperation) { Text("取消") }; Button(onClick = { pendingRestore = state.uri; showRestoreConfirmation = true }) { Text("继续恢复") } }
                    })
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
    if (showRestoreConfirmation) AlertDialog(onDismissRequest = { showRestoreConfirmation = false }, title = { Text("覆盖本地数据？") }, text = { Text("恢复会替换当前鱼缸、鱼只、所有养护记录、任务、设置和照片。系统会先备份当前数据；若验证或安装中断，将在下次启动时回滚。") }, confirmButton = { TextButton(onClick = { showRestoreConfirmation = false; pendingRestore?.let(vm::restore); pendingRestore = null }) { Text("确认恢复") } }, dismissButton = { TextButton(onClick = { showRestoreConfirmation = false; pendingRestore = null }) { Text("取消") } })
}

@Composable
fun AboutHelpScreen(onBack: () -> Unit, section: String = "about") {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PageTitle(if (section == "help") "帮助" else "关于", onBack = onBack)
        if (section == "about") {
            InfoCard("观鱼记", content = { Text("观鱼记第一阶段本地版", fontWeight = FontWeight.SemiBold); Text("所有鱼缸、鱼只、照片与养护记录保存在本机，不需要账号或网络。") })
            InfoCard("版本", content = { Text("版本 ${context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "未知"}") })
            InfoCard("数据与单位", content = { Text("更改展示单位不会改写历史记录。统计按原始测量值及其单位换算展示；缺少的测量保持为空。") })
        } else {
            InfoCard("开始使用", content = { Text("先创建鱼缸，再添加鱼只档案。鱼缸详情可记录水质、换水；养护页可管理计划和历史任务。") })
            InfoCard("数据与单位", content = { Text("数据保存在本机。可在“我的 - 数据与偏好 - 存储与备份”导出备份。更改单位只影响展示，不改写历史记录。") })
            InfoCard("通知", content = { Text("提醒由本地排程。系统通知权限关闭时，任务仍会保留在养护列表。") })
        }
    }
}

@Composable
private fun PreferenceGroup(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold); content() } }
}

@Composable
private fun ChoiceRow(title: String, choices: List<String>, current: String, labels: Map<String, String> = emptyMap(), onChoose: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f))
        Box {
            OutlinedButton(onClick = { expanded = true }) { Text(labels[current] ?: current) }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) { choices.forEach { value -> DropdownMenuItem(text = { Text(labels[value] ?: value) }, onClick = { onChoose(value); expanded = false }) } }
        }
    }
}

@Composable
private fun ProfileEntry(title: String, subtitle: String, onClick: () -> Unit) {
    OutlinedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.Medium); Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Text("›", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleLarge)
        }
    }
}

private fun Long.toSize(): String = when { this < 1024 -> "$this B"; this < 1024 * 1024 -> String.format(Locale.getDefault(), "%.1f KB", this / 1024.0); else -> String.format(Locale.getDefault(), "%.1f MB", this / (1024.0 * 1024.0)) }
private fun Long.formatDate() = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date(this))
