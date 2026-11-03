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
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.siro.nav.SiroNavController
import com.siro.nav.SiroNavListener
import com.siro.nav.billing.NavTripTotals
import com.siro.nav.engine.NavEvent
import com.siro.nav.models.NavPoint
import com.siro.nav.models.NavRoute
import com.siro.nav.models.SpeedCamera
import org.json.JSONArray
import org.json.JSONObject

/**
 * Full-screen Turn-by-Turn Navigation View for Android.
 * Renders the Siro vector map, the active route polyline, a dynamic 3D follow camera,
 * the rotating navigation arrow marker, and the turn-by-turn guidance HUD.
 */
class SiroNavView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr), SiroNavListener {

    private val webView = WebView(context)
    private var isMapLoaded = false

    private val topInstructionCard = LinearLayout(context)
    private val instructionLabel = TextView(context)
    private val distanceLabel = TextView(context)
    private val speedBadge = FrameLayout(context)
    private val speedLabel = TextView(context)
    private val bottomStatusBar = LinearLayout(context)
    private val remainingLabel = TextView(context)
    private val exitButton = Button(context)

    var onExitListener: (() -> Unit)? = null
    var styleUrl: String = "https://map.intaleqapp.com/style.json"

    private var activeController: SiroNavController? = null

    init {
        setBackgroundColor(Color.parseColor("#101014"))
        setupMapWebView()
        setupHUD(context)
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

        webView.setBackgroundColor(Color.parseColor("#101014"))
        webView.webChromeClient = WebChromeClient()
        webView.addJavascriptInterface(NavMapBridge(), "siroNavBridge")

        loadMapHtml()
    }

    private fun loadMapHtml() {
        val html = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="utf-8" />
                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
                <title>Siro Nav Map</title>
                <script src="https://unpkg.com/maplibre-gl@4.7.1/dist/maplibre-gl.js"></script>
                <link href="https://unpkg.com/maplibre-gl@4.7.1/dist/maplibre-gl.css" rel="stylesheet" />
                <style>
                    html, body, #map { margin: 0; padding: 0; width: 100%; height: 100%; background: #101014; }
                    .vehicle-arrow-container {
                        width: 44px;
                        height: 44px;
                        display: flex;
                        align-items: center;
                        justify-content: center;
                        transition: transform 0.2s linear;
                    }
                    .vehicle-arrow {
                        width: 0;
                        height: 0;
                        border-left: 12px solid transparent;
                        border-right: 12px solid transparent;
                        border-bottom: 28px solid #0071E3;
                        filter: drop-shadow(0 4px 8px rgba(0,0,0,0.5));
                        position: relative;
                    }
                    .vehicle-arrow::after {
                        content: '';
                        position: absolute;
                        top: 4px;
                        left: -8px;
                        width: 0;
                        height: 0;
                        border-left: 8px solid transparent;
                        border-right: 8px solid transparent;
                        border-bottom: 20px solid #FFFFFF;
                    }
                </style>
            </head>
            <body>
                <div id="map"></div>
                <script>
                    var map = new maplibregl.Map({
                        container: 'map',
                        style: '$styleUrl',
                        center: [35.9106, 31.9539],
                        zoom: 17.5,
                        pitch: 60.0,
                        bearing: 0,
                        attributionControl: false
                    });

                    var vehicleMarker = null;
                    var vehicleEl = null;

                    map.on('load', function() {
                        // Vehicle Marker Setup
                        vehicleEl = document.createElement('div');
                        vehicleEl.className = 'vehicle-arrow-container';
                        var arrow = document.createElement('div');
                        arrow.className = 'vehicle-arrow';
                        vehicleEl.appendChild(arrow);

                        vehicleMarker = new maplibregl.Marker({ element: vehicleEl, rotationAlignment: 'map' })
                            .setLngLat([35.9106, 31.9539])
                            .addTo(map);

                        // Route Source & Layers
                        map.addSource('nav-route-source', {
                            type: 'geojson',
                            data: { type: 'Feature', geometry: { type: 'LineString', coordinates: [] } }
                        });

                        // Casing layer
                        map.addLayer({
                            id: 'nav-route-casing',
                            type: 'line',
                            source: 'nav-route-source',
                            layout: { 'line-join': 'round', 'line-cap': 'round' },
                            paint: { 'line-color': '#FFFFFF', 'line-width': 9 }
                        });

                        // Main route line
                        map.addLayer({
                            id: 'nav-route-line',
                            type: 'line',
                            source: 'nav-route-source',
                            layout: { 'line-join': 'round', 'line-cap': 'round' },
                            paint: { 'line-color': '#0071E3', 'line-width': 6 }
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
                            if (vehicleEl) {
                                vehicleEl.style.transform = 'rotate(' + heading + 'deg)';
                            }
                        }
                        map.easeTo({
                            center: [lng, lat],
                            bearing: heading,
                            pitch: 60,
                            zoom: 17.5,
                            duration: 700
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

    private fun setupHUD(context: Context) {
        val dp = context.resources.displayMetrics.density

        // 1. Top Instruction Card
        topInstructionCard.apply {
            orientation = LinearLayout.VERTICAL
            setPadding((16 * dp).toInt(), (14 * dp).toInt(), (16 * dp).toInt(), (14 * dp).toInt())
            background = GradientDrawable().apply {
                setColor(Color.argb(240, 25, 25, 25))
                cornerRadius = 16 * dp
            }
            layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins((16 * dp).toInt(), (24 * dp).toInt(), (16 * dp).toInt(), 0)
                gravity = Gravity.TOP
            }
        }

        instructionLabel.apply {
            setTextColor(Color.WHITE)
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 2
        }

        distanceLabel.apply {
            setTextColor(Color.parseColor("#34D399"))
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, (6 * dp).toInt(), 0, 0)
        }

        topInstructionCard.addView(instructionLabel)
        topInstructionCard.addView(distanceLabel)
        addView(topInstructionCard)

        // 2. Speed Badge
        speedBadge.apply {
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                setStroke((3 * dp).toInt(), Color.RED)
                cornerRadius = 24 * dp
            }
            layoutParams = LayoutParams(
                (48 * dp).toInt(),
                (48 * dp).toInt()
            ).apply {
                setMargins((16 * dp).toInt(), 0, 0, (88 * dp).toInt())
                gravity = Gravity.BOTTOM or Gravity.START
            }
        }

        speedLabel.apply {
            setTextColor(Color.BLACK)
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        }
        speedBadge.addView(speedLabel)
        addView(speedBadge)

        // 3. Bottom Status Bar
        bottomStatusBar.apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((16 * dp).toInt(), 0, (16 * dp).toInt(), 0)
            background = GradientDrawable().apply {
                setColor(Color.argb(240, 30, 30, 30))
                cornerRadius = 20 * dp
            }
            layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                (64 * dp).toInt()
            ).apply {
                setMargins((12 * dp).toInt(), 0, (12 * dp).toInt(), (16 * dp).toInt())
                gravity = Gravity.BOTTOM
            }
        }

        remainingLabel.apply {
            setTextColor(Color.WHITE)
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f)
        }

        exitButton.apply {
            text = "خروج"
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#EF4444"))
                cornerRadius = 12 * dp
            }
            setOnClickListener { onExitListener?.invoke() }
            layoutParams = LinearLayout.LayoutParams((72 * dp).toInt(), (38 * dp).toInt())
        }

        bottomStatusBar.addView(remainingLabel)
        bottomStatusBar.addView(exitButton)
        addView(bottomStatusBar)
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
        }
    }

    override fun onProgressUpdated(progress: NavEvent.Progress) {
        post {
            speedLabel.text = progress.currentSpeedKmh.toInt().toString()
            val km = String.format("%.1f كم", progress.remainingDistanceMeters / 1000.0)
            val mins = "${progress.remainingDurationSeconds / 60} دقيقة"
            remainingLabel.text = "$km • $mins"
            updateVehicle(progress.snappedPosition.latitude, progress.snappedPosition.longitude, progress.snappedPosition.heading)
        }
    }

    override fun onSpeedExceeded(currentSpeed: Double, speedLimit: Double) {}
    override fun onCameraApproaching(camera: SpeedCamera, distance: Double) {}
    override fun onTripArrived(totals: NavTripTotals) {}
    override fun onError(error: String) {}

    private fun formatDistance(meters: Double): String {
        return if (meters >= 1000) {
            String.format("بعد %.1f كم", meters / 1000.0)
        } else {
            "بعد ${meters.toInt()} م"
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
