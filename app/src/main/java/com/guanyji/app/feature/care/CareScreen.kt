@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.guanyji.app.feature.care

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.guanyji.app.core.designsystem.*
import com.guanyji.app.data.local.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private enum class CareView(val title: String) { today("今日任务"), calendar("日历"), future("未来计划"), history("历史记录") }

@Composable
fun CareScreen(openTaskId: String? = null, onTaskOpened: () -> Unit = {}, vm: CareViewModel = viewModel(factory = CareViewModel.factory(LocalContext.current.applicationContext as android.app.Application))) {
    val context = LocalContext.current
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    val records by vm.records.collectAsStateWithLifecycle()
    val tanks by vm.tanks.collectAsStateWithLifecycle()
    val fish by vm.fish.collectAsStateWithLifecycle()
    var view by remember { mutableStateOf(CareView.today) }
    var showEditor by remember { mutableStateOf(false) }
    var selectedDay by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var editing by remember { mutableStateOf<CareTask?>(null) }
    var completing by remember { mutableStateOf<CareTask?>(null) }
    var postponing by remember { mutableStateOf<CareTask?>(null) }
    var confirmingCancel by remember { mutableStateOf<CareTask?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { vm.refreshOverdue() }
    var handledTaskId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(openTaskId, tasks) {
        val task = tasks.firstOrNull { it.id == openTaskId }
        if (task != null && handledTaskId != task.id) { editing = task; showEditor = true; handledTaskId = task.id; onTaskOpened() }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) message = "通知权限未开启，任务仍保存在 App 内；可在系统设置中允许通知。"
    }
    val now = System.currentTimeMillis()
    val cal = Calendar.getInstance().apply { timeInMillis = selectedDay; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
    val dayStart = cal.timeInMillis
    val dayEnd = Calendar.getInstance().apply { timeInMillis = dayStart; add(Calendar.DAY_OF_MONTH, 1) }.timeInMillis
    val displayTasks = when (view) {
        CareView.today -> tasks.filter { (it.plannedAt in dayStart until dayEnd || it.status == CareTaskStatus.overdue) && it.status !in setOf(CareTaskStatus.cancelled, CareTaskStatus.completed, CareTaskStatus.skipped) }
        CareView.calendar -> tasks.filter { it.plannedAt in dayStart until dayEnd }
        CareView.future -> tasks.filter { it.plannedAt >= dayEnd && it.status in setOf(CareTaskStatus.pending, CareTaskStatus.overdue) }
        CareView.history -> tasks.filter { it.status in setOf(CareTaskStatus.completed, CareTaskStatus.skipped, CareTaskStatus.cancelled) } + records.filter { record -> tasks.none { it.id == record.taskId } }.map { record -> CareTask(id = record.id, tankId = record.tankId, fishId = record.fishId, type = CareTaskType.valueOf(record.type.name), plannedAt = record.completedAt, recurrenceRule = null, status = CareTaskStatus.completed, notes = record.notes, createdAt = record.completedAt, updatedAt = record.completedAt) }
    }.distinctBy { it.id }.sortedBy { if (view == CareView.history) -it.plannedAt else it.plannedAt }
    Scaffold(floatingActionButton = { FloatingActionButton(onClick = { editing = null; showEditor = true }) { Icon(Icons.Outlined.Add, contentDescription = "新增养护任务") } }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            PageTitle("养护计划", "${tasks.count { it.status == CareTaskStatus.pending || it.status == CareTaskStatus.overdue }} 项待处理")
            ScrollableTabRow(selectedTabIndex = CareView.entries.indexOf(view), edgePadding = 0.dp) {
                CareView.entries.forEach { item -> Tab(view == item, { view = item }, text = { Text(item.title) }) }
            }
            if (view == CareView.calendar) {
                OutlinedButton(onClick = {
                    val c = Calendar.getInstance().apply { timeInMillis = selectedDay }
                    DatePickerDialog(context, { _, y, m, d -> selectedDay = Calendar.getInstance().apply { set(y, m, d, 0, 0, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
                }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) { Text("选择日期：${formatDate(selectedDay)}") }
            }
            if (displayTasks.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState(when (view) { CareView.today -> "今天暂无任务"; CareView.calendar -> "这天没有计划"; CareView.future -> "暂无未来计划"; CareView.history -> "暂无养护历史" }, "新增任务后会保存在本机；通知关闭时也可在这里查看。")
                }
            } else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(start = 0.dp, top = 12.dp, end = 0.dp, bottom = 88.dp)) {
                items(displayTasks, key = { it.id }) { task -> CareTaskItem(task, tanks.firstOrNull { it.id == task.tankId }?.name, fish.firstOrNull { it.fish.id == task.fishId }?.fish?.name,
                    onComplete = { completing = task }, onPostpone = { postponing = task }, onSkip = { vm.status(task.id, CareTaskStatus.skipped, {}, { message = it }) }, onEdit = { editing = task }, onCancel = { confirmingCancel = task }) }
            }
        }
    }
    if (showEditor) CareTaskEditor(editing, tanks, fish, onDismiss = { editing = null; showEditor = false }, onSave = { value, interval, reminder ->
        val allowed = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (reminder && !allowed) {
            val prefs = context.getSharedPreferences("care_notifications", android.content.Context.MODE_PRIVATE)
            if (Build.VERSION.SDK_INT >= 33 && !prefs.getBoolean("permission_request_seen", false)) {
                prefs.edit().putBoolean("permission_request_seen", true).apply()
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else message = "任务已保存；通知权限未开启，请在系统设置中允许观鱼记发送通知。"
            vm.save(value, interval, true, { editing = null; showEditor = false }, { message = it })
        } else vm.save(value, interval, reminder, { editing = null; showEditor = false; message = null }, { message = it })
    })
    completing?.let { task -> CareTaskCompletion(task, onDismiss = { completing = null }, onSave = { note, payload -> busy = true; vm.finish(task, note, payload, { busy = false; completing = null }, { busy = false; message = it }) }, busy = busy) }
    postponing?.let { task -> PostponeDialog(task, onDismiss = { postponing = null }, onSave = { time -> vm.postpone(task.id, time, { postponing = null }, { message = it }) }) }
    confirmingCancel?.let { task -> AlertDialog(onDismissRequest = { confirmingCancel = null }, title = { Text("取消任务？") }, text = { Text("取消后不会再触发提醒，历史记录会保留。") }, confirmButton = { TextButton(onClick = { vm.status(task.id, CareTaskStatus.cancelled, { confirmingCancel = null }, { message = it }) }) { Text("取消任务") } }, dismissButton = { TextButton(onClick = { confirmingCancel = null }) { Text("返回") } }) }
    message?.takeIf { it.isNotBlank() }?.let { text -> AlertDialog(onDismissRequest = { message = null }, confirmButton = {
        Row { if (text.contains("系统设置")) TextButton(onClick = { context.startActivity(android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)) }) { Text("打开通知设置") }; TextButton(onClick = { message = null }) { Text("知道了") } }
    }, text = { Text(text) }) }
}

@Composable
private fun CareTaskItem(task: CareTask, tank: String?, fish: String?, onComplete: () -> Unit, onPostpone: () -> Unit, onSkip: () -> Unit, onEdit: () -> Unit, onCancel: () -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(task.displayTitle(), style = MaterialTheme.typography.titleMedium)
                Text(task.status.label(), color = if (task.status == CareTaskStatus.overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            }
            Text("计划：${formatDateTime(task.plannedAt)}")
            task.postponedFrom?.let { Text("延期自：${formatDateTime(it)}", style = MaterialTheme.typography.bodySmall) }
            Text("对象：${listOfNotNull(tank, fish).joinToString(" · ").ifBlank { "未指定" }}")
            if (!task.notes.isNullOrBlank() && !task.notes.startsWith("water-change:")) Text(task.notes)
            if (task.status == CareTaskStatus.pending || task.status == CareTaskStatus.overdue) Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                TextButton(onClick = onComplete) { Text("完成") }; TextButton(onClick = onPostpone) { Text("延期") }; TextButton(onClick = onSkip) { Text("跳过") }; TextButton(onClick = onEdit) { Text("编辑") }; TextButton(onClick = onCancel) { Text("取消") }
            }
        }
    }
}

@Composable
private fun CareTaskEditor(existing: CareTask?, tanks: List<Tank>, fish: List<FishSummary>, onDismiss: () -> Unit, onSave: (CareTask, Int?, Boolean) -> Unit) {
    val context = LocalContext.current
    val now = System.currentTimeMillis()
    var type by remember(existing) { mutableStateOf(existing?.type ?: CareTaskType.feeding) }
    var tankId by remember(existing) { mutableStateOf(existing?.tankId) }
    var fishId by remember(existing) { mutableStateOf(existing?.fishId) }
    var time by remember(existing) { mutableLongStateOf(existing?.plannedAt ?: now) }
    var repeatDays by remember(existing) { mutableStateOf(if (existing?.ruleId == null) "" else existing.recurrenceRule?.substringAfter(':').orEmpty()) }
    var reminder by remember(existing) { mutableStateOf(existing?.reminderEnabled ?: false) }
    var customTitle by remember(existing) { mutableStateOf(existing?.customTitle.orEmpty()) }
    var notes by remember(existing) { mutableStateOf(existing?.notes.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    var typeExpanded by remember { mutableStateOf(false) }; var tankExpanded by remember { mutableStateOf(false) }; var fishExpanded by remember { mutableStateOf(false) }
    val allowedFish = fish.filter { summary -> summary.fish.status in setOf(FishStatus.active, FishStatus.quarantine) && (tankId == null || summary.fish.tankId == tankId) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (existing == null) "新增养护任务" else "编辑未来任务") },
        text = { Column(Modifier.heightIn(max = 520.dp).verticalScroll(androidx.compose.foundation.rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ExposedDropdownMenuBox(typeExpanded, { typeExpanded = it }) { OutlinedTextField(type.displayName(), {}, Modifier.menuAnchor().fillMaxWidth(), readOnly = true, label = { Text("任务类型") }); ExposedDropdownMenu(typeExpanded, { typeExpanded = false }) { CareTaskType.entries.forEach { item -> DropdownMenuItem(text = { Text(item.displayName()) }, onClick = { type = item; typeExpanded = false }) } } }
            ExposedDropdownMenuBox(tankExpanded, { tankExpanded = it }) { OutlinedTextField(tanks.firstOrNull { it.id == tankId }?.name ?: "不指定鱼缸", {}, Modifier.menuAnchor().fillMaxWidth(), readOnly = true, label = { Text("鱼缸（可选）") }); ExposedDropdownMenu(tankExpanded, { tankExpanded = false }) { DropdownMenuItem(text = { Text("不指定鱼缸") }, onClick = { tankId = null; fishId = null; tankExpanded = false }); tanks.filter { it.status == TankStatus.active }.forEach { t -> DropdownMenuItem(text = { Text(t.name + if (t.type == TankType.saltwater) " · 海水" else " · 淡水") }, onClick = { tankId = t.id; fishId = null; tankExpanded = false }) } } }
            ExposedDropdownMenuBox(fishExpanded, { fishExpanded = it }) { OutlinedTextField(allowedFish.firstOrNull { it.fish.id == fishId }?.fish?.name ?: "不关联鱼只", {}, Modifier.menuAnchor().fillMaxWidth(), readOnly = true, label = { Text("关联鱼只（可选）") }); ExposedDropdownMenu(fishExpanded, { fishExpanded = false }) { DropdownMenuItem(text = { Text("不关联鱼只") }, onClick = { fishId = null; fishExpanded = false }); allowedFish.forEach { row -> DropdownMenuItem(text = { Text(row.fish.name) }, onClick = { fishId = row.fish.id; tankId = row.fish.tankId; fishExpanded = false }) } } }
            OutlinedButton(onClick = { pickTime(context, time) { time = it } }, modifier = Modifier.fillMaxWidth()) { Text("计划时间：${formatDateTime(time)}") }
            OutlinedTextField(repeatDays, { repeatDays = it.filter(Char::isDigit).take(3) }, Modifier.fillMaxWidth(), label = { Text("重复间隔天数（留空为一次性）") }, singleLine = true)
            if (type == CareTaskType.custom) OutlinedTextField(customTitle, { customTitle = it }, Modifier.fillMaxWidth(), label = { Text("自定义任务名称 *") }, singleLine = true)
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(reminder, { reminder = it }); Text("启用本地通知提醒") }
            Text("通知按系统允许的非精确时间调度；省电策略、通知权限与厂商限制可能造成延迟。", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(notes, { notes = it }, Modifier.fillMaxWidth(), label = { Text("任务说明") }, minLines = 2)
            if (type == CareTaskType.salinity_test) {
                val selected = tanks.firstOrNull { it.id == tankId }
                if (selected?.type != TankType.saltwater || selected?.salinityManagementEnabled != true) Text("盐度提醒仅适用于已启用盐度管理的海水缸。", color = MaterialTheme.colorScheme.error)
                else Text("未记录盐度值时，本提醒仅提示检测，不判断异常。", style = MaterialTheme.typography.bodySmall)
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        } },
        confirmButton = { TextButton(onClick = {
            val interval = repeatDays.toIntOrNull()
            val tank = tanks.firstOrNull { it.id == tankId }
            if (type == CareTaskType.salinity_test && (tank?.type != TankType.saltwater || tank?.salinityManagementEnabled != true)) error = "请先选择已启用盐度管理的海水缸"
            else if (type == CareTaskType.custom && customTitle.isBlank()) error = "请输入自定义任务名称"
            else if (repeatDays.isNotBlank() && (interval == null || interval !in 1..365)) error = "重复间隔需为 1 到 365 天"
            else {
                val id = existing?.id ?: java.util.UUID.randomUUID().toString()
                val savedNotes = existing?.notes?.takeIf { it.startsWith("water-change:") } ?: notes.trim().ifBlank { null }
                onSave(CareTask(id = id, tankId = tankId, fishId = fishId, type = type, plannedAt = time, recurrenceRule = null, status = CareTaskStatus.pending, notes = savedNotes, createdAt = existing?.createdAt ?: now, updatedAt = now, ruleId = existing?.ruleId, customTitle = customTitle.trim().ifBlank { null }), interval, reminder)
            }
        }) { Text("保存") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("返回") } })
}

@Composable
private fun CareTaskCompletion(task: CareTask, onDismiss: () -> Unit, onSave: (String, String?) -> Unit, busy: Boolean) {
    var note by remember(task.id) { mutableStateOf("") }
    var measurement by remember(task.id) { mutableStateOf("") }
    val formLabel = when (task.type) { CareTaskType.feeding -> "饲料或投喂量"; CareTaskType.water_change -> "换水量"; CareTaskType.water_test -> "检测摘要"; CareTaskType.salinity_test -> "盐度检测值（可留空）"; CareTaskType.maintenance -> "维护内容"; CareTaskType.custom -> "完成内容" }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("完成${task.displayTitle()}") }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("请记录实际执行内容；该信息写入养护记录。")
        OutlinedTextField(measurement, { measurement = it }, label = { Text(formLabel) }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(note, { note = it }, label = { Text("备注") }, modifier = Modifier.fillMaxWidth())
        if (task.type == CareTaskType.salinity_test && measurement.isBlank()) Text("未填写测量值时仅记录完成检测，不表示盐度正常或异常。", style = MaterialTheme.typography.bodySmall)
    } }, confirmButton = { TextButton(enabled = !busy, onClick = { val value = measurement.trim(); onSave(note, value.takeIf(String::isNotEmpty)?.let { org.json.JSONObject().put("actual", it).toString() }) }) { Text(if (busy) "保存中" else "保存实际记录") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("返回") } })
}

@Composable
private fun PostponeDialog(task: CareTask, onDismiss: () -> Unit, onSave: (Long) -> Unit) {
    var time by remember(task.id) { mutableLongStateOf(task.plannedAt + 3_600_000L) }
    val context = LocalContext.current
    AlertDialog(onDismissRequest = onDismiss, title = { Text("延期任务") }, text = { OutlinedButton(onClick = { pickTime(context, time) { time = it } }, modifier = Modifier.fillMaxWidth()) { Text(formatDateTime(time)) } }, confirmButton = { TextButton(onClick = { onSave(time) }) { Text("确认延期") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("返回") } })
}

private fun pickTime(context: android.content.Context, initial: Long, selected: (Long) -> Unit) {
    val cal = Calendar.getInstance().apply { timeInMillis = initial }
    DatePickerDialog(context, { _, year, month, day ->
        TimePickerDialog(context, { _, hour, minute -> selected(Calendar.getInstance().apply { set(year, month, day, hour, minute, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis) }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show()
    }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
}
private fun formatDate(time: Long) = SimpleDateFormat("yyyy年M月d日", Locale.getDefault()).format(Date(time))
private fun formatDateTime(time: Long) = SimpleDateFormat("yyyy年M月d日 HH:mm", Locale.getDefault()).format(Date(time))
private fun CareTaskStatus.label() = when (this) { CareTaskStatus.pending -> "待完成"; CareTaskStatus.completed -> "已完成"; CareTaskStatus.skipped -> "已跳过"; CareTaskStatus.overdue -> "已逾期"; CareTaskStatus.cancelled -> "已取消" }
