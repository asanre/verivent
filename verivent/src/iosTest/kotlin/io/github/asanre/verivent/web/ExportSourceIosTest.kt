package io.github.asanre.verivent.web

import kotlin.test.Test
import kotlin.test.assertEquals

class ExportSourceIosTest {

    @Test
    fun theSourceIsIos() {
        assertEquals("ios", exportSource?.platform)
    }
}
