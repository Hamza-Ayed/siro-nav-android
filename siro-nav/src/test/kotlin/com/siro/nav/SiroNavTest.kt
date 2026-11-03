package com.siro.nav

import com.siro.nav.billing.NavFareConfig
import com.siro.nav.billing.NavTripMeter
import com.siro.nav.billing.NavTripTotals
import com.siro.nav.engine.*
import com.siro.nav.models.*
import org.junit.Assert.*
import org.junit.Test
import java.util.Date
import kotlin.math.roundToInt

class SiroNavTest {

    @Test
    fun testPolylineDecoding() {
        val points = PolylineCodec.decode("_p~iF~ps|U_ulLnnqC_mqNvxq`@")
        assertEquals(3, points.size)
        assertEquals(38.5, (points[0].latitude * 10.0).roundToInt() / 10.0, 0.001)
        assertEquals(-120.2, (points[0].longitude * 10.0).roundToInt() / 10.0, 0.001)
    }

    @Test
    fun testGeodesicDistanceAndBearing() {
        val p1 = NavPoint(latitude = 31.9539, longitude = 35.9106)
        val p2 = NavPoint(latitude = 31.9735, longitude = 35.9235)

        val dist = p1.distanceTo(p2)
        assertTrue("Distance should be between 2000m and 3000m", dist in 2000.0..3000.0)

        val bearing = p1.bearingTo(p2)
        assertTrue("Bearing should be between 0 and 90 degrees", bearing in 0.0..90.0)
    }

    @Test
    fun testMapMatcherSnapping() {
        val polyline = listOf(
            NavPoint(latitude = 31.9500, longitude = 35.9100),
            NavPoint(latitude = 31.9600, longitude = 35.9100),
            NavPoint(latitude = 31.9700, longitude = 35.9100)
        )

        val raw = NavPoint(latitude = 31.9550, longitude = 35.9105)
        val snapped = MapMatcher.snapToPolyline(rawPoint = raw, polyline = polyline)

        assertEquals(0, snapped.segmentIndex)
        assertTrue("Cross track distance should be around 40-50m", snapped.crossTrackDistanceMeters in 20.0..60.0)
        assertEquals(35.9100, snapped.snappedPoint.longitude, 0.0001)
    }

    @Test
    fun testApiKeyLicensingValidation() {
        val invalidEngine = NavigationEngine(NavConfig(apiKey = ""))
        val dummyRoute = NavRoute(
            id = "r1",
            polyline = listOf(NavPoint(latitude = 31.95, longitude = 35.91)),
            totalDistanceMeters = 100.0,
            totalDurationSeconds = 10.0
        )
        assertFalse(invalidEngine.startNavigation(dummyRoute))

        val validEngine = NavigationEngine(NavConfig(apiKey = "valid_commercial_key_123"))
        assertTrue(validEngine.startNavigation(dummyRoute))
    }

    @Test
    fun testTripMeterBillingCalculations() {
        val config = NavFareConfig(
            baseFare = 0.5,
            perKilometer = 0.35,
            perWaitingMinute = 0.05,
            currency = "JOD"
        )
        val meter = NavTripMeter(config)
        val t0 = Date()

        var lat = 31.9
        meter.addFix(NavPoint(latitude = lat, longitude = 35.9, speedKmh = 40.0, accuracy = 5.0, timestamp = t0))

        for (i in 1..20) {
            lat += 0.0005
            meter.addFix(NavPoint(latitude = lat, longitude = 35.9, speedKmh = 40.0, accuracy = 5.0, timestamp = Date(t0.time + i * 1000L)))
        }

        val totals = meter.totals
        assertEquals(1.1, totals.distanceKm, 0.08)
        assertEquals(0.0, totals.waitingMinutes, 0.001)
        assertTrue(totals.fare > 0.5)
        assertEquals("JOD", totals.currency)
    }

    @Test
    fun testSpeedCameraAlerting() {
        val tracker = CameraTracker()
        val cam = SpeedCamera(id = "cam_1", speedLimitKmh = 80.0, lat = 31.9550, lng = 35.9100)
        val pos = NavPoint(latitude = 31.9540, longitude = 35.9100, speedKmh = 95.0)

        val events = tracker.checkCameras(currentPosition = pos, cameras = listOf(cam), overspeedToleranceKmh = 5.0)
        assertTrue(events.isNotEmpty())
    }

    @Test
    fun testSimulatedTripFlow() {
        val poly = listOf(
            NavPoint(latitude = 31.9539, longitude = 35.9106),
            NavPoint(latitude = 31.9560, longitude = 35.9120),
            NavPoint(latitude = 31.9580, longitude = 35.9140)
        )
        val step1 = NavStep(
            index = 0,
            instruction = "اتجه نحو الشمال على شارع زهران",
            streetName = "شارع زهران",
            maneuverType = 0,
            distanceMeters = 400.0,
            durationSeconds = 30.0,
            lat = 31.9560,
            lng = 35.9120
        )
        val route = NavRoute(
            id = "route_amman_sample",
            routeName = "طريق تجريبي عمان",
            polyline = poly,
            steps = listOf(step1),
            totalDistanceMeters = 800.0,
            totalDurationSeconds = 60.0
        )

        val config = NavConfig(apiKey = "live_test_key_123")
        val engine = NavigationEngine(config)
        val tripMeter = NavTripMeter(NavFareConfig(baseFare = 0.50, perKilometer = 0.35, perWaitingMinute = 0.05, currency = "JOD"))

        var progressCount = 0
        var arrived = false
        var finalTotals: NavTripTotals? = null

        engine.listener = object : NavigationEngineListener {
            override fun onEvent(event: NavEvent) {
                when (event) {
                    is NavEvent.Progress -> progressCount++
                    is NavEvent.Arrival -> {
                        arrived = true
                        finalTotals = tripMeter.totals
                    }
                    else -> {}
                }
            }
        }

        assertTrue(engine.startNavigation(route))
        assertEquals(NavigationStatus.NAVIGATING, engine.status)

        val simulator = NavRouteSimulator(route, speedKmh = 60.0)
        val fixes = simulator.generateSimulatedFixes(stepIntervalSeconds = 0.5)
        assertTrue(fixes.size > 2)

        for (fix in fixes) {
            engine.updateLocation(fix)
            tripMeter.addFix(fix)
        }

        assertTrue(progressCount > 0)
        assertTrue(arrived)
        assertNotNull(finalTotals)
        assertTrue(finalTotals!!.distanceKm > 0.4)
        assertTrue(finalTotals!!.fare > 0.50)
    }
}
