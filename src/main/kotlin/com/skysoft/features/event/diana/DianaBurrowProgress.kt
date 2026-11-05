package com.skysoft.features.event.diana

internal data class DianaBurrowProgress(
    val completed: Int,
    val total: Int,
) {
    val isComplete: Boolean = completed >= total
}

internal fun parseDianaBurrowProgress(message: String): DianaBurrowProgress? {
    val match = GRIFFIN_BURROW_PROGRESS_PATTERN.matchEntire(message) ?: return null
    val completed = match.groupValues[COMPLETED_BURROW_GROUP].toIntOrNull() ?: return null
    val total = match.groupValues[TOTAL_BURROW_GROUP].toIntOrNull() ?: return null
    if (completed < 0 || total <= 0) return null
    return DianaBurrowProgress(completed, total)
}

internal const val BURROW_DUG_MESSAGE = "You dug out a Griffin Burrow!"
internal const val BURROW_CHAIN_FINISHED_MESSAGE = "You finished the Griffin burrow chain!"

private val GRIFFIN_BURROW_PROGRESS_PATTERN =
    Regex("""^You dug out a Griffin Burrow!\s*\((\d+)/(\d+)\).*$""")

private const val COMPLETED_BURROW_GROUP = 1
private const val TOTAL_BURROW_GROUP = 2
