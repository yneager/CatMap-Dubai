package com.catmap.dubai.ui.cats

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.catmap.dubai.R
import com.catmap.dubai.data.model.Cat
import com.catmap.dubai.data.model.CoatColor
import com.catmap.dubai.data.model.HealthStatus
import com.catmap.dubai.data.model.Labeled
import com.catmap.dubai.data.model.TnrStatus
import com.catmap.dubai.data.model.labeledFrom
import com.catmap.dubai.ui.components.LabeledChips

enum class Period(@StringRes override val label: Int, val days: Int?) : Labeled {
    ANY(R.string.period_any, null),
    DAY(R.string.period_day, 1),
    WEEK(R.string.period_week, 7),
    MONTH(R.string.period_month, 30),
    QUARTER(R.string.period_quarter, 90);

    override val key: String get() = name.lowercase()
}

data class CatFilters(
    val coats: Set<CoatColor> = emptySet(),
    val period: Period = Period.ANY,
    val health: Set<HealthStatus> = emptySet(),
    val tnr: Set<TnrStatus> = emptySet(),
    val neighborhood: String? = null,
) {
    val activeCount: Int
        get() = (if (coats.isNotEmpty()) 1 else 0) + (if (period != Period.ANY) 1 else 0) +
            (if (health.isNotEmpty()) 1 else 0) + (if (tnr.isNotEmpty()) 1 else 0)

    fun matches(cat: Cat, now: Long = System.currentTimeMillis()): Boolean {
        if (coats.isNotEmpty() && labeledFrom(cat.coatColor, CoatColor.UNKNOWN) !in coats) return false
        if (health.isNotEmpty() && labeledFrom(cat.healthStatus, HealthStatus.UNKNOWN) !in health) return false
        if (tnr.isNotEmpty() && labeledFrom(cat.tnrStatus, TnrStatus.UNKNOWN) !in tnr) return false
        if (period.days != null && cat.lastSeen < now - period.days * 86_400_000L) return false
        if (neighborhood != null && cat.neighborhood != neighborhood && neighborhood !in cat.neighborhoods) return false
        return true
    }
}

/** Bottom sheet for filtering by coat colour, date and health. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterSheet(initial: CatFilters, onApply: (CatFilters) -> Unit, onDismiss: () -> Unit) {
    var filters by remember { mutableStateOf(initial) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(stringResource(R.string.filters), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.filter_coat), style = MaterialTheme.typography.titleSmall)
            LabeledChips(CoatColor.entries.filter { it != CoatColor.UNKNOWN }, filters.coats, { c ->
                filters = filters.copy(coats = if (c in filters.coats) filters.coats - c else filters.coats + c)
            })
            Text(stringResource(R.string.filter_last_seen), style = MaterialTheme.typography.titleSmall)
            LabeledChips(Period.entries, setOf(filters.period), { filters = filters.copy(period = it) })
            Text(stringResource(R.string.filter_health), style = MaterialTheme.typography.titleSmall)
            LabeledChips(HealthStatus.entries, filters.health, { h ->
                filters = filters.copy(health = if (h in filters.health) filters.health - h else filters.health + h)
            })
            Text(stringResource(R.string.filter_tnr), style = MaterialTheme.typography.titleSmall)
            LabeledChips(TnrStatus.entries, filters.tnr, { t ->
                filters = filters.copy(tnr = if (t in filters.tnr) filters.tnr - t else filters.tnr + t)
            })
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { filters = CatFilters(neighborhood = initial.neighborhood) }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.reset))
                }
                Button(onClick = { onApply(filters); onDismiss() }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.apply))
                }
            }
        }
    }
}
