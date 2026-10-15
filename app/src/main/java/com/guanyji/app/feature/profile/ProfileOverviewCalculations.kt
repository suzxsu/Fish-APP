package com.guanyji.app.feature.profile

import com.guanyji.app.data.local.WaterTestWithValues

fun countLatestOutOfRangeValues(tests: List<WaterTestWithValues>): Int {
    val latest = tests.asSequence().flatMap { row ->
        row.values.asSequence()
            .filter { it.value?.isFinite() == true && (it.targetMin != null || it.targetMax != null) }
            .map { (row.test.tankId to it.parameterCode) to ((it.measuredAt ?: row.test.measuredAt) to it) }
    }.groupBy({ it.first }, { it.second })
        .values.mapNotNull { values -> values.maxByOrNull { it.first }?.second }

    return latest.count { value ->
        val actual = value.value ?: return@count false
        (value.targetMin != null && actual < value.targetMin) || (value.targetMax != null && actual > value.targetMax)
    }
}
