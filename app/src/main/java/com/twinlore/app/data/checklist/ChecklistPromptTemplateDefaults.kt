package com.twinlore.app.data.checklist

import com.twinlore.app.domain.ChecklistPromptTemplate

object ChecklistPromptTemplateDefaults {

    private val FORMAT_TAIL = """
        Format: `#` for tab titles (1–3 top-level groups), `##` for sections, `- [ ]` for each unchecked task.
        Return only the markdown list — no intro, no closing remarks, no code fences.
    """.trimIndent()

    // Helper: trim the description independently from FORMAT_TAIL, then join
    // with exactly one blank line. Doing them separately avoids trimIndent's
    // common-prefix logic getting confused by the interpolated multi-line value.
    private fun withTail(description: String): String =
        description.trimIndent() + "\n\n" + FORMAT_TAIL

    val templates: List<ChecklistPromptTemplate> = listOf(
        ChecklistPromptTemplate(
            id = "full-completion",
            name = "Full completion",
            content = withTail(
                """
                Generate a completionist checklist for {vault}. Cover every major category a 100% run would need: story milestones, collectables, side content, achievements, permanent missables.
                """,
            ),
        ),
        ChecklistPromptTemplate(
            id = "bosses-strategy",
            name = "Bosses & how to beat them",
            content = withTail(
                """
                List every boss in {vault}. Each boss is a section. Under each section, give items that together form a short, concrete strategy: weak points, recommended level/gear, key mechanics to dodge, and the order to attack phases.
                """,
            ),
        ),
        ChecklistPromptTemplate(
            id = "essential-items",
            name = "Essential items by progression",
            content = withTail(
                """
                For {vault}, list the essential items/upgrades a player should grab, ordered by when they become available in the game. Tabs = progression phases (e.g. Early / Mid / Late game). Sections = item categories. Items say what the thing is and how to get it.
                """,
            ),
        ),
        ChecklistPromptTemplate(
            id = "puzzle-solutions",
            name = "Puzzle hints",
            content = withTail(
                """
                For {vault}, list the puzzles the player will encounter. Each puzzle is a section. Items escalate: first item = a light nudge, middle items = partial hints, last item = the full solution — so the user can stop reading when they want.
                """,
            ),
        ),
        ChecklistPromptTemplate(
            id = "builds-progression",
            name = "Best builds by progression",
            content = withTail(
                """
                For {vault}, recommend character builds players commonly use. Tabs = progression phase or playstyle. Sections = builds. Items = the key gear/skills/stats and how to obtain each piece.
                """,
            ),
        ),
        ChecklistPromptTemplate(
            id = "metroidvania-exploration",
            name = "Metroidvania exploration",
            content = withTail(
                """
                Treat {vault} as a metroidvania. Tabs = regions. Sections = named areas within each region. Items = collectibles, ability-gated secrets, and return-trip unlocks the player should remember to come back for.
                """,
            ),
        ),
        ChecklistPromptTemplate(
            id = "jrpg-party-equipment",
            name = "JRPG party & equipment",
            content = withTail(
                """
                Treat {vault} as a JRPG. Tabs = party members and equipment routes. Sections group specific paths (e.g. a character's ultimate weapon quest). Items = steps and locations.
                """,
            ),
        ),
        ChecklistPromptTemplate(
            id = "soulslike-bosses",
            name = "Soulslike boss prep",
            content = withTail(
                """
                Treat {vault} as a Souls-like. List bosses in encounter order as sections. Each item is a specific attack pattern + how to dodge/counter it, or a loadout recommendation for that fight.
                """,
            ),
        ),
        ChecklistPromptTemplate(
            id = "puzzle-game-walkthrough",
            name = "Puzzle game walkthrough",
            content = withTail(
                """
                For {vault}, list levels or chapters as sections. Each section contains progressive hints: Hint 1 (nudge), Hint 2 (partial), Solution. The player can stop at whichever hint they need.
                """,
            ),
        ),
        ChecklistPromptTemplate(
            id = "strategy-build-order",
            name = "Strategy build orders",
            content = withTail(
                """
                For {vault} (strategy/4X game), list popular opening build orders or tech paths. Each build is a section. Items are ordered steps with timing/resource notes.
                """,
            ),
        ),
    )

    fun findById(id: String): ChecklistPromptTemplate? = templates.firstOrNull { it.id == id }
}
