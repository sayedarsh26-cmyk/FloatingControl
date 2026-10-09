package com.floatingcontrol

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private val bg = Color.parseColor("#F4F6FB")
    private val ink = Color.parseColor("#1B2033")
    private val muted = Color.parseColor("#6B7385")
    private val primary = Color.parseColor("#3B5BDB")

    private lateinit var overlayChip: TextView
    private lateinit var writeChip: TextView
    private lateinit var lockChip: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = bg
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(28), dp(20), dp(28))
        }

        root.addView(TextView(this).apply {
            text = "Floating Control"
            textSize = 28f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(ink)
        })
        root.addView(TextView(this).apply {
            text = "Volume, brightness and lock, from one floating dot."
            textSize = 14f
            setTextColor(muted)
            setPadding(0, dp(4), 0, dp(24))
        })

        root.addView(sectionLabel("SET UP ONCE"))

        val c1 = stepCard(
            "1", "Display over other apps",
            "Lets the dot appear on top of every app."
        ) {
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            )
        }
        overlayChip = c1.second
        root.addView(c1.first)

        val c2 = stepCard(
            "2", "Change brightness",
            "Allows the menu to raise and lower brightness."
        ) {
            startActivity(
                Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:$packageName"))
            )
        }
        writeChip = c2.second
        root.addView(c2.first)

        val c3 = stepCard(
            "3", "Lock with fingerprint",
            "Turn on Floating Control in Accessibility so the phone locks like the power button."
        ) {
            Toast.makeText(
                this, "Find 'Floating Control' in the list and turn it ON", Toast.LENGTH_LONG
            ).show()
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        lockChip = c3.second
        root.addView(c3.first)

        root.addView(sectionLabel("CONTROL").apply { setPadding(0, dp(22), 0, dp(8)) })

        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        buttons.addView(
            pillButton("Start", primary, Color.WHITE) {
                if (!Settings.canDrawOverlays(this)) {
                    Toast.makeText(this, "Do step 1 first", Toast.LENGTH_LONG).show()
                } else {
                    val svc = Intent(this, FloatingService::class.java)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(svc)
                    } else {
                        startService(svc)
                    }
                    Toast.makeText(this, "Dot started", Toast.LENGTH_SHORT).show()
                }
            },
            LinearLayout.LayoutParams(0, dp(52), 1f).apply { rightMargin = dp(6) }
        )
        buttons.addView(
            pillButton("Stop", Color.parseColor("#E3E7F0"), ink) {
                stopService(Intent(this, FloatingService::class.java))
                Toast.makeText(this, "Dot stopped", Toast.LENGTH_SHORT).show()
            },
            LinearLayout.LayoutParams(0, dp(52), 1f).apply { leftMargin = dp(6) }
        )
        root.addView(buttons)

        root.addView(TextView(this).apply {
            text = "Tap the dot to open the menu. Drag it to move it. " +
                "Tap anywhere outside the menu to close it."
            textSize = 13f
            setTextColor(muted)
            setPadding(dp(2), dp(20), dp(2), 0)
        })

        setContentView(ScrollView(this).apply {
            setBackgroundColor(bg)
            isFillViewport = true
            addView(root)
        })
    }

    override fun onResume() {
        super.onResume()
        setChip(overlayChip, Settings.canDrawOverlays(this))
        setChip(writeChip, Settings.System.canWrite(this))
        setChip(lockChip, LockAccessibilityService.instance != null)
    }

    // ---------- UI helpers ----------

    private fun sectionLabel(text: String) = TextView(this).apply {
        this.text = text
        textSize = 12f
        typeface = Typeface.DEFAULT_BOLD
        letterSpacing = 0.1f
        setTextColor(muted)
        setPadding(0, 0, 0, dp(8))
    }

    private fun stepCard(
        number: String, title: String, desc: String, onClick: () -> Unit
    ): Pair<View, TextView> {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = RippleDrawable(
                ColorStateList.valueOf(Color.parseColor("#22000000")),
                GradientDrawable().apply {
                    setColor(Color.WHITE)
                    cornerRadius = dp(18).toFloat()
                },
                GradientDrawable().apply {
                    setColor(Color.WHITE)
                    cornerRadius = dp(18).toFloat()
                }
            )
            elevation = dp(2).toFloat()
            isClickable = true
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(10) }
        }

        card.addView(TextView(this).apply {
            text = number
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(primary)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#E7ECFF"))
            }
            layoutParams = LinearLayout.LayoutParams(dp(34), dp(34)).apply { rightMargin = dp(14) }
        })

        val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(TextView(this).apply {
            text = title
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(ink)
        })
        texts.addView(TextView(this).apply {
            text = desc
            textSize = 13f
            setTextColor(muted)
            setPadding(0, dp(2), 0, 0)
        })
        card.addView(texts, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        val chip = TextView(this).apply {
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(5), dp(10), dp(5))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { leftMargin = dp(10) }
        }
        card.addView(chip)
        return Pair(card, chip)
    }

    private fun setChip(chip: TextView, ok: Boolean) {
        chip.text = if (ok) "DONE" else "TO DO"
        chip.setTextColor(Color.parseColor(if (ok) "#2B8A3E" else "#C92A2A"))
        chip.background = GradientDrawable().apply {
            setColor(Color.parseColor(if (ok) "#D3F9D8" else "#FFE3E3"))
            cornerRadius = dp(20).toFloat()
        }
    }

    private fun pillButton(label: String, fill: Int, textColor: Int, onClick: () -> Unit) =
        TextView(this).apply {
            text = label
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(textColor)
            background = RippleDrawable(
                ColorStateList.valueOf(Color.parseColor("#33000000")),
                GradientDrawable().apply {
                    setColor(fill)
                    cornerRadius = dp(26).toFloat()
                },
                GradientDrawable().apply {
                    setColor(Color.WHITE)
                    cornerRadius = dp(26).toFloat()
                }
            )
            isClickable = true
            setOnClickListener { onClick() }
        }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
