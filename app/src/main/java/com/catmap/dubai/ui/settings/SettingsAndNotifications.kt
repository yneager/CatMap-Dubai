package com.catmap.dubai.ui.settings

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.catmap.dubai.BuildConfig
import com.catmap.dubai.R
import com.catmap.dubai.data.AppSettings
import com.catmap.dubai.data.LocationPrivacy
import com.catmap.dubai.data.MapMode
import com.catmap.dubai.ui.map.MapStyles
import com.catmap.dubai.update.Release
import com.catmap.dubai.update.UpdateSheet
import com.catmap.dubai.data.NotifCategory
import com.catmap.dubai.data.ThemeMode
import com.catmap.dubai.notifications.NotificationText
import com.catmap.dubai.notifications.SystemNotifications
import com.catmap.dubai.ui.auth.PasswordField
import com.catmap.dubai.ui.components.AppTopBar
import com.catmap.dubai.ui.components.ConfirmDialog
import com.catmap.dubai.ui.components.EmptyState
import com.catmap.dubai.ui.components.LocalContainer
import com.catmap.dubai.ui.components.SectionHeader
import com.catmap.dubai.ui.components.relativeTime
import com.catmap.dubai.ui.components.rememberRunner
import com.catmap.dubai.ui.navigation.Routes
import com.catmap.dubai.ui.stations.SwitchRow
import kotlinx.coroutines.launch

@Composable
fun NotificationsScreen(nav: NavHostController) {
    val container = LocalContainer.current
    val context = LocalContext.current
    val runner = rememberRunner()
    val uid = container.session.uid ?: return
    val items by remember(uid) { container.notifications.notifications(uid) }.collectAsStateWithLifecycle(null)
    var confirmClear by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            AppTopBar(stringResource(R.string.notifications), onBack = { nav.popBackStack() }) {
                IconButton(onClick = {
                    val unread = items.orEmpty().filter { !it.read }.map { it.id }
                    runner.run { container.notifications.markAllRead(uid, unread) }
                }) { Icon(Icons.Default.DoneAll, stringResource(R.string.mark_all_read)) }
                IconButton(onClick = { confirmClear = true }) { Icon(Icons.Default.Delete, stringResource(R.string.clear_all)) }
            }
        },
    ) { padding ->
        val list = items
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
            if (list != null && list.isEmpty()) item { EmptyState(Icons.Outlined.NotificationsNone, stringResource(R.string.no_notifications)) }
            items(list.orEmpty(), key = { it.id }) { n ->
                val (title, body) = NotificationText.format(context, n)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            runner.run { if (!n.read) container.notifications.markRead(uid, n.id) }
                            if (n.route.isNotBlank()) nav.navigate(n.route)
                        }
                        .background(if (n.read) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(if (n.read) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.primary))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(title, fontWeight = if (n.read) FontWeight.Normal else FontWeight.SemiBold)
                        if (body.isNotBlank()) Text(body, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            relativeTime(n.createdAt) + " · " + stringResource(if (n.read) R.string.read else R.string.unread),
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { runner.run { container.notifications.delete(uid, n.id) } }) {
                        Icon(Icons.Default.Delete, stringResource(R.string.delete), tint = MaterialTheme.colorScheme.outline)
                    }
                }
                HorizontalDivider()
            }
        }
    }
    if (confirmClear) {
        ConfirmDialog(
            stringResource(R.string.clear_all), stringResource(R.string.clear_all_body), stringResource(R.string.clear_all),
            onConfirm = { runner.run { container.notifications.clearAll(uid) } },
            onDismiss = { confirmClear = false },
        )
    }
}

@Composable
fun SettingsScreen(nav: NavHostController) {
    val container = LocalContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val runner = rememberRunner()
    val settings by container.settings.settings.collectAsStateWithLifecycle(AppSettings())
    val me by container.session.profile.collectAsStateWithLifecycle()
    var dialog by remember { mutableStateOf<String?>(null) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val language = remember { mutableStateOf(container.settings.language()) }
    val resetSent = stringResource(R.string.reset_sent, container.authRepo.email())

    Scaffold(topBar = { AppTopBar(stringResource(R.string.settings), onBack = { nav.popBackStack() }) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            SectionHeader(stringResource(R.string.language))
            listOf("" to R.string.lang_system, "en" to R.string.lang_english, "ar" to R.string.lang_arabic).forEach { (tag, label) ->
                RadioRow(stringResource(label), language.value == tag) {
                    language.value = tag
                    container.settings.setLanguage(tag)
                }
            }

            SectionHeader(stringResource(R.string.appearance_settings))
            listOf(ThemeMode.SYSTEM to R.string.theme_system, ThemeMode.LIGHT to R.string.theme_light, ThemeMode.DARK to R.string.theme_dark).forEach { (mode, label) ->
                RadioRow(stringResource(label), settings.theme == mode) { scope.launch { container.settings.setTheme(mode) } }
            }
            SwitchRow(stringResource(R.string.larger_text), settings.largeText) { on -> scope.launch { container.settings.setLargeText(on) } }

            SectionHeader(stringResource(R.string.map_style))
            listOf(MapMode.MODERN to R.string.map_modern_desc, MapMode.CLASSIC to R.string.map_classic_desc).forEach { (mode, label) ->
                RadioRow(stringResource(label), settings.mapMode == mode) { scope.launch { container.settings.setMapMode(mode) } }
            }
            if (MapStyles.satelliteAvailable) {
                SwitchRow(stringResource(R.string.satellite), settings.satellite) { on -> scope.launch { container.settings.setSatellite(on) } }
            }

            SectionHeader(stringResource(R.string.notifications))
            SwitchRow(stringResource(R.string.notifications_enabled), settings.notificationsEnabled) { on ->
                scope.launch { container.settings.setNotificationsEnabled(on) }
                if (on && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !SystemNotifications.canPost(context)) {
                    permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            if (settings.notificationsEnabled) {
                NotifCategory.entries.forEach { c ->
                    SwitchRow("   " + stringResource(SystemNotifications.channelName(c)), settings.categories[c] != false) { on -> scope.launch { container.settings.setCategory(c, on) } }
                }
                TextButton(onClick = { nav.navigate(Routes.AREA_ALERTS) }) { Text(stringResource(R.string.area_alerts)) }
            }
            TextButton(onClick = {
                val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                runCatching { context.startActivity(intent) }
            }) { Text(stringResource(R.string.system_notification_settings)) }

            SectionHeader(stringResource(R.string.privacy))
            Text(stringResource(R.string.public_location_precision), style = MaterialTheme.typography.bodyMedium)
            RadioRow(stringResource(R.string.precision_standard, LocationPrivacy.approxRadiusMeters(LocationPrivacy.STANDARD)), settings.privacyLevel == LocationPrivacy.STANDARD) {
                scope.launch { container.settings.setPrivacyLevel(LocationPrivacy.STANDARD) }
            }
            RadioRow(stringResource(R.string.precision_high, LocationPrivacy.approxRadiusMeters(LocationPrivacy.HIGH)), settings.privacyLevel == LocationPrivacy.HIGH) {
                scope.launch { container.settings.setPrivacyLevel(LocationPrivacy.HIGH) }
            }
            SwitchRow(stringResource(R.string.share_exact_volunteers), settings.shareExactWithVolunteers) { on -> scope.launch { container.settings.setShareExact(on) } }
            me?.let { p ->
                SwitchRow(stringResource(R.string.show_stats_publicly), p.showStatsPublicly) { on -> runner.run { container.users.setShowStats(p.uid, on) } }
            }
            Text(stringResource(R.string.metadata_always_removed), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            SectionHeader(stringResource(R.string.account))
            Text(container.authRepo.email(), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = {
                runner.run(resetSent) { container.authRepo.sendPasswordReset(container.authRepo.email()) }
            }) { Text(stringResource(R.string.change_password)) }
            OutlinedButton(onClick = { dialog = "logout" }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.logout)) }
            TextButton(onClick = { dialog = "delete" }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.delete_account), color = MaterialTheme.colorScheme.error)
            }

            SectionHeader(stringResource(R.string.app_updates))
            UpdateCheckRow()

            SectionHeader(stringResource(R.string.about))
            Text(stringResource(R.string.about_text, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.map_attribution), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(vertical = 16.dp))
        }
    }

    when (dialog) {
        "logout" -> ConfirmDialog(
            stringResource(R.string.logout), stringResource(R.string.logout_body), stringResource(R.string.logout),
            onConfirm = {
                container.authRepo.logout()
                nav.navigate(Routes.LOGIN) { popUpTo(0) { inclusive = true } }
            },
            onDismiss = { dialog = null },
        )
        "delete" -> DeleteAccountDialog(onDismiss = { dialog = null }) { password ->
            runner.run(R.string.account_deleted, onSuccess = {
                dialog = null
                nav.navigate(Routes.LOGIN) { popUpTo(0) { inclusive = true } }
            }) { container.authRepo.deleteAccount(password, me) }
        }
    }
}

/** "Check for updates": asks GitHub now and shows the update sheet, or says you're up to date. */
@Composable
private fun UpdateCheckRow() {
    val container = LocalContainer.current
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    var release by remember { mutableStateOf<Release?>(null) }
    val upToDate = stringResource(R.string.up_to_date, BuildConfig.VERSION_NAME)
    val failed = stringResource(R.string.update_check_failed)
    val notConfigured = stringResource(R.string.updates_not_configured)
    Text(stringResource(R.string.current_version_x, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.bodyMedium)
    OutlinedButton(
        enabled = !checking,
        onClick = {
            scope.launch {
                checking = true
                result = null
                val updater = container.updater
                if (!updater.configured) {
                    result = notConfigured
                } else {
                    runCatching { updater.latest() }
                        .onSuccess { r -> if (r != null && updater.isNewer(r.version)) release = r else result = upToDate }
                        .onFailure { result = failed }
                }
                checking = false
            }
        },
        modifier = Modifier.fillMaxWidth(),
    ) { Text(stringResource(if (checking) R.string.checking else R.string.check_for_updates)) }
    result?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    release?.let { UpdateSheet(it, onDismiss = { release = null }) }
}

@Composable
private fun RadioRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(selected, role = Role.RadioButton, onClick = onClick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected, null)
        Text(label, Modifier.padding(start = 10.dp))
    }
}

@Composable
private fun DeleteAccountDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var password by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.delete_account)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.delete_account_body))
                PasswordField(password, { password = it }, stringResource(R.string.password))
            }
        },
        confirmButton = {
            TextButton(enabled = password.isNotBlank(), onClick = { onConfirm(password) }) {
                Text(stringResource(R.string.delete_forever), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
