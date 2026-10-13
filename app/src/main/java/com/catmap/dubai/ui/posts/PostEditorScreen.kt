package com.catmap.dubai.ui.posts

import com.catmap.dubai.ui.icons.CatMapIcon
import com.catmap.dubai.ui.icons.CatMapIcons
import com.catmap.dubai.ui.icons.IconLabel
import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.catmap.dubai.R
import com.catmap.dubai.data.AppSettings
import com.catmap.dubai.data.Dubai
import com.catmap.dubai.data.LocationPrivacy
import com.catmap.dubai.data.PostRepository
import com.catmap.dubai.data.model.AgeGroup
import com.catmap.dubai.data.model.AnimalType
import com.catmap.dubai.data.model.CatSex
import com.catmap.dubai.data.model.ContactMethod
import com.catmap.dubai.data.model.PostCategory
import com.catmap.dubai.data.userMessage
import com.catmap.dubai.ui.components.AppTopBar
import com.catmap.dubai.ui.components.LoadingBox
import com.catmap.dubai.ui.components.LocalBitmap
import com.catmap.dubai.ui.components.LocalContainer
import com.catmap.dubai.ui.components.LocalIsDark
import com.catmap.dubai.ui.components.LocalSnackbar
import com.catmap.dubai.ui.components.PostCard
import com.catmap.dubai.ui.components.RemoteImage
import com.catmap.dubai.ui.components.SectionHeader
import com.catmap.dubai.ui.components.dateTime
import com.catmap.dubai.ui.components.postTitle
import com.catmap.dubai.ui.map.AreaCircle
import com.catmap.dubai.ui.map.MarkerIcons
import com.catmap.dubai.ui.map.MiniMap
import com.catmap.dubai.ui.navigation.Routes
import com.catmap.dubai.ui.sighting.newCameraUri
import com.catmap.dubai.ui.stations.SwitchRow
import kotlinx.coroutines.launch
import java.util.Calendar

@Composable
fun PostEditorScreen(nav: NavHostController, postId: String?, presetCategory: String?, presetType: String?) {
    val container = LocalContainer.current
    val vm: PostEditorViewModel = viewModel { PostEditorViewModel(container, postId, presetCategory, presetType) }
    androidx.compose.runtime.DisposableEffect(vm) {onDispose {vm.saveDraftNow()} }
    val context = LocalContext.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val me by container.session.profile.collectAsStateWithLifecycle()
    if (vm.savedDraft != null) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.draft_available)) },
            confirmButton = { TextButton(onClick = { vm.continueDraft() }) { Text(stringResource(R.string.continue_draft)) } },
            dismissButton = { TextButton(onClick = { vm.discardDraft() }) { Text(stringResource(R.string.discard_draft)) } },
        )
    }
    val settings by container.settings.settings.collectAsStateWithLifecycle(AppSettings())
    val published = stringResource(if (postId == null) R.string.post_published else R.string.saved)

    // The location picker hands its result back through this screen's saved state.
    val handle = nav.currentBackStackEntry?.savedStateHandle
    val pickedLat by (handle?.getStateFlow<Double?>(Routes.PICKED_LAT, null) ?: kotlinx.coroutines.flow.MutableStateFlow(null)).collectAsStateWithLifecycle()
    val pickedLng by (handle?.getStateFlow<Double?>(Routes.PICKED_LNG, null) ?: kotlinx.coroutines.flow.MutableStateFlow(null)).collectAsStateWithLifecycle()
    val pickedRadius by (handle?.getStateFlow<Double?>(Routes.PICKED_RADIUS, null) ?: kotlinx.coroutines.flow.MutableStateFlow(null)).collectAsStateWithLifecycle()
    LaunchedEffect(pickedLat, pickedLng, pickedRadius) {
        if (pickedLat != null && pickedLng != null) {
            vm.lat = pickedLat
            vm.lng = pickedLng
            pickedRadius?.let { vm.radius = it }
            handle?.remove<Double>(Routes.PICKED_LAT)
            handle?.remove<Double>(Routes.PICKED_LNG)
            handle?.remove<Double>(Routes.PICKED_RADIUS)
        }
    }

    val steps = PostStep.entries
    val step = steps[vm.step]
    var forward by remember { mutableStateOf(true) }
    fun go(to: Int) {
        forward = to > vm.step
        vm.step = to.coerceIn(0, steps.lastIndex)
        vm.saveDraftNow()
        // Street animals: start the same-animal check as soon as photo and place are known.
        if (vm.step > PostStep.PLACE.ordinal) vm.checkSameAnimal()
        if (steps[vm.step] == PostStep.REVIEW) vm.checkDuplicates(me)
    }
    val addedToExisting = stringResource(R.string.same_animal_added)
    var askSameAnimal by remember { mutableStateOf<SameAnimalCandidate?>(null) }
    fun publishNow() {
        val profile = me ?: return
        scope.launch {
            runCatching { vm.publish(profile, settings.privacyLevel) }
                .onSuccess { id ->
                    snackbar.showSnackbar(published)
                    nav.popBackStack()
                    if (postId == null) nav.navigate(Routes.post(id))
                }
                .onFailure { snackbar.showSnackbar(it.userMessage(context)) }
        }
    }
    fun addToExisting(candidate: SameAnimalCandidate) {
        val profile = me ?: return
        scope.launch {
            runCatching { vm.addToExisting(candidate.post, profile, settings.privacyLevel) }
                .onSuccess { id ->
                    snackbar.showSnackbar(addedToExisting)
                    nav.popBackStack()
                    nav.navigate(Routes.post(id))
                }
                .onFailure { snackbar.showSnackbar(it.userMessage(context)) }
        }
    }
    askSameAnimal?.let { c ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { askSameAnimal = null },
            title = { Text(stringResource(R.string.same_animal_title)) },
            text = { Text(stringResource(R.string.same_animal_publish_body, postTitle(c.post))) },
            confirmButton = { TextButton(onClick = { askSameAnimal = null; addToExisting(c) }) { Text(stringResource(R.string.same_animal_add)) } },
            dismissButton = {
                TextButton(onClick = { askSameAnimal = null; vm.notSameAnimal += c.post.id; publishNow() }) { Text(stringResource(R.string.same_animal_post_anyway)) }
            },
        )
    }
    BackHandler(enabled = vm.step > 0 && !vm.publishing) { go(vm.step - 1) }

    Scaffold(
        topBar = {
            Column {
                AppTopBar(
                    stringResource(if (postId == null) R.string.post_new else R.string.edit_post),
                    onBack = { nav.popBackStack() },
                )
                val progress by animateFloatAsState((vm.step + 1f) / steps.size, tween(400), label = "progress")
                LinearProgressIndicator(progress = { progress }, Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(50)))
                Text(
                    stringResource(R.string.step_x_of_y, vm.step + 1, steps.size) + " · " + stringResource(stepTitle(step)),
                    Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Row(
                    Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (vm.step > 0) {
                        OutlinedButton(onClick = { go(vm.step - 1) }, enabled = !vm.publishing, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.back)) }
                    }
                    if (step != PostStep.REVIEW) {
                        Button(onClick = { go(vm.step + 1) }, enabled = vm.canContinue() && !vm.processing, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.next))
                        }
                    } else {
                        Button(
                            onClick = {
                                // The AI said this animal is already on the map: ask before making a second post.
                                val same = vm.unresolvedSameAnimal
                                if (same != null) askSameAnimal = same else publishNow()
                            },
                            // Street posts wait for the same-animal check, so a duplicate can't slip through.
                            enabled = !vm.publishing && !vm.sameAnimalChecking && me != null,
                            modifier = Modifier.weight(1f),
                        ) {
                            if (vm.publishing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                            else Text(stringResource(if (postId == null) R.string.publish else R.string.save))
                        }
                    }
                }
            }
        },
    ) { padding ->
        if (!vm.loaded) {
            LoadingBox(Modifier.padding(padding))
            return@Scaffold
        }
        AnimatedContent(
            step,
            transitionSpec = {
                val dir = if (forward) 1 else -1
                (slideInHorizontally(tween(320)) { it / 3 * dir } + fadeIn(tween(260))) togetherWith
                    (slideOutHorizontally(tween(260)) { -it / 3 * dir } + fadeOut(tween(200)))
            },
            label = "step",
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) { s ->
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (s) {
                    PostStep.TYPE -> TypeStep(vm)
                    PostStep.PHOTOS -> PhotosStep(vm)
                    PostStep.DETAILS -> DetailsStep(vm)
                    PostStep.PLACE -> PlaceStep(vm, nav)
                    PostStep.CONTACT -> ContactStep(vm)
                    PostStep.REVIEW -> ReviewStep(vm, nav, onSameAnimal = ::addToExisting)
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

private fun stepTitle(step: PostStep) = when (step) {
    PostStep.TYPE -> R.string.step_type
    PostStep.PHOTOS -> R.string.step_photos
    PostStep.DETAILS -> R.string.step_details
    PostStep.PLACE -> R.string.step_place
    PostStep.CONTACT -> R.string.step_contact
    PostStep.REVIEW -> R.string.step_review
}

/** Big friendly tiles for the category, then the kind of animal. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TypeStep(vm: PostEditorViewModel) {
    Text(stringResource(R.string.post_what_happened), style = MaterialTheme.typography.headlineSmall)
    PostCategory.entries.chunked(2).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            row.forEach { c ->
                ChoiceTile(
                    selected = vm.category == c.key,
                    title = stringResource(c.label),
                    subtitle = stringResource(categoryHint(c)),
                    icon = categoryIcon(c),
                    color = Color(c.color),
                    modifier = Modifier.weight(1f),
                ) {
                    vm.category = c.key
                    vm.approximate = c == PostCategory.ADOPTION
                }
            }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
    }
    Text(stringResource(R.string.which_animal), style = MaterialTheme.typography.titleMedium)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AnimalType.entries.forEach { t ->
            FilterChip(vm.animalType == t.key, { vm.animalType = t.key }, label = { IconLabel(t.icon, stringResource(t.label)) })
        }
    }
}

fun categoryIcon(c: PostCategory) = CatMapIcons.category(c)

fun categoryHint(c: PostCategory) = when (c) {
    PostCategory.LOST -> R.string.hint_lost
    PostCategory.FOUND -> R.string.hint_found
    PostCategory.SPOTTED -> R.string.hint_spotted
    PostCategory.NEEDS_HELP -> R.string.hint_needs_help
    PostCategory.STREET -> R.string.hint_street
    PostCategory.ADOPTION -> R.string.hint_adoption
    PostCategory.OTHER -> R.string.hint_other
}

@Composable
fun ChoiceTile(selected: Boolean, title: String, subtitle: String, icon: Int, color: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val scale by animateFloatAsState(if (selected) 1f else 0.97f, label = "tile")
    Card(
        onClick = onClick,
        modifier = modifier.graphicsLayer { scaleX = scale; scaleY = scale },
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = if (selected) color.copy(alpha = 0.2f) else color.copy(alpha = 0.08f)),
        border = if (selected) BorderStroke(2.dp, color) else null,
    ) {
        Column(Modifier.padding(14.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            CatMapIcon(icon, Modifier.size(32.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, minLines = 2, maxLines = 2)
        }
    }
}

@Composable
private fun PhotosStep(vm: PostEditorViewModel) {
    val context = LocalContext.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val container = LocalContainer.current
    val photoError = stringResource(R.string.err_photo)
    var cameraUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(PostRepository.MAX_PHOTOS)) { uris ->
        vm.addPhotos(context, uris) { scope.launch { snackbar.showSnackbar(photoError) } }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) cameraUri?.let { vm.addPhotos(context, listOf(it)) { scope.launch { snackbar.showSnackbar(photoError) } } }
    }
    val video = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) vm.videoUri = uri }

    Text(stringResource(R.string.add_photos_title), style = MaterialTheme.typography.headlineSmall)
    Text(stringResource(R.string.add_photos_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(
            onClick = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            enabled = vm.photos.size < PostRepository.MAX_PHOTOS, modifier = Modifier.weight(1f),
        ) {
            Icon(Icons.Default.AddPhotoAlternate, null)
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.gallery))
        }
        OutlinedButton(
            onClick = { newCameraUri(context).also { cameraUri = it; camera.launch(it) } },
            enabled = vm.photos.size < PostRepository.MAX_PHOTOS, modifier = Modifier.weight(1f),
        ) {
            Icon(Icons.Default.CameraAlt, null)
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.camera))
        }
    }
    if (vm.processing) LinearProgressIndicator(Modifier.fillMaxWidth())
    if (vm.photos.isEmpty()) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(22.dp))
                .border(2.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(22.dp))
                .clickable { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.AddPhotoAlternate, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.tap_to_add_photos), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    } else {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            itemsIndexed(vm.photos, key = { _, p -> if (p is FormPhoto.Remote) p.id else (p as FormPhoto.Local).draftId }, contentType = { _, _ -> "editor-photo" }) { i, p ->
                Box(Modifier.size(132.dp).animateItem()) {
                    when (p) {
                        is FormPhoto.Remote -> RemoteImage(p.id, Modifier.fillMaxSize().clip(RoundedCornerShape(18.dp)))
                        is FormPhoto.Local -> LocalBitmap(p.preview, Modifier.fillMaxSize().clip(RoundedCornerShape(18.dp)))
                    }
                    PhotoAction(Icons.Default.Close, stringResource(R.string.remove), Modifier.align(Alignment.TopEnd)) { vm.removePhoto(p) }
                    if (i == 0) {
                        Surface(Modifier.align(Alignment.BottomStart).padding(6.dp), shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primary) {
                            Text(stringResource(R.string.cover), Modifier.padding(horizontal = 8.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary)
                        }
                    } else {
                        PhotoAction(Icons.Default.Star, stringResource(R.string.make_cover), Modifier.align(Alignment.BottomStart)) { vm.makeCover(p) }
                    }
                }
            }
        }
        Text(stringResource(R.string.photos_n_of_max, vm.photos.size, PostRepository.MAX_PHOTOS), style = MaterialTheme.typography.labelMedium)
    }
    SectionHeader(stringResource(R.string.video_optional))
    if (container.videos.enabled) {
        val hasVideo = vm.videoUri != null || vm.videoPath.isNotBlank()
        if (hasVideo) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Videocam, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.video_attached), Modifier.weight(1f))
                TextButton(onClick = { vm.removeVideo() }) { Text(stringResource(R.string.remove)) }
            }
        } else {
            OutlinedButton(onClick = { video.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) }) {
                Icon(Icons.Default.Videocam, null)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.add_video))
            }
        }
        Text(stringResource(R.string.video_limits), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        Text(stringResource(R.string.video_needs_setup), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A small round button drawn on top of a photo thumbnail. */
@Composable
private fun PhotoAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.padding(6.dp).size(32.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f))
            .clickable(onClickLabel = label, role = androidx.compose.ui.semantics.Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, tint = Color.White, modifier = Modifier.size(18.dp)) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailsStep(vm: PostEditorViewModel) {
    Text(stringResource(R.string.microchip),style=MaterialTheme.typography.titleSmall)
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        for((key,label) in listOf("yes" to R.string.yes,"no" to R.string.no,"unknown" to R.string.unknown)) FilterChip(vm.microchip==key,{vm.microchip=key},label={Text(stringResource(label))})
    }
    Text(stringResource(R.string.microchip_privacy),style=MaterialTheme.typography.bodySmall)
    val adoption = vm.categoryEnum == PostCategory.ADOPTION
    Text(stringResource(R.string.tell_us_more), style = MaterialTheme.typography.headlineSmall)
    // Shown here too, because starting from the Post menu skips the first step.
    Text(stringResource(R.string.which_animal), style = MaterialTheme.typography.labelLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AnimalType.entries.forEach { t ->
            FilterChip(vm.animalType == t.key, { vm.animalType = t.key }, label = { IconLabel(t.icon, stringResource(t.label)) })
        }
    }
    OutlinedTextField(vm.name, { vm.name = it.take(60) }, label = { Text(stringResource(R.string.animal_name_optional)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(vm.color, { vm.color = it.take(60) }, label = { Text(stringResource(R.string.color)) }, singleLine = true, modifier = Modifier.weight(1f))
        OutlinedTextField(vm.breed, { vm.breed = it.take(60) }, label = { Text(stringResource(R.string.breed)) }, singleLine = true, modifier = Modifier.weight(1f))
    }
    Text(stringResource(R.string.sex), style = MaterialTheme.typography.labelLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CatSex.entries.forEach { s -> FilterChip(vm.sex == s.key, { vm.sex = s.key }, label = { Text(stringResource(s.label)) }) }
    }
    Text(stringResource(R.string.age), style = MaterialTheme.typography.labelLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AgeGroup.entries.forEach { a -> FilterChip(vm.ageGroup == a.key, { vm.ageGroup = a.key }, label = { Text(stringResource(a.label)) }) }
    }
    OutlinedTextField(
        vm.features, { vm.features = it.take(400) }, label = { Text(stringResource(R.string.identifying_features)) },
        supportingText = { Text(stringResource(R.string.identifying_features_hint)) }, modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        vm.description, { vm.description = it.take(2000) }, label = { Text(stringResource(R.string.description)) },
        minLines = 4, modifier = Modifier.fillMaxWidth(),
        supportingText = { Text("${vm.description.length}/2000") },
    )
    if (adoption) {
        SectionHeader(stringResource(R.string.health_info))
        OutlinedTextField(vm.health, { vm.health = it.take(400) }, label = { Text(stringResource(R.string.health_notes)) }, modifier = Modifier.fillMaxWidth())
        SwitchRow(stringResource(R.string.vaccinated), vm.vaccinated) { vm.vaccinated = it }
        SwitchRow(stringResource(R.string.neutered), vm.neutered) { vm.neutered = it }
    }
    if (!vm.canContinue()) Text(stringResource(R.string.details_required), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
}

@Composable
private fun PlaceStep(vm: PostEditorViewModel, nav: NavHostController) {
    val container = LocalContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by container.settings.settings.collectAsStateWithLifecycle(AppSettings())
    var locating by remember { mutableStateOf(false) }
    val lost = vm.categoryEnum == PostCategory.LOST
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted.values.any { it }) scope.launch {
            locating = true
            container.location.current()?.let { vm.lat = it.lat; vm.lng = it.lng }
            locating = false
        }
    }
    Text(stringResource(if (vm.categoryEnum.isSearch) R.string.where_last_seen else R.string.where_is_it), style = MaterialTheme.typography.headlineSmall)
    val la = vm.lat
    val ln = vm.lng
    if (la != null && ln != null) {
        MiniMap(
            la, ln, MarkerIcons.post(vm.category, vm.animalType), LocalIsDark.current, height = 200.dp, zoom = if (vm.radius > 600) 13.5 else 15.0,
            area = if (lost && vm.radius > 0) AreaCircle(la, ln, vm.radius) else null,
            onClick = { nav.navigate(Routes.pickLocation(la, ln, vm.radius, lost)) },
        )
        Text(
            if (Dubai.contains(la, ln)) Dubai.nearest(la, ln).displayName() else stringResource(R.string.outside_dubai),
            style = MaterialTheme.typography.titleMedium,
            color = if (Dubai.contains(la, ln)) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
        )
        if (lost && vm.radius > 0) Text(stringResource(R.string.search_area_m, vm.radius.toInt()), style = MaterialTheme.typography.bodySmall)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(
            onClick = { permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) },
            enabled = !locating, modifier = Modifier.weight(1f),
        ) {
            if (locating) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
            else Icon(Icons.Default.MyLocation, null)
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.use_my_location), maxLines = 1)
        }
        OutlinedButton(onClick = { nav.navigate(Routes.pickLocation(la, ln, vm.radius, lost)) }, modifier = Modifier.weight(1f)) {
            Icon(Icons.Default.Map, null)
            Spacer(Modifier.width(6.dp))
            Text(stringResource(if (lost) R.string.pin_and_area else R.string.choose_on_map), maxLines = 1)
        }
    }
    SwitchRow(stringResource(R.string.post_approximate_location), vm.approximate) { vm.approximate = it }
    Text(
        stringResource(R.string.approximate_location_hint, LocationPrivacy.approxRadiusMeters(settings.privacyLevel)),
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (vm.categoryEnum.isSearch || vm.categoryEnum == PostCategory.NEEDS_HELP) {
        SectionHeader(stringResource(R.string.when_seen))
        OutlinedButton(onClick = { pickDateTime(context, vm.lastSeenAt) { vm.lastSeenAt = it } }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.Event, null)
            Spacer(Modifier.width(8.dp))
            Text(dateTime(vm.lastSeenAt))
        }
    }
}

/** Date then time, never in the future. */
fun pickDateTime(context: android.content.Context, initial: Long, onPicked: (Long) -> Unit) {
    val cal = Calendar.getInstance().apply { timeInMillis = initial }
    DatePickerDialog(context, { _, y, m, d ->
        cal.set(y, m, d)
        TimePickerDialog(context, { _, h, min ->
            cal.set(Calendar.HOUR_OF_DAY, h)
            cal.set(Calendar.MINUTE, min)
            onPicked(minOf(cal.timeInMillis, System.currentTimeMillis()))
        }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), android.text.format.DateFormat.is24HourFormat(context)).show()
    }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).apply {
        datePicker.maxDate = System.currentTimeMillis()
    }.show()
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ContactStep(vm: PostEditorViewModel) {
    Text(stringResource(R.string.how_contact), style = MaterialTheme.typography.headlineSmall)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ContactMethod.entries.forEach { m -> FilterChip(vm.contactMethod == m.key, { vm.contactMethod = m.key }, label = { Text(stringResource(m.label)) }) }
    }
    AnimatedVisibility(vm.contactMethod == ContactMethod.PHONE.key) {
        OutlinedTextField(
            vm.phone, { vm.phone = it.filter { c -> c.isDigit() || c in "+ -" }.take(20) }, label = { Text(stringResource(R.string.phone_number)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
    }
    AnimatedVisibility(vm.contactMethod == ContactMethod.EMAIL.key) {
        OutlinedTextField(
            vm.email, { vm.email = it.trim().take(80) }, label = { Text(stringResource(R.string.email)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
    }
    Card(colors = CardDefaults.cardColors(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f))) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Lock, null, tint = MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.contact_privacy_note), style = MaterialTheme.typography.bodySmall)
        }
    }
    if (vm.categoryEnum == PostCategory.LOST) {
        SectionHeader(stringResource(R.string.reward_optional))
        OutlinedTextField(
            vm.reward, { vm.reward = it.filter { c -> c.isDigit() }.take(6) }, label = { Text(stringResource(R.string.reward_amount_aed)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReviewStep(vm: PostEditorViewModel, nav: NavHostController, onSameAnimal: (SameAnimalCandidate) -> Unit) {
    val container = LocalContainer.current
    val me by container.session.profile.collectAsStateWithLifecycle()
    Text(stringResource(R.string.looks_good), style = MaterialTheme.typography.headlineSmall)
    // Also covers reaching Review from a restored draft or the edit shortcuts (the check is keyed, so it runs once).
    LaunchedEffect(Unit) { vm.checkSameAnimal() }
    SameAnimalCard(vm, nav, onSameAnimal)
    val preview = vm.previewPost(me)
    val localCover = (vm.photos.firstOrNull() as? FormPhoto.Local)?.preview
    PostCard(
        preview, saved = false, onFavorite = {}, onClick = {},
        cover = localCover?.let { bmp -> { m: Modifier -> LocalBitmap(bmp, m) } },
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf(PostStep.PHOTOS, PostStep.DETAILS, PostStep.PLACE, PostStep.CONTACT).forEach { st ->
            TextButton(onClick = { vm.step = st.ordinal }) {
                Icon(Icons.Default.Edit, null, Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(stepTitle(st)), maxLines = 1)
            }
        }
    }
    // Posts already offered by the AI same-animal card aren't listed again here.
    val otherDuplicates = vm.duplicates.filter { d -> vm.sameAnimal.none { it.post.id == d.id } }
    AnimatedVisibility(otherDuplicates.isNotEmpty()) {
        Card(colors = CardDefaults.cardColors(MaterialTheme.colorScheme.tertiaryContainer)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.WarningAmber, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.possible_duplicates), style = MaterialTheme.typography.titleMedium)
                }
                Text(stringResource(R.string.possible_duplicates_hint), style = MaterialTheme.typography.bodySmall)
                otherDuplicates.forEach { d ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { nav.navigate(Routes.post(d.id)) }.padding(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        com.catmap.dubai.ui.components.PostPhoto(d, Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(postTitle(d), style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                            Text(Dubai.nameOf(d.neighborhood) + " · " + com.catmap.dubai.ui.components.relativeTime(d.createdAt), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }
    Text(stringResource(R.string.post_rules_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Start)
}

/**
 * Street posts: "Is this animal already on the map?" Nearby street posts that the AI server (or, when it's
 * offline, the on-device colour check) thinks show the same animal. Confirming adds the photo to that post.
 */
@Composable
private fun SameAnimalCard(vm: PostEditorViewModel, nav: NavHostController, onSameAnimal: (SameAnimalCandidate) -> Unit) {
    val visible = vm.sameAnimal.filter { it.post.id !in vm.notSameAnimal }
    if (vm.sameAnimalChecking) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.same_animal_checking), style = MaterialTheme.typography.bodyMedium)
        }
    }
    AnimatedVisibility(visible.isNotEmpty()) {
        Card(colors = CardDefaults.cardColors(MaterialTheme.colorScheme.secondaryContainer)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.same_animal_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    stringResource(if (visible.any { it.byServer }) R.string.same_animal_hint_ai else R.string.same_animal_hint_local),
                    style = MaterialTheme.typography.bodySmall,
                )
                visible.forEach { c ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { nav.navigate(Routes.post(c.post.id)) },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            com.catmap.dubai.ui.components.PostPhoto(c.post, Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)))
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(postTitle(c.post), style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                                Text(
                                    stringResource(R.string.km_away, "%.1f".format(c.distanceKm)) + " · " + com.catmap.dubai.ui.components.relativeTime(c.post.createdAt),
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    stringResource(if (c.verdict == "same") R.string.same_animal_likely else R.string.same_animal_maybe),
                                    style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold,
                                    color = if (c.verdict == "same") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                                )
                                Text(stringResource(R.string.percent, (c.similarity * 100).toInt()), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { onSameAnimal(c) }, enabled = !vm.publishing) { Text(stringResource(R.string.same_animal_yes)) }
                            OutlinedButton(onClick = { vm.notSameAnimal += c.post.id }, enabled = !vm.publishing) { Text(stringResource(R.string.same_animal_no)) }
                        }
                    }
                }
            }
        }
    }
}
