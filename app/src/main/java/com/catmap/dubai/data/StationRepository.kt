package com.catmap.dubai.data

import com.catmap.dubai.data.model.FeedingStation
import com.catmap.dubai.data.model.MaintenanceReport
import com.catmap.dubai.data.model.MaintenanceStatus
import com.catmap.dubai.data.model.StationCheckin
import com.catmap.dubai.data.model.Stats
import com.catmap.dubai.data.model.UserProfile
import com.catmap.dubai.gamification.Points
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.Calendar

class StationRepository(
    private val db: FirebaseFirestore,
    private val users: UserRepository,
    private val notifications: NotificationRepository,
    private val moderation: ModerationRepository,
) {
    private val stations = db.collection(Col.STATIONS)
    private val checkins = db.collection(Col.CHECKINS)
    private val maintenance = db.collection(Col.MAINTENANCE)

    fun stations(): Flow<List<FeedingStation>> = stations.listFlow<FeedingStation>().map { l -> l.sortedBy { it.name } }

    fun station(id: String): Flow<FeedingStation?> = stations.document(id).objectFlow()

    suspend fun save(station: FeedingStation, admin: UserProfile): String {
        val ref = if (station.id.isBlank()) stations.document() else stations.document(station.id)
        val saved = station.copy(
            neighborhood = Dubai.nearest(station.lat, station.lng).key,
            createdAt = if (station.createdAt == 0L) now() else station.createdAt,
            updatedAt = now(),
        )
        ref.set(saved).await()
        moderation.log(admin, if (station.id.isBlank()) "station_created" else "station_updated", "station", ref.id, station.name, "")
        return ref.id
    }

    suspend fun delete(station: FeedingStation, admin: UserProfile) {
        stations.document(station.id).delete().await()
        moderation.log(admin, "station_deleted", "station", station.id, station.name, "")
    }

    /** Adds clearly-labelled example stations so a fresh install has something on the map. */
    suspend fun seedSamples(admin: UserProfile) {
        val samples = listOf(
            Triple("Sample – Al Barsha Pond Park", 25.1027, 55.2003),
            Triple("Sample – Deira Souq Corner", 25.2706, 55.2990),
            Triple("Sample – Karama Park", 25.2410, 55.3010),
            Triple("Sample – JLT Cluster D", 25.0700, 55.1415),
            Triple("Sample – Mirdif Community Garden", 25.2170, 55.4180),
            Triple("Sample – Al Quoz Pond Park", 25.1440, 55.2380),
        )
        db.runBatch { b ->
            samples.forEach { (name, lat, lng) ->
                b.set(
                    stations.document(),
                    FeedingStation(
                        name = name,
                        description = "Example station created by the admin seeding tool. Edit or delete it.",
                        operatorName = "Ehsan volunteers",
                        schedule = "Daily, after Maghrib",
                        foodType = "Dry food",
                        lat = lat, lng = lng,
                        neighborhood = Dubai.nearest(lat, lng).key,
                        sample = true,
                        createdAt = now(), updatedAt = now(),
                    ),
                )
            }
        }.await()
        moderation.log(admin, "stations_seeded", "station", "", "${samples.size}", "")
    }

    fun checkins(stationId: String): Flow<List<StationCheckin>> =
        checkins.whereEqualTo("stationId", stationId).listFlow<StationCheckin>().map { l -> l.sortedByDescending { it.at }.take(50) }

    suspend fun checkIn(station: FeedingStation, catsSeen: Int, catIds: List<String>, food: String, water: String, note: String, me: UserProfile) {
        val at = now()
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        db.runBatch { b ->
            b.set(
                checkins.document(),
                StationCheckin(
                    stationId = station.id, stationName = station.name, userId = me.uid, username = me.username,
                    catsSeen = catsSeen.toLong(), catIds = catIds, foodLevel = food, waterLevel = water,
                    note = note.trim(), at = at,
                ),
            )
            b.update(
                stations.document(station.id),
                mapOf("checkinCount" to FieldValue.increment(1), "lastCheckinAt" to at, "lastCheckinBy" to me.username),
            )
            val stats = mapOf(Stats.CHECKINS to 1L, Stats.IFTAR_CHECKINS to if (hour in 18..19) 1L else 0L)
            users.addStats(b, me.uid, stats, Points.CHECKIN, station.neighborhood)
        }.await()
    }

    fun maintenanceFor(stationId: String): Flow<List<MaintenanceReport>> =
        maintenance.whereEqualTo("stationId", stationId).listFlow<MaintenanceReport>().map { l -> l.sortedByDescending { it.createdAt } }

    fun openMaintenance(): Flow<List<MaintenanceReport>> =
        maintenance.whereIn("status", listOf(MaintenanceStatus.OPEN.key, MaintenanceStatus.IN_PROGRESS.key))
            .listFlow<MaintenanceReport>().map { l -> l.sortedBy { it.createdAt } }

    suspend fun reportMaintenance(station: FeedingStation, issue: String, description: String, me: UserProfile) {
        db.runBatch { b ->
            b.set(
                maintenance.document(),
                MaintenanceReport(
                    stationId = station.id, stationName = station.name, userId = me.uid, username = me.username,
                    issue = issue, description = description.trim(), createdAt = now(), updatedAt = now(),
                ),
            )
            users.addStats(b, me.uid, mapOf(Stats.MAINTENANCE_REPORTS to 1L), Points.MAINTENANCE, rateLimited = true)
        }.await()
    }

    suspend fun updateMaintenance(report: MaintenanceReport, status: String, note: String, admin: UserProfile) {
        maintenance.document(report.id).update(
            mapOf("status" to status, "resolutionNote" to note.trim(), "resolvedBy" to admin.username, "updatedAt" to now()),
        ).await()
        moderation.log(admin, "maintenance_$status", "station", report.stationId, report.stationName, note)
        notifications.send(
            listOf(report.userId) - admin.uid, NotifType.MAINTENANCE_UPDATE,
            mapOf("station" to report.stationName, "status" to status), "station/${report.stationId}",
        )
    }
}
