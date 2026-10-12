package com.catmap.dubai.data

import com.catmap.dubai.data.model.RescueReport
import com.catmap.dubai.data.model.RescueStatus
import com.catmap.dubai.data.model.RescueUpdate
import com.catmap.dubai.data.model.Roles
import com.catmap.dubai.data.model.Stats
import com.catmap.dubai.data.model.UserProfile
import com.catmap.dubai.gamification.Points
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class RescueRepository(
    private val db: FirebaseFirestore,
    private val users: UserRepository,
    private val notifications: NotificationRepository,
    private val moderation: ModerationRepository,
) {
    private val col = db.collection(Col.RESCUES)

    /** Statuses volunteers may see; submitted and rejected reports stay with moderators. */
    val volunteerStatuses = listOf(RescueStatus.APPROVED.key, RescueStatus.IN_PROGRESS.key, RescueStatus.RESOLVED.key)

    fun myReports(uid: String): Flow<List<RescueReport>> =
        col.whereEqualTo("reporterId", uid).listFlow<RescueReport>().map { l -> l.sortedByDescending { it.createdAt } }

    fun volunteerBoard(): Flow<List<RescueReport>> =
        col.whereIn("status", volunteerStatuses).listFlow<RescueReport>().map { l -> l.sortedByDescending { it.updatedAt } }

    fun allReports(): Flow<List<RescueReport>> =
        col.listFlow<RescueReport>().map { l -> l.sortedByDescending { it.createdAt } }

    fun pending(): Flow<List<RescueReport>> =
        col.whereEqualTo("status", RescueStatus.SUBMITTED.key).listFlow<RescueReport>().map { l -> l.sortedBy { it.createdAt } }

    fun report(id: String): Flow<RescueReport?> = col.document(id).objectFlow()

    suspend fun submit(report: RescueReport, me: UserProfile): String {
        val ref = col.document()
        val created = now()
        val hood = Dubai.nearest(report.lat, report.lng).key
        val full = report.copy(
            lat = LocationPrivacy.approximate(report.lat),
            lng = LocationPrivacy.approximate(report.lng),
            neighborhood = hood,
            reporterId = me.uid,
            reporterName = me.username,
            status = RescueStatus.SUBMITTED.key,
            updates = listOf(RescueUpdate(RescueStatus.SUBMITTED.key, "", me.uid, me.username, created)),
            createdAt = created,
            updatedAt = created,
        )
        db.runBatch { b ->
            b.set(ref, full)
            users.addStats(b, me.uid, mapOf(Stats.RESCUE_REPORTS to 1L), Points.RESCUE, hood, rateLimited = true)
        }.await()
        val staff = runCatching { users.uidsWithRoles(Roles.MODERATOR, Roles.ADMIN) }.getOrDefault(emptyList())
        notifications.send(staff - me.uid, NotifType.RESCUE_NEW_MOD, mapOf("type" to report.type, "area" to hood), "rescue/${ref.id}")
        return ref.id
    }

    /**
     * Moves a report to [status] and records who did it. Approving makes the report visible to
     * volunteers and alerts them; every change notifies the reporter.
     */
    suspend fun changeStatus(report: RescueReport, status: String, note: String, by: UserProfile, assignToMe: Boolean = false) {
        val update = RescueUpdate(status, note.trim(), by.uid, by.username, now())
        val changes = mutableMapOf<String, Any>(
            "status" to status,
            "updates" to FieldValue.arrayUnion(update.asMap()),
            "updatedAt" to now(),
        )
        if (assignToMe) {
            changes["assignedTo"] = by.uid
            changes["assignedName"] = by.username
        }
        col.document(report.id).update(changes).await()

        if (report.status == RescueStatus.SUBMITTED.key && status in listOf(RescueStatus.APPROVED.key, RescueStatus.REJECTED.key)) {
            moderation.log(by, "rescue_$status", "rescue", report.id, Dubai.byKey(report.neighborhood).nameEn, note)
        }
        notifications.send(listOf(report.reporterId) - by.uid, NotifType.RESCUE_UPDATE, mapOf("status" to status, "area" to report.neighborhood), "rescue/${report.id}")
        if (status == RescueStatus.APPROVED.key) {
            val volunteers = runCatching { users.uidsWithRoles(Roles.VOLUNTEER, Roles.MODERATOR, Roles.ADMIN) }.getOrDefault(emptyList())
            notifications.send(volunteers - by.uid, NotifType.RESCUE_NEW_VOLUNTEER, mapOf("type" to report.type, "area" to report.neighborhood), "rescue/${report.id}")
        }
    }

    suspend fun addNote(report: RescueReport, note: String, by: UserProfile) {
        val update = RescueUpdate(report.status, note.trim(), by.uid, by.username, now())
        col.document(report.id).update(mapOf("updates" to FieldValue.arrayUnion(update.asMap()), "updatedAt" to now())).await()
        notifications.send(listOf(report.reporterId) - by.uid, NotifType.RESCUE_UPDATE, mapOf("status" to report.status, "area" to report.neighborhood), "rescue/${report.id}")
    }
}

private fun RescueUpdate.asMap() = mapOf("status" to status, "note" to note, "byUid" to byUid, "byName" to byName, "at" to at)
