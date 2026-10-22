package com.catmap.dubai.ui.matches

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.catmap.dubai.R
import com.catmap.dubai.data.Dubai
import com.catmap.dubai.data.loaded
import com.catmap.dubai.data.model.AgeGroup
import com.catmap.dubai.data.model.CatSex
import com.catmap.dubai.data.model.Post
import com.catmap.dubai.data.model.PostMatch
import com.catmap.dubai.data.model.PostStatus
import com.catmap.dubai.data.model.isModeratorRole
import com.catmap.dubai.data.model.labeledFrom
import com.catmap.dubai.ui.components.AppTopBar
import com.catmap.dubai.ui.components.EmptyState
import com.catmap.dubai.ui.components.LoadingBox
import com.catmap.dubai.ui.components.LocalContainer
import com.catmap.dubai.ui.components.RemoteImage
import com.catmap.dubai.ui.components.SectionHeader
import com.catmap.dubai.ui.components.dateTime
import com.catmap.dubai.ui.components.rememberRunner
import com.catmap.dubai.ui.navigation.Routes

/**
 * Compare a lost report with a sighting: every photo of both (tap to enlarge), the details side by side, why it was
 * suggested, and the answers. The owner answers for the lost animal, the reporter for the sighting; neither answer
 * merges or closes anything by itself.
 */
@Composable
fun MatchReviewScreen(nav: NavHostController, matchId: String) {
    val container = LocalContainer.current
    val loaded by remember(matchId) { container.matches.matchFlow(matchId).loaded() }.collectAsStateWithLifecycle(null)
    val match = loaded?.value
    // Opened from an old alert after one of the two posts was deleted: the pair is gone too.
    val orphaned by produceState(false, match?.id) { value = match != null && container.matches.orphaned(match) }
    when {
        loaded == null -> LoadingBox()
        match == null || orphaned -> Scaffold(topBar = { AppTopBar("", onBack = { nav.popBackStack() }) }) { padding ->
            EmptyState(Icons.Outlined.SearchOff, stringResource(R.string.match_unavailable), Modifier.padding(padding))
        }
        else -> MatchReview(nav, match)
    }
}

@Composable
private fun MatchReview(nav: NavHostController, match: PostMatch) {
    val container = LocalContainer.current
    val runner = rememberRunner()
    val me by container.session.profile.collectAsStateWithLifecycle()
    val uid = container.session.uid.orEmpty()
    val lost by remember(match.lostPostId) { container.posts.postFlow(match.lostPostId) }.collectAsStateWithLifecycle(null)
    val sighting by remember(match.sightingPostId) { container.posts.postFlow(match.sightingPostId) }.collectAsStateWithLifecycle(null)
    val isOwner = uid == match.lostOwnerId
    val isReporter = uid == match.sightingOwnerId
    val isMod = isModeratorRole(me?.role)
    val l = lost
    val s = sighting

    fun chat(info: Boolean) = runner.run {
        val profile = me ?: return@run
        val chatId = if (info) container.matches.requestInfo(match, l, s, profile) else container.matches.openChat(match, l, s, profile)
        nav.navigate(Routes.chat(chatId))
    }

    val answer = answerLine(match, isOwner)
    Scaffold(topBar = { AppTopBar(stringResource(R.string.match_review_title), onBack = { nav.popBackStack() }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TierPill(match)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.match_found_headline, animalName(l)), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
            }
            item { SideBySide(l, s, height = 170) { nav.navigate(Routes.photo(it)) } }
            // Every other photo of both posts, to compare angles.
            val more = (l?.photoIds.orEmpty().drop(1) + s?.photoIds.orEmpty().drop(1))
            if (more.isNotEmpty()) item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(more, key = { it }) { id ->
                        val label = stringResource(R.string.open_photo)
                        RemoteImage(id, Modifier.size(84.dp).clip(RoundedCornerShape(12.dp)).clickable(onClickLabel = label) { nav.navigate(Routes.photo(id)) }, contentDescription = label)
                    }
                }
            }
            item { MethodLine(match) }
            item {
                Card(colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceContainerLow), shape = MaterialTheme.shapes.large) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(R.string.match_why), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        matchReasons(match).forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
                        Text(stringResource(R.string.match_not_proof), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item { CompareTable(l, s) }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.match_compare_features), style = MaterialTheme.typography.bodyMedium)
                    l?.features?.takeIf { it.isNotBlank() }?.let { Text(stringResource(R.string.match_owner_features, it), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold) }
                }
            }
            answer?.let { line -> item { Text(line, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary) } }
            item { HorizontalDivider() }
            // ---- what this person can do ----
            if (isOwner && l?.status == PostStatus.OPEN.key) item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { runner.run(R.string.saved) { container.matches.setOwnerState(match, "maybe") } }, enabled = !runner.busy && match.ownerState != "maybe", modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.CheckCircle, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.match_action_maybe))
                    }
                    OutlinedButton(onClick = { runner.run(R.string.match_rejected_done) { container.matches.setOwnerState(match, "not_mine") } }, enabled = !runner.busy && match.ownerState != "not_mine", modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.Close, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.match_action_not_mine))
                    }
                    FilledTonalButton(onClick = { chat(info = true) }, enabled = !runner.busy, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.HelpOutline, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.match_action_info))
                    }
                    if (match.ownerState != "new") TextButton(onClick = { runner.run { container.matches.setOwnerState(match, "new") } }) { Text(stringResource(R.string.match_undo)) }
                }
            }
            if (isReporter) item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { runner.run(R.string.match_told_owner) { container.matches.setReporterState(match, "confirmed") } }, enabled = !runner.busy && match.reporterState != "confirmed", modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.CheckCircle, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.match_action_same))
                    }
                    OutlinedButton(onClick = { runner.run(R.string.saved) { container.matches.setReporterState(match, "not_same") } }, enabled = !runner.busy && match.reporterState != "not_same", modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.Close, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.match_action_not_same))
                    }
                }
            }
            if (isOwner || isReporter) item {
                Button(onClick = { chat(info = false) }, enabled = !runner.busy, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.AutoMirrored.Outlined.Chat, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.match_message))
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { nav.navigate(Routes.post(match.lostPostId)) }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.match_view_lost), maxLines = 1) }
                    OutlinedButton(onClick = { nav.navigate(Routes.post(match.sightingPostId)) }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.match_view_sighting), maxLines = 1) }
                }
            }
            if (isMod && match.ownerState == "reunited") item {
                OutlinedButton(onClick = { runner.run(R.string.saved) { container.admin.verifyReunion(match, me ?: return@run) } }, enabled = match.verifiedBy.isBlank(), modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Verified, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.match_verify))
                }
            }
            item {
                Text(
                    stringResource(R.string.match_suggested_at, dateTime(match.createdAt)),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** The two posts' details next to each other, so differences stand out. */
@Composable
private fun CompareTable(lost: Post?, sighting: Post?) {
    val rows = listOf(
        R.string.colour to (lost?.color to sighting?.color),
        R.string.breed to (lost?.breed to sighting?.breed),
        R.string.sex to (lost?.sex?.let { stringResource(labeledFrom(it, CatSex.UNKNOWN).label) } to sighting?.sex?.let { stringResource(labeledFrom(it, CatSex.UNKNOWN).label) }),
        R.string.age to (lost?.ageGroup?.let { stringResource(labeledFrom(it, AgeGroup.UNKNOWN).label) } to sighting?.ageGroup?.let { stringResource(labeledFrom(it, AgeGroup.UNKNOWN).label) }),
        R.string.identifying_features to (lost?.features to sighting?.features),
        R.string.area to (Dubai.nameOf(lost?.neighborhood) to Dubai.nameOf(sighting?.neighborhood)),
        R.string.match_when to (lost?.let { dateTime(it.lastSeenAt.takeIf { t -> t > 0 } ?: it.createdAt) } to sighting?.let { dateTime(it.lastSeenAt.takeIf { t -> t > 0 } ?: it.createdAt) }),
    )
    Column {
        SectionHeader(stringResource(R.string.match_compare_details))
        Row {
            Spacer(Modifier.weight(0.8f))
            Text(stringResource(R.string.match_lost_photo), Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text(stringResource(R.string.match_sighting_photo), Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
        rows.forEach { (label, values) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text(stringResource(label), Modifier.weight(0.8f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(values.first?.takeIf { it.isNotBlank() } ?: "—", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Text(values.second?.takeIf { it.isNotBlank() } ?: "—", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            }
            HorizontalDivider()
        }
    }
}
