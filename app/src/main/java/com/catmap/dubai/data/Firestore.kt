package com.catmap.dubai.data

import android.content.Context
import android.util.Log
import androidx.annotation.StringRes
import com.catmap.dubai.R
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.tasks.await

object Col {
    const val CONFIG = "config"
    const val AI_JOBS = "aiJobs"
    const val USERS = "users"
    const val PRIVATE = "private"
    const val NOTIFICATIONS = "notifications"
    const val USERNAMES = "usernames"
    const val CATS = "cats"
    const val SIGHTINGS = "sightings"
    const val PRIVATE_LOCATIONS = "privateLocations"
    const val MEDICAL = "medicalRecords"
    const val RESCUES = "rescues"
    const val STATIONS = "stations"
    const val CHECKINS = "checkins"
    const val MAINTENANCE = "maintenance"
    const val MERGES = "mergeRequests"
    const val REPORTS = "contentReports"
    const val MOD_ACTIONS = "modActions"
    const val VOLUNTEER_APPS = "volunteerApplications"
    const val IMAGES = "images"
    const val POSTS = "posts"
    const val UPDATES = "updates"
    const val ADOPTION_REQUESTS = "adoptionRequests"
    const val SITTERS = "sitters"
    const val BOOKINGS = "bookings"
    const val REVIEWS = "reviews"
    const val CHATS = "chats"
    const val MESSAGES = "messages"
    const val BLOCKED = "blocked"
    const val FAVORITES = "favorites"
    const val AREA_FOLLOWS = "areaFollows"
    const val MATCHES = "matches"
    const val POST_EMBEDDINGS = "postEmbeddings"
}

fun now() = System.currentTimeMillis()

/** Bounded batches avoid loading every JPEG blob into memory during export/cleanup. */
fun Query.pages(size:Long=10):Flow<List<DocumentSnapshot>> = kotlinx.coroutines.flow.flow {
    var cursor:DocumentSnapshot?=null
    while(true) {
        val page=(cursor?.let {startAfter(it)} ?: this@pages).limit(size).get().await().documents
        if(page.isEmpty()) break
        emit(page)
        if(page.size<size) break
        cursor=page.last()
    }
}

/** An error with a message that can be shown to people as-is. */
class AppException(@StringRes val messageRes: Int) : Exception()

/** Errors from background listeners end up here and are shown as a snackbar. */
object AppErrors {
    private lateinit var appContext: Context
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages
    val listenerError = MutableStateFlow<String?>(null)
    val retryVersion = MutableStateFlow(0L)

    fun retryListeners() {
        listenerError.value = null
        retryVersion.value += 1
    }

    suspend fun awaitListenerRetry(error: Throwable): Boolean {
        val version = retryVersion.value
        report(error)
        if (::appContext.isInitialized) listenerError.value = error.userMessage(appContext)
        retryVersion.first { it > version }
        return true
    }

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun report(t: Throwable) {
        Log.w("CatMap", "Background error", t)
        // Listeners are torn down with permission errors right after sign-out; those are expected.
        val signedOut = runCatching { FirebaseAuth.getInstance().currentUser == null }.getOrDefault(true)
        if (signedOut) return
        com.catmap.dubai.util.CrashReports.record(t)
        if (::appContext.isInitialized) _messages.tryEmit(t.userMessage(appContext))
    }

    fun show(message: String) {
        _messages.tryEmit(message)
    }
}

fun Throwable.userMessage(context: Context): String {
    val res = when (this) {
        is AppException -> messageRes
        is FirebaseAuthWeakPasswordException -> R.string.err_weak_password
        is FirebaseAuthUserCollisionException -> R.string.err_email_in_use
        is FirebaseAuthInvalidUserException -> R.string.err_no_account
        is FirebaseAuthInvalidCredentialsException -> R.string.err_wrong_credentials
        is FirebaseAuthRecentLoginRequiredException -> R.string.err_recent_login
        is FirebaseNetworkException -> R.string.err_network
        is FirebaseTooManyRequestsException -> R.string.err_too_many_requests
        is FirebaseFirestoreException -> when (code) {
            FirebaseFirestoreException.Code.PERMISSION_DENIED -> R.string.err_permission
            FirebaseFirestoreException.Code.UNAVAILABLE -> R.string.err_network
            FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED -> R.string.err_too_many_requests
            else -> null
        }
        else -> null
    }
    return if (res != null) context.getString(res) else (localizedMessage ?: context.getString(R.string.err_generic))
}

/** With [metadata], a snapshot also arrives when only "from the phone's cache / confirmed by the server" changes. */
fun Query.snapshotFlow(metadata: Boolean = false): Flow<QuerySnapshot> = callbackFlow {
    val changes = if (metadata) MetadataChanges.INCLUDE else MetadataChanges.EXCLUDE
    val registration = addSnapshotListener(changes) { snapshot, error ->
        if (error != null) {
            close(error)
            return@addSnapshotListener
        }
        if (snapshot != null) trySend(snapshot)
    }
    awaitClose { registration.remove() }
}

fun DocumentReference.snapshotFlow(): Flow<DocumentSnapshot> = callbackFlow {
    val registration = addSnapshotListener { snapshot, error ->
        if (error != null) {
            close(error)
            return@addSnapshotListener
        }
        if (snapshot != null) trySend(snapshot)
    }
    awaitClose { registration.remove() }
}

/**
 * Converts each document on its own. A document with a field of the wrong type (written by a modified app; the rules
 * can't check every list entry) is skipped, instead of making the whole list, and every phone's map with it, fail.
 */
inline fun <reified T : Any> QuerySnapshot.safeObjects(): List<T> = documents.mapNotNull { document ->
    runCatching { document.toObject(T::class.java) }
        .onFailure { android.util.Log.w("CatMap", "Skipped malformed ${document.reference.path}", it) }
        .getOrNull()
}

inline fun <reified T : Any> DocumentSnapshot.safeObject(): T? =
    runCatching { toObject(T::class.java) }
        .onFailure { android.util.Log.w("CatMap", "Skipped malformed ${reference.path}", it) }
        .getOrNull()

inline fun <reified T : Any> Query.listFlow(): Flow<List<T>> =
    snapshotFlow()
        .map { it.safeObjects<T>() }
        .retryWhen { error, _ -> AppErrors.awaitListenerRetry(error) }

inline fun <reified T : Any> DocumentReference.objectFlow(): Flow<T?> =
    snapshotFlow()
        .map { it.safeObject<T>() }
        .retryWhen { error, _ -> AppErrors.awaitListenerRetry(error) }

/** One finished document load; [value] is null when the document doesn't exist or can't be read. */
data class Loaded<out T>(val value: T?)

/**
 * Lets a screen tell "still loading" (nothing collected yet) from "gone" (`Loaded(null)`), so links to
 * deleted items show a message instead of a spinner that never stops.
 */
fun <T> Flow<T?>.loaded(): Flow<Loaded<T>> = map { Loaded(it) }
