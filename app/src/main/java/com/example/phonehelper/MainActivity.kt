package com.example.phonehelper

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var finderSwitch: Switch
    private lateinit var counterSwitch: Switch
    private lateinit var todayText: TextView
    private lateinit var historyText: TextView

    private val handler = Handler(Looper.getMainLooper())
    private val refresh = object : Runnable {
        override fun run() {
            updateCounts()
            handler.postDelayed(this, 2000)
        }
    }

    private var ignoreSwitch = false
    private var pendingFinder = false
    private var pendingCounter = false

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun toBn(s: String): String =
        s.map { if (it in '0'..'9') '০' + (it - '0') else it }.joinToString("")

    private fun text(t: String, sp: Float, bold: Boolean = false, color: Int = Color.parseColor("#1F2937")) =
        TextView(this).apply {
            this.text = t
            textSize = sp
            setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(16), dp(16), dp(16))
        background = GradientDrawable().apply {
            cornerRadius = dp(16).toFloat()
            setColor(Color.parseColor("#F2F4F7"))
        }
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        )
        lp.topMargin = dp(16)
        layoutParams = lp
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(28), dp(20), dp(28))
        }
        val scroll = ScrollView(this)
        scroll.addView(root)
        setContentView(scroll)

        root.addView(text("ফোন সহায়ক", 28f, true))
        root.addView(text("ফোন হারালে খুঁজে দেয়, আর কতবার ফোন ধরলে তা গুনে রাখে।", 14f, false, Color.parseColor("#6B7280")))

        // ---------- ফোন ফাইন্ডার ----------
        val finderCard = card()
        finderCard.addView(text("ফোন কোথায় রাখলাম?", 20f, true))
        finderSwitch = Switch(this).apply {
            text = "ফোন ফাইন্ডার চালু রাখো"
            textSize = 16f
            isChecked = Prefs.finderOn(this@MainActivity)
            setPadding(0, dp(12), 0, dp(8))
        }
        finderSwitch.setOnCheckedChangeListener { _, on ->
            if (!ignoreSwitch) onFinderToggle(on)
        }
        finderCard.addView(finderSwitch)
        finderCard.addView(text("চালু থাকলে ফোন ঘরের শব্দ শোনে। ফোন সাইলেন্ট থাকলেও দুইবার জোরে হাততালি দিলে ফুল ভলিউমে বাজবে। বন্ধ করতে ফোন আনলক করো বা নোটিফিকেশনের \"বন্ধ করো\" চাপো।", 13f, false, Color.parseColor("#4B5563")))
        finderCard.addView(text("সংবেদনশীলতা (ডানে নিলে হালকা শব্দও ধরবে)", 13f, true).apply {
            setPadding(0, dp(14), 0, dp(4))
        })
        val seek = SeekBar(this).apply {
            max = 100
            progress = Prefs.sensitivity(this@MainActivity)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                    if (fromUser) Prefs.setSensitivity(this@MainActivity, p)
                }
                override fun onStartTrackingTouch(sb: SeekBar) {}
                override fun onStopTrackingTouch(sb: SeekBar) {}
            })
        }
        finderCard.addView(seek)
        root.addView(finderCard)

        // ---------- পিকআপ কাউন্টার ----------
        val counterCard = card()
        counterCard.addView(text("ফোন-পিকআপ কাউন্টার", 20f, true))
        counterSwitch = Switch(this).apply {
            text = "গণনা চালু রাখো"
            textSize = 16f
            isChecked = Prefs.counterOn(this@MainActivity)
            setPadding(0, dp(12), 0, dp(8))
        }
        counterSwitch.setOnCheckedChangeListener { _, on ->
            if (!ignoreSwitch) onCounterToggle(on)
        }
        counterCard.addView(counterSwitch)

        todayText = text("০", 56f, true, Color.parseColor("#2563EB")).apply {
            gravity = Gravity.CENTER
        }
        counterCard.addView(todayText)
        counterCard.addView(text("আজ এতবার ফোন আনলক করেছ", 14f).apply {
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(12))
        })
        historyText = text("", 14f, false, Color.parseColor("#4B5563"))
        counterCard.addView(historyText)
        counterCard.addView(text("সূত্র: প্রতিবার স্ক্রিন আনলক হলে একবার গোনা হয়। এটাই ফোন হাতে তোলার সবচেয়ে কাছাকাছি হিসাব।", 12f, false, Color.parseColor("#6B7280")).apply {
            setPadding(0, dp(10), 0, 0)
        })
        root.addView(counterCard)

        // ---------- সেটিংস ----------
        val help = card()
        help.addView(text("কাজ না করলে", 16f, true))
        help.addView(text("কিছু ফোন (Xiaomi, Vivo, Oppo ইত্যাদি) ব্যাকগ্রাউন্ড অ্যাপ বন্ধ করে দেয়। অ্যাপের সেটিংসে গিয়ে ব্যাটারি \"Unrestricted\" বা \"No restrictions\" করে দাও।", 13f, false, Color.parseColor("#4B5563")).apply {
            setPadding(0, dp(6), 0, dp(10))
        })
        help.addView(Button(this).apply {
            text = "অ্যাপ সেটিংস খোলো"
            setOnClickListener {
                startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
                )
            }
        })
        root.addView(help)

        updateCounts()
    }

    override fun onResume() {
        super.onResume()
        handler.post(refresh)
        // ফোন রিস্টার্ট বা সিস্টেম বন্ধ করে দিলে সার্ভিস আবার চালু
        if (Prefs.finderOn(this) && hasPermission(Manifest.permission.RECORD_AUDIO)) {
            startForegroundService(Intent(this, ClapService::class.java))
        }
        if (Prefs.counterOn(this)) {
            startForegroundService(Intent(this, PickupService::class.java))
        }
    }

    override fun onPause() {
        handler.removeCallbacks(refresh)
        super.onPause()
    }

    private fun hasPermission(p: String) =
        checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

    private fun notifPermissionMissing() =
        Build.VERSION.SDK_INT >= 33 && !hasPermission(Manifest.permission.POST_NOTIFICATIONS)

    private fun setChecked(s: Switch, v: Boolean) {
        ignoreSwitch = true
        s.isChecked = v
        ignoreSwitch = false
    }

    // ---------- ফোন ফাইন্ডার চালু/বন্ধ ----------
    private fun onFinderToggle(on: Boolean) {
        if (!on) {
            Prefs.setFinderOn(this, false)
            stopService(Intent(this, ClapService::class.java))
            return
        }
        val need = mutableListOf<String>()
        if (!hasPermission(Manifest.permission.RECORD_AUDIO)) need.add(Manifest.permission.RECORD_AUDIO)
        if (notifPermissionMissing()) need.add(Manifest.permission.POST_NOTIFICATIONS)
        if (need.isEmpty()) {
            startFinder()
        } else {
            pendingFinder = true
            requestPermissions(need.toTypedArray(), 1)
        }
    }

    private fun startFinder() {
        Prefs.setFinderOn(this, true)
        startForegroundService(Intent(this, ClapService::class.java))
        Toast.makeText(this, "ফোন ফাইন্ডার চালু হলো", Toast.LENGTH_SHORT).show()
    }

    // ---------- পিকআপ কাউন্টার চালু/বন্ধ ----------
    private fun onCounterToggle(on: Boolean) {
        if (!on) {
            Prefs.setCounterOn(this, false)
            stopService(Intent(this, PickupService::class.java))
            return
        }
        if (notifPermissionMissing()) {
            pendingCounter = true
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 2)
        } else {
            startCounter()
        }
    }

    private fun startCounter() {
        Prefs.setCounterOn(this, true)
        startForegroundService(Intent(this, PickupService::class.java))
        Toast.makeText(this, "পিকআপ কাউন্টার চালু হলো", Toast.LENGTH_SHORT).show()
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(code, perms, results)
        if (pendingFinder) {
            pendingFinder = false
            if (hasPermission(Manifest.permission.RECORD_AUDIO)) {
                startFinder()
            } else {
                setChecked(finderSwitch, false)
                Toast.makeText(this, "মাইক্রোফোনের অনুমতি ছাড়া ফাইন্ডার চলবে না", Toast.LENGTH_LONG).show()
            }
        }
        if (pendingCounter) {
            pendingCounter = false
            startCounter()
        }
    }

    private fun updateCounts() {
        todayText.text = toBn(Prefs.countFor(this, 0).toString())
        historyText.text = (1..6).joinToString("\n") {
            toBn("${Prefs.dayLabel(it)}   :   ${Prefs.countFor(this, it)}") + " বার"
        }
    }
}
