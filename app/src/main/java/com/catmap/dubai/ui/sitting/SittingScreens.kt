package com.catmap.dubai.ui.sitting

import com.catmap.dubai.ui.icons.CatMapIcon
import com.catmap.dubai.ui.icons.animalNames
import com.catmap.dubai.ui.icons.CatMapIcons
import com.catmap.dubai.ui.icons.IconLabel
import com.catmap.dubai.ui.components.currentLocale
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PersonSearch
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.catmap.dubai.R
import com.catmap.dubai.data.Dubai
import com.catmap.dubai.data.LocationPrivacy
import com.catmap.dubai.data.distanceKm
import com.catmap.dubai.data.loaded
import com.catmap.dubai.data.model.AnimalType
import com.catmap.dubai.data.model.Booking
import com.catmap.dubai.data.model.BookingStatus
import com.catmap.dubai.data.model.RateUnit
import com.catmap.dubai.data.model.SitterProfile
import com.catmap.dubai.data.model.SitterService
import com.catmap.dubai.data.model.labeledFrom
import com.catmap.dubai.data.now
import com.catmap.dubai.ui.components.AppTopBar
import com.catmap.dubai.ui.components.EmptyState
import com.catmap.dubai.ui.components.LoadingBox
import com.catmap.dubai.ui.components.LocalContainer
import com.catmap.dubai.ui.components.LocalIsDark
import com.catmap.dubai.ui.components.RatingInput
import com.catmap.dubai.ui.components.RatingStars
import com.catmap.dubai.ui.components.RemoteImage
import com.catmap.dubai.ui.components.SectionHeader
import com.catmap.dubai.ui.components.SkeletonRow
import com.catmap.dubai.ui.components.StatusPill
import com.catmap.dubai.ui.components.dateOnly
import com.catmap.dubai.ui.components.distanceText
import com.catmap.dubai.ui.components.formatNumber
import com.catmap.dubai.ui.components.relativeTime
import com.catmap.dubai.ui.components.rememberRunner
import com.catmap.dubai.ui.explore.toggle
import com.catmap.dubai.ui.map.MapMemory
import com.catmap.dubai.ui.map.MarkerIcons
import com.catmap.dubai.ui.map.MiniMap
import com.catmap.dubai.ui.navigation.Routes
import com.catmap.dubai.ui.stations.SwitchRow
import kotlinx.coroutines.launch
import java.util.Calendar

@Composable
private fun SitterAvatar(sitter: SitterProfile, size: androidx.compose.ui.unit.Dp) {
    if (sitter.photoId.isNotBlank()) {
        RemoteImage(sitter.photoId, Modifier.size(size).clip(CircleShape))
    } else {
        Box(Modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.tertiaryContainer), contentAlignment = Alignment.Center) {
            Text(sitter.name.take(1).uppercase().ifBlank { "S" }, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onTertiaryContainer)
        }
    }
}

@Composable
private fun rateText(sitter: SitterProfile): String =
    if (sitter.rate <= 0) stringResource(R.string.rate_ask) else stringResource(R.string.rate_x, formatNumber(sitter.rate), stringResource(labeledFrom(sitter.rateUnit, RateUnit.VISIT).label))

/** Nearby pet sitters, nearest first when the location is known. */
@Composable
fun SittersScreen(nav: NavHostController) {
    val container = LocalContainer.current
    val uid = container.session.uid
    val sitters by remember { container.sitting.activeSitters() }.collectAsStateWithLifecycle(null)
    val mine by remember(uid) { container.sitting.sitterFlow(uid.orEmpty()) }.collectAsStateWithLifecycle(null)
    val fix by container.location.last.collectAsStateWithLifecycle()
    var pet by rememberSaveable { mutableStateOf<String?>(null) }
    var verifiedOnly by rememberSaveable { mutableStateOf(false) }
    val shown = sitters?.filter { (pet == null || pet in it.petTypes) && it.uid != uid && (!verifiedOnly || it.verified) }?.let { list ->
        val f = fix
        if (f != null) list.sortedBy { distanceKm(f.lat, f.lng, it.lat, it.lng) } else list.sortedByDescending { it.rating }
    }

    Scaffold(
        topBar = {
            AppTopBar(stringResource(R.string.pet_sitters), onBack = { nav.popBackStack() }) {
                TextButton(onClick = { nav.navigate(Routes.bookings()) }) { Text(stringResource(R.string.bookings)) }
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { nav.navigate(Routes.SITTER_EDIT) },
                icon = { Icon(if (mine == null) com.catmap.dubai.ui.icons.catMapVector(com.catmap.dubai.ui.icons.CatMapIcons.SITTER) else Icons.Default.Edit, null) },
                text = { Text(stringResource(if (mine == null) R.string.become_sitter else R.string.edit_sitter_profile)) },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { FilterChip(verifiedOnly,{verifiedOnly=!verifiedOnly},label={Text(stringResource(R.string.verified_only))}) }
            item { Text(stringResource(R.string.sitters_intro), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { FilterChip(pet == null, { pet = null }, label = { Text(stringResource(R.string.all)) }) }
                    items(AnimalType.entries,key={it.key},contentType={"animal-chip"}) { t -> FilterChip(pet == t.key, { pet = t.key }, label = { IconLabel(t.icon, stringResource(t.label)) }) }
                }
            }
            when {
                shown == null -> items(4, contentType = { "loading" }) { SkeletonRow() }
                shown.isEmpty() -> item { EmptyState(Icons.Outlined.PersonSearch, stringResource(R.string.no_sitters)) }
                else -> items(shown, key = { it.uid }, contentType = { "SittingScreens" }) { s -> SitterCard(s, Modifier.animateItem()) { nav.navigate(Routes.sitter(s.uid)) } }
            }
        }
    }
}

@Composable
private fun SitterCard(s: SitterProfile, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val fix by LocalContainer.current.location.last.collectAsStateWithLifecycle()
    Card(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            SitterAvatar(s, 60.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(s.name, style = MaterialTheme.typography.titleMedium)
                if(s.verified) IconLabel(CatMapIcons.VERIFIED,stringResource(R.string.verified_sitter))
                if (s.headline.isNotBlank()) Text(s.headline, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RatingStars(s.rating, size = 14.dp)
                    Spacer(Modifier.width(4.dp))
                    Text(if (s.ratingCount > 0) String.format(currentLocale(), "%.1f (%d)", s.rating, s.ratingCount) else stringResource(R.string.new_sitter), style = MaterialTheme.typography.labelSmall)
                }
                Text(
                    listOfNotNull(Dubai.nameOf(s.neighborhood), fix?.let { distanceText(distanceKm(it.lat, it.lng, s.lat, s.lng)) }).joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(animalNames(s.petTypes) + "   " + rateText(s), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SitterDetailScreen(nav: NavHostController, uid: String) {
    val container = LocalContainer.current
    val loaded by remember(uid) { container.sitting.sitterFlow(uid).loaded() }.collectAsStateWithLifecycle(null)
    val reviews by remember(uid) { container.sitting.reviewsFor(uid) }.collectAsStateWithLifecycle(emptyList())
    val me = container.session.uid
    val sitter = loaded?.value
    Scaffold(topBar = { AppTopBar(sitter?.name.orEmpty(), onBack = { nav.popBackStack() }) }) { padding ->
        when {
            loaded == null -> LoadingBox(Modifier.padding(padding))
            sitter == null -> EmptyState(Icons.Outlined.PersonSearch, stringResource(R.string.sitter_removed), Modifier.padding(padding))
            else -> LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        SitterAvatar(sitter, 96.dp)
                        Text("@" + sitter.name, style = MaterialTheme.typography.headlineSmall)
                        if (sitter.headline.isNotBlank()) Text(sitter.headline, style = MaterialTheme.typography.bodyLarge)
                        if(sitter.verified) IconLabel(CatMapIcons.VERIFIED,stringResource(R.string.verified_sitter))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RatingStars(sitter.rating, size = 20.dp)
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.reviews_n, sitter.ratingCount), style = MaterialTheme.typography.labelLarge)
                        }
                        Text(rateText(sitter), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        if (!sitter.active) StatusPill(stringResource(R.string.not_taking_bookings), Color(0xFF757575))
                    }
                }
                if (uid != me) item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = { nav.navigate(Routes.book(uid)) }, enabled = sitter.active, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Outlined.CalendarMonth, null)
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.request_booking), maxLines = 1)
                        }
                        OutlinedButton(onClick = { nav.navigate(Routes.chatWith(uid)) }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.AutoMirrored.Outlined.Chat, null)
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.message), maxLines = 1)
                        }
                    }
                } else item {
                    OutlinedButton(onClick = { nav.navigate(Routes.SITTER_EDIT) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.edit_sitter_profile)) }
                }
                item {
                    SectionHeader(stringResource(R.string.pets_cared_for))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        sitter.petTypes.forEach { t -> val a = labeledFrom(t, AnimalType.OTHER); StatusPill(stringResource(a.label), MaterialTheme.colorScheme.primary) }
                    }
                    if (sitter.services.isNotEmpty()) {
                        SectionHeader(stringResource(R.string.services))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            sitter.services.forEach { s -> StatusPill(stringResource(labeledFrom(s, SitterService.HOME_VISITS).label), MaterialTheme.colorScheme.secondary) }
                        }
                    }
                }
                if (sitter.description.isNotBlank()) item { SectionHeader(stringResource(R.string.about)); Text(sitter.description) }
                if (sitter.experience.isNotBlank()) item { SectionHeader(stringResource(R.string.experience)); Text(sitter.experience) }
                if (sitter.availability.isNotBlank()) item { SectionHeader(stringResource(R.string.availability)); Text(sitter.availability) }
                item {
                    SectionHeader(stringResource(R.string.area))
                    MiniMap(sitter.lat, sitter.lng, MarkerIcons.SITTER, LocalIsDark.current, zoom = 13.0)
                    Text(stringResource(R.string.sitter_area_note, Dubai.nameOf(sitter.neighborhood)), Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodySmall)
                }
                item { SectionHeader(stringResource(R.string.reviews)) }
                if (reviews.isEmpty()) item { Text(stringResource(R.string.no_reviews), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                items(reviews, key = { it.id }, contentType = { "SittingScreens" }) { r ->
                    Column(Modifier.padding(vertical = 6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("@" + r.authorName.ifBlank {stringResource(R.string.deleted_member)}, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                            RatingStars(r.rating.toDouble(), size = 14.dp)
                        }
                        if (r.text.isNotBlank()) Text(r.text, style = MaterialTheme.typography.bodyMedium)
                        Text(relativeTime(r.createdAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        HorizontalDivider(Modifier.padding(top = 8.dp))
                    }
                }
            }
        }
    }
}

/** Create or edit my pet-sitter profile. Only an approximate area is published. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SitterEditScreen(nav: NavHostController) {
    val container = LocalContainer.current
    val runner = rememberRunner()
    val scope = rememberCoroutineScope()
    val me by container.session.profile.collectAsStateWithLifecycle()
    val uid = container.session.uid ?: return
    val existing by remember(uid) { container.sitting.sitterFlow(uid).loaded() }.collectAsStateWithLifecycle(null)
    var headline by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var experience by rememberSaveable { mutableStateOf("") }
    var availability by rememberSaveable { mutableStateOf("") }
    var rate by rememberSaveable { mutableStateOf("") }
    var rateUnit by rememberSaveable { mutableStateOf(RateUnit.VISIT.key) }
    var pets by remember { mutableStateOf(setOf(AnimalType.CAT.key)) }
    var services by remember { mutableStateOf(setOf(SitterService.HOME_VISITS.key)) }
    var active by rememberSaveable { mutableStateOf(true) }
    var lat by rememberSaveable { mutableStateOf<Double?>(null) }
    var lng by rememberSaveable { mutableStateOf<Double?>(null) }
    var filled by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(existing) {
        val s = existing?.value
        if (!filled && existing != null) {
            filled = true
            if (s != null) {
                headline = s.headline; description = s.description; experience = s.experience; availability = s.availability
                rate = if (s.rate > 0) s.rate.toString() else ""; rateUnit = s.rateUnit; pets = s.petTypes.toSet(); services = s.services.toSet()
                active = s.active; lat = s.lat; lng = s.lng
            }
        }
    }
    val handle = nav.currentBackStackEntry?.savedStateHandle
    val pickedLat by (handle?.getStateFlow<Double?>(Routes.PICKED_LAT, null) ?: kotlinx.coroutines.flow.MutableStateFlow(null)).collectAsStateWithLifecycle()
    val pickedLng by (handle?.getStateFlow<Double?>(Routes.PICKED_LNG, null) ?: kotlinx.coroutines.flow.MutableStateFlow(null)).collectAsStateWithLifecycle()
    LaunchedEffect(pickedLat, pickedLng) {
        if (pickedLat != null && pickedLng != null) {
            // The sitter's home is blurred before it is stored.
            val (a, b) = LocationPrivacy.fuzz(pickedLat!!, pickedLng!!, LocationPrivacy.HIGH, uid)
            lat = a; lng = b
            handle?.remove<Double>(Routes.PICKED_LAT); handle?.remove<Double>(Routes.PICKED_LNG)
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted.values.any { it }) scope.launch {
            container.location.current()?.let { val (a, b) = LocationPrivacy.fuzz(it.lat, it.lng, LocationPrivacy.HIGH, uid); lat = a; lng = b }
        }
    }

    Scaffold(topBar = { AppTopBar(stringResource(if (existing?.value == null) R.string.become_sitter else R.string.edit_sitter_profile), onBack = { nav.popBackStack() }) }) { padding ->
        if (existing == null) { LoadingBox(Modifier.padding(padding)); return@Scaffold }
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(headline, { headline = it.take(80) }, label = { Text(stringResource(R.string.sitter_headline)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(description, { description = it.take(1500) }, label = { Text(stringResource(R.string.about_you)) }, minLines = 3, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(experience, { experience = it.take(600) }, label = { Text(stringResource(R.string.experience)) }, minLines = 2, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(availability, { availability = it.take(300) }, label = { Text(stringResource(R.string.availability)) },
                supportingText = { Text(stringResource(R.string.availability_hint)) }, modifier = Modifier.fillMaxWidth())
            Text(stringResource(R.string.pets_cared_for), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AnimalType.entries.forEach { t -> FilterChip(t.key in pets, { pets = pets.toggle(t.key) }, label = { IconLabel(t.icon, stringResource(t.label)) }) }
            }
            Text(stringResource(R.string.services), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SitterService.entries.forEach { s -> FilterChip(s.key in services, { services = services.toggle(s.key) }, label = { Text(stringResource(s.label)) }) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(rate, { rate = it.filter { c -> c.isDigit() }.take(5) }, label = { Text(stringResource(R.string.rate_optional)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.weight(1f))
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RateUnit.entries.forEach { u -> FilterChip(rateUnit == u.key, { rateUnit = u.key }, label = { Text(stringResource(u.label)) }) }
            }
            SectionHeader(stringResource(R.string.area))
            val la = lat
            val ln = lng
            if (la != null && ln != null && (la != 0.0 || ln != 0.0)) {
                MiniMap(la, ln, MarkerIcons.SITTER, LocalIsDark.current, zoom = 13.0)
                Text(Dubai.nearest(la, ln).displayName(), style = MaterialTheme.typography.titleSmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.use_my_location), maxLines = 1)
                }
                OutlinedButton(onClick = { nav.navigate(Routes.pickLocation(la ?: MapMemory.lat, ln ?: MapMemory.lng)) }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.choose_on_map), maxLines = 1)
                }
            }
            Text(stringResource(R.string.sitter_privacy_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SwitchRow(stringResource(R.string.taking_bookings), active) { active = it }
            Button(
                onClick = {
                    runner.run(R.string.saved, onSuccess = { nav.popBackStack() }) {
                        container.sitting.saveSitter(
                            me ?: return@run,
                            SitterProfile(
                                headline = headline, description = description, experience = experience, availability = availability,
                                rate = rate.toLongOrNull() ?: 0, rateUnit = rateUnit, petTypes = pets.toList(), services = services.toList(),
                                lat = la ?: return@run, lng = ln ?: return@run, active = active,
                            ),
                        )
                    }
                },
                enabled = la != null && ln != null && headline.isNotBlank() && pets.isNotEmpty() && !runner.busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.save)) }
        }
    }
}

/** Ask a sitter for dates; they accept or decline from their bookings list. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BookingRequestScreen(nav: NavHostController, sitterId: String) {
    val container = LocalContainer.current
    val context = LocalContext.current
    val runner = rememberRunner()
    val me by container.session.profile.collectAsStateWithLifecycle()
    val sitter by remember(sitterId) { container.sitting.sitterFlow(sitterId) }.collectAsStateWithLifecycle(null)
    var start by rememberSaveable { mutableStateOf(startOfDay(1)) }
    var end by rememberSaveable { mutableStateOf(startOfDay(3)) }
    var pets by remember { mutableStateOf(setOf(AnimalType.CAT.key)) }
    var count by rememberSaveable { mutableIntStateOf(1) }
    var notes by rememberSaveable { mutableStateOf("") }

    Scaffold(topBar = { AppTopBar(stringResource(R.string.request_booking), onBack = { nav.popBackStack() }) }) { padding ->
        val s = sitter
        if (s == null) { LoadingBox(Modifier.padding(padding)); return@Scaffold }
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SitterAvatar(s, 52.dp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("@" + s.name, style = MaterialTheme.typography.titleMedium)
                    Text(rateText(s), style = MaterialTheme.typography.bodySmall)
                }
            }
            SectionHeader(stringResource(R.string.dates))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { pickDate(context, start) { start = it; if (end < it) end = it } }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.from_x, dateOnly(start)), maxLines = 1)
                }
                OutlinedButton(onClick = { pickDate(context, end, minDate = start) { end = it } }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.to_x, dateOnly(end)), maxLines = 1)
                }
            }
            Text(stringResource(R.string.which_pets), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AnimalType.entries.filter { it.key in s.petTypes }.forEach { t -> FilterChip(t.key in pets, { pets = pets.toggle(t.key) }, label = { IconLabel(t.icon, stringResource(t.label)) }) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.how_many_pets), Modifier.weight(1f))
                TextButton(onClick = { if (count > 1) count-- }) { Text("−") }
                Text(count.toString(), style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { if (count < 20) count++ }) { Text("+") }
            }
            OutlinedTextField(notes, { notes = it.take(1000) }, label = { Text(stringResource(R.string.booking_notes)) }, minLines = 3, modifier = Modifier.fillMaxWidth())
            Button(
                onClick = {
                    runner.run(R.string.booking_sent, onSuccess = { nav.popBackStack(); nav.navigate(Routes.bookings()) }) {
                        container.sitting.requestBooking(s, pets.toList(), count.toLong(), start, end, notes, me ?: return@run)
                    }
                },
                enabled = pets.isNotEmpty() && !runner.busy && me != null,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.send_request)) }
        }
    }
}

private fun startOfDay(daysFromNow: Int): Long = Calendar.getInstance().apply {
    add(Calendar.DAY_OF_YEAR, daysFromNow)
    set(Calendar.HOUR_OF_DAY, 9); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

private fun pickDate(context: android.content.Context, initial: Long, minDate: Long = now(), onPicked: (Long) -> Unit) {
    val cal = Calendar.getInstance().apply { timeInMillis = initial }
    android.app.DatePickerDialog(context, { _, y, m, d ->
        cal.set(y, m, d, 9, 0, 0)
        onPicked(cal.timeInMillis)
    }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).apply {
        datePicker.minDate = minOf(minDate, initial) - 1000
    }.show()
}

private enum class BookingTab(val label: Int) { MINE(R.string.as_owner), SITTING(R.string.as_sitter) }

@Composable
fun BookingsScreen(nav: NavHostController, startAsSitter: Boolean = false) {
    val container = LocalContainer.current
    val runner = rememberRunner()
    val me by container.session.profile.collectAsStateWithLifecycle()
    val uid = container.session.uid ?: return
    var tab by rememberSaveable { mutableStateOf(if (startAsSitter) BookingTab.SITTING else BookingTab.MINE) }
    val asOwner by remember(uid) { container.sitting.asOwner(uid) }.collectAsStateWithLifecycle(null)
    val asSitter by remember(uid) { container.sitting.asSitter(uid) }.collectAsStateWithLifecycle(null)
    var reviewing by remember { mutableStateOf<Booking?>(null) }
    val list = if (tab == BookingTab.MINE) asOwner else asSitter

    Scaffold(topBar = { AppTopBar(stringResource(R.string.bookings), onBack = { nav.popBackStack() }) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            PrimaryTabRow(selectedTabIndex = tab.ordinal) {
                BookingTab.entries.forEach { t -> Tab(tab == t, { tab = t }, text = { Text(stringResource(t.label)) }) }
            }
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when {
                    list == null -> items(3, contentType = { "loading" }) { SkeletonRow() }
                    list.isEmpty() -> item {
                        EmptyState(Icons.Outlined.EventBusy, stringResource(if (tab == BookingTab.MINE) R.string.no_bookings_owner else R.string.no_bookings_sitter)) {
                            if (tab == BookingTab.MINE) Button(onClick = { nav.navigate(Routes.SITTERS) }) { Text(stringResource(R.string.find_sitter)) }
                        }
                    }
                    else -> items(list, key = { it.id }, contentType = { "SittingScreens" }) { b ->
                        BookingCard(
                            b, asSitter = tab == BookingTab.SITTING, modifier = Modifier.animateItem(),
                            onStatus = { st -> runner.run(R.string.updated) { container.sitting.setStatus(b, st, me ?: return@run) } },
                            onMessage = { nav.navigate(Routes.chatWith(if (tab == BookingTab.SITTING) b.ownerId else b.sitterId)) },
                            onReview = { reviewing = b },
                        )
                    }
                }
            }
        }
    }
    reviewing?.let { b -> ReviewDialog(b) { reviewing = null } }
}

@Composable
private fun BookingCard(b: Booking, asSitter: Boolean, modifier: Modifier, onStatus: (BookingStatus) -> Unit, onMessage: () -> Unit, onReview: () -> Unit) {
    val status = labeledFrom(b.status, BookingStatus.REQUESTED)
    Card(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("@" + (if (asSitter) b.ownerName else b.sitterName).ifBlank {stringResource(R.string.deleted_member)}, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                StatusPill(stringResource(status.label), Color(status.color))
            }
            Text(stringResource(R.string.date_range, dateOnly(b.startAt), dateOnly(b.endAt)), style = MaterialTheme.typography.bodyMedium)
            Text(
                animalNames(b.petTypes) + "  ×" + b.petCount,
                style = MaterialTheme.typography.bodyMedium,
            )
            if (b.notes.isNotBlank()) Text(b.notes, style = MaterialTheme.typography.bodySmall)
            if (b.sitterNote.isNotBlank()) Text(stringResource(R.string.sitter_note_x, b.sitterNote), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = onMessage) { Text(stringResource(R.string.message)) }
                when {
                    asSitter && status == BookingStatus.REQUESTED -> {
                        FilledTonalButton(onClick = { onStatus(BookingStatus.ACCEPTED) }) { Text(stringResource(R.string.accept)) }
                        TextButton(onClick = { onStatus(BookingStatus.DECLINED) }) { Text(stringResource(R.string.decline)) }
                    }
                    status == BookingStatus.ACCEPTED -> {
                        FilledTonalButton(onClick = { onStatus(BookingStatus.COMPLETED) }) { Text(stringResource(R.string.mark_completed)) }
                        TextButton(onClick = { onStatus(BookingStatus.CANCELLED) }) { Text(stringResource(R.string.cancel_booking)) }
                    }
                    !asSitter && status == BookingStatus.REQUESTED -> TextButton(onClick = { onStatus(BookingStatus.CANCELLED) }) { Text(stringResource(R.string.cancel_booking)) }
                    !asSitter && status == BookingStatus.COMPLETED && !b.reviewed -> FilledTonalButton(onClick = onReview) { Text(stringResource(R.string.leave_review)) }
                }
            }
            Text(relativeTime(b.createdAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ReviewDialog(b: Booking, onDismiss: () -> Unit) {
    val container = LocalContainer.current
    val runner = rememberRunner()
    val me by container.session.profile.collectAsStateWithLifecycle()
    var rating by rememberSaveable { mutableIntStateOf(5) }
    var text by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.review_x, b.sitterName)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                RatingInput(rating) { rating = it }
                OutlinedTextField(text, { text = it.take(800) }, label = { Text(stringResource(R.string.review_text)) }, minLines = 3, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(enabled = !runner.busy && me != null, onClick = {
                runner.run(R.string.review_thanks, onSuccess = onDismiss) { container.sitting.review(b, rating, text, me ?: return@run) }
            }) { Text(stringResource(R.string.submit)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
