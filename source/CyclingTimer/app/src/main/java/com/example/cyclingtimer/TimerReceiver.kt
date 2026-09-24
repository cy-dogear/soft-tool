package com.example.cyclingtimer

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.os.Build
import android.os.PowerManager
import androidx.localbroadcastmanager.content.LocalBroadcastManager

class TimerReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TimerReceiver:WakeLock")
        wakeLock.acquire(5000)

        try {
            val currentRound = intent.getIntExtra("current_round", 1)
            val totalRounds = intent.getIntExtra("total_rounds", 1)
            val durationSec = intent.getIntExtra("duration_sec", 30)

            // 播放系统默认通知音（冒泡声）
            playNotificationSound(context)

            if (currentRound < totalRounds) {
                // 还有下一轮
                val nextRound = currentRound + 1
                scheduleNextAlarm(context, durationSec, nextRound, totalRounds, durationSec)
            } else {
                // 全部结束，发送广播通知 MainActivity 停止闪烁
                val finishIntent = Intent("ALL_FINISHED")
                LocalBroadcastManager.getInstance(context).sendBroadcast(finishIntent)
            }

        } finally {
            wakeLock.release()
        }
    }

    private fun playNotificationSound(context: Context) {
        try {
            val player = MediaPlayer.create(context, R.raw.my_beep)
            player.setOnCompletionListener {
                it.release()
            }
            player.start()
        } catch (e: Exception) {
            // 忽略音频错误
        }
    }

    private fun scheduleNextAlarm(
        context: Context,
        delaySeconds: Int,
        currentRound: Int,
        totalRounds: Int,
        durationSec: Int
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) return
        }

        val triggerTime = System.currentTimeMillis() + delaySeconds * 1000L

        val intent = Intent(context, TimerReceiver::class.java).apply {
            putExtra("current_round", currentRound)
            putExtra("total_rounds", totalRounds)
            putExtra("duration_sec", durationSec)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            currentRound,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val alarmInfo = AlarmManager.AlarmClockInfo(triggerTime, null)
            alarmManager.setAlarmClock(alarmInfo, pendingIntent)
        } else {
            @Suppress("DEPRECATION")
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
        }
    }
}