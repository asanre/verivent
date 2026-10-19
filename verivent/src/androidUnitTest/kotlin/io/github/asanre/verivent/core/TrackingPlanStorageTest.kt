package io.github.asanre.verivent.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TrackingPlanStorageTest {

    private val dir: File = Files.createTempDirectory("verivent").toFile()
    private val plan = """{ "version": "3.0.0", "events": [] }"""

    @BeforeTest fun setup() = Inspector.reset()

    @AfterTest fun tearDown() {
        Inspector.reset()
        drainStorage()
        dir.deleteRecursively()
    }

    @Test
    fun aLoadedPlanIsRestoredByTheNextProcessThatSetsTheSameDirectory() {
        Inspector.configure(storageDir = "$dir/verivent")
        Inspector.loadTrackingPlan(plan)
        drainStorage()

        Inspector.reset() // what a restart leaves behind: nothing in memory
        assertNull(Inspector.trackingPlan.value)
        Inspector.configure(storageDir = "$dir/verivent")
        drainStorage()
        assertEquals("3.0.0", Inspector.trackingPlan.value?.version)
    }

    @Test
    fun configureReturnsBeforeTheStoredPlanIsRead() {
        File(dir, "tracking-plan.json").writeText(plan)
        whileStorageIsBusy {
            Inspector.configure(storageDir = dir.path)
            assertNull(Inspector.trackingPlan.value)
        }
        assertEquals("3.0.0", Inspector.trackingPlan.value?.version)
    }

    @Test
    fun clearingForgetsTheStoredPlan() {
        Inspector.configure(storageDir = dir.path)
        Inspector.loadTrackingPlan(plan)
        Inspector.clearTrackingPlan()
        drainStorage()
        assertFalse(File(dir, "tracking-plan.json").exists())
    }

    @Test
    fun clearingDeletesTheFileOffTheCallingThread() {
        File(dir, "tracking-plan.json").writeText(plan)
        Inspector.configure(storageDir = dir.path)
        drainStorage()
        whileStorageIsBusy {
            Inspector.clearTrackingPlan()
            assertNull(Inspector.trackingPlan.value)
            assertTrue(File(dir, "tracking-plan.json").exists())
        }
        assertFalse(File(dir, "tracking-plan.json").exists())
    }

    @Test
    fun aPlanLoadedWhileTheStoredOneIsRestoringWins() {
        File(dir, "tracking-plan.json").writeText(plan)
        whileStorageIsBusy {
            Inspector.configure(storageDir = dir.path)
            Inspector.loadTrackingPlan("""{ "version": "4.0.0", "events": [] }""")
        }
        assertEquals("4.0.0", Inspector.trackingPlan.value?.version)
    }

    @Test
    fun aCorruptStoredPlanIsIgnored() {
        File(dir, "tracking-plan.json").writeText("{")
        Inspector.configure(storageDir = dir.path)
        drainStorage()
        assertNull(Inspector.trackingPlan.value)
    }

    /** Runs [block] while the storage queue is held, then lets it drain. */
    private fun whileStorageIsBusy(block: () -> Unit) {
        val release = CountDownLatch(1)
        val started = CountDownLatch(1)
        CoroutineScope(Inspector.storage).launch {
            started.countDown()
            release.await()
        }
        started.await()
        try {
            block()
        } finally {
            release.countDown()
        }
        drainStorage()
    }

    private fun drainStorage() = runBlocking { withContext(Inspector.storage) {} }
}
