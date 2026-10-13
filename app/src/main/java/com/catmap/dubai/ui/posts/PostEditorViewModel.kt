package com.catmap.dubai.ui.posts

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.catmap.dubai.AppContainer
import com.catmap.dubai.R
import com.catmap.dubai.data.AppException
import com.catmap.dubai.data.Dubai
import com.catmap.dubai.data.LocationPrivacy
import com.catmap.dubai.data.PostDraft
import com.catmap.dubai.data.PostRepository
import com.catmap.dubai.data.SavedPostDraft
import com.catmap.dubai.data.AppErrors
import androidx.compose.runtime.snapshotFlow
import android.graphics.BitmapFactory
import kotlinx.coroutines.Job
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.cancelAndJoin
import java.util.UUID
import com.catmap.dubai.data.model.AdoptionStatus
import com.catmap.dubai.data.model.AgeGroup
import com.catmap.dubai.data.model.AnimalType
import com.catmap.dubai.data.model.CatSex
import com.catmap.dubai.data.model.ContactMethod
import com.catmap.dubai.data.model.Post
import com.catmap.dubai.data.model.PostCategory
import com.catmap.dubai.data.model.UserProfile
import com.catmap.dubai.data.model.labeledFrom
import com.catmap.dubai.data.now
import com.catmap.dubai.util.ImageUtils
import com.catmap.dubai.util.ProcessedPhoto
import com.catmap.dubai.ai.PostAppearance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A photo in the form: already uploaded (editing) or picked on this phone. */
sealed interface FormPhoto {
    data class Remote(val id: String) : FormPhoto
    class Local(val photo: ProcessedPhoto) : FormPhoto {
        val draftId: String = UUID.randomUUID().toString()
        val preview: Bitmap get() = photo.analysis
    }
}

enum class PostStep { TYPE, PHOTOS, DETAILS, PLACE, CONTACT, REVIEW }

/**
 * A nearby street-animal post that may show the same animal. [similarity] is the AI server's cosine score,
 * or the on-device colour similarity when [byServer] is false. [verdict] is "same" or "maybe".
 */
data class SameAnimalCandidate(val post: Post, val similarity: Double, val verdict: String, val byServer: Boolean, val distanceKm: Double)

@OptIn(FlowPreview::class)
/** Street animals within this distance are checked for being the same animal (public positions are blurred). */
const val SAME_ANIMAL_RADIUS_KM = 1.0
/** On-device fallback: colour similarity needed before a nearby post is suggested without the AI server. */
private const val LOCAL_MAYBE = 0.85

class PostEditorViewModel(
    private val container: AppContainer,
    val postId: String?,
    presetCategory: String?,
    presetType: String?,
) : ViewModel() {
    var loaded by mutableStateOf(postId == null)
        private set
    var existing: Post? = null
        private set

    /** Starts on details when the category was already chosen from the Post menu. */
    var step by mutableIntStateOf(if (presetCategory != null) PostStep.PHOTOS.ordinal else PostStep.TYPE.ordinal)

    var category by mutableStateOf(presetCategory?.takeIf { c -> PostCategory.entries.any { it.key == c } } ?: PostCategory.LOST.key)
    var animalType by mutableStateOf(presetType?.takeIf { t -> AnimalType.entries.any { it.key == t } } ?: AnimalType.CAT.key)
    var name by mutableStateOf("")
    var description by mutableStateOf("")
    var color by mutableStateOf("")
    var breed by mutableStateOf("")
    var features by mutableStateOf("")
    var sex by mutableStateOf(CatSex.UNKNOWN.key)
    var ageGroup by mutableStateOf(AgeGroup.UNKNOWN.key)
    val photos = mutableStateListOf<FormPhoto>()
    var processing by mutableStateOf(false)
        private set
    var videoUri by mutableStateOf<Uri?>(null)
    var videoPath by mutableStateOf("")
    var lat by mutableStateOf<Double?>(null)
    var lng by mutableStateOf<Double?>(null)
    var radius by mutableStateOf(0.0)
    var approximate by mutableStateOf(presetCategory == PostCategory.ADOPTION.key)
    var lastSeenAt by mutableStateOf(now())
    var reward by mutableStateOf("")
    var contactMethod by mutableStateOf(ContactMethod.IN_APP.key)
    var phone by mutableStateOf("")
    var email by mutableStateOf("")
    var health by mutableStateOf("")
    var vaccinated by mutableStateOf(false)
    var neutered by mutableStateOf(false)
    var microchip by mutableStateOf("unknown")
    var publishing by mutableStateOf(false)
        private set
    var duplicates by mutableStateOf<List<Post>>(emptyList())
        private set

    /** Street posts: nearby posts that may show the same animal, best first. */
    var sameAnimal by mutableStateOf<List<SameAnimalCandidate>>(emptyList())
        private set
    var sameAnimalChecking by mutableStateOf(false)
        private set
    /** Candidates the person said are a different animal. */
    val notSameAnimal = mutableStateListOf<String>()
    private var sameAnimalKey: String? = null

    private val removedRemote = mutableListOf<String>()
    var savedDraft by mutableStateOf<SavedPostDraft?>(null)
        private set
    private var draftReady by mutableStateOf(false)
    private var draftJob: Job? = null
    private var draftPublished = false
    private var preparedFingerprint by mutableStateOf<List<Double>>(emptyList())

    private fun snapshotDraft() = SavedPostDraft(mapOf(
        "step" to step.toString(), "category" to category, "animalType" to animalType,
        "name" to name, "description" to description, "color" to color, "breed" to breed, "features" to features,
        "sex" to sex, "ageGroup" to ageGroup, "lat" to lat.toString(), "lng" to lng.toString(),
        "radius" to radius.toString(), "approximate" to approximate.toString(), "lastSeenAt" to lastSeenAt.toString(),
        "reward" to reward, "contactMethod" to contactMethod, "phone" to phone, "email" to email,
        "health" to health, "vaccinated" to vaccinated.toString(), "neutered" to neutered.toString(),
        "videoPath" to videoPath, "microchip" to microchip, "fingerprint" to preparedFingerprint.joinToString(","),
    ), photos.map { when(it) { is FormPhoto.Local -> "local:${it.draftId}"; is FormPhoto.Remote -> "remote:${it.id}" } })

    fun saveDraftNow() {
        val uid=container.session.uid ?: return
        if(postId!=null || !draftReady || savedDraft!=null || draftPublished) return
        if(step==0 && name.isBlank() && description.isBlank() && features.isBlank() && photos.isEmpty() && lat==null) return
        val snapshot=snapshotDraft()
        val images=photos.filterIsInstance<FormPhoto.Local>().associate {it.draftId to it.photo.full}
        container.scope.launch {
            if(container.session.uid==uid) runCatching {container.drafts.save(uid,snapshot,images)}.onFailure {AppErrors.report(it)}
        }
    }

    fun discardDraft() {
        val uid = container.session.uid ?: return
        viewModelScope.launch { container.drafts.clear(uid); savedDraft = null }
    }

    fun continueDraft() {
        val uid = container.session.uid ?: return
        val draft = savedDraft ?: return
        viewModelScope.launch {
            val f = draft.fields
            category = f["category"]?.takeIf { key -> PostCategory.entries.any { it.key == key } } ?: category
            animalType = f["animalType"]?.takeIf { key -> AnimalType.entries.any { it.key == key } } ?: animalType
            name=f["name"].orEmpty(); description=f["description"].orEmpty(); color=f["color"].orEmpty()
            breed=f["breed"].orEmpty(); features=f["features"].orEmpty(); sex=f["sex"] ?: sex; ageGroup=f["ageGroup"] ?: ageGroup
            lat=f["lat"]?.toDoubleOrNull()?.takeIf { it.isFinite() }; lng=f["lng"]?.toDoubleOrNull()?.takeIf { it.isFinite() }
            radius=f["radius"]?.toDoubleOrNull()?.coerceIn(0.0,5000.0) ?: 0.0
            approximate=f["approximate"].toBoolean(); lastSeenAt=f["lastSeenAt"]?.toLongOrNull() ?: now()
            reward=f["reward"].orEmpty(); contactMethod=f["contactMethod"] ?: contactMethod
            phone=f["phone"].orEmpty(); email=f["email"].orEmpty(); health=f["health"].orEmpty()
            vaccinated=f["vaccinated"].toBoolean(); neutered=f["neutered"].toBoolean(); videoPath=f["videoPath"].orEmpty()
            microchip=f["microchip"]?.takeIf {it in listOf("yes","no","unknown")} ?: "unknown"
            preparedFingerprint=f["fingerprint"]?.split(',')?.mapNotNull {it.toDoubleOrNull()}?.takeIf(PostAppearance::valid).orEmpty()
            photos.clear()
            for (key in draft.photos) {
                if (key.startsWith("remote:")) photos.add(FormPhoto.Remote(key.substringAfter(':')))
                else container.drafts.photo(uid,key.substringAfter(':'))?.let { bytes ->
                    withContext(Dispatchers.Default) {
                        BitmapFactory.decodeByteArray(bytes,0,bytes.size)?.let { bitmap ->
                            FormPhoto.Local(ProcessedPhoto(bytes,ImageUtils.compress(ImageUtils.scaleToMax(bitmap,360),72),ImageUtils.scaleToMax(bitmap,512)))
                        }
                    }?.let(photos::add)
                }
            }
            step = f["step"]?.toIntOrNull()?.coerceIn(0,PostStep.entries.lastIndex) ?: 0
            savedDraft = null
        }
    }

    val categoryEnum: PostCategory get() = labeledFrom(category, PostCategory.OTHER)

    init {
        if (postId != null) viewModelScope.launch {
            container.posts.post(postId)?.let { p ->
                existing = p
                category = p.category
                animalType = p.animalType
                name = p.name
                description = p.description
                color = p.color
                breed = p.breed
                features = p.features
                sex = p.sex
                ageGroup = p.ageGroup
                photos.addAll(p.photoIds.map { FormPhoto.Remote(it) })
                videoPath = p.videoPath
                lat = p.lat
                lng = p.lng
                radius = p.areaRadiusM.toDouble()
                approximate = p.approximate
                lastSeenAt = p.lastSeenAt.takeIf { it > 0 } ?: p.createdAt
                reward = if (p.reward > 0) p.reward.toString() else ""
                contactMethod = p.contactMethod
                phone = p.contactPhone
                email = p.contactEmail
                health = p.health
                vaccinated = p.vaccinated
                neutered = p.neutered
                microchip = p.microchip
                preparedFingerprint = p.fingerprint
                step = PostStep.DETAILS.ordinal
            }
            loaded = true
        } else {
            email = container.authRepo.email()
            val uid = container.session.uid
            if (uid != null) {
                viewModelScope.launch { savedDraft = container.drafts.load(uid); draftReady = true }
                draftJob = viewModelScope.launch {
                    snapshotFlow { Triple(draftReady, savedDraft, snapshotDraft()) }.debounce(500).collect { (ready,pending,draft) ->
                        if (ready && pending == null && container.session.uid == uid && (step > 0 || name.isNotBlank() || description.isNotBlank() || photos.isNotEmpty())) {
                            try {
                                container.drafts.save(uid,draft,photos.filterIsInstance<FormPhoto.Local>().associate { it.draftId to it.photo.full })
                            } catch(e: Exception) { if(e is kotlinx.coroutines.CancellationException) throw e; AppErrors.report(e) }
                        }
                    }
                }
            }
        }
    }

    fun addPhotos(context: Context, uris: List<Uri>, onError: () -> Unit) {
        val room = PostRepository.MAX_PHOTOS - photos.size
        if (room <= 0 || uris.isEmpty()) return
        viewModelScope.launch {
            processing = true
            uris.take(room).forEach { uri ->
                val processed = runCatching { withContext(Dispatchers.Default) { ImageUtils.process(context, uri) } }.getOrNull()
                if (processed != null) photos.add(FormPhoto.Local(processed)) else onError()
            }
            processing = false
        }
    }

    fun removePhoto(photo: FormPhoto) {
        photos.remove(photo)
        if (photo is FormPhoto.Remote) removedRemote.add(photo.id)
    }

    fun makeCover(photo: FormPhoto) {
        photos.remove(photo)
        photos.add(0, photo)
    }

    fun removeVideo() {
        videoUri = null
        videoPath = ""
    }

    /** Whether the current step has what it needs to continue. */
    fun canContinue(): Boolean = when (PostStep.entries[step]) {
        PostStep.PLACE -> lat != null && lng != null && Dubai.contains(lat!!, lng!!)
        PostStep.DETAILS -> description.isNotBlank() || name.isNotBlank() || features.isNotBlank()
        PostStep.CONTACT -> when (contactMethod) {
            ContactMethod.PHONE.key -> phone.count { it.isDigit() } >= 7
            ContactMethod.EMAIL.key -> "@" in email && "." in email
            else -> true
        }
        else -> true
    }

    /** A post built from the form, for the review card and the duplicate check. */
    fun previewPost(me: UserProfile?): Post = Post(
        id = postId ?: "", ownerId = me?.uid.orEmpty(), ownerName = me?.username.orEmpty(),
        animalType = animalType, category = category, name = name.trim(), description = description.trim(),
        color = color.trim(), breed = breed.trim(), features = features.trim(), sex = sex, ageGroup = ageGroup,
        photoIds = photos.filterIsInstance<FormPhoto.Remote>().map { it.id },
        lat = lat ?: 0.0, lng = lng ?: 0.0, neighborhood = if (lat != null && lng != null) Dubai.nearest(lat!!, lng!!).key else "",
        lastSeenAt = lastSeenAt, reward = reward.toLongOrNull() ?: 0, adoptionStatus = AdoptionStatus.AVAILABLE.key,
        keywords = PostRepository.keywords(name, color, breed, features, description, if (lat != null) Dubai.nearest(lat!!, lng!!).key else ""),
        createdAt = existing?.createdAt ?: now(), microchip = microchip, fingerprint = preparedFingerprint,
    )

    fun checkDuplicates(me: UserProfile?) {
        if (postId != null) return
        viewModelScope.launch {
            val feed = runCatching { container.posts.feedOnce(300) }.getOrDefault(emptyList())
            duplicates = PostRepository.likelyDuplicates(previewPost(me), feed)
        }
    }

    /**
     * For a new street-animal post: looks for open street posts of the same kind of animal within
     * [SAME_ANIMAL_RADIUS_KM] and asks the AI server (pet-recognition-base on the owner's PC) whether the
     * new photo shows one of them. If the server can't be reached, the on-device colour fingerprint ranks
     * them instead, and only clearly similar ones are shown, marked as such.
     */
    fun checkSameAnimal() {
        if (postId != null || categoryEnum != PostCategory.STREET) { sameAnimal = emptyList(); return }
        val photo = photos.filterIsInstance<FormPhoto.Local>().firstOrNull() ?: run { sameAnimal = emptyList(); return }
        val la = lat ?: return
        val ln = lng ?: return
        val key = "${photo.draftId}|$la|$ln|$animalType"
        if (key == sameAnimalKey) return
        sameAnimalKey = key
        viewModelScope.launch {
            sameAnimalChecking = true
            try {
                val nearby = runCatching { container.posts.feedOnce(400) }.getOrDefault(emptyList()).asSequence()
                    .filter { it.category == PostCategory.STREET.key && it.status == "open" && !it.hidden && it.animalType == animalType && it.photoIds.isNotEmpty() }
                    .map { it to com.catmap.dubai.data.distanceKm(la, ln, it.lat, it.lng) }
                    .filter { (_, d) -> d.isFinite() && d <= SAME_ANIMAL_RADIUS_KM }
                    .sortedBy { it.second }.take(8).toList()
                if (nearby.isEmpty()) { sameAnimal = emptyList(); return@launch }
                val query = container.analyzer.cropSubject(photo.photo.analysis)
                val crops = nearby.mapNotNull { (p, _) ->
                    container.images.bitmap(p.photoIds.first(), thumb = false, target = 640)?.let { p.id to container.analyzer.cropSubject(it) }
                }
                val labels = crops.map { (id, _) -> nearby.first { it.first.id == id }.first.name.ifBlank { "Street ${animalType}" } }
                val server = if (crops.isEmpty()) null else container.aiServer.compare(query, crops, labels)
                val distance = nearby.associate { (p, d) -> p.id to d }
                sameAnimal = if (server != null) {
                    server.filter { it.verdict == "same" || it.verdict == "maybe" }.mapNotNull { m ->
                        nearby.firstOrNull { it.first.id == m.id }?.let { (p, d) -> SameAnimalCandidate(p, m.similarity, m.verdict, true, d) }
                    }.sortedByDescending { it.similarity }
                } else {
                    val mine = withContext(Dispatchers.Default) { PostAppearance.describe(photo.photo.analysis) }
                    nearby.map { (p, d) -> SameAnimalCandidate(p, PostAppearance.similarity(mine, p.fingerprint), "maybe", false, distance.getValue(p.id)) }
                        .filter { it.similarity >= LOCAL_MAYBE }.sortedByDescending { it.similarity }.take(3)
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                AppErrors.report(e)
                sameAnimal = emptyList()
            } finally {
                sameAnimalChecking = false
            }
        }
    }

    /** The AI said "same" for a candidate the person hasn't ruled out, so publishing should ask first. */
    val unresolvedSameAnimal: SameAnimalCandidate?
        get() = sameAnimal.firstOrNull { it.verdict == "same" && it.post.id !in notSameAnimal }

    /**
     * The person confirmed the photo shows an animal already on the map: instead of a second post, the photo
     * and note are added to that post as a sighting update. Returns the existing post's id.
     */
    suspend fun addToExisting(target: Post, me: UserProfile, privacyLevel: String): String {
        val la = lat ?: throw AppException(R.string.err_location_required)
        val ln = lng ?: throw AppException(R.string.err_location_required)
        publishing = true
        try {
            val local = photos.filterIsInstance<FormPhoto.Local>().firstOrNull()
            val photoId = local?.let { container.images.upload(me.uid, it.photo) }.orEmpty()
            val (pubLat, pubLng) = if (approximate) LocationPrivacy.fuzz(la, ln, privacyLevel, me.uid + la + ln) else la to ln
            val note = listOf(description, features).map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n")
            container.posts.addUpdate(target, com.catmap.dubai.data.model.UpdateKind.SIGHTING, note, photoId, pubLat, pubLng, me)
            draftPublished = true
            draftJob?.cancelAndJoin()
            container.drafts.clear(me.uid)
            return target.id
        } finally {
            publishing = false
        }
    }

    /** Uploads new photos (and the video), then saves the post. Returns the post id. */
    suspend fun publish(me: UserProfile, privacyLevel: String): String {
        val la = lat ?: throw AppException(R.string.err_location_required)
        val ln = lng ?: throw AppException(R.string.err_location_required)
        publishing = true
        try {
            val appearance = withContext(Dispatchers.Default) {
                PostAppearance.average(photos.filterIsInstance<FormPhoto.Local>().map { PostAppearance.describe(it.photo.analysis) } + listOf(existing?.fingerprint.orEmpty())).ifEmpty {preparedFingerprint}
            }
            preparedFingerprint = appearance
            val ids = photos.map { photo ->
                when (photo) {
                    is FormPhoto.Remote -> photo.id
                    is FormPhoto.Local -> container.images.upload(me.uid, photo.photo)
                }
            }
            // Replace local entries with their uploaded ids, so a retry after a failure doesn't upload twice.
            photos.clear()
            photos.addAll(ids.map { FormPhoto.Remote(it) })
            val uploadedVideo = videoUri?.let { container.videos.upload(me.uid, it) }
            if (uploadedVideo != null && videoPath.isNotBlank()) container.videos.delete(videoPath)
            val finalVideo = uploadedVideo ?: videoPath
            videoUri = null
            videoPath = finalVideo
            val (pubLat, pubLng) = if (approximate) LocationPrivacy.fuzz(la, ln, privacyLevel, me.uid + la + ln) else la to ln
            val draft = PostDraft(
                animalType = animalType, category = category, name = name, description = description, color = color,
                breed = breed, features = features, sex = sex, ageGroup = ageGroup, photoIds = ids, videoPath = finalVideo,
                lat = pubLat, lng = pubLng, approximate = approximate,
                areaRadiusM = if (categoryEnum == PostCategory.LOST) radius.toLong() else 0,
                lastSeenAt = lastSeenAt, reward = reward.filter { it.isDigit() }.take(6).toLongOrNull() ?: 0,
                contactMethod = contactMethod,
                contactPhone = if (contactMethod == ContactMethod.PHONE.key) phone else "",
                contactEmail = if (contactMethod == ContactMethod.EMAIL.key) email else "",
                health = health, vaccinated = vaccinated, neutered = neutered,
                fingerprint = appearance,
                microchip = microchip,
            )
            val current = existing
            val id = if (current == null) {
                container.posts.create(draft, me)
            } else {
                container.posts.update(current, draft, me)
                current.id
            }
            removedRemote.forEach { runCatching { container.images.delete(it) } }
            removedRemote.clear()
            if (postId == null) {
                draftPublished = true
                draftJob?.cancelAndJoin()
                container.drafts.clear(me.uid)
            }
            return id
        } finally {
            publishing = false
        }
    }
}
