package com.catmap.dubai.ui.explore

import com.catmap.dubai.ui.icons.CatMapIcon
import com.catmap.dubai.ui.icons.CatMapIcons
import com.catmap.dubai.ui.icons.IconLabel
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.catmap.dubai.R
import com.catmap.dubai.data.FeedFilters
import com.catmap.dubai.data.MapLayer
import com.catmap.dubai.data.distanceKm
import com.catmap.dubai.data.model.AnimalType
import com.catmap.dubai.data.model.Post
import com.catmap.dubai.data.model.PostCategory
import com.catmap.dubai.data.model.PostStatus
import com.catmap.dubai.location.Fix
import com.catmap.dubai.ui.components.SectionHeader
import com.catmap.dubai.ui.stations.SwitchRow

/** The chip labels and emoji for each map layer. */
val MapLayer.label: Int
    get() = when (this) {
        MapLayer.LOST -> R.string.cat_lost
        MapLayer.FOUND -> R.string.cat_found
        MapLayer.SPOTTED -> R.string.cat_spotted
        MapLayer.NEEDS_HELP -> R.string.cat_needs_help
        MapLayer.STREET_CATS -> R.string.layer_street_cats
        MapLayer.ADOPTION -> R.string.cat_adoption
        MapLayer.SITTING -> R.string.layer_sitting
    }

val MapLayer.icon: Int get() = CatMapIcons.layer(this)

private val MapLayer.category: PostCategory?
    get() = when (this) {
        MapLayer.LOST -> PostCategory.LOST
        MapLayer.FOUND -> PostCategory.FOUND
        MapLayer.SPOTTED -> PostCategory.SPOTTED
        MapLayer.NEEDS_HELP -> PostCategory.NEEDS_HELP
        MapLayer.STREET_CATS -> PostCategory.STREET
        MapLayer.ADOPTION -> PostCategory.ADOPTION
        MapLayer.SITTING -> null
    }

/** Whether a post passes the chip and advanced filters. Closed cases only show in lists, never on the map. */
fun FeedFilters.matches(post: Post, fix: Fix?, now: Long = System.currentTimeMillis(), includeClosed: Boolean = false): Boolean {
    if (!includeOlder && com.catmap.dubai.data.PostStaleness.hiddenByDefault(post, now)) return false
    if(microchip!=null && microchip!=post.microchip) return false
    if (!includeClosed && post.status != PostStatus.OPEN.key) return false
    if (layers.isNotEmpty()) {
        val categories = layers.mapNotNull { it.category?.key }
        if (post.category !in categories) return false
    }
    if (animalTypes.isNotEmpty() && post.animalType !in animalTypes) return false
    if (rewardOnly && post.reward <= 0) return false
    if (maxAgeDays > 0) {
        val time = post.lastSeenAt.takeIf { it > 0 } ?: post.createdAt
        if (now - time > maxAgeDays * 24L * 3600 * 1000) return false
    }
    if (maxDistanceKm > 0 && fix != null && distanceKm(fix.lat, fix.lng, post.lat, post.lng) > maxDistanceKm) return false
    return true
}

/** Community cats show for "All" and "Street cats". */
fun FeedFilters.showsCommunityCats() = layers.isEmpty() || MapLayer.STREET_CATS in layers

fun FeedFilters.showsSitters() = MapLayer.SITTING in layers

val distanceOptions = listOf(0, 1, 3, 5, 10, 25)
val ageOptions = listOf(0, 1, 3, 7, 30)

/** Advanced filters (animal type, distance, date, reward) plus, on the map, layer switches. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FilterSheet(
    filters: FeedFilters,
    onChange: (FeedFilters) -> Unit,
    onDismiss: () -> Unit,
    hasLocation: Boolean,
    extra: (@Composable () -> Unit)? = null,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.filters), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = { onChange(FeedFilters()) }) { Text(stringResource(R.string.clear_filters)) }
            }
            SectionHeader(stringResource(R.string.show))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MapLayer.entries.forEach { layer ->
                    FilterChip(
                        layer in filters.layers,
                        { onChange(filters.copy(layers = filters.layers.toggle(layer))) },
                        label = { IconLabel(layer.icon, stringResource(layer.label)) },
                    )
                }
            }
            SectionHeader(stringResource(R.string.animal_type))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AnimalType.entries.forEach { type ->
                    FilterChip(
                        type.key in filters.animalTypes,
                        { onChange(filters.copy(animalTypes = filters.animalTypes.toggle(type.key))) },
                        label = { IconLabel(type.icon, stringResource(type.label)) },
                    )
                }
            }
            SectionHeader(stringResource(R.string.distance))
            if (!hasLocation) Text(stringResource(R.string.distance_needs_location), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                distanceOptions.forEach { km ->
                    FilterChip(
                        filters.maxDistanceKm == km, { onChange(filters.copy(maxDistanceKm = km)) },
                        label = { Text(if (km == 0) stringResource(R.string.any_distance) else stringResource(R.string.within_km, km)) },
                    )
                }
            }
            SectionHeader(stringResource(R.string.date))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ageOptions.forEach { days ->
                    FilterChip(
                        filters.maxAgeDays == days, { onChange(filters.copy(maxAgeDays = days)) },
                        label = {
                            Text(
                                when (days) {
                                    0 -> stringResource(R.string.any_time)
                                    1 -> stringResource(R.string.last_24h)
                                    else -> stringResource(R.string.last_n_days, days)
                                },
                            )
                        },
                    )
                }
            }
            SwitchRow(stringResource(R.string.reward_only), filters.rewardOnly) { onChange(filters.copy(rewardOnly = it)) }
            SwitchRow(stringResource(R.string.include_older_posts), filters.includeOlder) { onChange(filters.copy(includeOlder = it)) }
            Text(stringResource(R.string.microchip),style=MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                for((key,label) in listOf(null to R.string.all,"yes" to R.string.yes,"no" to R.string.no,"unknown" to R.string.unknown)) {
                    FilterChip(filters.microchip==key,{onChange(filters.copy(microchip=key))},label={Text(stringResource(label))})
                }
            }
            extra?.invoke()
            androidx.compose.foundation.layout.Spacer(Modifier.padding(12.dp))
        }
    }
}

fun <T> Set<T>.toggle(item: T): Set<T> = if (item in this) this - item else this + item
