package io.github.asanre.verivent.core

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class TrackingPlanStorageTest {

    private val dir: File = Files.createTempDirectory("verivent").toFile()
    private val plan = """{ "version": "3.0.0", "events": [] }"""

    @BeforeTest fun setup() = Inspector.reset()

    @AfterTest fun tearDown() {
        Inspector.reset()
        dir.deleteRecursively()
    }

    @Test
    fun aLoadedPlanIsRestoredByTheNextProcessThatSetsTheSameDirectory() {
        Inspector.configure(storageDir = "$dir/verivent")
        Inspector.loadTrackingPlan(plan)

        Inspector.reset() // what a restart leaves behind: nothing in memory
        assertNull(Inspector.trackingPlan.value)
        Inspector.configure(storageDir = "$dir/verivent")
        assertEquals("3.0.0", Inspector.trackingPlan.value?.version)
    }

    @Test
    fun clearingForgetsTheStoredPlan() {
        Inspector.configure(storageDir = dir.path)
        Inspector.loadTrackingPlan(plan)
        Inspector.clearTrackingPlan()
        assertFalse(File(dir, "tracking-plan.json").exists())
    }

    @Test
    fun aCorruptStoredPlanIsIgnored() {
        File(dir, "tracking-plan.json").writeText("{")
        Inspector.configure(storageDir = dir.path)
        assertNull(Inspector.trackingPlan.value)
    }
}
