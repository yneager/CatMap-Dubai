package com.catmap.dubai.ui.sighting

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.catmap.dubai.data.loaded
import com.catmap.dubai.data.model.BehaviorTag
import com.catmap.dubai.data.model.CoatColor
import com.catmap.dubai.data.model.CoatPattern
import com.catmap.dubai.data.model.EarTip
import com.catmap.dubai.data.model.HealthTag
import com.catmap.dubai.data.model.PrivateLocation
import com.catmap.dubai.data.model.Sighting
import com.catmap.dubai.data.model.SubmissionStatus
import com.catmap.dubai.data.model.isModeratorRole
import com.catmap.dubai.data.model.isVolunteerRole
import com.catmap.dubai.data.model.labeledFrom
import com.catmap.dubai.ui.components.AppTopBar
import com.catmap.dubai.ui.components.ConfirmDialog
import com.catmap.dubai.ui.components.EmptyState
import com.catmap.dubai.ui.components.InfoRow
import com.catmap.dubai.ui.components.LabeledChips
import com.catmap.dubai.ui.components.LoadingBox
import com.catmap.dubai.ui.components.LocalContainer
import com.catmap.dubai.ui.components.LocalIsDark
import com.catmap.dubai.ui.components.Pill
import com.catmap.dubai.ui.components.RemoteImage
import com.catmap.dubai.ui.components.ReportContentDialog
import com.catmap.dubai.ui.components.SectionHeader
import com.catmap.dubai.ui.components.StatusPill
import com.catmap.dubai.ui.components.dateTime
import com.catmap.dubai.ui.components.rememberRunner
import com.catmap.dubai.ui.map.MiniMap
import com.catmap.dubai.ui.map.PointKind
import com.catmap.dubai.ui.navigation.Routes
import com.catmap.dubai.ui.theme.statusColor

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SightingDetailScreen(nav: NavHostController, sightingId: String) {
    val container = LocalContainer.current
    val runner = rememberRunner()
    val context = LocalContext.current
    val sightingState by remember(sightingId) { container.cats.sightingFlow(sightingId).loaded() }.collectAsStateWithLifecycle(null)
    val sighting = sightingState?.value
    val me by container.session.profile.collectAsStateWithLifecycle()
    var dialog by remember { mutableStateOf<String?>(null) }

    val s = sighting
    val isOwner = s != null && s.userId == me?.uid
    val isMod = isModeratorRole(me?.role)
    // Exact coordinates are readable only by the author and by volunteers (enforced by the rules).
    val exact by produceState<PrivateLocation?>(null, s?.id, me?.role) {
        if (s != null && (isOwner || isVolunteerRole(me?.role))) value = container.cats.privateLocation(s.id)
    }

    Scaffold(
        topBar = {
            AppTopBar(stringResource(R.string.sighting), onBack = { nav.popBackStack() }) {
                if (s != null) {
                    if (isOwner) IconButton(onClick = { nav.navigate(Routes.sightingEdit(s.id)) }) { Icon(Icons.Default.Edit, stringResource(R.string.edit)) }
                    if (isOwner || isMod) IconButton(onClick = { dialog = "delete" }) { Icon(Icons.Default.Delete, stringResource(R.string.delete)) }
                    if (!isOwner) IconButton(onClick = { dialog = "report" }) { Icon(Icons.Default.Flag, stringResource(R.string.report_content)) }
                }
            }
        },
    ) { padding ->
        if (s == null) {
            if (sightingState == null) LoadingBox(Modifier.padding(padding)) else EmptyState(com.catmap.dubai.ui.icons.catMapVector(com.catmap.dubai.ui.icons.CatMapIcons.PAW), stringResource(R.string.sighting_deleted), Modifier.padding(padding))
            return@Scaffold
        }
        val status = labeledFrom(s.status, SubmissionStatus.PENDING)
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (s.photoIds.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(s.photoIds, key = { it }, contentType = { "photo" }) { id ->
                        RemoteImage(id, Modifier.size(200.dp).clip(RoundedCornerShape(14.dp)).clickable { nav.navigate(Routes.photo(id, s.catId)) })
                    }
                }
            }
            Card(onClick = { nav.navigate(Routes.cat(s.catId)) }, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(com.catmap.dubai.ui.icons.catMapVector(com.catmap.dubai.ui.icons.CatMapIcons.PAW), null)
                    Spacer(Modifier.width(10.dp))
                    Text(s.catName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text(stringResource(R.string.view_profile), color = MaterialTheme.colorScheme.primary)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusPill(stringResource(status.label), statusColor(status.color))
                if (s.newCat) Pill(stringResource(R.string.first_sighting))
            }
            InfoRow(stringResource(R.string.seen_at), dateTime(s.seenAt))
            InfoRow(stringResource(R.string.reported_by), "@" + s.username)
            InfoRow(stringResource(R.string.neighborhood), Dubai.nameOf(s.neighborhood))
            InfoRow(stringResource(R.string.location_source), stringResource(if (s.locationSource == "gps") R.string.source_gps else R.string.source_manual))
            InfoRow(stringResource(R.string.coat_color), stringResource(labeledFrom(s.coatColor, CoatColor.UNKNOWN).label))
            InfoRow(stringResource(R.string.pattern), stringResource(labeledFrom(s.pattern, CoatPattern.UNKNOWN).label))
            InfoRow(stringResource(R.string.ear_tipped), stringResource(labeledFrom(s.earTipped, EarTip.UNSURE).label))
            if (s.matchScore > 0) InfoRow(stringResource(R.string.ai_match_score), stringResource(R.string.percent, (s.matchScore * 100).toInt()))
            if (s.needsMedical || s.possiblyNotNeutered) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (s.needsMedical) StatusPill(stringResource(R.string.needs_medical), statusColor(0xFFC62828))
                    if (s.possiblyNotNeutered) StatusPill(stringResource(R.string.obs_not_neutered), statusColor(0xFFEF6C00))
                }
            }
            if (s.healthTags.isNotEmpty()) {
                SectionHeader(stringResource(R.string.health_observations))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    s.healthTags.forEach { Pill(stringResource(labeledFrom(it, HealthTag.GOOD_WEIGHT).label)) }
                }
            }
            if (s.behaviorTags.isNotEmpty()) {
                SectionHeader(stringResource(R.string.behavior_observations))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    s.behaviorTags.forEach { Pill(stringResource(labeledFrom(it, BehaviorTag.FRIENDLY).label)) }
                }
            }
            if (s.notes.isNotBlank()) {
                SectionHeader(stringResource(R.string.notes))
                Text(s.notes)
            }
            SectionHeader(stringResource(R.string.location))
            MiniMap(s.lat, s.lng, PointKind.CAT, LocalIsDark.current, labeledFrom(s.coatColor, CoatColor.UNKNOWN))
            Text(stringResource(R.string.location_is_approximate), style = MaterialTheme.typography.labelSmall)
            exact?.let {
                Card(colors = CardDefaults.cardColors(MaterialTheme.colorScheme.tertiaryContainer)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Place, null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.exact_location, "%.5f, %.5f".format(it.lat, it.lng)), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (isMod && s.status == SubmissionStatus.PENDING.key) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 12.dp)) {
                    Button(onClick = { me?.let { m -> runner.run { container.cats.reviewSighting(s, true, m) } } }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.approve)) }
                    OutlinedButton(onClick = { me?.let { m -> runner.run { container.cats.reviewSighting(s, false, m) } } }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.reject)) }
                }
            }
        }
    }

    val current = s ?: return
    when (dialog) {
        "delete" -> ConfirmDialog(
            stringResource(R.string.delete_sighting), stringResource(R.string.delete_sighting_body), stringResource(R.string.delete),
            onConfirm = {
                runner.run(R.string.deleted, onSuccess = { nav.popBackStack() }) {
                    container.cats.deleteSighting(current)
                    if (isMod && !isOwner) me?.let { container.moderation.log(it, "sighting_deleted", "sighting", current.id, current.catName, "") }
                }
            },
            onDismiss = { dialog = null },
        )
        "report" -> ReportContentDialog("sighting", current.id, current.catName) { dialog = null }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SightingEditScreen(nav: NavHostController, sightingId: String) {
    val container = LocalContainer.current
    val runner = rememberRunner()
    val context = LocalContext.current
    val sighting by produceState<Sighting?>(null, sightingId) {
        container.cats.sightingFlow(sightingId).collect { if (value == null) value = it }
    }
    val handle = nav.currentBackStackEntry?.savedStateHandle
    val pickedLat by (handle?.getStateFlow<Double?>(Routes.PICKED_LAT, null) ?: kotlinx.coroutines.flow.MutableStateFlow(null)).collectAsStateWithLifecycle()
    val pickedLng by (handle?.getStateFlow<Double?>(Routes.PICKED_LNG, null) ?: kotlinx.coroutines.flow.MutableStateFlow(null)).collectAsStateWithLifecycle()

    Scaffold(topBar = { AppTopBar(stringResource(R.string.edit_sighting), onBack = { nav.popBackStack() }) }) { padding ->
        val s = sighting
        if (s == null) {
            LoadingBox(Modifier.padding(padding))
            return@Scaffold
        }
        var notes by rememberSaveable { mutableStateOf(s.notes) }
        var health by remember { mutableStateOf(s.healthTags.map { labeledFrom(it, HealthTag.GOOD_WEIGHT) }.toSet()) }
        var behavior by remember { mutableStateOf(s.behaviorTags.map { labeledFrom(it, BehaviorTag.FRIENDLY) }.toSet()) }
        var earTip by remember { mutableStateOf(labeledFrom(s.earTipped, EarTip.UNSURE)) }
        var notNeutered by remember { mutableStateOf(s.possiblyNotNeutered) }
        var needsMedical by remember { mutableStateOf(s.needsMedical) }
        var newLocation by remember { mutableStateOf<Pair<Double, Double>?>(null) }
        LaunchedEffect(pickedLat, pickedLng) {
            val la = pickedLat
            val ln = pickedLng
            if (la != null && ln != null) {
                newLocation = la to ln
                handle?.remove<Double>(Routes.PICKED_LAT)
                handle?.remove<Double>(Routes.PICKED_LNG)
            }
        }

        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).imePadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SectionHeader(stringResource(R.string.health_observations))
            LabeledChips(HealthTag.entries, health, { t -> health = if (t in health) health - t else health + t })
            SectionHeader(stringResource(R.string.behavior_observations))
            LabeledChips(BehaviorTag.entries, behavior, { t -> behavior = if (t in behavior) behavior - t else behavior + t })
            SectionHeader(stringResource(R.string.tnr_section))
            Row {
                EarTip.entries.forEach { option ->
                    Row(
                        Modifier.selectable(earTip == option, role = Role.RadioButton) { earTip = option }.padding(end = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(earTip == option, null)
                        Text(stringResource(option.label), Modifier.padding(start = 4.dp))
                    }
                }
            }
            CheckRow(stringResource(R.string.report_not_neutered), notNeutered) { notNeutered = it }
            CheckRow(stringResource(R.string.flag_needs_medical), needsMedical) { needsMedical = it }
            OutlinedTextField(notes, { notes = it.take(1000) }, label = { Text(stringResource(R.string.notes)) }, minLines = 3, modifier = Modifier.fillMaxWidth())
            SectionHeader(stringResource(R.string.location))
            val (la, ln) = newLocation ?: (s.lat to s.lng)
            MiniMap(la, ln, PointKind.CAT, LocalIsDark.current)
            OutlinedButton(onClick = { nav.navigate(Routes.pickLocation(la, ln)) }) {
                Icon(Icons.Default.Place, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.change_location))
            }
            Button(
                enabled = !runner.busy,
                onClick = {
                    runner.run(R.string.saved, onSuccess = { nav.popBackStack() }) {
                        container.cats.updateSighting(
                            s,
                            mapOf(
                                "notes" to notes.trim(),
                                "healthTags" to health.map { it.key },
                                "behaviorTags" to behavior.map { it.key },
                                "earTipped" to earTip.key,
                                "possiblyNotNeutered" to notNeutered,
                                "needsMedical" to needsMedical,
                            ),
                        )
                        newLocation?.let { (lat, lng) ->
                            val settings = container.settings.current()
                            container.cats.updateSightingLocation(s, lat, lng, settings.privacyLevel, settings.shareExactWithVolunteers)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.save)) }
        }
    }
}
