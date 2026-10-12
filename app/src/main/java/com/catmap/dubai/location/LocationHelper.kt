package com.catmap.dubai.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

data class Fix(val lat: Double, val lng: Double, val accuracy: Double)

class LocationHelper(private val context: Context) {
    private val client by lazy { LocationServices.getFusedLocationProviderClient(context) }

    /** The most recent fix this session, used for distance labels without asking for GPS again. */
    private val _last = MutableStateFlow<Fix?>(null)
    val last: StateFlow<Fix?> = _last.asStateFlow()

    /** Fetches a position once if permission is already granted; never prompts. */
    suspend fun refreshQuietly() {
        if (hasPermission() && _last.value == null) current()
    }

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** Current position, or the last known one if a fresh fix doesn't arrive within 10 seconds. */
    @SuppressLint("MissingPermission")
    suspend fun current(): Fix? {
        if (!hasPermission()) return null
        return runCatching {
            val token = CancellationTokenSource()
            val fresh = withTimeoutOrNull(10_000) {
                client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, token.token).await()
            }
            if (fresh == null) token.cancel()
            val location = fresh ?: client.lastLocation.await()
            location?.let { Fix(it.latitude, it.longitude, it.accuracy.toDouble()) }
        }.getOrNull()?.also { _last.value = it }
    }
}
