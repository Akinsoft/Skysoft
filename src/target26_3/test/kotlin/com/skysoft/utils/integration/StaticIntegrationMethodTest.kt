package com.skysoft.utils.integration

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class StaticIntegrationMethodTest {
    object Fixture {
        var received = 0
        val failure = IllegalStateException("actual integration implementation failure")
        @JvmStatic fun draw(value: Int) { received = value }
        @JvmStatic fun fail() { throw failure }
        @JvmStatic fun wrongReturn(): Int = 1
    }

    @Test fun invokesExactStaticVoidMethod() {
        val method = StaticIntegrationMethod.resolve(Fixture::class.java, "draw", Int::class.javaPrimitiveType!!)
        StaticIntegrationMethod.invoke(method, 37)
        assertEquals(37, Fixture.received)
    }

    @Test fun exposesMissingOrChangedSignatures() {
        assertThrows(LinkageError::class.java) { StaticIntegrationMethod.resolve(Fixture::class.java, "missing") }
        assertThrows(LinkageError::class.java) { StaticIntegrationMethod.resolve(Fixture::class.java, "draw", String::class.java) }
        assertThrows(LinkageError::class.java) { StaticIntegrationMethod.resolve(Fixture::class.java, "wrongReturn") }
    }

    @Test fun preservesTargetException() {
        val method = StaticIntegrationMethod.resolve(Fixture::class.java, "fail")
        val error = assertThrows(IllegalStateException::class.java) { StaticIntegrationMethod.invoke(method) }
        assertSame(Fixture.failure, error)
    }
}
