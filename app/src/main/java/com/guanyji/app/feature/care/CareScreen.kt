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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.guanyji.app.core.designsystem.*
import com.guanyji.app.data.local.*
import com.guanyji.app.feature.overview.FishIcons
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private enum class CareView(val title: String) { today("今日计划"), calendar("日历"), future("未来计划"), history("历史记录") }

private val CareBlue = Color(0xFF087CEB)
private val CareInk = Color(0xFF153B5B)
private val CareMuted = Color(0xFF66839B)
private val CareCanvas = Color(0xFFF1F8FC)
private val CareLine = Color(0xFFDDECF5)

@Composable
fun CareScreen(openTaskId: String? = null, onTaskOpened: () -> Unit = {}, initialView: String? = null, vm: CareViewModel = viewModel(factory = CareViewModel.factory(LocalContext.current.applicationContext as android.app.Application))) {
    val context = LocalContext.current
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    val records by vm.records.collectAsStateWithLifecycle()
    val tanks by vm.tanks.collectAsStateWithLifecycle()
    val fish by vm.fish.collectAsStateWithLifecycle()
    var view by rememberSaveable { mutableStateOf(if (initialView == "history") CareView.history else CareView.today) }
    LaunchedEffect(initialView) {
        if (initialView == "history") view = CareView.history
    }
    var showEditor by remember { mutableStateOf(false) }
    var selectedDay by rememberSaveable { mutableLongStateOf(System.currentTimeMillis()) }
    var shownMonth by rememberSaveable { mutableLongStateOf(System.currentTimeMillis()) }
    var historyType by rememberSaveable { mutableStateOf<CareRecordType?>(null) }
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
    val dayTasks = tasks.filter { it.plannedAt in dayStart until dayEnd && it.status != CareTaskStatus.cancelled }.sortedBy { it.plannedAt }
    val todayStart = Calendar.getInstance().apply { timeInMillis = now; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
    val todayEnd = Calendar.getInstance().apply { timeInMillis = todayStart; add(Calendar.DAY_OF_MONTH, 1) }.timeInMillis
    val todayTasks = tasks.filter { it.status in setOf(CareTaskStatus.pending, CareTaskStatus.overdue) && (it.plannedAt in todayStart until todayEnd || it.status == CareTaskStatus.overdue) }.sortedBy { it.plannedAt }
    val futureTasks = tasks.filter { it.plannedAt >= todayEnd && it.status == CareTaskStatus.pending }.sortedBy { it.plannedAt }
    val visibleRecords = records.filter { historyType == null || it.type == historyType }.sortedByDescending { it.completedAt }
    Scaffold(containerColor = CareCanvas, contentWindowInsets = WindowInsets(0, 0, 0, 0), floatingActionButton = { FloatingActionButton(onClick = { editing = null; showEditor = true }, containerColor = CareBlue, contentColor = Color.White) { Icon(Icons.Outlined.Add, contentDescription = "新增养护任务") } }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            CareHeader()
            CareTabRow(view, onSelect = { view = it })
            when (view) {
                CareView.today -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 100.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item { CareSectionHeading("今日养护", "${todayTasks.size} 项待完成", Icons.Outlined.DateRange) }
                    if (todayTasks.isEmpty()) item { CareEmptyState("今天暂无计划任务", "你可以创建一次性或重复养护计划。", onAdd = { editing = null; showEditor = true }) }
                    else items(todayTasks, key = { it.id }) { task ->
                        CareTaskCard(task, tanks.firstOrNull { it.id == task.tankId }?.name, fish.firstOrNull { it.fish.id == task.fishId }?.fish?.name,
                            onOpen = { completing = task }, onComplete = { completing = task }, onPostpone = { postponing = task }, onSkip = { vm.status(task.id, CareTaskStatus.skipped, {}, { message = it }) }, onEdit = { editing = task }, onCancel = { confirmingCancel = task })
                    }
                    if (todayTasks.isNotEmpty()) item { CareTipCard() }
                }
                CareView.calendar -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 100.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item { CareCalendar(tasks, shownMonth, selectedDay, onMonthChange = { shownMonth = it }, onDaySelect = { selectedDay = it }) }
                    item { CareSectionHeading(formatSelectedDate(selectedDay), "${dayTasks.size} 项计划", Icons.Outlined.DateRange) }
                    if (dayTasks.isEmpty()) item { CareEmptyState("这天没有计划", "选择其他日期，或新增一项养护任务。", onAdd = { editing = null; showEditor = true }) }
                    else items(dayTasks, key = { it.id }) { task ->
                        CareTaskCard(task, tanks.firstOrNull { it.id == task.tankId }?.name, fish.firstOrNull { it.fish.id == task.fishId }?.fish?.name,
                            onOpen = { completing = task }, onComplete = { completing = task }, onPostpone = { postponing = task }, onSkip = { vm.status(task.id, CareTaskStatus.skipped, {}, { message = it }) }, onEdit = { editing = task }, onCancel = { confirmingCancel = task })
                    }
                }
                CareView.future -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 100.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item { CareSectionHeading("未来计划", "${futureTasks.size} 项待执行", Icons.Outlined.DateRange) }
                    if (futureTasks.isEmpty()) item { CareEmptyState("暂无未来计划", "建立计划后，会按日期显示在这里。", onAdd = { editing = null; showEditor = true }) }
                    else {
                        itemsIndexed(futureTasks, key = { _, task -> task.id }) { index, task ->
                            val newDate = index == 0 || !sameDay(futureTasks[index - 1].plannedAt, task.plannedAt)
                            if (newDate) CareDateGroupLabel(task.plannedAt, todayStart)
                            CareTimelineItem(task, tanks.firstOrNull { it.id == task.tankId }?.name, fish.firstOrNull { it.fish.id == task.fishId }?.fish?.name,
                                onOpen = { completing = task }, onComplete = { completing = task }, onPostpone = { postponing = task }, onSkip = { vm.status(task.id, CareTaskStatus.skipped, {}, { message = it }) }, onEdit = { editing = task }, onCancel = { confirmingCancel = task })
                        }
                    }
                }
                CareView.history -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 100.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    item { CareSectionHeading("养护记录", "${visibleRecords.size} 条已保存记录", Icons.Outlined.DateRange) }
                    item {
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CareRecordType.entries.forEach { type ->
                                FilterChip(selected = historyType == type, onClick = { historyType = if (historyType == type) null else type }, label = { Text(type.displayName()) })
                            }
                        }
                    }
                    if (visibleRecords.isEmpty()) item { CareEmptyState("暂无养护记录", if (historyType == null) "完成一项任务后，实际执行信息会保存在这里。" else "当前类型还没有已保存记录。", onAdd = if (historyType == null) ({ editing = null; showEditor = true }) else null) }
                    else items(visibleRecords, key = { it.id }) { record ->
                        CareHistoryCard(record, tanks.firstOrNull { it.id == record.tankId }?.name, fish.firstOrNull { it.fish.id == record.fishId }?.fish?.name)
                    }
                }
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
private fun CareHeader() {
    val statusBarInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Box(Modifier.fillMaxWidth().height(172.dp + statusBarInset)) {
        androidx.compose.foundation.Image(painterResource(com.guanyji.app.R.drawable.care_header), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alignment = Alignment.Center)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x33102C45), Color(0xB3123854)))))
        Column(Modifier.align(Alignment.BottomStart).padding(start = 22.dp, bottom = 22.dp)) {
            Text("养护", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text("科学养护 · 让鱼儿更健康", color = Color.White.copy(alpha = .94f), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun CareTabRow(selected: CareView, onSelect: (CareView) -> Unit) {
    ScrollableTabRow(selectedTabIndex = CareView.entries.indexOf(selected), edgePadding = 12.dp, containerColor = Color.White, contentColor = CareBlue,
        indicator = { positions -> androidx.compose.material3.TabRowDefaults.SecondaryIndicator(Modifier.tabIndicatorOffset(positions[CareView.entries.indexOf(selected)]), color = CareBlue, height = 3.dp) }) {
        CareView.entries.forEach { item ->
            Tab(selected == item, onClick = { onSelect(item) }, modifier = Modifier.height(52.dp), text = {
                Text(item.title, color = if (selected == item) CareBlue else CareMuted, fontWeight = if (selected == item) FontWeight.SemiBold else FontWeight.Medium, maxLines = 1)
            })
        }
    }
}

@Composable
private fun CareSectionHeading(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = CareBlue, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(9.dp))
        Text(title, color = CareInk, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f).then(if (title == "养护记录") Modifier.testTag("care-history-title") else Modifier), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(subtitle, color = CareMuted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun CareTaskCard(task: CareTask, tank: String?, fish: String?, onOpen: () -> Unit, onComplete: () -> Unit, onPostpone: () -> Unit, onSkip: () -> Unit, onEdit: () -> Unit, onCancel: () -> Unit) {
    Surface(Modifier.fillMaxWidth().clickable(onClickLabel = "查看${task.displayTitle()}详情", onClick = onOpen), shape = RoundedCornerShape(16.dp), color = Color.White, border = androidx.compose.foundation.BorderStroke(1.dp, CareLine), shadowElevation = 1.dp) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CareTaskTypeIcon(task.type)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(task.displayTitle(), color = CareInk, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(listOfNotNull(tank, fish).joinToString(" · ").ifBlank { "未指定对象" }, color = CareMuted, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                CareStatusBadge(task.status)
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.DateRange, null, tint = CareMuted, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(5.dp))
                Text(formatDateTime(task.plannedAt), color = CareMuted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                if (task.status == CareTaskStatus.pending || task.status == CareTaskStatus.overdue) {
                    TextButton(onClick = onComplete) { Text("完成") }
                    TaskActionsMenu(onPostpone, onSkip, onEdit, onCancel)
                }
            }
            if (!task.notes.isNullOrBlank() && !task.notes.startsWith("water-change:")) Text(task.notes, color = CareMuted, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun CareTaskTypeIcon(type: CareTaskType) {
    val (icon, color, bg) = when (type) {
        CareTaskType.water_change -> Triple(FishIcons.Droplet, Color(0xFF1689EA), Color(0xFFE0F2FF))
        CareTaskType.feeding -> Triple(FishIcons.Fish, Color(0xFF14A88B), Color(0xFFE0F7F1))
        CareTaskType.water_test, CareTaskType.salinity_test -> Triple(Icons.Outlined.Check, Color(0xFF8555D6), Color(0xFFF0E9FF))
        CareTaskType.maintenance -> Triple(FishIcons.Aquarium, Color(0xFFEB842E), Color(0xFFFFF0E2))
        CareTaskType.custom -> Triple(Icons.Outlined.Check, Color(0xFF507DA3), Color(0xFFEAF3F9))
    }
    Box(Modifier.size(46.dp).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) { Icon(icon, contentDescription = type.displayName(), tint = color, modifier = Modifier.size(23.dp)) }
}

@Composable
private fun CareStatusBadge(status: CareTaskStatus) {
    val (label, foreground, background) = when (status) {
        CareTaskStatus.pending -> Triple("待完成", Color(0xFFCE4961), Color(0xFFFFE8EC))
        CareTaskStatus.overdue -> Triple("已逾期", Color(0xFFB33D30), Color(0xFFFFE6E1))
        CareTaskStatus.completed -> Triple("已完成", Color(0xFF178A72), Color(0xFFE1F5EF))
        CareTaskStatus.skipped -> Triple("已跳过", CareMuted, Color(0xFFEDF2F5))
        CareTaskStatus.cancelled -> Triple("已取消", CareMuted, Color(0xFFEDF2F5))
    }
    Text(label, color = foreground, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium, modifier = Modifier.clip(RoundedCornerShape(50)).background(background).padding(horizontal = 10.dp, vertical = 5.dp))
}

@Composable
private fun TaskActionsMenu(onPostpone: () -> Unit, onSkip: () -> Unit, onEdit: () -> Unit, onCancel: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }, modifier = Modifier.size(40.dp)) { Icon(Icons.Outlined.MoreVert, contentDescription = "更多任务操作", tint = CareMuted) }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            listOf("延期" to onPostpone, "跳过" to onSkip, "编辑" to onEdit, "取消计划" to onCancel).forEach { (label, action) -> DropdownMenuItem(text = { Text(label) }, onClick = { expanded = false; action() }) }
        }
    }
}

@Composable
private fun CareCalendar(tasks: List<CareTask>, month: Long, selectedDay: Long, onMonthChange: (Long) -> Unit, onDaySelect: (Long) -> Unit) {
    val calendar = Calendar.getInstance().apply { timeInMillis = month; set(Calendar.DAY_OF_MONTH, 1) }
    val year = calendar.get(Calendar.YEAR); val monthIndex = calendar.get(Calendar.MONTH)
    val firstWeekday = (calendar.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY + 7) % 7
    val days = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
    val today = Calendar.getInstance()
    val taskDays = tasks.filter { it.status != CareTaskStatus.cancelled }.map { Calendar.getInstance().apply { timeInMillis = it.plannedAt; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis }.toSet()
    Surface(shape = RoundedCornerShape(18.dp), color = Color.White, border = androidx.compose.foundation.BorderStroke(1.dp, CareLine)) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${year}年${monthIndex + 1}月", color = CareInk, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = { val c = Calendar.getInstance().apply { timeInMillis = month; add(Calendar.MONTH, -1) }; onMonthChange(c.timeInMillis) }) { Text("‹", color = CareBlue, fontSize = 28.sp) }
                IconButton(onClick = { val c = Calendar.getInstance().apply { timeInMillis = month; add(Calendar.MONTH, 1) }; onMonthChange(c.timeInMillis) }) { Text("›", color = CareBlue, fontSize = 28.sp) }
            }
            Row(Modifier.fillMaxWidth()) { listOf("日", "一", "二", "三", "四", "五", "六").forEach { Text(it, Modifier.weight(1f), color = CareMuted, style = MaterialTheme.typography.labelMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center) } }
            Spacer(Modifier.height(7.dp))
            val cellCount = ((firstWeekday + days + 6) / 7) * 7
            for (weekStart in 0 until cellCount step 7) {
                Row(Modifier.fillMaxWidth()) {
                    for (column in weekStart until weekStart + 7) {
                        val number = column - firstWeekday + 1
                        if (number !in 1..days) Spacer(Modifier.weight(1f).height(42.dp)) else {
                            val dayCal = Calendar.getInstance().apply { set(year, monthIndex, number, 0, 0, 0); set(Calendar.MILLISECOND, 0) }
                            val timestamp = dayCal.timeInMillis
                            val chosen = sameDay(timestamp, selectedDay)
                            val isToday = sameDay(timestamp, today.timeInMillis)
                            val hasTasks = taskDays.any { sameDay(it, timestamp) }
                            Column(Modifier.weight(1f).height(42.dp).clip(CircleShape).background(if (chosen) CareBlue else Color.Transparent).clickable { onDaySelect(timestamp) }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                Text(number.toString(), color = if (chosen) Color.White else if (isToday) CareBlue else CareInk, fontWeight = if (chosen || isToday) FontWeight.Bold else FontWeight.Normal, style = MaterialTheme.typography.bodyMedium)
                                Box(Modifier.padding(top = 2.dp).size(4.dp).clip(CircleShape).background(if (hasTasks) if (chosen) Color.White else CareBlue else Color.Transparent))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CareTimelineItem(task: CareTask, tank: String?, fish: String?, onOpen: () -> Unit, onComplete: () -> Unit, onPostpone: () -> Unit, onSkip: () -> Unit, onEdit: () -> Unit, onCancel: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(25.dp).padding(top = 12.dp)) {
            Box(Modifier.size(11.dp).clip(CircleShape).background(CareBlue))
            Box(Modifier.width(1.dp).height(92.dp).background(CareLine))
        }
        Spacer(Modifier.width(6.dp))
        Box(Modifier.weight(1f)) { CareTaskCard(task, tank, fish, onOpen, onComplete, onPostpone, onSkip, onEdit, onCancel) }
    }
}

@Composable
private fun CareDateGroupLabel(timestamp: Long, todayStart: Long) {
    val zone = java.time.ZoneId.systemDefault()
    val diff = java.time.Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate().toEpochDay() -
        java.time.Instant.ofEpochMilli(todayStart).atZone(zone).toLocalDate().toEpochDay()
    Row(Modifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(formatDate(timestamp) + "  " + weekday(timestamp), color = CareInk, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Text(if (diff == 1L) "明天" else "${diff}天后", color = CareBlue, style = MaterialTheme.typography.labelSmall, modifier = Modifier.clip(RoundedCornerShape(50)).background(Color(0xFFE1F1FF)).padding(horizontal = 9.dp, vertical = 5.dp))
    }
}

@Composable
private fun CareHistoryCard(record: CareRecord, tank: String?, fish: String?) {
    var showDetails by remember(record.id) { mutableStateOf(false) }
    Surface(onClick = { showDetails = true }, shape = RoundedCornerShape(16.dp), color = Color.White, border = androidx.compose.foundation.BorderStroke(1.dp, CareLine)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.Top) {
            CareTaskTypeIcon(CareTaskType.valueOf(record.type.name))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) { Text(record.type.displayName(), color = CareInk, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f)); Icon(Icons.Outlined.CheckCircle, null, tint = Color(0xFF21A083), modifier = Modifier.size(18.dp)) }
                Text(listOfNotNull(tank, fish).joinToString(" · ").ifBlank { "未指定对象" }, color = CareMuted, style = MaterialTheme.typography.bodySmall)
                recordSummary(record)?.let { Text(it, color = CareBlue, style = MaterialTheme.typography.bodySmall) }
                if (!record.notes.isNullOrBlank()) Text(record.notes, color = CareMuted, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(formatDateTime(record.completedAt), color = CareMuted, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
    if (showDetails) AlertDialog(
        onDismissRequest = { showDetails = false },
        title = { Text(record.type.displayName()) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("对象：${listOfNotNull(tank, fish).joinToString(" · ").ifBlank { "未指定" }}")
                Text("完成时间：${formatDateTime(record.completedAt)}")
                recordSummary(record)?.let { Text(it) }
                Text("备注：${record.notes?.takeIf(String::isNotBlank) ?: "暂无记录"}")
            }
        },
        confirmButton = { TextButton(onClick = { showDetails = false }) { Text("关闭") } },
    )
}

private fun recordSummary(record: CareRecord): String? {
    val payload = record.payloadJson?.takeIf(String::isNotBlank) ?: return null
    return runCatching {
        val json = org.json.JSONObject(payload)
        when (record.type) {
            CareRecordType.water_change -> json.optString("actual").takeIf(String::isNotBlank)?.let { "实际换水：$it" }
            CareRecordType.feeding -> json.optString("actual").takeIf(String::isNotBlank)?.let { "投喂记录：$it" }
            CareRecordType.water_test -> json.optString("actual").takeIf(String::isNotBlank)?.let { "检测记录：$it" }
            CareRecordType.salinity_test -> json.optString("actual").takeIf(String::isNotBlank)?.let { "实测盐度：$it" }
            CareRecordType.maintenance, CareRecordType.custom -> json.optString("actual").takeIf(String::isNotBlank)?.let { "执行内容：$it" }
        }
    }.getOrNull()
}

@Composable
private fun CareEmptyState(title: String, detail: String, onAdd: (() -> Unit)?) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = Color.White, border = androidx.compose.foundation.BorderStroke(1.dp, CareLine)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 26.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Outlined.CheckCircle, null, tint = Color(0xFF61A8D7), modifier = Modifier.size(30.dp))
            Spacer(Modifier.height(8.dp)); Text(title, color = CareInk, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp)); Text(detail, color = CareMuted, style = MaterialTheme.typography.bodySmall, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            if (onAdd != null) { Spacer(Modifier.height(12.dp)); OutlinedButton(onClick = onAdd) { Icon(Icons.Outlined.Add, null); Spacer(Modifier.width(4.dp)); Text("新增计划") } }
        }
    }
}

@Composable
private fun CareTipCard() {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = Color(0xFFE7F5FC)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.CheckCircle, null, tint = CareBlue, modifier = Modifier.size(25.dp)); Spacer(Modifier.width(10.dp))
            Column { Text("养护小贴士", color = CareBlue, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold); Text("按计划观察水质与设备状态，记录实际测量结果。", color = CareMuted, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

private fun sameDay(first: Long, second: Long): Boolean {
    val a = Calendar.getInstance().apply { timeInMillis = first }; val b = Calendar.getInstance().apply { timeInMillis = second }
    return a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
}
private fun startOfDay(timestamp: Long) = Calendar.getInstance().apply { timeInMillis = timestamp; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
private fun weekday(timestamp: Long) = listOf("周日", "周一", "周二", "周三", "周四", "周五", "周六")[Calendar.getInstance().apply { timeInMillis = timestamp }.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY]
private fun formatSelectedDate(timestamp: Long) = SimpleDateFormat("M月d日", Locale.getDefault()).format(Date(timestamp)) + "  " + weekday(timestamp)
private fun CareRecordType.displayName() = CareTaskType.valueOf(name).displayName()

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
