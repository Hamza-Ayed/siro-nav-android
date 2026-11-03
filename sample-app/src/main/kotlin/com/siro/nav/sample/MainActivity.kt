package com.siro.nav.sample

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.siro.nav.SiroNavController
import com.siro.nav.billing.NavFareConfig
import com.siro.nav.engine.NavRouteSimulator
import com.siro.nav.models.NavConfig
import com.siro.nav.models.NavPoint
import com.siro.nav.services.RoutingClient
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

        // 1. Configure Developer Options (Language, Fare Visibility, Custom Currency)
        navView.locale = "ar"              // Options: "ar" or "en"
        navView.showFareMetrics = true      // Developer toggle for fare subpanel

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
            startLiveRoutingTrip()
        }
    }

    private fun startLiveRoutingTrip() {
        btnStartNav.isEnabled = false
        btnStartNav.text = "جاري حساب المسار الملتزم بالشبكة الطرقية..."

        // Amman route: 3rd Circle to Zahran 5th Circle
        val origin = NavPoint(latitude = 31.9539, longitude = 35.9106)
        val destination = NavPoint(latitude = 31.9650, longitude = 35.8900)

        lifecycleScope.launch {
            try {
                val routingClient = RoutingClient(navController.config)
                val liveRoute = routingClient.getRoute(origin, destination, navView.locale)

                controlPanel.visibility = View.GONE
                navView.visibility = View.VISIBLE
                btnStartNav.isEnabled = true
                btnStartNav.text = "بدء الرحلة التجريبية (Simulated Nav)"

                // Developer custom tariff rules (Dynamic currency & custom perKm / perMinute)
                val fare = NavFareConfig(
                    baseFare = 0.50,            // Base starting price
                    perKilometer = 0.35,        // Price per KM
                    perWaitingMinute = 0.05,    // Price per minute waiting
                    currency = "JOD",           // Can be JOD, SAR, USD, AED, etc.
                    waitingSpeedKmh = 5.0
                )

                navController.startNavigation(route = liveRoute, fare = fare)
                navView.setRoute(liveRoute)

                // Smooth simulation driving with heading
                val simulator = NavRouteSimulator(liveRoute, speedKmh = 45.0)
                val fixes = simulator.generateSimulatedFixes(stepIntervalSeconds = 0.35)

                simJob = launch {
                    for (fix in fixes) {
                        if (!navController.isNavigating) break
                        val loc = android.location.Location("siro_road_gps").apply {
                            latitude = fix.latitude
                            longitude = fix.longitude
                            bearing = fix.heading.toFloat()
                            speed = (fix.speedKmh / 3.6).toFloat()
                            altitude = fix.altitude
                            accuracy = 3.0f
                            time = System.currentTimeMillis()
                        }
                        navController.updateLocation(loc)
                        delay(350)
                    }
                }
            } catch (e: Exception) {
                btnStartNav.isEnabled = true
                btnStartNav.text = "إعادة المحاولة"
                Toast.makeText(this@MainActivity, "خطأ: ${e.message}", Toast.LENGTH_LONG).show()
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
