package com.example.phonehelper

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.Icon
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import kotlin.math.abs

/**
 * মাইক্রোফোনে শব্দ শুনে দুইবার জোরে হাততালি ধরলে ফুল ভলিউমে অ্যালার্ম বাজায়।
 */
class ClapService : Service() {

    companion object {
        const val ACTION_STOP_ALARM = "com.example.phonehelper.STOP_ALARM"
        private const val CHANNEL_ID = "clap_channel"
        private const val NOTIF_ID = 101
        private const val SAMPLE_RATE = 16000
        private const val ALARM_MAX_MS = 90_000L
    }

    @Volatile private var running = false
    @Volatile private var ringing = false
    private var worker: Thread? = null
    private var player: MediaPlayer? = null
    private var savedVolume = -1
    private var unlockRegistered = false
    private val handler = Handler(Looper.getMainLooper())

    private val unlockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            stopAlarm()
        }
    }

    private val autoStop = Runnable { stopAlarm() }

    override fun onCreate() {
        super.onCreate()
        val ch = NotificationChannel(CHANNEL_ID, "ফোন ফাইন্ডার", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            startForeground(NOTIF_ID, buildNotification(ringing))
        } catch (e: Exception) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_STOP_ALARM) {
            stopAlarm()
            return START_STICKY
        }
        if (!running) {
            running = true
            worker = Thread { listen() }.also { it.start() }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        running = false
        stopAlarm()
        super.onDestroy()
    }

    private fun buildNotification(alarm: Boolean): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val b = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle(if (alarm) "ফোন এখানে!" else "ফোন ফাইন্ডার চালু")
            .setContentText(
                if (alarm) "অ্যালার্ম বন্ধ করতে বাটন চাপো বা ফোন আনলক করো"
                else "দুইবার জোরে হাততালি দিলে ফোন বাজবে"
            )
            .setOngoing(true)
            .setContentIntent(open)
        if (alarm) {
            val stop = PendingIntent.getService(
                this, 1,
                Intent(this, ClapService::class.java).setAction(ACTION_STOP_ALARM),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            b.addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(this, android.R.drawable.ic_media_pause),
                    "বন্ধ করো",
                    stop
                ).build()
            )
        }
        return b.build()
    }

    private fun updateNotification() {
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification(ringing))
    }

    /** সংবেদনশীলতা 0..100 থেকে শব্দের সীমা (threshold) বের করা */
    private fun thresholdFor(sens: Int): Int = 26000 - sens * 180

    private fun listen() {
        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val bufSize = maxOf(minBuf, SAMPLE_RATE / 10 * 2)
        val rec: AudioRecord? = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC, SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufSize
            )
        } catch (e: Exception) {
            null
        }
        if (rec == null || rec.state != AudioRecord.STATE_INITIALIZED) {
            running = false
            handler.post { stopSelf() }
            return
        }

        try {
            rec.startRecording()
            val buf = ShortArray(SAMPLE_RATE / 20) // প্রায় ৫০ মিলিসেকেন্ড
            var lastClap = 0L
            var armed = true
            while (running) {
                val n = rec.read(buf, 0, buf.size)
                if (n <= 0 || ringing) continue

                var peak = 0
                for (i in 0 until n) {
                    val a = abs(buf[i].toInt())
                    if (a > peak) peak = a
                }

                val threshold = thresholdFor(Prefs.sensitivity(this))
                val now = SystemClock.elapsedRealtime()
                if (peak > threshold) {
                    if (armed && now - lastClap > 120) {
                        if (lastClap != 0L && now - lastClap < 1200) {
                            lastClap = 0L
                            handler.post { startAlarm() }
                        } else {
                            lastClap = now
                        }
                    }
                    armed = false
                } else if (peak < threshold / 2) {
                    armed = true
                }
            }
        } finally {
            try { rec.stop() } catch (e: Exception) { }
            rec.release()
            running = false
        }
    }

    private fun startAlarm() {
        if (ringing) return
        ringing = true
        try {
            val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            try {
                savedVolume = am.getStreamVolume(AudioManager.STREAM_ALARM)
                am.setStreamVolume(
                    AudioManager.STREAM_ALARM, am.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0
                )
            } catch (e: Exception) {
                // কিছু ফোনে "বিরক্ত করবেন না" মোডে ভলিউম বদলানো যায় না
            }

            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(this@ClapService, uri)
                isLooping = true
                prepare()
                start()
            }
            vibrate(true)

            val f = IntentFilter(Intent.ACTION_USER_PRESENT)
            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(unlockReceiver, f, Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(unlockReceiver, f)
            }
            unlockRegistered = true

            updateNotification()
            handler.postDelayed(autoStop, ALARM_MAX_MS)
        } catch (e: Exception) {
            stopAlarm()
        }
    }

    private fun stopAlarm() {
        val wasRinging = ringing
        ringing = false
        handler.removeCallbacks(autoStop)
        try { player?.stop() } catch (e: Exception) { }
        try { player?.release() } catch (e: Exception) { }
        player = null
        vibrate(false)
        if (savedVolume >= 0) {
            try {
                val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                am.setStreamVolume(AudioManager.STREAM_ALARM, savedVolume, 0)
            } catch (e: Exception) { }
            savedVolume = -1
        }
        if (unlockRegistered) {
            try { unregisterReceiver(unlockReceiver) } catch (e: Exception) { }
            unlockRegistered = false
        }
        if (wasRinging) {
            try { updateNotification() } catch (e: Exception) { }
        }
    }

    @Suppress("DEPRECATION")
    private fun vibrate(on: Boolean) {
        val v = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
        if (on) {
            v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 700, 400), 0))
        } else {
            v.cancel()
        }
    }
}
