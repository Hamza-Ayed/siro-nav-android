package com.siro.nav

import android.content.Context
import android.location.Location
import com.siro.nav.billing.NavFareConfig
import com.siro.nav.billing.NavTripMeter
import com.siro.nav.billing.NavTripTotals
import com.siro.nav.engine.NavEvent
import com.siro.nav.engine.NavigationEngine
import com.siro.nav.engine.NavigationEngineListener
import com.siro.nav.models.NavConfig
import com.siro.nav.models.NavPoint
import com.siro.nav.models.NavRoute
import com.siro.nav.models.SpeedCamera
import com.siro.nav.voice.NavVoiceEngine
import java.util.Date

interface SiroNavListener {
    fun onInstructionChanged(instruction: NavEvent.Instruction)
    fun onProgressUpdated(progress: NavEvent.Progress)
    fun onSpeedExceeded(currentSpeed: Double, speedLimit: Double)
    fun onCameraApproaching(camera: SpeedCamera, distance: Double)
    fun onTripArrived(totals: NavTripTotals)
    fun onError(error: String)
}

class SiroNavController(
    val context: Context,
    val config: NavConfig
) : NavigationEngineListener {

    val engine = NavigationEngine(config)
    val voiceEngine = NavVoiceEngine(context)

    var listener: SiroNavListener? = null
    var isNavigating: Boolean = false
        private set
    var lastTripTotals: NavTripTotals? = null
        private set
    var currentInstruction: NavEvent.Instruction? = null
        private set
    var currentProgress: NavEvent.Progress? = null
        private set
    var activeError: String? = null
        private set

    private var tripMeter: NavTripMeter? = null

    init {
        engine.listener = this
    }

    fun startNavigation(route: NavRoute, fare: NavFareConfig? = null): Boolean {
        tripMeter = fare?.let { NavTripMeter(it) }
        val started = engine.startNavigation(route)
        if (started) {
            isNavigating = true
            activeError = null
            voiceEngine.playPrompt("start_route")
        }
        return started
    }

    fun stopNavigation() {
        engine.stopNavigation()
        isNavigating = false
        voiceEngine.stop()
    }

    fun updateLocation(location: Location) {
        val point = NavPoint(
            latitude = location.latitude,
            longitude = location.longitude,
            heading = if (location.hasBearing()) location.bearing.toDouble() else 0.0,
            speedKmh = if (location.hasSpeed()) location.speed * 3.6 else 0.0,
            altitude = location.altitude,
            accuracy = if (location.hasAccuracy()) location.accuracy.toDouble() else 5.0,
            timestamp = Date(location.time)
        )
        engine.updateLocation(point)

        tripMeter?.let {
            lastTripTotals = it.addFix(point)
        }
    }

    override fun onEvent(event: NavEvent) {
        when (event) {
            is NavEvent.Instruction -> {
                currentInstruction = event
                listener?.onInstructionChanged(event)
                if (event.distanceMeters <= 50.0) {
                    if (event.maneuverType == 1) voiceEngine.playPrompt("now_turn_right")
                    else if (event.maneuverType == -1) voiceEngine.playPrompt("now_turn_left")
                } else if (event.distanceMeters <= 200.0) {
                    if (event.maneuverType == 1) voiceEngine.playPrompt("after_200m_turn_right")
                    else if (event.maneuverType == -1) voiceEngine.playPrompt("after_200m_turn_left")
                }
            }
            is NavEvent.Progress -> {
                currentProgress = event
                listener?.onProgressUpdated(event)
            }
            is NavEvent.SpeedLimitExceeded -> {
                listener?.onSpeedExceeded(event.currentSpeed, event.speedLimit)
                voiceEngine.playPrompt("slow_down")
            }
            is NavEvent.CameraWarning -> {
                listener?.onCameraApproaching(event.camera, event.distanceMeters)
                voiceEngine.playPrompt("fixed_radar_warning")
            }
            is NavEvent.Arrival -> {
                isNavigating = false
                val totals = tripMeter?.totals ?: NavTripTotals(
                    distanceKm = engine.cumulativeDistanceMeters / 1000.0,
                    waitingMinutes = 0.0,
                    fare = 0.0,
                    currency = "JOD"
                )
                lastTripTotals = totals
                listener?.onTripArrived(totals)
                voiceEngine.playPrompt("arrived_destination")
            }
            is NavEvent.AuthError -> {
                activeError = event.message
                listener?.onError(event.message)
            }
            is NavEvent.OffRoute -> {
                if (event.offRouteDurationSeconds == 0) {
                    voiceEngine.playPrompt("rerouting_notice")
                }
            }
            else -> {}
        }
    }
}
