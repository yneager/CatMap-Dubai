package com.catmap.dubai.ui.messages

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.catmap.dubai.R
import com.catmap.dubai.data.model.Chat
import com.catmap.dubai.data.model.Message
import com.catmap.dubai.data.model.Post
import com.catmap.dubai.data.userMessage
import com.catmap.dubai.ui.components.AppTopBar
import com.catmap.dubai.ui.components.Avatar
import com.catmap.dubai.ui.components.ConfirmDialog
import com.catmap.dubai.ui.components.EmptyState
import com.catmap.dubai.ui.components.LoadingBox
import com.catmap.dubai.ui.components.LocalContainer
import com.catmap.dubai.ui.components.LocalSnackbar
import com.catmap.dubai.ui.components.PostPhoto
import com.catmap.dubai.ui.components.RemoteImage
import com.catmap.dubai.ui.components.ReportContentDialog
import com.catmap.dubai.ui.components.SkeletonRow
import com.catmap.dubai.ui.components.dateTime
import com.catmap.dubai.ui.components.postTitle
import com.catmap.dubai.ui.components.relativeTime
import com.catmap.dubai.ui.components.rememberRunner
import com.catmap.dubai.ui.navigation.NotificationBell
import com.catmap.dubai.ui.navigation.Routes
import com.catmap.dubai.ui.theme.BrandGradient
import com.catmap.dubai.util.ImageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** All conversations, newest first, with unread counts. */
@Composable
fun MessagesScreen(nav: NavHostController) {
    val container = LocalContainer.current
    val uid = container.session.uid ?: return
    val chats by remember(uid) { container.chats.chatsFor(uid) }.collectAsStateWithLifecycle(null)
    val blocked by remember(uid) { container.chats.blocked(uid) }.collectAsStateWithLifecycle(emptySet())
    Scaffold(topBar = { AppTopBar(stringResource(R.string.tab_messages), actions = { NotificationBell(nav) }) }) { padding ->
        val list = chats?.filter { it.lastText.isNotBlank() || it.lastSender.isNotBlank() }
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
            when {
                list == null -> items(5) { SkeletonRow(Modifier.padding(horizontal = 16.dp)) }
                list.isEmpty() -> item { EmptyState(Icons.Outlined.Forum, stringResource(R.string.no_chats)) }
                else -> items(list, key = { it.id }) { chat ->
                    ChatRow(chat, uid, chat.other(uid) in blocked, Modifier.animateItem()) { nav.navigate(Routes.chat(chat.id)) }
                }
            }
        }
    }
}

@Composable
private fun ChatRow(chat: Chat, me: String, blocked: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val other = chat.other(me)
    val unread = chat.unread[me] ?: 0
    Row(
        modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(chat.photos[other].orEmpty(), chat.names[other].orEmpty(), size = 52.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("@" + chat.names[other].orEmpty(), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), maxLines = 1)
                Text(relativeTime(chat.lastAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (blocked) stringResource(R.string.blocked) else (if (chat.lastSender == me) stringResource(R.string.you_prefix) + " " else "") + chat.lastText,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (unread > 0) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (unread > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                )
                if (unread > 0) Badge { Text(unread.toString()) }
            }
        }
    }
}

/** Opens (creating if needed) the chat with a person, then replaces itself with that chat. */
@Composable
fun ChatWithScreen(nav: NavHostController, otherUid: String, postId: String?) {
    val container = LocalContainer.current
    val context = LocalContext.current
    val me by container.session.profile.collectAsStateWithLifecycle()
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(me, otherUid) {
        val profile = me ?: return@LaunchedEffect
        runCatching { container.chats.open(profile, otherUid) }
            .onSuccess { id ->
                nav.navigate(Routes.chat(id, postId)) {
                    popUpTo(Routes.CHAT_WITH) { inclusive = true }
                }
            }
            .onFailure { error = it.userMessage(context) }
    }
    Scaffold(topBar = { AppTopBar("", onBack = { nav.popBackStack() }) }) { padding ->
        val e = error
        if (e == null) LoadingBox(Modifier.padding(padding)) else EmptyState(Icons.Outlined.Forum, e, Modifier.padding(padding))
    }
}

@Composable
fun ChatScreen(nav: NavHostController, chatId: String, sharePostId: String?) {
    val container = LocalContainer.current
    val context = LocalContext.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val runner = rememberRunner()
    val me by container.session.profile.collectAsStateWithLifecycle()
    val uid = container.session.uid ?: return
    val chat by remember(chatId) { container.chats.chatFlow(chatId) }.collectAsStateWithLifecycle(null)
    val messages by remember(chatId) { container.chats.messages(chatId) }.collectAsStateWithLifecycle(null)
    val blocked by remember(uid) { container.chats.blocked(uid) }.collectAsStateWithLifecycle(emptySet())
    var text by rememberSaveable { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<String?>(null) }
    var attachPostId by rememberSaveable { mutableStateOf(sharePostId) }
    val attachPost by produceState<Post?>(null, attachPostId) { value = attachPostId?.let { container.posts.post(it) } }
    val listState = rememberLazyListState()
    val c = chat
    val other = c?.other(uid).orEmpty()
    val iBlocked = other in blocked

    // Reading the chat clears its unread count and its alert, so the next message alerts again.
    LaunchedEffect(chatId, messages?.size) { container.chats.markRead(chatId, uid) }
    LaunchedEffect(messages?.size) { if (!messages.isNullOrEmpty()) listState.animateScrollToItem(0) }

    fun send(imageId: String = "") {
        val current = c ?: return
        val profile = me ?: return
        val body = text
        val post = attachPost
        if (body.isBlank() && imageId.isBlank() && post == null) return
        scope.launch {
            sending = true
            runCatching { container.chats.send(current, profile, body, imageId, post) }
                .onSuccess {
                    text = ""
                    attachPostId = null
                }
                .onFailure { snackbar.showSnackbar(it.userMessage(context)) }
            sending = false
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) scope.launch {
            sending = true
            runCatching {
                val processed = withContext(Dispatchers.Default) { ImageUtils.process(context, uri) }
                container.images.upload(uid, processed)
            }.onSuccess { send(it) }.onFailure { snackbar.showSnackbar(it.userMessage(context)) }
            sending = false
        }
    }

    Scaffold(
        topBar = {
            Surface(tonalElevation = 2.dp) {
                Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                    Avatar(c?.photos?.get(other).orEmpty(), c?.names?.get(other).orEmpty(), size = 40.dp, modifier = Modifier.clickable { if (other.isNotBlank()) nav.navigate(Routes.user(other)) })
                    Spacer(Modifier.width(10.dp))
                    Text("@" + c?.names?.get(other).orEmpty(), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), maxLines = 1)
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, stringResource(R.string.more)) }
                        DropdownMenu(menu, { menu = false }) {
                            DropdownMenuItem({ Text(stringResource(R.string.view_profile)) }, { menu = false; nav.navigate(Routes.user(other)) }, leadingIcon = { Icon(Icons.Outlined.Person, null) })
                            DropdownMenuItem(
                                { Text(stringResource(if (iBlocked) R.string.unblock_user else R.string.block_user)) },
                                { menu = false; dialog = "block" }, leadingIcon = { Icon(Icons.Outlined.Block, null) },
                            )
                            DropdownMenuItem({ Text(stringResource(R.string.report_user)) }, { menu = false; dialog = "report" }, leadingIcon = { Icon(Icons.Outlined.Flag, null) })
                        }
                    }
                }
            }
        },
        bottomBar = {
            Column(Modifier.navigationBarsPadding().imePadding()) {
                AnimatedVisibility(attachPost != null && !iBlocked, enter = slideInVertically { it } + fadeIn()) {
                    attachPost?.let { p ->
                        Row(
                            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer).padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            PostPhoto(p, Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)))
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.sharing_post), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                Text(postTitle(p), style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                            }
                            IconButton(onClick = { attachPostId = null }) { Icon(Icons.Default.Close, stringResource(R.string.remove)) }
                        }
                    }
                }
                HorizontalDivider()
                if (iBlocked) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.you_blocked_user), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = { runner.run(R.string.unblocked) { container.chats.unblock(uid, other) } }) { Text(stringResource(R.string.unblock_user)) }
                    }
                } else {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, enabled = !sending) {
                            Icon(Icons.Outlined.AddPhotoAlternate, stringResource(R.string.add_photo))
                        }
                        OutlinedTextField(
                            text, { text = it.take(2000) },
                            placeholder = { Text(stringResource(R.string.type_message)) },
                            shape = RoundedCornerShape(26.dp), maxLines = 4, modifier = Modifier.weight(1f),
                        )
                        val canSend = (text.isNotBlank() || attachPost != null) && !sending && c != null
                        val scale by animateFloatAsState(if (canSend) 1f else 0.85f, spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "send")
                        Spacer(Modifier.width(6.dp))
                        Box(
                            Modifier.size(48.dp).scale(scale).clip(CircleShape)
                                .background(if (canSend) BrandGradient else androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.surfaceVariant))
                                .clickable(enabled = canSend) { send() },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (sending) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                            else Icon(Icons.AutoMirrored.Filled.Send, stringResource(R.string.send), tint = if (canSend) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
    ) { padding ->
        val list = messages
        when {
            list == null -> LoadingBox(Modifier.padding(padding))
            list.isEmpty() -> EmptyState(Icons.Outlined.Forum, stringResource(R.string.say_hello), Modifier.padding(padding))
            else -> LazyColumn(
                Modifier.padding(padding).fillMaxSize(), state = listState, reverseLayout = true,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                val reversed = list.asReversed()
                items(reversed.size, key = { reversed[it].id }) { i ->
                    val m = reversed[i]
                    val mine = m.senderId == uid
                    // A time label above the first message of each gap of 15+ minutes.
                    val previous = reversed.getOrNull(i + 1)
                    Column(Modifier.animateItem()) {
                        if (previous == null || m.createdAt - previous.createdAt > 15 * 60 * 1000) {
                            Text(dateTime(m.createdAt), Modifier.fillMaxWidth().padding(vertical = 6.dp), style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        }
                        Bubble(m, mine, onPost = { nav.navigate(Routes.post(it)) }, onDelete = { runner.run { container.chats.deleteMessage(chatId, m) } })
                    }
                }
            }
        }
    }

    when (dialog) {
        "block" -> ConfirmDialog(
            stringResource(if (iBlocked) R.string.unblock_user else R.string.block_user),
            stringResource(if (iBlocked) R.string.unblock_body else R.string.block_body),
            stringResource(R.string.confirm),
            onConfirm = {
                runner.run(if (iBlocked) R.string.unblocked else R.string.blocked_done) {
                    if (iBlocked) container.chats.unblock(uid, other) else container.chats.block(uid, other)
                }
            },
            onDismiss = { dialog = null },
        )
        "report" -> ReportContentDialog("user", other, c?.names?.get(other).orEmpty()) { dialog = null }
    }
}

@Composable
private fun Bubble(m: Message, mine: Boolean, onPost: (String) -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Box {
            Column(
                Modifier
                    .widthIn(max = 290.dp)
                    .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = if (mine) 20.dp else 6.dp, bottomEnd = if (mine) 6.dp else 20.dp))
                    .background(if (mine) BrandGradient else androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.surfaceContainerHigh))
                    .clickable(enabled = mine) { menu = true }
                    .padding(4.dp),
            ) {
                if (m.imageId.isNotBlank()) {
                    RemoteImage(m.imageId, Modifier.size(220.dp).clip(RoundedCornerShape(16.dp)), thumb = false)
                }
                if (m.postId.isNotBlank()) {
                    Card(
                        onClick = { onPost(m.postId) },
                        modifier = Modifier.padding(4.dp).width(230.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surface),
                    ) {
                        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (m.postPhotoId.isNotBlank()) RemoteImage(m.postPhotoId, Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)))
                            else Text("🐾", style = MaterialTheme.typography.titleLarge)
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(m.postTitle.ifBlank { stringResource(R.string.view_post) }, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                                Text(stringResource(R.string.view_post), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
                if (m.text.isNotBlank()) {
                    Text(
                        m.text, Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        color = if (mine) Color.White else MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
            DropdownMenu(menu, { menu = false }) {
                DropdownMenuItem({ Text(stringResource(R.string.delete_message)) }, { menu = false; onDelete() })
            }
        }
    }
    Spacer(Modifier.height(2.dp))
}
