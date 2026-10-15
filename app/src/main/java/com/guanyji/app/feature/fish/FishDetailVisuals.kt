package com.guanyji.app.feature.fish

import com.guanyji.app.data.local.Fish
import com.guanyji.app.data.local.WaterTestValue
import com.guanyji.app.feature.overview.dashboardMetricPriority
import com.guanyji.app.feature.overview.dashboardParameterLabel
import com.guanyji.app.feature.overview.dashboardParameterText

/** 详情页水质条的一项，全部来自最近一次真实水质检测。 */
internal data class FishWaterMetric(val code: String, val label: String, val value: String)

/** 详情页身份卡上的真实档案标签。 */
internal data class FishDetailChip(val label: String, val value: String)

/**
 * 从最近一次水质检测的实测值中提取最多 [limit] 项指标。
 * 只保留有真实测量值的参数；没有任何记录时返回空列表，界面显示“尚无测量数据”，绝不补默认值。
 */
internal fun fishWaterMetrics(values: List<WaterTestValue>, limit: Int = 4): List<FishWaterMetric> =
    dashboardMetricPriority.take(limit).mapNotNull { code ->
        val row = values.firstOrNull { it.parameterCode == code } ?: return@mapNotNull null
        val number = row.value?.takeIf { it.isFinite() } ?: return@mapNotNull null
        val label = dashboardParameterLabel(code)
        val text = dashboardParameterText(code, number, row.unit)
        // 参数名已经在标签位上，避免出现“pH pH 7.2”这样的重复文案。
        FishWaterMetric(code = code, label = label, value = if (text.startsWith(label)) text.removePrefix(label).trim() else text)
    }

/**
 * 详情页身份卡上的附加标签：只展示用户真实填写过的字段。
 * 不生成任何虚构的行为 / 性格标签（例如“温和”“群游”）。
 */
internal fun fishDetailChips(fish: Fish, formatDate: (Long) -> String): List<FishDetailChip> = listOfNotNull(
    fish.sex?.trim()?.takeIf { it.isNotEmpty() }?.let { FishDetailChip("性别", it) },
    fish.source?.trim()?.takeIf { it.isNotEmpty() }?.let { FishDetailChip("来源", it) },
    fish.birthDate?.let { FishDetailChip("出生", formatDate(it)) },
)
