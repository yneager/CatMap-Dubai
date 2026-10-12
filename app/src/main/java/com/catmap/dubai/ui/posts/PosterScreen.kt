package com.catmap.dubai.ui.posts

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.catmap.dubai.R
import com.catmap.dubai.ui.components.AppTopBar
import com.catmap.dubai.ui.components.LocalContainer
import com.catmap.dubai.ui.components.shimmer
import com.catmap.dubai.util.PosterMaker
import com.catmap.dubai.util.Share
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Shows the generated poster and shares it to WhatsApp, Instagram, print apps… */
@Composable
fun PosterScreen(nav: NavHostController, postId: String) {
    val container = LocalContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val result by produceState<Pair<Bitmap, String>?>(null, postId) {
        val post = container.posts.post(postId) ?: return@produceState
        val photo = post.photoIds.firstOrNull()?.let { container.images.bytes(it) }
        val bitmap = withContext(Dispatchers.Default) { PosterMaker.make(context, post, photo) }
        value = bitmap to Share.postSummary(context, post)
    }

    Scaffold(topBar = { AppTopBar(stringResource(R.string.poster), onBack = { nav.popBackStack() }) }) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.poster_intro), style = MaterialTheme.typography.bodyMedium)
            Card(shape = RoundedCornerShape(20.dp), elevation = CardDefaults.cardElevation(6.dp)) {
                AnimatedContent(result, transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.96f)) togetherWith fadeOut() }, label = "poster") { r ->
                    if (r != null) {
                        Image(r.first.asImageBitmap(), stringResource(R.string.poster), Modifier.fillMaxWidth().aspectRatio(1080f / 1350f))
                    } else {
                        Box(Modifier.fillMaxWidth().aspectRatio(1080f / 1350f).clip(RoundedCornerShape(20.dp)).shimmer())
                    }
                }
            }
            Button(
                onClick = { result?.let { (bmp, text) -> scope.launch { Share.shareImage(context, bmp, "poster_$postId.jpg", text) } } },
                enabled = result != null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Share, null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.share_poster))
            }
        }
    }
}
