package com.catmap.dubai.ui.navigation

object Routes {
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val FORGOT = "forgot"
    const val VERIFY = "verify"

    // Bottom navigation
    const val MAP = "map"
    const val EXPLORE = "explore"
    const val MESSAGES = "messages"
    const val PROFILE = "profile"

    // Community cats, rescue and feeding stations (reached from Explore)
    const val CATS = "cats"
    const val RESCUE = "rescue"
    const val STATIONS = "stations"
    const val HELP_DIRECTORY = "help-directory"

    const val CAT = "cat/{id}"
    fun cat(id: String) = "cat/$id"
    const val CAT_EDIT = "cat-edit/{id}"
    fun catEdit(id: String) = "cat-edit/$id"
    const val CAT_MERGE = "cat-merge/{id}"
    fun catMerge(id: String) = "cat-merge/$id"
    const val PHOTO = "photo/{id}?catId={catId}"
    fun photo(id: String, catId: String? = null) = if (catId == null) "photo/$id" else "photo/$id?catId=$catId"

    const val NEW_SIGHTING = "sighting-new?catId={catId}"
    fun newSighting(catId: String? = null) = if (catId == null) "sighting-new" else "sighting-new?catId=$catId"
    const val SIGHTING = "sighting/{id}"
    fun sighting(id: String) = "sighting/$id"
    const val SIGHTING_EDIT = "sighting-edit/{id}"
    fun sightingEdit(id: String) = "sighting-edit/$id"

    const val NEW_RESCUE = "rescue-new?catId={catId}"
    fun newRescue(catId: String? = null) = if (catId == null) "rescue-new" else "rescue-new?catId=$catId"
    const val RESCUE_DETAIL = "rescue/{id}"
    fun rescue(id: String) = "rescue/$id"

    const val STATION = "station/{id}"
    fun station(id: String) = "station/$id"
    const val STATION_EDIT = "station-edit?id={id}"
    fun stationEdit(id: String? = null) = if (id == null) "station-edit" else "station-edit?id=$id"

    // Animal posts
    const val POST = "post/{id}"
    fun post(id: String) = "post/$id"
    const val NEW_POST = "post-new?category={category}&type={type}"
    fun newPost(category: String? = null, type: String? = null) = buildString {
        append("post-new")
        val params = listOfNotNull(category?.let { "category=$it" }, type?.let { "type=$it" })
        if (params.isNotEmpty()) append("?" + params.joinToString("&"))
    }
    const val EDIT_POST = "post-edit/{id}"
    fun editPost(id: String) = "post-edit/$id"
    const val POSTER = "poster/{id}"
    fun poster(id: String) = "poster/$id"
    const val MY_POSTS = "my-posts"
    const val FAVORITES = "favorites"
    const val AREA_ALERTS = "area-alerts"
    const val SEARCH = "search"

    // Adoption
    const val ADOPTION = "adoption"
    const val ADOPTION_REQUESTS = "adoption-requests"

    // Pet sitting
    const val SITTERS = "sitters"
    const val SITTER = "sitter/{uid}"
    fun sitter(uid: String) = "sitter/$uid"
    const val SITTER_EDIT = "sitter-edit"
    const val BOOK = "book/{uid}"
    fun book(uid: String) = "book/$uid"
    const val BOOKINGS = "bookings?tab={tab}"
    /** [asSitter] opens the sitter's side, where incoming requests are. */
    fun bookings(asSitter: Boolean = false) = if (asSitter) "bookings?tab=sitter" else "bookings"

    // Messaging
    const val CHAT = "chat/{id}?postId={postId}"
    fun chat(id: String, postId: String? = null) = if (postId == null) "chat/$id" else "chat/$id?postId=$postId"
    const val CHAT_WITH = "chat-with/{uid}?postId={postId}"
    fun chatWith(uid: String, postId: String? = null) = if (postId == null) "chat-with/$uid" else "chat-with/$uid?postId=$postId"

    const val PROFILE_EDIT = "profile-edit"
    const val USER = "user/{uid}"
    fun user(uid: String) = "user/$uid"
    const val ACHIEVEMENTS = "achievements"
    const val NOTIFICATIONS = "notifications"
    const val SETTINGS = "settings"

    const val ADMIN = "admin"
    const val ADMIN_REPORTS = "admin-reports"
    const val ADMIN_RESCUES = "admin-rescues"
    const val ADMIN_MERGES = "admin-merges"
    const val ADMIN_VOLUNTEERS = "admin-volunteers"
    const val ADMIN_MAINTENANCE = "admin-maintenance"
    const val ADMIN_SUBMISSIONS = "admin-submissions"
    const val ADMIN_LOG = "admin-log"
    const val ADMIN_POSTS = "admin-posts"
    const val ADMIN_USERS = "admin-users"
    const val ADMIN_SITTERS = "admin-sitters"

    const val PICK_LOCATION = "pick-location?lat={lat}&lng={lng}&radius={radius}&area={area}"
    fun pickLocation(lat: Double?, lng: Double?, radius: Double? = null, area: Boolean = false) = buildString {
        append("pick-location?area=$area")
        if (lat != null && lng != null) append("&lat=$lat&lng=$lng")
        if (radius != null) append("&radius=$radius")
    }

    /** Keys used to hand the picked location back to the previous screen. */
    const val PICKED_LAT = "picked_lat"
    const val PICKED_LNG = "picked_lng"
    const val PICKED_RADIUS = "picked_radius"

    val tabs = listOf(MAP, EXPLORE, MESSAGES, PROFILE)
    val authRoutes = setOf(LOGIN, REGISTER, FORGOT, VERIFY, HELP_DIRECTORY)
}
