package com.example.phonehelper

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * ফোন রিস্টার্ট হলে পিকআপ কাউন্টার নিজে থেকে আবার চালু করে।
 * (ফোন ফাইন্ডার মাইক্রোফোন ব্যবহার করে, তাই রিস্টার্টের পর একবার অ্যাপ খুলতে হয়।)
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED && Prefs.counterOn(context)) {
            try {
                context.startForegroundService(Intent(context, PickupService::class.java))
            } catch (e: Exception) {
                // অ্যাপ খুললে আবার চালু হবে
            }
        }
    }
}
