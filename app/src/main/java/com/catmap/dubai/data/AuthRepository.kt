package com.catmap.dubai.data

import com.catmap.dubai.R
import com.catmap.dubai.data.model.Roles
import com.catmap.dubai.data.model.UserProfile
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

class AuthRepository(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore,
    private val users: UserRepository,
    private val notifications: NotificationRepository,
    private val images: ImageRepository,
    private val settings: SettingsRepository,
    private val drafts: PostDraftStore,
    private val dataExport: UserDataExportRepository,
    val demoVault: DemoVault,
    /** Deletes the map's saved copies ([MapRepository.clearAll]). */
    private val clearMapCopies: () -> Unit = {},
    /** Marks Firestore's offline cache and the photo cache for clearing at the next start. */
    private val localWipe: LocalWipe? = null,
    /** Runs once signed out (with a message to show, or null): removes shown alerts and restarts the app. */
    private val afterSignOut: (message: Int?) -> Unit = {},
) {
    private val _user = MutableStateFlow(auth.currentUser)
    val user: StateFlow<FirebaseUser?> = _user.asStateFlow()

    init {
        auth.addAuthStateListener { _user.value = it.currentUser }
    }

    val currentUid: String? get() = auth.currentUser?.uid

    suspend fun register(email: String, password: String, username: String, displayName: String) {
        val name = username.trim()
        UserRepository.validateUsername(name)
        if (password.length < 8) throw AppException(R.string.err_weak_password)
        // Checked up front so most clashes are caught before an account is created.
        if (!users.isUsernameFree(name)) throw AppException(R.string.err_username_taken)

        val user = auth.createUserWithEmailAndPassword(email.trim(), password).await().user
            ?: throw AppException(R.string.err_generic)
        try {
            createProfile(user, name, displayName.ifBlank { name })
        } catch (e: Exception) {
            // Don't leave an account behind without a profile (e.g. the username was taken meanwhile).
            runCatching { user.delete().await() }
            throw e
        }
        runCatching { user.sendEmailVerification().await() }
    }

    private suspend fun createProfile(user: FirebaseUser, username: String, displayName: String, role: String = Roles.USER) {
        db.runBatch { b ->
            b.set(db.collection(Col.USERNAMES).document(username.lowercase()), mapOf("uid" to user.uid))
            b.set(
                users.ref(user.uid),
                UserProfile(username = username, displayName = displayName.trim().take(60), role = role, createdAt = now()),
            )
            b.set(
                users.ref(user.uid).collection(Col.PRIVATE).document("account"),
                mapOf("email" to (user.email ?: ""), "createdAt" to now()),
            )
        }.await()
        notifications.send(listOf(user.uid), NotifType.WELCOME, mapOf("user" to username))
    }

    /**
     * Signs in with an email address, or with a username for accounts that allow it: the demo accounts
     * ([DemoAccounts], e.g. "admin"), and accounts whose username document carries a `loginEmail`
     * (only admin tooling can set it).
     */
    suspend fun login(emailOrUsername: String, password: String) {
        val input = emailOrUsername.trim()
        DemoAccounts.find(input)?.let { demo ->
            signInDemo(demo, password)
            return
        }
        val email = if ("@" in input) input else {
            val key = input.removePrefix("@").lowercase()
            if (key.isBlank()) throw AppException(R.string.err_wrong_credentials)
            db.collection(Col.USERNAMES).document(key).get().await().getString("loginEmail")
                ?: throw AppException(R.string.err_username_login)
        }
        // "No account for this email" and "wrong password" get the same answer, so the sign-in screen can't be used to
        // find out who has an account.
        val user = try {
            auth.signInWithEmailAndPassword(email, password).await().user ?: return
        } catch (e: FirebaseAuthInvalidUserException) {
            throw AppException(R.string.err_wrong_credentials)
        }
        ensureProfile(user)
    }

    /** Signs in to a demo account and remembers the shared demo password on this device for switching. */
    private suspend fun signInDemo(account: DemoAccounts.Account, password: String) {
        val user = auth.signInWithEmailAndPassword(account.email, password).await().user ?: return
        ensureDemoProfile(user, account)
        demoVault.save(password)
    }

    /**
     * Switches to another demo account with the stored demo password. Signing in replaces the current
     * account directly (no signed-out moment, so the app doesn't jump to the login screen), and a failure
     * leaves the current account signed in. A password that no longer works (the script was re-run with a
     * new one) is forgotten, so the person signs in again.
     */
    suspend fun switchDemoAccount(account: DemoAccounts.Account) {
        val password = demoVault.password() ?: throw AppException(R.string.err_demo_sign_in_again)
        try {
            signInDemo(account, password)
        } catch (e: FirebaseAuthInvalidCredentialsException) {
            demoVault.clear()
            throw AppException(R.string.err_demo_sign_in_again)
        } catch (e: FirebaseAuthInvalidUserException) {
            throw AppException(R.string.err_demo_missing)
        }
    }

    /**
     * Demo accounts are created without a profile; the first sign-in makes one with the exact demo
     * username. The admin account (custom claim `catmapAdmin`) gets the admin role, which the rules allow
     * only for that claim, and gets it back if someone changed it.
     */
    private suspend fun ensureDemoProfile(user: FirebaseUser, account: DemoAccounts.Account) {
        val admin = user.getIdToken(false).await().claims["catmapAdmin"] == true
        val role = if (admin) Roles.ADMIN else Roles.USER
        val snapshot = users.ref(user.uid).get().await()
        if (!snapshot.exists()) {
            // Fall back to a numbered name if a real account already took the demo username.
            var name = account.username
            while (!users.isUsernameFree(name)) name = account.username.take(14) + (1000..9999).random()
            createProfile(user, name, account.displayName, role)
        } else if (admin && snapshot.getString("role") != Roles.ADMIN) {
            users.ref(user.uid).update("role", Roles.ADMIN).await()
        }
    }

    /** True when the signed-in account is one of the demo accounts. */
    fun isDemoAccount(): Boolean = DemoAccounts.isDemoEmail(auth.currentUser?.email)

    /** Recovers from a registration that created the account but not the profile. */
    private suspend fun ensureProfile(user: FirebaseUser) {
        if (users.ref(user.uid).get().await().exists()) return
        // Match the same ASCII alphabet accepted by validateUsername, including short/non-Latin emails.
        val base = (user.email ?: "cat").substringBefore('@')
            .filter { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '_' }
            .take(14).ifBlank { "catfriend" }.padEnd(3, '0')
        var candidate = base
        while (!users.isUsernameFree(candidate)) candidate = base.take(14) + (1000..9999).random()
        createProfile(user, candidate, candidate)
    }

    /**
     * Refreshes the account from the server. When the address has just been verified the ID token
     * is refreshed too, so the security rules immediately see `email_verified == true`.
     */
    suspend fun reloadVerified(): Boolean {
        val user = auth.currentUser ?: return false
        user.reload().await()
        val verified = auth.currentUser?.isEmailVerified == true
        if (verified) user.getIdToken(true).await()
        _user.value = auth.currentUser
        return verified
    }

    suspend fun resendVerification() {
        auth.currentUser?.sendEmailVerification()?.await()
    }

    /** The screen always says "if an account exists, a link is on its way", whether or not one does. */
    suspend fun sendPasswordReset(email: String) {
        try {
            auth.sendPasswordResetEmail(email.trim()).await()
        } catch (e: FirebaseAuthInvalidUserException) {
            // No account (or a disabled one): answer exactly as if the link had been sent.
        }
    }

    fun email(): String = auth.currentUser?.email ?: ""

    /**
     * Signs out and leaves nothing of the account readable on the phone: the demo password and the map copies go now,
     * Firestore's offline cache, the photo cache and shared files when the app restarts ([LocalWipe]). Drafts stay;
     * they're stored per account and come back on the next sign-in.
     */
    fun logout() {
        localWipe?.request()
        demoVault.clear()
        clearMapCopies()
        auth.signOut()
        afterSignOut(null)
    }

    /**
     * Deletes the account after re-checking the password. Authored posts, photos, exact locations,
     * inbox and profile are removed; shared cat profiles and verified care/moderation history stay.
     * Source deletion and aggregate changes are transactional so partial cleanup can be retried.
     */
    suspend fun deleteAccount(password: String, profile: UserProfile?) {
        val user = auth.currentUser ?: return
        val credential = EmailAuthProvider.getCredential(user.email ?: "", password)
        user.reauthenticate(credential).await()
        val uid = user.uid
        val savedProfile = users.ref(uid).get().await().toObject(UserProfile::class.java) ?: profile
        CommunityAccountCleanup(db).clean(uid)

        // Each transaction includes the source deletion and its aggregate change. A retry after
        // partial cleanup sees a missing source and cannot decrement the count a second time.
        val sightings = db.collection(Col.SIGHTINGS).whereEqualTo("userId", uid).get().await().documents
        for (sighting in sightings) {
            db.runTransaction { tx ->
                val fresh = tx.get(sighting.reference)
                if (fresh.exists()) {
                    val catId = fresh.getString("catId").orEmpty()
                    val catRef = catId.takeIf { it.isNotBlank() }?.let { db.collection(Col.CATS).document(it) }
                    val cat = catRef?.let(tx::get)
                    tx.delete(sighting.reference)
                    tx.delete(db.collection(Col.PRIVATE_LOCATIONS).document(sighting.id))
                    if (catRef != null && cat?.exists() == true) tx.update(catRef, "sightingCount", ((cat.getLong("sightingCount") ?: 0) - 1).coerceAtLeast(0))
                }
            }.await()
        }
        val checkins = db.collection(Col.CHECKINS).whereEqualTo("userId", uid).get().await().documents
        for (checkin in checkins) {
            db.runTransaction { tx ->
                val fresh = tx.get(checkin.reference)
                if (fresh.exists()) {
                    val stationId = fresh.getString("stationId").orEmpty()
                    val stationRef = stationId.takeIf { it.isNotBlank() }?.let { db.collection(Col.STATIONS).document(it) }
                    val station = stationRef?.let(tx::get)
                    tx.delete(checkin.reference)
                    if (stationRef != null && station?.exists() == true) {
                        val updates = mutableMapOf<String, Any>("checkinCount" to ((station.getLong("checkinCount") ?: 0) - 1).coerceAtLeast(0))
                        if (station.getLong("lastCheckinAt") == fresh.getLong("at")) {
                            updates["lastCheckinAt"] = 0L
                            updates["lastCheckinBy"] = ""
                        }
                        tx.update(stationRef, updates)
                    }
                }
            }.await()
        }

        val photos = mutableListOf<DocumentReference>()
        db.collection(Col.IMAGES).whereEqualTo("ownerId", uid).pages().collect {page -> photos.addAll(page.map {it.reference})}
        val photoIds = photos.map { it.id }.filterNot { it.endsWith("_t") }
        // Gallery and cover references must be detached before deleting the actual JPEGs.
        for (chunk in photoIds.chunked(10)) {
            val cats = db.collection(Col.CATS).whereArrayContainsAny("photoIds", chunk).get().await().documents
            for (cat in cats) db.runTransaction { tx ->
                val fresh = tx.get(cat.reference)
                if (fresh.exists()) {
                    val remaining = (fresh.get("photoIds") as? List<*>)?.filterIsInstance<String>().orEmpty() - chunk.toSet()
                    val updates = mutableMapOf<String, Any>("photoIds" to remaining)
                    if (fresh.getString("coverPhotoId") in chunk) updates["coverPhotoId"] = remaining.firstOrNull().orEmpty()
                    tx.update(cat.reference, updates)
                }
            }.await()
        }
        for (field in listOf("followers", "contributors")) {
            val cats = db.collection(Col.CATS).whereArrayContains(field, uid).get().await().documents
            for (cat in cats) cat.reference.update(field, FieldValue.arrayRemove(uid)).await()
        }

        val refs = buildList {
            addAll(db.collection(Col.PRIVATE_LOCATIONS).whereEqualTo("ownerId", uid).get().await().documents.map { it.reference })
            addAll(photos)
            for ((collection, ownerField) in listOf(Col.RESCUES to "reporterId", Col.MAINTENANCE to "userId", Col.REPORTS to "reporterId", Col.MERGES to "requestedBy")) {
                addAll(db.collection(collection).whereEqualTo(ownerField, uid).get().await().documents.map { it.reference })
            }
            add(db.collection(Col.VOLUNTEER_APPS).document(uid))
            addAll(users.ref(uid).collection(Col.NOTIFICATIONS).get().await().documents.map { it.reference })
        }
        deleteDocuments(refs)
        // The AI job can hold the last photos sent to the PC. A job still waiting for its answer can't be deleted for
        // its first seconds (rules); the PC removes answered jobs within the hour.
        runCatching { db.collection(Col.AI_JOBS).document(uid).delete().await() }
        // Keep the profile until the last batch, so role checks still work throughout cleanup.
        deleteDocuments(buildList {
            add(users.ref(uid).collection(Col.PRIVATE).document("account"))
            savedProfile?.username?.takeIf { it.isNotBlank() }?.let { add(db.collection(Col.USERNAMES).document(it.lowercase())) }
            add(users.ref(uid))
        })
        user.delete().await()
        // Local cache trouble must not turn a completed account deletion into a reported failure.
        runCatching {
            settings.clearDeliveredNotifications(uid)
            demoVault.clear()
            drafts.clear(uid)
            dataExport.clear(uid)
            images.clearCache()
            clearMapCopies()
            localWipe?.request()
        }.onFailure { android.util.Log.w("CatMap", "Local cleanup after account deletion failed", it) }
        afterSignOut(R.string.account_deleted)
    }

    private suspend fun deleteDocuments(refs: List<DocumentReference>) {
        refs.chunked(400).forEach { chunk -> db.runBatch { b -> chunk.forEach(b::delete) }.await() }
    }
}
