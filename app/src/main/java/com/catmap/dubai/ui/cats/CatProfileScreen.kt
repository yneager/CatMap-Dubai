package com.catmap.dubai.ui.cats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.outlined.LocalHospital
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import com.catmap.dubai.ai.Breeds
import com.catmap.dubai.data.Dubai
import com.catmap.dubai.data.loaded
import com.catmap.dubai.data.model.BehaviorTag
import com.catmap.dubai.data.model.Cat
import com.catmap.dubai.data.model.CatSex
import com.catmap.dubai.data.model.CatSize
import com.catmap.dubai.data.model.CoatColor
import com.catmap.dubai.data.model.CoatPattern
import com.catmap.dubai.data.model.EarTip
import com.catmap.dubai.data.model.HealthStatus
import com.catmap.dubai.data.model.HealthTag
import com.catmap.dubai.data.model.MedicalType
import com.catmap.dubai.data.model.Sighting
import com.catmap.dubai.data.model.TnrStatus
import com.catmap.dubai.data.model.isModeratorRole
import com.catmap.dubai.data.model.isVolunteerRole
import com.catmap.dubai.data.model.labeledFrom
import com.catmap.dubai.ui.components.AppTopBar
import com.catmap.dubai.ui.components.CatThumb
import com.catmap.dubai.ui.components.CoatSwatch
import com.catmap.dubai.ui.components.EmptyState
import com.catmap.dubai.ui.components.InfoRow
import com.catmap.dubai.ui.components.LabeledDropdown
import com.catmap.dubai.ui.components.LoadingBox
import com.catmap.dubai.ui.components.LocalContainer
import com.catmap.dubai.ui.components.LocalIsDark
import com.catmap.dubai.ui.components.Pill
import com.catmap.dubai.ui.components.RemoteImage
import com.catmap.dubai.ui.components.ReportContentDialog
import com.catmap.dubai.ui.components.SectionHeader
import com.catmap.dubai.ui.components.StatTile
import com.catmap.dubai.ui.components.StatusPill
import com.catmap.dubai.ui.components.TextInputDialog
import com.catmap.dubai.ui.components.dateOnly
import com.catmap.dubai.ui.components.dateTime
import com.catmap.dubai.ui.components.rememberRunner
import com.catmap.dubai.ui.components.relativeTime
import com.catmap.dubai.ui.map.MiniMap
import com.catmap.dubai.ui.map.PointKind
import com.catmap.dubai.ui.navigation.Routes
import com.catmap.dubai.ui.theme.statusColor
import com.catmap.dubai.util.Share
import kotlinx.coroutines.launch

private enum class CatTab(val label: Int) { OVERVIEW(R.string.tab_overview), TIMELINE(R.string.tab_timeline), PHOTOS(R.string.tab_photos), HEALTH(R.string.tab_health) }

@Composable
fun CatProfileScreen(nav: NavHostController, catId: String) {
    val container = LocalContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val runner = rememberRunner()
    val catState by remember(catId) { container.cats.catFlow(catId).loaded() }.collectAsStateWithLifecycle(null)
    val cat = catState?.value
    val sightings by remember(catId) { container.cats.sightingsForCat(catId) }.collectAsStateWithLifecycle(emptyList())
    val medical by remember(catId) { container.cats.medicalRecords(catId) }.collectAsStateWithLifecycle(emptyList())
    val me by container.session.profile.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(CatTab.OVERVIEW) }
    var menu by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<String?>(null) }

    val current = cat
    val isOwner = current != null && me?.uid == current.createdBy
    val isMod = isModeratorRole(me?.role)
    val isVolunteer = isVolunteerRole(me?.role)
    val following = me?.followedCats?.contains(catId) == true

    Scaffold(
        topBar = {
            AppTopBar(current?.name ?: "", onBack = { nav.popBackStack() }) {
                if (current != null) {
                    IconButton(onClick = { scope.launch { Share.shareCat(context, container, current) } }) {
                        Icon(Icons.Default.Share, stringResource(R.string.share_cat))
                    }
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, stringResource(R.string.more)) }
                    DropdownMenu(menu, { menu = false }) {
                        if (isOwner || isMod) {
                            DropdownMenuItem({ Text(stringResource(R.string.edit_profile_cat)) }, { menu = false; nav.navigate(Routes.catEdit(catId)) })
                        }
                        DropdownMenuItem({ Text(stringResource(R.string.add_alias)) }, { menu = false; dialog = "alias" })
                        DropdownMenuItem({ Text(stringResource(R.string.suggest_merge)) }, { menu = false; nav.navigate(Routes.catMerge(catId)) })
                        DropdownMenuItem({ Text(stringResource(R.string.report_content)) }, { menu = false; dialog = "report" })
                        if (isMod) {
                            DropdownMenuItem(
                                { Text(stringResource(if (current.hidden) R.string.restore_profile else R.string.hide_profile)) },
                                { menu = false; dialog = "hide" },
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        if (current == null) {
            if (catState == null) LoadingBox(Modifier.fillMaxSize().padding(padding))
            else EmptyState(Icons.Outlined.PhotoLibrary, stringResource(R.string.cat_not_found), Modifier.padding(padding))
            return@Scaffold
        }
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 24.dp)) {
            item { PhotoHeader(current) { nav.navigate(Routes.photo(it, catId)) } }
            if (current.mergedInto.isNotBlank()) {
                item {
                    Card(Modifier.fillMaxWidth().padding(16.dp), colors = CardDefaults.cardColors(MaterialTheme.colorScheme.tertiaryContainer)) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.merged_banner), Modifier.weight(1f))
                            TextButton(onClick = { nav.navigate(Routes.cat(current.mergedInto)) { popUpTo(Routes.CAT) { inclusive = true } } }) {
                                Text(stringResource(R.string.open))
                            }
                        }
                    }
                }
            }
            item { Header(current, following, onFollow = {
                val uid = me?.uid ?: return@Header
                runner.run { container.users.setFollowing(uid, catId, !following) }
            }, onSighting = { nav.navigate(Routes.newSighting(catId)) }, onRescue = { nav.navigate(Routes.newRescue(catId)) }) }
            item {
                PrimaryTabRow(selectedTabIndex = tab.ordinal) {
                    CatTab.entries.forEach { t ->
                        Tab(selected = tab == t, onClick = { tab = t }, text = { Text(stringResource(t.label), maxLines = 1) })
                    }
                }
            }
            when (tab) {
                CatTab.OVERVIEW -> item { Overview(current) }
                CatTab.TIMELINE -> {
                    if (sightings.isEmpty()) item { EmptyState(Icons.Outlined.PhotoLibrary, stringResource(R.string.no_sightings_yet)) }
                    items(sightings, key = { it.id }, contentType = { "CatProfileScreen" }) { s -> TimelineItem(s, onClick = { nav.navigate(Routes.sighting(s.id)) }) }
                }
                CatTab.PHOTOS -> {
                    if (current.photoIds.isEmpty()) item { EmptyState(Icons.Outlined.PhotoLibrary, stringResource(R.string.no_photos)) }
                    items(current.photoIds.reversed().chunked(3), key = { it.first() }, contentType = { "photo-row" }) { row ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            row.forEach { id ->
                                RemoteImage(
                                    id,
                                    Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(8.dp)).clickable { nav.navigate(Routes.photo(id, catId)) },
                                    contentDescription = stringResource(R.string.photo_of, current.name),
                                )
                            }
                            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
                CatTab.HEALTH -> item {
                    HealthSection(
                        cat = current,
                        sightings = sightings,
                        medical = medical,
                        canVerify = isVolunteer,
                        onAddRecord = { dialog = "medical" },
                        onFlag = { nav.navigate(Routes.newRescue(catId)) },
                        onObserve = { nav.navigate(Routes.newSighting(catId)) },
                    )
                }
            }
        }
    }

    val c = current ?: return
    when (dialog) {
        "alias" -> TextInputDialog(
            stringResource(R.string.add_alias), stringResource(R.string.alias), stringResource(R.string.add),
            singleLine = true, required = true,
            onConfirm = { alias -> runner.run { container.cats.addAlias(c.id, alias.take(40)) } },
            onDismiss = { dialog = null },
        )
        "report" -> ReportContentDialog("cat", c.id, c.name) { dialog = null }
        "hide" -> TextInputDialog(
            stringResource(if (c.hidden) R.string.restore_profile else R.string.hide_profile),
            stringResource(R.string.moderation_note), stringResource(R.string.confirm),
            onConfirm = { note -> me?.let { m -> runner.run { container.cats.setCatHidden(c, !c.hidden, m, note) } } },
            onDismiss = { dialog = null },
        )
        "medical" -> MedicalRecordDialog(onDismiss = { dialog = null }) { type, note ->
            me?.let { m -> runner.run(R.string.saved) { container.cats.addMedicalRecord(c, type, note, m) } }
        }
    }
}

@Composable
private fun PhotoHeader(cat: Cat, onOpen: (String) -> Unit) {
    val coat = labeledFrom(cat.coatColor, CoatColor.UNKNOWN)
    val photos = (listOf(cat.coverPhotoId) + cat.photoIds.reversed()).filter { it.isNotBlank() }.distinct()
    if (photos.isEmpty()) {
        CatThumb("", coat, Modifier.fillMaxWidth().height(200.dp))
        return
    }
    LazyRow(contentPadding = PaddingValues(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(photos, key = { it }, contentType = { "photo" }) { id ->
            RemoteImage(
                id,
                Modifier.height(220.dp).aspectRatio(if (photos.size == 1) 1.6f else 0.85f).clip(RoundedCornerShape(16.dp)).clickable { onOpen(id) },
                thumb = false,
                contentDescription = stringResource(R.string.photo_of, cat.name),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Header(cat: Cat, following: Boolean, onFollow: () -> Unit, onSighting: () -> Unit, onRescue: () -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(cat.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        if (cat.aliases.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.also_known_as), style = MaterialTheme.typography.bodySmall, modifier = Modifier.align(Alignment.CenterVertically))
                cat.aliases.forEach { Pill(it) }
            }
        }
        if (cat.hidden) StatusPill(stringResource(R.string.hidden_by_moderators), MaterialTheme.colorScheme.error)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (following) {
                OutlinedButton(onClick = onFollow) {
                    Icon(Icons.Default.Star, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.following))
                }
            } else {
                Button(onClick = onFollow) {
                    Icon(Icons.Default.StarBorder, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.follow))
                }
            }
            FilledTonalButton(onClick = onSighting) {
                Icon(Icons.Default.AddAPhoto, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.i_saw_this_cat))
            }
        }
        OutlinedButton(onClick = onRescue, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.LocalHospital, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.request_help))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
            StatTile(cat.sightingCount.toString(), stringResource(R.string.total_sightings), Modifier.weight(1f))
            StatTile(dateOnly(cat.firstSeen), stringResource(R.string.first_seen), Modifier.weight(1f))
            StatTile(relativeTime(cat.lastSeen), stringResource(R.string.last_seen), Modifier.weight(1f))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Overview(cat: Cat) {
    val coat = labeledFrom(cat.coatColor, CoatColor.UNKNOWN)
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SectionHeader(stringResource(R.string.appearance))
        Row(verticalAlignment = Alignment.CenterVertically) {
            CoatSwatch(coat, 22.dp)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(coat.label), style = MaterialTheme.typography.bodyLarge)
        }
        InfoRow(stringResource(R.string.pattern), stringResource(labeledFrom(cat.pattern, CoatPattern.UNKNOWN).label))
        InfoRow(stringResource(R.string.size), stringResource(labeledFrom(cat.size, CatSize.UNKNOWN).label))
        InfoRow(stringResource(R.string.sex), stringResource(labeledFrom(cat.sex, CatSex.UNKNOWN).label))
        InfoRow(stringResource(R.string.distinctive_marks), cat.distinctiveMarks)
        if (cat.description.isNotBlank()) {
            SectionHeader(stringResource(R.string.description))
            Text(cat.description)
        }
        if (cat.breedHints.isNotEmpty()) {
            SectionHeader(stringResource(R.string.ai_breed_traits))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                cat.breedHints.forEach { AssistChip(onClick = {}, label = { Text(stringResource(Breeds.label(it))) }) }
            }
        }
        SectionHeader(stringResource(R.string.where_seen))
        Text(
            stringResource(R.string.last_area, Dubai.nameOf(cat.neighborhood)),
            style = MaterialTheme.typography.bodyMedium,
        )
        MiniMap(cat.lat, cat.lng, PointKind.CAT, LocalIsDark.current, coat)
        Text(stringResource(R.string.location_is_approximate), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (cat.neighborhoods.size > 1) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                cat.neighborhoods.forEach { Pill(Dubai.nameOf(it)) }
            }
        }
        SectionHeader(stringResource(R.string.community))
        InfoRow(stringResource(R.string.registered_by), "@" + cat.createdByName)
        InfoRow(stringResource(R.string.contributors), cat.contributors.size.toString())
        InfoRow(stringResource(R.string.followers), cat.followers.size.toString())
        InfoRow(stringResource(R.string.registered_on), dateOnly(cat.createdAt))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TimelineItem(s: Sighting, onClick: () -> Unit) {
    val dot = MaterialTheme.colorScheme.primary
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(20.dp)) {
            Box(Modifier.size(12.dp).clip(CircleShape).background(dot))
            Box(Modifier.width(2.dp).height(90.dp).background(dot.copy(alpha = 0.3f)))
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(dateTime(s.seenAt), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Text(
                stringResource(R.string.seen_by_in, s.username, Dubai.nameOf(s.neighborhood)),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (s.notes.isNotBlank()) Text(s.notes, style = MaterialTheme.typography.bodyMedium, maxLines = 3)
            val tags = s.healthTags.map { labeledFrom(it, HealthTag.GOOD_WEIGHT).label } + s.behaviorTags.map { labeledFrom(it, BehaviorTag.FRIENDLY).label }
            if (tags.isNotEmpty() || s.needsMedical) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (s.needsMedical) StatusPill(stringResource(R.string.needs_medical), statusColor(0xFFC62828))
                    tags.forEach { Pill(stringResource(it)) }
                }
            }
            if (s.photoIds.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    s.photoIds.take(4).forEach { RemoteImage(it, Modifier.size(56.dp).clip(RoundedCornerShape(8.dp))) }
                }
            }
            if (s.status == "pending") Text(stringResource(R.string.awaiting_review), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
private fun HealthSection(
    cat: Cat,
    sightings: List<Sighting>,
    medical: List<com.catmap.dubai.data.model.MedicalRecord>,
    canVerify: Boolean,
    onAddRecord: () -> Unit,
    onFlag: () -> Unit,
    onObserve: () -> Unit,
) {
    val tnr = labeledFrom(cat.tnrStatus, TnrStatus.UNKNOWN)
    val health = labeledFrom(cat.healthStatus, HealthStatus.UNKNOWN)
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Card(colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceContainerHigh)) {
            Column(Modifier.padding(14.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.current_status), style = MaterialTheme.typography.titleSmall)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.tnr_status) + ":")
                    StatusPill(stringResource(tnr.label), MaterialTheme.colorScheme.secondary)
                    if (cat.tnrVerified) Icon(Icons.Default.Verified, stringResource(R.string.verified), tint = MaterialTheme.colorScheme.secondary)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.health) + ":")
                    StatusPill(stringResource(health.label), statusColor(health.color))
                }
                Text(
                    stringResource(if (cat.tnrVerified) R.string.tnr_verified_note else R.string.tnr_community_note),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onObserve, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.add_observation)) }
            Button(onClick = onFlag, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.flag_medical)) }
        }

        SectionHeader(stringResource(R.string.verified_medical_info)) {
            if (canVerify) TextButton(onClick = onAddRecord) { Text(stringResource(R.string.add)) }
        }
        if (medical.isEmpty()) Text(stringResource(R.string.no_verified_records), style = MaterialTheme.typography.bodySmall)
        medical.forEach { record ->
            Card(colors = CardDefaults.cardColors(MaterialTheme.colorScheme.secondaryContainer)) {
                Column(Modifier.padding(12.dp).fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Verified, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(labeledFrom(record.type, MedicalType.NOTE).label), fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text(dateOnly(record.at), style = MaterialTheme.typography.labelSmall)
                    }
                    if (record.note.isNotBlank()) Text(record.note, style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.verified_by, record.byName), style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        SectionHeader(stringResource(R.string.community_observations))
        val observations = sightings.filter {
            it.healthTags.isNotEmpty() || it.needsMedical || it.possiblyNotNeutered || it.earTipped == EarTip.YES.key
        }
        if (observations.isEmpty()) Text(stringResource(R.string.no_observations), style = MaterialTheme.typography.bodySmall)
        observations.forEach { s ->
            Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Text(dateTime(s.seenAt) + " · @" + s.username, style = MaterialTheme.typography.labelMedium)
                val parts = buildList {
                    if (s.earTipped == EarTip.YES.key) add(stringResource(R.string.obs_ear_tipped))
                    if (s.possiblyNotNeutered) add(stringResource(R.string.obs_not_neutered))
                    if (s.needsMedical) add(stringResource(R.string.needs_medical))
                    s.healthTags.forEach { add(stringResource(labeledFrom(it, HealthTag.GOOD_WEIGHT).label)) }
                }
                Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.community_observation_label), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                HorizontalDivider(Modifier.padding(top = 6.dp))
            }
        }
    }
}

@Composable
private fun MedicalRecordDialog(onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var type by remember { mutableStateOf(MedicalType.TNR_CONFIRMED) }
    var note by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_verified_record)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.verified_record_hint), style = MaterialTheme.typography.bodySmall)
                LabeledDropdown(stringResource(R.string.record_type), MedicalType.entries, type, { type = it })
                OutlinedTextField(note, { note = it.take(500) }, label = { Text(stringResource(R.string.note)) }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onClick = { onSave(type.key, note); onDismiss() }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
