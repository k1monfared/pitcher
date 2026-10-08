package pitcher.android.media

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/** Posts a "saved" notification whose tap opens the render's folder. */
object Notifications {

    private const val CHANNEL_ID = "renders"

    fun saved(context: Context, id: Int, title: String, openIntent: Intent?) {
        ensureChannel(context)
        val pending = openIntent?.let {
            PendingIntent.getActivity(
                context,
                id,
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(title)
            .setContentText("Tap to open the folder")
            .setAutoCancel(true)
        pending?.let { builder.setContentIntent(it) }
        runCatching {
            NotificationManagerCompat.from(context).notify(id, builder.build())
        }
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Renders", NotificationManager.IMPORTANCE_DEFAULT),
            )
        }
    }
}
