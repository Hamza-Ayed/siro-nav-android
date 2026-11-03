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

        val origin = NavPoint(latitude = 31.9539, longitude = 35.9106)      // 3rd Circle Amman
        val destination = NavPoint(latitude = 31.9650, longitude = 35.8900) // 5th Circle / Zahran

        lifecycleScope.launch {
            try {
                val routingClient = RoutingClient(navController.config)
                val liveRoute = routingClient.getRoute(origin, destination, "ar")

                controlPanel.visibility = View.GONE
                navView.visibility = View.VISIBLE
                btnStartNav.isEnabled = true
                btnStartNav.text = "بدء الرحلة التجريبية (Simulated Nav)"

                // Tariff configuration with developer custom conditions
                val fare = NavFareConfig(
                    baseFare = 0.50,
                    perKilometer = 0.35,
                    perWaitingMinute = 0.05,
                    currency = "JOD"
                )

                navController.startNavigation(route = liveRoute, fare = fare)
                navView.setRoute(liveRoute)

                // Drive smoothly following true road curvature & headings
                val simulator = NavRouteSimulator(liveRoute, speedKmh = 45.0)
                val fixes = simulator.generateSimulatedFixes(stepIntervalSeconds = 0.4)

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
                        delay(400)
                    }
                }
            } catch (e: Exception) {
                btnStartNav.isEnabled = true
                btnStartNav.text = "إعادة المحاولة"
                Toast.makeText(this@MainActivity, "خطأ في حساب المسار: ${e.message}", Toast.LENGTH_LONG).show()
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
