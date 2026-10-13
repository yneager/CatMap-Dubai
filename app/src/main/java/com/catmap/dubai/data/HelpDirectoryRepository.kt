package com.catmap.dubai.data

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

data class HelpContact(
    @DocumentId var id: String = "",
    var name: String = "",
    var kind: String = "vet",
    var phone: String = "",
    var address: String = "",
    var website: String = "",
    var note: String = "",
    var demo: Boolean = false,
    var updatedAt: Long = 0,
)

class HelpDirectoryRepository(private val db: FirebaseFirestore) {
    private val contacts = db.collection("helpDirectory")
    fun all() = contacts.listFlow<HelpContact>().map { it.sortedBy { entry -> entry.name.lowercase() } }
    suspend fun save(contact: HelpContact) {
        val ref=if(contact.id.isBlank()) contacts.document() else contacts.document(contact.id)
        ref.set(contact.copy(name=contact.name.trim(),updatedAt=now())).await()
    }
    suspend fun delete(contact: HelpContact) { contacts.document(contact.id).delete().await() }
}
