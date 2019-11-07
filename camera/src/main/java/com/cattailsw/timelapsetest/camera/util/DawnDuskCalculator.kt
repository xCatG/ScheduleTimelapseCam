package com.cattailsw.timelapsetest.camera.util

import ca.rmen.sunrisesunset.SunriseSunset
import java.util.*


class DawnDuskCalculator {

    /**
     * should do 1hr before sunrise and 1hr after sunset?
     */
    fun getRecordingSchedule(calendar: Calendar): RecScheduleData {
        val sunriseSunset = SunriseSunset.getCivilTwilight(calendar, 47.6062, -122.3321)
        val dawn = sunriseSunset[0]
        val dusk = sunriseSunset[1]


        val scheduleData = RecScheduleData(0, 0, 0, dusk.timeInMillis - dawn.timeInMillis, 0)
        scheduleData.startTimeInMillis = dawn.timeInMillis

        return scheduleData
    }
}