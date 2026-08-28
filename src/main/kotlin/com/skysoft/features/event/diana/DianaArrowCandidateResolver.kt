package com.skysoft.features.event.diana

import com.skysoft.utils.WorldVec
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos

internal data class ResolvedArrowCandidate(
    val raw: WorldVec,
    val location: WorldVec,
    val distanceFromOrigin: Double,
    val distanceToRay: Double,
    val scaledDistanceToRay: Double,
    val order: Int,
    val surfaceSource: DianaArrowCandidateSurfaceSource,
)

internal enum class DianaArrowCandidateSurfaceSource {
    LIVE,
    CACHED,
    UNKNOWN,
}

internal object DianaArrowCandidateResolver {
    fun resolve(ray: DianaArrowRay, bounds: DianaArrowBounds): List<ResolvedArrowCandidate> =
        resolve(ray, DianaArrowProjector.project(ray, bounds))

    fun resolve(ray: DianaArrowRay, candidates: List<DianaArrowCandidate>): List<ResolvedArrowCandidate> =
        rank(candidates.flatMapIndexed { index, candidate -> candidate.resolveLoadedSurfaceCandidates(ray, index) })

    fun rank(candidates: List<ResolvedArrowCandidate>): List<ResolvedArrowCandidate> {
        val bestByBlock = linkedMapOf<String, ResolvedArrowCandidate>()
        for (candidate in candidates) {
            val key = candidate.location.blockKey()
            val current = bestByBlock[key]
            if (current == null || candidate.scaledDistanceToRay < current.scaledDistanceToRay) {
                bestByBlock[key] = candidate
            }
        }
        val bestScore = bestByBlock.values.minOfOrNull { candidate -> candidate.scaledDistanceToRay }
            ?: return emptyList()
        return bestByBlock.values.filter { candidate -> candidate.scaledDistanceToRay == bestScore }
    }

    private fun DianaArrowCandidate.resolveLoadedSurfaceCandidates(
        ray: DianaArrowRay,
        order: Int,
    ): List<ResolvedArrowCandidate> {
        val level = Minecraft.getInstance().level
            ?: return resolveCachedSurface(ray, order)
        val blockPos = BlockPos(block.x.toInt(), block.y.toInt(), block.z.toInt())
        if (!level.isLoaded(blockPos)) return resolveCachedSurface(ray, order)
        return VERTICAL_SURFACE_SCAN_OFFSETS
            .asSequence()
            .map { offset -> blockPos.verticalOffset(offset) }
            .filter { candidate -> DianaBurrowSurfaceValidator.isValid(level, candidate) }
            .mapNotNull { candidate -> DianaArrowProjector.scoreBlock(ray, candidate.toWorldVec()) }
            .map { candidate ->
                candidate.toResolved(
                    order = order,
                    raw = block,
                    surfaceSource = DianaArrowCandidateSurfaceSource.LIVE,
                )
            }
            .toList()
    }

    private fun DianaArrowCandidate.resolveCachedSurface(
        ray: DianaArrowRay,
        order: Int,
    ): List<ResolvedArrowCandidate> {
        val cached = DianaHubSurfaceCache.cachedSurface(block)
        return when (cached.status) {
            DianaCachedSurfaceStatus.VALID ->
                cached.location
                    ?.let { location -> DianaArrowProjector.scoreBlock(ray, location) }
                    ?.let { candidate ->
                        listOf(
                            candidate.toResolved(
                                order = order,
                                raw = block,
                                surfaceSource = DianaArrowCandidateSurfaceSource.CACHED,
                            ),
                        )
                    }
                    .orEmpty()
            DianaCachedSurfaceStatus.INVALID -> emptyList()
            DianaCachedSurfaceStatus.UNKNOWN -> emptyList()
        }
    }

    private fun DianaArrowCandidate.toResolved(
        order: Int,
        raw: WorldVec = block,
        surfaceSource: DianaArrowCandidateSurfaceSource,
    ): ResolvedArrowCandidate =
        ResolvedArrowCandidate(
            raw = raw,
            location = block,
            distanceFromOrigin = distanceFromOrigin,
            distanceToRay = distanceToRay,
            scaledDistanceToRay = scaledDistanceToRay,
            order = order,
            surfaceSource = surfaceSource,
        )

    private fun BlockPos.verticalOffset(offset: Int): BlockPos =
        when {
            offset > 0 -> above(offset)
            offset < 0 -> below(-offset)
            else -> this
        }

    private fun BlockPos.toWorldVec(): WorldVec = WorldVec(x.toDouble(), y.toDouble(), z.toDouble())

    private const val VERTICAL_SURFACE_SCAN_RADIUS = 12
    private val VERTICAL_SURFACE_SCAN_OFFSETS = (0..VERTICAL_SURFACE_SCAN_RADIUS).flatMap { offset ->
        if (offset == 0) listOf(0) else listOf(-offset, offset)
    }
}
