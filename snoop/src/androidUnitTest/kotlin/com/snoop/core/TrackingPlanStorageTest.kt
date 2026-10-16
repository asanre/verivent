package com.snoop.core

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class TrackingPlanStorageTest {

    private val dir: File = Files.createTempDirectory("snoop").toFile()
    private val plan = """{ "version": "3.0.0", "events": [] }"""

    @BeforeTest fun setup() = SnoopCore.reset()

    @AfterTest fun tearDown() {
        SnoopCore.reset()
        dir.deleteRecursively()
    }

    @Test
    fun aLoadedPlanIsRestoredByTheNextProcessThatSetsTheSameDirectory() {
        SnoopCore.configure(storageDir = "$dir/snoop")
        SnoopCore.loadTrackingPlan(plan)

        SnoopCore.reset() // what a restart leaves behind: nothing in memory
        assertNull(SnoopCore.trackingPlan.value)
        SnoopCore.configure(storageDir = "$dir/snoop")
        assertEquals("3.0.0", SnoopCore.trackingPlan.value?.version)
    }

    @Test
    fun clearingForgetsTheStoredPlan() {
        SnoopCore.configure(storageDir = dir.path)
        SnoopCore.loadTrackingPlan(plan)
        SnoopCore.clearTrackingPlan()
        assertFalse(File(dir, "tracking-plan.json").exists())
    }

    @Test
    fun aCorruptStoredPlanIsIgnored() {
        File(dir, "tracking-plan.json").writeText("{")
        SnoopCore.configure(storageDir = dir.path)
        assertNull(SnoopCore.trackingPlan.value)
    }
}
