package com.mongodb.housekeeping

import com.mongodb.housekeeping.model.RateConfig
import com.mongodb.housekeeping.model.ServerStatus
import com.mongodb.housekeeping.model.WindowConfig
import kotlinx.datetime.LocalDateTime

fun List<RateConfig>.calculateRate(opcounters: ServerStatus.Opcounters, defaultRate: Int = 0): Int =
    filter { rate -> rate.criteria.any { it.accept(opcounters) } }
        .minOfOrNull { it.rate } ?: defaultRate

fun RateConfig.MetricThreshold.accept(opcounters: ServerStatus.Opcounters): Boolean {
    val metric = opcounters.getByKey(metric)
    return metric < max && metric >= min
}

fun List<WindowConfig>.accept(dt: LocalDateTime) =
    any { dt.dayOfWeek in it.days && dt.time >= it.from && dt.time < it.to }