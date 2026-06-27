package com.estudio.antiprocrastinacion.app.notification

import android.annotation.SuppressLint
import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.estudio.antiprocrastinacion.app.domain.repository.ContentRepository
import com.estudio.antiprocrastinacion.MainActivity
import com.estudio.antiprocrastinacion.R
import com.estudio.antiprocrastinacion.app.domain.repository.SessionRepository
import com.estudio.antiprocrastinacion.app.domain.repository.SettingsRepository
import com.estudio.antiprocrastinacion.app.domain.scheduler.SchedulerService
import com.estudio.antiprocrastinacion.app.model.content.Item
import com.estudio.antiprocrastinacion.app.model.content.SessionMode
import com.estudio.antiprocrastinacion.app.model.content.Surface
import com.estudio.antiprocrastinacion.app.ui.common.TimeProvider
import java.util.concurrent.TimeUnit

interface CognitiveNotificationScheduler {
    suspend fun refreshSchedule()
    suspend fun handleScheduledTrigger(slotIndex: Int): Boolean
}

class WorkManagerCognitiveNotificationScheduler(
    private val context: Context,
    private val contentRepository: ContentRepository,
    private val settingsRepository: SettingsRepository,
    private val sessionRepository: SessionRepository,
    private val schedulerService: SchedulerService,
    private val planner: CognitiveNotificationPlanner,
    private val timeProvider: TimeProvider,
) : CognitiveNotificationScheduler {
    override suspend fun refreshSchedule() {
        cancelAllScheduledNotifications()
        val settings = settingsRepository.getSettings()
        val plans = planner.buildPlans(settings, timeProvider.now())
        if (plans.isEmpty()) return

        val workManager = WorkManager.getInstance(context)
        plans.forEach { plan ->
            val request =
                PeriodicWorkRequestBuilder<CognitiveNotificationWorker>(1, TimeUnit.DAYS)
                    .setInitialDelay(plan.initialDelayMs, TimeUnit.MILLISECONDS)
                    .setInputData(Data.Builder().putInt(CognitiveNotificationWorker.KEY_SLOT_INDEX, plan.slotIndex).build())
                    .build()
            workManager.enqueueUniquePeriodicWork(
                uniqueWorkName(plan.slotIndex),
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }
    }

    @SuppressLint("MissingPermission")
    override suspend fun handleScheduledTrigger(slotIndex: Int): Boolean {
        val settings = settingsRepository.getSettings()
        if (!settings.cognitiveNotificationsEnabled) return false
        if (slotIndex !in 0 until settings.cognitiveNotificationsPerDay.coerceIn(1, CognitiveNotificationPlanner.MAX_COGNITIVE_NOTIFICATIONS_PER_DAY)) {
            return false
        }
        if (!notificationsPermissionGranted(context)) return false
        val renderState =
            resolveNotificationRenderState() ?: return false
        ensureChannelCreated(context)

        val intent =
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                markAsNotificationLaunch(
                    preferredUnitId = renderState.preferredUnitId,
                    preferredItemId = renderState.preferredItemId,
                )
            }
        val pendingIntent =
            PendingIntent.getActivity(
                context,
                renderState.preferredItemId.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        val notification =
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle(renderState.title)
                .setContentText(renderState.item.stem)
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText(renderState.item.stem),
                )
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .build()
        val notificationManager = NotificationManagerCompat.from(context)
        if (!notificationsPermissionGranted(context) || !notificationManager.areNotificationsEnabled()) {
            return false
        }
        try {
            notificationManager.notify(NOTIFICATION_ID_BASE + slotIndex, notification)
        } catch (_: SecurityException) {
            return false
        }
        return true
    }

    private suspend fun resolveNotificationRenderState(): NotificationRenderState? {
        val activeSession = sessionRepository.getActiveSession(SessionMode.QUICK)
        if (activeSession != null) {
            if (activeSession.mode != SessionMode.QUICK || activeSession.surface == Surface.SOCIAL_GATE) {
                return null
            }
            val currentItemId = activeSession.currentItemId ?: return null
            val currentItem = contentRepository.getItem(currentItemId) ?: return null
            return NotificationRenderState(
                title = activeSession.currentTopicTitle,
                item = currentItem,
                preferredUnitId = activeSession.topicUnitId,
                preferredItemId = currentItem.itemId,
            )
        }

        val packet = schedulerService.buildNotificationPacket() ?: return null
        val firstItemId = packet.initialItemIds.firstOrNull() ?: return null
        val firstItem = contentRepository.getItem(firstItemId) ?: return null
        return NotificationRenderState(
            title = packet.currentTopicTitle,
            item = firstItem,
            preferredUnitId = packet.topicUnitId,
            preferredItemId = firstItem.itemId,
        )
    }

    private fun cancelAllScheduledNotifications() {
        val workManager = WorkManager.getInstance(context)
        repeat(CognitiveNotificationPlanner.MAX_COGNITIVE_NOTIFICATIONS_PER_DAY) { index ->
            workManager.cancelUniqueWork(uniqueWorkName(index))
        }
    }

    private fun uniqueWorkName(slotIndex: Int): String = "cognitive_notification_slot_$slotIndex"

    companion object {
        const val CHANNEL_ID = "cognitive_notifications"
        private const val NOTIFICATION_ID_BASE = 4800

        fun ensureChannelCreated(context: Context) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel =
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.cognitive_notification_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = context.getString(R.string.cognitive_notification_channel_description)
                }
            notificationManager.createNotificationChannel(channel)
        }

        fun notificationsPermissionGranted(context: Context): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }
}

private data class NotificationRenderState(
    val title: String,
    val item: Item,
    val preferredUnitId: String,
    val preferredItemId: String,
)
