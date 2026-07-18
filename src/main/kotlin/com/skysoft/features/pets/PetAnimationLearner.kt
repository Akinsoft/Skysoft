package com.skysoft.features.pets

import com.skysoft.SkysoftMod
import com.skysoft.utils.SkysoftClientEvents

internal object PetAnimationLearner {
    private var captureKey: String? = null
    private var recorder: PetAnimationLoopRecorder? = null

    fun register() {
        ActivePetEntityTracker.registerConsumer("Pet Animation Learner", PetFeatureDemand::isDisplayActive)
        SkysoftClientEvents.onEndTick(
            "Pet Animation Learner tick",
            isActive = { PetFeatureDemand.isDisplayActive() || recorder != null },
        ) { tick() }
        SkysoftClientEvents.onDisconnect("Pet Animation Learner disconnect reset", ::discardCapture)
    }

    private fun tick() {
        if (!PetFeatureDemand.isDisplayActive()) {
            discardCapture()
            return
        }
        val currentPet = ActivePetTracker.currentPet
        val skin = currentPet?.skinInternalName?.takeIf(String::isNotBlank)
        if (currentPet == null || skin == null) {
            discardCapture()
            return
        }

        val observation = ActivePetEntityTracker.current()
        if (observation == null || !observation.matches(currentPet)) {
            discardCapture()
            return
        }
        if (PetSkins.hasBundled(skin, observation.texture) || PetSkins.hasLearned(skin, observation.texture)) {
            discardCapture()
            return
        }

        val nextCaptureKey = "$skin:${currentPet.uuid}:${observation.entity.id}"
        if (captureKey != nextCaptureKey) {
            captureKey = nextCaptureKey
            recorder = PetAnimationLoopRecorder()
        }

        val activeRecorder = recorder ?: return
        val confirmed = activeRecorder.observe(observation.texture)
        if (confirmed != null) {
            saveConfirmedAnimation(skin, confirmed)
        }
    }

    private fun saveConfirmedAnimation(
        skin: String,
        loop: ConfirmedPetAnimationLoop,
    ) {
        val identityTexture = loop.textures.first()
        val animation = AnimatedSkinJson(
            ticksPerTexture = loop.ticksPerTexture,
            textures = loop.textures,
        )
        PetSkins.storeLearned(skin, identityTexture, animation)
            .onSuccess { discardCapture() }
            .onFailure { error ->
                discardCapture()
                SkysoftMod.LOGGER.error("Failed to save learned pet animation for $skin", error)
            }
    }

    private fun discardCapture() {
        captureKey = null
        recorder = null
    }
}
