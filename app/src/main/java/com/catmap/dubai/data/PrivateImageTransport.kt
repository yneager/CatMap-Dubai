package com.catmap.dubai.data

import android.util.Base64
import com.catmap.dubai.BuildConfig
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Authenticated transfers bypass the SDK's durable photo cache. Tokens are never logged. */
internal class PrivateImageTransport(private val db: FirebaseFirestore) {
    private suspend fun request(suffix:String, body:JSONObject?=null):JSONObject {
        val auth=FirebaseAuth.getInstance(db.app)
        val user=requireNotNull(auth.currentUser)
        val token=requireNotNull(user.getIdToken(false).await().token)
        val appCheck=if(BuildConfig.EMULATOR_HOST.isEmpty()) runCatching { FirebaseAppCheck.getInstance(db.app).getAppCheckToken(false).await().token }.getOrNull() else null
        val project=requireNotNull(db.app.options.projectId)
        val origin=if(BuildConfig.EMULATOR_HOST.isNotEmpty()) "http://${BuildConfig.EMULATOR_HOST}:8080" else "https://firestore.googleapis.com"
        return withContext(Dispatchers.IO) {
            val connection=URL("$origin/v1/projects/$project/databases/(default)/documents$suffix").openConnection() as HttpURLConnection
            try {
                connection.connectTimeout=15_000;connection.readTimeout=15_000;connection.useCaches=false
                connection.setRequestProperty("Authorization","Bearer $token")
                connection.setRequestProperty("Cache-Control","no-store")
                appCheck?.let {connection.setRequestProperty("X-Firebase-AppCheck",it)}
                if(body!=null) {
                    connection.requestMethod="POST";connection.doOutput=true;connection.setRequestProperty("Content-Type","application/json")
                    connection.outputStream.use {it.write(body.toString().toByteArray())}
                }
                check(connection.responseCode in 200..299) {"Private photo transfer failed (${connection.responseCode})"}
                check(auth.currentUser?.uid==user.uid) {"Account changed during private photo transfer"}
                connection.inputStream.bufferedReader().use {JSONObject(it.readText())}
            } finally {connection.disconnect()}
        }
    }
    suspend fun bytes(id:String):ByteArray? {
        require(id.matches(Regex("c_[A-Za-z0-9_-]+")))
        val value=request("/images/$id").optJSONObject("fields")?.optJSONObject("data")?.optString("bytesValue") ?: return null
        return Base64.decode(value,Base64.DEFAULT)
    }
    private fun fields(values:Map<String,Any>)=JSONObject().apply {values.forEach {(key,value)->put(key,JSONObject().put(if(value is Number) "integerValue" else "stringValue",value.toString()))}}
    suspend fun upload(id:String,owner:String,chat:String,full:ByteArray,thumb:ByteArray,at:Long) {
        val project=requireNotNull(db.app.options.projectId)
        val prefix="projects/$project/databases/(default)/documents/"
        val common=mapOf("ownerId" to owner,"chatId" to chat,"audience" to "chat","rootId" to id,"messageId" to "","createdAt" to at)
        val writes=JSONArray()
        for((key,data) in listOf(id to full,"${id}_t" to thumb)) writes.put(JSONObject().put("update",JSONObject().put("name",prefix+"images/$key")
            .put("fields",fields(common).put("data",JSONObject().put("bytesValue",Base64.encodeToString(data,Base64.NO_WRAP)))))
            .put("currentDocument",JSONObject().put("exists",false)))
        writes.put(JSONObject().put("update",JSONObject().put("name",prefix+"mediaUploads/$id").put("fields",fields(mapOf("ownerId" to owner,"chatId" to chat,"audience" to "chat","status" to "staged","expiresAt" to at+7L*86400000))))
            .put("currentDocument",JSONObject().put("exists",false)))
        request(":commit",JSONObject().put("writes",writes))
    }
}
