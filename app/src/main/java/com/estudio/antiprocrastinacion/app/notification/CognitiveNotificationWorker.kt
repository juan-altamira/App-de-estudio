package com.estudio.antiprocrastinacion.app.notification

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.estudio.antiprocrastinacion.StudyApplication

class CognitiveNotificationWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val slotIndex = inputData.getInt(KEY_SLOT_INDEX, -1)
        if (slotIndex < 0) return Result.success()
        val scheduler = (applicationContext as StudyApplication).container.cognitiveNotificationScheduler
        scheduler.handleScheduledTrigger(slotIndex)
        return Result.success()
    }

    companion object {
        const val KEY_SLOT_INDEX = "slot_index"
    }
}
