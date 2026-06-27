package com.estudio.antiprocrastinacion.app.notification

import android.content.Intent

private const val EXTRA_NOTIFICATION_LAUNCH = "notification_launch"
private const val EXTRA_NOTIFICATION_UNIT_ID = "notification_unit_id"
private const val EXTRA_NOTIFICATION_ITEM_ID = "notification_item_id"

data class NotificationLaunchRequest(
    val preferredUnitId: String?,
    val preferredItemId: String?,
)

fun Intent.markAsNotificationLaunch(
    preferredUnitId: String?,
    preferredItemId: String?,
) {
    putExtra(EXTRA_NOTIFICATION_LAUNCH, true)
    putExtra(EXTRA_NOTIFICATION_UNIT_ID, preferredUnitId)
    putExtra(EXTRA_NOTIFICATION_ITEM_ID, preferredItemId)
}

fun Intent.consumeNotificationLaunchRequest(): NotificationLaunchRequest? {
    if (!getBooleanExtra(EXTRA_NOTIFICATION_LAUNCH, false)) return null
    val request =
        NotificationLaunchRequest(
            preferredUnitId = getStringExtra(EXTRA_NOTIFICATION_UNIT_ID),
            preferredItemId = getStringExtra(EXTRA_NOTIFICATION_ITEM_ID),
        )
    removeExtra(EXTRA_NOTIFICATION_LAUNCH)
    removeExtra(EXTRA_NOTIFICATION_UNIT_ID)
    removeExtra(EXTRA_NOTIFICATION_ITEM_ID)
    return request
}
