package com.catmap.dubai.data

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.google.firebase.Timestamp
import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File

/** Pure JSON writer used by export tests; binary photos and push credentials are never included. */
object UserDataExport {
    fun fileName(uid:String)="catmap-my-data-"+java.security.MessageDigest.getInstance("SHA-256").digest(uid.toByteArray()).take(8).joinToString("") {"%02x".format(it)}+".json"
    fun build(uid:String,at:Long,records:Map<String,List<Map<String,Any?>>>):String = json(mapOf("schemaVersion" to 1,"userId" to uid,"exportedAt" to at,"records" to records))
    private fun json(value:Any?):String=when(value) {
        null -> "null"
        is String -> "\""+buildString { value.forEach { c -> append(when(c) { '"'->"\\\""; '\\'->"\\\\"; '\n'->"\\n"; '\r'->"\\r"; '\t'->"\\t"; else->if(c.code<32) "\\u%04x".format(c.code) else c.toString() }) } }+"\""
        is Boolean -> value.toString()
        is Number -> if(value.toDouble().isFinite()) value.toString() else "null"
        is Map<*,*> -> value.entries.joinToString(",","{","}") { json(it.key.toString())+":"+json(it.value) }
        is Iterable<*> -> value.joinToString(",","[","]") { json(it) }
        else -> json(value.toString())
    }
}

class UserDataExportRepository(private val context:Context,private val db:FirebaseFirestore) {
    suspend fun clear(uid:String)=withContext(Dispatchers.IO) {File(context.cacheDir,"share/"+UserDataExport.fileName(uid)).delete()}
    private fun snapshot(doc:DocumentSnapshot):Map<String,Any?> = (doc.data.orEmpty().filterKeys { it !in listOf("data","tokens","password") }.mapValues { (_,v) -> clean(v) } + ("id" to doc.id))
    private fun clean(value:Any?):Any? = when(value) {
        is Timestamp -> value.toDate().time
        is Blob -> null
        is Map<*,*> -> value.entries.filter { it.key.toString() !in listOf("tokens","password") }.associate { it.key.toString() to clean(it.value) }
        is List<*> -> value.map(::clean)
        else -> value
    }

    suspend fun export(uid:String):Intent = withContext(Dispatchers.IO) {
        val records=linkedMapOf<String,List<Map<String,Any?>>>()
        val user=db.collection(Col.USERS).document(uid)
        records["profile"]=listOf(snapshot(user.get().await()))
        records["account"]=listOf(snapshot(user.collection(Col.PRIVATE).document("account").get().await()))
        for(collection in listOf(Col.SITTERS,Col.AREA_FOLLOWS,Col.VOLUNTEER_APPS)) {
            val document=db.collection(collection).document(uid).get().await()
            records[collection]=if(document.exists()) listOf(snapshot(document)) else emptyList()
        }
        for((collection,fields) in listOf(
            Col.POSTS to listOf("ownerId"),Col.SIGHTINGS to listOf("userId"),Col.RESCUES to listOf("reporterId"),
            Col.CHECKINS to listOf("userId"),Col.PRIVATE_LOCATIONS to listOf("ownerId"),Col.CATS to listOf("createdBy"),Col.MAINTENANCE to listOf("userId"),Col.REPORTS to listOf("reporterId"),
            Col.MERGES to listOf("requestedBy"),Col.IMAGES to listOf("ownerId"),Col.MEDICAL to listOf("byUid"),
            Col.ADOPTION_REQUESTS to listOf("requesterId","ownerId"),Col.BOOKINGS to listOf("ownerId","sitterId"),Col.REVIEWS to listOf("authorId","sitterId"),
        )) {
            val collected=linkedMapOf<String,Map<String,Any?>>()
            for(field in fields) db.collection(collection).whereEqualTo(field,uid).pages().collect {page ->page.forEach {collected[it.id]=snapshot(it)}}
            records[collection]=collected.values.toList()
        }
        for(collection in listOf(Col.NOTIFICATIONS,Col.FAVORITES,Col.BLOCKED)) records[collection]=user.collection(collection).get().await().documents.map(::snapshot)
        val chats=db.collection(Col.CHATS).whereArrayContains("members",uid).get().await().documents
        records["chats"]=chats.map(::snapshot)
        records["messages"]=chats.flatMap { chat -> chat.reference.collection(Col.MESSAGES).get().await().documents.map { snapshot(it)+( "chatId" to chat.id) } }
        records["postUpdates"]=db.collectionGroup(Col.UPDATES).whereEqualTo("byUid",uid).get().await().documents.filter {it.reference.path.split('/').let {parts ->parts.size==4 && parts.first()==Col.POSTS}}.map(::snapshot)
        val dir=File(context.cacheDir,"share").apply { mkdirs() }
        val target=File(dir,UserDataExport.fileName(uid))
        target.writeText(UserDataExport.build(uid,now(),records))
        val uri=FileProvider.getUriForFile(context,context.packageName+".fileprovider",target)
        Intent(Intent.ACTION_SEND).apply { type="application/json"; putExtra(Intent.EXTRA_STREAM,uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
    }
}
