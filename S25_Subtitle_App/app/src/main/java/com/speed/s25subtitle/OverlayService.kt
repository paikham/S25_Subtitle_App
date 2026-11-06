package com.speed.s25subtitle

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.*
import android.view.*
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class OverlayService : Service() {
    private lateinit var wm: WindowManager
    private var subContainer: LinearLayout? = null
    private var dragHandle: TextView? = null
    private lateinit var srcView: OutlinedTextView
    private lateinit var koView: OutlinedTextView
    private lateinit var subParams: WindowManager.LayoutParams
    private lateinit var handleParams: WindowManager.LayoutParams

    @Volatile private var running = false
    private val uiHandler = Handler(Looper.getMainLooper())
    private var channelId = ""
    private var delayMs = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        channelId = intent?.getStringExtra("channel") ?: "s25_sub_default"
        val koSize = intent?.getFloatExtra("ko_size", 20f) ?: 20f
        delayMs = intent?.getIntExtra("delay_ms", 0) ?: 0

        startForegroundNotification()
        setupOverlayViews(koSize)
        startStreamReader()
        return START_STICKY
    }

    private fun startForegroundNotification() {
        val chId = "s25_overlay_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val ch = NotificationChannel(chId, "S25 자막 서비스", NotificationManager.IMPORTANCE_LOW)
            nm.createNotificationChannel(ch)
        }
        val notif = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, chId)
                .setContentTitle("S25 투명 자막 작동 중")
                .setContentText("채널: $channelId")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .build()
        } else {
            Notification.Builder(this)
                .setContentTitle("S25 투명 자막 작동 중")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .build()
        }
        startForeground(1001, notif)
    }

    private fun setupOverlayViews(koSize: Float) {
        removeOverlays()
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val dm = resources.displayMetrics

        // 1. 순수 투명 자막 레이어 (터치 100% 투과: 유튜브/트위치 화면 조작 방해 제로)
        subContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.TRANSPARENT)
            setPadding(24, 8, 24, 8)
        }

        srcView = OutlinedTextView(this).apply {
            text = "[$channelId] PC 자막 연결 완료"
            textSize = (koSize * 0.62f).coerceAtLeast(11f)
            setTextColor(Color.parseColor("#DDDDDD"))
            strokeWidthPx = 6f
            gravity = Gravity.CENTER
            setTypeface(null, Typeface.BOLD)
        }

        koView = OutlinedTextView(this).apply {
            text = "유튜브·트위치 앱을 켜고 편안하게 감상하세요!"
            textSize = koSize
            setTextColor(Color.parseColor("#00FF7F"))
            strokeWidthPx = 9f
            gravity = Gravity.CENTER
            setTypeface(null, Typeface.BOLD)
        }

        subContainer?.addView(srcView)
        subContainer?.addView(koView)

        subParams = WindowManager.LayoutParams(
            (dm.widthPixels * 0.92).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = (dm.heightPixels * 0.76).toInt()
        }

        // 2. 우측 끝 초소형 이동/종료 핸들 (드래그: 자막 높이 조절 / 길게 터치: 종료)
        val pillBg = GradientDrawable().apply {
            setColor(Color.parseColor("#66000000"))
            cornerRadius = 40f
            setStroke(2, Color.parseColor("#8800FF7F"))
        }

        dragHandle = TextView(this).apply {
            text = "✥"
            textSize = 15f
            setTextColor(Color.parseColor("#00FF7F"))
            gravity = Gravity.CENTER
            background = pillBg
            setPadding(22, 12, 22, 12)
        }

        handleParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 16
            y = subParams.y
        }

        var initialY = 0
        var touchY = 0f
        var downTime = 0L

        dragHandle?.setOnTouchListener { _, ev ->
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialY = subParams.y
                    touchY = ev.rawY
                    downTime = System.currentTimeMillis()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dy = (ev.rawY - touchY).toInt()
                    subParams.y = initialY + dy
                    handleParams.y = initialY + dy
                    try {
                        wm.updateViewLayout(subContainer, subParams)
                        wm.updateViewLayout(dragHandle, handleParams)
                    } catch (_: Exception) {}
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (System.currentTimeMillis() - downTime > 900 && kotlin.math.abs(ev.rawY - touchY) < 25) {
                        stopSelf()
                    }
                    true
                }
                else -> false
            }
        }

        wm.addView(subContainer, subParams)
        wm.addView(dragHandle, handleParams)
    }

    private fun startStreamReader() {
        running = false
        Thread.sleep(100)
        running = true

        thread(start = true, isDaemon = true) {
            while (running) {
                var conn: HttpURLConnection? = null
                try {
                    val url = URL("https://ntfy.sh/$channelId/json")
                    conn = (url.openConnection() as HttpURLConnection).apply {
                        connectTimeout = 5000
                        readTimeout = 65000
                        requestMethod = "GET"
                    }
                    val reader = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8))
                    var line: String?
                    while (running && reader.readLine().also { line = it } != null) {
                        val raw = line?.trim() ?: continue
                        if (raw.isEmpty()) continue
                        val rootObj = JSONObject(raw)
                        if (rootObj.optString("event") == "message") {
                            val msgStr = rootObj.optString("message", "")
                            if (msgStr.startsWith("{")) {
                                val subObj = JSONObject(msgStr)
                                val lang = subObj.optString("lang", "EN")
                                val src = subObj.optString("src", "")
                                val ko = subObj.optString("ko", "")
                                val renderTask = Runnable {
                                    srcView.text = "[$lang] $src"
                                    koView.text = ko
                                }
                                if (delayMs > 0) {
                                    uiHandler.postDelayed(renderTask, delayMs.toLong())
                                } else {
                                    uiHandler.post(renderTask)
                                }
                            }
                        }
                    }
                } catch (_: Exception) {
                    Thread.sleep(1500)
                } finally {
                    try { conn?.disconnect() } catch (_: Exception) {}
                }
            }
        }
    }

    private fun removeOverlays() {
        try {
            if (subContainer != null) wm.removeView(subContainer)
        } catch (_: Exception) {}
        try {
            if (dragHandle != null) wm.removeView(dragHandle)
        } catch (_: Exception) {}
        subContainer = null
        dragHandle = null
    }

    override fun onDestroy() {
        running = false
        removeOverlays()
        super.onDestroy()
    }
}
