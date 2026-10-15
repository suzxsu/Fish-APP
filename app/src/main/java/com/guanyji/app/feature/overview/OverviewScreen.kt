package com.guanyji.app.feature.overview

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest
import com.guanyji.app.R
import com.guanyji.app.data.local.*
import com.guanyji.app.feature.photo.ManagedPhotoImage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val ScreenPadding = 16.dp
private val HeaderHeight = 176.dp

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
    onNavigate: (String) -> Unit = {},
    onRecordFeeding: (String?) -> Unit = { onFeeding() },
    onRecordWater: (String) -> Unit = { onWater(it) },
    onRecordGrowth: (String) -> Unit = {},
    onOpenRecords: () -> Unit = { onCare(null) },
    onOpenTask: (CareTask) -> Unit = {},
    vm: OverviewViewModel = viewModel(factory = OverviewViewModel.factory(LocalContext.current.applicationContext as Application)),
) {
    val overview by vm.state.collectAsStateWithLifecycle()
    val tanks = overview.tanks
    val tasks = overview.tasks
    val fishRows = overview.fish
    val fish = fishRows.filter { it.fish.status in setOf(FishStatus.active, FishStatus.quarantine) }
        .sortedByDescending { it.lastMeasuredAt ?: it.fish.addedAt }
    val fishByTank = remember(fishRows) {
        fishRows.filter { it.fish.status in setOf(FishStatus.active, FishStatus.quarantine) }.groupingBy { it.fish.tankId }.eachCount()
    }
    val completedTaskIds = remember(overview.careRecords) { overview.careRecords.mapNotNullTo(mutableSetOf()) { it.taskId } }
    val openTasks = tasks.filter { it.status in setOf(CareTaskStatus.pending, CareTaskStatus.overdue) }
    val recentEntries = remember(overview, fishRows, tanks) { buildRecentEntries(overview, fishRows, tanks) }

    var completing by remember { mutableStateOf<CareTask?>(null) }
    var saving by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DashboardTokens.PageBackground)
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(DashboardTokens.SectionGap),
    ) {
        DashboardHeader(badgeCount = openTasks.size, onNotifications = { onCare(null) })

        Column(
            modifier = Modifier.padding(horizontal = ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(DashboardTokens.SectionGap),
        ) {
            DashboardQuickActions(
                modifier = Modifier.offset(y = (-34).dp),
                actions = listOf(
                    DashboardAction("鱼缸管理", "${tanks.size} 个鱼缸", FishIcons.Aquarium, DashboardTokens.OceanBlue, DashboardTokens.OceanTint) { onNavigate("tanks") },
                    DashboardAction("鱼只档案", "${fish.size} 条鱼", FishIcons.Fish, DashboardTokens.AquaGreen, DashboardTokens.AquaTint) { onNavigate("fish") },
                    DashboardAction("喂食记录", "今日 ${overview.todayFeedings} 次", FishIcons.Calendar, DashboardTokens.SunsetOrange, DashboardTokens.SunsetTint, singleLine = true) { onRecordFeeding(null) },
                    DashboardAction("水质管理", waterRecordLabel(overview), FishIcons.Droplet, DashboardTokens.CoralPurple, DashboardTokens.CoralTint) {
                        val target = tanks.firstOrNull()?.tank?.id
                        if (target == null) onMessage("请先添加鱼缸，再记录对应鱼缸的水质。") else onRecordWater(target)
                    },
                ),
            )

            DashboardSection(
                title = "我的鱼缸",
                icon = FishIcons.Aquarium,
                actionLabel = if (tanks.isEmpty()) null else "查看全部",
                onAction = { onNavigate("tanks") },
            ) {
                if (tanks.isEmpty()) {
                    DashboardEmptyState("还没有鱼缸", "创建鱼缸后，这里会显示它的照片、水质与鱼只概况。", "添加鱼缸", onAddTank)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        tanks.chunked(2).forEach { rowTanks ->
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                                rowTanks.forEach { row ->
                                    val photoFlow = remember(row.tank.photoId) { vm.photo(row.tank.photoId) }
                                    val photo by (photoFlow?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(null) })
                                    val tankTests by remember(row.tank.id) { vm.waterTests(row.tank.id) }.collectAsStateWithLifecycle()
                                    AquariumCard(
                                        data = AquariumCardData(
                                            tank = row.tank,
                                            photo = photo,
                                            metrics = aquariumMetrics(tankTests.firstOrNull()),
                                            fishCount = fishByTank[row.tank.id] ?: 0,
                                        ),
                                        onClick = { onTank(row.tank.id) },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                if (rowTanks.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            DashboardSection(
                title = "今日任务",
                icon = FishIcons.Calendar,
                actionLabel = if (tasks.isEmpty()) null else "查看全部",
                onAction = { onCare(null) },
            ) {
                if (tasks.isEmpty()) {
                    DashboardEmptyState("今天暂无任务", "养护计划里的任务会自动出现在这里；未安排的任务不会被计为已完成。", "查看养护", { onCare(null) })
                } else {
                    Column {
                        tasks.forEachIndexed { index, task ->
                            if (index > 0) TaskDivider()
                            TaskRow(
                                task = task,
                                targetName = taskTargetName(task, tanks, fishRows),
                                completed = task.status == CareTaskStatus.completed || task.id in completedTaskIds,
                                onClick = { onOpenTask(task) },
                            )
                        }
                    }
                }
            }

            DashboardSection(
                title = "最近记录",
                icon = Icons.Outlined.Check,
                actionLabel = if (recentEntries.isEmpty()) null else "查看全部",
                onAction = onOpenRecords,
            ) {
                if (recentEntries.isEmpty()) {
                    DashboardEmptyState("还没有记录", "喂食、水质检测和体长测量保存后，最近的三条会显示在这里。", "添加鱼只", onAddFish)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        recentEntries.take(3).chunked(2).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                row.forEach { entry -> RecordChipCard(entry, Modifier.weight(1f), vm) }
                                if (row.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            QuickAddSection(
                onAddTank = onAddTank,
                onAddFish = onAddFish,
                onFeeding = { onRecordFeeding(null) },
                onWater = {
                    val target = tanks.firstOrNull()?.tank?.id
                    if (target == null) onMessage("请先添加鱼缸，再记录对应鱼缸的水质。") else onRecordWater(target)
                },
            )
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

/* ------------------------------- 顶部主题 Header ------------------------------- */

@Composable
private fun DashboardHeader(badgeCount: Int, onNotifications: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().height(HeaderHeight), contentAlignment = Alignment.TopStart) {
        SubcomposeAsyncImage(
            model = ImageRequest.Builder(LocalContext.current).data(R.drawable.dashboard_header_reef).crossfade(320).build(),
            contentDescription = "水族主题背景：珊瑚礁中的小丑鱼",
            contentScale = ContentScale.Crop,
            alignment = Alignment.TopCenter,
            modifier = Modifier.matchParentSize(),
            loading = { ReefFallback() },
            error = { ReefFallback() },
            success = { SubcomposeAsyncImageContent() },
        )
        Box(
            modifier = Modifier.matchParentSize().background(
                Brush.verticalGradient(
                    0.0f to Color(0x33102A44),
                    0.42f to Color(0x1A0E2740),
                    0.76f to Color(0xE6F2F7FB),
                    1.0f to DashboardTokens.PageBackground,
                ),
            ),
        )
        Column(
            modifier = Modifier.fillMaxWidth().padding(start = ScreenPadding + 2.dp, end = 12.dp, top = 26.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = "观鱼记",
                color = DashboardTokens.OnHero,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                modifier = Modifier.testTag("dashboard-title"),
            )
            Text(
                text = "记录每一条鱼的成长",
                color = Color(0xFFBBD6EC),
                fontSize = 13.sp,
                maxLines = 1,
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 24.dp, end = ScreenPadding)
                .size(40.dp)
                .clip(CircleShape)
                .background(Color(0x4DFFFFFF))
                .border(1.dp, Color(0x59FFFFFF), CircleShape)
                .clickable(onClickLabel = "查看养护提醒", onClick = onNotifications)
                .testTag("dashboard-notifications"),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Notifications, contentDescription = "养护提醒", tint = Color.White, modifier = Modifier.size(22.dp))
            if (badgeCount > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 6.dp, end = 7.dp)
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(DashboardTokens.AlertRed)
                        .border(1.5.dp, Color.White, CircleShape)
                        .testTag("dashboard-notification-badge"),
                )
            }
        }
    }
}

@Composable
private fun ReefFallback() {
    Box(
        modifier = Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(DashboardTokens.DeepBlue, DashboardTokens.OceanBlue, Color(0xFF6FB6E8))),
        ),
    )
}

/* ------------------------------- 快捷功能区 ------------------------------- */

private data class DashboardAction(
    val label: String,
    val caption: String,
    val icon: ImageVector,
    val accent: Color,
    val tint: Color,
    val singleLine: Boolean = false,
    val onClick: () -> Unit,
)

@Composable
private fun DashboardQuickActions(actions: List<DashboardAction>, modifier: Modifier = Modifier) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth().dashboardCardBorder(3.dp),
        shape = DashboardTokens.CardShape,
        colors = CardDefaults.elevatedCardColors(containerColor = DashboardTokens.SurfaceCard),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 3.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.Top,
        ) {
            actions.forEach { action -> QuickActionColumn(action, Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun QuickActionColumn(action: DashboardAction, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(DashboardTokens.InnerShape)
            .clickable(onClickLabel = action.label, onClick = action.onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(modifier = Modifier.size(46.dp).clip(RoundedCornerShape(15.dp)).background(action.tint), contentAlignment = Alignment.Center) {
            Icon(action.icon, contentDescription = null, tint = action.accent, modifier = Modifier.size(24.dp))
        }
        Text(
            text = action.label,
            fontSize = 13.sp,
            lineHeight = 17.sp,
            fontWeight = FontWeight.Medium,
            color = DashboardTokens.TitleInk,
            maxLines = if (action.singleLine) 1 else 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Text(
            text = action.caption,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            color = DashboardTokens.TertiaryInk,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * 统一的卡片描边：Material3 的 ElevatedCard 在当前版本没有 border 参数，
 * 这里在圆角形状上叠一层细描边，保持一致的轻微平面感。
 */
private fun Modifier.dashboardCardBorder(elevation: androidx.compose.ui.unit.Dp): Modifier = this
    .clip(DashboardTokens.CardShape)
    .background(DashboardTokens.CardBorder)
    .padding(1.dp)
    .shadow(elevation, DashboardTokens.CardShape)
    .background(DashboardTokens.SurfaceCard, DashboardTokens.CardShape)

/* ------------------------------- 分区标题 ------------------------------- */

@Composable
private fun DashboardSection(
    title: String,
    icon: ImageVector,
    actionLabel: String?,
    onAction: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(icon, contentDescription = null, tint = DashboardTokens.DeepBlue, modifier = Modifier.size(18.dp))
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = DashboardTokens.TitleInk, modifier = Modifier.weight(1f), maxLines = 1)
            if (actionLabel != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(DashboardTokens.BadgeShape)
                        .clickable(onClickLabel = actionLabel, onClick = onAction)
                        .padding(start = 6.dp, end = 2.dp, top = 4.dp, bottom = 4.dp),
                ) {
                    Text(actionLabel, fontSize = 12.sp, color = DashboardTokens.SecondaryInk, maxLines = 1)
                    Icon(FishIcons.ArrowRight, contentDescription = null, tint = DashboardTokens.TertiaryInk, modifier = Modifier.size(16.dp))
                }
            }
        }
        content()
    }
}

/* ------------------------------- 我的鱼缸 ------------------------------- */

internal data class AquariumMetric(val label: String, val icon: ImageVector)

internal data class AquariumCardData(
    val tank: Tank,
    val photo: Photo?,
    val metrics: List<AquariumMetric>,
    val fishCount: Int,
)

@Composable
private fun AquariumCard(data: AquariumCardData, onClick: () -> Unit, modifier: Modifier = Modifier) {
    ElevatedCard(
        modifier = modifier.dashboardCardBorder(2.dp).clickable(onClickLabel = data.tank.name, onClick = onClick),
        shape = DashboardTokens.CardShape,
        colors = CardDefaults.elevatedCardColors(containerColor = DashboardTokens.SurfaceCard),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
    ) {
        Column {
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(1.32f)) {
                if (data.photo != null) {
                    ManagedPhotoImage(data.photo, true, Modifier.matchParentSize())
                } else {
                    AquariumPhotoPlaceholder(Modifier.matchParentSize())
                }
                Surface(
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
                    shape = DashboardTokens.BadgeShape,
                    color = (if (data.tank.type == TankType.freshwater) DashboardTokens.OceanBlue else DashboardTokens.CoralPurple).copy(alpha = 0.9f),
                ) {
                    Text(
                        text = if (data.tank.type == TankType.freshwater) "淡水缸" else "海水缸",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = data.tank.name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = DashboardTokens.TitleInk,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                AquariumMetricLayout(data.metrics, data.fishCount)
            }
        }
    }
}

@Composable
private fun AquariumPhotoPlaceholder(modifier: Modifier) {
    Box(
        modifier = modifier.background(Brush.verticalGradient(listOf(DashboardTokens.OceanTint, DashboardTokens.AquaTint))),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(FishIcons.Aquarium, contentDescription = null, tint = DashboardTokens.OceanBlue, modifier = Modifier.size(26.dp))
            Text("尚未添加鱼缸照片", fontSize = 11.sp, color = DashboardTokens.SecondaryInk, maxLines = 1)
        }
    }
}

@Composable
private fun AquariumMetricLayout(metrics: List<AquariumMetric>, fishCount: Int) {
    val items = metrics.take(3) + AquariumMetric("$fishCount 条鱼", FishIcons.Eye)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.chunked(2).forEach { rowItems ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                rowItems.forEach { item -> AquariumMetricItem(item, Modifier.weight(1f)) }
                if (rowItems.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun AquariumMetricItem(metric: AquariumMetric, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Icon(metric.icon, contentDescription = null, tint = DashboardTokens.TertiaryInk, modifier = Modifier.size(13.dp))
        Text(metric.label, fontSize = 11.sp, color = DashboardTokens.SecondaryInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/* ------------------------------- 今日任务 ------------------------------- */

private enum class TaskVisual(val label: String, val tint: Color, val accent: Color) {
    done("已完成", DashboardTokens.AquaTint, DashboardTokens.AquaGreen),
    todo("待完成", DashboardTokens.OceanTint, DashboardTokens.DeepBlue),
    overdue("已逾期", DashboardTokens.SunsetTint, DashboardTokens.SunsetOrange),
}

private data class TaskTypeLook(val label: String, val icon: ImageVector, val tint: Color, val accent: Color)

@Composable
private fun TaskRow(task: CareTask, targetName: String, completed: Boolean, onClick: () -> Unit) {
    val look = taskVisualLook(task)
    val state = when {
        completed -> TaskVisual.done
        task.status == CareTaskStatus.overdue -> TaskVisual.overdue
        task.status == CareTaskStatus.pending -> if (task.plannedAt < System.currentTimeMillis()) TaskVisual.overdue else TaskVisual.todo
        else -> TaskVisual.todo
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = "打开任务" + task.displayTitleForOverview(), onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(modifier = Modifier.size(38.dp).clip(CircleShape).background(look.tint), contentAlignment = Alignment.Center) {
            Icon(look.icon, contentDescription = null, tint = look.accent, modifier = Modifier.size(19.dp))
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = taskOverviewTitle(task, targetName),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = DashboardTokens.TitleInk,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(time(task.plannedAt), fontSize = 12.sp, color = DashboardTokens.TertiaryInk, maxLines = 1)
        }
        TaskStateBadge(state)
    }
}

@Composable
private fun TaskStateBadge(state: TaskVisual) {
    Row(
        modifier = Modifier.clip(DashboardTokens.BadgeShape).background(state.tint).padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = if (state == TaskVisual.done) Icons.Outlined.Check else Icons.Outlined.Notifications,
            contentDescription = null,
            tint = state.accent,
            modifier = Modifier.size(13.dp),
        )
        Text(state.label, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = state.accent, maxLines = 1)
    }
}

@Composable
private fun TaskDivider() {
    Box(modifier = Modifier.fillMaxWidth().padding(start = 64.dp).height(1.dp).background(DashboardTokens.Track))
}

private fun taskVisualLook(task: CareTask): TaskTypeLook = when (task.type) {
    CareTaskType.feeding -> TaskTypeLook("喂食", FishIcons.Calendar, DashboardTokens.SunsetTint, DashboardTokens.SunsetOrange)
    CareTaskType.water_change -> TaskTypeLook("换水", FishIcons.Droplet, DashboardTokens.OceanTint, DashboardTokens.OceanBlue)
    CareTaskType.water_test -> TaskTypeLook("水质检测", FishIcons.Droplet, DashboardTokens.CoralTint, DashboardTokens.CoralPurple)
    CareTaskType.salinity_test -> TaskTypeLook("盐度检测", FishIcons.WaterLevel, DashboardTokens.CoralTint, DashboardTokens.CoralPurple)
    CareTaskType.maintenance -> TaskTypeLook("设备维护", FishIcons.Aquarium, DashboardTokens.AquaTint, DashboardTokens.AquaGreen)
    CareTaskType.custom -> TaskTypeLook(task.customTitle ?: "自定义任务", FishIcons.Tag, DashboardTokens.OceanTint, DashboardTokens.DeepBlue)
}

/* ------------------------------- 最近记录 ------------------------------- */

internal enum class RecordKind { feeding, water, growth }

internal data class DashboardRecord(
    val kind: RecordKind,
    val title: String,
    val primary: String,
    val caption: String,
    val at: Long,
    val photoId: String?,
    val photoThumbnail: Boolean,
)

private fun recordIcon(kind: RecordKind): ImageVector = when (kind) {
    RecordKind.feeding -> FishIcons.Calendar
    RecordKind.water -> FishIcons.Droplet
    RecordKind.growth -> FishIcons.Fish
}

private fun recordAccent(kind: RecordKind): Color = when (kind) {
    RecordKind.feeding -> DashboardTokens.SunsetOrange
    RecordKind.water -> DashboardTokens.OceanBlue
    RecordKind.growth -> DashboardTokens.AquaGreen
}

@Composable
private fun RecordChipCard(entry: DashboardRecord, modifier: Modifier = Modifier, vm: OverviewViewModel? = null) {
    val accent = recordAccent(entry.kind)
    ElevatedCard(
        modifier = modifier.dashboardCardBorder(1.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = DashboardTokens.SurfaceCard),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            verticalAlignment = Alignment.Top,
        ) {
            RecordThumbnail(entry, accent, vm)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(entry.title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = DashboardTokens.TitleInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(entry.primary, fontSize = 12.sp, color = DashboardTokens.SecondaryInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(entry.caption, fontSize = 11.sp, color = DashboardTokens.TertiaryInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun RecordThumbnail(entry: DashboardRecord, accent: Color, vm: OverviewViewModel?) {
    val photoFlow = remember(entry.photoId) { entry.photoId?.let { vm?.photo(it) } }
    val photo by (photoFlow?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(null) })
    Box(
        modifier = Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)).background(accent.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
    ) {
        if (photo != null) {
            ManagedPhotoImage(photo, entry.photoThumbnail, Modifier.matchParentSize())
        } else {
            Icon(recordIcon(entry.kind), contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
        }
    }
}

/* ------------------------------- 通用片段 ------------------------------- */

@Composable
private fun DashboardEmptyState(title: String, guidance: String, actionLabel: String, onAction: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(DashboardTokens.CardShape)
            .background(DashboardTokens.AquariumPanel)
            .border(1.dp, DashboardTokens.CardBorder, DashboardTokens.CardShape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(modifier = Modifier.size(42.dp).clip(CircleShape).background(DashboardTokens.OceanTint), contentAlignment = Alignment.Center) {
            Icon(FishIcons.Fish, contentDescription = null, tint = DashboardTokens.OceanBlue, modifier = Modifier.size(22.dp))
        }
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = DashboardTokens.TitleInk, textAlign = TextAlign.Center)
        Text(guidance, fontSize = 12.sp, lineHeight = 17.sp, color = DashboardTokens.SecondaryInk, textAlign = TextAlign.Center)
        TextButton(onClick = onAction) { Text(actionLabel) }
    }
}

@Composable
private fun QuickAddSection(onAddTank: () -> Unit, onAddFish: () -> Unit, onFeeding: () -> Unit, onWater: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("快捷记录", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = DashboardTokens.TitleInk)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            QuickAddButton("添加鱼缸", Icons.Outlined.Add, DashboardTokens.OceanBlue, DashboardTokens.OceanTint, onAddTank, Modifier.weight(1f))
            QuickAddButton("添加鱼只", Icons.Outlined.Add, DashboardTokens.AquaGreen, DashboardTokens.AquaTint, onAddFish, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            QuickAddButton("记录喂食", FishIcons.Calendar, DashboardTokens.SunsetOrange, DashboardTokens.SunsetTint, onFeeding, Modifier.weight(1f))
            QuickAddButton("记录水质", FishIcons.Droplet, DashboardTokens.CoralPurple, DashboardTokens.CoralTint, onWater, Modifier.weight(1f))
        }
    }
}

@Composable
private fun QuickAddButton(label: String, icon: ImageVector, accent: Color, tint: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .heightIn(min = 46.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(tint)
            .clickable(onClickLabel = label, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/* ------------------------------- 数据与文案 ------------------------------- */

private fun buildRecentEntries(overview: OverviewSnapshot, fishRows: List<FishSummary>, tanks: List<TankSummary>): List<DashboardRecord> {
    val tankNames = tanks.associate { it.tank.id to it.tank.name }
    val fishById = fishRows.associateBy { it.fish.id }
    val entries = ArrayList<DashboardRecord>(3)

    overview.records.feedings.maxByOrNull { it.event.fedAt }?.let { row ->
        val tankName = row.event.tankId?.let { tankNames[it] }
            ?: row.targets.firstNotNullOfOrNull { target ->
                target.tankId?.let { tankNames[it] } ?: target.fishId?.let { fishId -> fishById[fishId]?.fish?.tankId?.let { tankNames[it] } }
            }
        val photoId = row.event.tankId?.let { id -> tanks.firstOrNull { it.tank.id == id }?.tank?.photoId }
        entries += DashboardRecord(
            kind = RecordKind.feeding,
            title = "喂食",
            primary = tankName ?: "未指定鱼缸",
            caption = relativeTimeLabel(row.event.fedAt),
            at = row.event.fedAt,
            photoId = photoId,
            photoThumbnail = true,
        )
    }

    overview.records.waterTests.maxByOrNull { it.test.measuredAt }?.let { record ->
        val measured = record.values.filter { it.value != null }
        entries += DashboardRecord(
            kind = RecordKind.water,
            title = "水质",
            primary = keywordSummary(measured) ?: (tankNames[record.test.tankId] ?: "未指定鱼缸"),
            caption = relativeTimeLabel(record.test.measuredAt),
            at = record.test.measuredAt,
            photoId = null,
            photoThumbnail = false,
        )
    }

    val latestGrowth = overview.records.growthRecords.maxByOrNull { it.measuredAt }
    if (latestGrowth != null) {
        val fish = fishById[latestGrowth.fishId]
        val measurement = listOfNotNull(
            latestGrowth.lengthValue?.let { it.prettyNumber() + latestGrowth.lengthUnit.orEmpty() },
            latestGrowth.weightValue?.let { it.prettyNumber() + latestGrowth.weightUnit.orEmpty() },
        ).joinToString(" · ")
        entries += DashboardRecord(
            kind = RecordKind.growth,
            title = "成长记录",
            primary = (fish?.fish?.name ?: "未命名鱼只") + if (measurement.isEmpty()) "" else " · " + measurement,
            caption = relativeTimeLabel(latestGrowth.measuredAt),
            at = latestGrowth.measuredAt,
            photoId = fish?.fish?.coverPhotoId,
            photoThumbnail = true,
        )
    } else {
        val fallback = fishRows.filter { it.fish.status in setOf(FishStatus.active, FishStatus.quarantine) }.maxByOrNull { it.fish.addedAt }
        if (fallback != null) {
            entries += DashboardRecord(
                kind = RecordKind.growth,
                title = "成长记录",
                primary = fallback.fish.name + " · 尚无测量",
                caption = "入缸 " + durationLabel(fallback.fish.addedAt),
                at = fallback.fish.addedAt,
                photoId = fallback.fish.coverPhotoId,
                photoThumbnail = true,
            )
        }
    }

    return entries.sortedByDescending { it.at }
}

private fun keywordSummary(values: List<WaterTestValue>): String? =
    aquariumParameterSummaries(values, limit = 4).firstOrNull()

private fun aquariumMetrics(latest: WaterTestWithValues?): List<AquariumMetric> {
    val icons = mapOf(
        "水温" to FishIcons.Droplet,
        "pH" to FishIcons.Eye,
        "盐度" to FishIcons.WaterLevel,
        "比重" to FishIcons.WaterLevel,
    )
    return aquariumParameterSummaries(latest?.values.orEmpty()).map { summary ->
        val label = summary.substringBefore(' ')
        AquariumMetric(summary, icons[label] ?: FishIcons.Droplet)
    }
}

private fun waterRecordLabel(overview: OverviewSnapshot): String =
    if (overview.todayWaterTestTanks > 0) "今日 ${overview.todayWaterTestTanks} 次" else "尚未测量"

private fun taskTargetName(task: CareTask, tanks: List<TankSummary>, fishRows: List<FishSummary>): String {
    task.tankId?.let { id -> tanks.firstOrNull { it.tank.id == id }?.tank?.name?.let { name -> return name } }
    task.fishId?.let { id ->
        val fish = fishRows.firstOrNull { it.fish.id == id }?.fish ?: return@let
        return fish.name + " · " + (tanks.firstOrNull { it.tank.id == fish.tankId }?.tank?.name ?: "未指定鱼缸")
    }
    return "未指定鱼缸"
}

private fun taskOverviewTitle(task: CareTask, targetName: String): String = task.displayTitleForOverview() + " - " + targetName
private fun time(value: Long) = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(value))
