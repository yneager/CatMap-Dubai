package com.catmap.dubai

import android.content.Context
import com.catmap.dubai.ai.CatAnalyzer
import com.catmap.dubai.data.AdminRepository
import com.catmap.dubai.data.AdoptionRepository
import com.catmap.dubai.data.AuthRepository
import com.catmap.dubai.data.ChatRepository
import com.catmap.dubai.data.FavoritesRepository
import com.catmap.dubai.data.PostRepository
import com.catmap.dubai.data.PostDraftStore
import com.catmap.dubai.data.HelpDirectoryRepository
import com.catmap.dubai.data.UserDataExportRepository
import com.catmap.dubai.data.AdminAnalyticsRepository
import com.catmap.dubai.data.SittingRepository
import com.catmap.dubai.data.VideoRepository
import com.catmap.dubai.util.Connectivity
import com.catmap.dubai.data.CatRepository
import com.catmap.dubai.data.ImageRepository
import com.catmap.dubai.data.LocalWipe
import com.catmap.dubai.util.AppRestart
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.launch
import com.catmap.dubai.data.ModerationRepository
import com.catmap.dubai.data.NotificationRepository
import com.catmap.dubai.data.RescueRepository
import com.catmap.dubai.data.SettingsRepository
import com.catmap.dubai.data.StationRepository
import com.catmap.dubai.data.UserRepository
import com.catmap.dubai.data.model.UserProfile
import com.catmap.dubai.location.LocationHelper
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn

/** Simple manual dependency container, created once by [CatMapApp]. */
class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** False until app/google-services.json is added (see SETUP.md). */
    val firebaseReady: Boolean = BuildConfig.FIREBASE_CONFIGURED && FirebaseApp.getApps(appContext).isNotEmpty()

    val settings = SettingsRepository(appContext)
    val drafts = PostDraftStore(appContext)
    val location by lazy { LocationHelper(appContext) }
    val analyzer by lazy { CatAnalyzer() }

    private val auth by lazy {
        FirebaseAuth.getInstance().apply { if (BuildConfig.EMULATOR_HOST.isNotEmpty()) useEmulator(BuildConfig.EMULATOR_HOST, 9099) }
    }
    private val localWipe = LocalWipe(appContext)
    private val db by lazy {
        FirebaseFirestore.getInstance().apply { if (BuildConfig.EMULATOR_HOST.isNotEmpty()) useEmulator(BuildConfig.EMULATOR_HOST, 8080) }
    }

    val notifications by lazy { NotificationRepository(db, auth) }
    val users by lazy { UserRepository(db, notifications) }
    val authRepo by lazy {
        AuthRepository(
            auth, db, users, notifications, images, videos, settings, drafts, dataExport, com.catmap.dubai.data.DemoVault(appContext),
            clearMapCopies = { map.clearAll() },
            localWipe = localWipe,
            afterSignOut = { message ->
                NotificationManagerCompat.from(appContext).cancelAll()
                AppRestart.relaunch(appContext, message)
            },
        )
    }

    /**
     * Finishes a logout in the restarted process ([LocalWipe]): signs out again if the sign-out didn't reach the disk,
     * clears the offline cache and deletes the photo cache and shared files. [CatMapApp] calls it first, before
     * anything uses Firestore: the clear is only allowed then, and Firestore queues it ahead of the first query. A
     * failed clear is retried on the next start.
     */
    fun finishPendingWipe() {
        if (!firebaseReady) return
        if (localWipe.signOutPending) {
            auth.signOut()
            localWipe.signOutDone()
        }
        if (localWipe.cachePending) {
            db.clearPersistence()
                .addOnSuccessListener { localWipe.cacheDone() }
                .addOnFailureListener { android.util.Log.w("CatMap", "Offline cache not cleared yet", it) }
            scope.launch(Dispatchers.IO) {
                images.clearCache()
                localWipe.deleteCacheFiles()
            }
        }
    }
    val images by lazy { ImageRepository(appContext, db) }
    val moderation by lazy { ModerationRepository(db, notifications) }
    val cats by lazy { CatRepository(db, users, notifications, moderation) }
    val rescues by lazy { RescueRepository(db, users, notifications, moderation) }
    val stations by lazy { StationRepository(db, users, notifications, moderation) }
    val posts by lazy { PostRepository(db, users, notifications, moderation, videos) }
    val helpDirectory by lazy { HelpDirectoryRepository(db) }
    val dataExport by lazy { UserDataExportRepository(appContext,db) }
    val analytics by lazy { AdminAnalyticsRepository(db) }
    /** The map's posts and cats: the area on screen, changes since the last sync, and a saved copy. */
    val map by lazy { com.catmap.dubai.data.MapRepository(db, appContext, scope) }
    val adoption by lazy { AdoptionRepository(db, notifications) }
    val sitting by lazy { SittingRepository(db, notifications, moderation, scope) }
    val chats by lazy { ChatRepository(db, users, notifications, images) }
    val favorites by lazy { FavoritesRepository(db) }
    val admin by lazy { AdminRepository(db, moderation, notifications) }
    val videos by lazy { VideoRepository(appContext) }
    val connectivity by lazy { Connectivity(appContext) }
    val updater by lazy { com.catmap.dubai.update.Updater(appContext, settings) }
    val aiServer by lazy { com.catmap.dubai.data.AiServer(db, auth) }
    val matches by lazy { com.catmap.dubai.data.MatchRepository(db, posts, users, notifications, chats, aiServer) }

    val session by lazy { Session(this) }
}

/** The signed-in account and its live profile. */
@OptIn(ExperimentalCoroutinesApi::class)
class Session(container: AppContainer) {
    val user: StateFlow<FirebaseUser?> = container.authRepo.user

    val profile: StateFlow<UserProfile?> = user
        .flatMapLatest { u -> if (u == null) flowOf(null) else container.users.profileFlow(u.uid) }
        .stateIn(container.scope, SharingStarted.Eagerly, null)

    init {
        // Badges are awarded from one place: whenever the signed-in user's counters change.
        profile
            .distinctUntilChanged { a, b -> a?.uid == b?.uid && a?.stats == b?.stats && a?.neighborhoods == b?.neighborhoods && a?.followedCats == b?.followedCats && a?.badges == b?.badges }
            .onEach { p -> if (p != null) runCatching { container.users.awardNewBadges(p) } }
            .launchIn(container.scope)
    }

    val uid: String? get() = user.value?.uid
}
