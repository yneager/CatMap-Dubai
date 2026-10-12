package com.catmap.dubai.ui.stations

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.catmap.dubai.R
import com.catmap.dubai.data.Dubai
import com.catmap.dubai.data.distanceKm
import com.catmap.dubai.data.loaded
import com.catmap.dubai.data.model.FeedingStation
import com.catmap.dubai.data.model.MaintenanceIssue
import com.catmap.dubai.data.model.MaintenanceStatus
import com.catmap.dubai.data.model.SupplyLevel
import com.catmap.dubai.data.model.isAdminRole
import com.catmap.dubai.data.model.labeledFrom
import com.catmap.dubai.ui.components.AppTopBar
import com.catmap.dubai.ui.components.ClickableCard
import com.catmap.dubai.ui.components.ConfirmDialog
import com.catmap.dubai.ui.components.EmptyState
import com.catmap.dubai.ui.components.InfoRow
import com.catmap.dubai.ui.components.LabeledChips
import com.catmap.dubai.ui.components.LabeledDropdown
import com.catmap.dubai.ui.components.LoadingBox
import com.catmap.dubai.ui.components.LocalContainer
import com.catmap.dubai.ui.components.LocalIsDark
import com.catmap.dubai.ui.components.SectionHeader
import com.catmap.dubai.ui.components.StatusPill
import com.catmap.dubai.ui.components.dateTime
import com.catmap.dubai.ui.components.rememberRunner
import com.catmap.dubai.ui.components.relativeTime
import com.catmap.dubai.ui.map.CommunityMap
import com.catmap.dubai.ui.map.MapController
import com.catmap.dubai.ui.map.MapFeature
import com.catmap.dubai.ui.map.MarkerIcons
import com.catmap.dubai.ui.map.MiniMap
import com.catmap.dubai.ui.map.PointKind
import com.catmap.dubai.ui.navigation.NotificationBell
import com.catmap.dubai.ui.navigation.Routes
import com.catmap.dubai.ui.theme.statusColor

@Composable
fun StationsScreen(nav: NavHostController) {
    val container = LocalContainer.current
    val context = LocalContext.current
    val runner = rememberRunner()
    val stations by remember { container.stations.stations() }.collectAsStateWithLifecycle(null)
    val me by container.session.profile.collectAsStateWithLifecycle()
    val isAdmin = isAdminRole(me?.role)
    var showInactive by rememberSaveable { mutableStateOf(false) }
    val settings by container.settings.settings.collectAsStateWithLifecycle(com.catmap.dubai.data.AppSettings())
    val controller = remember { MapController() }
    val shown = stations?.filter { showInactive || it.active }
    val features = remember(shown) { shown.orEmpty().map { MapFeature(it.id, PointKind.STATION, it.lat, it.lng, MarkerIcons.STATION) } }

    Scaffold(
        topBar = { AppTopBar(stringResource(R.string.tab_stations), onBack = { nav.popBackStack() }, actions = { NotificationBell(nav) }) },
        floatingActionButton = {
            if (isAdmin) FloatingActionButton(onClick = { nav.navigate(Routes.stationEdit()) }) { Icon(Icons.Default.Add, stringResource(R.string.add_station)) }
        },
    ) { padding ->
        // Extra bottom space so the admin's add button never covers the last station.
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = if (isAdmin) 88.dp else 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(stringResource(R.string.stations_intro), style = MaterialTheme.typography.bodyMedium)
            }
            item {
                Box(Modifier.fillMaxWidth().height(240.dp).clip(RoundedCornerShape(16.dp))) {
                    CommunityMap(
                        Modifier.fillMaxSize(), features, settings.mapMode, LocalIsDark.current, controller,
                        initialZoom = 10.5, rememberCamera = false,
                        onFeatureClick = { nav.navigate(Routes.station(it.id)) },
                    )
                }
            }
            if (isAdmin) {
                item {
                    FilterChip(showInactive, { showInactive = !showInactive }, label = { Text(stringResource(R.string.show_inactive)) })
                }
            }
            when {
                shown == null -> item { LoadingBox(Modifier.fillMaxWidth().height(120.dp)) }
                shown.isEmpty() -> item {
                    EmptyState(Icons.Outlined.Restaurant, stringResource(R.string.no_stations)) {
                        if (isAdmin) {
                            Button(onClick = { me?.let { m -> runner.run(R.string.samples_added) { container.stations.seedSamples(m) } } }) {
                                Text(stringResource(R.string.add_sample_stations))
                            }
                        }
                    }
                }
                else -> items(shown, key = { it.id }) { s -> StationCard(s) { nav.navigate(Routes.station(s.id)) } }
            }
        }
    }
}

@Composable
private fun StationCard(s: FeedingStation, onClick: () -> Unit) {
    ClickableCard(onClick) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Restaurant, null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(s.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f, fill = false))
                    if (s.verified) Icon(Icons.Default.Verified, stringResource(R.string.verified), Modifier.padding(start = 4.dp).size(16.dp), tint = MaterialTheme.colorScheme.secondary)
                }
                Text(Dubai.nameOf(s.neighborhood), style = MaterialTheme.typography.bodySmall)
                Text(
                    if (s.lastCheckinAt > 0) stringResource(R.string.last_checkin, relativeTime(s.lastCheckinAt)) else stringResource(R.string.no_checkins_yet),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!s.active) StatusPill(stringResource(R.string.inactive), MaterialTheme.colorScheme.outline)
        }
    }
}

private sealed interface Activity {
    val at: Long
}

private data class CheckinActivity(val c: com.catmap.dubai.data.model.StationCheckin) : Activity {
    override val at get() = c.at
}

private data class IssueActivity(val m: com.catmap.dubai.data.model.MaintenanceReport) : Activity {
    override val at get() = m.createdAt
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StationDetailScreen(nav: NavHostController, stationId: String) {
    val container = LocalContainer.current
    val runner = rememberRunner()
    val context = LocalContext.current
    val stationState by remember(stationId) { container.stations.station(stationId).loaded() }.collectAsStateWithLifecycle(null)
    val station = stationState?.value
    val checkins by remember(stationId) { container.stations.checkins(stationId) }.collectAsStateWithLifecycle(emptyList())
    val issues by remember(stationId) { container.stations.maintenanceFor(stationId) }.collectAsStateWithLifecycle(emptyList())
    val me by container.session.profile.collectAsStateWithLifecycle()
    val isAdmin = isAdminRole(me?.role)
    var dialog by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            AppTopBar(station?.name ?: "", onBack = { nav.popBackStack() }) {
                if (isAdmin && station != null) {
                    IconButton(onClick = { nav.navigate(Routes.stationEdit(stationId)) }) { Icon(Icons.Default.Edit, stringResource(R.string.edit)) }
                    IconButton(onClick = { dialog = "delete" }) { Icon(Icons.Default.Delete, stringResource(R.string.delete)) }
                }
            }
        },
    ) { padding ->
        val s = station
        if (s == null) {
            if (stationState == null) LoadingBox(Modifier.padding(padding))
            else EmptyState(Icons.Outlined.Restaurant, stringResource(R.string.station_not_found), Modifier.padding(padding))
            return@Scaffold
        }
        val activity: List<Activity> = (checkins.map { CheckinActivity(it) } + issues.map { IssueActivity(it) }).sortedByDescending { it.at }.take(40)
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (s.verified) StatusPill(stringResource(R.string.verified_station), MaterialTheme.colorScheme.secondary)
                        if (!s.active) StatusPill(stringResource(R.string.inactive), MaterialTheme.colorScheme.outline)
                        if (s.sample) StatusPill(stringResource(R.string.sample_data), MaterialTheme.colorScheme.tertiary)
                    }
                    if (s.description.isNotBlank()) Text(s.description)
                    MiniMap(s.lat, s.lng, PointKind.STATION, LocalIsDark.current)
                    InfoRow(stringResource(R.string.neighborhood), Dubai.nameOf(s.neighborhood))
                    InfoRow(stringResource(R.string.operator), s.operatorName)
                    InfoRow(stringResource(R.string.schedule), s.schedule)
                    InfoRow(stringResource(R.string.food_type), s.foodType)
                    InfoRow(stringResource(R.string.water), stringResource(if (s.hasWater) R.string.yes else R.string.no))
                    InfoRow(stringResource(R.string.total_checkins), s.checkinCount.toString())
                    InfoRow(stringResource(R.string.last_visit), if (s.lastCheckinAt > 0) relativeTime(s.lastCheckinAt) + " · @" + s.lastCheckinBy else "")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { dialog = "checkin" }, enabled = s.active, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.CheckCircle, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.check_in))
                        }
                        OutlinedButton(onClick = { dialog = "issue" }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Build, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.report_problem))
                        }
                    }
                    SectionHeader(stringResource(R.string.recent_activity))
                    if (activity.isEmpty()) Text(stringResource(R.string.no_activity), style = MaterialTheme.typography.bodySmall)
                }
            }
            items(activity) { a ->
                when (a) {
                    is CheckinActivity -> Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text(stringResource(R.string.checkin_by, a.c.username, a.c.catsSeen), fontWeight = FontWeight.SemiBold)
                        Text(
                            dateTime(a.c.at) + " · " + stringResource(R.string.food) + ": " + stringResource(labeledFrom(a.c.foodLevel, SupplyLevel.UNKNOWN).label) +
                                " · " + stringResource(R.string.water) + ": " + stringResource(labeledFrom(a.c.waterLevel, SupplyLevel.UNKNOWN).label),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        if (a.c.note.isNotBlank()) Text(a.c.note, style = MaterialTheme.typography.bodyMedium)
                        HorizontalDivider(Modifier.padding(top = 6.dp))
                    }
                    is IssueActivity -> Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        val st = labeledFrom(a.m.status, MaintenanceStatus.OPEN)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Build, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(labeledFrom(a.m.issue, MaintenanceIssue.OTHER).label), fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            StatusPill(stringResource(st.label), statusColor(st.color))
                        }
                        Text(dateTime(a.m.createdAt) + " · @" + a.m.username, style = MaterialTheme.typography.bodySmall)
                        if (a.m.description.isNotBlank()) Text(a.m.description, style = MaterialTheme.typography.bodyMedium)
                        if (a.m.resolutionNote.isNotBlank()) Text(stringResource(R.string.admin_note, a.m.resolutionNote), style = MaterialTheme.typography.bodySmall)
                        HorizontalDivider(Modifier.padding(top = 6.dp))
                    }
                }
            }
        }

        val m = me ?: return@Scaffold
        when (dialog) {
            "checkin" -> CheckinDialog(s, onDismiss = { dialog = null }) { count, catIds, food, water, note ->
                runner.run(R.string.checked_in) { container.stations.checkIn(s, count, catIds, food, water, note, m) }
            }
            "issue" -> IssueDialog(onDismiss = { dialog = null }) { issue, description ->
                runner.run(R.string.problem_reported) { container.stations.reportMaintenance(s, issue, description, m) }
            }
            "delete" -> ConfirmDialog(
                stringResource(R.string.delete_station), stringResource(R.string.delete_station_body, s.name), stringResource(R.string.delete),
                onConfirm = { runner.run(onSuccess = { nav.popBackStack() }) { container.stations.delete(s, m) } },
                onDismiss = { dialog = null },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CheckinDialog(
    station: FeedingStation,
    onDismiss: () -> Unit,
    onSave: (Int, List<String>, String, String, String) -> Unit,
) {
    val container = LocalContainer.current
    val cats by remember { container.cats.allCats() }.collectAsStateWithLifecycle(emptyList())
    val nearby = remember(cats) { cats.filter { distanceKm(it.lat, it.lng, station.lat, station.lng) < 0.8 }.take(12) }
    var count by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var food by remember { mutableStateOf(SupplyLevel.UNKNOWN) }
    var water by remember { mutableStateOf(SupplyLevel.UNKNOWN) }
    var note by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.check_in)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.cats_seen_here))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { if (count > 0) count-- }) { Icon(Icons.Default.Remove, stringResource(R.string.decrease)) }
                    Text(count.toString(), style = MaterialTheme.typography.titleLarge)
                    IconButton(onClick = { count++ }) { Icon(Icons.Default.Add, stringResource(R.string.increase)) }
                }
                if (nearby.isNotEmpty()) {
                    Text(stringResource(R.string.known_cats_nearby), style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        nearby.forEach { cat ->
                            FilterChip(cat.id in selected, {
                                selected = if (cat.id in selected) selected - cat.id else selected + cat.id
                                if (count < selected.size) count = selected.size
                            }, label = { Text(cat.name) })
                        }
                    }
                }
                Text(stringResource(R.string.food_level), style = MaterialTheme.typography.labelLarge)
                LabeledChips(SupplyLevel.entries, setOf(food), { food = it })
                Text(stringResource(R.string.water_level), style = MaterialTheme.typography.labelLarge)
                LabeledChips(SupplyLevel.entries, setOf(water), { water = it })
                OutlinedTextField(note, { note = it.take(300) }, label = { Text(stringResource(R.string.note_optional)) }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onClick = { onSave(count, selected.toList(), food.key, water.key, note); onDismiss() }) { Text(stringResource(R.string.check_in)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun IssueDialog(onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var issue by remember { mutableStateOf(MaintenanceIssue.EMPTY_FOOD) }
    var description by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.report_problem)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                LabeledDropdown(stringResource(R.string.problem), MaintenanceIssue.entries, issue, { issue = it })
                OutlinedTextField(description, { description = it.take(500) }, label = { Text(stringResource(R.string.details_optional)) }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onClick = { onSave(issue.key, description); onDismiss() }) { Text(stringResource(R.string.send)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
fun StationEditScreen(nav: NavHostController, stationId: String?) {
    val container = LocalContainer.current
    val runner = rememberRunner()
    val context = LocalContext.current
    val me by container.session.profile.collectAsStateWithLifecycle()
    val existing by produceState<FeedingStation?>(if (stationId == null) FeedingStation(lat = com.catmap.dubai.ui.map.MapMemory.lat, lng = com.catmap.dubai.ui.map.MapMemory.lng) else null, stationId) {
        if (stationId != null) container.stations.station(stationId).collect { if (value == null) value = it }
    }
    val handle = nav.currentBackStackEntry?.savedStateHandle
    val pickedLat by (handle?.getStateFlow<Double?>(Routes.PICKED_LAT, null) ?: kotlinx.coroutines.flow.MutableStateFlow(null)).collectAsStateWithLifecycle()
    val pickedLng by (handle?.getStateFlow<Double?>(Routes.PICKED_LNG, null) ?: kotlinx.coroutines.flow.MutableStateFlow(null)).collectAsStateWithLifecycle()

    Scaffold(topBar = { AppTopBar(stringResource(if (stationId == null) R.string.add_station else R.string.edit_station), onBack = { nav.popBackStack() }) }) { padding ->
        val base = existing
        if (base == null) {
            LoadingBox(Modifier.padding(padding))
            return@Scaffold
        }
        var name by rememberSaveable { mutableStateOf(base.name) }
        var description by rememberSaveable { mutableStateOf(base.description) }
        var operator by rememberSaveable { mutableStateOf(base.operatorName) }
        var schedule by rememberSaveable { mutableStateOf(base.schedule) }
        var food by rememberSaveable { mutableStateOf(base.foodType) }
        var water by rememberSaveable { mutableStateOf(base.hasWater) }
        var verified by rememberSaveable { mutableStateOf(base.verified) }
        var active by rememberSaveable { mutableStateOf(base.active) }
        var lat by rememberSaveable { mutableStateOf(base.lat) }
        var lng by rememberSaveable { mutableStateOf(base.lng) }
        LaunchedEffect(pickedLat, pickedLng) {
            if (pickedLat != null && pickedLng != null) {
                lat = pickedLat!!; lng = pickedLng!!
                handle?.remove<Double>(Routes.PICKED_LAT); handle?.remove<Double>(Routes.PICKED_LNG)
            }
        }
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).imePadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedTextField(name, { name = it.take(80) }, label = { Text(stringResource(R.string.station_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(description, { description = it.take(600) }, label = { Text(stringResource(R.string.description)) }, minLines = 2, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(operator, { operator = it.take(80) }, label = { Text(stringResource(R.string.operator)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(schedule, { schedule = it.take(120) }, label = { Text(stringResource(R.string.schedule)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(food, { food = it.take(80) }, label = { Text(stringResource(R.string.food_type)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            SwitchRow(stringResource(R.string.has_water), water) { water = it }
            SwitchRow(stringResource(R.string.verified_station), verified) { verified = it }
            SwitchRow(stringResource(R.string.active), active) { active = it }
            MiniMap(lat, lng, PointKind.STATION, LocalIsDark.current)
            OutlinedButton(onClick = { nav.navigate(Routes.pickLocation(lat, lng)) }) {
                Icon(Icons.Default.Place, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.change_location))
            }
            Button(
                enabled = name.isNotBlank() && Dubai.contains(lat, lng) && !runner.busy,
                onClick = {
                    val m = me ?: return@Button
                    runner.run(R.string.saved, onSuccess = { nav.popBackStack() }) {
                        container.stations.save(
                            base.copy(
                                name = name.trim(), description = description.trim(), operatorName = operator.trim(),
                                schedule = schedule.trim(), foodType = food.trim(), hasWater = water, verified = verified,
                                active = active, lat = lat, lng = lng,
                            ),
                            m,
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.save)) }
        }
    }
}

/** The whole row toggles, so the label is a touch target and screen readers announce it with the switch. */
@Composable
fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().toggleable(checked, role = Role.Switch, onValueChange = onChange).padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f))
        Switch(checked, onCheckedChange = null)
    }
}
