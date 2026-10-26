package com.catmap.dubai.ui.matches

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.catmap.dubai.R
import com.catmap.dubai.data.Dubai
import com.catmap.dubai.data.model.AnimalType
import com.catmap.dubai.data.model.MatchTier
import com.catmap.dubai.data.model.Post
import com.catmap.dubai.data.model.PostCategory
import com.catmap.dubai.data.model.PostMatch
import com.catmap.dubai.data.model.PostStatus
import com.catmap.dubai.data.model.PostUpdate
import com.catmap.dubai.data.model.Resolution
import com.catmap.dubai.data.model.labeledFrom
import com.catmap.dubai.ui.components.LocalContainer
import com.catmap.dubai.ui.components.RemoteImage
import com.catmap.dubai.ui.components.SectionHeader
import com.catmap.dubai.ui.components.StatusPill
import com.catmap.dubai.ui.components.dateOnly
import com.catmap.dubai.ui.components.dateTime
import com.catmap.dubai.ui.components.distanceText
import com.catmap.dubai.ui.components.postTitle
import com.catmap.dubai.ui.components.rememberRunner
import com.catmap.dubai.ui.navigation.Routes
import kotlin.math.abs
import kotlin.math.roundToInt

// ---------- small pieces ----------

/** The strength label as a coloured pill (words, never a percentage). */
@Composable
fun TierPill(match: PostMatch, modifier: Modifier = Modifier) {
    val tier = match.tierEnum
    StatusPill(stringResource(tier.label), Color(tier.color), modifier, solid = tier == MatchTier.STRONG)
}

/** The animal's name, or "your pet"-style wording when it has none. */
@Composable
fun animalName(post: Post?): String = post?.name?.takeIf { it.isNotBlank() }
    ?: stringResource(labeledFrom(post?.animalType, AnimalType.OTHER).label)

/** strong 0, possible 1, weak 2, anything else 3: for ordering equal scores. */
fun tierRank(tier: String): Int = when (tier) {
    "strong" -> 0
    "possible" -> 1
    "weak" -> 2
    else -> 3
}

@Composable
private fun daysText(days: Double): String {
    val d = abs(days)
    return if (d < 1) stringResource(R.string.days_less_than_one) else stringResource(R.string.days_n, d.roundToInt())
}

/** One sentence per reason the match was suggested, in plain words. */
@Composable
fun matchReasons(match: PostMatch): List<String> = match.reasons.mapNotNull { r ->
    when (r) {
        "photo_very_similar" -> stringResource(R.string.reason_photo_very_similar)
        "photo_similar" -> stringResource(R.string.reason_photo_similar)
        "photo_somewhat_similar" -> stringResource(R.string.reason_photo_somewhat_similar)
        "photo_different" -> stringResource(R.string.reason_photo_different)
        "colour_only" -> stringResource(R.string.reason_colour_only)
        "no_photo" -> stringResource(R.string.reason_no_photo)
        "same_colour_words" -> stringResource(R.string.reason_same_colour_words)
        "near" -> stringResource(R.string.reason_near, distanceText(match.distanceKm))
        "in_range" -> stringResource(R.string.reason_in_range, distanceText(match.distanceKm))
        "far" -> stringResource(R.string.reason_far, distanceText(match.distanceKm))
        "recent" -> stringResource(R.string.reason_recent, daysText(match.daysApart))
        "long_after" -> stringResource(R.string.reason_long_after, daysText(match.daysApart))
        "before_lost" -> stringResource(R.string.reason_before_lost)
        "like_rejected" -> stringResource(R.string.reason_like_rejected)
        "reporter_rejected" -> stringResource(R.string.reason_reporter_rejected)
        "reporter_linked" -> stringResource(R.string.reason_reporter_linked)
        else -> null
    }
}

/** What the other person said, from the viewer's side. */
@Composable
fun answerLine(match: PostMatch, viewerIsOwner: Boolean): String? = when {
    match.ownerState == "reunited" -> stringResource(R.string.match_reunited_badge)
    viewerIsOwner && match.ownerState == "maybe" -> stringResource(R.string.match_you_said_maybe)
    viewerIsOwner && match.ownerState == "not_mine" -> stringResource(R.string.match_you_said_no)
    viewerIsOwner && match.reporterState == "confirmed" -> stringResource(R.string.match_reporter_said_same)
    viewerIsOwner && match.reporterState == "not_same" -> stringResource(R.string.reason_reporter_rejected)
    !viewerIsOwner && match.ownerState == "maybe" -> stringResource(R.string.match_owner_said_maybe)
    !viewerIsOwner && match.ownerState == "not_mine" -> stringResource(R.string.match_owner_said_no)
    !viewerIsOwner && match.ownerState == "need_info" -> stringResource(R.string.match_owner_wants_info)
    else -> null
}

@Composable
private fun rememberPost(id: String): Post? {
    val container = LocalContainer.current
    val post by remember(id) { container.posts.postFlow(id) }.collectAsStateWithLifecycle(null)
    return post
}

/** Two photos side by side: the lost animal and the sighting. */
@Composable
fun SideBySide(lost: Post?, sighting: Post?, modifier: Modifier = Modifier, height: Int = 120, onOpen: ((String) -> Unit)? = null) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(lost to R.string.match_lost_photo, sighting to R.string.match_sighting_photo).forEach { (p, label) ->
            val photo = p?.photoIds?.firstOrNull().orEmpty()
            val name = stringResource(label)
            Column(Modifier.weight(1f)) {
                RemoteImage(
                    photo,
                    Modifier.fillMaxWidth().height(height.dp).clip(RoundedCornerShape(14.dp))
                        .then(if (onOpen != null && photo.isNotBlank()) Modifier.clickable(onClickLabel = name) { onOpen(photo) } else Modifier),
                    contentDescription = name,
                )
                Text(name, Modifier.padding(top = 4.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ---------- the list on a report ----------

/**
 * "Possible matches" on a lost report or a sighting, for the two people involved: live from `matches`, best first,
 * with a waiting state while the PC checks a new post, an offline note when the phone did the matching, and weak
 * ones folded away.
 */
@Composable
fun PostMatchesSection(nav: NavHostController, post: Post, matches: List<PostMatch>, history: com.catmap.dubai.ui.components.LiveHistory<PostMatch>) {
    val container = LocalContainer.current
    val uid = container.session.uid.orEmpty()
    val me by container.session.profile.collectAsStateWithLifecycle()
    val all = matches
    val pcOnline by produceState<Boolean?>(null, post.id) { value = container.aiServer.health() != null }
    var showWeak by rememberSaveable { mutableStateOf(false) }
    val isLost = post.category == PostCategory.LOST.key
    val isOwner = post.ownerId == uid
    // While the PC is off, the owner's phone pairs the report with sightings posted since (case: sighting first).
    LaunchedEffect(post.id, me?.uid, pcOnline) {
        val profile = me ?: return@LaunchedEffect
        if (pcOnline == false && isOwner) runCatching { container.matches.reconcile(post, profile) }
    }
    val list = all
    // A pair someone turned down leaves their list (it stays stored, so it is never suggested again).
    // Best first: score, then the stronger tier, then the closer photo (pages arrive by score, so equal scores need it).
    val visible = list.filter {
        it.visible && !(it.lostOwnerId == uid && it.rejected) && !(it.sightingOwnerId == uid && it.reporterState == "not_same")
    }.sortedWith(compareByDescending<PostMatch> { it.score }.thenBy { tierRank(it.tier) }.thenByDescending { it.visual })
    val shown = visible.filter { it.tier != MatchTier.WEAK.key || it.ownerState != "new" || it.reporterState != "new" }
    val weak = visible - shown.toSet()
    val fresh = System.currentTimeMillis() - post.createdAt < 3 * 60_000
    if (visible.isEmpty() && !isOwner && history.page.end && !history.page.loading) return
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(stringResource(R.string.possible_post_matches))
        when {
            visible.isEmpty() && history.page.loading -> CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            visible.isEmpty() && fresh && pcOnline == true -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text(stringResource(if (isLost) R.string.matches_checking else R.string.matches_checking_sighting), style = MaterialTheme.typography.bodyMedium)
            }
            visible.isEmpty() && history.page.end -> Text(
                stringResource(if (isLost) R.string.matches_none_lost else R.string.matches_none_sighting),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (visible.any { it.source == "device" }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.CloudOff, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.matches_offline), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        (shown + if (showWeak) weak else emptyList()).forEach { m ->
            MatchCard(m, viewerIsOwner = m.lostOwnerId == uid, onClick = { nav.navigate(Routes.match(m.id)) })
        }
        if (weak.isNotEmpty() && !showWeak) {
            TextButton(onClick = { showWeak = true }) { Text(stringResource(R.string.show_weak_matches, weak.size)) }
        }
        com.catmap.dubai.ui.components.HistoryMore(history)
    }
}

/** One suggestion: both photos, strength, where/when, the other person's answer and the next step. */
@Composable
fun MatchCard(match: PostMatch, viewerIsOwner: Boolean, onClick: () -> Unit) {
    val lost = rememberPost(match.lostPostId)
    val sighting = rememberPost(match.sightingPostId)
    if (lost == null || sighting == null) return
    val found = lost?.status == PostStatus.REUNITED.key && lost.resolvedMatchId != match.id
    val other = if (viewerIsOwner) sighting else lost
    val headline = if (viewerIsOwner) stringResource(R.string.match_seen_near, Dubai.nameOf(sighting?.neighborhood).ifBlank { "-" })
    else stringResource(R.string.match_found_headline, animalName(lost)) + " — " + stringResource(R.string.match_lost_near, Dubai.nameOf(lost?.neighborhood).ifBlank { "-" })
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SideBySide(lost, sighting, height = 110)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (found) Text(stringResource(R.string.match_owner_found_animal)) else TierPill(match)
                Spacer(Modifier.width(8.dp))
                Text(headline, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, maxLines = 2)
            }
            Text(
                listOfNotNull(
                    other?.let { dateOnly(it.lastSeenAt.takeIf { t -> t > 0 } ?: it.createdAt) },
                    stringResource(R.string.match_distance_apart, distanceText(match.distanceKm)),
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            answerLine(match, viewerIsOwner)?.let { Text(it, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold) }
            Text(stringResource(R.string.match_review), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
}

// ---------- reunion ----------

/** The banner at the top of a reunited report: when, how, and how it was confirmed. */
@Composable
fun ReunionBanner(post: Post, credited: PostMatch?) {
    if (post.status != PostStatus.REUNITED.key) return
    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = Color(0xFF2E7D32).copy(alpha = 0.12f)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Favorite, null, tint = Color(0xFF2E7D32))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.reunited_banner, dateOnly(post.resolvedAt.takeIf { it > 0 } ?: post.updatedAt)), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                when (post.resolution) {
                    Resolution.CATMAP.key -> Text(stringResource(R.string.reunited_via_catmap), style = MaterialTheme.typography.bodySmall)
                    Resolution.HOME.key -> Text(stringResource(R.string.reunited_home), style = MaterialTheme.typography.bodySmall)
                }
                // Owner-reported vs. independently confirmed, so a reunion claim stays auditable.
                Text(
                    stringResource(
                        when {
                            credited?.verifiedBy?.isNotBlank() == true -> R.string.reunion_verified
                            credited?.reporterState == "confirmed" -> R.string.reunion_confirmed
                            else -> R.string.reunion_owner_reported
                        },
                    ),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * "Mark as reunited": how the animal was found (through a CatMap sighting, which one, or on its own), then one
 * transaction. Works with or without any match, so an owner can always close the report honestly.
 */
@Composable
fun ReuniteDialog(post: Post, matches: List<PostMatch>, onDismiss: () -> Unit, onDone: () -> Unit) {
    val container = LocalContainer.current
    val runner = rememberRunner()
    val me by container.session.profile.collectAsStateWithLifecycle()
    val candidates = matches.filter { it.lostPostId == post.id && it.ownerState != "not_mine" }
    var how by rememberSaveable { mutableStateOf(if (candidates.any { it.ownerState == "maybe" }) Resolution.CATMAP.key else if (candidates.isEmpty()) Resolution.HOME.key else Resolution.CATMAP.key) }
    var chosen by rememberSaveable { mutableStateOf(candidates.firstOrNull { it.ownerState == "maybe" }?.id ?: candidates.firstOrNull()?.id) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reunite_title, animalName(post))) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.reunite_how), style = MaterialTheme.typography.labelLarge)
                Resolution.entries.forEach { r ->
                    Row(
                        Modifier.fillMaxWidth().selectable(how == r.key, role = Role.RadioButton) { how = r.key },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(how == r.key, null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(r.label))
                    }
                }
                if (how == Resolution.CATMAP.key && candidates.isNotEmpty()) {
                    Text(stringResource(R.string.reunite_which), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp))
                    candidates.forEach { m ->
                        val s = rememberPost(m.sightingPostId)
                        Row(
                            Modifier.fillMaxWidth().selectable(chosen == m.id, role = Role.RadioButton) { chosen = m.id },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(chosen == m.id, null)
                            Spacer(Modifier.width(8.dp))
                            RemoteImage(s?.photoIds?.firstOrNull().orEmpty(), Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)))
                            Spacer(Modifier.width(8.dp))
                            Text("@" + s?.ownerName.orEmpty() + " · " + Dubai.nameOf(s?.neighborhood), style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = !runner.busy && me != null, onClick = {
                runner.run(onSuccess = onDone) {
                    val resolution = labeledFrom(how, Resolution.OTHER)
                    container.matches.reunite(post, candidates.firstOrNull { it.id == chosen }, resolution, me ?: return@run)
                }
            }) { Text(stringResource(R.string.reunite_confirm)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** The confirmation shown once the report is marked reunited. */
@Composable
fun ReunitedCelebration(post: Post, onClose: () -> Unit) {
    val context = LocalContext.current
    val shareText = stringResource(R.string.reunited_share_text, animalName(post))
    val pop by animateFloatAsState(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "reunited")
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth().padding(24.dp), shape = RoundedCornerShape(28.dp)) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    Modifier.size(84.dp).scale(pop).clip(CircleShape).background(Color(0xFF2E7D32).copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Outlined.CheckCircle, null, Modifier.size(52.dp), tint = Color(0xFF2E7D32)) }
                RemoteImage(post.photoIds.firstOrNull().orEmpty(), Modifier.fillMaxWidth().aspectRatio(1.6f).clip(RoundedCornerShape(18.dp)), thumb = false)
                Text(stringResource(R.string.reunited_title, animalName(post)), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                Text(stringResource(R.string.reunited_body), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = {
                    // Only the animal's name: no place, no contact, no photo of the owner.
                    val send = android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, shareText)
                    context.startActivity(android.content.Intent.createChooser(send, null))
                }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Share, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.reunited_share))
                }
                Button(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.close)) }
            }
        }
    }
}

// ---------- history ----------

/** A report's history from what's already stored: posted, sightings and updates, matches and answers, closing. */
@Composable
fun ReportHistory(post: Post, updates: List<PostUpdate>, matches: List<PostMatch>) {
    data class Event(val at: Long, val text: String)
    val events = buildList {
        add(Event(post.createdAt, stringResource(if (post.category == PostCategory.LOST.key) R.string.history_reported_lost else R.string.history_posted)))
        updates.forEach { add(Event(it.createdAt, stringResource(R.string.history_update, "@" + it.byName))) }
        matches.filter { it.visible }.forEach { m ->
            add(Event(m.createdAt, stringResource(R.string.history_match, stringResource(m.tierEnum.label))))
            if (m.reviewedAt > 0) answerLine(m, viewerIsOwner = true)?.let { add(Event(m.reviewedAt, it)) }
        }
        if (post.status != PostStatus.OPEN.key && post.resolvedAt > 0) {
            add(Event(post.resolvedAt, stringResource(R.string.history_closed, stringResource(labeledFrom(post.status, PostStatus.OPEN).label))))
        }
    }.sortedByDescending { it.at }
    Column(Modifier.padding(horizontal = 16.dp)) {
        SectionHeader(stringResource(R.string.report_history))
        events.forEach { e ->
            Row(Modifier.padding(vertical = 4.dp).semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.Top) {
                Box(Modifier.padding(top = 6.dp).size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(e.text, style = MaterialTheme.typography.bodyMedium)
                    Text(dateTime(e.at), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** A small "checked by AI / by colour on a phone" line, so people know what the strength is based on. */
@Composable
fun MethodLine(match: PostMatch) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(if (match.method == "embedding") Icons.Outlined.AutoAwesome else Icons.Outlined.CloudOff, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(6.dp))
        Text(
            stringResource(if (match.method == "embedding") R.string.match_ai_label else R.string.match_device_label),
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
