package com.example.cyclingtimer

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager

class MainActivity : AppCompatActivity() {

    private lateinit var statusIcon: TextView
    private lateinit var durationInput: EditText
    private lateinit var roundsInput: EditText

    private var isRunning = false
    private var blinkHandler: Handler? = null
    private var blinkRunnable: Runnable? = null
    private var currentBlinkState = true

    private val prefs: SharedPreferences by lazy {
        getSharedPreferences("timer", MODE_PRIVATE)
    }

    // 广播接收器：接收全部结束信号
    private val allFinishedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == "ALL_FINISHED") {
                stopTimer()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusIcon = findViewById(R.id.statusIcon)
        durationInput = findViewById(R.id.durationInput)
        roundsInput = findViewById(R.id.roundsInput)

        // 设置初始图标文字
        statusIcon.text = "▷"

        loadSettings()

        // 点击输入框时中止计时
        durationInput.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus && isRunning) {
                stopTimer()
            }
        }
        roundsInput.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus && isRunning) {
                stopTimer()
            }
        }

        // 在 onCreate 方法中添加
        val alarmEntry = findViewById<TextView>(R.id.alarmEntry)
        alarmEntry.setOnClickListener {
            startActivity(Intent(this, AlarmActivity::class.java))
        }

        // 点击空白区域开始计时
        val mainLayout = findViewById<View>(android.R.id.content)
        mainLayout.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                hideKeyboard()
                if (!isRunning) {
                    startTimer()
                }
            }
            true
        }

        // 注册本地广播接收器
        val filter = IntentFilter("ALL_FINISHED")
        LocalBroadcastManager.getInstance(this).registerReceiver(allFinishedReceiver, filter)
    }

    override fun onDestroy() {
        super.onDestroy()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(allFinishedReceiver)
        stopBlink()
    }

    private fun loadSettings() {
        val duration = prefs.getInt("saved_duration", 30)
        val rounds = prefs.getInt("saved_rounds", 3)
        durationInput.setText(duration.toString())
        roundsInput.setText(rounds.toString())
    }

    private fun saveSettings() {
        val duration = durationInput.text.toString().toIntOrNull() ?: 30
        val rounds = roundsInput.text.toString().toIntOrNull() ?: 3
        prefs.edit().apply {
            putInt("saved_duration", duration)
            putInt("saved_rounds", rounds)
            apply()
        }
    }

    private fun startTimer() {
        saveSettings()

        val durationSec = durationInput.text.toString().toIntOrNull() ?: 30
        val totalRounds = roundsInput.text.toString().toIntOrNull() ?: 3

        if (durationSec <= 0 || totalRounds <= 0) return

        // 检查精确闹钟权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                val intent = Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                intent.data = Uri.parse("package:$packageName")
                startActivity(intent)
                return
            }
        }

        isRunning = true
        startBlink()

        // 注册第一个闹钟
        scheduleAlarm(durationSec, 1, totalRounds, durationSec)
    }

    private fun stopTimer() {
        if (!isRunning) return

        isRunning = false
        stopBlink()
        cancelAllAlarms()
        statusIcon.text = "▷"
    }

    private fun scheduleAlarm(delaySeconds: Int, round: Int, total: Int, duration: Int) {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerTime = System.currentTimeMillis() + delaySeconds * 1000L

        val intent = Intent(this, TimerReceiver::class.java).apply {
            putExtra("current_round", round)
            putExtra("total_rounds", total)
            putExtra("duration_sec", duration)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            this,
            round,
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

    private fun cancelAllAlarms() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        for (i in 1..100) {
            val intent = Intent(this, TimerReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                this,
                i,
                intent,
                PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
        }
    }

    private fun startBlink() {
        stopBlink()
        blinkHandler = Handler(Looper.getMainLooper())
        currentBlinkState = true
        statusIcon.visibility = View.VISIBLE
        statusIcon.text = "▷"

        blinkRunnable = object : Runnable {
            override fun run() {
                currentBlinkState = !currentBlinkState
                statusIcon.visibility = if (currentBlinkState) View.VISIBLE else View.INVISIBLE
                blinkHandler?.postDelayed(this, 500)
            }
        }
        blinkHandler?.post(blinkRunnable!!)
    }

    private fun stopBlink() {
        blinkHandler?.removeCallbacks(blinkRunnable!!)
        blinkHandler = null
        blinkRunnable = null
        statusIcon.visibility = View.VISIBLE
        statusIcon.text = "▷"
    }

    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        currentFocus?.let { view ->
            imm.hideSoftInputFromWindow(view.windowToken, 0)
        }
    }
}