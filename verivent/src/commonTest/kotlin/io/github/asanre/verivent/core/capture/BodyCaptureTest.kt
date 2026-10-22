package io.github.asanre.verivent.core.capture

import io.github.asanre.verivent.core.internal.DiagnosticLog
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class BodyCaptureTest {

    private val warnings = mutableListOf<String>()

    @BeforeTest fun listen() {
        forgetWarnedCharsets()
        DiagnosticLog.onWarn = { warnings += it }
    }

    @AfterTest fun stopListening() {
        DiagnosticLog.onWarn = null
    }

    @Test
    fun aCharsetThePlatformDoesntKnowIsShownAsUtf8AndSaysSoOnce() {
        val preview = BodyCapture.textPreview("hello".encodeToByteArray(), "text/plain; charset=x-made-up", maxLength = 100)
        BodyCapture.textPreview("again".encodeToByteArray(), "text/plain; charset=X-Made-Up", maxLength = 100)

        assertEquals("hello", preview.text)
        assertEquals(listOf("[capture] charset x-made-up isn't supported here: bodies shown as UTF-8"), warnings)
    }

    @Test
    fun charsetWarningsStopAfter32Charsets() {
        (1..33).forEach { BodyCapture.textPreview("hi".encodeToByteArray(), "text/plain; charset=x-made-up-$it", maxLength = 100) }

        assertEquals(32, warnings.size)
    }

    @Test
    fun aBodyInItsDeclaredCharsetDecodesQuietly() {
        BodyCapture.textPreview(byteArrayOf(0xE9.toByte()), "text/plain; charset=ISO-8859-1", maxLength = 100)
        BodyCapture.textPreview("hi".encodeToByteArray(), "application/json", maxLength = 100)

        assertEquals(emptyList(), warnings)
    }
}
