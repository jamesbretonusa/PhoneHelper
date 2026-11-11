package com.example.phonehelper

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object Prefs {
    private const val NAME = "phonehelper"

    private fun sp(c: Context) = c.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun finderOn(c: Context): Boolean = sp(c).getBoolean("finder_on", false)
    fun setFinderOn(c: Context, v: Boolean) = sp(c).edit().putBoolean("finder_on", v).apply()

    fun counterOn(c: Context): Boolean = sp(c).getBoolean("counter_on", false)
    fun setCounterOn(c: Context, v: Boolean) = sp(c).edit().putBoolean("counter_on", v).apply()

    /** 0..100, বেশি মানে হাততালি আরও সহজে ধরবে */
    fun sensitivity(c: Context): Int = sp(c).getInt("sens", 50)
    fun setSensitivity(c: Context, v: Int) = sp(c).edit().putInt("sens", v).apply()

    /** offset = 0 মানে আজ, 1 মানে গতকাল, ইত্যাদি */
    fun dayLabel(offset: Int): String {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -offset)
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
    }

    fun countFor(c: Context, offset: Int): Int = sp(c).getInt("count_" + dayLabel(offset), 0)

    fun increment(c: Context) {
        val key = "count_" + dayLabel(0)
        val p = sp(c)
        p.edit().putInt(key, p.getInt(key, 0) + 1).apply()
    }
}
