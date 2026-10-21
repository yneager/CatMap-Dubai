package com.catmap.dubai.ui.rescue

import androidx.core.net.toUri
import android.Manifest
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.catmap.dubai.R
import com.catmap.dubai.data.Dubai
import com.catmap.dubai.data.loaded
import com.catmap.dubai.data.model.Cat
import com.catmap.dubai.data.model.RescueReport
import com.catmap.dubai.data.model.RescueStatus
import com.catmap.dubai.data.model.RescueType
import com.catmap.dubai.data.model.Urgency
import com.catmap.dubai.data.model.isModeratorRole
import com.catmap.dubai.data.model.isVolunteerRole
import com.catmap.dubai.data.model.labeledFrom
import com.catmap.dubai.data.userMessage
import com.catmap.dubai.ui.components.AppTopBar
import com.catmap.dubai.ui.components.ClickableCard
import com.catmap.dubai.ui.components.EmptyState
import com.catmap.dubai.ui.components.InfoRow
import com.catmap.dubai.ui.components.LabeledChips
import com.catmap.dubai.ui.components.LoadingBox
import com.catmap.dubai.ui.components.LocalBitmap
import com.catmap.dubai.ui.components.LocalContainer
import com.catmap.dubai.ui.components.LocalIsDark
import com.catmap.dubai.ui.components.LocalSnackbar
import com.catmap.dubai.ui.components.RemoteImage
import com.catmap.dubai.ui.components.ReportContentDialog
import com.catmap.dubai.ui.components.SectionHeader
import com.catmap.dubai.ui.components.StatusPill
import com.catmap.dubai.ui.components.TextInputDialog
import com.catmap.dubai.ui.components.dateTime
import com.catmap.dubai.ui.components.relativeTime
import com.catmap.dubai.ui.components.rememberRunner
import com.catmap.dubai.ui.map.MiniMap
import com.catmap.dubai.ui.map.PointKind
import com.catmap.dubai.ui.navigation.NotificationBell
import com.catmap.dubai.ui.navigation.Routes
import com.catmap.dubai.ui.sighting.newCameraUri
import com.catmap.dubai.ui.theme.statusColor
import com.catmap.dubai.util.ImageUtils
import com.catmap.dubai.util.ProcessedPhoto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class RescueTab(val label: Int) { MINE(R.string.my_reports), BOARD(R.string.volunteer_board), ALL(R.string.moderation_queue) }

@Composable
fun RescueListScreen(nav: NavHostController, moderationView: Boolean = false) {
    val container = LocalContainer.current
    val me by container.session.profile.collectAsStateWithLifecycle()
    val uid = container.session.uid ?: ""
    val tabs = buildList {
        if (!moderationView) add(RescueTab.MINE)
        if (isVolunteerRole(me?.role) && !moderationView) add(RescueTab.BOARD)
        if (isModeratorRole(me?.role)) add(RescueTab.ALL)
    }
    var tab by rememberSaveable { mutableStateOf(if (moderationView) RescueTab.ALL else RescueTab.MINE) }
    if (tab !in tabs && tabs.isNotEmpty()) tab = tabs.first()
    var statusFilter by rememberSaveable { mutableStateOf<String?>(if (moderationView) RescueStatus.SUBMITTED.key else null) }

    val flow = remember(tab, uid, statusFilter) {
        when (tab) {
            RescueTab.MINE -> container.rescues.myReports(uid)
            RescueTab.BOARD -> container.rescues.volunteerBoard()
            RescueTab.ALL -> container.rescues.allReports(statusFilter)
        }
    }
    val reports by flow.collectAsStateWithLifecycle(null)
    val shown = reports?.filter { statusFilter == null || tab != RescueTab.ALL || it.status == statusFilter }

    Scaffold(
        topBar = {
            AppTopBar(
                stringResource(if (moderationView) R.string.rescue_reports else R.string.tab_rescue),
                onBack = { nav.popBackStack() },
                actions = { if (!moderationView) NotificationBell(nav) },
            )
        },
        floatingActionButton = {
            if (!moderationView) {
                ExtendedFloatingActionButton(
                    onClick = { nav.navigate(Routes.newRescue()) },
                    icon = { Icon(com.catmap.dubai.ui.icons.catMapVector(com.catmap.dubai.ui.icons.CatMapIcons.RESCUE), null) },
                    text = { Text(stringResource(R.string.report_emergency)) },
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (tabs.size > 1) {
                PrimaryScrollableTabRow(selectedTabIndex = tabs.indexOf(tab).coerceAtLeast(0), edgePadding = 16.dp) {
                    tabs.forEach { t -> Tab(selected = tab == t, onClick = { tab = t }, text = { Text(stringResource(t.label)) }) }
                }
            }
            if (tab == RescueTab.ALL) {
                androidx.compose.foundation.lazy.LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        androidx.compose.material3.FilterChip(statusFilter == null, { statusFilter = null }, label = { Text(stringResource(R.string.all)) })
                    }
                    items(RescueStatus.entries, key = { it.key }, contentType = { "status-chip" }) { st ->
                        androidx.compose.material3.FilterChip(statusFilter == st.key, { statusFilter = st.key }, label = { Text(stringResource(st.label)) })
                    }
                }
            }
            LazyColumn(
                // Extra bottom space so the emergency button never covers the last report.
                contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = if (moderationView) 16.dp else 88.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (tab == RescueTab.MINE) {
                    item {
                        Card(colors = CardDefaults.cardColors(MaterialTheme.colorScheme.errorContainer)) {
                            Text(stringResource(R.string.emergency_note), Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                when {
                    shown == null -> item { LoadingBox(Modifier.fillMaxWidth().height(160.dp)) }
                    shown.isEmpty() -> item {
                        EmptyState(
                            com.catmap.dubai.ui.icons.catMapVector(com.catmap.dubai.ui.icons.CatMapIcons.RESCUE),
                            stringResource(if (tab == RescueTab.BOARD) R.string.no_board_reports else R.string.no_rescue_reports),
                        )
                    }
                    else -> items(shown, key = { it.id }, contentType = { "RescueScreens" }) { r -> RescueCard(r) { nav.navigate(Routes.rescue(r.id)) } }
                }
            }
        }
    }
}

@Composable
fun RescueCard(r: RescueReport, onClick: () -> Unit) {
    val type = labeledFrom(r.type, RescueType.OTHER)
    val status = labeledFrom(r.status, RescueStatus.SUBMITTED)
    val urgency = labeledFrom(r.urgency, Urgency.MEDIUM)
    ClickableCard(onClick) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (r.photoIds.isNotEmpty()) {
                RemoteImage(r.photoIds.first(), Modifier.size(64.dp).clip(RoundedCornerShape(10.dp)))
            } else {
                Box(Modifier.size(64.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.errorContainer), contentAlignment = Alignment.Center) {
                    Icon(com.catmap.dubai.ui.icons.catMapVector(com.catmap.dubai.ui.icons.CatMapIcons.RESCUE), null, tint = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(type.label), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(Dubai.nameOf(r.neighborhood) + " · " + relativeTime(r.createdAt), style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatusPill(stringResource(status.label), statusColor(status.color))
                    StatusPill(stringResource(urgency.label), statusColor(urgency.color))
                }
            }
        }
    }
}

// ---------------- New report ----------------

@Composable
fun NewRescueScreen(nav: NavHostController, catId: String?) {
    val container = LocalContainer.current
    val context = LocalContext.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val me by container.session.profile.collectAsStateWithLifecycle()
    val cat by produceState<Cat?>(null, catId) { if (catId != null) value = container.cats.cat(catId) }

    var type by rememberSaveable { mutableStateOf(RescueType.INJURED) }
    var urgency by rememberSaveable { mutableStateOf(Urgency.HIGH) }
    var description by rememberSaveable { mutableStateOf("") }
    var landmark by rememberSaveable { mutableStateOf("") }
    val photos = remember { mutableStateListOf<ProcessedPhoto>() }
    var lat by rememberSaveable { mutableStateOf<Double?>(null) }
    var lng by rememberSaveable { mutableStateOf<Double?>(null) }
    var locating by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var cameraUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    val sent = stringResource(R.string.rescue_sent)

    fun addUris(uris: List<Uri>) = scope.launch {
        uris.take(4 - photos.size).forEach { uri ->
            runCatching { withContext(Dispatchers.IO) { ImageUtils.process(context, uri) } }.onSuccess { photos += it }
        }
    }
    fun locate() = scope.launch {
        locating = true
        container.location.current()?.let { lat = it.lat; lng = it.lng }
        locating = false
    }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) cameraUri?.let { addUris(listOf(it)) } }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(4)) { addUris(it) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { locate() }
    LaunchedEffect(Unit) {
        if (lat == null) permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }

    val handle = nav.currentBackStackEntry?.savedStateHandle
    val pickedLat by (handle?.getStateFlow<Double?>(Routes.PICKED_LAT, null) ?: kotlinx.coroutines.flow.MutableStateFlow(null)).collectAsStateWithLifecycle()
    val pickedLng by (handle?.getStateFlow<Double?>(Routes.PICKED_LNG, null) ?: kotlinx.coroutines.flow.MutableStateFlow(null)).collectAsStateWithLifecycle()
    LaunchedEffect(pickedLat, pickedLng) {
        if (pickedLat != null && pickedLng != null) {
            lat = pickedLat; lng = pickedLng
            handle?.remove<Double>(Routes.PICKED_LAT); handle?.remove<Double>(Routes.PICKED_LNG)
        }
    }
    val inside = lat != null && lng != null && Dubai.contains(lat!!, lng!!)

    Scaffold(topBar = { AppTopBar(stringResource(R.string.report_emergency), onBack = { nav.popBackStack() }) }) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).imePadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            cat?.let { Text(stringResource(R.string.rescue_for_cat, it.name), style = MaterialTheme.typography.titleSmall) }
            SectionHeader(stringResource(R.string.what_happened))
            LabeledChips(RescueType.entries, setOf(type), { type = it })
            SectionHeader(stringResource(R.string.urgency))
            LabeledChips(Urgency.entries, setOf(urgency), { urgency = it })
            OutlinedTextField(
                description, { description = it.take(1000) },
                label = { Text(stringResource(R.string.describe_situation)) }, minLines = 3, modifier = Modifier.fillMaxWidth(),
            )
            SectionHeader(stringResource(R.string.photos))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { newCameraUri(context).also { cameraUri = it; camera.launch(it) } }, enabled = photos.size < 4, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.CameraAlt, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.take_photo))
                }
                OutlinedButton(onClick = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, enabled = photos.size < 4, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.PhotoLibrary, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.from_gallery))
                }
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(photos.toList(), key = { System.identityHashCode(it) }, contentType = { "local-photo" }) { p ->
                    Box {
                        LocalBitmap(p.analysis, Modifier.size(100.dp).clip(RoundedCornerShape(10.dp)))
                        FilledIconButton(onClick = { photos.remove(p) }, modifier = Modifier.align(Alignment.TopEnd).size(26.dp)) {
                            Icon(Icons.Default.Close, stringResource(R.string.remove), Modifier.size(14.dp))
                        }
                    }
                }
            }
            SectionHeader(stringResource(R.string.approximate_location))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { locate() }, enabled = !locating, modifier = Modifier.weight(1f)) {
                    if (locating) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Icon(Icons.Default.MyLocation, null)
                    Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.use_gps))
                }
                OutlinedButton(onClick = { nav.navigate(Routes.pickLocation(lat, lng)) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Place, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.pick_on_map))
                }
            }
            if (lat != null && lng != null) {
                MiniMap(lat!!, lng!!, PointKind.RESCUE, LocalIsDark.current)
                if (!inside) Text(stringResource(R.string.outside_dubai_hint), color = MaterialTheme.colorScheme.error)
                else Text(stringResource(R.string.near_x, Dubai.nearest(lat!!, lng!!).displayName()))
            }
            OutlinedTextField(
                landmark, { landmark = it.take(200) },
                label = { Text(stringResource(R.string.landmark)) },
                placeholder = { Text(stringResource(R.string.landmark_hint)) }, modifier = Modifier.fillMaxWidth(),
            )
            Text(stringResource(R.string.rescue_visibility_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                enabled = !busy && description.isNotBlank() && inside && me != null,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                onClick = {
                    scope.launch {
                        busy = true
                        error = null
                        try {
                            val profile = me!!
                            val ids = photos.map { container.images.upload(profile.uid, it) }
                            val id = container.rescues.submit(
                                RescueReport(
                                    type = type.key, urgency = urgency.key, description = description.trim(),
                                    landmark = landmark.trim(), photoIds = ids, lat = lat!!, lng = lng!!,
                                    catId = catId ?: "", catName = cat?.name ?: "",
                                ),
                                profile,
                            )
                            container.scope.launch { snackbar.showSnackbar(sent) }
                            nav.navigate(Routes.rescue(id)) { popUpTo(Routes.NEW_RESCUE) { inclusive = true } }
                        } catch (e: Exception) {
                            error = e.userMessage(context)
                        } finally {
                            busy = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text(stringResource(R.string.send_report))
            }
        }
    }
}

// ---------------- Report detail & progress ----------------

@Composable
fun RescueDetailScreen(nav: NavHostController, reportId: String) {
    val container = LocalContainer.current
    val context = LocalContext.current
    val runner = rememberRunner()
    val reportState by remember(reportId) { container.rescues.report(reportId).loaded() }.collectAsStateWithLifecycle(null)
    val report = reportState?.value
    val me by container.session.profile.collectAsStateWithLifecycle()
    var dialog by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            AppTopBar(stringResource(R.string.rescue_report), onBack = { nav.popBackStack() }) {
                if (report != null && report.reporterId != me?.uid) {
                    IconButton(onClick = { dialog = "report" }) { Icon(Icons.Default.Flag, stringResource(R.string.report_content)) }
                }
            }
        },
    ) { padding ->
        val r = report
        if (reportState != null && r == null) {
            EmptyState(com.catmap.dubai.ui.icons.catMapVector(com.catmap.dubai.ui.icons.CatMapIcons.RESCUE), stringResource(R.string.rescue_not_found), Modifier.padding(padding))
            return@Scaffold
        }
        if (r == null || me == null) {
            LoadingBox(Modifier.padding(padding))
            return@Scaffold
        }
        val m = me!!
        val status = labeledFrom(r.status, RescueStatus.SUBMITTED)
        val isMod = isModeratorRole(m.role)
        val isVolunteer = isVolunteerRole(m.role)
        val isReporter = r.reporterId == m.uid
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(stringResource(labeledFrom(r.type, RescueType.OTHER).label), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StatusPill(stringResource(status.label), statusColor(status.color))
                val urgency = labeledFrom(r.urgency, Urgency.MEDIUM)
                StatusPill(stringResource(urgency.label), statusColor(urgency.color))
            }
            StatusStepper(status)
            if (r.photoIds.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(r.photoIds, key = { it }, contentType = { "photo" }) { id ->
                        RemoteImage(id, Modifier.size(150.dp).clip(RoundedCornerShape(12.dp)).clickable { nav.navigate(Routes.photo(id)) })
                    }
                }
            }
            Text(r.description)
            InfoRow(stringResource(R.string.reported), dateTime(r.createdAt))
            InfoRow(stringResource(R.string.reported_by), "@" + r.reporterName)
            InfoRow(stringResource(R.string.neighborhood), Dubai.nameOf(r.neighborhood))
            InfoRow(stringResource(R.string.landmark), r.landmark)
            if (r.assignedName.isNotBlank()) InfoRow(stringResource(R.string.handled_by), "@" + r.assignedName)
            if (r.catId.isNotBlank()) {
                OutlinedButton(onClick = { nav.navigate(Routes.cat(r.catId)) }) { Text(stringResource(R.string.view_cat, r.catName)) }
            }
            MiniMap(r.lat, r.lng, PointKind.RESCUE, LocalIsDark.current)
            if (isVolunteer) {
                OutlinedButton(onClick = {
                    val uri = "geo:${r.lat},${r.lng}?q=${r.lat},${r.lng}".toUri()
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                }) {
                    Icon(Icons.Default.Directions, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.directions))
                }
            }

            // ---- Actions ----
            when {
                isMod && r.status == RescueStatus.SUBMITTED.key -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { dialog = RescueStatus.APPROVED.key }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.approve)) }
                    OutlinedButton(onClick = { dialog = RescueStatus.REJECTED.key }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.reject)) }
                }
                isVolunteer && r.status == RescueStatus.APPROVED.key -> Button(onClick = { dialog = RescueStatus.IN_PROGRESS.key }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.i_will_handle))
                }
                isVolunteer && r.status == RescueStatus.IN_PROGRESS.key -> Button(onClick = { dialog = RescueStatus.RESOLVED.key }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.mark_resolved))
                }
            }
            if (isVolunteer || isReporter) {
                OutlinedButton(onClick = { dialog = "note" }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.add_update)) }
            }

            SectionHeader(stringResource(R.string.progress_updates))
            r.updates.sortedByDescending { it.at }.forEach { u ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Box(Modifier.padding(top = 6.dp).size(10.dp).clip(CircleShape).background(statusColor(labeledFrom(u.status, RescueStatus.SUBMITTED).color)))
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(stringResource(labeledFrom(u.status, RescueStatus.SUBMITTED).label) + " · @" + u.byName, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                        Text(dateTime(u.at), style = MaterialTheme.typography.labelSmall)
                        if (u.note.isNotBlank()) Text(u.note, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        val done = stringResource(R.string.updated)
        when (val d = dialog) {
            null -> Unit
            "report" -> ReportContentDialog("rescue", r.id, Dubai.nameOf(r.neighborhood)) { dialog = null }
            "note" -> TextInputDialog(
                stringResource(R.string.add_update), stringResource(R.string.note), stringResource(R.string.add), required = true,
                onConfirm = { note -> runner.run(done) { container.rescues.addNote(r, note, m) } },
                onDismiss = { dialog = null },
            )
            else -> TextInputDialog(
                stringResource(labeledFrom(d, RescueStatus.SUBMITTED).label),
                stringResource(R.string.note_optional), stringResource(R.string.confirm),
                onConfirm = { note ->
                    runner.run(done) { container.rescues.changeStatus(r, d, note, m, assignToMe = d == RescueStatus.IN_PROGRESS.key) }
                },
                onDismiss = { dialog = null },
            )
        }
    }
}

@Composable
private fun StatusStepper(current: RescueStatus) {
    if (current == RescueStatus.REJECTED) return
    val steps = listOf(RescueStatus.SUBMITTED, RescueStatus.APPROVED, RescueStatus.IN_PROGRESS, RescueStatus.RESOLVED)
    val index = steps.indexOf(current)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        steps.forEachIndexed { i, step ->
            val reached = i <= index
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.size(14.dp).clip(CircleShape)
                        .background(if (reached) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                )
                Text(
                    stringResource(step.label), style = MaterialTheme.typography.labelSmall,
                    color = if (reached) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}
