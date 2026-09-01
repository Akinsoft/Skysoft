package com.skysoft.features.event.diana

import com.skysoft.utils.WorldVec
import com.skysoft.utils.toWorldVec
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos

internal data class ResolvedArrowCandidate(
    val location: WorldVec,
    val scaledDistanceToRay: Double,
)

internal object DianaArrowCandidateResolver {
    fun resolve(ray: DianaArrowRay, bounds: DianaArrowBounds): List<ResolvedArrowCandidate> =
        resolve(ray, DianaArrowProjector.project(ray, bounds))

    fun resolve(ray: DianaArrowRay, candidates: List<DianaArrowCandidate>): List<ResolvedArrowCandidate> =
        rank(candidates.flatMap { candidate -> candidate.resolveLoadedSurfaceCandidates(ray) })

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

    private fun DianaArrowCandidate.resolveLoadedSurfaceCandidates(ray: DianaArrowRay): List<ResolvedArrowCandidate> {
        val level = Minecraft.getInstance().level
            ?: return resolveCachedSurface(ray)
        val blockPos = BlockPos(block.x.toInt(), block.y.toInt(), block.z.toInt())
        if (!level.isLoaded(blockPos)) return resolveCachedSurface(ray)
        return VERTICAL_SURFACE_SCAN_OFFSETS
            .asSequence()
            .map { offset -> blockPos.verticalOffset(offset) }
            .filter { candidate -> DianaBurrowSurfaceValidator.isValid(level, candidate) }
            .mapNotNull { candidate -> DianaArrowProjector.scoreBlock(ray, candidate.toWorldVec()) }
            .map { candidate -> candidate.toResolved() }
            .toList()
    }

    private fun DianaArrowCandidate.resolveCachedSurface(ray: DianaArrowRay): List<ResolvedArrowCandidate> {
        val cached = DianaHubSurfaceCache.cachedSurface(block)
        return when (cached.status) {
            DianaCachedSurfaceStatus.VALID ->
                cached.location
                    ?.let { location -> DianaArrowProjector.scoreBlock(ray, location) }
                    ?.let { candidate -> listOf(candidate.toResolved()) }
                    .orEmpty()
            DianaCachedSurfaceStatus.INVALID -> emptyList()
            DianaCachedSurfaceStatus.UNKNOWN -> emptyList()
        }
    }

    private fun DianaArrowCandidate.toResolved(): ResolvedArrowCandidate =
        ResolvedArrowCandidate(block, scaledDistanceToRay)

    private fun BlockPos.verticalOffset(offset: Int): BlockPos =
        when {
            offset > 0 -> above(offset)
            offset < 0 -> below(-offset)
            else -> this
        }

    private const val VERTICAL_SURFACE_SCAN_RADIUS = 12
    private val VERTICAL_SURFACE_SCAN_OFFSETS = (0..VERTICAL_SURFACE_SCAN_RADIUS).flatMap { offset ->
        if (offset == 0) listOf(0) else listOf(-offset, offset)
    }
}
