package com.catmap.dubai.ui.sighting

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.catmap.dubai.AppContainer
import com.catmap.dubai.R
import com.catmap.dubai.ai.BreedHint
import com.catmap.dubai.ai.CatAnalysis
import com.catmap.dubai.ai.Similarity
import com.catmap.dubai.data.AppException
import com.catmap.dubai.data.Dubai
import com.catmap.dubai.data.LocationPrivacy
import com.catmap.dubai.data.SightingDraft
import com.catmap.dubai.data.distanceKm
import com.catmap.dubai.data.model.BehaviorTag
import com.catmap.dubai.data.model.Cat
import com.catmap.dubai.data.model.CatSex
import com.catmap.dubai.data.model.CatSize
import com.catmap.dubai.data.model.CoatColor
import com.catmap.dubai.data.model.CoatPattern
import com.catmap.dubai.data.model.EarTip
import com.catmap.dubai.data.model.HealthTag
import com.catmap.dubai.data.model.labeledFrom
import com.catmap.dubai.data.userMessage
import com.catmap.dubai.util.ImageUtils
import com.catmap.dubai.util.ProcessedPhoto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PickedPhoto(val key: Long, val photo: ProcessedPhoto, val analysis: CatAnalysis?)

/** A possible existing profile for the cat in the photos, and how confidently the app words it. */
data class CatMatch(
    val cat: Cat,
    val breakdown: Similarity.Breakdown,
    val distanceKm: Double?,
    val strength: Similarity.Strength = Similarity.Strength.POSSIBLE,
)

/** Combined result of the on-device analysis over all photos. */
data class CombinedAnalysis(
    val catDetected: Boolean,
    val coat: CoatColor,
    val coatConfidence: Float,
    val pattern: CoatPattern,
    val patternConfidence: Float,
    val breedHints: List<BreedHint>,
    val features: List<Double>,
    /** True when a photo had several animals or people in it, so only the largest was analysed. */
    val severalSubjects: Boolean = false,
)

class NewSightingViewModel(private val container: AppContainer, presetCatId: String?) : ViewModel() {
    var step by mutableIntStateOf(0)

    val photos = mutableStateListOf<PickedPhoto>()
    var processing by mutableStateOf(false)
        private set
    var analysis by mutableStateOf<CombinedAnalysis?>(null)
        private set

    var presetCat by mutableStateOf<Cat?>(null)
        private set
    var matches by mutableStateOf<List<CatMatch>>(emptyList())
        private set
    var matching by mutableStateOf(false)
        private set
    var matchesSearched by mutableStateOf(false)
        private set
    val rejected = mutableStateListOf<String>()
    var confirmed by mutableStateOf<Cat?>(null)
        private set

    // Details
    var name by mutableStateOf("")
    var description by mutableStateOf("")
    var marks by mutableStateOf("")
    var coat by mutableStateOf(CoatColor.UNKNOWN)
    var pattern by mutableStateOf(CoatPattern.UNKNOWN)
    var size by mutableStateOf(CatSize.UNKNOWN)
    var sex by mutableStateOf(CatSex.UNKNOWN)
    private var coatEdited = false
    val healthTags = mutableStateListOf<HealthTag>()
    val behaviorTags = mutableStateListOf<BehaviorTag>()
    var earTip by mutableStateOf(EarTip.UNSURE)
    var notNeutered by mutableStateOf(false)
    var needsMedical by mutableStateOf(false)
    var notes by mutableStateOf("")

    // Location and time
    val seenAt: Long = System.currentTimeMillis()
    var lat by mutableStateOf<Double?>(null)
        private set
    var lng by mutableStateOf<Double?>(null)
        private set
    var accuracy by mutableStateOf(0.0)
        private set
    var locationSource by mutableStateOf("gps")
        private set
    var locating by mutableStateOf(false)
        private set

    var submitting by mutableStateOf(false)
        private set
    var progress by mutableFloatStateOf(0f)
        private set
    var error by mutableStateOf<String?>(null)

    private var nextKey = 0L
    private var matchJob: Job? = null
    private var matchVersion = 0
    private val uploadedPhotos = mutableMapOf<Pair<String, Long>, String>()

    val target: Cat? get() = presetCat ?: confirmed
    val isNewCat: Boolean get() = target == null
    val insideDubai: Boolean get() = lat != null && lng != null && Dubai.contains(lat!!, lng!!)

    init {
        if (presetCatId != null) {
            viewModelScope.launch {
                presetCat = runCatching { container.cats.cat(presetCatId) }.getOrNull()
                presetCat?.let {
                    coat = labeledFrom(it.coatColor, CoatColor.UNKNOWN)
                    pattern = labeledFrom(it.pattern, CoatPattern.UNKNOWN)
                    size = labeledFrom(it.size, CatSize.UNKNOWN)
                }
            }
        }
    }

    fun addPhotos(uris: List<Uri>) {
        if (uris.isEmpty() || processing || submitting) return
        viewModelScope.launch {
            processing = true
            error = null
            for (uri in uris.take(MAX_PHOTOS - photos.size)) {
                try {
                    val processed = withContext(Dispatchers.IO) { ImageUtils.process(container.appContext, uri) }
                    val result = runCatching { container.analyzer.analyze(processed.analysis) }.getOrNull()
                    photos += PickedPhoto(nextKey++, processed, result)
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    android.util.Log.w("CatMap", "Could not process photo $uri", e)
                    error = container.appContext.getString(R.string.err_photo)
                }
            }
            recombine()
            processing = false
            findMatches()
        }
    }

    fun removePhoto(photo: PickedPhoto) {
        if (processing || submitting) return
        photos.remove(photo)
        recombine()
        findMatches()
    }

    private fun recombine() {
        val results = photos.mapNotNull { it.analysis }
        if (results.isEmpty()) {
            analysis = null
            return
        }
        val best = results.maxBy { it.colorConfidence }
        val bestPattern = results.maxBy { it.patternConfidence }
        analysis = CombinedAnalysis(
            catDetected = results.any { it.catDetected },
            coat = best.coatColor,
            coatConfidence = best.colorConfidence,
            pattern = bestPattern.pattern,
            patternConfidence = bestPattern.patternConfidence,
            breedHints = best.breedHints,
            features = Similarity.average(results.map { it.features }),
            severalSubjects = results.any { it.subjectCount > 1 },
        )
        if (!coatEdited && presetCat == null) {
            coat = best.coatColor
            pattern = bestPattern.pattern
        }
    }

    fun editCoat(value: CoatColor) { coat = value; coatEdited = true }
    fun editPattern(value: CoatPattern) { pattern = value; coatEdited = true }

    fun findMatches() {
        val version = ++matchVersion
        matchJob?.cancel()
        matching = false
        if (presetCat != null) return
        val a = analysis ?: run { matches = emptyList(); matchesSearched = false; return }
        matchJob = viewModelScope.launch {
            matching = true
            matchesSearched = false
            try {
                val cats = container.cats.allCatsOnce()
                val here = if (lat != null && lng != null) lat!! to lng!! else null
                // Public cat positions are blurred (up to the "high" privacy radius) and GPS has its own error.
                val uncertaintyKm = (accuracy + LocationPrivacy.approxRadiusMeters(LocationPrivacy.HIGH)) / 1000.0
                val ranked = withContext(Dispatchers.Default) {
                    cats.asSequence()
                        .filter { it.features.isNotEmpty() }
                        .map { cat ->
                            val d = here?.let { distanceKm(it.first, it.second, cat.lat, cat.lng) }
                            val days = if (cat.lastSeen > 0) (seenAt - cat.lastSeen).coerceAtLeast(0) / 86_400_000.0 else 0.0
                            CatMatch(cat, Similarity.score(a.features, a.coat.key, a.pattern.key, cat.features, cat.coatColor, cat.pattern, d, uncertaintyKm, days), d)
                        }
                        .filter { it.breakdown.total >= MATCH_THRESHOLD }
                        .sortedByDescending { it.breakdown.total }
                        .take(6).toList()
                }
                matches = ranked.mapIndexed { i, m -> m.copy(strength = Similarity.strength(m.breakdown.total, ranked.getOrNull(if (i == 0) 1 else 0)?.breakdown?.total)) }
                matchesSearched = true
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                matches = emptyList()
                error = e.userMessage(container.appContext)
            } finally { if (version == matchVersion) matching = false }
        }
    }

    fun confirm(cat: Cat) {
        confirmed = cat
        rejected.remove(cat.id)
    }

    fun reject(cat: Cat) {
        if (confirmed?.id == cat.id) confirmed = null
        if (cat.id !in rejected) rejected += cat.id
    }

    fun clearConfirmation() { confirmed = null }

    fun locate() {
        viewModelScope.launch {
            locating = true
            container.location.current()?.let {
                lat = it.lat
                lng = it.lng
                accuracy = it.accuracy
                locationSource = "gps"
            }
            locating = false
            findMatches()
        }
    }

    fun setManualLocation(newLat: Double, newLng: Double) {
        lat = newLat
        lng = newLng
        accuracy = 0.0
        locationSource = "manual"
        findMatches()
    }

    fun toggleHealth(tag: HealthTag) { if (tag in healthTags) healthTags.remove(tag) else healthTags += tag }
    fun toggleBehavior(tag: BehaviorTag) { if (tag in behaviorTags) behaviorTags.remove(tag) else behaviorTags += tag }

    fun suggestedNames(): List<String> = NameIdeas.forCoat(coat)

    fun submit(onDone: (String) -> Unit) {
        if (submitting || processing) return
        viewModelScope.launch {
            submitting = true
            error = null
            progress = 0f
            try {
                val me = container.session.profile.value ?: throw AppException(R.string.err_not_signed_in)
                if (!insideDubai) throw AppException(R.string.outside_dubai)
                if (isNewCat && name.isBlank()) throw AppException(R.string.err_name_required)
                val settings = container.settings.current()
                val picked = photos.toList()
                val ids = picked.mapIndexed { i, p ->
                    val key = me.uid to p.key
                    (uploadedPhotos[key] ?: container.images.upload(me.uid, p.photo).also { uploadedPhotos[key] = it })
                        .also { progress = (i + 1f) / (picked.size + 1) }
                }
                val a = analysis
                val draft = SightingDraft(
                    existingCat = target,
                    newCatName = name,
                    description = description,
                    distinctiveMarks = marks,
                    sex = sex.key,
                    coatColor = coat.key,
                    pattern = pattern.key,
                    size = size.key,
                    breedHints = a?.breedHints?.map { it.key } ?: emptyList(),
                    photoIds = ids,
                    features = a?.features ?: emptyList(),
                    lat = lat!!,
                    lng = lng!!,
                    accuracy = accuracy,
                    locationSource = locationSource,
                    seenAt = seenAt,
                    healthTags = healthTags.map { it.key },
                    behaviorTags = behaviorTags.map { it.key },
                    earTip = earTip.key,
                    possiblyNotNeutered = notNeutered,
                    needsMedical = needsMedical,
                    notes = notes,
                    matchScore = confirmed?.let { c -> matches.firstOrNull { it.cat.id == c.id }?.breakdown?.total } ?: 0.0,
                    // What the person was shown and ruled out, kept for measuring recognition later.
                    suggestedCatIds = matches.map { it.cat.id },
                    rejectedCatIds = rejected.toList(),
                    aiSummary = a?.let { "${it.coat.key}:${it.pattern.key}:${"%.2f".format(java.util.Locale.US, it.coatConfidence)}" } ?: "",
                )
                val catId = container.cats.submitSighting(draft, me, settings.privacyLevel, settings.shareExactWithVolunteers)
                progress = 1f
                onDone(catId)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                error = e.userMessage(container.appContext)
            } finally {
                submitting = false
            }
        }
    }

    companion object {
        const val MAX_PHOTOS = 5
        const val MATCH_THRESHOLD = 0.45
    }
}

/** Popular Emirati-style cat names, picked to suit the coat. */
object NameIdeas {
    fun forCoat(coat: CoatColor): List<String> = when (coat) {
        CoatColor.ORANGE, CoatColor.ORANGE_WHITE -> listOf("Zafran", "Mishmish", "Asal", "Burtuqala")
        CoatColor.BLACK -> listOf("Qahwa", "Layl", "Fahm", "Dukhoon")
        CoatColor.WHITE -> listOf("Thalj", "Lulu", "Qamar", "Haleeb")
        CoatColor.GRAY, CoatColor.GRAY_WHITE -> listOf("Ramadi", "Ghaim", "Fidda", "Dukhan")
        CoatColor.BLACK_WHITE -> listOf("Domino", "Shatranj", "Oreo", "Bisbis")
        CoatColor.CALICO, CoatColor.TORTOISESHELL -> listOf("Fusaifisa", "Kunafa", "Zainab", "Sadu")
        CoatColor.CREAM, CoatColor.COLORPOINT -> listOf("Luqaimat", "Karak", "Sukkar", "Hareera")
        CoatColor.BROWN -> listOf("Tamr", "Dallah", "Bunni", "Khubz")
        CoatColor.UNKNOWN -> listOf("Bissa", "Najma", "Sahm", "Habibi")
    }
}
