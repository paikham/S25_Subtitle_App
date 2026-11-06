package com.speed.s25subtitle

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.*

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("s25_sub_prefs", Context.MODE_PRIVATE)
        val savedChannel = prefs.getString("channel", "s25_sub_e2787140") ?: "s25_sub_e2787140"
        val savedSize = prefs.getFloat("ko_size", 20f)
        val savedDelay = prefs.getInt("delay_ms", 0)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0D0D0D"))
            setPadding(56, 80, 56, 56)
        }

        val title = TextView(this).apply {
            text = "📱 갤럭시 S25 투명 자막 오버레이"
            textSize = 21f
            setTextColor(Color.parseColor("#00FF7F"))
            setTypeface(null, Typeface.BOLD)
        }
        root.addView(title)

        val desc = TextView(this).apply {
            text = "시커먼 PIP 상자 없이 유튜브·트위치 앱 위에 글자만 투명하게 뜨며, 자막 위를 눌러도 터치가 그대로 통과됩니다."
            textSize = 13f
            setTextColor(Color.parseColor("#AAAAAA"))
            setPadding(0, 16, 0, 40)
        }
        root.addView(desc)

        val chLabel = TextView(this).apply {
            text = "🔑 PC 직통 채널 ID (자동 고정됨)"
            textSize = 14f
            setTextColor(Color.parseColor("#FFE600"))
            setTypeface(null, Typeface.BOLD)
        }
        root.addView(chLabel)

        val chInput = EditText(this).apply {
            setText(savedChannel)
            textSize = 17f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#1E1E1E"))
            setPadding(32, 28, 32, 28)
        }
        root.addView(chInput)

        val sizeLabel = TextView(this).apply {
            text = "📏 한국어 자막 크기: ${savedSize.toInt()}sp"
            textSize = 14f
            setTextColor(Color.WHITE)
            setPadding(0, 36, 0, 12)
        }
        root.addView(sizeLabel)

        val sizeSeek = SeekBar(this).apply {
            max = 20
            progress = (savedSize.toInt() - 14).coerceIn(0, 20)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, f: Boolean) {
                    sizeLabel.text = "📏 한국어 자막 크기: ${14 + p}sp"
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }
        root.addView(sizeSeek)

        val delayLabel = TextView(this).apply {
            text = "⏱️ 폰-PC 영상 싱크 지연: ${savedDelay / 1000.0}초"
            textSize = 14f
            setTextColor(Color.WHITE)
            setPadding(0, 28, 0, 12)
        }
        root.addView(delayLabel)

        val delaySeek = SeekBar(this).apply {
            max = 50
            progress = (savedDelay / 100).coerceIn(0, 50)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, f: Boolean) {
                    delayLabel.text = "⏱️ 폰-PC 영상 싱크 지연: ${p / 10.0}초"
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }
        root.addView(delaySeek)

        val startBtn = Button(this).apply {
            text = "🚀 투명 자막 오버레이 시작"
            textSize = 16f
            setTextColor(Color.BLACK)
            setBackgroundColor(Color.parseColor("#00FF7F"))
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 34, 0, 34)
            setOnClickListener {
                if (!Settings.canDrawOverlays(this@MainActivity)) {
                    Toast.makeText(this@MainActivity, "먼저 '다른 앱 위에 표시' 권한을 허용해 주세요!", Toast.LENGTH_LONG).show()
                    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                    startActivity(intent)
                    return@setOnClickListener
                }
                val ch = chInput.text.toString().trim()
                val sz = (14 + sizeSeek.progress).toFloat()
                val dl = delaySeek.progress * 100
                prefs.edit().putString("channel", ch).putFloat("ko_size", sz).putInt("delay_ms", dl).apply()

                val svc = Intent(this@MainActivity, OverlayService::class.java).apply {
                    putExtra("channel", ch)
                    putExtra("ko_size", sz)
                    putExtra("delay_ms", dl)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(svc)
                } else {
                    startService(svc)
                }
                Toast.makeText(this@MainActivity, "투명 자막이 켜졌습니다! 이제 유튜브/트위치 앱을 실행하세요.", Toast.LENGTH_SHORT).show()
                moveTaskToBack(true)
            }
        }
        val lpStart = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = 56
        }
        root.addView(startBtn, lpStart)

        val stopBtn = Button(this).apply {
            text = "⏹ 자막 오버레이 끄기"
            textSize = 15f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#441111"))
            setPadding(0, 28, 0, 28)
            setOnClickListener {
                stopService(Intent(this@MainActivity, OverlayService::class.java))
                Toast.makeText(this@MainActivity, "자막 오버레이가 종료되었습니다.", Toast.LENGTH_SHORT).show()
            }
        }
        val lpStop = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = 24
        }
        root.addView(stopBtn, lpStop)

        setContentView(root)
    }
}
