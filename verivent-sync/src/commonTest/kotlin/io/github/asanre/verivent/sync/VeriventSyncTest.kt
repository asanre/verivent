package io.github.asanre.verivent.sync

import io.github.asanre.verivent.ktor.Verivent
import io.ktor.client.plugins.pluginOrNull
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertNull

class VeriventSyncTest {

    @AfterTest fun tearDown() = VeriventSync.disconnect()

    @Test
    fun aMalformedConnectionNeverReachesTheApp() {
        listOf("", "vv_123", "https://ref.supabase.co").forEach { VeriventSync.connect(it) }
    }

    @Test
    fun itsOwnClientCarriesNoCapturePlugin() {
        syncHttpClient().use { client ->
            assertNull(client.pluginOrNull(Verivent))
        }
    }
}
