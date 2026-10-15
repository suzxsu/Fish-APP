@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.guanyji.app.feature.feeding

import android.app.Application
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.guanyji.app.core.designsystem.EmptyState
import com.guanyji.app.core.designsystem.InfoCard
import com.guanyji.app.core.designsystem.LoadingState
import com.guanyji.app.core.designsystem.rememberLeaveFormHandler
import com.guanyji.app.data.local.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

private enum class FeedingMode { quick, full }
private enum class FeedingScope { tank, selected }

@Composable
fun FeedingScreen(
    tankId: String?, fishId: String?, onBack: () -> Unit,
    vm: FeedingViewModel = viewModel(factory = FeedingViewModel.factory(LocalContext.current.applicationContext as Application)),
) {
    val tanks by vm.tanks.collectAsStateWithLifecycle()
    var chosenTank by remember(tankId) { mutableStateOf(tankId.orEmpty()) }
    LaunchedEffect(tankId, tanks) {
        if (chosenTank.isBlank()) chosenTank = tankId ?: tanks.firstOrNull { it.status == TankStatus.active }?.id.orEmpty()
    }
    val tank = tanks.firstOrNull { it.id == chosenTank }
    val fish by remember(chosenTank) { if (chosenTank.isBlank()) kotlinx.coroutines.flow.flowOf(emptyList()) else vm.fish(chosenTank) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val historyFlow = remember(chosenTank) { if (chosenTank.isBlank()) kotlinx.coroutines.flow.flowOf(emptyList()) else vm.history(chosenTank) }
    val history by historyFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val foods by vm.foods.collectAsStateWithLifecycle()
    var mode by remember { mutableStateOf(FeedingMode.quick) }
    var scope by remember { mutableStateOf(if (fishId == null) FeedingScope.tank else FeedingScope.selected) }
    var selectedFish by remember(fishId) { mutableStateOf(if (fishId == null) emptySet() else setOf(fishId)) }
    var food by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("g") }
    var result by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var fedAt by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var editing by remember { mutableStateOf<FeedingEventWithTargets?>(null) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var foodMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val tankName = { id: String? -> tanks.firstOrNull { it.id == id }?.name ?: "鱼缸" }
    val hasUnsavedChanges = editing?.let { row ->
        val event = row.event
        val originalTargets = row.targets
        val originalScope = originalTargets.firstOrNull()?.targetType ?: FeedingTargetType.tank
        val originalFish = originalTargets.mapNotNull { it.fishId }.toSet()
        chosenTank != event.tankId.orEmpty() || fedAt != event.fedAt || food != event.foodType.orEmpty() || amount != event.amountValue?.toString().orEmpty() ||
            unit != (event.amountUnit ?: "g") || result != event.result.orEmpty() || notes != event.notes.orEmpty() ||
            (scope == FeedingScope.tank) != (originalScope == FeedingTargetType.tank) || selectedFish != originalFish
    } ?: (food.isNotBlank() || amount.isNotBlank() || result.isNotBlank() || notes.isNotBlank() || mode != FeedingMode.quick ||
        (fishId != null && selectedFish != setOf(fishId)) || (fishId == null && tankId != null && chosenTank != tankId))
    val leaveForm = rememberLeaveFormHandler(hasUnsavedChanges, onBack)

    Scaffold(
        topBar = { TopAppBar(title = { Text(if (editing == null) "喂食记录" else "编辑喂食") }, navigationIcon = { IconButton(onClick = leaveForm) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回") } }) },
        bottomBar = { Surface(shadowElevation = 4.dp) { Button(enabled = !saving && tank != null, onClick = {
            if (saving) return@Button
            val cleanFood = food.trim()
            val quantity = amount.trim().toDoubleOrNull()
            if (amount.isNotBlank() && (quantity == null || !quantity.isFinite() || quantity <= 0)) { error = "数量必须是大于 0 的有效数字"; return@Button }
            if (scope == FeedingScope.selected && selectedFish.isEmpty()) { error = "请选择至少一条鱼"; return@Button }
            val eventId = editing?.event?.id ?: UUID.randomUUID().toString()
            val event = FeedingEvent(id = eventId, tankId = chosenTank, fedAt = fedAt, foodType = cleanFood.ifBlank { null }, amountValue = quantity, amountUnit = quantity?.let { unit }, result = result.trim().ifBlank { null }, notes = notes.trim().ifBlank { null })
            val targets = if (scope == FeedingScope.tank) listOf(FeedingTarget(feedingEventId = eventId, targetType = FeedingTargetType.tank, tankId = chosenTank, fishId = null, result = result.trim().ifBlank { null })) else selectedFish.map { id -> FeedingTarget(feedingEventId = eventId, targetType = FeedingTargetType.fish, tankId = null, fishId = id, result = result.trim().ifBlank { null }) }
            saving = true; error = null
            vm.save(event, targets, { saving = false; onBack() }, { saving = false; error = it })
        }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) { Text(if (saving) "保存中…" else if (editing == null && mode == FeedingMode.quick) "确认喂食" else "保存记录") } } },
    ) { padding ->
        if (tanks.isEmpty()) Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { EmptyState("还没有可用鱼缸", "先创建鱼缸，再记录喂食。") }
        else Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(bottom = 88.dp).padding(horizontal = 16.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(mode == FeedingMode.quick, { mode = FeedingMode.quick }, label = { Text("快速记录") })
                FilterChip(mode == FeedingMode.full, { mode = FeedingMode.full }, label = { Text("完整表单") })
            }
            if (tankId == null) {
                var tankMenu by remember { mutableStateOf(false) }
                Box { OutlinedButton(onClick = { tankMenu = true }, modifier = Modifier.fillMaxWidth()) { Text("鱼缸：${tank?.name ?: "选择鱼缸"}") }; DropdownMenu(tankMenu, { tankMenu = false }) { tanks.filter { it.status == TankStatus.active }.forEach { selected -> DropdownMenuItem(text = { Text(selected.name) }, onClick = { chosenTank = selected.id; if (fishId == null) selectedFish = emptySet(); tankMenu = false }) } } }
            } else Text("鱼缸：${tank?.name ?: "正在加载…"}", style = MaterialTheme.typography.titleSmall)
            if (mode == FeedingMode.full || editing != null) {
                OutlinedButton(onClick = {
                    val c = Calendar.getInstance().apply { timeInMillis = fedAt }
                    DatePickerDialog(context, { _, y, m, d ->
                        val date = Calendar.getInstance().apply { timeInMillis = fedAt; set(y, m, d) }
                        TimePickerDialog(context, { _, h, min -> date.set(Calendar.HOUR_OF_DAY, h); date.set(Calendar.MINUTE, min); date.set(Calendar.SECOND, 0); date.set(Calendar.MILLISECOND, 0); fedAt = date.timeInMillis }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), true).show()
                    }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
                }, modifier = Modifier.fillMaxWidth()) { Text("喂食时间：${dateTime(fedAt)}") }
                Text("喂食对象", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(scope == FeedingScope.tank, { scope = FeedingScope.tank; selectedFish = emptySet() }, label = { Text("全缸") })
                    FilterChip(scope == FeedingScope.selected && selectedFish.size <= 1, { scope = FeedingScope.selected }, label = { Text("指定鱼") })
                    FilterChip(scope == FeedingScope.selected && selectedFish.size > 1, { scope = FeedingScope.selected }, label = { Text("多条鱼") })
                }
                if (scope == FeedingScope.selected) {
                    LaunchedEffect(fishId, fish) { if (fishId != null && fish.any { it.id == fishId } && selectedFish.isEmpty()) selectedFish = setOf(fishId) }
                    if (fish.isEmpty()) Text("该鱼缸还没有鱼只档案")
                    fish.forEach { record -> Row(Modifier.fillMaxWidth().clickable { selectedFish = if (record.id in selectedFish) selectedFish - record.id else selectedFish + record.id }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) { Checkbox(record.id in selectedFish, { checked -> selectedFish = if (checked) selectedFish + record.id else selectedFish - record.id }); Text(record.name) } }
                }
            } else {
                Text(if (fishId != null) "对象：${fish.firstOrNull { it.id == fishId }?.name ?: "指定鱼"}" else "对象：${tank?.name ?: "全缸"}", style = MaterialTheme.typography.titleSmall)
                if (fishId == null && fish.isNotEmpty()) Text("本次按全缸喂食记录")
            }
            if (foods.isNotEmpty()) Box { OutlinedButton(onClick = { foodMenu = true }, modifier = Modifier.fillMaxWidth()) { Text("常用饲料：${food.ifBlank { "选择" }}") }; DropdownMenu(foodMenu, { foodMenu = false }) { foods.forEach { option -> DropdownMenuItem(text = { Text(option.name) }, onClick = { food = option.name; foodMenu = false }) } } }
            OutlinedTextField(food, { food = it }, Modifier.fillMaxWidth(), label = { Text("饲料类型（可选）") }, singleLine = true)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(amount, { amount = it }, Modifier.weight(1f), label = { Text("数量（可选）") }, singleLine = true)
                listOf("g", "粒", "片", "ml").forEach { option -> FilterChip(unit == option, { unit = option }, label = { Text(option) }) }
            }
            if (mode == FeedingMode.full || editing != null) {
                var resultMenu by remember { mutableStateOf(false) }
                Box { OutlinedButton(onClick = { resultMenu = true }, modifier = Modifier.fillMaxWidth()) { Text("摄食情况：${result.ifBlank { "未记录" }}") }; DropdownMenu(resultMenu, { resultMenu = false }) { listOf("正常", "部分摄食", "未摄食").forEach { item -> DropdownMenuItem(text = { Text(item) }, onClick = { result = item; resultMenu = false }) } } }
                OutlinedTextField(notes, { notes = it }, Modifier.fillMaxWidth(), label = { Text("备注") }, minLines = 2)
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (editing == null && mode == FeedingMode.quick) Text("记录时间：${dateTime(fedAt)}")
            Text("喂食历史 · ${history.size} 次", style = MaterialTheme.typography.titleMedium)
            if (history.isEmpty()) EmptyState("暂无喂食记录", "保存后，这里会按真实事件展示喂食历史。")
            history.forEach { row ->
                InfoCard(dateTime(row.event.fedAt), {
                    Text("${row.event.foodType ?: "饲料未记录"} · ${row.event.amountValue?.let { "${it.pretty()} ${row.event.amountUnit.orEmpty()}" } ?: "数量未记录"}")
                Text("对象：${if (row.targets.any { it.targetType == FeedingTargetType.tank }) tankName(row.event.tankId) else row.targets.mapNotNull { target -> fish.firstOrNull { it.id == target.fishId }?.name }.joinToString().ifBlank { "指定鱼只" }}")
                    row.event.result?.let { Text("摄食情况：$it") }; row.event.notes?.let { Text(it) }
                    TextButton(enabled = !saving, onClick = {
                        editing = row; mode = FeedingMode.full; chosenTank = row.event.tankId.orEmpty(); fedAt = row.event.fedAt; food = row.event.foodType.orEmpty(); amount = row.event.amountValue?.toString().orEmpty(); unit = row.event.amountUnit ?: "g"; result = row.event.result.orEmpty(); notes = row.event.notes.orEmpty()
                        val tankTarget = row.targets.any { it.targetType == FeedingTargetType.tank }; scope = if (tankTarget) FeedingScope.tank else FeedingScope.selected; selectedFish = row.targets.mapNotNull { it.fishId }.toSet()
                    }) { Icon(Icons.Outlined.Edit, contentDescription = "编辑记录"); Text("编辑") }
                })
            }
        }
    }
}

private fun dateTime(value: Long) = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(value))
private fun Double.pretty() = if (this % 1.0 == 0.0) toLong().toString() else "%.1f".format(Locale.getDefault(), this)
