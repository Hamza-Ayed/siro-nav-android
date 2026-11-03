package com.siro.nav.billing

import com.siro.nav.models.NavConfig
import com.siro.nav.models.NavPoint

data class NavFareConfig(
    val baseFare: Double = 0.0,
    val perKilometer: Double,
    val perWaitingMinute: Double,
    val currency: String = "JOD",
    val waitingSpeedKmh: Double = 5.0
)

data class NavTripTotals(
    val distanceKm: Double,
    val waitingMinutes: Double,
    val fare: Double,
    val currency: String
) {
    val fareLabel: String
        get() = String.format("%.2f %s", fare, currency)
}

class NavTripMeter(val config: NavFareConfig) {
    private val maxAccuracyMeters = 50.0
    private val countStepMeters = 10.0
    private val maxGapMillis = 30000L

    private var anchor: NavPoint? = null
    private var last: NavPoint? = null
    private var distanceMeters = 0.0
    private var waitingSeconds = 0.0

    fun addFix(fix: NavPoint): NavTripTotals {
        if (fix.accuracy > maxAccuracyMeters) return totals

        val prevLast = last
        val prevAnchor = anchor
        if (prevLast == null || prevAnchor == null) {
            last = fix
            anchor = fix
            return totals
        }

        last = fix
        val elapsedMillis = fix.timestamp.time - prevLast.timestamp.time
        if (elapsedMillis <= 0) return totals

        if (elapsedMillis > maxGapMillis) {
            anchor = fix
            return totals
        }

        val moved = prevAnchor.distanceTo(fix)
        if (moved >= countStepMeters) {
            distanceMeters += moved
            anchor = fix
        } else if (fix.speedKmh < config.waitingSpeedKmh) {
            waitingSeconds += elapsedMillis / 1000.0
        }

        return totals
    }

    val totals: NavTripTotals
        get() {
            val km = distanceMeters / 1000.0
            val minutes = waitingSeconds / 60.0
            val fare = config.baseFare + (config.perKilometer * km) + (config.perWaitingMinute * minutes)
            return NavTripTotals(
                distanceKm = km,
                waitingMinutes = minutes,
                fare = fare,
                currency = config.currency
            )
        }
}

class TelemetryCollector(val config: NavConfig) {
    private val buffer = mutableListOf<NavPoint>()
    private var cumulativeDistance = 0.0
    private var lastRecordedPoint: NavPoint? = null

    val bufferedCount: Int get() = buffer.size
    val cumulativeDistanceMeters: Double get() = cumulativeDistance

    fun recordPoint(point: NavPoint) {
        lastRecordedPoint?.let {
            cumulativeDistance += it.distanceTo(point)
        }
        lastRecordedPoint = point
        buffer.add(point)

        if (buffer.size >= config.telemetryBatchSize) {
            flush()
        }
    }

    fun flush() {
        buffer.clear()
    }
}
