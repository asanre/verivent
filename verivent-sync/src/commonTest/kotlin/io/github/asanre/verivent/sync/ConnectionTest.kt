package io.github.asanre.verivent.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ConnectionTest {

    @Test
    fun readsTheProjectAddressAndBothKeys() {
        val connection = parseConnection("https://sb_publishable_abc:vv_123@ref.supabase.co")
        assertEquals("https://ref.supabase.co/rest/v1/rpc/ingest_events", connection.rpc("ingest_events"))
        assertEquals("sb_publishable_abc", connection.apiKey)
        assertEquals("vv_123", connection.key)
    }

    @Test
    fun keepsAPortForALocalProject() {
        val connection = parseConnection(" http://anon:vv_1@127.0.0.1:54321 ")
        assertEquals("http://127.0.0.1:54321/rest/v1/rpc/device_plan", connection.rpc("device_plan"))
    }

    @Test
    fun refusesWhatIsNotAConnection() {
        listOf("", "vv_123", "https://ref.supabase.co", "https://only-api-key@ref.supabase.co", "ftp://a:b@ref.supabase.co")
            .forEach { text -> assertFailsWith<IllegalArgumentException>(text) { parseConnection(text) } }
    }
}
