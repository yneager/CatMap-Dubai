package com.catmap.dubai.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.text.format.DateUtils
import androidx.annotation.StringRes
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.catmap.dubai.AppContainer
import com.catmap.dubai.R
import com.catmap.dubai.data.model.CoatColor
import com.catmap.dubai.data.model.Labeled
import com.catmap.dubai.data.model.ReportReason
import com.catmap.dubai.data.userMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val LocalContainer = staticCompositionLocalOf<AppContainer> { error("No AppContainer") }
val LocalSnackbar = staticCompositionLocalOf<SnackbarHostState> { error("No SnackbarHostState") }
val LocalIsDark = compositionLocalOf { false }

// ---------- Running actions ----------

/** Runs a suspending action, tracks [busy] and reports success or failure in the snackbar. */
class ActionRunner(
    private val scope: CoroutineScope,
    private val appScope: CoroutineScope,
    private val snackbar: SnackbarHostState,
    private val context: Context,
) {
    var busy by mutableStateOf(false)
        private set

    fun run(@StringRes success: Int, onSuccess: () -> Unit = {}, block: suspend () -> Unit) =
        run(context.getString(success), onSuccess, block)

    fun run(success: String? = null, onSuccess: () -> Unit = {}, block: suspend () -> Unit) {
        if (busy) return
        scope.launch {
            busy = true
            try {
                block()
                success?.let { appScope.launch { snackbar.showSnackbar(it) } }
                onSuccess()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.w("CatMap", "Action failed", e)
                val message = e.userMessage(context)
                appScope.launch { snackbar.showSnackbar(message) }
            } finally {
                busy = false
            }
        }
    }
}

@Composable
fun rememberRunner(): ActionRunner {
    val scope = rememberCoroutineScope()
    val container = LocalContainer.current
    val snackbar = LocalSnackbar.current
    val context = LocalContext.current
    return remember { ActionRunner(scope, container.scope, snackbar, context) }
}

// ---------- Images ----------

@Composable
fun RemoteImage(
    imageId: String,
    modifier: Modifier = Modifier,
    thumb: Boolean = true,
    contentScale: ContentScale = ContentScale.Crop,
    contentDescription: String? = null,
) {
    val images = LocalContainer.current.images
    androidx.compose.foundation.layout.BoxWithConstraints(modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
    val density=androidx.compose.ui.platform.LocalDensity.current
    val target=with(density) { maxOf(maxWidth.toPx(),maxHeight.toPx()).toInt().coerceIn(1,if(thumb) 360 else 1280) }
    // A full-size photo starts from its cached thumbnail, so it never flashes empty while loading.
    fun cachedNow() = (images.cached(imageId, thumb,target) ?: if (!thumb) images.cached(imageId, true,360) else null)?.asImageBitmap()
    val bitmap by produceState(cachedNow(), imageId, thumb,target) {
        // produceState retains its value when a key changes (e.g. a new cover photo).
        value = cachedNow()
        if (imageId.isNotBlank() && images.cached(imageId, thumb,target) == null) images.bitmap(imageId, thumb,target)?.asImageBitmap()?.let { value = it }
    }
        val b = bitmap
        if (b != null) {
            Image(b, contentDescription, Modifier.fillMaxSize(), contentScale = contentScale)
        } else {
            com.catmap.dubai.ui.icons.CatMapIcon(com.catmap.dubai.ui.icons.CatMapIcons.PAW,contentDescription=contentDescription,tint=MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=.4f))
        }
    }
}

@Composable
fun LocalBitmap(bitmap: Bitmap, modifier: Modifier = Modifier, contentDescription: String? = null) {
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    Image(image, contentDescription, modifier, contentScale = ContentScale.Crop)
}

@Composable
fun Avatar(photoId: String, name: String, modifier: Modifier = Modifier, size: Dp = 44.dp) {
    if (photoId.isNotBlank()) {
        RemoteImage(photoId, modifier.size(size).clip(CircleShape), contentDescription = name)
    } else {
        Box(
            modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                name.take(1).uppercase().ifBlank { "?" },
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                style = if (size > 60.dp) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleMedium,
            )
        }
    }
}

/** A cat's cover photo, or its coat colours when it has none. */
@Composable
fun CatThumb(photoId: String, coat: CoatColor, modifier: Modifier = Modifier, contentDescription: String? = null) {
    if (photoId.isNotBlank()) {
        RemoteImage(photoId, modifier, contentDescription = contentDescription)
    } else {
        Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            CoatSwatch(coat, 28.dp)
        }
    }
}

@Composable
fun CoatSwatch(coat: CoatColor, size: Dp = 18.dp) {
    val outline = MaterialTheme.colorScheme.outline
    Canvas(Modifier.size(size)) {
        val colors = listOfNotNull(coat.primary, coat.secondary, coat.tertiary).map { Color(it) }
        val sweep = 360f / colors.size
        colors.forEachIndexed { i, c -> drawArc(c, -90f + i * sweep, sweep, useCenter = true) }
        drawCircle(outline, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx()))
    }
}

// ---------- Layout helpers ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(title: String, onBack: (() -> Unit)? = null, overlay:Boolean=false, titleContent:(@Composable ()->Unit)?=null, actions: @Composable () -> Unit = {}) {
    TopAppBar(
        title = { if(titleContent!=null) titleContent() else Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                }
            }
        },
        actions = { actions() },
        colors=androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
            containerColor=if(overlay) Color.Transparent else MaterialTheme.colorScheme.surface,
            navigationIconContentColor=if(overlay) Color.White else MaterialTheme.colorScheme.onSurface,
            actionIconContentColor=if(overlay) Color.White else MaterialTheme.colorScheme.onSurface,
        ),
    )
}

@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
fun EmptyState(icon: ImageVector, text: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(
        modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
        Text(text, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        action?.invoke()
    }
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        trailing()
    }
}

@Composable
fun StatTile(value: String, label: String, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(Modifier.padding(12.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            if (icon != null) Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            BasicText(
                value,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, color = LocalContentColor.current),
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(minFontSize = 12.sp, maxFontSize = MaterialTheme.typography.titleLarge.fontSize),
            )
            Text(label, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = 2)
        }
    }
}

@Composable
fun StatusPill(text: String, color: Color, modifier: Modifier = Modifier, solid: Boolean = false) {
    Surface(
        modifier,
        shape = RoundedCornerShape(50),
        // Solid pills stay readable on top of photos.
        color = if (solid) color else color.copy(alpha = 0.15f),
        border = if (solid) null else BorderStroke(1.dp, color.copy(alpha = 0.6f)),
    ) {
        Text(
            text,
            Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelMedium,
            color = when {
                solid -> Color.White
                MaterialTheme.colorScheme.background.luminanceIsDark() -> color.lighten()
                else -> color
            },
        )
    }
}

private fun Color.luminanceIsDark() = (0.299f * red + 0.587f * green + 0.114f * blue) < 0.5f
private fun Color.lighten() = Color(red + (1 - red) * 0.45f, green + (1 - green) * 0.45f, blue + (1 - blue) * 0.45f, alpha)

@Composable
fun InfoRow(label: String, value: String, modifier: Modifier = Modifier) {
    if (value.isBlank()) return
    Row(modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, Modifier.width(130.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

// ---------- Choice inputs ----------

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T : Labeled> LabeledChips(
    options: List<T>,
    selected: Set<T>,
    onToggle: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            val isSelected = option in selected
            FilterChip(
                selected = isSelected,
                onClick = { onToggle(option) },
                label = { Text(stringResource(option.label)) },
                leadingIcon = if (isSelected) ({ Icon(Icons.Default.Check, null, Modifier.size(FilterChipDefaults.IconSize)) }) else null,
            )
        }
    }
}

@Composable
fun <T> DropdownField(
    label: String,
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    leading: (@Composable (T) -> Unit)? = null,
    text: @Composable (T) -> String,
) {
    var open by remember { mutableStateOf(false) }
    val selectedText = text(selected)
    Box(modifier) {
        OutlinedTextField(
            value = selectedText,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            leadingIcon = leading?.let { { it(selected) } },
            trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) },
            // The overlay below speaks for the field, so screen readers hear it once.
            modifier = Modifier.fillMaxWidth().clearAndSetSemantics {},
        )
        // Transparent overlay so the whole field opens the menu.
        Box(
            Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(4.dp))
                .clickable(role = Role.DropdownList) { open = true }
                .semantics {
                    contentDescription = label
                    stateDescription = selectedText
                },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(text(option)) },
                    leadingIcon = leading?.let { { it(option) } },
                    onClick = {
                        onSelect(option)
                        open = false
                    },
                )
            }
        }
    }
}

@Composable
fun <T : Labeled> LabeledDropdown(label: String, options: List<T>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    DropdownField(label, options, selected, onSelect, modifier) { stringResource(it.label) }
}

// ---------- Dialogs ----------

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
fun TextInputDialog(
    title: String,
    label: String,
    confirmLabel: String,
    initial: String = "",
    singleLine: Boolean = false,
    required: Boolean = false,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(value, { value = it }, label = { Text(label) }, singleLine = singleLine, modifier = Modifier.fillMaxWidth())
        },
        confirmButton = {
            TextButton(enabled = !required || value.isNotBlank(), onClick = { onConfirm(value.trim()); onDismiss() }) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** "Report inappropriate content" dialog used on cats, sightings, photos, users and rescues. */
@Composable
fun ReportContentDialog(targetType: String, targetId: String, targetLabel: String, onDismiss: () -> Unit) {
    val container = LocalContainer.current
    val runner = rememberRunner()
    val me by container.session.profile.collectAsStateWithLifecycle()
    var reason by remember { mutableStateOf(ReportReason.INAPPROPRIATE) }
    var details by rememberSaveable { mutableStateOf("") }
    val thanks = stringResource(R.string.report_thanks)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.report_content)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.report_content_hint), style = MaterialTheme.typography.bodySmall)
                LabeledDropdown(stringResource(R.string.reason), ReportReason.entries, reason, { reason = it })
                OutlinedTextField(details, { details = it.take(500) }, label = { Text(stringResource(R.string.details_optional)) }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(enabled = !runner.busy && me != null, onClick = {
                runner.run(thanks, onSuccess = onDismiss) {
                    container.moderation.reportContent(targetType, targetId, targetLabel, reason.key, details, me ?: return@run)
                }
            }) { Text(stringResource(R.string.send_report)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

// ---------- Cards ----------

@Composable
fun ClickableCard(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) { content() }
}

@Composable
fun Pill(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.secondaryContainer) {
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(color)
            .border(0.dp, Color.Transparent, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 3.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium)
    }
}

// ---------- Time ----------

fun relativeTime(millis: Long): String =
    if (millis <= 0) "" else System.currentTimeMillis().let { now ->
        // Avoid "0 minutes ago" for things that just happened.
        DateUtils.getRelativeTimeSpanString(minOf(millis, now - DateUtils.MINUTE_IN_MILLIS), now, DateUtils.MINUTE_IN_MILLIS).toString()
    }

// Spelled-out months read well in both languages and avoid numeric dates flipping in RTL.
private fun pattern(skeleton: String): SimpleDateFormat =
    Locale.getDefault().let { SimpleDateFormat(android.text.format.DateFormat.getBestDateTimePattern(it, skeleton), it) }

fun dateTime(millis: Long): String =
    if (millis <= 0) "" else pattern("dMMMyjmm").format(Date(millis))

fun dateOnly(millis: Long): String =
    if (millis <= 0) "" else pattern("dMMMy").format(Date(millis))
