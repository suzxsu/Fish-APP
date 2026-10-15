@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.guanyji.app.feature.tank

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.guanyji.app.core.designsystem.*
import com.guanyji.app.data.local.*
import com.guanyji.app.data.repository.waterChangeTaskMarker
import com.guanyji.app.feature.photo.ManagedPhotoImage
import com.guanyji.app.feature.photo.PhotoPickerField
import com.guanyji.app.feature.photo.PhotoViewModel
import com.guanyji.app.feature.photo.PhotoViewModelFactory
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private enum class TankFilter(val label: String) { all("全部"), freshwater("淡水"), saltwater("海水"), archived("已归档") }
private enum class TankSection(val label: String) { overview("概览"), fish("鱼只"), water("水质"), care("养护"), stats("统计") }
private data class WaterParameter(val code: String, val label: String, val unit: String, val units: List<String> = listOf(unit))

private val freshwaterParameters = listOf(
    WaterParameter("temperature", "温度", "°C", listOf("°C", "°F")),
    WaterParameter("ph", "pH", "pH"),
    WaterParameter("ammonia", "氨/铵", "mg/L", listOf("mg/L", "ppm")),
    WaterParameter("nitrite", "亚硝酸盐", "mg/L", listOf("mg/L", "ppm")),
    WaterParameter("nitrate", "硝酸盐", "mg/L", listOf("mg/L", "ppm")),
)
private val saltwaterParameters = listOf(
    WaterParameter("salinity", "盐度", "ppt", listOf("ppt", "PSU")),
    WaterParameter("specific_gravity", "比重", "SG"),
    WaterParameter("alkalinity", "碱度", "dKH", listOf("dKH", "meq/L")),
)
private fun waterParameters(type: TankType) = freshwaterParameters + if (type == TankType.saltwater) saltwaterParameters else emptyList()
private fun parameterLabel(code: String) = (freshwaterParameters + saltwaterParameters).firstOrNull { it.code == code }?.label ?: code

@Composable
fun TankListScreen(onCreate: () -> Unit, onOpen: (String) -> Unit, vm: TankViewModel = viewModel(factory = TankViewModel.factory(LocalContext.current.applicationContext as android.app.Application))) {
    val tankData by vm.tanks.collectAsStateWithLifecycle()
    val tanks = tankData.orEmpty()
    var search by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(TankFilter.all) }
    val visible = tanks.filter { summary ->
        val tank = summary.tank
        val matches = tank.name.contains(search.trim(), ignoreCase = true)
        matches && when (filter) {
            TankFilter.all -> tank.status == TankStatus.active
            TankFilter.freshwater -> tank.status == TankStatus.active && tank.type == TankType.freshwater
            TankFilter.saltwater -> tank.status == TankStatus.active && tank.type == TankType.saltwater
            TankFilter.archived -> tank.status == TankStatus.archived
        }
    }
    Scaffold(floatingActionButton = { if (filter != TankFilter.archived) FloatingActionButton(onClick = onCreate) { Icon(Icons.Outlined.Add, contentDescription = "新增鱼缸") } }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            PageTitle("鱼缸", "${tanks.count { it.tank.status == TankStatus.active }} 个在用")
            OutlinedTextField(search, { search = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("搜索鱼缸") })
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TankFilter.entries.forEach { item -> FilterChip(selected = filter == item, onClick = { filter = item }, label = { Text(item.label) }) }
            }
            if (tankData == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingState() }
            } else if (visible.isEmpty()) {
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    EmptyState(if (filter == TankFilter.archived) "暂无归档鱼缸" else "还没有鱼缸", if (search.isBlank()) "创建鱼缸后，在这里管理环境与养护记录。" else "没有匹配的鱼缸。")
                    if (filter != TankFilter.archived) Button(onClick = onCreate, modifier = Modifier.padding(top = 16.dp)) { Text("创建鱼缸") }
                }
            } else LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 88.dp)) {
                items(visible, key = { it.tank.id }) { summary ->
                    val photo by remember(summary.tank.photoId) { vm.photo(summary.tank.photoId) }.collectAsStateWithLifecycle()
                    TankCard(summary, photo, onClick = { onOpen(summary.tank.id) })
                }
            }
        }
    }
}

@Composable
private fun TankCard(summary: TankSummary, photo: Photo?, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            ManagedPhotoImage(photo, thumbnail = true, modifier = Modifier.size(92.dp, 82.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(summary.tank.name, style = MaterialTheme.typography.titleMedium)
                    AssistChip(onClick = {}, label = { Text(if (summary.tank.type == TankType.freshwater) "淡水" else "海水") })
                }
                Text("容量  ${summary.tank.capacityValue?.let { "${it.pretty()} ${summary.tank.capacityUnit.orEmpty()}${if (summary.tank.capacityIsEstimated) "（估算）" else ""}" } ?: "未记录"}", style = MaterialTheme.typography.bodySmall)
                Text("在养 ${summary.activeFishCount} 条", style = MaterialTheme.typography.bodySmall)
                Text("最近换水  ${summary.lastWaterChangeAt?.let(::formatDate) ?: "暂无记录"}", style = MaterialTheme.typography.bodySmall)
                Text("最近检测  ${summary.lastWaterTestAt?.let(::formatDate) ?: "暂无记录"}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun TankFormScreen(tankId: String?, onBack: () -> Unit, onSaved: (String) -> Unit, vm: TankViewModel = viewModel(factory = TankViewModel.factory(LocalContext.current.applicationContext as android.app.Application))) {
    val loadedTank by remember(tankId) { vm.summary(tankId) }.collectAsStateWithLifecycle(initialValue = null)
    val existing = loadedTank?.tank
    var formLoaded by remember(tankId) { mutableStateOf(tankId == null) }
    val photoVm: PhotoViewModel = viewModel(key = "tank-photo-$tankId", factory = PhotoViewModelFactory(vm.photoRepository))
    val photoState by photoVm.state.collectAsStateWithLifecycle()
    LaunchedEffect(tankId, existing?.photoId) { if (tankId != null) photoVm.load(existing?.photoId) }
    var name by remember(tankId) { mutableStateOf("") }
    var type by remember(tankId) { mutableStateOf(TankType.freshwater) }
    var length by remember(tankId) { mutableStateOf("") }
    var width by remember(tankId) { mutableStateOf("") }
    var height by remember(tankId) { mutableStateOf("") }
    var capacity by remember(tankId) { mutableStateOf("") }
    var unit by remember(tankId) { mutableStateOf("L") }
    var startedAt by remember(tankId) { mutableStateOf<Long?>(null) }
    var equipment by remember(tankId) { mutableStateOf("") }
    var salinityManagementEnabled by remember(tankId) { mutableStateOf(false) }
    var notes by remember(tankId) { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var typeConfirmation by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    val activeFishCount = loadedTank?.activeFishCount ?: 0
    LaunchedEffect(existing?.id) {
        if (existing != null && !formLoaded) {
            name = existing.name; type = existing.type
            length = existing.lengthCm?.pretty().orEmpty(); width = existing.widthCm?.pretty().orEmpty(); height = existing.heightCm?.pretty().orEmpty()
            capacity = existing.capacityValue?.takeUnless { existing.capacityIsEstimated }?.pretty().orEmpty()
            unit = existing.capacityUnit ?: "L"; startedAt = existing.startedAt; equipment = existing.equipment.orEmpty(); notes = existing.notes.orEmpty(); salinityManagementEnabled = existing.salinityManagementEnabled
            formLoaded = true
        }
    }
    val estimate = listOf(length, width, height).mapNotNull(String::toDoubleOrNull).takeIf { it.size == 3 }?.let { estimateTankCapacityLiters(it[0], it[1], it[2]) }
    val estimateInUnit = estimate?.let { if (unit == "gal") it / 3.785411784 else it }
    val effectiveCapacity = capacity.toDoubleOrNull() ?: estimateInUnit
    val hasRealOverride = capacity.toDoubleOrNull() != null
    val hasUnsavedChanges = photoState.isBusy || if (tankId == null) {
        name.isNotBlank() || length.isNotBlank() || width.isNotBlank() || height.isNotBlank() || capacity.isNotBlank() || equipment.isNotBlank() || notes.isNotBlank() || salinityManagementEnabled || photoState.photo != null
    } else formLoaded && existing != null && (
        name != existing.name || type != existing.type || length.toDoubleOrNull() != existing.lengthCm || width.toDoubleOrNull() != existing.widthCm || height.toDoubleOrNull() != existing.heightCm ||
            capacity.toDoubleOrNull() != existing.capacityValue.takeUnless { existing.capacityIsEstimated } || unit != (existing.capacityUnit ?: "L") || startedAt != existing.startedAt ||
            equipment != existing.equipment.orEmpty() || salinityManagementEnabled != existing.salinityManagementEnabled || notes != existing.notes.orEmpty() || photoState.photo?.id != existing.photoId
        )
    val leaveForm = rememberLeaveFormHandler(hasUnsavedChanges, onBack)

    if (typeConfirmation) ConfirmDialog(title = "切换鱼缸类型？", message = "请检查现有 ${activeFishCount} 条鱼的类型适配性。历史水质检测会保留。", confirmText = "确认切换", onConfirm = { type = if (type == TankType.freshwater) TankType.saltwater else TankType.freshwater; typeConfirmation = false }, onDismiss = { typeConfirmation = false })
    Scaffold(topBar = { TopAppBarSimple(if (tankId == null) "创建鱼缸" else "编辑鱼缸", leaveForm) }, bottomBar = { Surface(shadowElevation = 4.dp) { Button(enabled = formLoaded && !isSaving && !photoState.isBusy && photoState.cameraCaptureUri == null, onClick = {
        val parsedCapacity = effectiveCapacity
        if (name.isBlank()) { error = "请输入鱼缸名称"; return@Button }
        val measurements = listOf(length, width, height, capacity)
        if (measurements.any { it.isNotBlank() && it.toDoubleOrNull() == null }) { error = "尺寸和容量请输入有效数字"; return@Button }
        if (measurements.mapNotNull(String::toDoubleOrNull).any { it <= 0 || !it.isFinite() }) { error = "尺寸和容量必须大于 0"; return@Button }
        val now = System.currentTimeMillis()
        val tank = Tank(id = existing?.id ?: java.util.UUID.randomUUID().toString(), name = name, type = type, photoId = photoState.photo?.id ?: existing?.photoId, lengthCm = length.toDoubleOrNull(), widthCm = width.toDoubleOrNull(), heightCm = height.toDoubleOrNull(), capacityValue = parsedCapacity, capacityUnit = parsedCapacity?.let { unit }, capacityIsEstimated = parsedCapacity != null && !hasRealOverride && estimate != null, startedAt = startedAt, equipment = equipment.ifBlank { null }, status = existing?.status ?: TankStatus.active, salinityManagementEnabled = type == TankType.saltwater && salinityManagementEnabled, notes = notes.ifBlank { null }, createdAt = existing?.createdAt ?: now, updatedAt = now)
        isSaving = true
        vm.save(tank, { onSaved(tank.id) }, { isSaving = false; error = it })
    }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) { Text(if (isSaving) "保存中…" else "保存鱼缸") } } }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PhotoPickerField(photoVm, Modifier.fillMaxWidth())
            OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("名称 *") }, singleLine = true)
            Text("鱼缸类型", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(type == TankType.freshwater, { if (type != TankType.freshwater) { if (activeFishCount > 0) typeConfirmation = true else type = TankType.freshwater } }, label = { Text("淡水") })
                FilterChip(type == TankType.saltwater, { if (type != TankType.saltwater) { if (activeFishCount > 0) typeConfirmation = true else type = TankType.saltwater } }, label = { Text("海水") })
            }
            Text("水质参数模板：${if (type == TankType.freshwater) "温度、pH、氨/铵、亚硝酸盐、硝酸盐" else "温度、pH、氨/铵、亚硝酸盐、硝酸盐、盐度、比重、碱度"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (type == TankType.saltwater) Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(salinityManagementEnabled, { salinityManagementEnabled = it }, modifier = Modifier.padding(end = 8.dp))
                Column { Text("启用盐度管理"); Text("开启后可安排盐度检测提醒；无测量值时只提示检测。", style = MaterialTheme.typography.bodySmall) }
            }
            Text("尺寸（厘米）", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(length, { length = it }, Modifier.weight(1f), label = { Text("长") }, singleLine = true)
                OutlinedTextField(width, { width = it }, Modifier.weight(1f), label = { Text("宽") }, singleLine = true)
                OutlinedTextField(height, { height = it }, Modifier.weight(1f), label = { Text("高") }, singleLine = true)
            }
            estimateInUnit?.let { Text("尺寸估算容量：${it.pretty()} $unit（估算）", color = MaterialTheme.colorScheme.primary) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(capacity, { capacity = it }, Modifier.weight(1f), label = { Text("实际水体容量") }, singleLine = true)
                var unitMenu by remember { mutableStateOf(false) }
                Box { OutlinedButton(onClick = { unitMenu = true }) { Text(unit) }; DropdownMenu(unitMenu, { unitMenu = false }) { listOf("L", "gal").forEach { DropdownMenuItem(text = { Text(it) }, onClick = { unit = it; unitMenu = false }) } } }
            }
            if (capacity.isBlank() && estimate != null) Text("未填写实际容量时保存估算值；输入实际容量可覆盖估算。", style = MaterialTheme.typography.bodySmall)
            DateField("启用日期", startedAt) { startedAt = it }
            OutlinedTextField(equipment, { equipment = it }, Modifier.fillMaxWidth(), label = { Text("设备信息（过滤、加热、照明、造流）") }, minLines = 2)
            OutlinedTextField(notes, { notes = it }, Modifier.fillMaxWidth(), label = { Text("备注") }, minLines = 3)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
fun TankDetailScreen(tankId: String, onBack: () -> Unit, onEdit: () -> Unit, onQuickAction: (String, String) -> Unit, vm: TankViewModel = viewModel(factory = TankViewModel.factory(LocalContext.current.applicationContext as android.app.Application))) {
    val summaryFlow = remember(tankId) { vm.summary(tankId) }
    val summary by summaryFlow.collectAsStateWithLifecycle()
    val fish by remember(tankId) { vm.fish(tankId) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val tests by remember(tankId) { vm.tests(tankId) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val changes by remember(tankId) { vm.changes(tankId) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val taskFlow = remember(tankId) { vm.tasks(tankId) }
    val tasks by taskFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val photoId = summary?.tank?.photoId
    val photo by remember(photoId) { vm.photo(photoId) }.collectAsStateWithLifecycle()
    var section by remember { mutableStateOf(TankSection.overview) }
    var showArchive by remember { mutableStateOf(false) }
    var selectedFish by remember { mutableStateOf<Fish?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val tank = summary?.tank
    selectedFish?.let { fishRecord ->
        AlertDialog(onDismissRequest = { selectedFish = null }, title = { Text(fishRecord.name) }, text = { Text("品种：${fishRecord.species ?: "未记录"}\n状态：${fishRecord.status}\n入缸日期：${formatDate(fishRecord.addedAt)}\n备注：${fishRecord.notes ?: "无"}") }, confirmButton = { TextButton(onClick = { selectedFish = null }) { Text("关闭") } })
    }
    if (showArchive) ConfirmDialog(title = "归档鱼缸？", message = if ((summary?.activeFishCount ?: 0) > 0) "仍有 ${summary?.activeFishCount} 条在养鱼。归档后停止待办任务，但鱼只归属和历史数据保留。请先自行安排转移或确认按当前归属保留。" else "归档后停止未来待办任务，历史数据仍可查询。", confirmText = if ((summary?.activeFishCount ?: 0) > 0) "确认保留鱼只并归档" else "归档", onConfirm = { vm.archive(tankId, true, onBack, { error = it }); showArchive = false }, onDismiss = { showArchive = false })
    Scaffold(topBar = { TopAppBarSimple(tank?.name ?: "鱼缸详情", onBack, action = { IconButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, contentDescription = "编辑鱼缸") } }) }) { padding ->
        if (tank == null || summary == null) LoadingState("正在加载鱼缸…") else Column(Modifier.fillMaxSize().padding(padding)) {
            ManagedPhotoImage(photo, true, Modifier.fillMaxWidth().height(170.dp))
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                AssistChip(onClick = {}, label = { Text(if (tank.type == TankType.freshwater) "淡水" else "海水") })
                Text("${tank.capacityValue?.let { "${it.pretty()} ${tank.capacityUnit.orEmpty()}" } ?: "容量未记录"} · 在养 ${fish.count { it.status == FishStatus.active || it.status == FishStatus.quarantine }}")
            }
            ScrollableTabRow(selectedTabIndex = section.ordinal) { TankSection.entries.forEach { Tab(selected = section == it, onClick = { section = it }, text = { Text(it.label) }) } }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (section) {
                    TankSection.overview -> {
                        InfoCard("鱼缸信息", content = { Text("尺寸：${listOfNotNull(tank.lengthCm, tank.widthCm, tank.heightCm).takeIf { it.size == 3 }?.joinToString(" × ") { it.pretty() + " cm" } ?: "未记录"}"); Text("启用日期：${tank.startedAt?.let(::formatDate) ?: "未记录"}"); Text("设备：${tank.equipment ?: "未记录"}"); Text("最近水质：${summary?.lastWaterTestAt?.let(::formatDate) ?: "暂无记录"}"); Text("最近换水：${summary?.lastWaterChangeAt?.let(::formatDate) ?: "暂无记录"}") })
                        if (tank.status == TankStatus.active) QuickActions(tankId, onQuickAction)
                        Text("近期记录", style = MaterialTheme.typography.titleMedium)
                        (tests.map { "水质检测" to it.test.measuredAt } + changes.map { "换水" to it.completedAt }).sortedByDescending { it.second }.take(6).ifEmpty { emptyList() }.forEach { (label, time) -> Text("$label · ${formatDate(time)}") }
                        if (tests.isEmpty() && changes.isEmpty()) Text("暂无水质或换水记录")
                    }
                    TankSection.fish -> {
                        Button(onClick = { onQuickAction("fish", tankId) }) { Text("添加鱼") }
                        if (fish.isEmpty()) Text("这个鱼缸还没有鱼只记录")
                        fish.forEach { Text("${it.name} · ${it.species ?: "品种未记录"} · ${it.status.toChineseLabel()}", Modifier.fillMaxWidth().clickable { selectedFish = it }.padding(vertical = 10.dp)) }
                    }
                    TankSection.water -> {
                        QuickActions(tankId, onQuickAction)
                        WaterQualityPanel(tank.type, tests, onRecord = { onQuickAction("water", tankId) })
                        Text("换水历史 · ${changes.size} 次", style = MaterialTheme.typography.titleMedium)
                        if (changes.isEmpty()) InfoCard("暂无换水记录", content = { Text("保存实际完成的换水后，记录会显示在这里。") }) else changes.forEach { change ->
                            InfoCard(formatDate(change.completedAt), content = {
                                Text("实际完成：${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(change.completedAt))}")
                                Text("换水量：${change.volumeValue?.let { "${it.pretty()} ${change.volumeUnit.orEmpty()}" } ?: "未记录"}")
                                if (change.ratio != null) Text("换水比例：${change.ratio.pretty()}%${if (tank.capacityIsEstimated) " · 容量估算" else ""}")
                                else if (tank.capacityValue == null) Text("比例：鱼缸容量未记录")
                                if (change.beforeTemperature != null || change.afterTemperature != null) Text("水温：${change.beforeTemperature?.pretty() ?: "未记录"} → ${change.afterTemperature?.pretty() ?: "未记录"} ${change.temperatureUnit.orEmpty()}")
                                if (change.beforeSalinity != null || change.afterSalinity != null) Text("盐度：${change.beforeSalinity?.pretty() ?: "未记录"} → ${change.afterSalinity?.pretty() ?: "未记录"} ${change.salinityUnit.orEmpty()}")
                                change.supplies?.let { Text("用品：$it") }
                                change.notes?.let { Text("备注：$it") }
                                change.nextPlannedAt?.let { Text("下次计划：${formatDate(it)}") }
                                TextButton(onClick = { onQuickAction("change/${change.id}", tankId) }) { Icon(Icons.Outlined.Edit, contentDescription = null); Text("编辑记录") }
                            })
                        }
                    }
                    TankSection.care -> {
                        if (tank.status == TankStatus.active) Button(onClick = { onQuickAction("task", tankId) }) { Text("添加养护任务") }
                        val tankTasks = tasks.filter { it.tankId == tankId }
                        val linkedTaskIds = tankTasks.filter { task -> changes.any { task.notes == waterChangeTaskMarker(it.id) } }.map { it.id }.toSet()
                        val visibleTasks = tankTasks.filter { it.id !in linkedTaskIds }
                        if (visibleTasks.isEmpty() && changes.none { it.nextPlannedAt != null }) Text("暂无养护任务")
                        visibleTasks.forEach { Text("${it.type.label()} · ${formatDate(it.plannedAt)} · ${it.status}") }
                        changes.mapNotNull { change -> change.nextPlannedAt?.let { it to change } }.sortedBy { it.first }.forEach { (plannedAt, change) ->
                            InfoCard("换水计划", content = { Text("计划日期：${formatDate(plannedAt)}"); Text("对应完成记录：${formatDate(change.completedAt)}"); TextButton(onClick = { onQuickAction("change/${change.id}", tankId) }) { Text("编辑记录与计划") } })
                        }
                    }
                    TankSection.stats -> InfoCard("真实记录统计", content = { Text("在养鱼只：${fish.count { it.status == FishStatus.active || it.status == FishStatus.quarantine }}"); Text("水质检测：${tests.size} 次"); Text("换水记录：${changes.size} 次"); Text("统计只包含已保存的本机记录。") })
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (tank.status == TankStatus.active) OutlinedButton(onClick = { showArchive = true }, modifier = Modifier.fillMaxWidth()) { Text("归档鱼缸") }
            }
        }
    }
}

@Composable
private fun QuickActions(tankId: String, onQuickAction: (String, String) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("feeding" to "快速喂食", "fish" to "添加鱼", "water" to "记录水质", "change" to "记录换水").forEach { (action, label) -> OutlinedButton(onClick = { onQuickAction(action, tankId) }, modifier = Modifier.weight(1f)) { Text(label, style = MaterialTheme.typography.labelSmall) } }
    }
}

@Composable
fun TankQuickFormScreen(tankId: String, action: String, editId: String? = null, onBack: () -> Unit, vm: TankViewModel = viewModel(factory = TankViewModel.factory(LocalContext.current.applicationContext as android.app.Application))) {
    val summary by remember(tankId) { vm.summary(tankId) }.collectAsStateWithLifecycle(initialValue = null)
    val tank = summary?.tank
    val parameters = remember(tank?.type) { tank?.type?.let(::waterParameters).orEmpty() }
    val actionTitle = when (action) { "fish" -> "添加鱼"; "water" -> "记录水质"; "change" -> if (editId == null) "记录换水" else "编辑换水记录"; else -> "添加养护任务" }
    var name by remember { mutableStateOf("") }
    var species by remember { mutableStateOf("") }
    var values by remember(tank?.type) { mutableStateOf(parameters.associate { it.code to "" }) }
    var selectedParameters by remember(tank?.type) { mutableStateOf(parameters.map { it.code }.toSet()) }
    var units by remember(tank?.type) { mutableStateOf(parameters.associate { it.code to it.unit }) }
    var methods by remember(tank?.type) { mutableStateOf(parameters.associate { it.code to "" }) }
    var parameterNotes by remember(tank?.type) { mutableStateOf(parameters.associate { it.code to "" }) }
    var targetMins by remember(tank?.type) { mutableStateOf(parameters.associate { it.code to "" }) }
    var targetMaxs by remember(tank?.type) { mutableStateOf(parameters.associate { it.code to "" }) }
    var volume by remember { mutableStateOf("") }
    var volumeUnit by remember { mutableStateOf("L") }
    var beforeTemperature by remember { mutableStateOf("") }
    var afterTemperature by remember { mutableStateOf("") }
    var beforeSalinity by remember { mutableStateOf("") }
    var afterSalinity by remember { mutableStateOf("") }
    var supplies by remember { mutableStateOf("") }
    var nextPlannedAt by remember { mutableStateOf<Long?>(null) }
    var taskType by remember { mutableStateOf(CareTaskType.water_change) }
    var taskCustomTitle by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var date by remember { androidx.compose.runtime.mutableLongStateOf(System.currentTimeMillis()) }
    var initialDate by remember(editId) { mutableLongStateOf(date) }
    var error by remember { mutableStateOf<String?>(null) }
    var warning by remember { mutableStateOf<String?>(null) }
    var pendingWaterTest by remember { mutableStateOf<Pair<WaterTest, List<WaterTestValue>>?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    var formLoaded by remember(editId) { mutableStateOf(editId == null) }
    LaunchedEffect(editId) {
        if (editId != null) {
            val record = vm.waterChange(editId)
            if (record != null && record.tankId == tankId) {
                volume = record.volumeValue?.pretty().orEmpty(); volumeUnit = record.volumeUnit ?: "L"
                beforeTemperature = record.beforeTemperature?.pretty().orEmpty(); afterTemperature = record.afterTemperature?.pretty().orEmpty()
                beforeSalinity = record.beforeSalinity?.pretty().orEmpty(); afterSalinity = record.afterSalinity?.pretty().orEmpty()
                supplies = record.supplies.orEmpty(); notes = record.notes.orEmpty(); date = record.completedAt; nextPlannedAt = record.nextPlannedAt
                initialDate = record.completedAt
            } else error = "无法加载这条换水记录"
            formLoaded = true
        }
    }
    val hasUnsavedChanges = when (action) {
        "fish" -> name.isNotBlank() || species.isNotBlank() || notes.isNotBlank()
        "water" -> date != initialDate || values.values.any(String::isNotBlank) || notes.isNotBlank() || methods.values.any(String::isNotBlank) || parameterNotes.values.any(String::isNotBlank) || targetMins.values.any(String::isNotBlank) || targetMaxs.values.any(String::isNotBlank) || selectedParameters != parameters.map { it.code }.toSet() || units != parameters.associate { it.code to it.unit }
        "change" -> date != initialDate || volume.isNotBlank() || beforeTemperature.isNotBlank() || afterTemperature.isNotBlank() || beforeSalinity.isNotBlank() || afterSalinity.isNotBlank() || supplies.isNotBlank() || notes.isNotBlank() || nextPlannedAt != null || volumeUnit != "L"
        else -> date != initialDate || taskType != CareTaskType.water_change || taskCustomTitle.isNotBlank() || notes.isNotBlank()
    }
    val leaveForm = rememberLeaveFormHandler(formLoaded && hasUnsavedChanges, onBack)
    val persistWaterTest: (WaterTest, List<WaterTestValue>) -> Unit = { test, records ->
        isSaving = true
        vm.addWaterTest(test, records, { isSaving = false; onBack() }, { message -> isSaving = false; error = message })
    }
    val taskLabels = mapOf(CareTaskType.feeding to "喂食", CareTaskType.water_change to "换水", CareTaskType.water_test to "水质检测", CareTaskType.salinity_test to "盐度检测", CareTaskType.maintenance to "设备维护", CareTaskType.custom to "自定义")

    Scaffold(topBar = { TopAppBarSimple(actionTitle, leaveForm) }, bottomBar = { Surface(shadowElevation = 4.dp) { Button(enabled = !isSaving && formLoaded, onClick = {
        if (tank == null || isSaving || !formLoaded) return@Button
        val now = System.currentTimeMillis()
        val failed = { message: String -> isSaving = false; error = message }
        val saved = { isSaving = false; onBack() }
        when (action) {
            "fish" -> {
                if (name.isBlank()) { error = "请输入鱼只名称"; return@Button }
                isSaving = true
                vm.addFish(Fish(tankId = tankId, name = name.trim(), species = species.ifBlank { null }, coverPhotoId = null, addedAt = date, birthDate = null, status = FishStatus.active, statusChangedAt = null, notes = notes.ifBlank { null }, createdAt = now, updatedAt = now), saved, failed)
            }
            "water" -> {
                val entered = parameters.filter { it.code in selectedParameters && values[it.code].orEmpty().isNotBlank() }
                if (entered.isEmpty()) { error = "至少填写一项实测水质参数"; return@Button }
                val invalid = entered.any { parameter -> isValidWaterMeasurement(values[parameter.code].orEmpty(), targetMins[parameter.code].orEmpty(), targetMaxs[parameter.code].orEmpty()).not() }
                if (invalid) { error = "检测值和目标范围请输入有效数字，并确认下限不高于上限"; return@Button }
                isSaving = true
                val test = WaterTest(tankId = tankId, measuredAt = date, method = null, notes = notes.ifBlank { null })
                val records = entered.map { parameter ->
                    val minimum = targetMins[parameter.code]?.takeIf(String::isNotBlank)?.toDoubleOrNull()
                    val maximum = targetMaxs[parameter.code]?.takeIf(String::isNotBlank)?.toDoubleOrNull()
                    WaterTestValue(waterTestId = test.id, parameterCode = parameter.code, value = values[parameter.code]?.toDoubleOrNull(), unit = units[parameter.code] ?: parameter.unit, targetMin = minimum, targetMax = maximum, measuredAt = date, method = methods[parameter.code]?.ifBlank { null }, notes = parameterNotes[parameter.code]?.ifBlank { null }, targetSource = if (minimum != null || maximum != null) "user" else null)
                }
                val outside = records.filter { isOutsideWaterTarget(it.value!!, it.targetMin, it.targetMax) }
                if (outside.isNotEmpty()) {
                    warning = "${outside.joinToString { parameterLabel(it.parameterCode) }} 超出你设置的目标范围；请核对测量并考虑复测。不会自动调整鱼缸。"
                    pendingWaterTest = test to records
                    return@Button
                }
                persistWaterTest(test, records)
            }
            "change" -> {
                val quantity = volume.toDoubleOrNull()
                if (quantity == null || !quantity.isFinite() || quantity <= 0) { error = "请输入大于 0 的换水量"; return@Button }
                val optionalNumbers = listOf(beforeTemperature, afterTemperature, beforeSalinity, afterSalinity)
                if (optionalNumbers.any { it.isNotBlank() && (it.toDoubleOrNull()?.let { number -> number > 0 && number.isFinite() } != true) }) { error = "水温和盐度须为大于 0 的有效数值"; return@Button }
                isSaving = true
                vm.addWaterChange(WaterChange(id = editId ?: java.util.UUID.randomUUID().toString(), tankId = tankId, completedAt = date, volumeValue = quantity, volumeUnit = volumeUnit, ratio = calculateWaterChangeRatioPercent(quantity, volumeUnit, tank.capacityValue, tank.capacityUnit), nextPlannedAt = nextPlannedAt, notes = notes.ifBlank { null }, beforeTemperature = beforeTemperature.toDoubleOrNull(), afterTemperature = afterTemperature.toDoubleOrNull(), temperatureUnit = if (beforeTemperature.isNotBlank() || afterTemperature.isNotBlank()) "°C" else null, beforeSalinity = beforeSalinity.toDoubleOrNull(), afterSalinity = afterSalinity.toDoubleOrNull(), salinityUnit = if (beforeSalinity.isNotBlank() || afterSalinity.isNotBlank()) "ppt" else null, supplies = supplies.ifBlank { null }), saved, failed)
            }
            else -> {
                if (taskType == CareTaskType.custom && taskCustomTitle.isBlank()) { error = "请输入自定义任务名称"; return@Button }
                isSaving = true
                vm.addTask(CareTask(tankId = tankId, fishId = null, type = taskType, plannedAt = date, recurrenceRule = null, notes = notes.ifBlank { null }, createdAt = now, updatedAt = now, customTitle = taskCustomTitle.trim().ifBlank { null }), saved, failed)
            }
        }
    }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) { Text(if (isSaving) "保存中…" else "保存") } } }) { padding ->
        if (tank == null) Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { LoadingState() }
        else Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("关联鱼缸：${tank.name}", style = MaterialTheme.typography.titleSmall)
            when (action) {
                "fish" -> {
                    OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("鱼只名称 *") }, singleLine = true)
                    OutlinedTextField(species, { species = it }, Modifier.fillMaxWidth(), label = { Text("品种") }, singleLine = true)
                    DateField("入缸日期", date) { date = it ?: date }
                    OutlinedTextField(notes, { notes = it }, Modifier.fillMaxWidth(), label = { Text("备注") }, minLines = 2)
                }
                "water" -> {
                    Text("${if (tank.type == TankType.freshwater) "淡水" else "海水"}水质参数")
                    Text("选择本次要测的参数", style = MaterialTheme.typography.titleSmall)
                    parameters.forEach { parameter ->
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = parameter.code in selectedParameters, onCheckedChange = { checked -> selectedParameters = if (checked) selectedParameters + parameter.code else selectedParameters - parameter.code })
                                Text(parameter.label, modifier = Modifier.weight(1f))
                                var unitMenu by remember(parameter.code) { mutableStateOf(false) }
                                Box { TextButton(onClick = { unitMenu = true }) { Text(units[parameter.code] ?: parameter.unit) }; DropdownMenu(unitMenu, { unitMenu = false }) { parameter.units.forEach { option -> DropdownMenuItem(text = { Text(option) }, onClick = { units = units + (parameter.code to option); unitMenu = false }) } } }
                            }
                            if (parameter.code in selectedParameters) {
                                OutlinedTextField(values[parameter.code].orEmpty(), { values = values + (parameter.code to it) }, Modifier.fillMaxWidth(), label = { Text("${parameter.label}（${units[parameter.code] ?: parameter.unit}）") }, singleLine = true)
                                OutlinedTextField(methods[parameter.code].orEmpty(), { methods = methods + (parameter.code to it) }, Modifier.fillMaxWidth(), label = { Text("测量方式（可选）") }, singleLine = true)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(targetMins[parameter.code].orEmpty(), { targetMins = targetMins + (parameter.code to it) }, Modifier.weight(1f), label = { Text("目标下限（可选）") }, singleLine = true)
                                    OutlinedTextField(targetMaxs[parameter.code].orEmpty(), { targetMaxs = targetMaxs + (parameter.code to it) }, Modifier.weight(1f), label = { Text("目标上限（可选）") }, singleLine = true)
                                }
                                Text("目标范围由你设置，仅作记录参考", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                OutlinedTextField(parameterNotes[parameter.code].orEmpty(), { parameterNotes = parameterNotes + (parameter.code to it) }, Modifier.fillMaxWidth(), label = { Text("参数备注（可选）") }, minLines = 1)
                            }
                        }
                    }
                    DateTimeField("检测时间", date) { date = it }
                    OutlinedTextField(notes, { notes = it }, Modifier.fillMaxWidth(), label = { Text("备注") }, minLines = 2)
                }
                "change" -> {
                    tank.capacityValue?.let { Text("鱼缸容量：${it.pretty()} ${tank.capacityUnit.orEmpty()}${if (tank.capacityIsEstimated) "（估算）" else ""}；保存时据此计算比例。", style = MaterialTheme.typography.bodySmall) } ?: Text("鱼缸容量未记录；只保存换水量，不计算比例。", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(volume, { volume = it }, Modifier.fillMaxWidth(), label = { Text("换水量 *") }, singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("L", "gal").forEach { FilterChip(volumeUnit == it, { volumeUnit = it }, label = { Text(it) }) } }
                    DateTimeField("实际完成时间", date) { date = it }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(beforeTemperature, { beforeTemperature = it }, Modifier.weight(1f), label = { Text("换水前水温 °C") }, singleLine = true)
                        OutlinedTextField(afterTemperature, { afterTemperature = it }, Modifier.weight(1f), label = { Text("换水后水温 °C") }, singleLine = true)
                    }
                    if (tank.type == TankType.saltwater) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(beforeSalinity, { beforeSalinity = it }, Modifier.weight(1f), label = { Text("换水前盐度 ppt") }, singleLine = true)
                        OutlinedTextField(afterSalinity, { afterSalinity = it }, Modifier.weight(1f), label = { Text("换水后盐度 ppt") }, singleLine = true)
                    }
                    OutlinedTextField(supplies, { supplies = it }, Modifier.fillMaxWidth(), label = { Text("用品（可选）") }, singleLine = true)
                    DateField("下一次换水计划（可选）", nextPlannedAt) { nextPlannedAt = it }
                    OutlinedTextField(notes, { notes = it }, Modifier.fillMaxWidth(), label = { Text("备注") }, minLines = 2)
                }
                else -> {
                    var menuOpen by remember { mutableStateOf(false) }
                    val availableTaskLabels = taskLabels.filterKeys { it != CareTaskType.salinity_test || (tank.type == TankType.saltwater && tank.salinityManagementEnabled) }
                    Box { OutlinedButton(onClick = { menuOpen = true }) { Text("任务类型：${availableTaskLabels[taskType] ?: "换水"}") }; DropdownMenu(menuOpen, { menuOpen = false }) { availableTaskLabels.forEach { (type, label) -> DropdownMenuItem(text = { Text(label) }, onClick = { taskType = type; menuOpen = false }) } } }
                    if (taskType == CareTaskType.custom) OutlinedTextField(taskCustomTitle, { taskCustomTitle = it }, Modifier.fillMaxWidth(), label = { Text("自定义任务名称 *") }, singleLine = true)
                    DateField("计划时间", date) { date = it ?: date }
                    OutlinedTextField(notes, { notes = it }, Modifier.fillMaxWidth(), label = { Text("备注") }, minLines = 2)
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            warning?.let { message ->
                AlertDialog(onDismissRequest = { warning = null; pendingWaterTest = null }, title = { Text("请核对测量") }, text = { Text(message) }, confirmButton = { TextButton(onClick = { warning = null; pendingWaterTest?.let { persistWaterTest(it.first, it.second) }; pendingWaterTest = null }) { Text("已核对，仍然保存") } }, dismissButton = { TextButton(onClick = { warning = null; pendingWaterTest = null }) { Text("返回检查") } })
            }
        }
    }
}

@Composable
private fun WaterQualityPanel(type: TankType, tests: List<WaterTestWithValues>, onRecord: () -> Unit) {
    val parameters = remember(type) { waterParameters(type) }
    val measured = remember(tests) { tests.flatMap { record -> record.values.filter { it.value != null }.map { value -> Triple(record.test, value, value.measuredAt ?: record.test.measuredAt) } } }
    var selectedCode by remember(type) { mutableStateOf(parameters.firstOrNull()?.code.orEmpty()) }
    var fromDate by remember { mutableStateOf<Long?>(null) }
    var toDate by remember { mutableStateOf<Long?>(null) }
    val latestByParameter = measured.groupBy { it.second.parameterCode }.mapValues { (_, values) -> values.maxBy { it.third } }

    Text("最近水质", style = MaterialTheme.typography.titleMedium)
    if (latestByParameter.isEmpty()) {
        InfoCard("尚无测量数据", content = { Text("保存实测值后，这里会显示最近水质摘要和趋势。") })
    } else {
        latestByParameter.toSortedMap().forEach { (code, point) ->
            val (test, value, measuredAt) = point
            InfoCard(parameterLabel(code), content = {
                Text("${value.value?.pretty() ?: "未测量"} ${value.unit}", style = MaterialTheme.typography.titleLarge)
                Text("测量时间：${formatDate(measuredAt)}")
                if (value.targetMin != null || value.targetMax != null) {
                    val range = "${value.targetMin?.pretty() ?: "不限"} - ${value.targetMax?.pretty() ?: "不限"} ${value.unit}"
                    Text("${targetSourceLabel(value.targetSource)}：$range", style = MaterialTheme.typography.bodySmall)
                }
                if (test.method != null || value.method != null) Text("方式：${value.method ?: test.method}", style = MaterialTheme.typography.bodySmall)
            })
        }
    }

    Text("参数趋势", style = MaterialTheme.typography.titleMedium)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        parameters.forEach { parameter -> FilterChip(selected = selectedCode == parameter.code, onClick = { selectedCode = parameter.code }, label = { Text(parameter.label) }) }
    }
    val endOfSelectedDay = toDate?.plus(86_399_999L)
    val trend = measured.filter { it.second.parameterCode == selectedCode && (fromDate == null || it.third >= fromDate!!) && (endOfSelectedDay == null || it.third <= endOfSelectedDay) }.sortedBy { it.third }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        DateField("开始日期", fromDate) { fromDate = it }
        DateField("结束日期", toDate) { toDate = it }
    }
    if (trend.isEmpty()) InfoCard("暂无${parameterLabel(selectedCode)}数据", content = { Text("所选时间范围内没有实测点。") })
    else WaterTrendGraph(trend)

    Text("检测历史", style = MaterialTheme.typography.titleMedium)
    var historyCode by remember(type) { mutableStateOf("all") }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        FilterChip(historyCode == "all", { historyCode = "all" }, label = { Text("全部") })
        parameters.forEach { parameter -> FilterChip(historyCode == parameter.code, { historyCode = parameter.code }, label = { Text(parameter.label) }) }
    }
    val history = tests.filter { record ->
        (fromDate == null || record.test.measuredAt >= fromDate!!) && (endOfSelectedDay == null || record.test.measuredAt <= endOfSelectedDay) && (historyCode == "all" || record.values.any { it.parameterCode == historyCode })
    }
    if (history.isEmpty()) Text("所选条件下暂无检测记录") else history.forEach { record ->
        InfoCard(formatDate(record.test.measuredAt), content = {
            record.values.filter { historyCode == "all" || it.parameterCode == historyCode }.forEach { value ->
                Text("${parameterLabel(value.parameterCode)}：${value.value?.pretty() ?: "未记录"}${value.value?.let { " ${value.unit}" }.orEmpty()}")
                if (value.targetMin != null || value.targetMax != null) Text("${targetSourceLabel(value.targetSource)}：${value.targetMin?.pretty() ?: "不限"} - ${value.targetMax?.pretty() ?: "不限"} ${value.unit}", style = MaterialTheme.typography.bodySmall)
                (value.notes ?: record.test.notes)?.let { Text("备注：$it", style = MaterialTheme.typography.bodySmall) }
            }
        })
    }
    Button(onClick = onRecord, modifier = Modifier.fillMaxWidth()) { Text("添加水质记录") }
}

@Composable
private fun WaterTrendGraph(points: List<Triple<WaterTest, WaterTestValue, Long>>) {
    val sorted = points.sortedBy { it.third }
    val color = MaterialTheme.colorScheme.primary
    val axisColor = MaterialTheme.colorScheme.outlineVariant
    InfoCard("${parameterLabel(sorted.first().second.parameterCode)}趋势", content = {
        Text("单位：${sorted.first().second.unit} · ${sorted.size} 个实测点", style = MaterialTheme.typography.bodySmall)
        Column(Modifier.horizontalScroll(rememberScrollState())) {
            Column {
                Canvas(Modifier.width((sorted.size * 76).coerceAtLeast(260).dp).height(160.dp)) {
                    val values = sorted.mapNotNull { it.second.value }
                    val min = values.minOrNull() ?: return@Canvas
                    val max = values.maxOrNull() ?: min
                    val spread = (max - min).takeIf { it > 0.0 } ?: 1.0
                    val left = 28f
                    val right = size.width - 12f
                    val top = 14f
                    val bottom = size.height - 22f
                    val coordinates = sorted.mapIndexedNotNull { index, item ->
                        val value = item.second.value ?: return@mapIndexedNotNull null
                        val x = if (sorted.size == 1) (left + right) / 2f else left + (right - left) * index / (sorted.size - 1)
                        val y = bottom - ((value - min) / spread).toFloat() * (bottom - top)
                        Offset(x, y)
                    }
                    drawLine(axisColor, Offset(left, bottom), Offset(right, bottom), strokeWidth = 1.dp.toPx())
                    if (coordinates.size > 1) {
                        val path = Path().apply { moveTo(coordinates.first().x, coordinates.first().y); coordinates.drop(1).forEach { lineTo(it.x, it.y) } }
                        drawPath(path, color, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()))
                    }
                    coordinates.forEach { drawCircle(color, radius = 4.dp.toPx(), center = it) }
                }
                Row(Modifier.width((sorted.size * 76).coerceAtLeast(260).dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    sorted.forEach { (_, value, measuredAt) ->
                        Column(Modifier.width(72.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(formatDate(measuredAt).takeLast(5), style = MaterialTheme.typography.labelSmall)
                            Text(value.value?.pretty().orEmpty(), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    })
}

@Composable
private fun TopAppBarSimple(title: String, onBack: () -> Unit, action: @Composable RowScope.() -> Unit = {}) {
    TopAppBar(title = { Text(title) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回") } }, actions = action)
}

@Composable
private fun DateField(label: String, value: Long?, onChange: (Long?) -> Unit) {
    val context = LocalContext.current
    OutlinedButton(onClick = {
        val calendar = Calendar.getInstance().apply { if (value != null) timeInMillis = value }
        DatePickerDialog(context, { _, year, month, day -> onChange(Calendar.getInstance().apply { set(year, month, day, 12, 0, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis) }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
    }, modifier = Modifier.fillMaxWidth()) { Text("$label  ${value?.let(::formatDate) ?: "选择日期"}") }
}

@Composable
private fun DateTimeField(label: String, value: Long, onChange: (Long) -> Unit) {
    val context = LocalContext.current
    OutlinedButton(onClick = {
        val initial = Calendar.getInstance().apply { timeInMillis = value }
        DatePickerDialog(context, { _, year, month, day ->
            val selectedDate = Calendar.getInstance().apply {
                timeInMillis = value
                set(Calendar.YEAR, year); set(Calendar.MONTH, month); set(Calendar.DAY_OF_MONTH, day)
            }
            TimePickerDialog(context, { _, hour, minute ->
                selectedDate.set(Calendar.HOUR_OF_DAY, hour); selectedDate.set(Calendar.MINUTE, minute); selectedDate.set(Calendar.SECOND, 0); selectedDate.set(Calendar.MILLISECOND, 0)
                onChange(selectedDate.timeInMillis)
            }, initial.get(Calendar.HOUR_OF_DAY), initial.get(Calendar.MINUTE), true).show()
        }, initial.get(Calendar.YEAR), initial.get(Calendar.MONTH), initial.get(Calendar.DAY_OF_MONTH)).show()
    }, modifier = Modifier.fillMaxWidth()) {
        Text("$label  ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(value))}")
    }
}

private fun formatDate(value: Long) = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(value))
private fun Double.pretty() = if (this % 1.0 == 0.0) toLong().toString() else "%.1f".format(Locale.getDefault(), this)
private fun targetSourceLabel(source: String?) = when (source) { "user" -> "目标范围（用户设置）"; "config" -> "目标范围（来源配置）"; else -> "目标范围（来源未记录）" }
private fun FishStatus.toChineseLabel() = when (this) { FishStatus.active -> "在养"; FishStatus.quarantine -> "检疫"; FishStatus.transferred -> "已转出"; FishStatus.deceased -> "已故"; FishStatus.archived -> "已归档" }
private fun CareTaskType.label() = when (this) { CareTaskType.feeding -> "喂食"; CareTaskType.water_change -> "换水"; CareTaskType.water_test -> "水质检测"; CareTaskType.salinity_test -> "盐度检测"; CareTaskType.maintenance -> "设备维护"; CareTaskType.custom -> "自定义" }
