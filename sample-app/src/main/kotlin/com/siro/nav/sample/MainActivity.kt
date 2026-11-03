package com.siro.nav.sample

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.siro.nav.SiroNavController
import com.siro.nav.SiroNavListener
import com.siro.nav.billing.NavFareConfig
import com.siro.nav.billing.NavTripTotals
import com.siro.nav.engine.NavEvent
import com.siro.nav.engine.NavRouteSimulator
import com.siro.nav.models.NavConfig
import com.siro.nav.models.NavPoint
import com.siro.nav.models.NavRoute
import com.siro.nav.models.NavStep
import com.siro.nav.models.SpeedCamera
import com.siro.nav.view.SiroNavView
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var navController: SiroNavController
    private lateinit var navView: SiroNavView
    private lateinit var controlPanel: View
    private lateinit var btnStartNav: Button

    private var simJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        controlPanel = findViewById(R.id.controlPanel)
        navView = findViewById(R.id.siroNavView)
        btnStartNav = findViewById(R.id.btnStartNav)

        // Initialize Siro Nav Controller
        val config = NavConfig(
            apiKey = "siro_live_commercial_eval_key",
            baseUrl = "https://map-saas.intaleqapp.com/api"
        )
        navController = SiroNavController(context = this, config = config)
        navView.bindController(navController)

        navView.onExitListener = {
            stopNavigation()
        }

        btnStartNav.setOnClickListener {
            startDemoTrip()
        }
    }

    private fun startDemoTrip() {
        controlPanel.visibility = View.GONE
        navView.visibility = View.VISIBLE

        // Realistic Route: Amman Zahran Street (3rd to 5th Circle)
        val polyline = listOf(
            NavPoint(latitude = 31.9539, longitude = 35.9106),
            NavPoint(latitude = 31.9555, longitude = 35.9075),
            NavPoint(latitude = 31.9575, longitude = 35.9035),
            NavPoint(latitude = 31.9600, longitude = 35.8995),
            NavPoint(latitude = 31.9625, longitude = 35.8950),
            NavPoint(latitude = 31.9650, longitude = 35.8900)
        )

        val steps = listOf(
            NavStep(
                index = 0,
                instruction = "اتجه نحو الدوار الخامس على شارع زهران",
                streetName = "شارع زهران",
                maneuverType = 0,
                distanceMeters = 800.0,
                durationSeconds = 60.0,
                lat = 31.9575,
                lng = 35.9035
            ),
            NavStep(
                index = 1,
                instruction = "انعطف يميناً باتجاه شارع الكندي",
                streetName = "شارع الكندي",
                maneuverType = 1,
                distanceMeters = 700.0,
                durationSeconds = 50.0,
                lat = 31.9650,
                lng = 35.8900
            )
        )

        val camera = SpeedCamera(
            id = "cam_zahran_1",
            speedLimitKmh = 60.0,
            lat = 31.9575,
            lng = 35.9035
        )

        val route = NavRoute(
            id = "zahran_demo_route",
            routeName = "شارع زهران إلى الدوار الخامس",
            polyline = polyline,
            steps = steps,
            speedCameras = listOf(camera),
            totalDistanceMeters = 1500.0,
            totalDurationSeconds = 110.0
        )

        val fare = NavFareConfig(
            baseFare = 0.50,
            perKilometer = 0.35,
            perWaitingMinute = 0.05,
            currency = "JOD"
        )

        navController.startNavigation(route = route, fare = fare)
        navView.setRoute(route)

        // Drive the simulator smoothly along the route
        val simulator = NavRouteSimulator(route, speedKmh = 45.0)
        val fixes = simulator.generateSimulatedFixes(stepIntervalSeconds = 0.5)

        simJob = lifecycleScope.launch {
            for (fix in fixes) {
                if (!navController.isNavigating) break
                val loc = android.location.Location("simulator").apply {
                    latitude = fix.latitude
                    longitude = fix.longitude
                    bearing = fix.heading.toFloat()
                    speed = (fix.speedKmh / 3.6).toFloat()
                    altitude = fix.altitude
                    accuracy = 3.0f
                    time = System.currentTimeMillis()
                }
                navController.updateLocation(loc)
                delay(500)
            }
        }
    }

    private fun stopNavigation() {
        simJob?.cancel()
        navController.stopNavigation()
        navView.visibility = View.GONE
        controlPanel.visibility = View.VISIBLE
    }

    override fun onDestroy() {
        super.onDestroy()
        simJob?.cancel()
        navController.stopNavigation()
        navView.onDestroy()
    }
}
