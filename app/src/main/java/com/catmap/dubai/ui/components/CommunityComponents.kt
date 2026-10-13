package com.catmap.dubai.ui.components

import androidx.core.net.toUri
import com.catmap.dubai.ui.icons.CatMapIcon
import com.catmap.dubai.ui.icons.CatMapIcons
import com.catmap.dubai.ui.icons.IconLabel
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.automirrored.filled.StarHalf
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.NearMe
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.catmap.dubai.R
import com.catmap.dubai.data.Dubai
import com.catmap.dubai.data.distanceKm
import com.catmap.dubai.data.model.AdoptionStatus
import com.catmap.dubai.data.model.AgeGroup
import com.catmap.dubai.data.model.AnimalType
import com.catmap.dubai.data.model.CatSex
import com.catmap.dubai.data.model.Post
import com.catmap.dubai.data.model.PostCategory
import com.catmap.dubai.data.model.PostStatus
import com.catmap.dubai.data.model.labeledFrom
import java.text.NumberFormat
import kotlin.math.roundToInt

// ---------- Text helpers ----------

/** "Lost cat", "Dog for adoption"… */
@Composable
fun postHeadline(post: Post): String {
    // "Lost cat", not "Lost Cat": the animal is lower-cased inside the phrase, the phrase starts with a capital.
    val animal = stringResource(labeledFrom(post.animalType, AnimalType.OTHER).label).lowercase()
    return stringResource(labeledFrom(post.category, PostCategory.OTHER).headline, animal).replaceFirstChar { it.uppercase() }
}

/** The post's name when it has one, otherwise its headline. */
@Composable
fun postTitle(post: Post): String = post.name.ifBlank { postHeadline(post) }

fun formatNumber(value: Long): String = NumberFormat.getIntegerInstance().format(value)

/** The app's current locale, read so that a language switch recomposes. */
@Composable
@ReadOnlyComposable
fun currentLocale(): java.util.Locale = LocalConfiguration.current.locales[0]

@Composable
fun distanceText(km: Double): String = when {
    km < 0.05 -> stringResource(R.string.distance_here)
    km < 1 -> stringResource(R.string.distance_m, ((km * 1000) / 10).roundToInt() * 10)
    km < 10 -> stringResource(R.string.distance_km, String.format(currentLocale(), "%.1f", km))
    else -> stringResource(R.string.distance_km, km.roundToInt().toString())
}

/** Distance from the last known position, or null when it isn't known (no permission yet). */
@Composable
fun rememberDistanceKm(lat: Double, lng: Double): Double? {
    val fix by LocalContainer.current.location.last.collectAsStateWithLifecycle()
    val f = fix ?: return null
    if (lat == 0.0 && lng == 0.0) return null
    return remember(f, lat, lng) { distanceKm(f.lat, f.lng, lat, lng) }
}

// ---------- Pills ----------

@Composable
fun CategoryPill(category: String, modifier: Modifier = Modifier, solid: Boolean = true) {
    val c = labeledFrom(category, PostCategory.OTHER)
    val color = Color(c.color)
    Surface(modifier, shape = RoundedCornerShape(50), color = if (solid) color else color.copy(alpha = 0.14f)) {
        Text(
            stringResource(c.label),
            Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = if (solid) Color.White else color,
        )
    }
}

@Composable
fun RewardPill(amount: Long, modifier: Modifier = Modifier) {
    Surface(modifier, shape = RoundedCornerShape(50), color = Color(0xFFFFC233)) {
        Text(
            stringResource(R.string.reward_aed, formatNumber(amount)),
            Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF3A2A00),
        )
    }
}

@Composable
fun StatusBadge(post: Post, modifier: Modifier = Modifier, onImage: Boolean = false) {
    if (post.category == PostCategory.ADOPTION.key) {
        val s = labeledFrom(post.adoptionStatus, AdoptionStatus.AVAILABLE)
        StatusPill(stringResource(s.label), Color(s.color), modifier, solid = onImage)
    } else if (post.status != PostStatus.OPEN.key) {
        val s = labeledFrom(post.status, PostStatus.OPEN)
        StatusPill(stringResource(s.label), Color(s.color), modifier, solid = onImage)
    }
}

@Composable
private fun MetaLine(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    if (text.isBlank()) return
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(14.dp), tint = color)
        Spacer(Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ---------- Favourite button ----------

/** A heart that pops when toggled. */
@Composable
fun FavoriteButton(saved: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier, onImage: Boolean = false) {
    val scale by animateFloatAsState(if (saved) 1.15f else 1f, spring(dampingRatio = Spring.DampingRatioHighBouncy, stiffness = Spring.StiffnessMedium), label = "heart")
    val description = stringResource(if (saved) R.string.remove_favorite else R.string.add_favorite)
    IconButton(onClick = onToggle, modifier = modifier.semantics { contentDescription = description }) {
        Box(
            if (onImage) Modifier.size(36.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.35f)) else Modifier,
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (saved) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                null,
                Modifier.scale(scale),
                tint = if (saved) Color(0xFFFF4D6D) else if (onImage) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ---------- Cards ----------

/** The main feed card: a large photo with the category and reward on top and the details below. */
@Composable
fun PostCard(
    post: Post,
    saved: Boolean,
    onFavorite: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** Replaces the cover photo, e.g. with a photo picked on this phone that isn't uploaded yet. */
    cover: (@Composable (Modifier) -> Unit)? = null,
) {
    val distance = rememberDistanceKm(post.lat, post.lng)
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 10f)) {
            // Lists use thumbnails; only detail/gallery and posters request full photos.
            if (cover != null) cover(Modifier.fillMaxSize()) else PostPhoto(post, Modifier.fillMaxSize(), thumb = true)
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(0.55f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.55f)),
                ),
            )
            Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CategoryPill(post.category)
                if (post.reward > 0) RewardPill(post.reward)
            }
            FavoriteButton(saved, onFavorite, Modifier.align(Alignment.TopEnd).padding(4.dp), onImage = true)
            Column(Modifier.align(Alignment.BottomStart).padding(14.dp)) {
                Text(postTitle(post), style = MaterialTheme.typography.titleLarge, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (post.name.isNotBlank()) Text(postHeadline(post), style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.85f))
            }
        }
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val text = post.description.ifBlank { post.features }
            if (text.isNotBlank()) Text(text, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                MetaLine(Icons.Outlined.Place, Dubai.nameOf(post.neighborhood))
                MetaLine(Icons.Outlined.Schedule, relativeTime(post.lastSeenAt.takeIf { it > 0 } ?: post.createdAt))
                if (distance != null) MetaLine(Icons.Outlined.NearMe, distanceText(distance), MaterialTheme.colorScheme.primary)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusBadge(post)
                Spacer(Modifier.weight(1f))
                if (post.updatesCount > 0) MetaLine(Icons.Outlined.ChatBubbleOutline, stringResource(R.string.updates_n, post.updatesCount))
            }
        }
    }
}

/** A compact two-column card for adoption listings and favourites. */
@Composable
fun PostGridCard(post: Post, saved: Boolean, onFavorite: () -> Unit, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val distance = rememberDistanceKm(post.lat, post.lng)
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
            PostPhoto(post, Modifier.fillMaxSize(), thumb = true)
            FavoriteButton(saved, onFavorite, Modifier.align(Alignment.TopEnd), onImage = true)
            StatusBadge(post, Modifier.align(Alignment.BottomStart).padding(8.dp), onImage = true)
        }
        Column(Modifier.padding(10.dp)) {
            Text(postTitle(post), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val details = listOfNotNull(
                labeledFrom(post.sex, CatSex.UNKNOWN).takeIf { it != CatSex.UNKNOWN }?.let { stringResource(it.label) },
                labeledFrom(post.ageGroup, AgeGroup.UNKNOWN).takeIf { it != AgeGroup.UNKNOWN }?.let { stringResource(it.label) },
            ).joinToString(" · ")
            if (details.isNotBlank()) Text(details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                Dubai.nameOf(post.neighborhood) + (distance?.let { " · " + distanceText(it) } ?: ""),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** A post's cover photo, or a soft tile with the animal's emoji when there is none. */
@Composable
fun PostPhoto(post: Post, modifier: Modifier = Modifier, thumb: Boolean = true) {
    val cover = post.photoIds.firstOrNull()
    if (cover != null) {
        RemoteImage(cover, modifier, thumb = thumb, contentDescription = postTitle(post))
    } else {
        val c = Color(labeledFrom(post.category, PostCategory.OTHER).color)
        Box(modifier.background(Brush.linearGradient(listOf(c.copy(alpha = 0.35f), c.copy(alpha = 0.12f)))), contentAlignment = Alignment.Center) {
            CatMapIcon(labeledFrom(post.animalType, AnimalType.OTHER).icon, Modifier.size(48.dp))
        }
    }
}

/** Swipeable photos with page dots. */
@Composable
fun PhotoPager(photoIds: List<String>, modifier: Modifier = Modifier, onOpen: (String) -> Unit = {}) {
    if (photoIds.isEmpty()) return
    val state = rememberPagerState { photoIds.size }
    Box(modifier) {
        HorizontalPager(state, Modifier.fillMaxSize()) { page ->
            RemoteImage(
                photoIds[page], Modifier.fillMaxSize().clickable { onOpen(photoIds[page]) }, thumb = false,
                contentDescription = stringResource(R.string.photo_n, page + 1),
            )
        }
        if (photoIds.size > 1) {
            Row(
                Modifier.align(Alignment.BottomCenter).padding(10.dp).clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.3f)).padding(horizontal = 8.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                repeat(photoIds.size) { i ->
                    val selected = state.currentPage == i
                    val width by animateFloatAsState(if (selected) 18f else 7f, tween(250), label = "dot")
                    Box(Modifier.height(7.dp).width(width.dp).clip(RoundedCornerShape(50)).background(if (selected) Color.White else Color.White.copy(alpha = 0.55f)))
                }
            }
        }
    }
}

// ---------- Ratings ----------

@Composable
fun RatingStars(rating: Double, modifier: Modifier = Modifier, size: Dp = 16.dp) {
    Row(modifier) {
        for (i in 1..5) {
            val icon = when {
                rating >= i -> Icons.Filled.Star
                rating >= i - 0.5 -> Icons.AutoMirrored.Filled.StarHalf
                else -> Icons.Filled.StarBorder
            }
            Icon(icon, null, Modifier.size(size), tint = Color(0xFFFFB300))
        }
    }
}

@Composable
fun RatingInput(value: Int, onChange: (Int) -> Unit) {
    Row {
        for (i in 1..5) {
            val scale by animateFloatAsState(if (i <= value) 1.15f else 1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "star")
            IconButton(onClick = { onChange(i) }) {
                Icon(
                    if (i <= value) Icons.Filled.Star else Icons.Filled.StarBorder,
                    stringResource(R.string.rating_n, i),
                    Modifier.size(34.dp).scale(scale),
                    tint = Color(0xFFFFB300),
                )
            }
        }
    }
}

// ---------- Loading skeletons ----------

/** A soft moving highlight used on placeholder shapes while content loads. */
fun Modifier.shimmer(): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val x by transition.animateFloat(
        -400f, 1200f,
        infiniteRepeatable(tween(1300, easing = FastOutSlowInEasing), RepeatMode.Restart), label = "shimmer-x",
    )
    val base = MaterialTheme.colorScheme.surfaceVariant
    background(
        Brush.linearGradient(
            listOf(base.copy(alpha = 0.6f), base.copy(alpha = 0.25f), base.copy(alpha = 0.6f)),
            start = Offset(x, 0f), end = Offset(x + 400f, 300f),
        ),
    )
}

@Composable
fun SkeletonCard(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 10f).shimmer())
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.fillMaxWidth(0.7f).height(14.dp).clip(RoundedCornerShape(6.dp)).shimmer())
            Box(Modifier.fillMaxWidth(0.45f).height(12.dp).clip(RoundedCornerShape(6.dp)).shimmer())
        }
    }
}

@Composable
fun SkeletonRow(modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(52.dp).clip(CircleShape).shimmer())
        Spacer(Modifier.width(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.fillMaxWidth(0.6f).height(14.dp).clip(RoundedCornerShape(6.dp)).shimmer())
            Box(Modifier.fillMaxWidth(0.4f).height(12.dp).clip(RoundedCornerShape(6.dp)).shimmer())
        }
    }
}

// ---------- Offline ----------

/** Slides in at the top while there is no connection; cached data keeps showing underneath. */
@Composable
fun OfflineBanner(online: Boolean, modifier: Modifier = Modifier) {
    AnimatedVisibility(!online, modifier, enter = expandVertically(), exit = shrinkVertically()) {
        Row(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.inverseSurface).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.CloudOff, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.inverseOnSurface)
            Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.offline_banner), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.inverseOnSurface)
        }
    }
}

/** Opens the system dialer or mail app; nothing happens if no app can handle it. */
fun openContact(context: android.content.Context, uri: String) {
    runCatching {
        context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, uri.toUri()).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
