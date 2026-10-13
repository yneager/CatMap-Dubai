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
data class AdminAnalytics(val weeks:List<AnalyticsWeek>,val searchPosts:Long,val reunited:Long) {
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
        AdminAnalytics(weeks,total.await(),reunited.await()).also { cached=now() to it }
    }
}
