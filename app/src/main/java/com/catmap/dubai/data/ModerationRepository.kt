package com.catmap.dubai.data

import com.catmap.dubai.data.model.ContentReport
import com.catmap.dubai.data.model.ModAction
import com.catmap.dubai.data.model.Roles
import com.catmap.dubai.data.model.UserProfile
import com.catmap.dubai.data.model.VolunteerApplication
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class ModerationRepository(private val db: FirebaseFirestore, private val notifications: NotificationRepository) {
    private val reports = db.collection(Col.REPORTS)
    private val actions = db.collection(Col.MOD_ACTIONS)
    private val apps = db.collection(Col.VOLUNTEER_APPS)

    // ---- Content reports ----

    suspend fun reportContent(targetType: String, targetId: String, targetLabel: String, reason: String, details: String, me: UserProfile) {
        db.runBatch { b ->
            b.set(
                reports.document(),
                ContentReport(
                    targetType = targetType, targetId = targetId, targetLabel = targetLabel.take(80),
                    reason = reason, details = details.trim().take(500),
                    reporterId = me.uid, reporterName = me.username, createdAt = now(),
                ),
            )
            b.update(db.collection(Col.USERS).document(me.uid), "lastPostAt", FieldValue.serverTimestamp())
        }.await()
    }

    fun openReports(): Flow<List<ContentReport>> =
        reports.whereEqualTo("status", "open").listFlow<ContentReport>().map { l -> l.sortedByDescending { it.createdAt } }

    suspend fun closeReport(report: ContentReport, status: String, resolution: String, moderator: UserProfile) {
        reports.document(report.id).update(
            mapOf("status" to status, "resolution" to resolution, "resolvedBy" to moderator.uid),
        ).await()
        log(moderator, "report_$status", report.targetType, report.targetId, report.targetLabel, resolution)
        notifications.send(
            listOf(report.reporterId), NotifType.MODERATION,
            mapOf("target" to "report", "label" to report.targetLabel, "decision" to status),
        )
    }

    // ---- Moderation log ----

    suspend fun log(moderator: UserProfile, action: String, targetType: String, targetId: String, targetLabel: String, note: String) {
        runCatching {
            actions.add(
                ModAction(
                    moderatorId = moderator.uid, moderatorName = moderator.username, action = action,
                    targetType = targetType, targetId = targetId, targetLabel = targetLabel, note = note, at = now(),
                ),
            ).await()
        }
    }

    fun actions(): Flow<List<ModAction>> =
        actions.orderBy("at", Query.Direction.DESCENDING).limit(300).listFlow()

    // ---- Volunteers ----

    fun applicationFlow(uid: String): Flow<VolunteerApplication?> = apps.document(uid).objectFlow()

    fun pendingApplications(): Flow<List<VolunteerApplication>> =
        apps.whereEqualTo("status", "pending").listFlow<VolunteerApplication>().map { l -> l.sortedBy { it.createdAt } }

    suspend fun applyAsVolunteer(me: UserProfile, motivation: String, experience: String, area: String) {
        db.runBatch { b ->
            b.set(
                apps.document(me.uid),
                VolunteerApplication(
                    userId = me.uid, username = me.username, motivation = motivation.trim(),
                    experience = experience.trim(), area = area, createdAt = now(),
                ),
            )
            b.update(db.collection(Col.USERS).document(me.uid), "volunteerStatus", "applied")
        }.await()
    }

    suspend fun decideApplication(app: VolunteerApplication, approve: Boolean, admin: UserProfile) {
        db.runBatch { b ->
            b.update(apps.document(app.id), mapOf("status" to if (approve) "approved" else "rejected", "reviewedBy" to admin.uid))
            val updates = mutableMapOf<String, Any>("volunteerStatus" to if (approve) "approved" else "rejected")
            if (approve) updates["role"] = Roles.VOLUNTEER
            b.update(db.collection(Col.USERS).document(app.userId), updates)
        }.await()
        log(admin, if (approve) "volunteer_approved" else "volunteer_rejected", "user", app.userId, app.username, "")
        notifications.send(
            listOf(app.userId), NotifType.VOLUNTEER_DECISION,
            mapOf("decision" to if (approve) "approved" else "rejected"), "profile",
        )
    }

    suspend fun changeRole(user: UserProfile, role: String, admin: UserProfile) {
        val updates = mutableMapOf<String, Any>("role" to role)
        updates["volunteerStatus"] = if (role == Roles.USER) "none" else "approved"
        db.collection(Col.USERS).document(user.uid).update(updates).await()
        log(admin, "role_$role", "user", user.uid, user.username, "")
        notifications.send(listOf(user.uid), NotifType.MODERATION, mapOf("target" to "role", "label" to role, "decision" to "changed"), "profile")
    }
}
