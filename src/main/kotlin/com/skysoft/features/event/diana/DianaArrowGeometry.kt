package com.skysoft.features.event.diana

import com.skysoft.utils.WorldVec
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sign

internal data class DianaArrowRay(
    val origin: WorldVec,
    val direction: WorldVec,
    val distanceHint: DianaArrowDistance,
)

internal data class DianaArrowBounds(
    val min: WorldVec,
    val max: WorldVec,
) {
    fun contains(point: WorldVec): Boolean =
        point.x >= min.x && point.x <= max.x &&
            point.y >= min.y && point.y <= max.y &&
            point.z >= min.z && point.z <= max.z
}

internal val dianaHubBounds = DianaArrowBounds(
    min = WorldVec(-283.0, 0.0, -208.0),
    max = WorldVec(175.0, 256.0, 205.0),
)

internal data class DianaArrowCandidate(
    val block: WorldVec,
    val scaledDistanceToRay: Double,
    val distanceFromOrigin: Double,
)

internal class DianaArrowShapeDetector {
    private val points = mutableListOf<TimedArrowPoint>()

    fun add(location: WorldVec, distanceHint: DianaArrowDistance, now: Long = System.currentTimeMillis()): DianaArrowRay? {
        prune(now)
        if (points.none { it.location.distanceSq(location) <= DUPLICATE_POINT_DISTANCE_SQ }) {
            points += TimedArrowPoint(location, now)
        }
        val ray = detect(distanceHint)
        if (ray != null) points.clear()
        return ray
    }

    fun prune(now: Long = System.currentTimeMillis()) {
        points.removeAll { point -> now - point.seenAtMillis > POINT_MEMORY_MILLIS }
    }

    private fun detect(distanceHint: DianaArrowDistance): DianaArrowRay? {
        val line = findShaft(points.map { it.location }) ?: return null
        val firstCount = countNear(line[1])
        val secondCount = countNear(line[line.lastIndex - 1])
        if (setOf(firstCount, secondCount) != setOf(BASE_NEIGHBOR_COUNT, TIP_NEIGHBOR_COUNT)) return null

        val tip = if (firstCount == TIP_NEIGHBOR_COUNT) line.first() else line.last()
        val base = if (firstCount == TIP_NEIGHBOR_COUNT) line.last() else line.first()
        val direction = (tip.down(ARROW_HEIGHT_OFFSET) - base.down(ARROW_HEIGHT_OFFSET)).normalize()
        if (direction.lengthSq() != 0.0) return DianaArrowRay(base.down(ARROW_HEIGHT_OFFSET), direction, distanceHint)
        return null
    }

    private fun findShaft(locations: List<WorldVec>): List<WorldVec>? {
        for (first in locations) {
            for (second in locations) {
                if (first == second) continue
                val step = first.distance(second)
                if (step !in MIN_SHAFT_STEP..MAX_SHAFT_STEP) continue

                val line = buildLine(first, second, locations)
                if (line != null) return line
            }
        }
        return null
    }

    private fun buildLine(first: WorldVec, second: WorldVec, locations: List<WorldVec>): List<WorldVec>? {
        val direction = (second - first).normalize()
        val line = mutableListOf(first, second)
        val visited = mutableSetOf(first, second)
        while (line.size < SHAFT_POINT_COUNT) {
            val last = line.last()
            val next = locations
                .asSequence()
                .filter { point -> point !in visited }
                .filter { point -> point.isForwardShaftPoint(first, second, last, direction) }
                .minByOrNull { point -> point.distance(last) }
                ?: return null
            line += next
            visited += next
        }
        if (line.first().distance(line.last()) < MIN_SHAFT_SPAN) return null
        return line
    }

    private fun WorldVec.isForwardShaftPoint(
        shaftStart: WorldVec,
        shaftSecond: WorldVec,
        shaftEnd: WorldVec,
        direction: WorldVec,
    ): Boolean {
        val fromEnd = this - shaftEnd
        val projectedStep = fromEnd.dot(direction)
        return projectedStep in MIN_SHAFT_ADVANCE..MAX_SHAFT_STEP &&
            distance(shaftEnd) <= MAX_SHAFT_STEP &&
            distanceToInfiniteLine(shaftStart, shaftSecond) <= LINE_RESIDUAL_TOLERANCE
    }

    private fun countNear(origin: WorldVec): Int =
        points.count { point -> point.location != origin && point.location.distanceSq(origin) <= POINT_DETECTION_TOLERANCE_SQ }

    private data class TimedArrowPoint(
        val location: WorldVec,
        val seenAtMillis: Long,
    )

    private companion object {
        const val SHAFT_POINT_COUNT = 20
        const val POINT_MEMORY_MILLIS = 2_500L
        const val POINT_DETECTION_TOLERANCE = 0.12
        const val LINE_RESIDUAL_TOLERANCE = 0.025
        const val MIN_SHAFT_STEP = 0.04
        const val MIN_SHAFT_ADVANCE = 0.04
        const val MAX_SHAFT_STEP = POINT_DETECTION_TOLERANCE
        const val DUPLICATE_POINT_DISTANCE = 0.01
        const val ARROW_HEIGHT_OFFSET = 1.5
        const val MIN_SHAFT_SPAN = 1.2
        const val TIP_NEIGHBOR_COUNT = 4
        const val BASE_NEIGHBOR_COUNT = 2
        const val POINT_DETECTION_TOLERANCE_SQ = POINT_DETECTION_TOLERANCE * POINT_DETECTION_TOLERANCE
        const val DUPLICATE_POINT_DISTANCE_SQ = DUPLICATE_POINT_DISTANCE * DUPLICATE_POINT_DISTANCE
    }
}

internal object DianaArrowProjector {
    fun project(
        ray: DianaArrowRay,
        bounds: DianaArrowBounds,
    ): List<DianaArrowCandidate> {
        if (!bounds.contains(ray.origin)) return emptyList()
        val exitPoint = ray.intersect(bounds) ?: return emptyList()
        val axis = ray.bestProjectionAxis(exitPoint) ?: return emptyList()
        val candidates = linkedMapOf<String, DianaArrowCandidate>()
        val originAxis = ray.origin.axis(axis)
        val endAxis = exitPoint.axis(axis)
        val directionAxis = sign(ray.direction.axis(axis))
        val iterations = abs(endAxis - originAxis).toInt()

        for (step in 1..iterations) {
            val axisValue = originAxis + step * directionAxis
            val rayPoint = ray.pointAtAxis(axis, axisValue) ?: continue
            val block = rayPoint.roundToBlock()
            val candidate = block.toProjectedArrowCandidate(ray, rayPoint) ?: continue
            val key = block.blockKey()
            val previous = candidates[key]
            if (previous == null || candidate.scaledDistanceToRay < previous.scaledDistanceToRay) {
                candidates[key] = candidate
            }
        }

        if (candidates.isEmpty()) return emptyList()
        val inRange = candidates.values
            .asSequence()
            .filter { candidate -> ray.distanceHint.includes(candidate.distanceFromOrigin) }
            .toList()
        if (inRange.isEmpty()) return emptyList()

        return inRange.sortedWith(
            compareBy<DianaArrowCandidate> { candidate -> candidate.scaledDistanceToRay }
                .thenBy { candidate -> candidate.distanceFromOrigin },
        )
    }

    fun scoreBlock(ray: DianaArrowRay, block: WorldVec): DianaArrowCandidate? =
        block.toArrowCandidate(ray)

    private fun WorldVec.toArrowCandidate(ray: DianaArrowRay): DianaArrowCandidate? {
        val alongRay = (blockCenter() - ray.origin).dot(ray.direction)
        if (!ray.distanceHint.includes(alongRay)) return null
        val distanceToRay = blockCenter().distanceToRay(ray)
        if (distanceToRay > EXISTING_TARGET_RAY_TOLERANCE) return null
        return DianaArrowCandidate(
            block = this,
            scaledDistanceToRay = scaleRayDistance(distanceToRay, alongRay),
            distanceFromOrigin = alongRay,
        )
    }
}

private const val EXISTING_TARGET_RAY_TOLERANCE = 4.0
private const val SCALED_DISTANCE_FACTOR = 500_000.0
private const val ROUNDING_SCALE = 100.0
private const val RAY_EPSILON = 1e-12
private const val MIN_AXIS_DELTA = 0.9

private fun WorldVec.distanceToInfiniteLine(start: WorldVec, directionPoint: WorldVec): Double {
    val direction = directionPoint - start
    val length = direction.length()
    if (length == 0.0) return Double.MAX_VALUE
    return ((this - start).cross(direction).length() / length)
}

private fun WorldVec.distanceToRay(ray: DianaArrowRay): Double {
    val fromOrigin = this - ray.origin
    val projection = fromOrigin.dot(ray.direction)
    if (projection < 0.0) return Double.MAX_VALUE
    val closestPoint = ray.origin + ray.direction * projection
    return distance(closestPoint)
}

private fun DianaArrowRay.intersect(bounds: DianaArrowBounds): WorldVec? {
    var tMin = -Double.MAX_VALUE
    var tMax = Double.MAX_VALUE
    for (axis in AXES) {
        val origin = origin.axis(axis)
        val direction = direction.axis(axis)
        val min = bounds.min.axis(axis)
        val max = bounds.max.axis(axis)
        if (abs(direction) < RAY_EPSILON) {
            if (origin < min || origin > max) return null
            continue
        }

        val inverseDirection = 1.0 / direction
        var first = (min - origin) * inverseDirection
        var second = (max - origin) * inverseDirection
        if (first > second) {
            val oldFirst = first
            first = second
            second = oldFirst
        }
        tMin = max(tMin, first)
        tMax = min(tMax, second)
        if (tMin > tMax) return null
    }
    return origin + direction * tMax
}

private fun DianaArrowRay.bestProjectionAxis(exitPoint: WorldVec): Int? {
    var bestAxis: Int? = null
    var bestDistance = Double.MAX_VALUE
    for (axis in AXES) {
        val axisDistance = abs(exitPoint.axis(axis) - origin.axis(axis))
        if (axisDistance > MIN_AXIS_DELTA && axisDistance < bestDistance) {
            bestAxis = axis
            bestDistance = axisDistance
        }
    }
    return bestAxis
}

private fun DianaArrowRay.pointAtAxis(axis: Int, targetValue: Double): WorldVec? {
    val axisDirection = direction.axis(axis)
    if (abs(axisDirection) < RAY_EPSILON) return null
    val distance = (targetValue - origin.axis(axis)) / axisDirection
    if (distance < 0.0) return null
    return origin + direction * distance
}

private fun WorldVec.toProjectedArrowCandidate(ray: DianaArrowRay, pointOnRay: WorldVec): DianaArrowCandidate? {
    val distanceFromOrigin = pointOnRay.distance(ray.origin)
    if (distanceFromOrigin <= 0.0) return null
    val distanceToRay = blockCenter().distanceToRay(ray)
    return DianaArrowCandidate(
        block = this,
        scaledDistanceToRay = scaleRayDistance(distanceToRay, distanceFromOrigin),
        distanceFromOrigin = distanceFromOrigin,
    )
}

private fun scaleRayDistance(distanceToRay: Double, distanceFromOrigin: Double): Double =
    (distanceToRay * SCALED_DISTANCE_FACTOR / distanceFromOrigin * ROUNDING_SCALE).roundToInt() / ROUNDING_SCALE

private fun DianaArrowDistance.includes(distance: Double): Boolean =
    distance.toInt() in minDistance..maxDistance

private fun WorldVec.axis(axis: Int): Double = when (axis) {
    X_AXIS -> x
    Y_AXIS -> y
    Z_AXIS -> z
    else -> error("Unknown axis $axis")
}

private const val X_AXIS = 0
private const val Y_AXIS = 1
private const val Z_AXIS = 2
private val AXES = X_AXIS..Z_AXIS
