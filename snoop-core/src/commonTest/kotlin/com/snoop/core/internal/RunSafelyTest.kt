package com.snoop.core.internal

import kotlin.test.Test
import kotlin.test.assertEquals

class RunSafelyTest {

    @Test
    fun aFailedBlockDoesNotStopTheNextOne() {
        var ran = 0
        // AssertionError on purpose: the contract is catching Throwable, not just Exception.
        runSafely("test") { throw AssertionError("boom") }
        runSafely("test") { ran++ }
        assertEquals(1, ran)
    }
}
