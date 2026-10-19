package io.github.asanre.verivent.ui

import io.github.asanre.verivent.core.model.PlanIssue
import io.github.asanre.verivent.ui.internal.issueRoot
import io.github.asanre.verivent.ui.internal.lineUnder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class IssueRootTest {

    private val sent = setOf("tracks", "item", "item.id")

    @Test
    fun anIssueOnASentPropertyIsItsOwn() {
        assertEquals("tracks", issueRoot("tracks", sent))
        assertEquals("item.id", issueRoot("item.id", sent))
    }

    @Test
    fun aNestedPathBelongsToThePropertyItStartsFrom() {
        assertEquals("tracks", issueRoot("tracks[*].duration_ms", sent))
        assertEquals("item", issueRoot("item.price", sent))
        assertEquals("item.id", issueRoot("item.id.prefix", sent))
    }

    @Test
    fun aPropertyThatWasNotSentHasNoRow() {
        assertNull(issueRoot("search_term", sent))
        assertNull(issueRoot("tracksy", sent))
    }

    @Test
    fun nestedIssuesSayWhereUnderTheirRow() {
        val issue = PlanIssue("tracks[*].duration_ms", "Expected integer, got string · 60 of 60")
        assertEquals("tracks[*].duration_ms · Expected integer, got string · 60 of 60", issue.lineUnder("tracks"))
        assertEquals("Expected array, got string", PlanIssue("tracks", "Expected array, got string").lineUnder("tracks"))
    }
}
