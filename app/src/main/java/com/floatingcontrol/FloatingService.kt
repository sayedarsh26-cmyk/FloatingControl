package com.floatingcontrol

import android.accessibilityservice.AccessibilityService
import android.animation.ValueAnimator
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.RippleDrawable
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
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
    private var snapAnim: ValueAnimator? = null

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
        snapAnim?.cancel()
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
        val size = dp(46)

        val outer = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(Color.parseColor("#E63B5BDB"), Color.parseColor("#E67048E8"))
        ).apply {
            shape = GradientDrawable.OVAL
            setStroke(dp(2), Color.parseColor("#99FFFFFF"))
        }
        val inner = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor("#E6FFFFFF"))
        }
        val layers = LayerDrawable(arrayOf<Drawable>(outer, inner))
        layers.setLayerInset(1, dp(15), dp(15), dp(15), dp(15))

        dot = View(this).apply {
            background = layers
            alpha = 0.9f
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
                    snapAnim?.cancel()
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
                    if (!moved) togglePanel() else snapToEdge(size)
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    if (moved) snapToEdge(size)
                    true
                }
                else -> false
            }
        }
        wm.addView(dot, dotParams)
    }

    /** After dragging, the dot glides to the nearest left/right edge (short, one-time animation). */
    private fun snapToEdge(size: Int) {
        val dm = resources.displayMetrics
        val target = if (dotParams.x + size / 2 < dm.widthPixels / 2) 0 else dm.widthPixels - size
        dotParams.y = dotParams.y.coerceIn(0, maxOf(0, dm.heightPixels - size))
        snapAnim?.cancel()
        snapAnim = ValueAnimator.ofInt(dotParams.x, target).apply {
            duration = 180
            addUpdateListener {
                dotParams.x = it.animatedValue as Int
                try { wm.updateViewLayout(dot, dotParams) } catch (_: Exception) {}
            }
            start()
        }
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
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(20))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#F21C2030"))
                cornerRadius = dp(26).toFloat()
            }
            isClickable = true // taps on the card must not close the menu
        }

        card.addView(TextView(this).apply {
            text = "Quick Controls"
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
        })
        card.addView(TextView(this).apply {
            text = "Tap outside to close"
            textSize = 12f
            setTextColor(Color.parseColor("#8B93A7"))
            setPadding(0, dp(2), 0, dp(10))
        })

        val volValue = valueText()
        val briValue = valueText()
        refreshVolume(volValue)
        refreshBrightness(briValue)

        card.addView(controlRow("🔊", "Volume", volValue,
            { changeVolume(AudioManager.ADJUST_LOWER); refreshVolume(volValue) },
            { changeVolume(AudioManager.ADJUST_RAISE); refreshVolume(volValue) }))

        card.addView(controlRow("☀️", "Brightness", briValue,
            { changeBrightness(-25); refreshBrightness(briValue) },
            { changeBrightness(25); refreshBrightness(briValue) }))

        card.addView(TextView(this).apply {
            text = "🔒  Lock phone"
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = ripple("#E03131", oval = false, radiusDp = 16)
            isClickable = true
            setOnClickListener {
                hidePanel()
                lockPhone()
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(50)
            ).apply { topMargin = dp(14) }
        })

        // Full-screen layer: tapping anywhere outside the card closes the menu.
        val scrim = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#55000000"))
            setOnClickListener { hidePanel() }
        }
        val cardWidth = minOf(dp(330), resources.displayMetrics.widthPixels - dp(40))
        scrim.addView(
            card,
            FrameLayout.LayoutParams(cardWidth, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER)
        )

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )

        wm.addView(scrim, params)
        panel = scrim
    }

    private fun valueText() = TextView(this).apply {
        textSize = 14f
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        setTextColor(Color.parseColor("#C9D1E3"))
    }

    private fun controlRow(
        icon: String, label: String, value: TextView,
        onMinus: () -> Unit, onPlus: () -> Unit
    ): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, dp(8))
        }
        row.addView(TextView(this).apply {
            text = "$icon  $label"
            textSize = 15f
            setTextColor(Color.WHITE)
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        row.addView(roundButton("−", onMinus))
        row.addView(value, LinearLayout.LayoutParams(dp(52), LinearLayout.LayoutParams.WRAP_CONTENT))
        row.addView(roundButton("+", onPlus))
        return row
    }

    private fun roundButton(label: String, onClick: () -> Unit): TextView =
        TextView(this).apply {
            text = label
            textSize = 22f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = ripple("#2C3348", oval = true)
            isClickable = true
            layoutParams = LinearLayout.LayoutParams(dp(42), dp(42))
            setOnClickListener { onClick() }
        }

    private fun ripple(fill: String, oval: Boolean, radiusDp: Int = 0): RippleDrawable {
        fun shapeOf(color: Int) = GradientDrawable().apply {
            if (oval) shape = GradientDrawable.OVAL else cornerRadius = dp(radiusDp).toFloat()
            setColor(color)
        }
        return RippleDrawable(
            ColorStateList.valueOf(Color.parseColor("#44FFFFFF")),
            shapeOf(Color.parseColor(fill)),
            shapeOf(Color.WHITE)
        )
    }

    // ---------- Actions ----------

    private fun refreshVolume(tv: TextView) {
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val cur = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        tv.text = (cur * 100 / max).toString() + "%"
    }

    private fun refreshBrightness(tv: TextView) {
        tv.text = if (Settings.System.canWrite(this)) {
            val v = Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128)
            (v * 100 / 255).toString() + "%"
        } else {
            "--"
        }
    }

    private fun changeVolume(direction: Int) {
        // No system volume bar: our own menu shows the value.
        audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, 0)
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
    }

    private fun lockPhone() {
        // Preferred: acts like the power button, so fingerprint still works.
        val svc = LockAccessibilityService.instance
        if (Build.VERSION.SDK_INT >= 28 && svc != null) {
            svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
            return
        }
        if (Build.VERSION.SDK_INT >= 28) {
            // Never fall back to device-admin lock here: it forces PIN instead of fingerprint.
            toast("Turn on Floating Control in Accessibility settings (step 3)")
            return
        }
        // Android 8 only: device admin is the only way (asks for PIN after locking).
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = ComponentName(this, LockAdminReceiver::class.java)
        if (dpm.isAdminActive(admin)) {
            dpm.lockNow()
        } else {
            toast("Allow lock phone in the app first")
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
