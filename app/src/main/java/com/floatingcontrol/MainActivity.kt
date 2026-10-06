package com.floatingcontrol

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pad = dp(20)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        root.addView(TextView(this).apply {
            text = "Floating Control"
            textSize = 26f
            setTextColor(0xFF111111.toInt())
        })

        root.addView(TextView(this).apply {
            text = "Do steps 1, 2 and 3 once. Then press Start."
            textSize = 15f
            setPadding(0, dp(8), 0, dp(16))
        })

        root.addView(makeButton("1. Allow display over other apps") {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
        })

        root.addView(makeButton("2. Allow modify system settings (brightness)") {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_WRITE_SETTINGS,
                    Uri.parse("package:$packageName")
                )
            )
        })

        root.addView(makeButton("3. Allow lock phone (device admin)") {
            val admin = ComponentName(this, LockAdminReceiver::class.java)
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin)
                putExtra(
                    DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    "Needed so the floating button can lock your screen."
                )
            }
            startActivity(intent)
        })

        root.addView(makeButton("START floating dot") {
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
        })

        root.addView(makeButton("STOP floating dot") {
            stopService(Intent(this, FloatingService::class.java))
        })

        statusText = TextView(this).apply {
            textSize = 15f
            setPadding(0, dp(20), 0, 0)
        }
        root.addView(statusText)

        setContentView(ScrollView(this).apply { addView(root) })
    }

    override fun onResume() {
        super.onResume()
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = ComponentName(this, LockAdminReceiver::class.java)
        fun mark(ok: Boolean) = if (ok) "DONE" else "NOT DONE"
        statusText.text =
            "Step 1 (overlay): " + mark(Settings.canDrawOverlays(this)) + "\n" +
            "Step 2 (brightness): " + mark(Settings.System.canWrite(this)) + "\n" +
            "Step 3 (lock): " + mark(dpm.isAdminActive(admin))
    }

    private fun makeButton(label: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            text = label
            isAllCaps = false
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(10) }
            setOnClickListener { _: View -> onClick() }
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
