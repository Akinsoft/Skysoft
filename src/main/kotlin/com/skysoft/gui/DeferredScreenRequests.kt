package com.skysoft.gui

import com.skysoft.utils.SkysoftClientEvents
import com.skysoft.utils.SkysoftErrorBoundary

internal object DeferredScreenRequests {
    private var pendingRequest: DeferredScreenRequest? = null

    fun register() {
        SkysoftClientEvents.onEndTick(
            "Deferred screen requests",
            isActive = { pendingRequest != null },
        ) { openPending() }
    }

    fun request(name: String, open: () -> Unit) {
        pendingRequest = DeferredScreenRequest(name, open)
    }

    private fun openPending() {
        val request = pendingRequest ?: return
        pendingRequest = null
        SkysoftErrorBoundary.run("${request.name} screen opening", request.open)
    }

    private data class DeferredScreenRequest(
        val name: String,
        val open: () -> Unit,
    )
}
