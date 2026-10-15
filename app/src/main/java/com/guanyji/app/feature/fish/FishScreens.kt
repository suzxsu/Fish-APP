@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.guanyji.app.feature.fish

import android.app.Application
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.flowOf
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest
import com.guanyji.app.R
import com.guanyji.app.core.designsystem.*
import com.guanyji.app.data.local.*
import com.guanyji.app.feature.overview.DashboardTokens
import com.guanyji.app.feature.overview.FishIcons
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

/**
 * 我的鱼列表：顶部为水族主题海报头部，下方为自适应双列鱼只卡牌墙。
 * 搜索、鱼缸筛选、状态筛选、排序与新增 / 编辑行为均保持原有实现，仅调整视觉与布局。
 */
@Composable
fun FishListScreen(onCreate: () -> Unit, onOpen: (String) -> Unit, onEdit: (String) -> Unit, vm: FishViewModel = viewModel(factory = FishViewModel.factory(LocalContext.current.applicationContext as Application))) {
    val data by vm.fish.collectAsStateWithLifecycle()
    val tanks by vm.tanks.collectAsStateWithLifecycle()
    var search by remember { mutableStateOf("") }
    var tankId by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<FishStatus?>(null) }
    var sort by remember { mutableStateOf(FishSort.added) }
    var tankMenu by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    // 只做一次入场动画的鱼只，避免滚动回收后重复播放。
    val entranceShown = remember { mutableSetOf<String>() }
    val tankNames = tanks.associate { it.id to it.name }
    val rows = data.orEmpty().filter { row ->
        (status == null || row.fish.status == status) && (tankId == null || row.fish.tankId == tankId) &&
            (row.fish.name.contains(search.trim(), true) || row.fish.species.orEmpty().contains(search.trim(), true))
    }.let { rows -> when (sort) {
        FishSort.added -> rows.sortedByDescending { it.fish.createdAt }
        FishSort.tankDate -> rows.sortedByDescending { it.fish.addedAt }
        FishSort.measured -> rows.sortedByDescending { it.lastMeasuredAt ?: Long.MIN_VALUE }
    } }
    val activeCount = data?.count { it.fish.status == FishStatus.active || it.fish.status == FishStatus.quarantine }
    Scaffold(containerColor = DashboardTokens.PageBackground) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            FishListHeader(activeCount = activeCount, onAdd = onCreate)
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 148.dp),
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(11.dp),
                verticalArrangement = Arrangement.spacedBy(11.dp),
            ) {
                item(key = "controls", span = { GridItemSpan(maxLineSpan) }) {
                    Column(Modifier.fillMaxWidth().padding(bottom = 2.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = search,
                            onValueChange = { search = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = FishCardTokens.FieldShape,
                            label = { Text("搜索名称或鱼种") },
                            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                        )
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box {
                                OutlinedButton(shape = FishCardTokens.FieldShape, onClick = { tankMenu = true }) { Text("鱼缸：${tankId?.let(tankNames::get) ?: "全部"}") }
                                DropdownMenu(tankMenu, { tankMenu = false }) {
                                    DropdownMenuItem(text = { Text("全部鱼缸") }, onClick = { tankId = null; tankMenu = false })
                                    tanks.forEach { t -> DropdownMenuItem(text = { Text(t.name) }, onClick = { tankId = t.id; tankMenu = false }) }
                                }
                            }
                            Box {
                                OutlinedButton(shape = FishCardTokens.FieldShape, onClick = { sortMenu = true }) { Text("排序：${sort.label}") }
                                DropdownMenu(sortMenu, { sortMenu = false }) { FishSort.entries.forEach { item -> DropdownMenuItem(text = { Text(item.label) }, onClick = { sort = item; sortMenu = false }) } }
                            }
                        }
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            statuses.forEach { (value, label) -> FilterChip(status == value, { status = value }, label = { Text(label, style = MaterialTheme.typography.labelSmall) }) }
                        }
                    }
                }
                if (data == null) {
                    item(key = "loading", span = { GridItemSpan(maxLineSpan) }) {
                        Box(Modifier.fillMaxWidth().padding(vertical = 56.dp), contentAlignment = Alignment.Center) { LoadingState() }
                    }
                } else if (rows.isEmpty()) {
                    item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                        Column(Modifier.fillMaxWidth().padding(top = 40.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            EmptyState(if (search.isNotBlank() || status != null || tankId != null) "没有匹配的鱼只" else "还没有鱼只档案", "添加鱼只后，照片、成长和喂食记录会在这里汇总。")
                            if (search.isNotBlank()) TextButton(onClick = { search = "" }) { Text("清除搜索") }
                            Button(onClick = onCreate) { Text("添加鱼") }
                        }
                    }
                } else {
                    itemsIndexed(rows, key = { _, row -> row.fish.id }) { index, row ->
                        val photo by remember(row.fish.coverPhotoId) { vm.photo(row.fish.coverPhotoId) }.collectAsStateWithLifecycle()
                        val model = remember(row, tanks) { fishCardModel(row, tanks) }
                        FishTradingCard(
                            model = model,
                            photo = photo,
                            onClick = { onOpen(row.fish.id) },
                            onEdit = { onEdit(row.fish.id) },
                            animateEntrance = index < 8 && !entranceShown.contains(row.fish.id),
                            onEntranceShown = { shown -> entranceShown.add(shown) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }
    }
}

/** 页面顶部水族海报：背景使用设计稿提供的珊瑚礁小丑鱼图，文字只展示真实计数。 */
@Composable
private fun FishListHeader(activeCount: Int?, onAdd: () -> Unit) {
    Box(Modifier.fillMaxWidth().heightIn(min = 152.dp)) {
        SubcomposeAsyncImage(
            model = ImageRequest.Builder(LocalContext.current).data(R.drawable.dashboard_header_reef).crossfade(320).build(),
            contentDescription = "水族主题背景：珊瑚礁中的小丑鱼",
            contentScale = ContentScale.Crop,
            alignment = Alignment.TopCenter,
            modifier = Modifier.matchParentSize(),
            loading = { FishListHeaderFallback() },
            error = { FishListHeaderFallback() },
            success = { SubcomposeAsyncImageContent() },
        )
        Box(
            Modifier.matchParentSize().background(
                Brush.verticalGradient(
                    0.0f to Color(0x80102A44),
                    0.5f to Color(0x4D102A44),
                    0.78f to Color(0xE6F2F7FB),
                    1.0f to DashboardTokens.PageBackground,
                ),
            ),
        )
        Column(
            Modifier.fillMaxWidth().padding(start = 18.dp, end = 12.dp, top = 18.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(FishIcons.Fish, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "我的鱼",
                    color = Color.White,
                    fontSize = 28.sp,
                    lineHeight = 34.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).testTag("fish-list-title"),
                )
                Box(
                    Modifier.size(46.dp).clip(CircleShape).background(Color(0x40FFFFFF)).border(1.dp, Color(0x59FFFFFF), CircleShape).clickable(onClick = onAdd),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = "添加鱼", tint = Color.White, modifier = Modifier.size(22.dp))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "记录每一条鱼的成长",
                    color = Color(0xFFE4F0FA),
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = if (activeCount == null) "正在读取本地记录" else "在养 $activeCount 条",
                    color = Color(0xFFEAF4FB),
                    fontSize = 12.sp,
                    maxLines = 1,
                    modifier = Modifier
                        .clip(FishCardTokens.ChipShape)
                        .background(FishCardTokens.ControlSurface)
                        .border(1.dp, Color(0x59FFFFFF), FishCardTokens.ChipShape)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun FishListHeaderFallback() {
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(FishCardTokens.ReefFallbackTop, FishCardTokens.ReefFallbackBottom))))
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
fun FishDetailScreen(
    fishId: String,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onOpenRecords: (String) -> Unit = {},
    onQuickFeeding: () -> Unit = {},
    onOpenTank: (String) -> Unit = {},
    vm: FishViewModel = viewModel(factory = FishViewModel.factory(LocalContext.current.applicationContext as Application)),
) {
    val summary by remember(fishId) { vm.summary(fishId) }.collectAsStateWithLifecycle()
    val tanks by vm.tanks.collectAsStateWithLifecycle()
    val events by remember(fishId) { vm.events(fishId) }.collectAsStateWithLifecycle()
    val growth by remember(fishId) { vm.growth(fishId) }.collectAsStateWithLifecycle()
    val latestLength by remember(fishId) { vm.latestLength(fishId) }.collectAsStateWithLifecycle()
    val latestWeight by remember(fishId) { vm.latestWeight(fishId) }.collectAsStateWithLifecycle()
    val fish = summary?.fish
    val tank = tanks.firstOrNull { it.id == fish?.tankId }
    val photo by remember(fish?.coverPhotoId) { vm.photo(fish?.coverPhotoId) }.collectAsStateWithLifecycle()
    val tankPhoto by remember(tank?.photoId) { vm.photo(tank?.photoId) }.collectAsStateWithLifecycle()
    // 水质只展示该鱼缸最近一次真实检测结果，没有记录时保持“尚无测量数据”。
    val tankId = tank?.id
    val waterTests by remember(tankId) { if (tankId == null) flowOf(emptyList<WaterTestWithValues>()) else vm.waterTests(tankId) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val latestWaterTest = remember(waterTests) { waterTests.maxByOrNull { it.test.measuredAt } }
    val waterMetrics = remember(latestWaterTest) { fishWaterMetrics(latestWaterTest?.values.orEmpty()) }
    var action by remember { mutableStateOf<FishStatus?>(null) }
    var showArchive by remember { mutableStateOf(false) }
    var unknownDeath by remember { mutableStateOf(false) }
    var showMove by remember { mutableStateOf(false) }
    var targetTankId by remember { mutableStateOf("") }
    var moreMenu by remember { mutableStateOf(false) }
    var addMenu by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var date by remember { mutableLongStateOf(todayStart()) }

    Scaffold(
        containerColor = DashboardTokens.PageBackground,
        bottomBar = {
            if (fish != null) FishDetailActionBar(
                busy = busy,
                menuOpen = addMenu,
                onMenuOpenChange = { addMenu = it },
                onEdit = onEdit,
                onQuickFeeding = { addMenu = false; onQuickFeeding() },
                onGrowthRecord = { addMenu = false; onOpenRecords("growth") },
                onFeedingRecords = { addMenu = false; onOpenRecords("feeding") },
            )
        },
    ) { padding ->
        if (summary == null) Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { LoadingState("正在加载鱼只档案") }
        else Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            FishDetailHero(
                fish = fish!!,
                tankType = tank?.type,
                photo = photo,
                durationDays = durationDays(fish.addedAt, fish.deathDate, fish.status == FishStatus.deceased),
                lengthText = latestLength?.let { "${it.lengthValue?.pretty()} ${it.lengthUnit.orEmpty()}" } ?: "暂无记录",
                chips = fishDetailChips(fish, ::formatDate),
                onBack = onBack,
                onEdit = onEdit,
                moreMenu = moreMenu,
                onMoreMenuChange = { moreMenu = it },
                onMove = { showMove = true },
                onStatus = { action = it },
                onArchive = { showArchive = true },
            )
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                FishDetailWaterStrip(metrics = waterMetrics, measuredAt = latestWaterTest?.test?.measuredAt)

                FishDetailSection(title = "成长记录", actionLabel = "查看全部", onAction = { onOpenRecords("growth") }) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FishDetailMetricCard(
                            title = "最近体长",
                            value = latestLength?.let { "${it.lengthValue?.pretty()} ${it.lengthUnit.orEmpty()}" } ?: "未记录",
                            caption = latestLength?.let { formatDate(it.measuredAt) } ?: "--",
                            modifier = Modifier.weight(1f),
                        )
                        FishDetailMetricCard(
                            title = "最近体重",
                            value = latestWeight?.let { "${it.weightValue?.pretty()} ${it.weightUnit.orEmpty()}" } ?: "未记录",
                            caption = latestWeight?.let { formatDate(it.measuredAt) } ?: "--",
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Text("共 ${growth.size} 条成长记录", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                FishDetailSection(title = "喂食记录", actionLabel = "查看全部", onAction = { onOpenRecords("feeding") }) {
                    FishDetailLinkCard(
                        icon = FishIcons.Fish,
                        title = "今日喂食",
                        subtitle = if (summary!!.todayFeedings == 0) "暂无记录" else "已记录 ${summary!!.todayFeedings} 次",
                        onClick = { onOpenRecords("feeding") },
                    )
                }

                FishDetailSection(title = "相关鱼缸") {
                    if (tank == null) {
                        Text("鱼缸未找到", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        FishDetailTankCard(tank = tank, photo = tankPhoto, onClick = { onOpenTank(tank.id) })
                    }
                }

                FishDetailSection(title = "时间线", actionLabel = "查看全部", onAction = { onOpenRecords("timeline") }) {
                    if (events.isEmpty()) {
                        Text("暂无历史事件", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            events.take(2).forEach { event ->
                                Text(
                                    "${formatDate(event.occurredAt)} · ${fishEventLabel(event, tanks)}${if (!event.dateKnown) "（日期未知）" else ""}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }

                FishDetailSection(title = "档案信息") {
                    FishDetailProfileCard(fish)
                }

                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            }
        }
    }

    if (action == FishStatus.deceased) AlertDialog(onDismissRequest = { action = null }, title = { Text("标记为已故") }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("请确认发生日期；日期不详时可明确记录为未知。") ; DateField("发生日期", date) { it?.let { v -> date = v } }; Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(unknownDeath, { unknownDeath = it }); Text("日期不详") } } }, confirmButton = { TextButton(enabled = !busy, onClick = { busy = true; vm.setStatus(fishId, FishStatus.deceased, if (unknownDeath) null else date, !unknownDeath, { busy = false; action = null }, { busy = false; error = it }) }) { Text(if (busy) "保存中" else "确认") } }, dismissButton = { TextButton(onClick = { action = null }) { Text("取消") } })
    else if (action == FishStatus.transferred) AlertDialog(onDismissRequest = { action = null }, title = { Text("标记已转出") }, text = { DateField("转出日期", date) { it?.let { v -> date = v } } }, confirmButton = { TextButton(enabled = !busy, onClick = { busy = true; vm.setStatus(fishId, FishStatus.transferred, date, true, { busy = false; action = null }, { busy = false; error = it }) }) { Text(if (busy) "保存中" else "确认转出") } }, dismissButton = { TextButton(onClick = { action = null }) { Text("取消") } })
    else if (action == FishStatus.active || action == FishStatus.quarantine) AlertDialog(onDismissRequest = { action = null }, title = { Text("更改鱼只状态") }, text = { Text("${fish?.name}将标记为${action!!.label()}。") }, confirmButton = { TextButton(enabled = !busy, onClick = { busy = true; vm.setStatus(fishId, action!!, System.currentTimeMillis(), onSaved = { busy = false; action = null }, onError = { busy = false; error = it }) }) { Text("确认") } }, dismissButton = { TextButton(onClick = { action = null }) { Text("取消") } })
    if (showArchive) ConfirmDialog("归档鱼只？", "归档后停止该鱼只的未来任务，档案与历史记录仍可查询。", "归档", onConfirm = { busy = true; vm.archive(fishId, { busy = false; showArchive = false; onBack() }, { busy = false; error = it }); showArchive = false }, onDismiss = { showArchive = false })
    if (showMove) AlertDialog(onDismissRequest = { showMove = false }, title = { Text("转缸") }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { var menu by remember { mutableStateOf(false) }; Box { OutlinedButton(onClick = { menu = true }, modifier = Modifier.fillMaxWidth()) { Text("目标鱼缸：${tanks.firstOrNull { it.id == targetTankId }?.name ?: "选择鱼缸"}") }; DropdownMenu(menu, { menu = false }) { tanks.filter { it.status == TankStatus.active && it.id != fish?.tankId }.forEach { t -> DropdownMenuItem(text = { Text(t.name) }, onClick = { targetTankId = t.id; menu = false }) } } }; DateField("转缸日期", date) { it?.let { value -> date = value } } } }, confirmButton = { TextButton(enabled = !busy && targetTankId.isNotBlank(), onClick = { busy = true; vm.move(fishId, targetTankId, date, { busy = false; showMove = false }, { busy = false; error = it }) }) { Text(if (busy) "保存中" else "确认转缸") } }, dismissButton = { TextButton(onClick = { showMove = false }) { Text("取消") } })
}

/* ---------------------------- 详情页：水族头部 ---------------------------- */

@Composable
private fun FishDetailHero(
    fish: Fish,
    tankType: TankType?,
    photo: Photo?,
    durationDays: Long,
    lengthText: String,
    chips: List<FishDetailChip>,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    moreMenu: Boolean,
    onMoreMenuChange: (Boolean) -> Unit,
    onMove: () -> Unit,
    onStatus: (FishStatus) -> Unit,
    onArchive: () -> Unit,
) {
    val palette = remember(fish.status) { fish.status.cardPalette() }
    Box(Modifier.fillMaxWidth().heightIn(min = 320.dp)) {
        if (photo == null) FishDetailPhotoFallback()
        else ManagedPhotoImage(photo, thumbnail = false, modifier = Modifier.matchParentSize())
        // 上暗保证返回 / 更多按钮可读，下暗让身份卡与照片自然过渡。
        Box(
            Modifier.matchParentSize().background(
                Brush.verticalGradient(
                    0.0f to Color(0x8C0B2540),
                    0.34f to Color(0x2E0B2540),
                    0.7f to Color(0x66081D33),
                    1.0f to Color(0xB3081D33),
                ),
            ),
        )
        Row(
            modifier = Modifier.align(Alignment.TopStart).fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FishHeroIconButton(icon = Icons.AutoMirrored.Outlined.ArrowBack, description = "返回", onClick = onBack)
            Spacer(Modifier.weight(1f))
            Box {
                FishHeroIconButton(icon = Icons.Outlined.MoreVert, description = "更多操作", onClick = { onMoreMenuChange(true) })
                DropdownMenu(expanded = moreMenu, onDismissRequest = { onMoreMenuChange(false) }) {
                    val manageable = fish.status == FishStatus.active || fish.status == FishStatus.quarantine
                    if (manageable) {
                        DropdownMenuItem(text = { Text("转缸") }, onClick = { onMoreMenuChange(false); onMove() })
                        if (fish.status == FishStatus.quarantine) {
                            DropdownMenuItem(text = { Text("恢复在养") }, onClick = { onMoreMenuChange(false); onStatus(FishStatus.active) })
                        } else {
                            DropdownMenuItem(text = { Text("隔离观察") }, onClick = { onMoreMenuChange(false); onStatus(FishStatus.quarantine) })
                        }
                        DropdownMenuItem(text = { Text("标记转出") }, onClick = { onMoreMenuChange(false); onStatus(FishStatus.transferred) })
                        DropdownMenuItem(text = { Text("标记已故") }, onClick = { onMoreMenuChange(false); onStatus(FishStatus.deceased) })
                    }
                    if (fish.status != FishStatus.archived) {
                        DropdownMenuItem(text = { Text("归档鱼只") }, onClick = { onMoreMenuChange(false); onArchive() })
                    }
                }
            }
        }
        // 状态标签与身份卡放在同一列流式布局中，字体放大或名称换行时不会互相遮挡。
        Column(
            modifier = Modifier.align(Alignment.BottomStart).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FishCardStatus(fish.status.label(), palette)
            FishDetailIdentityPanel(
                fish = fish,
                tankType = tankType,
                durationDays = durationDays,
                lengthText = lengthText,
                chips = chips,
                onEdit = onEdit,
            )
        }
    }
}

@Composable
private fun FishDetailIdentityPanel(
    fish: Fish,
    tankType: TankType?,
    durationDays: Long,
    lengthText: String,
    chips: List<FishDetailChip>,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = remember(fish.status) { fish.status.cardPalette() }
    val speciesLine = listOfNotNull(fish.species?.trim()?.takeIf { it.isNotEmpty() } ?: "鱼种未记录", tankType?.let(::waterTypeLabel)).joinToString(" · ")
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(FishCardTokens.CardShape)
            .background(Brush.verticalGradient(listOf(Color(0xE6163B5E), Color(0xF00A2440))))
            .border(1.dp, Brush.linearGradient(listOf(palette.accent.copy(alpha = 0.55f), Color(0x33FFFFFF))), FishCardTokens.CardShape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = fish.name,
                color = FishCardTokens.CardInk,
                fontSize = 24.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            FishHeroIconButton(icon = Icons.Outlined.Edit, description = "编辑档案", onClick = onEdit, boxSize = 40.dp, iconSize = 18.dp)
        }
        Text(speciesLine, color = FishCardTokens.CardSecondaryInk, fontSize = 13.sp, lineHeight = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (chips.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                chips.forEach { chip -> FishDetailChipView(chip) }
            }
        }
        Box(Modifier.fillMaxWidth().padding(top = 2.dp).height(1.dp).background(Color(0x26FFFFFF)))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            FishDetailHeroStat(icon = FishIcons.Calendar, label = "入缸", value = "$durationDays 天", modifier = Modifier.weight(1f))
            Box(Modifier.width(1.dp).height(34.dp).background(Color(0x26FFFFFF)))
            FishDetailHeroStat(icon = FishIcons.Ruler, label = "最后体长", value = lengthText, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun FishDetailHeroStat(icon: ImageVector, label: String, value: String, modifier: Modifier = Modifier) {
    Row(modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        Icon(icon, contentDescription = null, tint = FishCardTokens.CardSecondaryInk, modifier = Modifier.size(16.dp))
        Column {
            Text(label, color = FishCardTokens.CardSecondaryInk, fontSize = 11.sp, lineHeight = 14.sp, maxLines = 1)
            Text(value, color = FishCardTokens.CardInk, fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun FishDetailChipView(chip: FishDetailChip) {
    Row(
        modifier = Modifier
            .clip(FishCardTokens.ChipShape)
            .background(Color(0x33FFFFFF))
            .border(1.dp, Color(0x2EFFFFFF), FishCardTokens.ChipShape)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(chip.label, color = FishCardTokens.CardSecondaryInk, fontSize = 11.sp, maxLines = 1)
        Text(chip.value, color = FishCardTokens.CardInk, fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

@Composable
private fun FishHeroIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    boxSize: Dp = 44.dp,
    iconSize: Dp = 20.dp,
) {
    Box(
        modifier = Modifier
            .size(boxSize)
            .clip(CircleShape)
            .background(Color(0x4D0B2540))
            .border(1.dp, Color(0x40FFFFFF), CircleShape)
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = description
                role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(iconSize))
    }
}

@Composable
private fun FishDetailPhotoFallback() {
    Box(
        modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF123C60), Color(0xFF0A2036)))),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(FishIcons.Fish, contentDescription = null, tint = Color(0x66CFE6F7), modifier = Modifier.size(44.dp))
            Text("暂无照片", color = Color(0x99CFE6F7), fontSize = 13.sp)
        }
    }
}

/* ---------------------------- 详情页：内容区块 ---------------------------- */

@Composable
private fun FishDetailSection(
    title: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            if (actionLabel != null && onAction != null) TextButton(onClick = onAction) { Text(actionLabel, style = MaterialTheme.typography.labelLarge) }
        }
        content()
    }
}

/** 水质摘要：只使用该鱼缸最近一次检测的真实数值，没有记录时不显示任何默认值。 */
@Composable
private fun FishDetailWaterStrip(metrics: List<FishWaterMetric>, measuredAt: Long?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.medium)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("最近水质", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text(
                measuredAt?.let { "检测于 ${formatDate(it)}" } ?: "尚无测量数据",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (metrics.isEmpty()) {
            Text("该鱼缸还没有水质检测记录，可在鱼缸页记录后再回到这里查看。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val columns = if (maxWidth < 300.dp) 2 else metrics.size
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    metrics.chunked(columns).forEach { rowItems ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            rowItems.forEach { metric -> FishDetailWaterMetric(metric, Modifier.weight(1f)) }
                            repeat(columns - rowItems.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FishDetailWaterMetric(metric: FishWaterMetric, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(waterMetricIcon(metric.code), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
            Text(metric.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        Text(metric.value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private fun waterMetricIcon(code: String): ImageVector = when (code) {
    "temperature" -> FishIcons.Droplet
    "ph" -> FishIcons.Eye
    "salinity", "specific_gravity" -> FishIcons.WaterLevel
    else -> FishIcons.Tag
}

@Composable
private fun FishDetailMetricCard(title: String, value: String, caption: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.medium)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

@Composable
private fun FishDetailLinkCard(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        Icon(FishIcons.ArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun FishDetailTankCard(tank: Tank, photo: Photo?, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (photo == null) {
            Box(Modifier.size(58.dp, 46.dp).clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                Icon(FishIcons.Aquarium, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            }
        } else {
            ManagedPhotoImage(photo, thumbnail = true, modifier = Modifier.size(58.dp, 46.dp).clip(MaterialTheme.shapes.small))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(tank.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(waterTypeLabel(tank.type), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        Icon(FishIcons.ArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun FishDetailProfileCard(fish: Fish) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.medium)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        FishDetailProfileRow("入缸日期", formatDate(fish.addedAt))
        FishDetailProfileRow("出生日期", fish.birthDate?.let(::formatDate) ?: "未记录")
        if (fish.status == FishStatus.deceased) FishDetailProfileRow("死亡日期", fish.deathDate?.let(::formatDate) ?: "未知")
        FishDetailProfileRow("性别", fish.sex ?: "未记录")
        FishDetailProfileRow("来源", fish.source ?: "未记录")
        FishDetailProfileRow("备注", fish.notes?.takeIf { it.isNotBlank() } ?: "暂无")
    }
}

@Composable
private fun FishDetailProfileRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(64.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

/** 底部操作栏：编辑档案 + 添加记录（喂食 / 成长）。 */
@Composable
private fun FishDetailActionBar(
    busy: Boolean,
    menuOpen: Boolean,
    onMenuOpenChange: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onQuickFeeding: () -> Unit,
    onGrowthRecord: () -> Unit,
    onFeedingRecords: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 8.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedButton(onClick = onEdit, enabled = !busy, modifier = Modifier.weight(1f).height(48.dp)) {
                Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("编辑")
            }
            Box(Modifier.weight(1f)) {
                Button(onClick = { onMenuOpenChange(true) }, enabled = !busy, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                    Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("添加记录")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { onMenuOpenChange(false) }) {
                    DropdownMenuItem(text = { Text("记录喂食") }, onClick = onQuickFeeding)
                    DropdownMenuItem(text = { Text("记录成长（体长 / 体重）") }, onClick = onGrowthRecord)
                    DropdownMenuItem(text = { Text("查看喂食记录") }, onClick = onFeedingRecords)
                }
            }
        }
    }
}

private fun fishEventLabel(event: FishEvent, tanks: List<Tank>): String = when (event.type) {
    FishEventType.created -> "建立档案"
    FishEventType.moved -> {
        val from = tanks.firstOrNull { it.id == event.tankId }?.name ?: "原鱼缸"
        val to = tanks.firstOrNull { it.id == event.targetTankId }?.name ?: "目标鱼缸"
        "转缸：$from → $to"
    }
    FishEventType.statusChanged -> event.status?.label() ?: "状态变更"
    FishEventType.archived -> "归档"
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