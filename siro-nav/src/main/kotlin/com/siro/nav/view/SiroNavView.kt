package com.siro.nav.view

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.siro.nav.SiroNavController
import com.siro.nav.SiroNavListener
import com.siro.nav.billing.NavTripTotals
import com.siro.nav.engine.NavEvent
import com.siro.nav.models.NavRoute
import com.siro.nav.models.SpeedCamera
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Premium Turn-by-Turn View on Siro Maps.
 * Matches Siro Limestone design:
 * - Real 3D Chase Camera tracking GPS Bearing/Heading
 * - Native Directional Chevron Arrow rotating in real-time with GPS Heading
 * - Floating Elevated Speedometer Badge positioned clearly above bottom bar
 * - Multi-language support (Arabic / English)
 * - Developer-controlled optional Fare & Distance panel: [انطلاق الرحلة | المسافة | الأجرة]
 */
class SiroNavView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr), SiroNavListener {

    private val webView = WebView(context)
    private var isMapLoaded = false

    // Options for developers
    var locale: String = "ar" // "ar" or "en"
    var showFareMetrics: Boolean = true
    var styleUrl: String = "https://map.intaleqapp.com/style.json"
    var onExitListener: (() -> Unit)? = null

    // UI Elements
    private val topInstructionCard = LinearLayout(context)
    private val maneuverIcon = TextView(context)
    private val distanceLabel = TextView(context)
    private val instructionLabel = TextView(context)
    private val streetLabel = TextView(context)

    private val speedBadgeContainer = LinearLayout(context)
    private val speedLabel = TextView(context)
    private val speedUnitLabel = TextView(context)

    private val bottomCard = LinearLayout(context)
    private val etaLabel = TextView(context)
    private val etaClockLabel = TextView(context)
    private val remainingDistLabel = TextView(context)
    private val fareMetricsContainer = LinearLayout(context)
    private val fareMetricsLabel = TextView(context)
    private val exitButton = FrameLayout(context)

    private var activeController: SiroNavController? = null

    init {
        setBackgroundColor(Color.parseColor("#0F172A"))
        setupMapWebView()
        setupPremiumHUD(context)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupMapWebView() {
        val params = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        webView.layoutParams = params
        addView(webView)

        val settings: WebSettings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.allowFileAccess = true
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true
        settings.cacheMode = WebSettings.LOAD_DEFAULT

        webView.setBackgroundColor(Color.parseColor("#0F172A"))
        webView.webChromeClient = WebChromeClient()
        webView.addJavascriptInterface(NavMapBridge(), "siroNavBridge")

        loadMapHtml()
    }

    private fun loadMapHtml() {
        val html = """
            <!DOCTYPE html>
            <html dir="${if (locale == "ar") "rtl" else "ltr"}">
            <head>
                <meta charset="utf-8" />
                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
                <title>Siro Maps Navigation</title>
                <script src="https://unpkg.com/maplibre-gl@4.7.1/dist/maplibre-gl.js"></script>
                <link href="https://unpkg.com/maplibre-gl@4.7.1/dist/maplibre-gl.css" rel="stylesheet" />
                <style>
                    html, body, #map { margin: 0; padding: 0; width: 100%; height: 100%; background: #0F172A; }
                    
                    /* Directional Vehicle Chevron Marker with High-Visibility Halo */
                    .vehicle-container {
                        width: 52px;
                        height: 52px;
                        display: flex;
                        align-items: center;
                        justify-content: center;
                        position: relative;
                        pointer-events: none;
                        z-index: 9999;
                    }
                    .vehicle-disc {
                        width: 40px;
                        height: 40px;
                        background: #FFFFFF;
                        border-radius: 50%;
                        box-shadow: 0 4px 14px rgba(0,0,0,0.5);
                        display: flex;
                        align-items: center;
                        justify-content: center;
                        border: 2.5px solid #FFFFFF;
                        position: relative;
                    }
                    .vehicle-arrow-svg {
                        width: 22px;
                        height: 22px;
                        display: block;
                    }
                </style>
            </head>
            <body>
                <div id="map"></div>
                <script>
                    if (maplibregl.getRTLTextPluginStatus() === 'unavailable') {
                        maplibregl.setRTLTextPlugin(
                            'https://unpkg.com/@mapbox/mapbox-gl-rtl-text@0.2.3/mapbox-gl-rtl-text.min.js',
                            null,
                            true
                        );
                    }

                    var map = new maplibregl.Map({
                        container: 'map',
                        style: '$styleUrl',
                        center: [35.9106, 31.9539],
                        zoom: 17.8,
                        pitch: 58.0,
                        bearing: 0,
                        attributionControl: false
                    });

                    var vehicleMarker = null;
                    var vehicleEl = null;

                    map.on('load', function() {
                        var icons = [
                            'badge-cafe', 'badge-restaurant', 'badge-fuel', 'badge-hospital',
                            'badge-pharmacy', 'badge-bank', 'badge-supermarket', 'arrow'
                        ];
                        icons.forEach(function(icon) {
                            var img = new Image(24, 24);
                            img.onload = function() {
                                if (!map.hasImage(icon)) {
                                    map.addImage(icon, img);
                                }
                            };
                            img.src = 'https://map.intaleqapp.com/assets/map_icons/' + icon + '.svg';
                        });

                        // Create Sharp SVG Navigation Arrow
                        vehicleEl = document.createElement('div');
                        vehicleEl.className = 'vehicle-container';
                        
                        var disc = document.createElement('div');
                        disc.className = 'vehicle-disc';
                        disc.innerHTML = '<svg class="vehicle-arrow-svg" viewBox="0 0 24 24" fill="none"><path d="M12 2L3 21L12 17L21 21L12 2Z" fill="#0071E3" stroke="#005bb5" stroke-width="1.5" stroke-linejoin="round"/></svg>';
                        
                        vehicleEl.appendChild(disc);

                        vehicleMarker = new maplibregl.Marker({ element: vehicleEl, rotationAlignment: 'map' })
                            .setLngLat([35.9106, 31.9539])
                            .addTo(map);

                        map.addSource('nav-route-source', {
                            type: 'geojson',
                            data: { type: 'Feature', geometry: { type: 'LineString', coordinates: [] } }
                        });

                        map.addLayer({
                            id: 'nav-route-casing',
                            type: 'line',
                            source: 'nav-route-source',
                            layout: { 'line-join': 'round', 'line-cap': 'round' },
                            paint: { 'line-color': '#FFFFFF', 'line-width': 10.0 }
                        });

                        map.addLayer({
                            id: 'nav-route-line',
                            type: 'line',
                            source: 'nav-route-source',
                            layout: { 'line-join': 'round', 'line-cap': 'round' },
                            paint: { 'line-color': '#0071E3', 'line-width': 6.5 }
                        });

                        if (window.siroNavBridge) {
                            window.siroNavBridge.onMapReady();
                        }
                    });

                    function setRoute(coordsJson) {
                        try {
                            var coords = JSON.parse(coordsJson);
                            var source = map.getSource('nav-route-source');
                            if (source) {
                                source.setData({
                                    type: 'Feature',
                                    geometry: { type: 'LineString', coordinates: coords }
                                });
                            }
                            if (coords.length > 0) {
                                updateVehiclePosition(coords[0][1], coords[0][0], 0);
                            }
                        } catch(e) {}
                    }

                    function updateVehiclePosition(lat, lng, heading) {
                        if (vehicleMarker) {
                            vehicleMarker.setLngLat([lng, lat]);
                            if (heading >= 0) {
                                vehicleMarker.setRotation(heading);
                            }
                        }
                        var targetBearing = heading >= 0 ? heading : map.getBearing();
                        map.easeTo({
                            center: [lng, lat],
                            bearing: targetBearing,
                            pitch: 58.0,
                            zoom: 17.8,
                            duration: 350
                        });
                    }
                </script>
            </body>
            </html>
        """.trimIndent()

        webView.loadDataWithBaseURL("https://map.intaleqapp.com", html, "text/html", "UTF-8", null)
    }

    private inner class NavMapBridge {
        @JavascriptInterface
        fun onMapReady() {
            post {
                isMapLoaded = true
                activeController?.activeRoute?.let { setRoute(it) }
            }
        }
    }

    private fun setupPremiumHUD(context: Context) {
        val dp = context.resources.displayMetrics.density

        // ── 1. Top Instruction Banner (Slate-900 with Cyan accents) ─────────
        topInstructionCard.apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((16 * dp).toInt(), (14 * dp).toInt(), (16 * dp).toInt(), (14 * dp).toInt())
            background = GradientDrawable().apply {
                setColor(Color.argb(245, 15, 23, 42)) // Slate 900
                cornerRadius = 20 * dp
                setStroke((1 * dp).toInt(), Color.argb(100, 51, 65, 85))
            }
            layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins((16 * dp).toInt(), (32 * dp).toInt(), (16 * dp).toInt(), 0)
                gravity = Gravity.TOP
            }
        }

        val iconBox = FrameLayout(context).apply {
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#0284C7")) // Sky blue 600
                cornerRadius = 14 * dp
            }
            layoutParams = LinearLayout.LayoutParams((46 * dp).toInt(), (46 * dp).toInt()).apply {
                setMargins(0, 0, (14 * dp).toInt(), 0)
            }
        }
        maneuverIcon.apply {
            text = "↱"
            setTextColor(Color.WHITE)
            textSize = 24f
            gravity = Gravity.CENTER
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        }
        iconBox.addView(maneuverIcon)
        topInstructionCard.addView(iconBox)

        val textDetailsLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f)
        }

        distanceLabel.apply {
            setTextColor(Color.parseColor("#38BDF8"))
            textSize = 19f
            typeface = Typeface.DEFAULT_BOLD
        }

        instructionLabel.apply {
            setTextColor(Color.WHITE)
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 2
            setPadding(0, (2 * dp).toInt(), 0, 0)
        }

        streetLabel.apply {
            setTextColor(Color.parseColor("#94A3B8"))
            textSize = 13f
            maxLines = 1
            visibility = GONE
        }

        textDetailsLayout.addView(distanceLabel)
        textDetailsLayout.addView(instructionLabel)
        textDetailsLayout.addView(streetLabel)
        topInstructionCard.addView(textDetailsLayout)
        addView(topInstructionCard)

        // ── 2. Speedometer Badge (Highly Elevated: 195dp above bottom) ──────
        speedBadgeContainer.apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = 28 * dp
                setStroke((3.5 * dp).toInt(), Color.parseColor("#EF4444"))
            }
            elevation = 14 * dp
            layoutParams = LayoutParams(
                (56 * dp).toInt(),
                (56 * dp).toInt()
            ).apply {
                // Generous vertical clearance from bottom card
                setMargins((18 * dp).toInt(), 0, 0, (195 * dp).toInt())
                gravity = Gravity.BOTTOM or Gravity.START
            }
        }

        speedLabel.apply {
            text = "0"
            setTextColor(Color.parseColor("#0F172A"))
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }

        speedUnitLabel.apply {
            text = if (locale == "ar") "كم/س" else "km/h"
            setTextColor(Color.parseColor("#64748B"))
            textSize = 9f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }

        speedBadgeContainer.addView(speedLabel)
        speedBadgeContainer.addView(speedUnitLabel)
        addView(speedBadgeContainer)

        // ── 3. Bottom Trip Status & Metrics Card ─────────────────────────────
        bottomCard.apply {
            orientation = LinearLayout.VERTICAL
            setPadding((18 * dp).toInt(), (14 * dp).toInt(), (18 * dp).toInt(), (14 * dp).toInt())
            background = GradientDrawable().apply {
                setColor(Color.argb(248, 15, 23, 42)) // Slate 900
                cornerRadius = 24 * dp
                setStroke((1 * dp).toInt(), Color.argb(100, 51, 65, 85))
            }
            elevation = 16 * dp
            layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins((14 * dp).toInt(), 0, (14 * dp).toInt(), (18 * dp).toInt())
                gravity = Gravity.BOTTOM
            }
        }

        val topRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        exitButton.apply {
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#334155"))
                shape = GradientDrawable.OVAL
                setStroke((1 * dp).toInt(), Color.parseColor("#475569"))
            }
            val closeIcon = TextView(context).apply {
                text = "✕"
                setTextColor(Color.parseColor("#EF4444"))
                textSize = 18f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            }
            addView(closeIcon)
            setOnClickListener { onExitListener?.invoke() }
            layoutParams = LinearLayout.LayoutParams((44 * dp).toInt(), (44 * dp).toInt()).apply {
                setMargins(0, 0, (14 * dp).toInt(), 0)
            }
        }
        topRow.addView(exitButton)

        val etaColumn = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f)
        }

        val etaTimeRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        etaLabel.apply {
            setTextColor(Color.parseColor("#10B981"))
            textSize = 21f
            typeface = Typeface.DEFAULT_BOLD
        }

        etaClockLabel.apply {
            setTextColor(Color.parseColor("#94A3B8"))
            textSize = 14f
            setPadding((8 * dp).toInt(), 0, 0, 0)
        }

        etaTimeRow.addView(etaLabel)
        etaTimeRow.addView(etaClockLabel)

        remainingDistLabel.apply {
            setTextColor(Color.argb(200, 255, 255, 255))
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, (2 * dp).toInt(), 0, 0)
        }

        etaColumn.addView(etaTimeRow)
        etaColumn.addView(remainingDistLabel)
        topRow.addView(etaColumn)
        bottomCard.addView(topRow)

        // ── 4. Developer Configured Metrics: [انطلاق الرحلة | المسافة | الأجرة]
        fareMetricsContainer.apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((12 * dp).toInt(), (8 * dp).toInt(), (12 * dp).toInt(), (8 * dp).toInt())
            background = GradientDrawable().apply {
                setColor(Color.argb(120, 30, 41, 59))
                cornerRadius = 12 * dp
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, (12 * dp).toInt(), 0, 0)
            }
            visibility = if (showFareMetrics) VISIBLE else GONE
        }

        fareMetricsLabel.apply {
            setTextColor(Color.parseColor("#FBBF24")) // Amber Gold
            textSize = 13.5f
            typeface = Typeface.DEFAULT_BOLD
        }
        fareMetricsContainer.addView(fareMetricsLabel)
        bottomCard.addView(fareMetricsContainer)

        addView(bottomCard)
    }

    fun bindController(controller: SiroNavController) {
        this.activeController = controller
        controller.listener = this
        controller.activeRoute?.let { setRoute(it) }
    }

    fun setRoute(route: NavRoute) {
        val coords = JSONArray()
        for (p in route.polyline) {
            val pointArr = JSONArray().apply {
                put(p.longitude)
                put(p.latitude)
            }
            coords.put(pointArr)
        }
        evaluateJs("setRoute('${coords.toString().replace("'", "\\'")}');")
    }

    fun updateVehicle(lat: Double, lng: Double, heading: Double) {
        evaluateJs("updateVehiclePosition($lat, $lng, $heading);")
    }

    override fun onInstructionChanged(instruction: NavEvent.Instruction) {
        post {
            instructionLabel.text = instruction.instruction
            distanceLabel.text = formatDistance(instruction.distanceMeters)
            if (instruction.streetName.isNotEmpty()) {
                streetLabel.visibility = VISIBLE
                streetLabel.text = instruction.streetName
            } else {
                streetLabel.visibility = GONE
            }
            maneuverIcon.text = when (instruction.maneuverType) {
                1 -> "↱"
                -1 -> "↰"
                -98 -> "⮌"
                else -> "↑"
            }
        }
    }

    override fun onProgressUpdated(progress: NavEvent.Progress) {
        post {
            // Speed
            speedLabel.text = progress.currentSpeedKmh.toInt().toString()

            // ETA & Clock
            val mins = (progress.remainingDurationSeconds / 60).coerceAtLeast(1)
            etaLabel.text = if (locale == "ar") "$mins دقيقة" else "$mins min"

            val cal = Calendar.getInstance().apply {
                add(Calendar.SECOND, progress.remainingDurationSeconds)
            }
            val hour = cal.get(Calendar.HOUR).let { if (it == 0) 12 else it }
            val minuteStr = String.format("%02d", cal.get(Calendar.MINUTE))
            val ampm = if (cal.get(Calendar.AM_PM) == Calendar.PM) {
                if (locale == "ar") "م" else "PM"
            } else {
                if (locale == "ar") "ص" else "AM"
            }
            etaClockLabel.text = "($hour:$minuteStr $ampm)"

            // Remaining Distance
            val km = progress.remainingDistanceMeters / 1000.0
            remainingDistLabel.text = if (locale == "ar") {
                if (km >= 1.0) String.format("%.1f كم متبقية", km) else "${progress.remainingDistanceMeters.toInt()} م متبقية"
            } else {
                if (km >= 1.0) String.format("%.1f km remaining", km) else "${progress.remainingDistanceMeters.toInt()} m remaining"
            }

            // Real Heading & Position Update
            updateVehicle(
                progress.snappedPosition.latitude,
                progress.snappedPosition.longitude,
                progress.snappedPosition.heading
            )

            // Dynamic Developer Tariff Metrics: [انطلاق الرحلة | المسافة | الأجرة]
            if (showFareMetrics) {
                activeController?.lastTripTotals?.let { totals ->
                    fareMetricsContainer.visibility = VISIBLE
                    val startFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
                    val startTimeStr = activeController?.tripStartTime?.let { startFormat.format(it) } ?: "--:--"

                    fareMetricsLabel.text = if (locale == "ar") {
                        "انطلاق: $startTimeStr  •  المسافة: ${String.format("%.2f", totals.distanceKm)} كم  •  الأجرة: ${totals.fareLabel}"
                    } else {
                        "Start: $startTimeStr  •  Distance: ${String.format("%.2f", totals.distanceKm)} km  •  Fare: ${totals.fareLabel}"
                    }
                }
            } else {
                fareMetricsContainer.visibility = GONE
            }
        }
    }

    override fun onSpeedExceeded(currentSpeed: Double, speedLimit: Double) {}
    override fun onCameraApproaching(camera: SpeedCamera, distance: Double) {}
    override fun onTripArrived(totals: NavTripTotals) {}
    override fun onError(error: String) {}

    private fun formatDistance(meters: Double): String {
        return if (locale == "ar") {
            if (meters >= 1000) String.format("بعد %.1f كم", meters / 1000.0) else "بعد ${meters.toInt()} م"
        } else {
            if (meters >= 1000) String.format("In %.1f km", meters / 1000.0) else "In ${meters.toInt()} m"
        }
    }

    private fun evaluateJs(script: String) {
        post {
            webView.evaluateJavascript(script, null)
        }
    }

    fun onDestroy() {
        webView.destroy()
    }
}
