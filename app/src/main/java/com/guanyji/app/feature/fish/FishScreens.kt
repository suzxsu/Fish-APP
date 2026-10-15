@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.guanyji.app.feature.fish

import android.app.Application
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.guanyji.app.core.designsystem.*
import com.guanyji.app.data.local.*
import com.guanyji.app.feature.photo.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

private enum class FishSort(val label: String) { added("最近添加"), tankDate("入缸日期"), measured("最近测量") }
private val statuses = listOf(null to "全部", FishStatus.active to "在养", FishStatus.quarantine to "隔离", FishStatus.transferred to "转出", FishStatus.deceased to "已故", FishStatus.archived to "归档")

@Composable
fun FishListScreen(onCreate: () -> Unit, onOpen: (String) -> Unit, onEdit: (String) -> Unit, vm: FishViewModel = viewModel(factory = FishViewModel.factory(LocalContext.current.applicationContext as Application))) {
    val data by vm.fish.collectAsStateWithLifecycle()
    val tanks by vm.tanks.collectAsStateWithLifecycle()
    var search by remember { mutableStateOf("") }
    var tankId by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<FishStatus?>(null) }
    var sort by remember { mutableStateOf(FishSort.added) }
    var tankMenu by remember { mutableStateOf(false) }
    val tankNames = tanks.associate { it.id to it.name }
    val rows = data.orEmpty().filter { row ->
        (status == null || row.fish.status == status) && (tankId == null || row.fish.tankId == tankId) &&
            (row.fish.name.contains(search.trim(), true) || row.fish.species.orEmpty().contains(search.trim(), true))
    }.let { rows -> when (sort) {
        FishSort.added -> rows.sortedByDescending { it.fish.createdAt }
        FishSort.tankDate -> rows.sortedByDescending { it.fish.addedAt }
        FishSort.measured -> rows.sortedByDescending { it.lastMeasuredAt ?: Long.MIN_VALUE }
    } }
    Scaffold(floatingActionButton = { FloatingActionButton(onClick = onCreate) { Icon(Icons.Outlined.Add, contentDescription = "添加鱼") } }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            PageTitle("我的鱼", "${data?.count { it.fish.status == FishStatus.active || it.fish.status == FishStatus.quarantine } ?: 0} 条在养")
            OutlinedTextField(search, { search = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("搜索名称或鱼种") })
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box {
                    OutlinedButton(onClick = { tankMenu = true }) { Text("鱼缸：${tankId?.let(tankNames::get) ?: "全部"}") }
                    DropdownMenu(tankMenu, { tankMenu = false }) {
                        DropdownMenuItem(text = { Text("全部鱼缸") }, onClick = { tankId = null; tankMenu = false })
                        tanks.forEach { t -> DropdownMenuItem(text = { Text(t.name) }, onClick = { tankId = t.id; tankMenu = false }) }
                    }
                }
                Spacer(Modifier.width(8.dp))
                var sortMenu by remember { mutableStateOf(false) }
                Box {
                    OutlinedButton(onClick = { sortMenu = true }) { Text("排序：${sort.label}") }
                    DropdownMenu(sortMenu, { sortMenu = false }) { FishSort.entries.forEach { item -> DropdownMenuItem(text = { Text(item.label) }, onClick = { sort = item; sortMenu = false }) } }
                }
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                statuses.forEach { (value, label) -> FilterChip(status == value, { status = value }, label = { Text(label, style = MaterialTheme.typography.labelSmall) }) }
            }
            if (data == null) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingState() }
            else if (rows.isEmpty()) Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                EmptyState(if (search.isNotBlank() || status != null || tankId != null) "没有匹配的鱼只" else "还没有鱼只档案", "添加鱼只后，照片、成长和喂食记录会在这里汇总。")
                if (search.isNotBlank()) TextButton(onClick = { search = "" }) { Text("清除搜索") }
                Button(onClick = onCreate) { Text("添加鱼") }
            } else LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp), contentPadding = PaddingValues(bottom = 88.dp)) {
                items(rows, key = { it.fish.id }) { row ->
                    val photo by remember(row.fish.coverPhotoId) { vm.photo(row.fish.coverPhotoId) }.collectAsStateWithLifecycle()
                    FishCard(row, photo, tankNames[row.fish.tankId] ?: "鱼缸未找到", { onOpen(row.fish.id) }, { onEdit(row.fish.id) })
                }
            }
        }
    }
}

@Composable
private fun FishCard(row: FishSummary, photo: Photo?, tankName: String, onOpen: () -> Unit, onEdit: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Row(Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            ManagedPhotoImage(photo, true, Modifier.size(92.dp, 86.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(row.fish.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text(row.fish.status.label(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
                Text("${row.fish.species ?: "魚種未記錄"} · $tankName", style = MaterialTheme.typography.bodySmall)
                Text("入缸 ${durationDays(row.fish.addedAt, row.fish.deathDate, row.fish.status == FishStatus.deceased)} 天 · 體長 ${row.lastLength?.let { "${it.pretty()} ${row.lastLengthUnit.orEmpty()}" } ?: "暂无记录"}", style = MaterialTheme.typography.bodySmall)
                Text("今日喂食 ${if (row.todayFeedings == 0) "暂无记录" else "${row.todayFeedings} 次"}", style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, contentDescription = "编辑${row.fish.name}") }
        }
    }
}

@Composable
fun FishFormScreen(fishId: String?, onBack: () -> Unit, onSaved: (String) -> Unit, vm: FishViewModel = viewModel(factory = FishViewModel.factory(LocalContext.current.applicationContext as Application))) {
    val allFish by vm.fish.collectAsStateWithLifecycle()
    val tanks by vm.tanks.collectAsStateWithLifecycle()
    val existing = allFish?.firstOrNull { it.fish.id == fishId }?.fish
    val photoVm: PhotoViewModel = viewModel(key = "fish-photo-$fishId", factory = PhotoViewModelFactory(vm.photoRepository))
    var initialized by remember(fishId) { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }; var species by remember { mutableStateOf("") }; var tankId by remember { mutableStateOf("") }
    var addedAt by remember { mutableLongStateOf(todayStart()) }; var birthDate by remember { mutableStateOf<Long?>(null) }
    var length by remember { mutableStateOf("") }; var weight by remember { mutableStateOf("") }; var sex by remember { mutableStateOf("") }
    var source by remember { mutableStateOf("") }; var notes by remember { mutableStateOf("") }; var busy by remember { mutableStateOf(false) }; var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(existing?.id, tanks.size) {
        if (fishId != null && existing != null && !initialized) {
            name = existing.name; species = existing.species.orEmpty(); tankId = existing.tankId; addedAt = existing.addedAt; birthDate = existing.birthDate
            sex = existing.sex.orEmpty(); source = existing.source.orEmpty(); notes = existing.notes.orEmpty()
            photoVm.load(existing.coverPhotoId); initialized = true
        } else if (fishId == null && tankId.isBlank()) tankId = tanks.firstOrNull { it.status == TankStatus.active }?.id.orEmpty()
    }
    val photoState by photoVm.state.collectAsStateWithLifecycle()
    val tank = tanks.firstOrNull { it.id == tankId }
    val hasUnsavedChanges = photoState.isBusy || if (fishId == null) {
        name.isNotBlank() || species.isNotBlank() || length.isNotBlank() || weight.isNotBlank() || sex.isNotBlank() || source.isNotBlank() || notes.isNotBlank() || photoState.photo != null
    } else initialized && existing != null && (
        name != existing.name || species != existing.species.orEmpty() || tankId != existing.tankId || addedAt != existing.addedAt || birthDate != existing.birthDate ||
            sex != existing.sex.orEmpty() || source != existing.source.orEmpty() || notes != existing.notes.orEmpty() || photoState.photo?.id != existing.coverPhotoId
        )
    val leaveForm = rememberLeaveFormHandler(hasUnsavedChanges, onBack)
    Scaffold(
        topBar = { FishTopBar(if (fishId == null) "添加鱼" else "编辑鱼", leaveForm) },
        bottomBar = {
            Surface(shadowElevation = 4.dp) {
                Button(enabled = !busy && !photoState.isBusy, onClick = {
        if (busy) return@Button
        val cleanName = name.trim(); val lengthValue = length.toDoubleOrNull(); val weightValue = weight.toDoubleOrNull()
        if (cleanName.isBlank()) { error = "鱼名为必填项"; return@Button }
        if (tank?.status != TankStatus.active) { error = "请选择一个在用鱼缸"; return@Button }
        if (todayStartOf(addedAt) > todayStart()) { error = "入缸日期不能晚于今天"; return@Button }
        if (birthDate != null && todayStartOf(birthDate!!) > todayStartOf(addedAt)) { error = "出生日期不能晚于入缸日期"; return@Button }
        if (length.isNotBlank() && (lengthValue == null || !lengthValue.isFinite() || lengthValue <= 0)) { error = "初始体长请输入大于 0 的有效数字"; return@Button }
        if (weight.isNotBlank() && (weightValue == null || !weightValue.isFinite() || weightValue <= 0)) { error = "初始体重请输入大于 0 的有效数字"; return@Button }
        val now = System.currentTimeMillis(); val id = existing?.id ?: UUID.randomUUID().toString()
        val value = Fish(id = id, tankId = tankId, name = cleanName, species = species.trim().ifBlank { null }, coverPhotoId = photoState.photo?.id ?: existing?.coverPhotoId, addedAt = addedAt, birthDate = birthDate, status = existing?.status ?: FishStatus.active, statusChangedAt = existing?.statusChangedAt, notes = notes.trim().ifBlank { null }, sex = sex.ifBlank { null }, source = source.trim().ifBlank { null }, deathDateKnown = existing?.deathDateKnown ?: true, deathDate = existing?.deathDate, createdAt = existing?.createdAt ?: now, updatedAt = now)
        busy = true; error = null
        vm.save(value, if (fishId == null) lengthValue else null, if (fishId == null) weightValue else null, { busy = false; onSaved(id) }, { busy = false; error = it })
                }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) { Text(if (busy) "保存中…" else "保存") }
            }
        },
    ) { padding ->
        if (fishId != null && existing == null) Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { LoadingState("正在加载鱼只档案") }
        else Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PhotoPickerField(photoVm)
            OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("鱼名 *") }, singleLine = true)
            OutlinedTextField(species, { species = it }, Modifier.fillMaxWidth(), label = { Text("鱼种（可选）") }, singleLine = true)
            var tankMenu by remember { mutableStateOf(false) }
            Box { OutlinedButton(onClick = { tankMenu = true }, modifier = Modifier.fillMaxWidth()) { Text("所属鱼缸 *：${tank?.name ?: "选择鱼缸"} · ${tank?.type?.let { if (it == TankType.freshwater) "淡水" else "海水" }.orEmpty()}") }; DropdownMenu(tankMenu, { tankMenu = false }) { tanks.filter { it.status == TankStatus.active }.forEach { t -> DropdownMenuItem(text = { Text("${t.name} · ${if (t.type == TankType.freshwater) "淡水" else "海水"}") }, onClick = { tankId = t.id; tankMenu = false }) } } }
            DateField("入缸日期 *", addedAt) { it?.let { value -> addedAt = value } }
            DateField("出生日期（可选）", birthDate, true) { birthDate = it }
            OutlinedTextField(length, { length = it }, Modifier.fillMaxWidth(), label = { Text(if (fishId == null) "初始体长（cm，可选）" else "初始体长仅首次建档时填写") }, singleLine = true, enabled = fishId == null)
            OutlinedTextField(weight, { weight = it }, Modifier.fillMaxWidth(), label = { Text(if (fishId == null) "初始体重（g，可选）" else "初始体重仅首次建档时填写") }, singleLine = true, enabled = fishId == null)
            OutlinedTextField(sex, { sex = it }, Modifier.fillMaxWidth(), label = { Text("性别（可选）") }, singleLine = true)
            OutlinedTextField(source, { source = it }, Modifier.fillMaxWidth(), label = { Text("来源（可选）") }, singleLine = true)
            OutlinedTextField(notes, { notes = it }, Modifier.fillMaxWidth(), label = { Text("备注（可选）") }, minLines = 2)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
fun FishDetailScreen(fishId: String, onBack: () -> Unit, onEdit: () -> Unit, onOpenRecords: (String) -> Unit = {}, onQuickFeeding: () -> Unit = {}, vm: FishViewModel = viewModel(factory = FishViewModel.factory(LocalContext.current.applicationContext as Application))) {
    val summary by remember(fishId) { vm.summary(fishId) }.collectAsStateWithLifecycle()
    val tanks by vm.tanks.collectAsStateWithLifecycle(); val events by remember(fishId) { vm.events(fishId) }.collectAsStateWithLifecycle(); val growth by remember(fishId) { vm.growth(fishId) }.collectAsStateWithLifecycle()
    val latestLength by remember(fishId) { vm.latestLength(fishId) }.collectAsStateWithLifecycle()
    val latestWeight by remember(fishId) { vm.latestWeight(fishId) }.collectAsStateWithLifecycle()
    val fish = summary?.fish; val photo by remember(fish?.coverPhotoId) { vm.photo(fish?.coverPhotoId) }.collectAsStateWithLifecycle()
    var action by remember { mutableStateOf<FishStatus?>(null) }; var showArchive by remember { mutableStateOf(false) }; var unknownDeath by remember { mutableStateOf(false) }; var showMove by remember { mutableStateOf(false) }; var targetTankId by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }; var busy by remember { mutableStateOf(false) }; var date by remember { mutableLongStateOf(todayStart()) }
    Scaffold(topBar = { FishTopBar(fish?.name ?: "鱼只详情", onBack, { IconButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, contentDescription = "编辑档案") } }) }) { padding ->
        if (summary == null) Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { LoadingState("正在加载鱼只档案") }
        else Column(Modifier.fillMaxSize().padding(padding)) {
            ManagedPhotoImage(photo, false, Modifier.fillMaxWidth().height(210.dp))
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("${fish!!.name} · ${fish.species ?: "鱼种未记录"}", style = MaterialTheme.typography.headlineSmall)
                Text("${tanks.firstOrNull { it.id == fish.tankId }?.name ?: "鱼缸未找到"} · ${fish.status.label()}")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    InfoCard("入缸时长", { Text("${durationDays(fish.addedAt, fish.deathDate, fish.status == FishStatus.deceased)} 天") }, Modifier.weight(1f))
                    InfoCard("最近体长", { Text(latestLength?.let { "${it.lengthValue?.pretty()} ${it.lengthUnit.orEmpty()}" } ?: "暂无记录"); latestLength?.let { Text(formatDate(it.measuredAt), style = MaterialTheme.typography.bodySmall) } }, Modifier.weight(1f))
                }
                InfoCard("最近体重", { Text(latestWeight?.let { "${it.weightValue?.pretty()} ${it.weightUnit.orEmpty()}" } ?: "暂无记录"); latestWeight?.let { Text(formatDate(it.measuredAt), style = MaterialTheme.typography.bodySmall) } })
                InfoCard("档案信息", { Text("入缸日期：${formatDate(fish.addedAt)}"); Text("出生日期：${fish.birthDate?.let(::formatDate) ?: "未记录"}"); if (fish.status == FishStatus.deceased) Text("死亡日期：${fish.deathDate?.let(::formatDate) ?: "未知"}"); Text("性别：${fish.sex ?: "未记录"} · 来源：${fish.source ?: "未记录"}"); Text("备注：${fish.notes ?: "暂无"}") })
                InfoCard("今日喂食", { Text(if (summary!!.todayFeedings == 0) "暂无记录" else "已记录 ${summary!!.todayFeedings} 次") })
                InfoCard("成长记录", { Text("共 ${growth.size} 条"); growth.take(3).forEach { Text("${formatDate(it.measuredAt)} · 体长 ${it.lengthValue?.let { n -> "${n.pretty()} ${it.lengthUnit.orEmpty()}" } ?: "暂无记录"} · 体重 ${it.weightValue?.let { n -> "${n.pretty()} ${it.weightUnit.orEmpty()}" } ?: "暂无记录"}") }; TextButton(onClick = { onOpenRecords("growth") }) { Text("查看成长") } })
                InfoCard("喂食", { Row { Button(onClick = onQuickFeeding) { Text("快速喂食") }; TextButton(onClick = { onOpenRecords("feeding") }) { Text("查看喂食记录") } } })
                InfoCard("时间线", {
                    if (events.isEmpty()) Text("暂无历史事件")
                    events.forEach { event ->
                        val description = when (event.type) {
                            FishEventType.created -> "建立档案"
                            FishEventType.moved -> {
                                val from = tanks.firstOrNull { it.id == event.tankId }?.name ?: "原鱼缸"
                                val to = tanks.firstOrNull { it.id == event.targetTankId }?.name ?: "目标鱼缸"
                                "转缸：$from → $to"
                            }
                            FishEventType.statusChanged -> event.status?.label() ?: "状态变更"
                            FishEventType.archived -> "归档"
                        }
                        Text("${formatDate(event.occurredAt)} · $description${if (!event.dateKnown) "（日期未知）" else ""}")
                    }
                    TextButton(onClick = { onOpenRecords("timeline") }) { Text("查看完整时间线") }
                })
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (fish.status == FishStatus.active || fish.status == FishStatus.quarantine) {
                        OutlinedButton(onClick = { showMove = true }) { Text("转缸") }
                        OutlinedButton(onClick = { action = FishStatus.quarantine }) { Text("隔离") }
                        if (fish.status == FishStatus.quarantine) OutlinedButton(onClick = { action = FishStatus.active }) { Text("恢复在养") }
                        OutlinedButton(onClick = { action = FishStatus.transferred }) { Text("标记转出") }
                        OutlinedButton(onClick = { action = FishStatus.deceased }) { Text("标记已故") }
                    }
                }
                if (fish.status != FishStatus.archived) OutlinedButton(onClick = { action = null; showArchive = true }, modifier = Modifier.fillMaxWidth()) { Text("归档鱼只") }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }
    }
    if (action == FishStatus.deceased) AlertDialog(onDismissRequest = { action = null }, title = { Text("标记为已故") }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("请确认发生日期；日期不详时可明确记录为未知。") ; DateField("发生日期", date) { it?.let { v -> date = v } }; Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(unknownDeath, { unknownDeath = it }); Text("日期不详") } } }, confirmButton = { TextButton(enabled = !busy, onClick = { busy = true; vm.setStatus(fishId, FishStatus.deceased, if (unknownDeath) null else date, !unknownDeath, { busy = false; action = null }, { busy = false; error = it }) }) { Text(if (busy) "保存中" else "确认") } }, dismissButton = { TextButton(onClick = { action = null }) { Text("取消") } })
    else if (action == FishStatus.transferred) AlertDialog(onDismissRequest = { action = null }, title = { Text("标记已转出") }, text = { DateField("转出日期", date) { it?.let { v -> date = v } } }, confirmButton = { TextButton(enabled = !busy, onClick = { busy = true; vm.setStatus(fishId, FishStatus.transferred, date, true, { busy = false; action = null }, { busy = false; error = it }) }) { Text(if (busy) "保存中" else "确认转出") } }, dismissButton = { TextButton(onClick = { action = null }) { Text("取消") } })
    else if (action == FishStatus.active || action == FishStatus.quarantine) AlertDialog(onDismissRequest = { action = null }, title = { Text("更改鱼只状态") }, text = { Text("${fish?.name}将标记为${action!!.label()}。") }, confirmButton = { TextButton(enabled = !busy, onClick = { busy = true; vm.setStatus(fishId, action!!, System.currentTimeMillis(), onSaved = { busy = false; action = null }, onError = { busy = false; error = it }) }) { Text("确认") } }, dismissButton = { TextButton(onClick = { action = null }) { Text("取消") } })
    if (showArchive) ConfirmDialog("归档鱼只？", "归档后停止该鱼只的未来任务，档案与历史记录仍可查询。", "归档", onConfirm = { busy = true; vm.archive(fishId, { busy = false; showArchive = false; onBack() }, { busy = false; error = it }); showArchive = false }, onDismiss = { showArchive = false })
    if (showMove) AlertDialog(onDismissRequest = { showMove = false }, title = { Text("转缸") }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { var menu by remember { mutableStateOf(false) }; Box { OutlinedButton(onClick = { menu = true }, modifier = Modifier.fillMaxWidth()) { Text("目标鱼缸：${tanks.firstOrNull { it.id == targetTankId }?.name ?: "选择鱼缸"}") }; DropdownMenu(menu, { menu = false }) { tanks.filter { it.status == TankStatus.active && it.id != fish?.tankId }.forEach { t -> DropdownMenuItem(text = { Text(t.name) }, onClick = { targetTankId = t.id; menu = false }) } } }; DateField("转缸日期", date) { it?.let { value -> date = value } } } }, confirmButton = { TextButton(enabled = !busy && targetTankId.isNotBlank(), onClick = { busy = true; vm.move(fishId, targetTankId, date, { busy = false; showMove = false }, { busy = false; error = it }) }) { Text(if (busy) "保存中" else "确认转缸") } }, dismissButton = { TextButton(onClick = { showMove = false }) { Text("取消") } })
}

@Composable
fun FishRecordsScreen(fishId: String, section: String, onBack: () -> Unit, vm: FishViewModel = viewModel(factory = FishViewModel.factory(LocalContext.current.applicationContext as Application))) {
    val fishData by vm.fish.collectAsStateWithLifecycle()
    val events by remember(fishId) { vm.events(fishId) }.collectAsStateWithLifecycle()
    val growth by remember(fishId) { vm.growth(fishId) }.collectAsStateWithLifecycle()
    val fish = fishData?.firstOrNull { it.fish.id == fishId }?.fish
    val feedings by remember(fishId, fish?.tankId) { fish?.let { vm.feedings(fishId, it.tankId) } ?: kotlinx.coroutines.flow.flowOf(emptyList<FeedingEvent>()) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val title = when (section) { "growth" -> "成长记录"; "feeding" -> "喂食记录"; else -> "鱼只时间线" }
    Scaffold(topBar = { FishTopBar("${fish?.name ?: "鱼只"} · $title", onBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            when (section) {
                "growth" -> {
                    GrowthHistoryContent(fishId, growth, vm)
                }
                "feeding" -> {
                    if (feedings.isEmpty()) EmptyState("暂无喂食记录", "指定鱼只和所属鱼缸的喂食事件会显示在这里。")
                    feedings.forEach { record -> InfoCard(formatDateTime(record.fedAt), { Text("${record.foodType ?: "饲料未记录"} · ${record.amountValue?.let { "${it.pretty()} ${record.amountUnit.orEmpty()}" } ?: "投喂量未记录"}"); record.result?.let { Text("摄食情况：$it") }; record.notes?.let { Text(it) } }) }
                }
                else -> {
                    if (events.isEmpty()) EmptyState("暂无历史事件", "鱼只状态和转缸操作会记录在这里。")
                    events.forEach { event -> InfoCard(formatDate(event.occurredAt), { Text(when (event.type) { FishEventType.created -> "建立档案"; FishEventType.moved -> "转缸记录"; FishEventType.statusChanged -> event.status?.label() ?: "状态变更"; FishEventType.archived -> "归档"}); if (!event.dateKnown) Text("发生日期未知") }) }
                }
            }
        }
    }
}

@Composable
private fun GrowthHistoryContent(fishId: String, records: List<GrowthRecord>, vm: FishViewModel) {
    var range by remember(fishId) { mutableStateOf(GrowthRange.Days30) }
    var editing by remember { mutableStateOf<GrowthRecord?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<GrowthRecord?>(null) }
    var selected by remember { mutableStateOf<GrowthPoint?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val latest = records.maxByOrNull { it.measuredAt }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        latest?.let { record -> InfoCard("最近测量", { Text(growthMeasurementSummary(record)); Text(formatDateTime(record.measuredAt), style = MaterialTheme.typography.bodySmall) }) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GrowthRange.entries.forEach { option -> FilterChip(selected = range == option, onClick = { range = option; selected = null }, label = { Text(option.label) }) }
        }
        if (records.isEmpty()) {
            EmptyState("暂无成长记录", "记录体长或体重后，这里会显示真实变化趋势。")
        } else {
            GrowthTrend("体长趨势", growthPoints(records, true, range, System.currentTimeMillis()), selected?.takeIf { it.record.lengthValue != null }, { selected = it })
            GrowthTrend("体重趋势", growthPoints(records, false, range, System.currentTimeMillis()), selected?.takeIf { it.record.weightValue != null }, { selected = it })
            Button(enabled = !busy, onClick = { editing = null; creating = true }, modifier = Modifier.fillMaxWidth()) { Text("记录成长") }
            Text("历史记录 · ${records.size} 条", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            records.forEach { record ->
                InfoCard(formatDateTime(record.measuredAt), {
                    Text(growthMeasurementSummary(record).ifBlank { "无有效测量" })
                    record.method?.let { Text("测量方式：$it") }
                    record.notes?.takeIf(String::isNotBlank)?.let { Text(it) }
                    record.photoId?.let { photoId ->
                        val photo by remember(photoId) { vm.photo(photoId) }.collectAsStateWithLifecycle()
                        ManagedPhotoImage(photo, true, Modifier.fillMaxWidth().height(160.dp))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { editing = record; creating = true }) { Text("查看 / 更正") }
                        TextButton(enabled = !busy, onClick = { deleting = record }) { Text("删除") }
                    }
                })
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
    if (creating) GrowthRecordDialog(
        fishId = fishId,
        record = editing,
        vm = vm,
        busy = busy,
        onBusy = { busy = it },
        onError = { error = it },
        onDismiss = { if (!busy) creating = false },
        onSaved = { busy = false; creating = false; error = null },
    )
    deleting?.let { record ->
        ConfirmDialog("删除成长记录？", "此操作会移除此测量记录，并更新成长趋势与最近测量摘要。", "删除", onConfirm = {
            if (!busy) { busy = true; vm.deleteGrowth(record.id, { busy = false; deleting = null }, { busy = false; error = it }) }
        }, onDismiss = { deleting = null })
    }
}

@Composable
private fun GrowthTrend(title: String, points: List<GrowthPoint>, selected: GrowthPoint?, onSelect: (GrowthPoint) -> Unit) {
    InfoCard(title, {
        when (points.size) {
            0 -> Text("此范围暂无${if (title.startsWith("体长")) "体长" else "体重"}测量数据")
            1 -> Text("仅有一条测量记录，暂不形成趋势")
            else -> Text("${points.size} 个真实测量点 · 单位 ${points.first().unit}", style = MaterialTheme.typography.bodySmall)
        }
        if (points.isNotEmpty()) {
            GrowthChart(points, onSelect, Modifier.fillMaxWidth().height(150.dp))
            selected?.let { point -> Text("${formatDateTime(point.record.measuredAt)} · ${point.value.pretty()} ${point.unit}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
        }
    })
}

@Composable
private fun GrowthChart(points: List<GrowthPoint>, onSelect: (GrowthPoint) -> Unit, modifier: Modifier = Modifier) {
    var size by remember { mutableStateOf(androidx.compose.ui.geometry.Size.Zero) }
    val chartColor = MaterialTheme.colorScheme.primary
    Canvas(modifier.clickable {
        if (points.isNotEmpty() && size.width > 0f) {
            val step = if (points.size == 1) 0f else size.width / (points.size - 1)
            val index = (0 until points.size).minByOrNull { i -> kotlin.math.abs(i * step - size.width / 2f) } ?: 0
            onSelect(points[index])
        }
    }) {
        size = this.size
        val values = points.map { it.value }
        val low = values.minOrNull() ?: return@Canvas
        val high = values.maxOrNull() ?: return@Canvas
        val spread = max(high - low, max(kotlin.math.abs(high) * 0.08, 0.1))
        val bottom = this.size.height - 12.dp.toPx()
        val top = 12.dp.toPx()
        val coordinates = points.mapIndexed { index, point ->
            val x = if (points.size == 1) this.size.width / 2f else index * this.size.width / (points.size - 1)
            val y = bottom - ((point.value - low) / spread * (bottom - top)).toFloat()
            Offset(x, y)
        }
        if (coordinates.size > 1) {
            val path = Path().apply { moveTo(coordinates.first().x, coordinates.first().y); coordinates.drop(1).forEach { lineTo(it.x, it.y) } }
            drawPath(path, chartColor, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
        }
        coordinates.forEach { drawCircle(chartColor, radius = 5.dp.toPx(), center = it) }
    }
}

@Composable
private fun GrowthRecordDialog(
    fishId: String,
    record: GrowthRecord?,
    vm: FishViewModel,
    busy: Boolean,
    onBusy: (Boolean) -> Unit,
    onError: (String) -> Unit,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
) {
    val photoVm: PhotoViewModel = viewModel(key = "growth-photo-${record?.id ?: "new-$fishId"}", factory = PhotoViewModelFactory(vm.photoRepository))
    var initialized by remember(record?.id) { mutableStateOf(false) }
    var measuredAt by remember(record?.id) { mutableLongStateOf(record?.measuredAt ?: System.currentTimeMillis()) }
    var length by remember(record?.id) { mutableStateOf(record?.lengthValue?.toString().orEmpty()) }
    var lengthUnit by remember(record?.id) { mutableStateOf(record?.lengthUnit ?: "cm") }
    var weight by remember(record?.id) { mutableStateOf(record?.weightValue?.toString().orEmpty()) }
    var weightUnit by remember(record?.id) { mutableStateOf(record?.weightUnit ?: "g") }
    var method by remember(record?.id) { mutableStateOf(record?.method.orEmpty()) }
    var notes by remember(record?.id) { mutableStateOf(record?.notes.orEmpty()) }
    var validation by remember(record?.id) { mutableStateOf<String?>(null) }
    val photoState by photoVm.state.collectAsStateWithLifecycle()
    LaunchedEffect(record?.id) { if (!initialized) { photoVm.load(record?.photoId); initialized = true } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (record == null) "记录成长" else "查看 / 更正成长记录") },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DateTimeField("测量时间", measuredAt) { measuredAt = it }
                OutlinedTextField(length, { length = it }, Modifier.fillMaxWidth(), label = { Text("体长（可选）") }, singleLine = true)
                UnitChoice(lengthUnit, listOf("cm", "mm", "in")) { lengthUnit = it }
                OutlinedTextField(weight, { weight = it }, Modifier.fillMaxWidth(), label = { Text("体重（可选）") }, singleLine = true)
                UnitChoice(weightUnit, listOf("g", "kg", "mg")) { weightUnit = it }
                OutlinedTextField(method, { method = it }, Modifier.fillMaxWidth(), label = { Text("测量方式（可选）") }, singleLine = true)
                OutlinedTextField(notes, { notes = it }, Modifier.fillMaxWidth(), label = { Text("备注（可选）") }, minLines = 2)
                PhotoPickerField(photoVm)
                validation?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy && !photoState.isBusy, onClick = {
                val cleanLength = length.trim().toDoubleOrNull()
                val cleanWeight = weight.trim().toDoubleOrNull()
                validation = when {
                    length.isNotBlank() && (cleanLength == null || !cleanLength.isFinite() || cleanLength <= 0) -> "体长必须是大于 0 的有限数字"
                    weight.isNotBlank() && (cleanWeight == null || !cleanWeight.isFinite() || cleanWeight <= 0) -> "体重必须是大于 0 的有限数字"
                    cleanLength == null && cleanWeight == null -> "体长或体重至少填写一项有效数据"
                    measuredAt > System.currentTimeMillis() -> "测量时间不能晚于当前时间"
                    else -> null
                }
                if (validation == null) {
                    onBusy(true)
                    val value = GrowthRecord(
                        id = record?.id ?: UUID.randomUUID().toString(), fishId = fishId, measuredAt = measuredAt,
                        lengthValue = cleanLength, lengthUnit = cleanLength?.let { lengthUnit },
                        weightValue = cleanWeight, weightUnit = cleanWeight?.let { weightUnit },
                        method = method.trim().ifBlank { null }, photoId = photoState.photo?.id,
                        notes = notes.trim().ifBlank { null },
                    )
                    vm.saveGrowth(value, onSaved, { onBusy(false); onError(it); validation = it })
                }
            }) { Text(if (busy) "保存中…" else "保存") }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun UnitChoice(selected: String, options: List<String>, onSelect: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { unit -> FilterChip(selected = selected == unit, onClick = { onSelect(unit) }, label = { Text(unit) }) }
    }
}

@Composable
private fun DateTimeField(label: String, value: Long, onChange: (Long) -> Unit) {
    val context = LocalContext.current
    OutlinedButton(onClick = {
        val calendar = Calendar.getInstance().apply { timeInMillis = value }
        DatePickerDialog(context, { _, year, month, day ->
            val selectedDate = Calendar.getInstance().apply { timeInMillis = value; set(year, month, day) }
            TimePickerDialog(context, { _, hour, minute -> selectedDate.set(Calendar.HOUR_OF_DAY, hour); selectedDate.set(Calendar.MINUTE, minute); selectedDate.set(Calendar.SECOND, 0); selectedDate.set(Calendar.MILLISECOND, 0); onChange(selectedDate.timeInMillis) }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true).show()
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
    }, modifier = Modifier.fillMaxWidth()) { Text("$label：${formatDateTime(value)}") }
}

@Composable
private fun FishTopBar(title: String, onBack: () -> Unit, action: @Composable RowScope.() -> Unit = {}) {
    TopAppBar(title = { Text(title) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回") } }, actions = action)
}

@Composable
private fun DateField(label: String, value: Long?, optional: Boolean = false, onChange: (Long?) -> Unit) {
    val context = LocalContext.current
    OutlinedButton(onClick = { val cal = Calendar.getInstance().apply { value?.let { timeInMillis = it } }; DatePickerDialog(context, { _, y, m, d -> onChange(Calendar.getInstance().apply { set(y,m,d,12,0,0); set(Calendar.MILLISECOND,0) }.timeInMillis) }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show() }, modifier = Modifier.fillMaxWidth()) { Text("$label：${value?.let(::formatDate) ?: if (optional) "未选择" else "选择日期"}") }
    if (optional && value != null) TextButton(onClick = { onChange(null) }) { Text("清除日期") }
}

private fun todayStart(): Long = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY,0); set(Calendar.MINUTE,0); set(Calendar.SECOND,0); set(Calendar.MILLISECOND,0) }.timeInMillis
internal fun durationDays(addedAt: Long, deathDate: Long?, deceased: Boolean, today: Long = System.currentTimeMillis()): Long {
    val end = if (deceased) deathDate ?: today else today
    return ((todayStartOf(end) - todayStartOf(addedAt)) / 86_400_000L).coerceAtLeast(0)
}
private fun todayStartOf(value: Long) = Calendar.getInstance().apply { timeInMillis = value; set(Calendar.HOUR_OF_DAY,0); set(Calendar.MINUTE,0); set(Calendar.SECOND,0); set(Calendar.MILLISECOND,0) }.timeInMillis
private fun formatDate(value: Long) = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(value))
private fun formatDateTime(value: Long) = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(value))
private fun Double.pretty() = if (this % 1.0 == 0.0) toLong().toString() else "%.1f".format(Locale.getDefault(), this)
private fun FishStatus.label() = when (this) { FishStatus.active -> "在养"; FishStatus.quarantine -> "隔离"; FishStatus.transferred -> "已转出"; FishStatus.deceased -> "已故"; FishStatus.archived -> "已归档" }
