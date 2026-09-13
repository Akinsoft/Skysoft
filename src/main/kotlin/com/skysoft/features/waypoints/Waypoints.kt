package com.skysoft.features.waypoints

import com.skysoft.config.SkysoftConfigGui
import com.skysoft.data.SkyBlockIsland
import com.skysoft.data.hypixel.HypixelLocationState
import com.skysoft.data.hypixel.SkyBlockProfileApi
import com.skysoft.utils.SidebarScoreboardState
import com.skysoft.utils.SkysoftClientEvents
import com.skysoft.utils.WorldVec
import com.skysoft.utils.toWorldVec
import java.util.UUID
import net.minecraft.client.Minecraft

internal object Waypoints {
    val config get() = SkysoftConfigGui.config().world.waypoints
    val route = WaypointRoute()
    val history = WaypointHistory()
    var selection = WaypointSelection()
    var browseIsland: SkyBlockIsland? = null
    var visitId: String = UUID.randomUUID().toString()
        private set
    private var locationKey: Pair<SkyBlockIsland?, String?>? = null
    private var wasEnabled = false
    val currentProfile: String? get() = SkyBlockProfileApi.currentProfileId?.let { "${it.playerKey}/${it.profileKey}" }
    val group: WaypointGroup? get() = WaypointLibrary.groups.firstOrNull { it.id == selection.groupId }
    val point: WaypointPoint? get() = group?.points?.firstOrNull { it.id == selection.pointId }
    val island: SkyBlockIsland?
        get() {
            val title = SidebarScoreboardState.title
            return HypixelLocationState.currentIsland.takeIf {
                HypixelLocationState.inSkyBlock &&
                    (it !in PROFILE_ISLANDS || title.startsWith("SKYBLOCK") && title != "SKYBLOCK GUEST")
            }
        }

    fun register() {
        WaypointLibrary.register()
        WaypointPanel.register()
        WaypointWorldRenderer.register()
        SkyBlockProfileApi.registerConsumer("Waypoints") { config.enabled || WaypointPanel.isOpen }
        HypixelLocationState.onChange("Waypoint island change", { config.enabled || WaypointPanel.isOpen }) {
            updateLocation()
        }
        SidebarScoreboardState.onChange("Waypoint island ownership", { config.enabled || WaypointPanel.isOpen }) {
            updateLocation()
        }
        SkyBlockProfileApi.onProfileChange("Waypoint profile change", { config.enabled || WaypointPanel.isOpen }) {
            route.stop()
            WaypointPlacement.clear()
            if (browseIsland == island && group?.let(::isInContext) != true) {
                selection = WaypointSelection(WaypointLibrary.groups.firstOrNull(::isInContext)?.id)
            }
        }
        SkysoftClientEvents.onEndTick(
            "Waypoints",
            { config.enabled || WaypointPanel.isOpen || wasEnabled || config.settings.editorKey >= 0 },
            ::tick,
        )
        SkysoftClientEvents.onDisconnect("Waypoint session reset") {
            route.stop()
            history.clear()
            WaypointPanel.close()
            WaypointPlacement.clear()
            locationKey = null
            browseIsland = null
            selection = WaypointSelection()
            wasEnabled = false
        }
    }

    fun isInContext(group: WaypointGroup): Boolean = group.island == island && when (group.scope) {
        WaypointScope.ISLAND -> true
        WaypointScope.PROFILE -> group.profile == currentProfile && currentProfile != null
        WaypointScope.VISIT -> group.visit == visitId
    }

    fun defaultScope(target: SkyBlockIsland): WaypointScope = when (target) {
        SkyBlockIsland.PRIVATE_ISLAND, SkyBlockIsland.GARDEN -> WaypointScope.PROFILE
        SkyBlockIsland.DUNGEONS, SkyBlockIsland.GLACITE_MINESHAFTS -> WaypointScope.VISIT
        else -> WaypointScope.ISLAND
    }

    fun scoped(group: WaypointGroup, scope: WaypointScope): WaypointGroup {
        require(scope != WaypointScope.PROFILE || currentProfile != null) { "Wait for your SkyBlock profile to load." }
        require(scope != WaypointScope.VISIT || group.island == island) { "Visit that island to create a temporary preset." }
        return group.copy(
            scope = scope,
            profile = currentProfile.takeIf { scope == WaypointScope.PROFILE },
            visit = visitId.takeIf { scope == WaypointScope.VISIT },
        )
    }

    fun select(group: WaypointGroup, point: WaypointPoint? = null) {
        browseIsland = group.island
        selection = WaypointSelection(group.id, point?.id)
    }

    fun updateGroups(groups: List<WaypointGroup>, after: WaypointSelection = selection) {
        val before = WaypointLibrary.groups
        WaypointLibrary.replace(groups)
        if (before != groups) history.record(before, selection)
        selection = after
        route.reconcile(WaypointLibrary.groups.firstOrNull { it.id == route.groupId && isInContext(it) })
    }

    fun updateGroup(updated: WaypointGroup, after: WaypointSelection = selection) {
        updateGroups(WaypointLibrary.groups.map { if (it.id == updated.id) updated else it }, after)
    }

    fun undo() {
        selection = history.undo(selection)
        group?.let { browseIsland = it.island }
        route.stop()
    }

    fun redo() {
        selection = history.redo(selection)
        group?.let { browseIsland = it.island }
        route.stop()
    }

    fun follow(fromSelection: Boolean = false) {
        require(config.enabled) { "Enable Waypoints first." }
        val selected = group ?: error("Select a preset first.")
        require(selected.route) { "Select an ordered route first." }
        require(isInContext(selected)) { "Visit this preset's island and profile to start it." }
        require(selected.points.any { it.enabled }) { "Enable at least one waypoint first." }
        val routeGroup = selected.copy(enabled = true)
        if (routeGroup != selected) updateGroup(routeGroup)
        route.start(routeGroup, point.takeIf { fromSelection })
    }

    fun toggleFollowing() {
        if (route.groupId != null && route.groupId == selection.groupId && !route.complete) {
            route.togglePause()
        } else {
            follow()
        }
    }

    fun step(direction: Int) {
        val routeGroup = WaypointLibrary.groups.firstOrNull { it.id == route.groupId } ?: return
        route.step(routeGroup, direction)
    }

    private fun tick(minecraft: Minecraft) {
        if (!config.enabled) {
            route.stop()
            WaypointPlacement.clear()
            if (wasEnabled) locationKey = null
        } else if (island == null || minecraft.level == null) {
            route.stop()
            WaypointPlacement.clear()
        } else {
            WaypointPlacement.capture(minecraft)
            val routeGroup = WaypointLibrary.groups.firstOrNull { it.id == route.groupId && isInContext(it) }
            minecraft.player?.position()?.toWorldVec()?.let { route.tick(routeGroup, it) }
        }
        wasEnabled = config.enabled
        WaypointKeybinds.tick()
        WaypointPanel.tick()
    }

    private fun updateLocation() {
        val key = island to HypixelLocationState.currentServerName
        if (key == locationKey) return
        locationKey = key
        beginVisit()
    }

    private fun beginVisit() {
        visitId = UUID.randomUUID().toString()
        route.stop()
        history.clear()
        WaypointPlacement.clear()
        if (WaypointLibrary.loadError == null) {
            val saved = WaypointLibrary.groups.filter { it.scope != WaypointScope.VISIT }
            if (saved.size != WaypointLibrary.groups.size) WaypointLibrary.replace(saved)
        }
        browseIsland = island
        val selected = WaypointLibrary.groups.firstOrNull { isInContext(it) }
        selection = WaypointSelection(selected?.id)
    }

    private val PROFILE_ISLANDS = setOf(SkyBlockIsland.PRIVATE_ISLAND, SkyBlockIsland.GARDEN)
}

internal object WaypointEditing {
    fun add(position: WorldVec) {
        require(Waypoints.config.enabled) { "Enable Waypoints first." }
        val group = Waypoints.group ?: error("Create or select a preset first.")
        require(Waypoints.isInContext(group)) { "Select a preset for your current island and profile." }
        val point = WaypointPoint(x = position.x, y = position.y, z = position.z)
        val updated = group.copy(points = group.points + point)
        Waypoints.updateGroup(updated, WaypointSelection(group.id, point.id))
    }

    fun updatePoint(point: WaypointPoint) {
        val group = Waypoints.group ?: return
        Waypoints.updateGroup(group.copy(points = group.points.map { if (it.id == point.id) point else it }))
    }

    fun removePoint() {
        val group = Waypoints.group ?: return
        val point = Waypoints.point ?: return
        val index = group.points.indexOf(point)
        val updated = group.copy(points = group.points.filterNot { it.id == point.id })
        Waypoints.updateGroup(
            updated,
            WaypointSelection(group.id, updated.points.getOrNull(index.coerceAtMost(updated.points.lastIndex))?.id),
        )
    }

    fun reorderPoint(targetIndex: Int) {
        val group = Waypoints.group ?: return
        val index = group.points.indexOfFirst { it.id == Waypoints.selection.pointId }
        if (index < 0 || targetIndex !in group.points.indices || index == targetIndex) return
        val points = group.points.toMutableList()
        points.add(targetIndex, points.removeAt(index))
        Waypoints.updateGroup(group.copy(points = points))
    }

    fun removeGroup() {
        val id = Waypoints.selection.groupId ?: return
        Waypoints.updateGroups(WaypointLibrary.groups.filterNot { it.id == id }, WaypointSelection())
    }

    fun duplicateGroup() {
        val group = Waypoints.group ?: return
        val copy = group.copy(
            id = UUID.randomUUID().toString(),
            name = group.name.take(MAX_WAYPOINT_NAME - COPY_SUFFIX.length) + COPY_SUFFIX,
            points = group.points.map { it.copy(id = UUID.randomUUID().toString()) },
        )
        Waypoints.updateGroups(WaypointLibrary.groups + copy, WaypointSelection(copy.id))
    }

    private const val COPY_SUFFIX = " (copy)"
}
