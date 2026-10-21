package com.catmap.dubai.util

/**
 * The screens an alert may open: the routes `firestore.rules` accepts in an alert from someone else (`route` in the
 * alert check), plus the inbox. Anything else, from a tapped notification or an inbox entry, opens nothing
 * (threat model S10). Ids start with a letter or digit: "post/.." would be an invalid Firestore document and crash the
 * screen that opens it.
 */
object AppRoutes {
    private val alertRoute = Regex(
        "(post|cat|sighting|rescue|station|sitter|user|match|chat)/[A-Za-z0-9][A-Za-z0-9_.-]{0,299}" +
            "|adoption-requests|achievements|profile|bookings|bookings[?]tab=(sitter|owner)|notifications",
    )

    fun alert(route: String?): String? = route?.takeIf { alertRoute.matches(it) }
}
