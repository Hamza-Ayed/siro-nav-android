package com.siro.nav.engine

import com.siro.nav.models.NavPoint
import com.siro.nav.models.NavRoute
import com.siro.nav.models.SpeedCamera

sealed class NavEvent {
    data class Instruction(
        val stepIndex: Int,
        val instruction: String,
        val streetName: String = "",
        val maneuverType: Int = 0,
        val exitNumber: Int? = null,
        val distanceMeters: Double = 0.0,
        val timeSeconds: Int = 0,
        val priority: Int = 2
    ) : NavEvent()

    data class Progress(
        val rawPosition: NavPoint,
        val snappedPosition: NavPoint,
        val distanceToNextManeuverMeters: Double,
        val remainingDistanceMeters: Double,
        val remainingDurationSeconds: Int,
        val currentSpeedKmh: Double,
        val currentStepIndex: Int,
        val progressFraction: Double
    ) : NavEvent()

    data class OffRoute(
        val currentPosition: NavPoint,
        val deviationMeters: Double,
        val offRouteDurationSeconds: Int
    ) : NavEvent()

    data class RerouteTriggered(
        val deviationPoint: NavPoint,
        val reason: String
    ) : NavEvent()

    data class RerouteSuccess(val newRoute: NavRoute) : NavEvent()
    data class SpeedLimitExceeded(val currentSpeed: Double, val speedLimit: Double) : NavEvent()
    data class CameraWarning(val camera: SpeedCamera, val distanceMeters: Double) : NavEvent()
    data class Arrival(val finalPosition: NavPoint, val totalDistanceMeters: Double) : NavEvent()
    data class AuthError(val code: String, val message: String) : NavEvent()
    data class GpsSignalDegraded(val currentAccuracyMeters: Double) : NavEvent()
}
