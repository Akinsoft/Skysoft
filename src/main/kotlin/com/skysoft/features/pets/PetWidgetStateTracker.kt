package com.skysoft.features.pets

import com.skysoft.data.hypixel.TabListApi
import kotlin.time.Duration.Companion.seconds

object PetWidgetStateTracker {
    private val widgetLoadGrace = 3.seconds
    private var state = State.LOADING
    private var tabSessionId = Long.MIN_VALUE

    val isReadyForDisplay: Boolean
        get() = isCurrentWidgetState && state == State.READY

    val displayMessage: List<String>?
        get() = when {
            isCurrentWidgetState &&
                state == State.NOT_READY &&
                ActivePetTracker.currentPet != null &&
                TabListApi.hasWaitedForSkyBlockData(widgetLoadGrace) -> listOf(
                "§cPet Tab Widget Missing",
                "§cDo /widget and enable the pet widget",
            )

            isCurrentWidgetState && state == State.MAXED_WITHOUT_OVERFLOW_XP -> listOf(
                "§cPet Widget Overflow XP Missing",
                "§cEnable overflow XP in the pet widget",
            )

            else -> null
        }

    private val isCurrentWidgetState: Boolean
        get() = TabListApi.isSkyBlockDataLoaded && tabSessionId == TabListApi.sessionId

    fun syncLoadingState() {
        if (!isCurrentWidgetState && state != State.LOADING) {
            state = State.LOADING
        }
    }

    fun reset() {
        state = State.LOADING
        tabSessionId = Long.MIN_VALUE
    }

    fun setReady() {
        update(State.READY)
    }

    fun setNotReady() {
        update(State.NOT_READY)
    }

    fun setMaxedWithoutOverflowXp() {
        update(State.MAXED_WITHOUT_OVERFLOW_XP)
    }

    private fun update(newState: State) {
        state = newState
        tabSessionId = TabListApi.sessionId
    }

    private enum class State {
        LOADING,
        NOT_READY,
        READY,
        MAXED_WITHOUT_OVERFLOW_XP,
    }
}
