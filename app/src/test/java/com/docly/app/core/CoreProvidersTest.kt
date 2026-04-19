package com.docly.app.core

import com.docly.app.core.common.UuidIdProvider
import com.docly.app.core.testing.FixedIdProvider
import com.docly.app.core.testing.FixedTimeProvider
import com.docly.app.core.testing.SequenceIdProvider
import com.docly.app.core.testing.TestDispatcherProvider
import java.util.UUID
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Test

class CoreProvidersTest {
    @Test
    fun uuidIdProviderReturnsParseableUuidStrings() {
        val provider = UuidIdProvider()

        val first = provider.generateId()
        val second = provider.generateId()

        UUID.fromString(first)
        UUID.fromString(second)
        assertNotEquals(first, second)
    }

    @Test
    fun fixedIdProviderReturnsDeterministicId() {
        val provider = FixedIdProvider("fixed-id")

        assertEquals("fixed-id", provider.generateId())
        assertEquals("fixed-id", provider.generateId())
    }

    @Test
    fun sequenceIdProviderReturnsIdsInOrder() {
        val provider = SequenceIdProvider(listOf("first-id", "second-id"))

        assertEquals("first-id", provider.generateId())
        assertEquals("second-id", provider.generateId())
    }

    @Test
    fun fixedTimeProviderReturnsConfiguredTimestamp() {
        val provider = FixedTimeProvider(1234L)

        assertEquals(1234L, provider.now())
    }

    @Test
    fun testDispatcherProviderUsesSameDispatcherForEveryContext() {
        val dispatcher = StandardTestDispatcher()
        val provider = TestDispatcherProvider(dispatcher)

        assertSame(dispatcher, provider.main)
        assertSame(dispatcher, provider.io)
        assertSame(dispatcher, provider.default)
    }
}
