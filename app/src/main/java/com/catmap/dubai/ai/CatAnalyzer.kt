package com.catmap.dubai.ai

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import androidx.annotation.StringRes
import com.catmap.dubai.R
import com.catmap.dubai.data.model.CoatColor
import com.catmap.dubai.data.model.CoatPattern
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.withLock
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** One of the coat colour families the analyser measures. */
enum class ColorFamily { BLACK, WHITE, GRAY, ORANGE, CREAM, BROWN }

data class BreedHint(val key: String, val confidence: Float) {
    @get:StringRes val text: Int get() = Breeds.label(key)
}

/** Breed-characteristic hints are stored by key so they can be shown in any language. */
object Breeds {
    private val labels = mapOf(
        "arabian_mau" to R.string.breed_arabian_mau,
        "domestic_shorthair" to R.string.breed_domestic_shorthair,
        "colorpoint" to R.string.breed_colorpoint,
        "tabby" to R.string.breed_tabby_trait,
        "tuxedo" to R.string.breed_tuxedo_trait,
        "orange_male" to R.string.breed_orange_male,
        "tortie_female" to R.string.breed_tortie_female,
        "white_deafness" to R.string.breed_white_deafness,
        "gray" to R.string.breed_gray_trait,
    )

    fun label(key: String): Int = labels[key] ?: R.string.breed_domestic_shorthair
}

data class CatAnalysis(
    val catDetected: Boolean,
    val catConfidence: Float,
    val coatColor: CoatColor,
    val colorConfidence: Float,
    val pattern: CoatPattern,
    val patternConfidence: Float,
    /** Share of the cat's body covered by each colour family. */
    val colorShares: Map<ColorFamily, Float>,
    val breedHints: List<BreedHint>,
    val features: List<Double>,
    val usedSegmentation: Boolean,
    /** Separate subjects the segmenter found; with more than one, only the largest is described. */
    val subjectCount: Int = 1,
)

/**
 * On-device cat analysis.
 *
 * 1. ML Kit image labelling checks there is a cat in the picture.
 * 2. ML Kit subject segmentation separates the cat from the background (falls back to a
 *    centre-weighted mask when the model isn't available yet). When the photo has several
 *    subjects (two cats, a cat and a person), only the largest is described, so two animals
 *    are never blended into one descriptor.
 * 3. The cat's pixels are clustered and each cluster is classified into a coat colour family,
 *    which gives the coat colour; texture (edge density and orientation) gives the pattern.
 * 4. A compact appearance descriptor (colour histogram, colour layout, texture and colour
 *    shares) is produced for re-identifying the same cat in later photos – see [Similarity].
 */
class CatAnalyzer {
    private val labeler by lazy { ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS) }
    private val segmenter by lazy {
        SubjectSegmentation.getClient(
            SubjectSegmenterOptions.Builder()
                .enableForegroundConfidenceMask()
                .enableMultipleSubjects(SubjectSegmenterOptions.SubjectResultOptions.Builder().enableConfidenceMask().build())
                .build(),
        )
    }

    suspend fun analyze(bitmap: Bitmap): CatAnalysis = withContext(Dispatchers.Default) {
        val image = InputImage.fromBitmap(bitmap, 0)

        val labels = runCatching { labeler.process(image).await() }.getOrDefault(emptyList())
        val catLabel = labels.filter { it.text.equals("Cat", true) || it.text.equals("Kitten", true) }.maxOfOrNull { it.confidence } ?: 0f
        val animalHint = labels.any { it.text in setOf("Pet", "Whiskers", "Mammal", "Fur") }

        val segmentation = segment(image)

        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h).also { bitmap.getPixels(it, 0, w, 0, 0, w, h) }
        val weights = FloatArray(w * h)
        var usedSegmentation = false
        val subjects = segmentation?.subjects.orEmpty()
        if (subjects.size > 1) {
            // Several subjects: keep only the largest one (most foreground confidence).
            val largest = subjects.maxBy { subject ->
                subject.confidenceMask?.let { m -> m.rewind(); var sum = 0f; while (m.hasRemaining()) sum += m.get(); sum } ?: 0f
            }
            largest.confidenceMask?.let { m ->
                m.rewind()
                var total = 0f
                for (y in 0 until largest.height) for (x in 0 until largest.width) {
                    val v = m.get().coerceIn(0f, 1f).let { if (it < 0.35f) 0f else it }
                    val px = largest.startX + x
                    val py = largest.startY + y
                    if (px in 0 until w && py in 0 until h) { weights[py * w + px] = v; total += v }
                }
                usedSegmentation = total > w * h * 0.03f
            }
        }
        val mask = segmentation?.foregroundConfidenceMask
        if (!usedSegmentation && mask != null && mask.capacity() >= w * h) {
            mask.rewind()
            var total = 0f
            for (i in 0 until w * h) {
                weights[i] = mask.get().coerceIn(0f, 1f).let { if (it < 0.35f) 0f else it }
                total += weights[i]
            }
            usedSegmentation = total > w * h * 0.03f
        }
        if (!usedSegmentation) centreWeights(w, h, weights)

        val analysis = describe(pixels, weights, w, h)
        val detected = catLabel >= 0.5f || (catLabel > 0.2f && animalHint)
        analysis.copy(catDetected = detected, catConfidence = catLabel, usedSegmentation = usedSegmentation, subjectCount = subjects.size.coerceAtLeast(1))
    }

    /** ML Kit's on-device labels for a photo (text and confidence), for the photo check when the PC is offline. */
    suspend fun labels(bitmap: Bitmap): List<Pair<String, Float>> = withContext(Dispatchers.Default) {
        runCatching { labeler.process(InputImage.fromBitmap(bitmap, 0)).await() }.getOrDefault(emptyList()).map { it.text to it.confidence }
    }

    /**
     * One segmentation at a time. ML Kit's segmenter crashes natively when a second request starts while one
     * is still running (which happened when a slow first run outlasted a short timeout), so calls queue here.
     */
    private suspend fun segment(image: InputImage) = if (onEmulator) null else segmentLock.withLock {
        runCatching { withTimeoutOrNull(20_000) { segmenter.process(image).await() } }
            .onFailure { Log.i("CatAnalyzer", "Segmentation unavailable, using centre mask", it) }.getOrNull()
    }
    private val segmentLock = kotlinx.coroutines.sync.Mutex()

    /**
     * The segmentation module crashes natively (SIGSEGV in its GL runner) on the x86 Android emulator once it is
     * downloaded, so emulators use the centre-weighted fallback. Phones are unaffected.
     */
    private val onEmulator = android.os.Build.HARDWARE in setOf("ranchu", "goldfish") || android.os.Build.PRODUCT.startsWith("sdk_gphone")

    /**
     * The largest animal in the photo, cropped with a small margin, for the AI server's recognition model
     * (it expects a crop of the animal, not the whole scene). Falls back to a centred square when the
     * segmenter finds nothing.
     */
    suspend fun cropSubject(bitmap: Bitmap): Bitmap = withContext(Dispatchers.Default) {
        val result = segment(InputImage.fromBitmap(bitmap, 0))
        val subject = result?.subjects.orEmpty().maxByOrNull { it.width * it.height }
        val (x, y, w, h) = if (subject != null && subject.width * subject.height > bitmap.width * bitmap.height / 50) {
            val mx = subject.width / 10
            val my = subject.height / 10
            val left = (subject.startX - mx).coerceAtLeast(0)
            val top = (subject.startY - my).coerceAtLeast(0)
            listOf(left, top, (subject.width + 2 * mx).coerceAtMost(bitmap.width - left), (subject.height + 2 * my).coerceAtMost(bitmap.height - top))
        } else {
            val side = (min(bitmap.width, bitmap.height) * 0.85f).toInt()
            listOf((bitmap.width - side) / 2, (bitmap.height - side) / 2, side, side)
        }
        val crop = Bitmap.createBitmap(bitmap, x, y, w.coerceAtLeast(1), h.coerceAtLeast(1))
        com.catmap.dubai.util.ImageUtils.scaleToMax(crop, 320)
    }

    private fun centreWeights(w: Int, h: Int, out: FloatArray) {
        val cx = w / 2f
        val cy = h / 2f
        val sx = w * 0.30f
        val sy = h * 0.32f
        for (y in 0 until h) for (x in 0 until w) {
            val dx = (x - cx) / sx
            val dy = (y - cy) / sy
            out[y * w + x] = exp(-(dx * dx + dy * dy) / 2f)
        }
    }

    private fun describe(pixels: IntArray, weights: FloatArray, w: Int, h: Int): CatAnalysis {
        val hsv = FloatArray(3)

        // ---- Colour families, weighted by how "cat" each pixel is ----
        val familyWeight = FloatArray(ColorFamily.entries.size)
        var catWeight = 0f
        val hist = DoubleArray(HIST_BINS)
        for (i in pixels.indices) {
            val wgt = weights[i]
            if (wgt <= 0.05f) continue
            Color.colorToHSV(pixels[i], hsv)
            val family = classify(hsv)
            hist[histBin(hsv)] += wgt.toDouble()
            if (family != null) {
                familyWeight[family.ordinal] += wgt
                catWeight += wgt
            }
        }
        val shares = ColorFamily.entries.associateWith { if (catWeight > 0) familyWeight[it.ordinal] / catWeight else 0f }
        val histSum = hist.sum().takeIf { it > 0 } ?: 1.0
        for (i in hist.indices) hist[i] /= histSum

        // ---- Texture: gradient strength and orientation on the cat's pixels ----
        val gray = FloatArray(w * h) { i ->
            val c = pixels[i]
            (Color.red(c) * 0.299f + Color.green(c) * 0.587f + Color.blue(c) * 0.114f) / 255f
        }
        val orientation = DoubleArray(8)
        var edgeWeight = 0.0
        var areaWeight = 0.0
        for (y in 1 until h - 1) for (x in 1 until w - 1) {
            val i = y * w + x
            val wgt = weights[i]
            if (wgt <= 0.3f) continue
            val gx = gray[i + 1] - gray[i - 1]
            val gy = gray[i + w] - gray[i - w]
            val mag = sqrt(gx * gx + gy * gy)
            areaWeight += wgt
            if (mag > 0.08f) {
                edgeWeight += wgt
                val angle = (atan2(gy, gx) + Math.PI) / (2 * Math.PI) // 0..1
                orientation[((angle * 8).toInt()).coerceIn(0, 7)] += mag.toDouble() * wgt
            }
        }
        val edgeDensity = if (areaWeight > 0) (edgeWeight / areaWeight).toFloat() else 0f
        val oriSum = orientation.sum().takeIf { it > 0 } ?: 1.0
        for (i in orientation.indices) orientation[i] /= oriSum

        // ---- Colour layout: average colour in a 4×4 grid over the cat's bounding box ----
        val layout = colourLayout(pixels, weights, w, h)

        val (coat, colorConf) = coatColorFrom(shares)
        val stripes = stripeRate(gray, weights, w, h)
        val (pattern, patternConf) = patternFrom(coat, shares, edgeDensity, stripes)

        val features = ArrayList<Double>(FEATURE_SIZE)
        hist.forEach { features += it }
        layout.forEach { features += it }
        orientation.forEach { features += it }
        features += edgeDensity.toDouble()
        ColorFamily.entries.forEach { features += shares.getValue(it).toDouble() }

        return CatAnalysis(
            catDetected = false,
            catConfidence = 0f,
            coatColor = coat,
            colorConfidence = colorConf,
            pattern = pattern,
            patternConfidence = patternConf,
            colorShares = shares,
            breedHints = breedHints(coat, pattern),
            features = features.map { Math.round(it * 10_000) / 10_000.0 },
            usedSegmentation = false,
        )
    }

    private fun colourLayout(pixels: IntArray, weights: FloatArray, w: Int, h: Int): DoubleArray {
        var minX = w; var minY = h; var maxX = 0; var maxY = 0
        for (y in 0 until h) for (x in 0 until w) {
            if (weights[y * w + x] > 0.5f) {
                minX = min(minX, x); maxX = max(maxX, x); minY = min(minY, y); maxY = max(maxY, y)
            }
        }
        if (maxX <= minX || maxY <= minY) { minX = 0; minY = 0; maxX = w - 1; maxY = h - 1 }
        val out = DoubleArray(GRID * GRID * 3)
        val totals = DoubleArray(GRID * GRID)
        val bw = (maxX - minX + 1).toDouble()
        val bh = (maxY - minY + 1).toDouble()
        for (y in minY..maxY) for (x in minX..maxX) {
            val i = y * w + x
            val wgt = weights[i].toDouble()
            if (wgt <= 0.2) continue
            val cell = (((y - minY) / bh * GRID).toInt().coerceIn(0, GRID - 1)) * GRID + ((x - minX) / bw * GRID).toInt().coerceIn(0, GRID - 1)
            val c = pixels[i]
            out[cell * 3] += Color.red(c) / 255.0 * wgt
            out[cell * 3 + 1] += Color.green(c) / 255.0 * wgt
            out[cell * 3 + 2] += Color.blue(c) / 255.0 * wgt
            totals[cell] += wgt
        }
        for (cell in 0 until GRID * GRID) {
            val t = totals[cell]
            for (k in 0..2) out[cell * 3 + k] = if (t > 0) out[cell * 3 + k] / t else 0.5
        }
        return out
    }

    private fun coatColorFrom(s: Map<ColorFamily, Float>): Pair<CoatColor, Float> {
        val black = s.getValue(ColorFamily.BLACK)
        val white = s.getValue(ColorFamily.WHITE)
        val gray = s.getValue(ColorFamily.GRAY)
        val orange = s.getValue(ColorFamily.ORANGE)
        val cream = s.getValue(ColorFamily.CREAM)
        val brown = s.getValue(ColorFamily.BROWN)
        val warm = orange + brown * 0.5f

        return when {
            warm >= 0.12f && black >= 0.12f && white >= 0.15f -> CoatColor.CALICO to confidence(min(min(warm, black), white) * 3)
            warm >= 0.15f && black >= 0.18f && white < 0.15f -> CoatColor.TORTOISESHELL to confidence(min(warm, black) * 2.5f)
            (cream + white) >= 0.55f && (brown + black) in 0.08f..0.35f && orange < 0.12f ->
                CoatColor.COLORPOINT to confidence(0.45f)
            white >= 0.2f && black >= 0.2f -> CoatColor.BLACK_WHITE to confidence(min(white, black) * 2.2f)
            white >= 0.2f && orange + cream * 0.5f >= 0.2f -> CoatColor.ORANGE_WHITE to confidence(min(white, orange + cream * 0.5f) * 2.2f)
            white >= 0.2f && gray >= 0.2f -> CoatColor.GRAY_WHITE to confidence(min(white, gray) * 2.2f)
            else -> {
                val (family, share) = s.maxBy { it.value }.toPair()
                val coat = when (family) {
                    ColorFamily.BLACK -> CoatColor.BLACK
                    ColorFamily.WHITE -> CoatColor.WHITE
                    ColorFamily.GRAY -> CoatColor.GRAY
                    ColorFamily.ORANGE -> CoatColor.ORANGE
                    ColorFamily.CREAM -> CoatColor.CREAM
                    ColorFamily.BROWN -> CoatColor.BROWN
                }
                if (share <= 0f) CoatColor.UNKNOWN to 0f else coat to confidence(share)
            }
        }
    }

    /**
     * Light/dark alternations per 100 px of cat, scanned along rows and columns with hysteresis.
     * Tabby stripes repeat across the body; smooth shading on a solid coat barely registers.
     */
    private fun stripeRate(gray: FloatArray, weights: FloatArray, w: Int, h: Int): Float {
        var transitions = 0
        var length = 0
        fun scan(count: Int, index: (Int) -> Int) {
            var extreme = -1f
            var rising = 0
            for (k in 0 until count) {
                val i = index(k)
                if (weights[i] < 0.5f) { extreme = -1f; rising = 0; continue }
                length++
                val v = gray[i]
                if (extreme < 0f) { extreme = v; continue }
                when {
                    rising == 0 -> if (abs(v - extreme) > STRIPE_STEP) { rising = if (v > extreme) 1 else -1; extreme = v }
                    rising > 0 -> if (v > extreme) extreme = v else if (extreme - v > STRIPE_STEP) { transitions++; rising = -1; extreme = v }
                    else -> if (v < extreme) extreme = v else if (v - extreme > STRIPE_STEP) { transitions++; rising = 1; extreme = v }
                }
            }
        }
        for (y in 0 until h step 3) scan(w) { y * w + it }
        for (x in 0 until w step 3) scan(h) { it * w + x }
        return if (length > 0) transitions * 100f / length else 0f
    }

    private fun isStriped(edgeDensity: Float, stripes: Float) = stripes > TABBY_STRIPES || edgeDensity > TABBY_EDGES

    private fun patternFrom(coat: CoatColor, s: Map<ColorFamily, Float>, edgeDensity: Float, stripes: Float): Pair<CoatPattern, Float> = when (coat) {
        CoatColor.CALICO -> CoatPattern.CALICO to 0.8f
        CoatColor.TORTOISESHELL -> CoatPattern.TORTOISESHELL to 0.75f
        CoatColor.COLORPOINT -> CoatPattern.COLORPOINT to 0.5f
        CoatColor.BLACK_WHITE ->
            if (s.getValue(ColorFamily.BLACK) > s.getValue(ColorFamily.WHITE) * 1.4f) CoatPattern.TUXEDO to 0.65f
            else CoatPattern.BICOLOR to 0.7f
        CoatColor.ORANGE_WHITE, CoatColor.GRAY_WHITE ->
            if (isStriped(edgeDensity, stripes)) CoatPattern.TABBY to 0.55f else CoatPattern.BICOLOR to 0.7f
        CoatColor.BLACK, CoatColor.WHITE -> CoatPattern.SOLID to 0.75f
        CoatColor.UNKNOWN -> CoatPattern.UNKNOWN to 0f
        else ->
            if (isStriped(edgeDensity, stripes)) CoatPattern.TABBY to confidence(0.5f + (stripes - TABBY_STRIPES) / 10f)
            else CoatPattern.SOLID to confidence(0.4f + (TABBY_STRIPES - stripes) / 10f)
    }

    private fun breedHints(coat: CoatColor, pattern: CoatPattern): List<BreedHint> = buildList {
        // Most street cats in the UAE are Arabian Mau or Arabian Mau mixes.
        add(BreedHint("arabian_mau", 0.65f))
        add(BreedHint("domestic_shorthair", 0.55f))
        when (pattern) {
            CoatPattern.COLORPOINT -> add(BreedHint("colorpoint", 0.5f))
            CoatPattern.TABBY -> add(BreedHint("tabby", 0.6f))
            CoatPattern.TUXEDO -> add(BreedHint("tuxedo", 0.6f))
            else -> Unit
        }
        when (coat) {
            CoatColor.ORANGE, CoatColor.ORANGE_WHITE -> add(BreedHint("orange_male", 0.8f))
            CoatColor.CALICO, CoatColor.TORTOISESHELL -> add(BreedHint("tortie_female", 0.95f))
            CoatColor.WHITE -> add(BreedHint("white_deafness", 0.4f))
            CoatColor.GRAY -> add(BreedHint("gray", 0.35f))
            else -> Unit
        }
    }.sortedByDescending { it.confidence }

    companion object {
        private const val GRID = 4
        private const val HUE_BINS = 12
        /** 12 hues × 2 saturations × 2 values for coloured pixels, plus 6 grey levels. */
        const val HIST_BINS = HUE_BINS * 4 + 6
        const val LAYOUT_SIZE = GRID * GRID * 3
        const val FEATURE_SIZE = HIST_BINS + LAYOUT_SIZE + 8 + 1 + 6
        private const val TABBY_EDGES = 0.30f
        private const val TABBY_STRIPES = 2.5f
        private const val STRIPE_STEP = 0.07f

        private fun confidence(v: Float) = v.coerceIn(0.2f, 0.97f)

        fun histBin(hsv: FloatArray): Int {
            val (hue, sat, value) = Triple(hsv[0], hsv[1], hsv[2])
            if (sat < 0.18f || value < 0.15f) {
                return HUE_BINS * 4 + (value * 6).toInt().coerceIn(0, 5)
            }
            val hBin = ((hue / 360f) * HUE_BINS).toInt().coerceIn(0, HUE_BINS - 1)
            val sBin = if (sat < 0.5f) 0 else 1
            val vBin = if (value < 0.55f) 0 else 1
            return hBin * 4 + sBin * 2 + vBin
        }

        /** Maps one pixel to a coat colour family, or null for colours cats don't have (sky, grass…). */
        fun classify(hsv: FloatArray): ColorFamily? {
            val hue = hsv[0]
            val sat = hsv[1]
            val value = hsv[2]
            return when {
                value < 0.22f -> ColorFamily.BLACK
                sat < 0.14f && value > 0.78f -> ColorFamily.WHITE
                sat < 0.18f -> if (value < 0.3f) ColorFamily.BLACK else ColorFamily.GRAY
                hue < 50f || hue > 345f -> when {
                    sat > 0.42f && value > 0.45f -> ColorFamily.ORANGE
                    value > 0.68f -> ColorFamily.CREAM
                    else -> ColorFamily.BROWN
                }
                hue < 65f -> if (value > 0.6f) ColorFamily.CREAM else ColorFamily.BROWN
                hue in 180f..260f && sat < 0.3f -> if (value > 0.75f) ColorFamily.WHITE else ColorFamily.GRAY
                else -> null
            }
        }
    }
}

/** Scores how likely two appearance descriptors show the same cat. */
object Similarity {
    data class Breakdown(val total: Double, val colour: Double, val texture: Double, val layout: Double, val attributes: Double, val proximity: Double?)

    fun visual(a: List<Double>, b: List<Double>): Triple<Double, Double, Double> {
        if (!valid(a) || !valid(b)) return Triple(0.0, 0.0, 0.0)
        var p = 0
        // Histogram intersection of the colour histograms.
        var hist = 0.0
        repeat(CatAnalyzer.HIST_BINS) { hist += min(a[p + it], b[p + it]) }
        p += CatAnalyzer.HIST_BINS
        // Colour layout distance.
        var layoutDiff = 0.0
        repeat(CatAnalyzer.LAYOUT_SIZE) { layoutDiff += abs(a[p + it] - b[p + it]) }
        val layout = (1 - layoutDiff / CatAnalyzer.LAYOUT_SIZE / 0.35).coerceIn(0.0, 1.0)
        p += CatAnalyzer.LAYOUT_SIZE
        // Texture: orientation histogram cosine and edge density.
        var dot = 0.0; var na = 0.0; var nb = 0.0
        repeat(8) { dot += a[p + it] * b[p + it]; na += a[p + it] * a[p + it]; nb += b[p + it] * b[p + it] }
        val cosine = if (na > 0 && nb > 0) dot / sqrt(na * nb) else 0.0
        p += 8
        val edge = 1 - min(1.0, abs(a[p] - b[p]) * 3)
        p += 1
        val texture = 0.6 * cosine + 0.4 * edge
        // Colour family shares.
        var shareDiff = 0.0
        repeat(6) { shareDiff += abs(a[p + it] - b[p + it]) }
        val shares = 1 - shareDiff / 2
        val colour = 0.55 * hist.coerceIn(0.0, 1.0) + 0.45 * shares.coerceIn(0.0, 1.0)
        return Triple(colour.coerceIn(0.0, 1.0), texture.coerceIn(0.0, 1.0), layout)
    }

    fun score(
        features: List<Double>,
        coat: String,
        pattern: String,
        candidateFeatures: List<Double>,
        candidateCoat: String,
        candidatePattern: String,
        distanceKm: Double?,
        uncertaintyKm: Double = 0.0,
        daysApart: Double = 0.0,
    ): Breakdown {
        val (colour, texture, layout) = visual(features, candidateFeatures)
        val attributes = (if (coat == candidateCoat) 0.65 else 0.0) + (if (pattern == candidatePattern) 0.35 else 0.0)
        val visual = 0.6 * colour + 0.25 * texture + 0.15 * layout
        val proximity = distanceKm?.let { proximity(it, uncertaintyKm, daysApart) }
        val total = if (proximity != null) 0.65 * visual + 0.2 * attributes + 0.15 * proximity else 0.77 * visual + 0.23 * attributes
        return Breakdown(total.coerceIn(0.0, 1.0), colour, texture, layout, attributes, proximity)
    }

    /**
     * Location as supporting evidence, 0..1. The distance is first reduced by how uncertain the two
     * positions are (GPS accuracy, and the blurring applied to public cat locations), so a cat seen
     * across the street isn't marked down. The decay widens slowly with the time since the cat was last
     * seen (about 1.5 km at first, at most 4 km), because a cat can move between neighbourhoods. Location
     * is only 15% of the total, so it ranks candidates but never rules one out on its own.
     */
    fun proximity(distanceKm: Double, uncertaintyKm: Double = 0.0, daysApart: Double = 0.0): Double? {
        if (!distanceKm.isFinite()) return null
        val effective = (distanceKm - uncertaintyKm.coerceAtLeast(0.0)).coerceAtLeast(0.0)
        val scale = 1.5 + (daysApart.coerceAtLeast(0.0) * 0.05).coerceAtMost(2.5)
        return exp(-effective / scale)
    }

    /** How the app words a score, instead of showing a bare percentage. */
    enum class Strength { STRONG, POSSIBLE, WEAK }

    /**
     * A match is "strong" only when it scores high and clearly beats the runner-up. Two similar orange
     * tabbies both scoring 0.8 are "possible", because the descriptor can't tell them apart. These
     * thresholds are provisional; Admin > Recognition test measures where they should sit.
     */
    fun strength(total: Double, runnerUp: Double?): Strength = when {
        total >= STRONG_SCORE && (runnerUp == null || total - runnerUp >= STRONG_LEAD) -> Strength.STRONG
        total >= POSSIBLE_SCORE -> Strength.POSSIBLE
        else -> Strength.WEAK
    }

    const val STRONG_SCORE = 0.80
    const val STRONG_LEAD = 0.05
    const val POSSIBLE_SCORE = 0.62

    /** Averages descriptors from several photos of the same sighting. */
    fun average(list: List<List<Double>>): List<Double> {
        val valid = list.filter { valid(it) }
        if (valid.isEmpty()) return emptyList()
        return List(CatAnalyzer.FEATURE_SIZE) { i -> Math.round(valid.sumOf { it[i] } / valid.size * 10_000) / 10_000.0 }
    }

    private fun valid(features: List<Double>) =
        features.size == CatAnalyzer.FEATURE_SIZE && features.all { it.isFinite() && it in 0.0..1.0 }
}
