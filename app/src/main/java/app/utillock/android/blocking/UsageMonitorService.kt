package app.utillock.android.blocking

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import app.utillock.android.MainActivity
import app.utillock.android.R
import app.utillock.android.UtilLockApplication
import app.utillock.android.model.ScheduleEvaluator
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

class UsageMonitorService : Service() {
    private val running = AtomicBoolean(false)
    private val repository by lazy { (application as UtilLockApplication).container.protectionRepository }
    private val usageStats by lazy { getSystemService(UsageStatsManager::class.java) }
    private var lastPackage = ""
    private var lastBlockAt = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            repository.setUsageMonitor(false)
            running.set(false)
            stopSelf()
            return START_NOT_STICKY
        }
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MANIFEST,
        )
        if (running.compareAndSet(false, true)) {
            thread(name = "utillock-usage", isDaemon = true, block = ::monitorLoop)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        running.set(false)
        super.onDestroy()
    }

    private fun monitorLoop() {
        while (running.get()) {
            val now = System.currentTimeMillis()
            val events = usageStats.queryEvents(now - 2_500, now)
            val event = UsageEvents.Event()
            var foreground: String? = null
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) foreground = event.packageName
            }
            foreground?.let(::maybeBlock)
            SystemClock.sleep(750)
        }
    }

    private fun maybeBlock(packageName: String) {
        if (packageName == this.packageName || packageName == lastPackage) return
        lastPackage = packageName
        val active = ScheduleEvaluator.activeProtection(repository.snapshot())
        if (!active.active || packageName !in active.packages) return
        val elapsed = SystemClock.elapsedRealtime()
        if (elapsed - lastBlockAt < 1_200) return
        lastBlockAt = elapsed
        startActivity(
            Intent(this, BlockedActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(BlockedActivity.EXTRA_PACKAGE, packageName),
        )
    }

    private fun notification(): android.app.Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.usage_notification_channel), NotificationManager.IMPORTANCE_LOW),
        )
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.usage_notification_title))
            .setContentText(getString(R.string.usage_notification_text))
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val ACTION_START = "app.utillock.android.START_USAGE_MONITOR"
        const val ACTION_STOP = "app.utillock.android.STOP_USAGE_MONITOR"
        private const val CHANNEL_ID = "usage-monitor"
        private const val NOTIFICATION_ID = 2102

        fun start(context: Context) {
            context.startForegroundService(Intent(context, UsageMonitorService::class.java).setAction(ACTION_START))
        }

        fun stop(context: Context) {
            context.startService(Intent(context, UsageMonitorService::class.java).setAction(ACTION_STOP))
        }
    }
}
