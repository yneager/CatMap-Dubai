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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A photo in the form: already uploaded (editing) or picked on this phone. */
sealed interface FormPhoto {
    data class Remote(val id: String) : FormPhoto
    class Local(val photo: ProcessedPhoto) : FormPhoto {
        val preview: Bitmap get() = photo.analysis
    }
}

enum class PostStep { TYPE, PHOTOS, DETAILS, PLACE, CONTACT, REVIEW }

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
    var publishing by mutableStateOf(false)
        private set
    var duplicates by mutableStateOf<List<Post>>(emptyList())
        private set

    private val removedRemote = mutableListOf<String>()

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
                step = PostStep.DETAILS.ordinal
            }
            loaded = true
        } else {
            email = container.authRepo.email()
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
        createdAt = existing?.createdAt ?: now(),
    )

    fun checkDuplicates(me: UserProfile?) {
        if (postId != null) return
        viewModelScope.launch {
            val feed = runCatching { container.posts.feedOnce(300) }.getOrDefault(emptyList())
            duplicates = PostRepository.likelyDuplicates(previewPost(me), feed)
        }
    }

    /** Uploads new photos (and the video), then saves the post. Returns the post id. */
    suspend fun publish(me: UserProfile, privacyLevel: String): String {
        val la = lat ?: throw AppException(R.string.err_location_required)
        val ln = lng ?: throw AppException(R.string.err_location_required)
        publishing = true
        try {
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
            return id
        } finally {
            publishing = false
        }
    }
}
