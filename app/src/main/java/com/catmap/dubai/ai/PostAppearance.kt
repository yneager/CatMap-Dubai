package com.catmap.dubai.ai

import android.graphics.Bitmap
import android.graphics.Color
import androidx.core.graphics.get
import com.catmap.dubai.data.distanceKm
import com.catmap.dubai.data.model.Post
import com.catmap.dubai.util.ImageUtils
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.min

/** Cat-independent descriptor: folded HSV histogram plus three-column RGB layout (27 values). */
object PostAppearance {
    const val HIST_BINS = 18
    const val SIZE = HIST_BINS + 9
    fun valid(values: List<Double>) = values.size == SIZE && values.all { it.isFinite() && it in 0.0..1.0 }
    fun describe(source: Bitmap): List<Double> {
        val bitmap=ImageUtils.scaleToMax(source,128)
        val values=DoubleArray(SIZE)
        val totals=DoubleArray(3)
        val hsv=FloatArray(3)
        var total=0.0
        for(y in 0 until bitmap.height) for(x in 0 until bitmap.width) {
            val dx=(x-bitmap.width/2.0)/(bitmap.width*0.3)
            val dy=(y-bitmap.height/2.0)/(bitmap.height*0.32)
            val weight=exp(-(dx*dx+dy*dy)/2)
            val pixel=bitmap[x,y]
            Color.colorToHSV(pixel,hsv)
            val bin=CatAnalyzer.histBin(hsv)
            values[if(bin<48) bin/4 else 12+bin-48]+=weight
            total+=weight
            val cell=(x*3/bitmap.width).coerceIn(0,2)
            totals[cell]+=weight
            values[HIST_BINS+cell*3]+=Color.red(pixel)/255.0*weight
            values[HIST_BINS+cell*3+1]+=Color.green(pixel)/255.0*weight
            values[HIST_BINS+cell*3+2]+=Color.blue(pixel)/255.0*weight
        }
        repeat(HIST_BINS) { values[it]/=total.coerceAtLeast(1e-9) }
        repeat(9) { values[HIST_BINS+it]/=totals[it/3].coerceAtLeast(1e-9) }
        return values.map { kotlin.math.round(it*10_000)/10_000 }
    }
    fun average(values: List<List<Double>>): List<Double> {
        val valid=values.filter(::valid)
        return if(valid.isEmpty()) emptyList() else List(SIZE) { index -> valid.sumOf { it[index] }/valid.size }
    }
    fun similarity(a:List<Double>,b:List<Double>): Double {
        if(!valid(a) || !valid(b)) return 0.0
        val histogram=(0 until HIST_BINS).sumOf { min(a[it],b[it]) }.coerceIn(0.0,1.0)
        val layout=(1-(HIST_BINS until SIZE).sumOf { abs(a[it]-b[it]) }/9).coerceIn(0.0,1.0)
        return .8*histogram+.2*layout
    }
    data class Match(val post: Post,val score:Double,val photoBased:Boolean)
    fun matches(post:Post,candidates:List<Post>):List<Match> = candidates.asSequence()
        .filter { it.id!=post.id && !it.hidden && it.status=="open" && it.animalType==post.animalType &&
            ((post.category=="lost" && it.category in listOf("found","spotted")) || (post.category in listOf("found","spotted") && it.category=="lost")) }
        .mapNotNull { candidate ->
            val distance=distanceKm(post.lat,post.lng,candidate.lat,candidate.lng)
            if(!distance.isFinite() || distance>10) return@mapNotNull null
            val time=exp(-abs(post.createdAt-candidate.createdAt).toDouble()/(21*86_400_000.0))
            val proximity=exp(-distance/3)
            val photo=similarity(post.fingerprint,candidate.fingerprint)
            val photoBased=valid(post.fingerprint)&&valid(candidate.fingerprint)
            Match(candidate,if(photoBased) .6*photo+.25*proximity+.15*time else .6*proximity+.4*time,photoBased)
        }.sortedByDescending { it.score }.take(6).toList()
}
