package io.github.asanre.verivent.analytics

import io.github.asanre.verivent.core.model.KeyValue
import io.github.asanre.verivent.core.model.PlanEvent
import io.github.asanre.verivent.core.model.PlanIssue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class EventVariantsTest {

    private val schema = """{ "required": ["element_id", "action"],
        "properties": { "element_id": { "type": "string" }, "action": { "type": "string" },
                        "item_id": { "type": "string" }, "position": { "type": "integer" } } }"""

    private fun plan(variants: String, schema: String = this.schema) = TrackingPlan.parse(
        """{ "version": "1.0.0", "events": [ { "name": "ui_interaction", "channel": "firebase",
             "schema": $schema, "variants": $variants } ] }""",
    )

    private fun reason(variants: String, schema: String = this.schema) =
        assertFailsWith<IllegalArgumentException> { plan(variants, schema) }.message.orEmpty()

    private val share = """{ "id": "share_playlist", "when": { "element_id": "playlist_share" } }"""

    @Test
    fun aPlanWithVariantsLoadsAndToleratesADescription() {
        plan(
            """{ "by": ["element_id"], "list": [
                 { "id": "share_playlist", "description": "Share button on a playlist",
                   "when": { "element_id": "playlist_share" }, "required": ["item_id"],
                   "properties": { "action": { "const": "share" }, "item_id": { "pattern": "^pl_" } } },
                 { "id": "home_banner", "when": { "element_id": "home_hero_banner" },
                   "properties": { "action": { "enum": ["tap", "swipe"] } } } ] }""",
        )
    }

    @Test
    fun variantsHoldOnlyByAndList() {
        assertEquals(
            "ui_interaction.variants holds only by and list, not otherwise",
            reason("""{ "by": ["element_id"], "list": [$share], "otherwise": "base" }"""),
        )
    }

    @Test
    fun byIsANonEmptyListOfDistinctRequiredProperties() {
        assertEquals("ui_interaction.variants.by must be a non-empty array", reason("""{ "by": [], "list": [$share] }"""))
        assertEquals(
            "ui_interaction.variants.by lists element_id twice",
            reason("""{ "by": ["element_id", "element_id"], "list": [$share] }"""),
        )
        assertEquals(
            "ui_interaction.variants.by: screen is not a property of ui_interaction",
            reason("""{ "by": ["screen"], "list": [$share] }"""),
        )
        assertEquals(
            "ui_interaction.variants.by: item_id must be required, so a variant can always be told",
            reason("""{ "by": ["item_id"], "list": [ { "id": "a", "when": { "item_id": "x" } } ] }"""),
        )
    }

    @Test
    fun listIsANonEmptyArray() {
        assertEquals("ui_interaction.variants.list must be a non-empty array", reason("""{ "by": ["element_id"], "list": [] }"""))
    }

    @Test
    fun idsAreNonEmptyAndUnique() {
        assertEquals(
            "ui_interaction.variants.list[0].id must not be empty",
            reason("""{ "by": ["element_id"], "list": [ { "id": "", "when": { "element_id": "a" } } ] }"""),
        )
        assertEquals(
            "ui_interaction.variants: share_playlist is defined twice",
            reason("""{ "by": ["element_id"], "list": [$share, { "id": "share_playlist", "when": { "element_id": "other" } }] }"""),
        )
    }

    @Test
    fun whenSetsExactlyTheByPropertiesToPlainValues() {
        assertEquals(
            "ui_interaction.variants.share_playlist.when must set exactly element_id, action",
            reason("""{ "by": ["element_id", "action"], "list": [$share] }"""),
        )
        assertEquals(
            "ui_interaction.variants.share_playlist.when.element_id must be a plain value",
            reason("""{ "by": ["element_id"], "list": [ { "id": "share_playlist", "when": { "element_id": ["a"] } } ] }"""),
        )
    }

    @Test
    fun twoVariantsCantShareAWhen() {
        assertEquals(
            "ui_interaction.variants: share_playlist and share_again have the same when",
            reason("""{ "by": ["element_id"], "list": [$share, { "id": "share_again", "when": { "element_id": "playlist_share" } }] }"""),
        )
    }

    @Test
    fun aVariantOnlyNarrowsPropertiesTheEventAlreadyHas() {
        assertEquals(
            "ui_interaction.variants.share_playlist.properties: share_target is not a property of ui_interaction",
            reason(
                """{ "by": ["element_id"], "list": [ { "id": "share_playlist", "when": { "element_id": "playlist_share" },
                     "properties": { "share_target": { "const": "copy_link" } } } ] }""",
            ),
        )
        assertEquals(
            "ui_interaction.variants.share_playlist.required: share_target is not a property of ui_interaction",
            reason(
                """{ "by": ["element_id"], "list": [ { "id": "share_playlist", "when": { "element_id": "playlist_share" },
                     "required": ["share_target"] } ] }""",
            ),
        )
    }

    @Test
    fun aVariantDoesNotNarrowWhatTellsVariantsApart() {
        assertEquals(
            "ui_interaction.variants.share_playlist.properties: element_id tells variants apart, its when sets it",
            reason(
                """{ "by": ["element_id"], "list": [ { "id": "share_playlist", "when": { "element_id": "playlist_share" },
                     "properties": { "element_id": { "pattern": "^playlist" } } } ] }""",
            ),
        )
    }

    @Test
    fun aVariantNarrowsAPropertyOnlyWithConstEnumAndPattern() {
        assertEquals(
            "ui_interaction.variants.share_playlist.properties.position: a variant narrows a value with const, enum or pattern, not type",
            reason(
                """{ "by": ["element_id"], "list": [ { "id": "share_playlist", "when": { "element_id": "playlist_share" },
                     "properties": { "position": { "type": "string" } } } ] }""",
            ),
        )
    }

    private val generic by lazy {
        plan(
            """{ "by": ["element_id"], "list": [
                 { "id": "home_banner", "when": { "element_id": "home_hero_banner" }, "required": ["position"],
                   "properties": { "action": { "const": "tap" }, "item_id": { "pattern": "^bnr_" } } },
                 { "id": "like_track", "when": { "element_id": "player_like" },
                   "properties": { "action": { "enum": ["like", "unlike"] } } },
                 { "id": "share_playlist", "when": { "element_id": "playlist_share" },
                   "properties": { "action": { "const": "share" }, "item_id": { "pattern": "^pl_" } } } ] }""",
        )
    }

    private fun check(properties: Map<String, Any?>, hidden: Set<String> = emptySet()) =
        generic.check("ui_interaction", "firebase", hostValues(properties).hiding(hidden))!!

    @Test
    fun aSendThatFitsItsVariantComplies() {
        val check = check(mapOf("element_id" to "playlist_share", "action" to "share", "item_id" to "pl_42"))
        assertEquals(emptyList(), check.issues)
        assertEquals("share_playlist", check.variant)
    }

    @Test
    fun whatBreaksAVariantNamesItsIdButNeverTheValueSent() {
        val check = check(mapOf("element_id" to "playlist_share", "action" to "tap", "item_id" to "trk_42"))
        assertEquals(
            listOf(
                PlanIssue("action", "For share_playlist: Not the expected value · expects share"),
                PlanIssue("item_id", "For share_playlist: Doesn't match the pattern ^pl_"),
            ),
            check.issues,
        )
        assertEquals("share_playlist", check.variant)
    }

    @Test
    fun aVariantCanRequireAPropertyTheEventLeavesOptional() {
        assertEquals(
            listOf(PlanIssue("position", "For home_banner: Required, but not sent")),
            check(mapOf("element_id" to "home_hero_banner", "action" to "tap", "item_id" to "bnr_1")).issues,
        )
        assertEquals(emptyList(), check(mapOf("element_id" to "player_like", "action" to "like")).issues)
    }

    @Test
    fun aSendNoVariantFitsIsOneFindingOnWhatTellsThemApart() {
        val check = check(mapOf("element_id" to "cart_icon", "action" to "share", "item_id" to "trk_1"))
        assertEquals(
            listOf(PlanIssue("element_id", "Not a planned variant · expects home_hero_banner, player_like or playlist_share")),
            check.issues,
        )
        assertNull(check.variant)
    }

    @Test
    fun aRedactedDiscriminatorOnlyLetsTheCommonPartBeChecked() {
        val check = check(mapOf("element_id" to "playlist_share", "action" to 3, "item_id" to "trk_1"), hidden = setOf("element_id"))
        assertEquals(
            listOf(
                PlanIssue("action", "Expected string, got integer"),
                PlanIssue("element_id", "Redacted: can't tell which variant, only the common part was checked"),
            ),
            check.issues,
        )
        assertNull(check.variant)
    }

    @Test
    fun aDiscriminatorTheCommonPartRejectsIsReportedOnce() {
        assertEquals(listOf(PlanIssue("element_id", "Required, but not sent")), check(mapOf("action" to "tap")).issues)
        assertEquals(
            listOf(PlanIssue("element_id", "Expected string, got integer")),
            check(mapOf("element_id" to 7, "action" to "tap")).issues,
        )
    }

    @Test
    fun aPropertyTheCommonPartRejectsIsNotCheckedAgainForTheVariant() {
        assertEquals(
            listOf(PlanIssue("action", "Expected string, got integer")),
            check(mapOf("element_id" to "playlist_share", "action" to 3, "item_id" to "pl_1")).issues,
        )
    }

    @Test
    fun aRedactedNarrowedPropertyIsOnlyCheckedForPresence() {
        val check = check(mapOf("element_id" to "playlist_share", "action" to "share", "item_id" to "trk_1"), hidden = setOf("item_id"))
        assertEquals(emptyList(), check.issues)
    }

    @Test
    fun variantsByTwoPropertiesListEachCombination() {
        val plan = plan(
            """{ "by": ["element_id", "action"], "list": [
                 { "id": "share", "when": { "element_id": "playlist_share", "action": "share" } },
                 { "id": "copy", "when": { "element_id": "playlist_share", "action": "copy" } } ] }""",
        )
        val check = plan.check("ui_interaction", "firebase", hostValues(mapOf("element_id" to "playlist_share", "action" to "tap")))!!
        assertEquals(
            listOf(PlanIssue("element_id", "Not a planned variant of element_id + action · expects playlist_share + share or playlist_share + copy")),
            check.issues,
        )
    }

    @Test
    fun aDiscriminatorExportedBeforeTypesWereFitsByItsText() {
        val plan = plan(
            """{ "by": ["position"], "list": [ { "id": "first", "when": { "position": 1 } } ] }""",
            schema = """{ "required": ["position"], "properties": { "position": { "type": "integer" } } }""",
        )
        val untyped = exportedValues(listOf(KeyValue("position", "1", redacted = false, type = null)))
        assertEquals("first", plan.check("ui_interaction", "firebase", untyped)!!.variant)
    }

    @Test
    fun theSummaryListsEachEventsVariantsInThePlansOrder() {
        assertEquals(
            mapOf(PlanEvent("ui_interaction", "firebase") to listOf("home_banner", "like_track", "share_playlist")),
            generic.summary.variants,
        )
    }

    @Test
    fun anEventWithoutVariantsHasNone() {
        val plan = TrackingPlan.parse(
            """{ "version": "1", "events": [ { "name": "search", "channel": "firebase", "schema": {} } ] }""",
        )
        assertNull(plan.check("search", "firebase", hostValues(emptyMap()))!!.variant)
    }
}
