package com.catmap.dubai.data

import com.google.firebase.firestore.AggregateSource
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

@androidx.compose.runtime.Immutable
data class AnalyticsWeek(val start:Long,val posts:Long,val members:Long)
/** The lost-to-reunited workflow in numbers: suggestions by strength, owners' answers and how reunions were credited. */
data class MatchStats(val total:Int,val strong:Int,val possible:Int,val weak:Int,val maybe:Int,val notMine:Int,val viaCatmap:Int,val confirmed:Int,val verified:Int)
data class AdminAnalytics(val weeks:List<AnalyticsWeek>,val searchPosts:Long,val reunited:Long,val matches:MatchStats?=null) {
    val reunionRate:Double get()=if(searchPosts==0L) 0.0 else reunited*100.0/searchPosts
}

class AdminAnalyticsRepository(private val db:FirebaseFirestore) {
    private var cached:Pair<Long,AdminAnalytics>?=null
    suspend fun load():AdminAnalytics=coroutineScope {
        cached?.takeIf { now()-it.first<5*60_000 }?.second?.let { return@coroutineScope it }
        val zone=ZoneId.of("Asia/Dubai")
        val monday=LocalDate.now(zone).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val starts=(7L downTo 0).map { monday.minusWeeks(it).atStartOfDay(zone).toInstant().toEpochMilli() }
        val weeks=starts.map { start -> async {
            val end=start+7*86_400_000L
            val posts=async { db.collection(Col.POSTS).whereGreaterThanOrEqualTo("createdAt",start).whereLessThan("createdAt",end).count().get(AggregateSource.SERVER).await().count }
            val members=async { db.collection(Col.USERS).whereGreaterThanOrEqualTo("createdAt",start).whereLessThan("createdAt",end).count().get(AggregateSource.SERVER).await().count }
            AnalyticsWeek(start,posts.await(),members.await())
        } }.map { it.await() }
        val search=db.collection(Col.POSTS).whereIn("category",listOf("lost","found")).whereGreaterThanOrEqualTo("createdAt",starts.first())
        val total=async { search.count().get(AggregateSource.SERVER).await().count }
        val reunited=async { search.whereEqualTo("status","reunited").count().get(AggregateSource.SERVER).await().count }
        // Count queries cost one read per 1,000 matches, where downloading the matches cost one read each (up to 5,000).
        val matches=async { runCatching {
            val col=db.collection(Col.MATCHES)
            suspend fun count(q:com.google.firebase.firestore.Query)=q.count().get(AggregateSource.SERVER).await().count.toInt()
            val reunions=col.whereEqualTo("ownerState","reunited")
            val strong=async { count(col.whereEqualTo("tier","strong")) }
            val possible=async { count(col.whereEqualTo("tier","possible")) }
            val weak=async { count(col.whereEqualTo("tier","weak")) }
            val maybe=async { count(col.whereEqualTo("ownerState","maybe")) }
            val notMine=async { count(col.whereEqualTo("ownerState","not_mine")) }
            val viaCatmap=async { count(reunions) }
            val confirmed=async { count(reunions.whereEqualTo("reporterState","confirmed")) }
            // Needs the (ownerState, verifiedBy) index in firestore.indexes.json.
            val verified=async { count(reunions.whereGreaterThan("verifiedBy","")) }
            MatchStats(strong.await()+possible.await()+weak.await(),strong.await(),possible.await(),weak.await(),maybe.await(),notMine.await(),
                viaCatmap.await(),confirmed.await(),verified.await())
        }.getOrNull() }
        AdminAnalytics(weeks,total.await(),reunited.await(),matches.await()).also { cached=now() to it }
    }
}
