package com.guanyji.app.data.local

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.room.withTransaction
import com.guanyji.app.MainActivity
import com.guanyji.app.data.repository.taskOccurrenceKey
import kotlinx.coroutines.runBlocking

object CareNotificationScheduler {
    const val EXTRA_TASK_ID = "care_task_id"
    private const val CHANNEL = "care_tasks"

    fun sync(context: Context) {
        val db = DatabaseFactory.create(context)
        val tasks = runBlocking { db.care().allTasks() }
        val rules = runBlocking { db.care().allRules() }.associateBy { it.id }
        val notificationsEnabled = runBlocking { db.settings().get("notifications.enabled")?.value?.toBooleanStrictOrNull() ?: true }
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        tasks.forEach { task ->
            val pending = alarmIntent(context, task.id)
            val wantsReminder = notificationsEnabled && (task.ruleId?.let { rules[it]?.let { rule -> rule.enabled && rule.reminderEnabled } ?: false } ?: task.reminderEnabled)
            if (task.status !in setOf(CareTaskStatus.pending, CareTaskStatus.overdue) || !wantsReminder || task.plannedAt <= System.currentTimeMillis()) manager.cancel(pending)
            else manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, task.plannedAt, pending)
        }
    }

    fun cancel(context: Context, taskId: String) {
        (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(alarmIntent(context, taskId))
    }

    internal fun alarmIntent(context: Context, taskId: String) = PendingIntent.getBroadcast(
        context, taskId.hashCode(), Intent(context, CareTaskAlarmReceiver::class.java).setData(Uri.parse(taskAlarmDataUri(taskId))).putExtra(EXTRA_TASK_ID, taskId),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    fun notify(context: Context, task: CareTask) {
        val enabled = runBlocking { DatabaseFactory.create(context).settings().get("notifications.enabled")?.value?.toBooleanStrictOrNull() ?: true }
        if (!enabled) return
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "养护任务提醒", NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(context, task.id.hashCode(), Intent(context, MainActivity::class.java).setData(Uri.parse(taskAlarmDataUri(task.id))).putExtra(EXTRA_TASK_ID, task.id).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val title = task.displayTitle()
        NotificationManagerCompat.from(context).notify(task.id, 0, NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_popup_reminder).setContentTitle("养护提醒：$title")
            .setContentText("计划时间已到，点击查看任务")
            .setContentIntent(open).setAutoCancel(true).build())
    }
}

internal fun taskAlarmDataUri(taskId: String) = "fishcare://task/$taskId"

class CareTaskAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(CareNotificationScheduler.EXTRA_TASK_ID) ?: return
        val db = DatabaseFactory.create(context)
        val task = runBlocking { db.care().getTask(id) } ?: return
        if (task.status == CareTaskStatus.pending || task.status == CareTaskStatus.overdue) {
            val dueTask = if (task.status == CareTaskStatus.pending && task.plannedAt <= System.currentTimeMillis()) task.copy(status = CareTaskStatus.overdue, updatedAt = System.currentTimeMillis()).also { runBlocking { db.care().updateTask(it) } } else task
            CareNotificationScheduler.notify(context, dueTask)
        }
    }
}

class CareRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_TIMEZONE_CHANGED) rebaseRules(context)
        if (intent.action in setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_MY_PACKAGE_REPLACED)) CareNotificationScheduler.sync(context)
    }

    private fun rebaseRules(context: Context) {
        val db = DatabaseFactory.create(context)
        runBlocking {
            val zone = java.time.ZoneId.systemDefault()
            db.withTransaction {
                val rules = db.care().allRules()
                rules.forEach { rule ->
                    val oldZone = runCatching { java.time.ZoneId.of(rule.timeZoneId) }.getOrDefault(zone)
                    db.care().openTasksForRule(rule.id).forEach { task ->
                        val localDate = java.time.Instant.ofEpochMilli(task.plannedAt).atZone(oldZone).toLocalDate()
                        val rebased = localDate.atTime(rule.localHour, rule.localMinute).atZone(zone).toInstant().toEpochMilli()
                        val postponedFrom = task.postponedFrom?.let { java.time.Instant.ofEpochMilli(it).atZone(oldZone).toLocalDateTime().atZone(zone).toInstant().toEpochMilli() }
                        db.care().updateTask(task.copy(plannedAt = rebased, instanceKey = taskOccurrenceKey(rebased, zone), postponedFrom = postponedFrom, updatedAt = System.currentTimeMillis()))
                    }
                    db.care().updateRule(rule.copy(timeZoneId = zone.id, updatedAt = System.currentTimeMillis()))
                }
            }
        }
    }
}

fun CareTaskType.displayName() = when (this) {
    CareTaskType.feeding -> "喂食"; CareTaskType.water_change -> "换水"; CareTaskType.water_test -> "水质检测"
    CareTaskType.salinity_test -> "盐度检测"; CareTaskType.maintenance -> "设备维护"; CareTaskType.custom -> "自定义"
}

fun CareTask.displayTitle() = if (type == CareTaskType.custom) customTitle?.takeIf(String::isNotBlank) ?: type.displayName() else type.displayName()
