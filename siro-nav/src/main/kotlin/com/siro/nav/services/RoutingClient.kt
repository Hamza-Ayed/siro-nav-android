package com.siro.nav.services

import com.siro.nav.engine.PolylineCodec
import com.siro.nav.models.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class RoutingClient(val config: NavConfig) {

    suspend fun getRoute(
        origin: NavPoint,
        destination: NavPoint,
        lang: String = "ar"
    ): NavRoute = withContext(Dispatchers.IO) {
        val siroUrl = "${config.baseUrl}/maps/route?fromLat=${origin.latitude}&fromLng=${origin.longitude}&toLat=${destination.latitude}&toLng=${destination.longitude}&steps=true&lang=$lang"
        try {
            val res = fetchUrl(siroUrl, config.apiKey)
            if (res.isNotEmpty() && !res.contains("error code: 502") && !res.contains("<html>")) {
                return@withContext parseSiroRoute(res)
            }
        } catch (_: Exception) {}

        val osrmUrl = "https://router.project-osrm.org/route/v1/driving/${origin.longitude},${origin.latitude};${destination.longitude},${destination.latitude}?overview=full&geometries=polyline&steps=true"
        val osrmRes = fetchUrl(osrmUrl, null)
        return@withContext parseOsrmRoute(osrmRes)
    }

    private fun fetchUrl(urlString: String, apiKey: String?): String {
        val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8000
            readTimeout = 8000
            setRequestProperty("User-Agent", "SiroNavAndroid-SDK/0.1.0")
            setRequestProperty("Accept", "application/json")
            apiKey?.let { setRequestProperty("x-api-key", it) }
        }
        return conn.inputStream.bufferedReader().use { it.readText() }
    }

    private fun parseSiroRoute(body: String): NavRoute {
        val json = JSONObject(body)
        val polyline = PolylineCodec.decode(json.getString("points"))
        val distance = json.optDouble("distance", 0.0)
        val duration = json.optDouble("duration", 0.0)

        val steps = mutableListOf<NavStep>()
        val instArr = json.optJSONArray("instructions")
        if (instArr != null) {
            for (i in 0 until instArr.length()) {
                val inst = instArr.getJSONObject(i)
                steps.add(
                    NavStep(
                        index = i,
                        instruction = inst.optString("text", ""),
                        streetName = inst.optString("street_name", ""),
                        maneuverType = inst.optInt("sign", 0),
                        distanceMeters = inst.optDouble("distance", 0.0),
                        durationSeconds = inst.optDouble("time", 0.0) / 1000.0,
                        lat = polyline.getOrNull(i)?.latitude ?: 0.0,
                        lng = polyline.getOrNull(i)?.longitude ?: 0.0
                    )
                )
            }
        }

        return NavRoute(
            id = "siro_route_${System.currentTimeMillis()}",
            routeName = json.optString("routeName", "طريق سيرو المباشر"),
            polyline = polyline,
            steps = steps,
            totalDistanceMeters = distance,
            totalDurationSeconds = duration
        )
    }

    private fun parseOsrmRoute(body: String): NavRoute {
        val root = JSONObject(body)
        val routes = root.getJSONArray("routes")
        val routeObj = routes.getJSONObject(0)
        val geometry = routeObj.getString("geometry")
        val polyline = PolylineCodec.decode(geometry)
        val distance = routeObj.getDouble("distance")
        val duration = routeObj.getDouble("duration")

        val steps = mutableListOf<NavStep>()
        val legs = routeObj.getJSONArray("legs")
        if (legs.length() > 0) {
            val legSteps = legs.getJSONObject(0).getJSONArray("steps")
            for (i in 0 until legSteps.length()) {
                val st = legSteps.getJSONObject(i)
                val maneuver = st.getJSONObject("maneuver")
                val mType = maneuver.optString("type", "")
                val modifier = maneuver.optString("modifier", "")
                val loc = maneuver.getJSONArray("location")
                val exitNumber = if (maneuver.has("exit")) maneuver.optInt("exit") else null
                val street = st.optString("name", "")

                val isRoundabout = mType == "roundabout" || mType == "rotary" || modifier.contains("roundabout")
                val sign = when {
                    isRoundabout -> 6
                    modifier == "uturn" -> -98
                    modifier in listOf("right", "slight right", "sharp right") -> 1
                    modifier in listOf("left", "slight left", "sharp left") -> -1
                    else -> 0
                }

                val instText = if (isRoundabout) {
                    val exitNameAr = when (exitNumber) {
                        1 -> "المخرج الأول"
                        2 -> "المخرج الثاني"
                        3 -> "المخرج الثالث"
                        4 -> "المخرج الرابع"
                        5 -> "المخرج الخامس"
                        6 -> "المخرج السادس"
                        else -> if (exitNumber != null) "المخرج رقم $exitNumber" else "المخرج المناسب"
                    }
                    if (street.isNotEmpty()) {
                        "عند الدوار، اسلك $exitNameAr إلى $street"
                    } else {
                        "عند الدوار، اسلك $exitNameAr"
                    }
                } else {
                    when (sign) {
                        1 -> if (street.isNotEmpty()) "انعطف يميناً إلى $street" else "انعطف يميناً"
                        -1 -> if (street.isNotEmpty()) "انعطف يساراً إلى $street" else "انعطف يساراً"
                        -98 -> "قم بالدوران للخلف"
                        else -> if (street.isNotEmpty()) "تابع السير على $street" else "تابع السير على الطريق"
                    }
                }

                steps.add(
                    NavStep(
                        index = i,
                        instruction = instText,
                        streetName = street,
                        maneuverType = sign,
                        exitNumber = exitNumber,
                        distanceMeters = st.optDouble("distance", 0.0),
                        durationSeconds = st.optDouble("duration", 0.0),
                        lat = loc.getDouble(1),
                        lng = loc.getDouble(0)
                    )
                )
            }
        }

        return NavRoute(
            id = "osrm_route_${System.currentTimeMillis()}",
            routeName = "مسار الطرق المعتمد",
            polyline = polyline,
            steps = steps,
            totalDistanceMeters = distance,
            totalDurationSeconds = duration
        )
    }
}
