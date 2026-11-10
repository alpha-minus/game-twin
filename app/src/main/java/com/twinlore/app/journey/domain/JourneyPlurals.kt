package com.twinlore.app.journey.domain

/**
 * "1 unlock" / "2 unlocks": a count and a noun that only takes an "s" when it should. Journey copy
 * builds these counts in several places, and "1 unlocks" reads like a bug to the person seeing it.
 */
fun pluralize(count: Int, singular: String, plural: String = "${singular}s"): String =
    "$count ${if (count == 1) singular else plural}"
