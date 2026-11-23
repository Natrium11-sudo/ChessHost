package org.example.schach.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import org.example.schach.App
import org.example.schach.MainActivity
import org.example.schach.R
import org.example.schach.engine.tournament.TournamentRunState
import org.example.schach.ui.i18n.trNow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Hält den Prozess am Leben, solange ein Turnier läuft, und zeigt den Fortschritt
 * in einer Benachrichtigung.
 *
 * Warum ein Service: 100 Partien dauern je nach Bedenkzeit eine bis mehrere
 * Stunden. Ohne Foreground-Service beendet Android den Prozess, sobald die App im
 * Hintergrund ist oder der Bildschirm ausgeht – das Turnier wäre dann weg. Die
 * eigentliche Arbeit macht der `TournamentController` im Application-Scope; dieser
 * Service hält nur wach und informiert.
 */
class TournamentService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val controller = (application as App).container.tournamentController
        createChannel()
        startForeground(NOTIFICATION_ID, buildNotification(controller.state.value))
        acquireWakeLock()

        scope.launch {
            controller.state.collect { state ->
                if (state == null || !state.running) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return@collect
                }
                notificationManager().notify(NOTIFICATION_ID, buildNotification(state))
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        releaseWakeLock()
        super.onDestroy()
    }

    private fun buildNotification(state: TournamentRunState?): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val played = state?.playedGames ?: 0
        val total = state?.totalGames ?: 0
        val pairing = state?.whiteLabel?.let { white ->
            trNow("%s gegen %s", white, state.blackLabel.orEmpty())
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentTitle(trNow("Turnier läuft"))
            .setContentText(
                trNow("%s von %s Partien", played, total) + (pairing?.let { " · $it" } ?: ""),
            )
            .setContentIntent(openApp)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setProgress(total.coerceAtLeast(1), played, false)
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    buildString {
                        append(trNow("%s von %s Partien gespielt.", played, total))
                        pairing?.let { append("\n" + trNow("Gerade: %s", it)) }
                        state?.openingName?.let { append("\n" + trNow("Eröffnung: %s", trNow(it))) }
                    },
                ),
            )
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            trNow("Turniere"),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = trNow("Fortschritt laufender Engine-Turniere")
            setShowBadge(false)
        }
        notificationManager().createNotificationChannel(channel)
    }

    private fun notificationManager(): NotificationManager =
        getSystemService(NotificationManager::class.java)

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val power = getSystemService(PowerManager::class.java) ?: return
        wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG).apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    private fun releaseWakeLock() {
        runCatching { wakeLock?.takeIf { it.isHeld }?.release() }
        wakeLock = null
    }

    companion object {
        private const val CHANNEL_ID = "tournament"
        private const val NOTIFICATION_ID = 42
        private const val WAKE_LOCK_TAG = "schach:tournament"

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, TournamentService::class.java),
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, TournamentService::class.java))
        }
    }
}
