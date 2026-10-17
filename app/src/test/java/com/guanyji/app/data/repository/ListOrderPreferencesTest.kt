package com.guanyji.app.data.repository

import com.guanyji.app.data.local.AppSetting
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 列表自定义顺序的规则回归。
 *
 * 顺序以字符串存在设置表里，解析、对账、筛选内合并都在这里钉死：
 * 顺序串损坏不能让列表崩，新增条目必须落在末尾，筛选状态下拖拽不能动隐藏项。
 */
class ListOrderPreferencesTest {
    @Test
    fun parsingDropsBlankDuplicatesAndFragments() {
        assertEquals(
            listOf("a", "b", "c"),
            parseOrderIds(" a , b ,, a , c ,"),
        )
        assertEquals(emptyList<String>(), parseOrderIds(null))
        assertEquals(emptyList<String>(), parseOrderIds("   "))
    }

    @Test
    fun encodingKeepsOrderAndRemovesDuplicates() {
        assertEquals("b,a,c", encodeOrderIds(listOf("b", "a", "b", " ", "c")))
        assertEquals("", encodeOrderIds(emptyList()))
    }

    @Test
    fun savedOrderIsUsedWhenTheKeyExists() {
        val settings = listOf(
            AppSetting(FishOrderKey, "f3,f1,f2", 0L),
            AppSetting(TankOrderKey, "t2,t1", 0L),
        )
        assertEquals(listOf("f3", "f1", "f2"), settings.savedOrder(FishOrderKey))
        assertEquals(listOf("t2", "t1"), settings.savedOrder(TankOrderKey))
        assertEquals(emptyList<String>(), settings.savedOrder("list.order.unknown"))
    }

    @Test
    fun savedSortNameFallsBackToNullWhenMissingOrBlank() {
        val settings = listOf(AppSetting(FishSortKey, "  custom  ", 0L))
        assertEquals("custom", settings.savedSortName(FishSortKey))
        assertEquals(null, settings.savedSortName("list.sort.unknown"))
        assertEquals(null, listOf(AppSetting(FishSortKey, "   ", 0L)).savedSortName(FishSortKey))
    }

    @Test
    fun applySavedOrderKeepsNewItemsAtTheEnd() {
        val current = listOf("f1", "f2", "f3", "f4")
        val saved = listOf("f3", "f1")
        assertEquals(listOf("f3", "f1", "f2", "f4"), current.applySavedOrder(saved) { it })
    }

    @Test
    fun applySavedOrderIgnoresStaleIdsAndEmptySaved() {
        val current = listOf("f1", "f2")
        // 顺序串里的 gone 已经不存在：丢掉它，剩下的 f2 按已保存顺序排到最前，未记录的 f1 追加在后。
        assertEquals(listOf("f2", "f1"), current.applySavedOrder(listOf("gone", "f2")) { it })
        assertEquals(current, current.applySavedOrder(emptyList()) { it })
        assertEquals(emptyList<String>(), emptyList<String>().applySavedOrder(listOf("f1")) { it })
    }

    @Test
    fun visibleReorderOnlySwapsTheSlotsTheVisibleItemsUsed() {
        // 完整顺序 c1 c2 c3 c4 c5，当前只显示 c1 c3 c5（按下标 0、2、4 占用槽位）
        val full = listOf("c1", "c2", "c3", "c4", "c5")
        // 用户把 c5 拖到最前面：可见顺序变成 c5 c1 c3
        val result = mergeVisibleReorder(full, listOf("c5", "c1", "c3"))
        assertEquals(listOf("c5", "c2", "c1", "c4", "c3"), result)
    }

    @Test
    fun visibleReorderKeepsFullOrderWhenNothingChanged() {
        val full = listOf("a", "b", "c")
        assertEquals(full, mergeVisibleReorder(full, listOf("a", "b", "c")))
        assertEquals(full, mergeVisibleReorder(full, emptyList()))
        assertEquals(emptyList<String>(), mergeVisibleReorder(emptyList(), listOf("a")))
    }

    @Test
    fun visibleReorderAppendsIdsMissingFromFullOrder() {
        val result = mergeVisibleReorder(listOf("a", "b"), listOf("a", "b", "new"))
        assertEquals(listOf("a", "b", "new"), result)
    }

    @Test
    fun movedItemHandlesEdgesAndOutOfRange() {
        val list = listOf("a", "b", "c")
        assertEquals(listOf("c", "a", "b"), list.movedItem(2, 0))
        assertEquals(listOf("b", "a", "c"), list.movedItem(1, 0))
        assertEquals(list, list.movedItem(1, 1))
        assertEquals(list, list.movedItem(-1, 2))
        assertEquals(list, list.movedItem(0, 5))
        assertEquals(emptyList<String>(), emptyList<String>().movedItem(0, 1))
    }
}
