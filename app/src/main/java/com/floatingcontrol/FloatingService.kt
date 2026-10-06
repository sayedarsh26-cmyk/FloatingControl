package com.floatingcontrol

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import kotlin.math.abs

class FloatingService : Service() {

    private lateinit var wm: WindowManager
    private lateinit var audio: AudioManager
    private lateinit var dot: View
    private lateinit var dotParams: WindowManager.LayoutParams
    private var panel: View? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        startForeground(1, buildNotification())
        addDot()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        super.onDestroy()
        hidePanel()
        if (::dot.isInitialized) {
            try { wm.removeView(dot) } catch (_: Exception) {}
        }
    }

    // ---------- Notification (needed for a service that keeps running) ----------

    private fun buildNotification(): Notification {
        val channelId = "floating_control"
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(channelId, "Floating Control", NotificationManager.IMPORTANCE_LOW)
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, channelId)
            .setContentTitle("Floating Control is running")
            .setContentText("Tap to open settings")
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }

    // ---------- Floating dot ----------

    private fun overlayType() = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY

    private fun addDot() {
        val size = dp(52)
        dot = View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#CC2196F3"))
                setStroke(dp(2), Color.WHITE)
            }
        }
        dotParams = WindowManager.LayoutParams(
            size, size, overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = dp(200)
        }

        var startX = 0
        var startY = 0
        var touchX = 0f
        var touchY = 0f
        var moved = false

        dot.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = dotParams.x
                    startY = dotParams.y
                    touchX = e.rawX
                    touchY = e.rawY
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (e.rawX - touchX).toInt()
                    val dy = (e.rawY - touchY).toInt()
                    if (abs(dx) > dp(6) || abs(dy) > dp(6)) moved = true
                    if (moved) {
                        dotParams.x = startX + dx
                        dotParams.y = startY + dy
                        wm.updateViewLayout(dot, dotParams)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) togglePanel()
                    true
                }
                else -> false
            }
        }
        wm.addView(dot, dotParams)
    }

    // ---------- Menu panel ----------

    private fun togglePanel() {
        if (panel == null) showPanel() else hidePanel()
    }

    private fun hidePanel() {
        panel?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        panel = null
    }

    private fun showPanel() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#F2222222"))
                cornerRadius = dp(18).toFloat()
            }
        }

        box.addView(controlRow("Volume", { changeVolume(AudioManager.ADJUST_LOWER) },
            { changeVolume(AudioManager.ADJUST_RAISE) }))
        box.addView(controlRow("Brightness", { changeBrightness(-25) },
            { changeBrightness(25) }))

        box.addView(actionButton("Lock phone") {
            hidePanel()
            lockPhone()
        })
        box.addView(actionButton("Close menu") { hidePanel() })

        val params = WindowManager.LayoutParams(
            dp(260),
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.CENTER }

        wm.addView(box, params)
        panel = box
    }

    private fun controlRow(label: String, onMinus: () -> Unit, onPlus: () -> Unit): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(4), 0, dp(4))
        }
        row.addView(TextView(this).apply {
            text = label
            textSize = 16f
            setTextColor(Color.WHITE)
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        row.addView(smallButton("-", onMinus))
        row.addView(smallButton("+", onPlus))
        return row
    }

    private fun smallButton(text: String, onClick: () -> Unit): Button =
        Button(this).apply {
            this.text = text
            textSize = 20f
            layoutParams = LinearLayout.LayoutParams(dp(64), dp(48)).apply { leftMargin = dp(8) }
            setOnClickListener { onClick() }
        }

    private fun actionButton(text: String, onClick: () -> Unit): Button =
        Button(this).apply {
            this.text = text
            isAllCaps = false
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8) }
            setOnClickListener { onClick() }
        }

    // ---------- Actions ----------

    private fun changeVolume(direction: Int) {
        audio.adjustStreamVolume(
            AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI
        )
    }

    private fun changeBrightness(delta: Int) {
        if (!Settings.System.canWrite(this)) {
            toast("Allow 'modify system settings' in the app first")
            return
        }
        val cr = contentResolver
        Settings.System.putInt(
            cr, Settings.System.SCREEN_BRIGHTNESS_MODE,
            Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
        )
        val current = Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS, 128)
        val next = (current + delta).coerceIn(10, 255)
        Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, next)
        toast("Brightness: " + (next * 100 / 255) + "%")
    }

    private fun lockPhone() {
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = ComponentName(this, LockAdminReceiver::class.java)
        if (dpm.isAdminActive(admin)) {
            dpm.lockNow()
        } else {
            toast("Allow 'lock phone' in the app first")
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
