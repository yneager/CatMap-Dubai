package com.catmap.dubai.ui.map

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.catmap.dubai.R
import com.catmap.dubai.data.AppSettings
import com.catmap.dubai.data.Dubai
import com.catmap.dubai.ui.components.AppTopBar
import com.catmap.dubai.ui.components.LocalContainer
import com.catmap.dubai.ui.components.LocalIsDark
import com.catmap.dubai.ui.navigation.Routes
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Pick a spot by moving the map under a fixed pin, optionally with a search-area radius.
 * The result goes back to the previous screen through its saved state.
 */
@Composable
fun LocationPickerScreen(nav: NavHostController, initialLat: Double?, initialLng: Double?, initialRadius: Double?, withRadius: Boolean) {
    val container = LocalContainer.current
    val scope = rememberCoroutineScope()
    val settings by container.settings.settings.collectAsStateWithLifecycle(AppSettings())
    val hasStart = initialLat != null && initialLng != null && Dubai.contains(initialLat, initialLng)
    val startLat = initialLat?.takeIf { hasStart } ?: MapMemory.lat
    val startLng = initialLng?.takeIf { hasStart } ?: MapMemory.lng
    var lat by remember { mutableStateOf(startLat) }
    var lng by remember { mutableStateOf(startLng) }
    var radius by remember { mutableFloatStateOf((initialRadius ?: 0.0).toFloat()) }
    var moving by remember { mutableStateOf(false) }
    val controller = remember { MapController() }
    val inside = Dubai.contains(lat, lng)
    // The pin lifts while the map moves and drops back with a bounce when it settles.
    val lift by animateDpAsState(if (moving) (-14).dp else 0.dp, spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "pin")

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted.values.any { it }) scope.launch {
            container.location.current()?.let { controller.flyTo(it.lat, it.lng, 17.0) }
        }
    }

    Scaffold(topBar = { AppTopBar(stringResource(if (withRadius) R.string.pick_search_area else R.string.pick_location), onBack = { nav.popBackStack() }) }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            CommunityMap(
                Modifier.fillMaxSize(),
                features = emptyList(),
                mode = settings.mapMode,
                dark = LocalIsDark.current,
                satellite = settings.satellite,
                controller = controller,
                area = if (withRadius && radius > 0) AreaCircle(lat, lng, radius.toDouble()) else null,
                initialLat = startLat,
                initialLng = startLng,
                initialZoom = if (hasStart) 16.5 else maxOf(MapMemory.zoom, 14.0),
                rememberCamera = false,
                showUserLocation = container.location.hasPermission(),
                onCameraMoveStarted = { moving = true },
                onCameraIdle = {
                    lat = it.latitude
                    lng = it.longitude
                    moving = false
                },
            )
            Icon(
                Icons.Default.Place,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.Center).size(52.dp).offset(y = (-26).dp + lift),
            )
            SmallFloatingActionButton(
                onClick = { permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) },
                modifier = Modifier.align(Alignment.TopEnd).padding(16.dp),
            ) { Icon(Icons.Default.MyLocation, stringResource(R.string.my_location)) }
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp).navigationBarsPadding()) {
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), elevation = CardDefaults.cardElevation(8.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            if (inside) Dubai.nearest(lat, lng).displayName() else stringResource(R.string.outside_dubai),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (inside) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
                        )
                        Text(stringResource(R.string.pick_location_hint), style = MaterialTheme.typography.bodySmall)
                        if (withRadius) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    if (radius <= 0f) stringResource(R.string.search_area_none)
                                    else stringResource(R.string.search_area_m, radius.roundToInt()),
                                    style = MaterialTheme.typography.labelLarge,
                                )
                            }
                            Slider(radius, { radius = (it / 50f).roundToInt() * 50f }, valueRange = 0f..3000f)
                        }
                        Button(
                            enabled = inside,
                            onClick = {
                                nav.previousBackStackEntry?.savedStateHandle?.apply {
                                    set(Routes.PICKED_LAT, lat)
                                    set(Routes.PICKED_LNG, lng)
                                    if (withRadius) set(Routes.PICKED_RADIUS, radius.toDouble())
                                }
                                nav.popBackStack()
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.use_this_location)) }
                    }
                }
            }
        }
    }
}
