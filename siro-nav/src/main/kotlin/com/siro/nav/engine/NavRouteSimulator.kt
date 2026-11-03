package com.siro.nav.engine

import com.siro.nav.models.NavPoint
import com.siro.nav.models.NavRoute
import java.util.Date

class NavRouteSimulator(
    val route: NavRoute,
    val speedKmh: Double = 60.0
) {
    fun generateSimulatedFixes(stepIntervalSeconds: Double = 1.0): List<NavPoint> {
        if (route.polyline.size <= 1) return route.polyline
        val fixes = mutableListOf<NavPoint>()
        val speedMps = (speedKmh * 1000.0) / 3600.0
        val stepDist = speedMps * stepIntervalSeconds
        var baseTime = Date()

        for (i in 0 until route.polyline.size - 1) {
            val p1 = route.polyline[i]
            val p2 = route.polyline[i + 1]
            val segmentDist = p1.distanceTo(p2)
            val bearing = p1.bearingTo(p2)

            fixes.add(
                NavPoint(
                    latitude = p1.latitude,
                    longitude = p1.longitude,
                    heading = bearing,
                    speedKmh = speedKmh,
                    altitude = p1.altitude,
                    accuracy = 3.0,
                    timestamp = baseTime
                )
            )

            if (segmentDist > stepDist) {
                val steps = (segmentDist / stepDist).toInt()
                for (s in 1..steps) {
                    baseTime = Date(baseTime.time + (stepIntervalSeconds * 1000).toLong())
                    val frac = (s * stepDist) / segmentDist
                    val lat = p1.latitude + frac * (p2.latitude - p1.latitude)
                    val lng = p1.longitude + frac * (p2.longitude - p1.longitude)
                    fixes.add(
                        NavPoint(
                            latitude = lat,
                            longitude = lng,
                            heading = bearing,
                            speedKmh = speedKmh,
                            altitude = p1.altitude,
                            accuracy = 3.0,
                            timestamp = baseTime
                        )
                    )
                }
            }
            baseTime = Date(baseTime.time + (stepIntervalSeconds * 1000).toLong())
        }

        route.polyline.lastOrNull()?.let { last ->
            fixes.add(
                NavPoint(
                    latitude = last.latitude,
                    longitude = last.longitude,
                    heading = fixes.lastOrNull()?.heading ?: 0.0,
                    speedKmh = 0.0,
                    altitude = last.altitude,
                    accuracy = 3.0,
                    timestamp = baseTime
                )
            )
        }

        return fixes
    }
}
