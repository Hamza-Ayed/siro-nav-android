package com.siro.nav.models

import java.util.Date
import kotlin.math.*

data class NavPoint(
    val latitude: Double,
    val longitude: Double,
    val heading: Double = 0.0,
    val speedKmh: Double = 0.0,
    val altitude: Double = 0.0,
    val accuracy: Double = 5.0,
    val timestamp: Date = Date()
) {
    fun distanceTo(other: NavPoint): Double {
        val earthRadiusMeters = 6371000.0
        val dLat = Math.toRadians(other.latitude - latitude)
        val dLng = Math.toRadians(other.longitude - longitude)

        val lat1 = Math.toRadians(latitude)
        val lat2 = Math.toRadians(other.latitude)

        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(lat1) * cos(lat2) * sin(dLng / 2) * sin(dLng / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadiusMeters * c
    }

    fun bearingTo(other: NavPoint): Double {
        val lat1 = Math.toRadians(latitude)
        val lat2 = Math.toRadians(other.latitude)
        val dLng = Math.toRadians(other.longitude - longitude)

        val y = sin(dLng) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLng)
        val initialBearing = Math.toDegrees(atan2(y, x))
        return (initialBearing + 360.0) % 360.0
    }
}

data class NavConfig(
    val apiKey: String,
    val baseUrl: String = "https://map-saas.intaleqapp.com/api",
    val offRouteThresholdMeters: Double = 30.0,
    val offRouteDebounceSeconds: Int = 3,
    val overspeedToleranceKmh: Double = 5.0,
    val telemetryBatchSize: Int = 10
)

data class NavStep(
    val index: Int,
    val instruction: String,
    val streetName: String = "",
    val maneuverType: Int = 0,
    val exitNumber: Int? = null,
    val distanceMeters: Double = 0.0,
    val durationSeconds: Double = 0.0,
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val coordinateInterval: List<Int> = listOf(0, 0)
)

data class SpeedCamera(
    val id: String,
    val speedLimitKmh: Double,
    val lat: Double,
    val lng: Double
)

data class NavRoute(
    val id: String,
    val routeName: String = "",
    val polyline: List<NavPoint>,
    val steps: List<NavStep> = emptyList(),
    val speedCameras: List<SpeedCamera> = emptyList(),
    val totalDistanceMeters: Double,
    val totalDurationSeconds: Double
) {
    val isEmpty: Boolean get() = polyline.isEmpty()
    val isNotEmpty: Boolean get() = polyline.isNotEmpty()
}
