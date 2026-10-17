package com.guanyji.app.core.time

import java.time.ZoneId
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge

/** 距离下一个自然日 0 点还有多久；跨零点瞬间返回下一整天的长度。 */
internal fun nextDayBoundsDelay(
    now: Long,
    zone: ZoneId = ZoneId.systemDefault(),
): Long = (deviceDayBounds(now, zone).second - now).coerceAtLeast(MinDayBoundsDelayMillis)

/**
 * 会自己跟进自然日的「今天」边界流。
 *
 * 每天 0 点重新发射一次新边界；另外每 [MaxDayBoundsDelayMillis] 强制复核一次，
 * 用于兜住设备休眠、手动改时间或改时区导致定时器晚触发的情况。
 * 用 [distinctUntilChanged] 保证同一天内不会因为复核而重复触发下游查询。
 *
 * @param refresh 外部补充的刷新信号（例如页面可见时主动复核一次）。
 */
internal fun dayBoundsFlow(
    refresh: Flow<Unit> = emptyFlow(),
    now: () -> Long = System::currentTimeMillis,
    zone: () -> ZoneId = ZoneId::systemDefault,
): Flow<Pair<Long, Long>> =
    merge(
        flow {
            while (true) {
                emit(Unit)
                delay(nextDayBoundsDelay(now(), zone()).coerceAtMost(MaxDayBoundsDelayMillis))
            }
        },
        refresh,
    )
        .map { deviceDayBounds(now(), zone()) }
        .distinctUntilChanged()

/** 下限避免 0 延迟忙循环。 */
internal const val MinDayBoundsDelayMillis = 1_000L

/** 复核上限：即使定时器被系统挂起，最多半小时后也会重新核对自然日。 */
internal const val MaxDayBoundsDelayMillis = 30 * 60 * 1_000L
