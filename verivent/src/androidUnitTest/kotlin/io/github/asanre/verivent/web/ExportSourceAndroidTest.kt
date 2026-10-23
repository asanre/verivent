package io.github.asanre.verivent.web

import io.github.asanre.verivent.core.InternalVeriventApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExportSourceAndroidTest {

    @Test
    fun theSourceIsAndroid() {
        assertEquals("android", exportSource?.platform)
    }

    // A host unit test never creates the provider, so the app's version is unknown: left out, not null.
    @OptIn(InternalVeriventApi::class)
    @Test
    fun anUnknownAppVersionIsLeftOut() {
        assertNull(exportSource?.appVersion)
        val opener = Json.parseToJsonElement(encodeExport(emptyList())).jsonArray.single().jsonObject
        assertTrue("appVersion" !in opener.getValue("source").jsonObject)
    }
}
