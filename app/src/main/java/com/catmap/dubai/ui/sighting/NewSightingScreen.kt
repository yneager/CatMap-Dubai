package com.catmap.dubai.ui.sighting

import android.Manifest
import android.content.Context
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.catmap.dubai.R
import com.catmap.dubai.data.Dubai
import com.catmap.dubai.data.LocationPrivacy
import com.catmap.dubai.data.model.BehaviorTag
import com.catmap.dubai.data.model.CatSex
import com.catmap.dubai.data.model.CatSize
import com.catmap.dubai.data.model.CoatColor
import com.catmap.dubai.data.model.CoatPattern
import com.catmap.dubai.data.model.EarTip
import com.catmap.dubai.data.model.HealthTag
import com.catmap.dubai.data.model.labeledFrom
import com.catmap.dubai.ui.components.AppTopBar
import com.catmap.dubai.ui.components.CatThumb
import com.catmap.dubai.ui.components.CoatSwatch
import com.catmap.dubai.ui.components.DropdownField
import com.catmap.dubai.ui.components.LabeledChips
import com.catmap.dubai.ui.components.LabeledDropdown
import com.catmap.dubai.ui.components.LocalBitmap
import com.catmap.dubai.ui.components.LocalContainer
import com.catmap.dubai.ui.components.LocalIsDark
import com.catmap.dubai.ui.components.LocalSnackbar
import com.catmap.dubai.ui.components.SectionHeader
import com.catmap.dubai.ui.components.dateTime
import com.catmap.dubai.ui.map.MiniMap
import com.catmap.dubai.ui.map.PointKind
import com.catmap.dubai.ui.navigation.Routes
import kotlinx.coroutines.launch
import java.io.File

private val stepTitles = listOf(R.string.step_photos, R.string.step_ai, R.string.step_details, R.string.step_location)

fun newCameraUri(context: Context): Uri {
    val dir = File(context.cacheDir, "camera").apply { mkdirs() }
    val file = File(dir, "IMG_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
}

@Composable
fun NewSightingScreen(nav: NavHostController, presetCatId: String?) {
    val container = LocalContainer.current
    val vm: NewSightingViewModel = viewModel { NewSightingViewModel(container, presetCatId) }
    val context = LocalContext.current
    val snackbar = LocalSnackbar.current
    val saved = stringResource(R.string.sighting_saved)

    // Location picked on the map screen comes back through the saved state handle.
    val handle = nav.currentBackStackEntry?.savedStateHandle
    val pickedLat by (handle?.getStateFlow<Double?>(Routes.PICKED_LAT, null) ?: kotlinx.coroutines.flow.MutableStateFlow(null)).collectAsStateWithLifecycle()
    val pickedLng by (handle?.getStateFlow<Double?>(Routes.PICKED_LNG, null) ?: kotlinx.coroutines.flow.MutableStateFlow(null)).collectAsStateWithLifecycle()
    LaunchedEffect(pickedLat, pickedLng) {
        val la = pickedLat
        val ln = pickedLng
        if (la != null && ln != null) {
            vm.setManualLocation(la, ln)
            handle?.remove<Double>(Routes.PICKED_LAT)
            handle?.remove<Double>(Routes.PICKED_LNG)
        }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { vm.locate() }
    LaunchedEffect(Unit) {
        if (vm.lat == null) permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }

    BackHandler(enabled = vm.step > 0 && !vm.submitting) { vm.step-- }

    Scaffold(
        topBar = {
            AppTopBar(
                stringResource(if (vm.presetCat != null) R.string.new_sighting_of else R.string.report_sighting, vm.presetCat?.name ?: ""),
                onBack = { if (vm.step > 0) vm.step-- else nav.popBackStack() },
            )
        },
        bottomBar = {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp)) {
                vm.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 8.dp)) }
                if (vm.submitting) LinearProgressIndicator(progress = { vm.progress }, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (vm.step > 0) {
                        OutlinedButton(onClick = { vm.step-- }, enabled = !vm.submitting, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.back)) }
                    }
                    val last = vm.step == stepTitles.lastIndex
                    val canContinue = when (vm.step) {
                        0 -> !vm.processing && (vm.photos.isNotEmpty() || vm.presetCat != null)
                        2 -> !vm.isNewCat || vm.name.isNotBlank()
                        3 -> vm.insideDubai && !vm.submitting
                        else -> !vm.processing
                    }
                    Button(
                        onClick = {
                            if (last) {
                                vm.submit { catId ->
                                    container.scope.launch { snackbar.showSnackbar(saved) }
                                    nav.navigate(Routes.cat(catId)) { popUpTo(Routes.NEW_SIGHTING) { inclusive = true } }
                                }
                            } else {
                                vm.step++
                            }
                        },
                        enabled = canContinue,
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(if (last) R.string.submit else R.string.next)) }
                }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().imePadding()) {
            LinearProgressIndicator(progress = { (vm.step + 1f) / stepTitles.size }, modifier = Modifier.fillMaxWidth())
            Text(
                stringResource(R.string.step_x_of_y, vm.step + 1, stepTitles.size) + " · " + stringResource(stepTitles[vm.step]),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (vm.step) {
                    0 -> PhotosStep(vm)
                    1 -> AiStep(vm)
                    2 -> DetailsStep(vm)
                    else -> LocationStep(vm, onPick = { nav.navigate(Routes.pickLocation(vm.lat, vm.lng)) })
                }
                Spacer(Modifier.size(24.dp))
            }
        }
    }
}

@Composable
private fun PhotosStep(vm: NewSightingViewModel) {
    val context = LocalContext.current
    var cameraUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = cameraUri
        if (ok && uri != null) vm.addPhotos(listOf(uri))
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(NewSightingViewModel.MAX_PHOTOS)) { uris ->
        vm.addPhotos(uris)
    }
    val full = vm.photos.size >= NewSightingViewModel.MAX_PHOTOS

    Text(stringResource(R.string.photos_hint))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(
            onClick = { newCameraUri(context).also { cameraUri = it; camera.launch(it) } },
            enabled = !full && !vm.processing,
            modifier = Modifier.weight(1f),
        ) {
            Icon(Icons.Default.CameraAlt, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.take_photo))
        }
        OutlinedButton(
            onClick = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            enabled = !full && !vm.processing,
            modifier = Modifier.weight(1f),
        ) {
            Icon(Icons.Default.PhotoLibrary, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.from_gallery))
        }
    }
    if (vm.processing) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.analyzing_photos))
        }
    }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(vm.photos, key = { it.key }, contentType = { "NewSightingScreen" }) { p ->
            Box {
                LocalBitmap(p.photo.analysis, Modifier.size(130.dp).clip(RoundedCornerShape(12.dp)), stringResource(R.string.photo))
                FilledIconButton(
                    onClick = { vm.removePhoto(p) },
                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(28.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)),
                ) { Icon(Icons.Default.Close, stringResource(R.string.remove), Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurface) }
            }
        }
    }
    Card(colors = CardDefaults.cardColors(MaterialTheme.colorScheme.secondaryContainer)) {
        Text(stringResource(R.string.metadata_removed_note), Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
    }
    Text(stringResource(R.string.auto_time, dateTime(vm.seenAt)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AiStep(vm: NewSightingViewModel) {
    val a = vm.analysis
    if (a == null) {
        Text(stringResource(if (vm.photos.isEmpty()) R.string.ai_needs_photo else R.string.analyzing_photos))
    } else {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.ai_results), style = MaterialTheme.typography.titleMedium)
                }
                if (!a.catDetected) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.ai_no_cat), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
                ConfidenceRow(stringResource(R.string.coat_color), stringResource(a.coat.label), a.coatConfidence) { CoatSwatch(a.coat) }
                ConfidenceRow(stringResource(R.string.pattern), stringResource(a.pattern.label), a.patternConfidence)
                Text(stringResource(R.string.ai_breed_traits), style = MaterialTheme.typography.titleSmall)
                a.breedHints.forEach { hint -> ConfidenceRow(null, stringResource(hint.text), hint.confidence) }
                Text(stringResource(R.string.ai_disclaimer), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    val preset = vm.presetCat
    if (preset != null) {
        SectionHeader(stringResource(R.string.adding_to_profile))
        MatchedCatRow(preset.name, preset.coverPhotoId, labeledFrom(preset.coatColor, CoatColor.UNKNOWN), Dubai.nameOf(preset.neighborhood))
        return
    }

    SectionHeader(stringResource(R.string.possible_matches)) {
        if (vm.matching) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
    }
    Text(stringResource(R.string.matches_hint), style = MaterialTheme.typography.bodySmall)
    val visible = vm.matches.filter { it.cat.id !in vm.rejected }
    if (vm.matchesSearched && visible.isEmpty()) {
        // Say "you ruled them out" rather than "none found" when the person rejected every suggestion.
        Text(stringResource(if (vm.matches.isEmpty()) R.string.no_matches else R.string.all_matches_rejected), style = MaterialTheme.typography.bodyMedium)
    }
    visible.forEach { match ->
        val selected = vm.confirmed?.id == match.cat.id
        Card(
            Modifier.fillMaxWidth(),
            border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CatThumb(match.cat.coverPhotoId, labeledFrom(match.cat.coatColor, CoatColor.UNKNOWN), Modifier.size(64.dp).clip(RoundedCornerShape(10.dp)))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(match.cat.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            Dubai.nameOf(match.cat.neighborhood) + (match.distanceKm?.let { " · " + stringResource(R.string.km_away, "%.1f".format(it)) } ?: ""),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.percent, (match.breakdown.total * 100).toInt()), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                        Text(stringResource(R.string.similarity), style = MaterialTheme.typography.labelSmall)
                    }
                }
                ScoreBar(stringResource(R.string.score_colour), match.breakdown.colour)
                ScoreBar(stringResource(R.string.score_texture), match.breakdown.texture)
                ScoreBar(stringResource(R.string.score_attributes), match.breakdown.attributes)
                match.breakdown.proximity?.let { ScoreBar(stringResource(R.string.score_distance), it) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (selected) {
                        AssistChip(onClick = { vm.clearConfirmation() }, label = { Text(stringResource(R.string.confirmed_same_cat)) },
                            leadingIcon = { Icon(Icons.Default.CheckCircle, null, Modifier.size(18.dp)) })
                    } else {
                        Button(onClick = { vm.confirm(match.cat) }) { Text(stringResource(R.string.same_cat)) }
                    }
                    OutlinedButton(onClick = { vm.reject(match.cat) }) { Text(stringResource(R.string.not_this_cat)) }
                }
            }
        }
    }
    Card(colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Text(
            stringResource(if (vm.confirmed == null) R.string.will_create_new else R.string.will_add_to, vm.confirmed?.name ?: ""),
            Modifier.padding(12.dp),
        )
    }
}

@Composable
private fun ConfidenceRow(label: String?, value: String, confidence: Float, leading: (@Composable () -> Unit)? = null) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (label != null) Text("$label: ", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            leading?.invoke()
            if (leading != null) Spacer(Modifier.width(6.dp))
            Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text(stringResource(R.string.percent, (confidence * 100).toInt()), style = MaterialTheme.typography.labelMedium)
        }
        LinearProgressIndicator(progress = { confidence }, modifier = Modifier.fillMaxWidth().padding(top = 2.dp))
    }
}

@Composable
private fun ScoreBar(label: String, value: Double) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(90.dp))
        LinearProgressIndicator(progress = { value.toFloat().coerceIn(0f, 1f) }, modifier = Modifier.weight(1f))
        Text(stringResource(R.string.percent, (value * 100).toInt()), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 6.dp).width(40.dp))
    }
}

@Composable
private fun MatchedCatRow(name: String, photoId: String, coat: CoatColor, area: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CatThumb(photoId, coat, Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)))
        Spacer(Modifier.width(10.dp))
        Column {
            Text(name, fontWeight = FontWeight.SemiBold)
            Text(area, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailsStep(vm: NewSightingViewModel) {
    val target = vm.target
    if (target == null) {
        SectionHeader(stringResource(R.string.new_cat_profile))
        OutlinedTextField(
            vm.name, { vm.name = it.take(40) },
            label = { Text(stringResource(R.string.cat_name_required)) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.name_ideas), style = MaterialTheme.typography.labelMedium, modifier = Modifier.align(Alignment.CenterVertically))
            vm.suggestedNames().forEach { idea -> SuggestionChip(onClick = { vm.name = idea }, label = { Text(idea) }) }
        }
        OutlinedTextField(
            vm.description, { vm.description = it.take(800) },
            label = { Text(stringResource(R.string.description)) }, minLines = 2, modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            vm.marks, { vm.marks = it.take(300) },
            label = { Text(stringResource(R.string.distinctive_marks)) },
            placeholder = { Text(stringResource(R.string.distinctive_marks_hint)) }, modifier = Modifier.fillMaxWidth(),
        )
    } else {
        SectionHeader(stringResource(R.string.adding_to_profile))
        MatchedCatRow(target.name, target.coverPhotoId, labeledFrom(target.coatColor, CoatColor.UNKNOWN), Dubai.nameOf(target.neighborhood))
    }

    SectionHeader(stringResource(R.string.physical_characteristics))
    DropdownField(stringResource(R.string.coat_color), CoatColor.entries, vm.coat, { vm.editCoat(it) }, leading = { CoatSwatch(it) }) { stringResource(it.label) }
    LabeledDropdown(stringResource(R.string.pattern), CoatPattern.entries, vm.pattern, { vm.editPattern(it) })
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LabeledDropdown(stringResource(R.string.size), CatSize.entries, vm.size, { vm.size = it }, Modifier.weight(1f))
        LabeledDropdown(stringResource(R.string.sex), CatSex.entries, vm.sex, { vm.sex = it }, Modifier.weight(1f))
    }

    SectionHeader(stringResource(R.string.health_observations))
    LabeledChips(HealthTag.entries, vm.healthTags.toSet(), { vm.toggleHealth(it) })
    SectionHeader(stringResource(R.string.behavior_observations))
    LabeledChips(BehaviorTag.entries, vm.behaviorTags.toSet(), { vm.toggleBehavior(it) })

    SectionHeader(stringResource(R.string.tnr_section))
    Text(stringResource(R.string.ear_tip_question), style = MaterialTheme.typography.bodyMedium)
    Row {
        EarTip.entries.forEach { option ->
            Row(
                Modifier.selectable(vm.earTip == option, role = Role.RadioButton) { vm.earTip = option }.padding(end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(vm.earTip == option, null)
                Text(stringResource(option.label), Modifier.padding(start = 4.dp))
            }
        }
    }
    CheckRow(stringResource(R.string.report_not_neutered), vm.notNeutered) { vm.notNeutered = it }
    CheckRow(stringResource(R.string.flag_needs_medical), vm.needsMedical) { vm.needsMedical = it }
    if (vm.needsMedical) {
        Text(stringResource(R.string.medical_rescue_tip), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
    OutlinedTextField(
        vm.notes, { vm.notes = it.take(1000) },
        label = { Text(stringResource(R.string.notes)) }, minLines = 2, modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
fun CheckRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(checked, role = Role.Checkbox) { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked, null)
        Text(label, Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun LocationStep(vm: NewSightingViewModel, onPick: () -> Unit) {
    val container = LocalContainer.current
    val settings by container.settings.settings.collectAsStateWithLifecycle(null)
    val lat = vm.lat
    val lng = vm.lng
    Text(stringResource(R.string.location_hint))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = { vm.locate() }, enabled = !vm.locating, modifier = Modifier.weight(1f)) {
            if (vm.locating) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            else Icon(Icons.Default.MyLocation, null)
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.use_gps))
        }
        OutlinedButton(onClick = onPick, modifier = Modifier.weight(1f)) {
            Icon(Icons.Default.Place, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.pick_on_map))
        }
    }
    if (lat != null && lng != null) {
        MiniMap(lat, lng, PointKind.CAT, LocalIsDark.current, vm.coat, height = 200.dp, zoom = 16.0)
        if (vm.insideDubai) {
            Text(
                stringResource(R.string.near_x, Dubai.nearest(lat, lng).displayName()) +
                    if (vm.locationSource == "gps" && vm.accuracy > 0) " · " + stringResource(R.string.accuracy_m, vm.accuracy.toInt()) else "",
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            Card(colors = CardDefaults.cardColors(MaterialTheme.colorScheme.errorContainer)) {
                Text(stringResource(R.string.outside_dubai_hint), Modifier.padding(12.dp))
            }
        }
    } else if (!vm.locating) {
        Text(stringResource(R.string.no_location_yet), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    val level = settings?.privacyLevel ?: LocationPrivacy.STANDARD
    Card(colors = CardDefaults.cardColors(MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(12.dp)) {
            Text(stringResource(R.string.privacy_public_map, LocationPrivacy.approxRadiusMeters(level)), style = MaterialTheme.typography.bodySmall)
            Text(
                stringResource(if (settings?.shareExactWithVolunteers != false) R.string.privacy_exact_shared else R.string.privacy_exact_not_shared),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
    Text(stringResource(R.string.auto_time, dateTime(vm.seenAt)), style = MaterialTheme.typography.bodySmall)
}
