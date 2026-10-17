package com.guanyji.app.data.repository

import com.guanyji.app.data.local.AppSetting

/**
 * 列表自定义顺序的持久化与合并规则。
 *
 * 顺序是「显示偏好」而不是业务记录，因此存放在 `app_settings` 键值表里，
 * 不新增数据库列：新增列必须升 schema 版本，而当前备份恢复是整库文件 + 严格版本校验
 * （`sqliteUserVersion == FishDatabase.VERSION`），一升版本用户手上的备份就全部无法恢复。
 *
 * 编码用逗号分隔的 id 串（id 为 UUID，不含分隔符），不依赖 org.json，
 * 因此这些规则可以在普通 JVM 单元测试里直接验证。
 */

/** 「我的鱼」列表的自定义顺序。 */
internal const val FishOrderKey = "list.order.fish"

/** 「鱼缸」列表的自定义顺序。 */
internal const val TankOrderKey = "list.order.tank"

/**
 * 「我的鱼」上次选中的排序方式（存枚举名）。
 *
 * 必须一起持久化：顺序落了库但排序模式没记住的话，重启后页面回到「最近添加」，
 * 用户会以为拖拽白做了。
 */
internal const val FishSortKey = "list.sort.fish"

/** 读取排序方式枚举名；缺失或非法时返回 null，由调用方回落到默认排序。 */
internal fun List<AppSetting>.savedSortName(key: String): String? =
    firstOrNull { it.key == key }?.value?.trim()?.takeIf(String::isNotEmpty)

/** 解析顺序串：空白、重复、碎片一律丢弃；格式损坏时退化为空表而不是抛异常。 */
internal fun parseOrderIds(raw: String?): List<String> =
    raw.orEmpty()
        .split(',')
        .map(String::trim)
        .filter(String::isNotEmpty)
        .distinct()

/** 编码顺序串，保持顺序并去重。 */
internal fun encodeOrderIds(ids: List<String>): String =
    ids.map(String::trim)
        .filter(String::isNotEmpty)
        .distinct()
        .joinToString(",")

/** 从设置行里取顺序，缺少该键时返回空表（表示"还没有自定义顺序"）。 */
internal fun List<AppSetting>.savedOrder(key: String): List<String> =
    parseOrderIds(firstOrNull { it.key == key }?.value)

/**
 * 把已保存的顺序套到当前数据上。
 *
 * 已保存且仍存在的 id 依次排在前面；新增加的、以及顺序串里没有记录的条目，
 * 保持调用方传入的默认顺序追加在末尾——这样"新增鱼只"永远出现在最后，不会打乱用户排好的顺序。
 */
internal fun <T> List<T>.applySavedOrder(savedIds: List<String>, idOf: (T) -> String): List<T> {
    if (savedIds.isEmpty() || isEmpty()) return this
    val byId = associateBy(idOf)
    val ordered = savedIds.mapNotNull(byId::get)
    if (ordered.size == size) return ordered
    val placed = ordered.mapTo(HashSet()) { idOf(it) }
    return ordered + filterNot { idOf(it) in placed }
}

/**
 * 只重排可见子集。
 *
 * 筛选 / 搜索状态下可见的条目只是全量的一部分，拖拽只应改变它们在完整顺序里
 * **原先占据的槽位**，被隐藏条目的相对位置保持不变。
 *
 * @param fullOrder 当前完整顺序（已通过 [applySavedOrder] 补齐所有现存 id）
 * @param reorderedVisible 拖拽后可见条目的新顺序
 */
internal fun mergeVisibleReorder(fullOrder: List<String>, reorderedVisible: List<String>): List<String> {
    if (fullOrder.isEmpty() || reorderedVisible.isEmpty()) return fullOrder
    val visible = reorderedVisible.toSet()
    val slots = fullOrder.indices.filter { fullOrder[it] in visible }
    val result = fullOrder.toMutableList()
    if (slots.isNotEmpty()) {
        slots.forEachIndexed { position, slot ->
            result[slot] = reorderedVisible.getOrNull(position) ?: fullOrder[slot]
        }
    }
    // 防御：可见项里有完整顺序尚未收录的 id（例如刚新增且顺序尚未落盘），追加到末尾而不是丢弃。
    val missing = reorderedVisible.filterNot { it in result }
    return if (missing.isEmpty()) result else result + missing
}

/**
 * 单条移动：把 [from] 位置的元素移到 [to] 位置，越界或同位时原样返回。
 * 拖拽回调在每跨过一格时调用它，避免在 UI 里重复写索引运算。
 */
internal fun <T> List<T>.movedItem(from: Int, to: Int): List<T> {
    if (from == to) return this
    if (from !in indices || to !in indices) return this
    return toMutableList().apply { add(to, removeAt(from)) }
}
