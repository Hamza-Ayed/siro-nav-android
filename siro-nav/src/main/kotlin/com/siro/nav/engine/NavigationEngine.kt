package com.siro.nav.engine

import com.siro.nav.billing.TelemetryCollector
import com.siro.nav.models.*
import java.util.Date
import kotlin.math.max

enum class NavigationStatus {
    IDLE,
    AUTHENTICATING,
    NAVIGATING,
    REROUTING,
    ARRIVED,
    ERROR
}

interface NavigationEngineListener {
    fun onEvent(event: NavEvent)
}

class InstructionScheduler {
    private var lastAnnouncedDistance = Double.POSITIVE_INFINITY
    private var lastAnnouncedStepIndex = -1

    fun reset() {
        lastAnnouncedDistance = Double.POSITIVE_INFINITY
        lastAnnouncedStepIndex = -1
    }

    fun evaluate(currentStep: NavStep, distanceToStepMeters: Double, speedKmh: Double): NavEvent.Instruction? {
        if (currentStep.index != lastAnnouncedStepIndex) {
            lastAnnouncedStepIndex = currentStep.index
            lastAnnouncedDistance = distanceToStepMeters
            return NavEvent.Instruction(
                stepIndex = currentStep.index,
                instruction = currentStep.instruction,
                streetName = currentStep.streetName,
                maneuverType = currentStep.maneuverType,
                exitNumber = currentStep.exitNumber,
                distanceMeters = distanceToStepMeters,
                timeSeconds = if (currentStep.durationSeconds > 0) currentStep.durationSeconds.toInt() else 0,
                priority = 2
            )
        }

        val thresholds = if (speedKmh > 70.0) {
            doubleArrayOf(1000.0, 500.0, 200.0, 50.0)
        } else {
            doubleArrayOf(500.0, 200.0, 50.0)
        }

        for (threshold in thresholds) {
            if (distanceToStepMeters <= threshold && lastAnnouncedDistance > threshold) {
                lastAnnouncedDistance = distanceToStepMeters
                return NavEvent.Instruction(
                    stepIndex = currentStep.index,
                    instruction = currentStep.instruction,
                    streetName = currentStep.streetName,
                    maneuverType = currentStep.maneuverType,
                    exitNumber = currentStep.exitNumber,
                    distanceMeters = distanceToStepMeters,
                    timeSeconds = (distanceToStepMeters / max(5.0, (speedKmh / 3.6))).toInt(),
                    priority = if (threshold <= 50.0) 3 else 2
                )
            }
        }
        return null
    }
}

class CameraTracker {
    private val alertedCameraIds = mutableSetOf<String>()

    fun reset() {
        alertedCameraIds.clear()
    }

    fun checkCameras(
        currentPosition: NavPoint,
        cameras: List<SpeedCamera>,
        overspeedToleranceKmh: Double = 5.0
    ): List<NavEvent> {
        val events = mutableListOf<NavEvent>()
        for (cam in cameras) {
            val camPos = NavPoint(latitude = cam.lat, longitude = cam.lng)
            val dist = currentPosition.distanceTo(camPos)

            if (dist <= 500.0 && !alertedCameraIds.contains(cam.id)) {
                alertedCameraIds.add(cam.id)
                events.add(NavEvent.CameraWarning(cam, dist))
            }

            if (dist <= 300.0 && currentPosition.speedKmh > (cam.speedLimitKmh + overspeedToleranceKmh)) {
                events.add(NavEvent.SpeedLimitExceeded(currentPosition.speedKmh, cam.speedLimitKmh))
            }
        }
        return events
    }
}

class NavigationEngine(val config: NavConfig) {
    var listener: NavigationEngineListener? = null

    private val telemetryCollector = TelemetryCollector(config)
    private val instructionScheduler = InstructionScheduler()
    private val cameraTracker = CameraTracker()

    var status: NavigationStatus = NavigationStatus.IDLE
        private set
    var activeRoute: NavRoute? = null
        private set
    var currentStepIndex: Int = 0
        private set
    var lastKnownSegmentIndex: Int = 0
        private set
    var displayPoint: NavPoint? = null
        private set

    private var offRouteStartTime: Date? = null
    private var isRerouting = false

    val cumulativeDistanceMeters: Double
        get() = telemetryCollector.cumulativeDistanceMeters

    fun startNavigation(route: NavRoute): Boolean {
        status = NavigationStatus.AUTHENTICATING

        val trimmedKey = config.apiKey.trim()
        if (trimmedKey.isEmpty() || trimmedKey.contains(" ")) {
            status = NavigationStatus.ERROR
            listener?.onEvent(NavEvent.AuthError("ERR_INVALID_API_KEY", "Siro Maps API key is invalid or empty"))
            return false
        }

        if (route.isEmpty) {
            status = NavigationStatus.ERROR
            return false
        }

        activeRoute = route
        displayPoint = null
        currentStepIndex = 0
        lastKnownSegmentIndex = 0
        offRouteStartTime = null
        isRerouting = false
        instructionScheduler.reset()
        cameraTracker.reset()

        status = NavigationStatus.NAVIGATING

        route.steps.firstOrNull()?.let { firstStep ->
            listener?.onEvent(
                NavEvent.Instruction(
                    stepIndex = 0,
                    instruction = firstStep.instruction,
                    streetName = firstStep.streetName,
                    maneuverType = firstStep.maneuverType,
                    exitNumber = firstStep.exitNumber,
                    distanceMeters = firstStep.distanceMeters,
                    timeSeconds = firstStep.durationSeconds.toInt(),
                    priority = 2
                )
            )
        }

        return true
    }

    fun updateLocation(rawPoint: NavPoint) {
        if (status != NavigationStatus.NAVIGATING || activeRoute == null) return
        val route = activeRoute!!

        telemetryCollector.recordPoint(rawPoint)

        if (rawPoint.accuracy > 40.0) {
            listener?.onEvent(NavEvent.GpsSignalDegraded(rawPoint.accuracy))
        }

        val snapped = MapMatcher.snapToPolyline(
            rawPoint = rawPoint,
            polyline = route.polyline,
            lastKnownSegmentIndex = lastKnownSegmentIndex
        )
        lastKnownSegmentIndex = snapped.segmentIndex

        if (snapped.crossTrackDistanceMeters > config.offRouteThresholdMeters) {
            displayPoint = rawPoint
            handleOffRoute(rawPoint, snapped.crossTrackDistanceMeters)
            return
        }

        offRouteStartTime = null
        displayPoint = snapped.snappedPoint

        updateStepProgression(snapped.snappedPoint)

        // Arrival Check
        route.polyline.lastOrNull()?.let { dest ->
            if (snapped.snappedPoint.distanceTo(dest) < 25.0) {
                status = NavigationStatus.ARRIVED
                listener?.onEvent(NavEvent.Arrival(snapped.snappedPoint, telemetryCollector.cumulativeDistanceMeters))
                stopNavigation()
                return
            }
        }

        val remainingDist = max(0.0, route.totalDistanceMeters - snapped.distanceAlongRouteMeters)
        val fraction = if (route.totalDistanceMeters > 0) {
            (snapped.distanceAlongRouteMeters / route.totalDistanceMeters).coerceIn(0.0, 1.0)
        } else 1.0

        val currentStep = if (currentStepIndex < route.steps.size) route.steps[currentStepIndex] else route.steps.lastOrNull()
        val distToManeuver = currentStep?.let {
            snapped.snappedPoint.distanceTo(NavPoint(latitude = it.lat, longitude = it.lng))
        } ?: remainingDist

        listener?.onEvent(
            NavEvent.Progress(
                rawPosition = rawPoint,
                snappedPosition = snapped.snappedPoint,
                distanceToNextManeuverMeters = distToManeuver,
                remainingDistanceMeters = remainingDist,
                remainingDurationSeconds = (remainingDist / 11.0).toInt(),
                currentSpeedKmh = rawPoint.speedKmh,
                currentStepIndex = currentStepIndex,
                progressFraction = fraction
            )
        )

        currentStep?.let { step ->
            instructionScheduler.evaluate(step, distToManeuver, rawPoint.speedKmh)?.let { instEv ->
                listener?.onEvent(instEv)
            }
        }

        val camEvents = cameraTracker.checkCameras(
            currentPosition = rawPoint,
            cameras = route.speedCameras,
            overspeedToleranceKmh = config.overspeedToleranceKmh
        )
        for (ev in camEvents) {
            listener?.onEvent(ev)
        }
    }

    private fun handleOffRoute(rawPoint: NavPoint, deviationMeters: Double) {
        val now = Date()
        val start = offRouteStartTime ?: now.also { offRouteStartTime = it }
        val elapsed = ((now.time - start.time) / 1000).toInt()

        listener?.onEvent(NavEvent.OffRoute(rawPoint, deviationMeters, elapsed))

        if (elapsed >= config.offRouteDebounceSeconds && !isRerouting) {
            isRerouting = true
            status = NavigationStatus.REROUTING
            listener?.onEvent(NavEvent.RerouteTriggered(rawPoint, "continuous_deviation_detected"))
        }
    }

    private fun updateStepProgression(snappedPoint: NavPoint) {
        val route = activeRoute ?: return
        if (route.steps.isEmpty()) return

        while (currentStepIndex < route.steps.size - 1) {
            val step = route.steps[currentStepIndex]
            val target = NavPoint(latitude = step.lat, longitude = step.lng)
            if (snappedPoint.distanceTo(target) < 25.0) {
                currentStepIndex++
                val nextStep = route.steps[currentStepIndex]
                listener?.onEvent(
                    NavEvent.Instruction(
                        stepIndex = currentStepIndex,
                        instruction = nextStep.instruction,
                        streetName = nextStep.streetName,
                        maneuverType = nextStep.maneuverType,
                        exitNumber = nextStep.exitNumber,
                        distanceMeters = nextStep.distanceMeters,
                        timeSeconds = nextStep.durationSeconds.toInt(),
                        priority = 2
                    )
                )
            } else {
                break
            }
        }
    }

    fun updateRouteAfterReroute(newRoute: NavRoute) {
        activeRoute = newRoute
        currentStepIndex = 0
        lastKnownSegmentIndex = 0
        offRouteStartTime = null
        isRerouting = false
        status = NavigationStatus.NAVIGATING
        instructionScheduler.reset()
        listener?.onEvent(NavEvent.RerouteSuccess(newRoute))
    }

    fun stopNavigation() {
        status = NavigationStatus.IDLE
        displayPoint = null
        telemetryCollector.flush()
    }
}
