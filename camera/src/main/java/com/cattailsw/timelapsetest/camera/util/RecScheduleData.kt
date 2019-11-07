package com.cattailsw.timelapsetest.camera.util

import android.app.AlarmManager
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date

data class RecScheduleData @JvmOverloads constructor(
        val startHR: Int,
        val startMin: Int,
        val startSec: Int,
        val recordInterval: Long,
        val repeatInterval: Long = AlarmManager.INTERVAL_DAY, // use AlarmManger.INTERVAL*
        val recordingFPS: Float = 0.1f) {

    var startTimeInMillis: Long = 0

    val endTimeInMillis: Long
        get() = startTimeInMillis + recordInterval

    val startTimeString: String
        get() {
            val d = Date(startTimeInMillis)
            val sdf = SimpleDateFormat()
            return sdf.format(d)
        }

    init {

        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, startHR)
        calendar.set(Calendar.MINUTE, startMin)
        calendar.set(Calendar.SECOND, startSec)
        startTimeInMillis = calendar.timeInMillis
    }
}
