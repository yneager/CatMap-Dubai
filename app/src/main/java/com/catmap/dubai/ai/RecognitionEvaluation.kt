package com.catmap.dubai.ai

import java.util.Locale

/**
 * Measures how well the cat matcher re-identifies individual cats, on a labelled set of photos
 * (one folder per cat). It uses exactly the app's descriptor and [Similarity.score], so the numbers
 * describe this app, not a published model benchmark. Location isn't part of the test (dataset photos
 * have no trustworthy positions), so it measures the visual evidence plus coat attributes only.
 *
 * Two protocols, both leave-one-out over every photo of a cat that has at least two photos:
 * - **Known cat** (closed set): the probe's cat is in the gallery, built from its *other* photos.
 *   Reports top-1 / top-3 accuracy for the app's method (one averaged descriptor per cat) and for the
 *   alternative of comparing against each stored photo and keeping the best.
 * - **Unseen cat** (open set): the probe's cat is removed from the gallery, so every suggestion is
 *   wrong. Reports how often the app would still show a suggestion, a "possible" one, or a "strong" one.
 */
object RecognitionEvaluation {
    data class Sample(val identity: String, val file: String, val features: List<Double>, val coat: String, val pattern: String, val millis: Long)

    data class ProbeResult(val identity: String, val file: String, val rankAverage: Int, val rankBestPhoto: Int, val genuine: Double, val bestImpostor: Double)

    data class Result(
        val identities: Int,
        val photos: Int,
        val probes: Int,
        val skippedPhotos: Int,
        val top1: Double,
        val top3: Double,
        val top1BestPhoto: Double,
        val top3BestPhoto: Double,
        val unseenAnySuggestion: Double,
        val unseenPossible: Double,
        val unseenStrong: Double,
        val genuineMean: Double,
        val impostorMean: Double,
        /** Lowest score that at most 5% of different-cat comparisons reach, and the same-cat pairs kept there. */
        val threshold5: Double?,
        val genuineAtThreshold5: Double?,
        val averageMillis: Double,
        val perProbe: List<ProbeResult>,
    )

    fun evaluate(all: List<Sample>, matchThreshold: Double = 0.45): Result? {
        val samples = all.filter { it.features.size == CatAnalyzer.FEATURE_SIZE }
        val byCat = samples.groupBy { it.identity }
        if (byCat.size < 2) return null
        val probes = samples.filter { byCat.getValue(it.identity).size >= 2 }
        if (probes.isEmpty()) return null

        fun score(probe: Sample, gallery: List<Sample>, bestPhoto: Boolean): Double {
            if (gallery.isEmpty()) return 0.0
            if (!bestPhoto) {
                val avg = Similarity.average(gallery.map { it.features })
                val ref = gallery.first()
                // The profile's coat/pattern is what its first sighting recorded, as in the app.
                return Similarity.score(probe.features, probe.coat, probe.pattern, avg, ref.coat, ref.pattern, null).total
            }
            return gallery.maxOf { Similarity.score(probe.features, probe.coat, probe.pattern, it.features, it.coat, it.pattern, null).total }
        }

        val genuine = ArrayList<Double>()
        val impostor = ArrayList<Double>()
        val perProbe = ArrayList<ProbeResult>()
        var top1 = 0; var top3 = 0; var top1b = 0; var top3b = 0
        var anySuggestion = 0; var possible = 0; var strong = 0
        for (probe in probes) {
            val galleries = byCat.mapValues { (id, list) -> if (id == probe.identity) list.filter { it !== probe } else list }
            val avgScores = galleries.mapValues { (_, g) -> score(probe, g, bestPhoto = false) }
            val bestScores = galleries.mapValues { (_, g) -> score(probe, g, bestPhoto = true) }
            val rankAvg = 1 + avgScores.count { (id, s) -> id != probe.identity && s > avgScores.getValue(probe.identity) }
            val rankBest = 1 + bestScores.count { (id, s) -> id != probe.identity && s > bestScores.getValue(probe.identity) }
            if (rankAvg == 1) top1++
            if (rankAvg <= 3) top3++
            if (rankBest == 1) top1b++
            if (rankBest <= 3) top3b++
            val own = avgScores.getValue(probe.identity)
            genuine += own
            val others = avgScores.filterKeys { it != probe.identity }.values.sortedDescending()
            impostor += others
            // Unseen cat: only the other cats remain, so anything the app shows is a false suggestion.
            val best = others.firstOrNull() ?: 0.0
            if (best >= matchThreshold) anySuggestion++
            when (Similarity.strength(best, others.getOrNull(1))) {
                Similarity.Strength.STRONG -> { strong++; possible++ }
                Similarity.Strength.POSSIBLE -> possible++
                Similarity.Strength.WEAK -> Unit
            }
            perProbe += ProbeResult(probe.identity, probe.file, rankAvg, rankBest, own, best)
        }
        val n = probes.size.toDouble()
        val sortedImpostor = impostor.sorted()
        val threshold = sortedImpostor.takeIf { it.isNotEmpty() }?.let { it[((it.size - 1) * 0.95).toInt()] }
        return Result(
            identities = byCat.size,
            photos = samples.size,
            probes = probes.size,
            skippedPhotos = all.size - samples.size,
            top1 = top1 / n, top3 = top3 / n, top1BestPhoto = top1b / n, top3BestPhoto = top3b / n,
            unseenAnySuggestion = anySuggestion / n, unseenPossible = possible / n, unseenStrong = strong / n,
            genuineMean = genuine.average(), impostorMean = impostor.average(),
            threshold5 = threshold,
            genuineAtThreshold5 = threshold?.let { t -> genuine.count { it > t } / genuine.size.toDouble() },
            averageMillis = samples.map { it.millis }.average(),
            perProbe = perProbe,
        )
    }

    data class EmbeddingResult(
        val probes: Int,
        val top1Average: Double,
        val top1BestPhoto: Double,
        val top3BestPhoto: Double,
        val unseenSame: Double,
        val unseenMaybe: Double,
        val knownSame: Double,
        val genuineMean: Double,
        val impostorMean: Double,
        val threshold5: Double?,
        val genuineAtThreshold5: Double?,
    )

    /**
     * The same leave-one-out protocol for the AI server's embeddings (cosine similarity), so both methods
     * are measured on the same photos. [same]/[maybe] are the server's verdict thresholds: "unseen" rates say
     * how often a cat that isn't on the map would still be called the same as one that is.
     */
    fun evaluateEmbeddings(samples: List<Pair<String, List<Double>>>, same: Double, maybe: Double): EmbeddingResult? {
        val valid = samples.filter { it.second.isNotEmpty() }
        val byCat = valid.groupBy { it.first }
        if (byCat.size < 2) return null
        val probes = valid.filter { byCat.getValue(it.first).size >= 2 }
        if (probes.isEmpty()) return null
        fun cos(a: List<Double>, b: List<Double>) = a.indices.sumOf { a[it] * b[it] } /
            (kotlin.math.sqrt(a.sumOf { it * it }) * kotlin.math.sqrt(b.sumOf { it * it })).coerceAtLeast(1e-9)
        fun mean(list: List<List<Double>>) = List(list.first().size) { i -> list.sumOf { it[i] } / list.size }
        var top1a = 0; var top1b = 0; var top3b = 0; var unseenSame = 0; var unseenMaybe = 0; var knownSame = 0
        val genuine = ArrayList<Double>(); val impostor = ArrayList<Double>()
        for (probe in probes) {
            val galleries = byCat.mapValues { (id, list) -> (if (id == probe.first) list.filter { it !== probe } else list).map { it.second } }
            val avg = galleries.mapValues { (_, g) -> cos(probe.second, mean(g)) }
            val best = galleries.mapValues { (_, g) -> g.maxOf { cos(probe.second, it) } }
            val own = probe.first
            if (avg.count { (id, s) -> id != own && s > avg.getValue(own) } == 0) top1a++
            val rankBest = 1 + best.count { (id, s) -> id != own && s > best.getValue(own) }
            if (rankBest == 1) top1b++
            if (rankBest <= 3) top3b++
            genuine += best.getValue(own)
            if (best.getValue(own) >= same) knownSame++
            val others = best.filterKeys { it != own }.values
            impostor += others
            val top = others.maxOrNull() ?: 0.0
            if (top >= same) unseenSame++
            if (top >= maybe) unseenMaybe++
        }
        val n = probes.size.toDouble()
        val sorted = impostor.sorted()
        val t = sorted.takeIf { it.isNotEmpty() }?.let { it[((it.size - 1) * 0.95).toInt()] }
        return EmbeddingResult(
            probes.size, top1a / n, top1b / n, top3b / n, unseenSame / n, unseenMaybe / n, knownSame / n,
            genuine.average(), impostor.average(), t, t?.let { th -> genuine.count { it > th } / genuine.size.toDouble() },
        )
    }

    fun embeddingReport(r: EmbeddingResult, model: String, same: Double, maybe: Double): String {
        fun pct(v: Double) = String.format(Locale.US, "%.1f%%", v * 100)
        fun num(v: Double) = String.format(Locale.US, "%.3f", v)
        return buildString {
            appendLine("AI model ($model on the AI server; verdict thresholds same >= ${num(same)}, maybe >= ${num(maybe)})")
            appendLine("  Known cat: top-1 ${pct(r.top1BestPhoto)} (best photo), ${pct(r.top1Average)} (averaged), top-3 ${pct(r.top3BestPhoto)}")
            appendLine("  Same cat reaches \"same\": ${pct(r.knownSame)}")
            appendLine("  Unseen cat called \"same\": ${pct(r.unseenSame)}; \"maybe\" or better: ${pct(r.unseenMaybe)}")
            appendLine("  Cosine: same cat mean ${num(r.genuineMean)}, different cats mean ${num(r.impostorMean)}")
            r.threshold5?.let { appendLine("  At most 5% of different-cat pairs score above ${num(it)}; ${pct(r.genuineAtThreshold5 ?: 0.0)} of same-cat pairs do.") }
        }
    }

    /** A plain-text report to share or paste into the capstone write-up. */
    fun report(r: Result, appVersion: String): String {
        fun pct(v: Double) = String.format(Locale.US, "%.1f%%", v * 100)
        fun num(v: Double) = String.format(Locale.US, "%.3f", v)
        return buildString {
            appendLine("CatMap recognition test (app $appVersion)")
            appendLine("Dataset: ${r.identities} cats, ${r.photos} photos, ${r.probes} test photos (cats with 2+ photos); ${r.skippedPhotos} photos could not be analysed.")
            appendLine("Location is not used in this test; scores are visual + coat attributes.")
            appendLine()
            appendLine("Known cat (closed set, leave-one-out)")
            appendLine("  App method (averaged descriptor): top-1 ${pct(r.top1)}, top-3 ${pct(r.top3)}")
            appendLine("  Best single photo instead:        top-1 ${pct(r.top1BestPhoto)}, top-3 ${pct(r.top3BestPhoto)}")
            appendLine("  Chance level: top-1 ${pct(1.0 / r.identities)}")
            appendLine()
            appendLine("Unseen cat (open set: every suggestion is wrong)")
            appendLine("  Any suggestion shown (score >= 0.45): ${pct(r.unseenAnySuggestion)}")
            appendLine("  Labelled \"possible\" or better:      ${pct(r.unseenPossible)}")
            appendLine("  Labelled \"strong\":                  ${pct(r.unseenStrong)}")
            appendLine()
            appendLine("Scores: same cat mean ${num(r.genuineMean)}, different cats mean ${num(r.impostorMean)}")
            r.threshold5?.let { appendLine("  At most 5% of different-cat pairs score above ${num(it)}; ${pct(r.genuineAtThreshold5 ?: 0.0)} of same-cat pairs do.") }
            appendLine("Average analysis time per photo: ${r.averageMillis.toInt()} ms")
            appendLine()
            appendLine("identity,file,rank_average,rank_best_photo,same_cat_score,best_other_score")
            r.perProbe.forEach { p -> appendLine("${p.identity},${p.file},${p.rankAverage},${p.rankBestPhoto},${num(p.genuine)},${num(p.bestImpostor)}") }
        }
    }
}
