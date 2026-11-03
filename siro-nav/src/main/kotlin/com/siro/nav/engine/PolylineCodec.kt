package com.siro.nav.engine

import com.siro.nav.models.NavPoint
import java.util.Date
import kotlin.math.pow

object PolylineCodec {
    fun decode(encoded: String, precision: Int = 5): List<NavPoint> {
        val factor = 10.0.pow(precision.toDouble())
        val points = mutableListOf<NavPoint>()
        val now = Date()
        var index = 0

        fun readDelta(): Int {
            var result = 0
            var shift = 0
            var chunk: Int
            do {
                if (index >= encoded.length) {
                    throw IllegalArgumentException("Truncated encoded polyline")
                }
                chunk = encoded[index++].code - 63
                result = result or ((chunk and 0x1f) shl shift)
                shift += 5
            } while (chunk >= 0x20)
            return if ((result and 1) != 0) (result shr 1).inv() else (result shr 1)
        }

        var lat = 0
        var lng = 0
        while (index < encoded.length) {
            lat += readDelta()
            lng += readDelta()
            points.add(
                NavPoint(
                    latitude = lat / factor,
                    longitude = lng / factor,
                    timestamp = now
                )
            )
        }
        return points
    }
}
