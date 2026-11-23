package io.github.asanre.verivent.export

import kotlin.test.Test
import kotlin.test.assertEquals

class ExportSourceIosTest {

    @Test
    fun theSourceIsIos() {
        assertEquals("ios", exportSource?.platform)
    }
}
