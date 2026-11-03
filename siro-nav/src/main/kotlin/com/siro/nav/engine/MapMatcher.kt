package com.siro.nav.engine

import com.siro.nav.models.NavPoint
import kotlin.math.max
import kotlin.math.min

data class SnappedResult(
    val snappedPoint: NavPoint,
    val segmentIndex: Int,
    val crossTrackDistanceMeters: Double,
    val distanceAlongRouteMeters: Double
)

object MapMatcher {
    private const val WINDOW_LOOKAHEAD = 30
    private const val WINDOW_LOOKBEHIND = 5
    private const val NEAR_TIE_METERS = 15.0

    fun snapToPolyline(
        rawPoint: NavPoint,
        polyline: List<NavPoint>,
        lastKnownSegmentIndex: Int = 0
    ): SnappedResult {
        if (polyline.isEmpty()) {
            return SnappedResult(
                snappedPoint = rawPoint,
                segmentIndex = 0,
                crossTrackDistanceMeters = 0.0,
                distanceAlongRouteMeters = 0.0
            )
        }

        if (polyline.size == 1) {
            val dist = rawPoint.distanceTo(polyline.first())
            return SnappedResult(
                snappedPoint = polyline.first(),
                segmentIndex = 0,
                crossTrackDistanceMeters = dist,
                distanceAlongRouteMeters = 0.0
            )
        }

        val totalSegments = polyline.size - 1
        val startIdx = max(0, lastKnownSegmentIndex - WINDOW_LOOKBEHIND)
        val endIdx = min(totalSegments, lastKnownSegmentIndex + WINDOW_LOOKAHEAD)

        var minDistance = Double.POSITIVE_INFINITY
        for (i in startIdx until endIdx) {
            val projected = projectPointOnSegment(rawPoint, polyline[i], polyline[i + 1])
            val dist = rawPoint.distanceTo(projected)
            if (dist < minDistance) {
                minDistance = dist
            }
        }

        var bestSegmentIndex = lastKnownSegmentIndex.coerceIn(0, totalSegments - 1)
        var bestDistance = minDistance
        var bestSnappedPoint = rawPoint

        for (i in startIdx until endIdx) {
            val projected = projectPointOnSegment(rawPoint, polyline[i], polyline[i + 1])
            val dist = rawPoint.distanceTo(projected)
            if (dist <= minDistance + NEAR_TIE_METERS) {
                bestSegmentIndex = i
                bestDistance = dist
                bestSnappedPoint = projected
                break
            }
        }

        var distanceAlongRoute = 0.0
        for (i in 0 until bestSegmentIndex) {
            distanceAlongRoute += polyline[i].distanceTo(polyline[i + 1])
        }
        distanceAlongRoute += polyline[bestSegmentIndex].distanceTo(bestSnappedPoint)

        return SnappedResult(
            snappedPoint = bestSnappedPoint,
            segmentIndex = bestSegmentIndex,
            crossTrackDistanceMeters = bestDistance,
            distanceAlongRouteMeters = distanceAlongRoute
        )
    }

    private fun projectPointOnSegment(p: NavPoint, a: NavPoint, b: NavPoint): NavPoint {
        val dx = b.longitude - a.longitude
        val dy = b.latitude - a.latitude

        if (dx == 0.0 && dy == 0.0) {
            return a
        }

        val t = ((p.longitude - a.longitude) * dx + (p.latitude - a.latitude) * dy) / (dx * dx + dy * dy)
        val clampedT = t.coerceIn(0.0, 1.0)

        val projLng = a.longitude + clampedT * dx
        val projLat = a.latitude + clampedT * dy
        val segmentBearing = a.bearingTo(b)

        return NavPoint(
            latitude = projLat,
            longitude = projLng,
            heading = segmentBearing,
            speedKmh = p.speedKmh,
            altitude = a.altitude + clampedT * (b.altitude - a.altitude),
            accuracy = p.accuracy,
            timestamp = p.timestamp
        )
    }
}
