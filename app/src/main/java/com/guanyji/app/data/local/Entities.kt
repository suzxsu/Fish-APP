package com.guanyji.app.data.local

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

private fun newId() = UUID.randomUUID().toString()

enum class TankType { freshwater, saltwater }
enum class TankStatus { active, archived }
enum class FishStatus { active, quarantine, transferred, deceased, archived }
enum class FishEventType { created, moved, statusChanged, archived }
enum class FeedingTargetType { tank, fish }
enum class CareTaskType { feeding, water_change, water_test, salinity_test, maintenance, custom }
enum class CareTaskStatus { pending, completed, skipped, overdue, cancelled }
enum class CareRecordType { feeding, water_change, water_test, salinity_test, maintenance, custom }

@Entity(tableName = "photos", indices = [Index("localPath")])
data class Photo(
    @PrimaryKey val id: String = newId(),
    val localPath: String,
    val thumbnailPath: String?,
    val mimeType: String,
    val width: Int?,
    val height: Int?,
    val createdAt: Long,
)

@Entity(
    tableName = "tanks",
    foreignKeys = [ForeignKey(entity = Photo::class, parentColumns = ["id"], childColumns = ["photoId"], onDelete = ForeignKey.SET_NULL)],
    indices = [Index("status"), Index("photoId")],
)
data class Tank(
    @PrimaryKey val id: String = newId(),
    val name: String,
    val type: TankType,
    val photoId: String?,
    val lengthCm: Double? = null,
    val widthCm: Double? = null,
    val heightCm: Double? = null,
    val capacityValue: Double?,
    val capacityUnit: String?,
    val capacityIsEstimated: Boolean = false,
    val startedAt: Long?,
    val equipment: String? = null,
    val status: TankStatus = TankStatus.active,
    @ColumnInfo(defaultValue = "0") val salinityManagementEnabled: Boolean = false,
    val notes: String?,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "fish",
    foreignKeys = [
        ForeignKey(entity = Tank::class, parentColumns = ["id"], childColumns = ["tankId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = Photo::class, parentColumns = ["id"], childColumns = ["coverPhotoId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("tankId"), Index("coverPhotoId"), Index("status")],
)
data class Fish(
    @PrimaryKey val id: String = newId(),
    val tankId: String,
    val name: String,
    val species: String?,
    val coverPhotoId: String?,
    val addedAt: Long,
    val birthDate: Long?,
    val status: FishStatus = FishStatus.active,
    val statusChangedAt: Long?,
    val notes: String?,
    val sex: String? = null,
    val source: String? = null,
    val deathDateKnown: Boolean = true,
    val deathDate: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "fish_events", foreignKeys = [ForeignKey(entity = Fish::class, parentColumns = ["id"], childColumns = ["fishId"], onDelete = ForeignKey.CASCADE), ForeignKey(entity = Tank::class, parentColumns = ["id"], childColumns = ["tankId"], onDelete = ForeignKey.SET_NULL)], indices = [Index(value = ["fishId", "occurredAt"]), Index("tankId")])
data class FishEvent(
    @PrimaryKey val id: String = newId(),
    val fishId: String,
    val type: FishEventType,
    val occurredAt: Long,
    val tankId: String?,
    val targetTankId: String?,
    val status: FishStatus?,
    val dateKnown: Boolean = true,
    val notes: String? = null,
)

@Entity(
    tableName = "growth_records",
    foreignKeys = [
        ForeignKey(entity = Fish::class, parentColumns = ["id"], childColumns = ["fishId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Photo::class, parentColumns = ["id"], childColumns = ["photoId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index(value = ["fishId", "measuredAt"]), Index("photoId")],
)
data class GrowthRecord(
    @PrimaryKey val id: String = newId(),
    val fishId: String,
    val measuredAt: Long,
    val lengthValue: Double?,
    val lengthUnit: String?,
    val weightValue: Double?,
    val weightUnit: String?,
    val method: String?,
    val photoId: String?,
    val notes: String?,
)

@Entity(tableName = "feeding_events", foreignKeys = [ForeignKey(entity = Tank::class, parentColumns = ["id"], childColumns = ["tankId"], onDelete = ForeignKey.SET_NULL)], indices = [Index(value = ["tankId", "fedAt"])])
data class FeedingEvent(
    @PrimaryKey val id: String = newId(),
    val tankId: String?,
    val fedAt: Long,
    val foodType: String?,
    val amountValue: Double?,
    val amountUnit: String?,
    val result: String?,
    val notes: String?,
)

@Entity(
    tableName = "feeding_targets",
    foreignKeys = [
        ForeignKey(entity = FeedingEvent::class, parentColumns = ["id"], childColumns = ["feedingEventId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Tank::class, parentColumns = ["id"], childColumns = ["tankId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Fish::class, parentColumns = ["id"], childColumns = ["fishId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("feedingEventId"), Index("tankId"), Index("fishId")],
)
data class FeedingTarget(
    @PrimaryKey val id: String = newId(),
    val feedingEventId: String,
    val targetType: FeedingTargetType,
    val tankId: String?,
    val fishId: String?,
    val result: String?,
)

@Entity(tableName = "common_foods", indices = [Index(value = ["name"], unique = true)])
data class CommonFood(
    @PrimaryKey val id: String = newId(),
    val name: String,
    val createdAt: Long,
)

@Entity(tableName = "water_tests", foreignKeys = [ForeignKey(entity = Tank::class, parentColumns = ["id"], childColumns = ["tankId"], onDelete = ForeignKey.CASCADE)], indices = [Index(value = ["tankId", "measuredAt"])])
data class WaterTest(
    @PrimaryKey val id: String = newId(),
    val tankId: String,
    val measuredAt: Long,
    val method: String?,
    val notes: String?,
)

@Entity(tableName = "water_test_values", foreignKeys = [ForeignKey(entity = WaterTest::class, parentColumns = ["id"], childColumns = ["waterTestId"], onDelete = ForeignKey.CASCADE)], indices = [Index("waterTestId"), Index("parameterCode"), Index(value = ["waterTestId", "parameterCode"], unique = true)])
data class WaterTestValue(
    @PrimaryKey val id: String = newId(),
    val waterTestId: String,
    val parameterCode: String,
    val value: Double?,
    val unit: String,
    val targetMin: Double?,
    val targetMax: Double?,
    val measuredAt: Long? = null,
    val method: String? = null,
    val notes: String? = null,
    val targetSource: String? = null,
)

@Entity(tableName = "water_changes", foreignKeys = [ForeignKey(entity = Tank::class, parentColumns = ["id"], childColumns = ["tankId"], onDelete = ForeignKey.CASCADE)], indices = [Index(value = ["tankId", "completedAt"])])
data class WaterChange(
    @PrimaryKey val id: String = newId(),
    val tankId: String,
    val completedAt: Long,
    val volumeValue: Double?,
    val volumeUnit: String?,
    val ratio: Double?,
    val nextPlannedAt: Long?,
    val notes: String?,
    val beforeTemperature: Double? = null,
    val afterTemperature: Double? = null,
    val temperatureUnit: String? = null,
    val beforeSalinity: Double? = null,
    val afterSalinity: Double? = null,
    val salinityUnit: String? = null,
    val supplies: String? = null,
)

@Entity(
    tableName = "care_tasks",
    foreignKeys = [
        ForeignKey(entity = Tank::class, parentColumns = ["id"], childColumns = ["tankId"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(entity = Fish::class, parentColumns = ["id"], childColumns = ["fishId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index(value = ["plannedAt", "status"]), Index("tankId"), Index("fishId"), Index(value = ["ruleId", "instanceKey"], unique = true)],
)
data class CareTask(
    @PrimaryKey val id: String = newId(),
    val tankId: String?,
    val fishId: String?,
    val type: CareTaskType,
    val plannedAt: Long,
    val recurrenceRule: String?,
    val status: CareTaskStatus = CareTaskStatus.pending,
    val notes: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val ruleId: String? = null,
    val instanceKey: String? = null,
    @ColumnInfo(defaultValue = "0") val reminderEnabled: Boolean = false,
    val postponedFrom: Long? = null,
    val lastPostponedAt: Long? = null,
    val customTitle: String? = null,
)

@Entity(tableName = "care_rules", indices = [Index("tankId"), Index("fishId")])
data class CareRule(
    @PrimaryKey val id: String = newId(),
    val tankId: String?,
    val fishId: String?,
    val type: CareTaskType,
    val intervalDays: Int,
    val localHour: Int,
    val localMinute: Int,
    val timeZoneId: String,
    val notes: String?,
    val reminderEnabled: Boolean,
    val enabled: Boolean = true,
    val createdAt: Long,
    val updatedAt: Long,
    val customTitle: String? = null,
)

@Entity(
    tableName = "care_records",
    foreignKeys = [
        ForeignKey(entity = CareTask::class, parentColumns = ["id"], childColumns = ["taskId"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(entity = Tank::class, parentColumns = ["id"], childColumns = ["tankId"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(entity = Fish::class, parentColumns = ["id"], childColumns = ["fishId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("taskId"), Index(value = ["tankId", "completedAt"]), Index("fishId")],
)
data class CareRecord(
    @PrimaryKey val id: String = newId(),
    val taskId: String?,
    val tankId: String?,
    val fishId: String?,
    val type: CareRecordType,
    val completedAt: Long,
    val payloadJson: String?,
    val notes: String?,
)

@Entity(tableName = "app_settings")
data class AppSetting(
    @PrimaryKey val key: String,
    val value: String,
    val updatedAt: Long,
)
