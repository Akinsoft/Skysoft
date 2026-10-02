package com.skysoft.test

import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import org.spongepowered.asm.mixin.MixinEnvironment
import org.spongepowered.asm.mixin.transformer.IMixinTransformer

class NativeMixinAuditTest {
    @Test fun allRequiredMixinsApplyToNativeMinecraft() {
        val environment = MixinEnvironment.getCurrentEnvironment()
        assertInstanceOf(IMixinTransformer::class.java, environment.activeTransformer)
        environment.audit()
    }

    @Test fun everyRequiredMixinIsPresentOnTheAuditedClasspath() {
        val loader = javaClass.classLoader
        val config = loader.getResourceAsStream("skysoft.mixins.json")!!.bufferedReader().use {
            JsonParser.parseReader(it).asJsonObject
        }
        assertEquals(true, config.get("required").asBoolean)
        assertEquals(1, config.getAsJsonObject("injectors").get("defaultRequire").asInt)
        val mixins = config.getAsJsonArray("client")
        assertEquals(90, mixins.size())
        mixins.forEach {
            checkNotNull(loader.getResource("com/skysoft/mixin/${it.asString.replace('.', '/')}.class")) {
                "Required mixin ${it.asString} is missing from the audited classpath"
            }
        }
    }
}
