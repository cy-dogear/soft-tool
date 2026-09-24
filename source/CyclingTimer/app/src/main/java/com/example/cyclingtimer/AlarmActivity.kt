package com.example.cyclingtimer

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.widget.NumberPicker
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import java.util.Calendar

class AlarmActivity : AppCompatActivity() {

    private lateinit var alarm1Switch: SwitchCompat
    private lateinit var alarm1Hour: NumberPicker
    private lateinit var alarm1Minute: NumberPicker
    private lateinit var alarm2Switch: SwitchCompat
    private lateinit var alarm2Hour: NumberPicker
    private lateinit var alarm2Minute: NumberPicker

    private val prefs: SharedPreferences by lazy {
        getSharedPreferences("timer", MODE_PRIVATE)
    }

    companion object {
        private const val ALARM1_REQUEST_CODE = 1001
        private const val ALARM2_REQUEST_CODE = 1002
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_alarm)

        alarm1Switch = findViewById(R.id.alarm1Switch)
        alarm1Hour = findViewById(R.id.alarm1Hour)
        alarm1Minute = findViewById(R.id.alarm1Minute)
        alarm2Switch = findViewById(R.id.alarm2Switch)
        alarm2Hour = findViewById(R.id.alarm2Hour)
        alarm2Minute = findViewById(R.id.alarm2Minute)

        findViewById<TextView>(R.id.backButton).setOnClickListener {
            finish()
        }

        setupNumberPickers()
        loadSettings()
        bindEvents()
    }

    private fun setupNumberPickers() {
        alarm1Hour.minValue = 0
        alarm1Hour.maxValue = 23
        alarm1Hour.wrapSelectorWheel = false

        alarm2Hour.minValue = 0
        alarm2Hour.maxValue = 23
        alarm2Hour.wrapSelectorWheel = false

        alarm1Minute.minValue = 0
        alarm1Minute.maxValue = 59
        alarm1Minute.wrapSelectorWheel = false

        alarm2Minute.minValue = 0
        alarm2Minute.maxValue = 59
        alarm2Minute.wrapSelectorWheel = false
    }

    private fun loadSettings() {
        alarm1Switch.isChecked = prefs.getBoolean("alarm1_enabled", false)
        alarm1Hour.value = prefs.getInt("alarm1_hour", 10)
        alarm1Minute.value = prefs.getInt("alarm1_minute", 0)

        alarm2Switch.isChecked = prefs.getBoolean("alarm2_enabled", false)
        alarm2Hour.value = prefs.getInt("alarm2_hour", 15)
        alarm2Minute.value = prefs.getInt("alarm2_minute", 0)
    }

    private fun saveSettings() {
        prefs.edit().apply {
            putBoolean("alarm1_enabled", alarm1Switch.isChecked)
            putInt("alarm1_hour", alarm1Hour.value)
            putInt("alarm1_minute", alarm1Minute.value)
            putBoolean("alarm2_enabled", alarm2Switch.isChecked)
            putInt("alarm2_hour", alarm2Hour.value)
            putInt("alarm2_minute", alarm2Minute.value)
            apply()
        }
    }

    private fun bindEvents() {
        alarm1Switch.setOnCheckedChangeListener { _, _ ->
            saveSettings()
            if (alarm1Switch.isChecked) {
                scheduleAlarm(ALARM1_REQUEST_CODE, alarm1Hour.value, alarm1Minute.value)
            } else {
                cancelAlarm(ALARM1_REQUEST_CODE)
            }
        }

        alarm2Switch.setOnCheckedChangeListener { _, _ ->
            saveSettings()
            if (alarm2Switch.isChecked) {
                scheduleAlarm(ALARM2_REQUEST_CODE, alarm2Hour.value, alarm2Minute.value)
            } else {
                cancelAlarm(ALARM2_REQUEST_CODE)
            }
        }

        val valueChangeListener = NumberPicker.OnValueChangeListener { _, _, _ ->
            saveSettings()
            if (alarm1Switch.isChecked) {
                scheduleAlarm(ALARM1_REQUEST_CODE, alarm1Hour.value, alarm1Minute.value)
            }
            if (alarm2Switch.isChecked) {
                scheduleAlarm(ALARM2_REQUEST_CODE, alarm2Hour.value, alarm2Minute.value)
            }
        }

        alarm1Hour.setOnValueChangedListener(valueChangeListener)
        alarm1Minute.setOnValueChangedListener(valueChangeListener)
        alarm2Hour.setOnValueChangedListener(valueChangeListener)
        alarm2Minute.setOnValueChangedListener(valueChangeListener)
    }

    private fun scheduleAlarm(requestCode: Int, hour: Int, minute: Int) {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                val intent = Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                intent.data = android.net.Uri.parse("package:$packageName")
                startActivity(intent)
                return
            }
        }

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        val alarmIntent = Intent(this, AlarmReceiver::class.java).apply {
            action = "ALARM_$requestCode"
        }
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            requestCode,
            alarmIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmInfo = AlarmManager.AlarmClockInfo(calendar.timeInMillis, null)
        alarmManager.setAlarmClock(alarmInfo, pendingIntent)
    }

    private fun cancelAlarm(requestCode: Int) {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val alarmIntent = Intent(this, AlarmReceiver::class.java).apply {
            action = "ALARM_$requestCode"
        }
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            requestCode,
            alarmIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    class AlarmReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // 播放声音
            try {
                val player = MediaPlayer.create(context, R.raw.reminder)
                player.setOnCompletionListener { it.release() }
                player.start()
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // 获取闹钟标识
            val alarmAction = intent.action ?: return
            val requestCode = when (alarmAction) {
                "ALARM_1001" -> 1001
                "ALARM_1002" -> 1002
                else -> return
            }

            // 读取时间设置
            val prefs = context.getSharedPreferences("timer", Context.MODE_PRIVATE)
            val hour = prefs.getInt(if (requestCode == 1001) "alarm1_hour" else "alarm2_hour", 0)
            val minute = prefs.getInt(if (requestCode == 1001) "alarm1_minute" else "alarm2_minute", 0)

            // 计算明天同一时间
            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                add(Calendar.DAY_OF_YEAR, 1)
            }

            // 重新注册闹钟
            val alarmIntent = Intent(context, AlarmReceiver::class.java).apply {
                action = alarmAction
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                alarmIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val alarmInfo = AlarmManager.AlarmClockInfo(calendar.timeInMillis, null)
            alarmManager.setAlarmClock(alarmInfo, pendingIntent)
        }
    }
}