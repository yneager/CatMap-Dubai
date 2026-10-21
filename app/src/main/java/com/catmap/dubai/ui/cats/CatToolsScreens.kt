package com.catmap.dubai.ui.cats

import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.catmap.dubai.R
import com.catmap.dubai.ai.Similarity
import com.catmap.dubai.data.Dubai
import com.catmap.dubai.data.Loaded
import com.catmap.dubai.data.SearchTerms
import com.catmap.dubai.data.distanceKm
import com.catmap.dubai.data.safeObjects
import kotlinx.coroutines.tasks.await
import com.catmap.dubai.data.model.Cat
import com.catmap.dubai.data.model.CatSex
import com.catmap.dubai.data.model.CatSize
import com.catmap.dubai.data.model.CoatColor
import com.catmap.dubai.data.model.CoatPattern
import com.catmap.dubai.data.model.isModeratorRole
import com.catmap.dubai.data.model.labeledFrom
import com.catmap.dubai.ui.components.AppTopBar
import com.catmap.dubai.ui.components.CatThumb
import com.catmap.dubai.ui.components.ClickableCard
import com.catmap.dubai.ui.components.CoatSwatch
import com.catmap.dubai.ui.components.DropdownField
import com.catmap.dubai.ui.components.LabeledDropdown
import com.catmap.dubai.ui.components.LoadingBox
import com.catmap.dubai.ui.components.LocalContainer
import com.catmap.dubai.ui.components.ReportContentDialog
import com.catmap.dubai.ui.components.rememberRunner

// ---------------- Edit cat profile (creator and moderators) ----------------

@Composable
fun CatEditScreen(nav: NavHostController, catId: String) {
    val container = LocalContainer.current
    val runner = rememberRunner()
    val context = LocalContext.current
    val cat by produceState<Cat?>(null, catId) { value = container.cats.cat(catId) }
    val c = cat
    Scaffold(topBar = { AppTopBar(stringResource(R.string.edit_profile_cat), onBack = { nav.popBackStack() }) }) { padding ->
        if (c == null) {
            LoadingBox(Modifier.padding(padding).fillMaxSize())
            return@Scaffold
        }
        var name by rememberSaveable { mutableStateOf(c.name) }
        var description by rememberSaveable { mutableStateOf(c.description) }
        var marks by rememberSaveable { mutableStateOf(c.distinctiveMarks) }
        var coat by remember { mutableStateOf(labeledFrom(c.coatColor, CoatColor.UNKNOWN)) }
        var pattern by remember { mutableStateOf(labeledFrom(c.pattern, CoatPattern.UNKNOWN)) }
        var size by remember { mutableStateOf(labeledFrom(c.size, CatSize.UNKNOWN)) }
        var sex by remember { mutableStateOf(labeledFrom(c.sex, CatSex.UNKNOWN)) }
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(name, { name = it.take(40) }, label = { Text(stringResource(R.string.cat_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(description, { description = it.take(800) }, label = { Text(stringResource(R.string.description)) }, minLines = 3, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(marks, { marks = it.take(300) }, label = { Text(stringResource(R.string.distinctive_marks)) }, modifier = Modifier.fillMaxWidth())
            DropdownField(stringResource(R.string.coat_color), CoatColor.entries, coat, { coat = it }, leading = { CoatSwatch(it) }) { stringResource(it.label) }
            LabeledDropdown(stringResource(R.string.pattern), CoatPattern.entries, pattern, { pattern = it })
            LabeledDropdown(stringResource(R.string.size), CatSize.entries, size, { size = it })
            LabeledDropdown(stringResource(R.string.sex), CatSex.entries, sex, { sex = it })
            Button(
                enabled = name.isNotBlank() && !runner.busy,
                onClick = {
                    runner.run(R.string.saved, onSuccess = { nav.popBackStack() }) {
                        container.cats.updateCat(
                            catId,
                            mapOf(
                                "name" to name.trim(), "description" to description.trim(), "distinctiveMarks" to marks.trim(),
                                "coatColor" to coat.key, "pattern" to pattern.key, "size" to size.key, "sex" to sex.key,
                                "searchTerms" to container.cats.searchTerms(name.trim(), c.aliases),
                            ),
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.save)) }
        }
    }
}

// ---------------- Suggest merging duplicate profiles ----------------

/** Profiles further apart than this are rarely the same cat; a typed name searches everywhere. */
private const val MERGE_RADIUS_KM = 5.0

@Composable
fun MergeSuggestScreen(nav: NavHostController, catId: String) {
    val container = LocalContainer.current
    val runner = rememberRunner()
    val context = LocalContext.current
    val me by container.session.profile.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var chosen by remember { mutableStateOf<Pair<Cat, Double>?>(null) }
    val loadedSelf by produceState<Cat?>(null, catId) { value = container.cats.cat(catId) }
    val self = loadedSelf
    // Duplicates are nearly always nearby; a name typed in the search box finds profiles anywhere.
    val word = SearchTerms.words(query).maxByOrNull { it.length }.orEmpty()
    val all by produceState<List<Cat>?>(null, self?.id, word) {
        val s = self ?: return@produceState
        value = runCatching {
            if (word.length >= 2) container.cats.searchQuery(word).limit(60).get().await().safeObjects<Cat>()
            else container.cats.near(s.lat, s.lng, MERGE_RADIUS_KM)
        }.getOrDefault(emptyList()) + s
    }
    val ranked = remember(all, query) {
        if (all == null || self == null) emptyList()
        else all!!.filter { it.id != catId }
            .map { other ->
                val d = distanceKm(self.lat, self.lng, other.lat, other.lng)
                other to Similarity.score(self.features, self.coatColor, self.pattern, other.features, other.coatColor, other.pattern, d).total
            }
            .filter { (c, _) -> query.isBlank() || c.name.contains(query, true) || c.aliases.any { it.contains(query, true) } }
            .sortedByDescending { it.second }
            .take(40)
    }

    Scaffold(topBar = { AppTopBar(stringResource(R.string.suggest_merge), onBack = { nav.popBackStack() }) }) { padding ->
        if (all == null) {
            LoadingBox(Modifier.padding(padding).fillMaxSize())
            return@Scaffold
        }
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { Text(stringResource(R.string.merge_hint, self?.name ?: "")) }
            item {
                OutlinedTextField(
                    query, { query = it }, leadingIcon = { Icon(Icons.Default.Search, null) },
                    placeholder = { Text(stringResource(R.string.search_cats)) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
            }
            items(ranked, key = { it.first.id }, contentType = { "CatToolsScreens" }) { (other, score) ->
                ClickableCard(onClick = { chosen = other to score }) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        CatThumb(other.coverPhotoId, labeledFrom(other.coatColor, CoatColor.UNKNOWN), Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(other.name, fontWeight = FontWeight.SemiBold)
                            Text(Dubai.nameOf(other.neighborhood), style = MaterialTheme.typography.bodySmall)
                            LinearProgressIndicator(progress = { score.toFloat() }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(stringResource(R.string.percent, (score * 100).toInt()), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }

    val pick = chosen
    if (pick != null && self != null && me != null) {
        var keepThis by remember(pick) { mutableStateOf(self.sightingCount >= pick.first.sightingCount) }
        var reason by remember(pick) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { chosen = null },
            title = { Text(stringResource(R.string.suggest_merge)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.which_profile_to_keep))
                    listOf(true to self.name, false to pick.first.name).forEach { (value, label) ->
                        Row(
                            Modifier.fillMaxWidth().selectable(keepThis == value, role = Role.RadioButton) { keepThis = value },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(keepThis == value, null)
                            Text(label, Modifier.padding(start = 8.dp))
                        }
                    }
                    OutlinedTextField(reason, { reason = it.take(300) }, label = { Text(stringResource(R.string.why_same_cat)) }, modifier = Modifier.fillMaxWidth())
                    Text(stringResource(R.string.merge_moderator_note), style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(enabled = reason.isNotBlank() && !runner.busy, onClick = {
                    val (primary, duplicate) = if (keepThis) self to pick.first else pick.first to self
                    runner.run(R.string.merge_sent, onSuccess = { chosen = null; nav.popBackStack() }) {
                        container.cats.suggestMerge(primary, duplicate, reason, pick.second, me!!)
                    }
                }) { Text(stringResource(R.string.send)) }
            },
            dismissButton = { TextButton(onClick = { chosen = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

// ---------------- Full-screen photo ----------------

@Composable
fun PhotoViewerScreen(nav: NavHostController, photoId: String, catId: String?) {
    val container = LocalContainer.current
    val runner = rememberRunner()
    val context = LocalContext.current
    val me by container.session.profile.collectAsStateWithLifecycle()
    val cat by produceState<Cat?>(null, catId) { if (catId != null) value = container.cats.cat(catId) }
    val bitmap by produceState<Loaded<ImageBitmap>?>(null, photoId) {
        value = Loaded(container.images.bitmap(photoId, thumb = false)?.asImageBitmap())
    }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    val transform = rememberTransformableState { _, zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, 5f)
        offset = if (scale == 1f) androidx.compose.ui.geometry.Offset.Zero else offset + pan
    }
    var reporting by remember { mutableStateOf(false) }
    val canSetCover = cat != null && (cat?.createdBy == me?.uid || isModeratorRole(me?.role))

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            AppTopBar(cat?.name ?: "", onBack = { nav.popBackStack() }) {
                if (canSetCover) {
                    IconButton(onClick = { runner.run(R.string.cover_updated) { container.cats.setCoverPhoto(catId!!, photoId) } }) {
                        Icon(Icons.Default.Image, stringResource(R.string.set_as_cover))
                    }
                }
                IconButton(onClick = { reporting = true }) { Icon(Icons.Default.Flag, stringResource(R.string.report_content)) }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
            val loadedBitmap = bitmap
            val b = loadedBitmap?.value
            if (loadedBitmap == null) LoadingBox()
            else if (b == null) Text(stringResource(R.string.photo_not_found), Modifier.padding(24.dp), color = Color.White, textAlign = TextAlign.Center)
            else Image(
                b,
                contentDescription = stringResource(R.string.photo_of, cat?.name ?: ""),
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .transformable(transform)
                    .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y),
            )
        }
    }
    if (reporting) ReportContentDialog("photo", photoId, cat?.name ?: photoId) { reporting = false }
}
