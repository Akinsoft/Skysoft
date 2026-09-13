package com.skysoft.features.waypoints

import com.skysoft.utils.WorldVec

internal class WaypointRoute {
    var groupId: String? = null
        private set
    var pointId: String? = null
        private set
    var paused = false
        private set
    var complete = false
        private set
    private var mustLeave: String? = null

    fun start(group: WaypointGroup, point: WaypointPoint? = null) {
        val target = if (point == null) group.points.firstOrNull { it.enabled }
        else group.points.firstOrNull { it.id == point.id && it.enabled }
        require(target != null) { "Enable at least one point to follow this route." }
        groupId = group.id
        pointId = target.id
        paused = false
        complete = false
        mustLeave = null
    }

    fun stop() {
        groupId = null
        pointId = null
        paused = false
        complete = false
        mustLeave = null
    }

    fun togglePause() {
        if (!complete && groupId != null) paused = !paused
    }

    fun step(group: WaypointGroup, direction: Int) {
        require(direction == -1 || direction == 1) { "Route steps must be Previous or Next." }
        reconcile(group)
        if (groupId == null) return
        val points = group.points.filter { it.enabled }
        val current = points.indexOfFirst { it.id == pointId }
        val next = current + direction
        if (next >= points.size && !group.loop) {
            complete = true
            paused = false
            return
        }
        val nextIndex = if (group.loop) Math.floorMod(next, points.size) else next.coerceIn(points.indices)
        mustLeave = pointId
        pointId = points[nextIndex].id
        complete = false
    }

    fun reconcile(group: WaypointGroup?) {
        val valid = group != null && group.id == groupId && group.enabled && group.route &&
            group.points.any { it.id == pointId && it.enabled }
        if (!valid) stop()
    }

    fun tick(group: WaypointGroup?, position: WorldVec) {
        reconcile(group)
        if (groupId == null || group == null || paused || complete) return
        val feet = WaypointPlacement.feetAt(position)
        val current = group.points.indexOfFirst { it.id == pointId }
        val currentPoint = group.points[current]
        val currentDistance = currentPoint.destination.distance(position)
        val previous = group.points.firstOrNull { it.id == mustLeave }
        val previousDistance = previous?.destination?.distance(position)
        val heldDistance = previousDistance?.takeIf {
            (it <= (previous.radius ?: group.radius) || group.skipAhead && feet == previous.position.roundToBlock()) &&
                it <= currentDistance
        }
        if (heldDistance == null) mustLeave = null
        val reachedCurrent = currentDistance <= (currentPoint.radius ?: group.radius) &&
            (heldDistance == null || heldDistance > currentDistance)
        val last = if (group.skipAhead && !reachedCurrent) group.points.lastIndex else current
        for (index in last downTo current) {
            val point = group.points[index]
            if (!point.enabled) continue
            val distance = point.destination.distance(position)
            val reached = if (index == current) distance <= (point.radius ?: group.radius) else feet == point.position.roundToBlock()
            if (!reached || heldDistance != null && heldDistance <= distance) continue
            pointId = point.id
            step(group, 1)
            return
        }
    }

    fun visiblePoints(group: WaypointGroup, upcoming: Int): Set<String> {
        val enabled = group.points.filter { it.enabled }
        val current = enabled.indexOfFirst { it.id == pointId }
        if (current < 0) return emptySet()
        return (0..upcoming.coerceAtMost(enabled.lastIndex)).mapNotNull { offset ->
            val index = current + offset
            if (group.loop) enabled[index % enabled.size].id else enabled.getOrNull(index)?.id
        }.toSet()
    }
}
