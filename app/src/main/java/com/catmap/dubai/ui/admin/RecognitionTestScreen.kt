package com.catmap.dubai.ui.admin

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.catmap.dubai.BuildConfig
import com.catmap.dubai.R
import com.catmap.dubai.ai.RecognitionEvaluation
import com.catmap.dubai.ui.components.AppTopBar
import com.catmap.dubai.ui.components.LocalContainer
import com.catmap.dubai.util.ImageUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Admin > Recognition test. The admin picks a folder on the phone with one sub-folder per cat
 * (for example CatTest/Zafran/1.jpg, CatTest/Zafran/2.jpg, CatTest/Layl/1.jpg). Every photo goes
 * through the app's own analyser, then [RecognitionEvaluation] reports how often the right cat ranks
 * first. Nothing is uploaded or saved; the report can be shared as text.
 */
@Composable
fun RecognitionTestScreen(nav: NavHostController) {
    val container = LocalContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var running by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var status by remember { mutableStateOf<String?>(null) }
    var report by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<RecognitionEvaluation.Result?>(null) }
    var aiResult by remember { mutableStateOf<RecognitionEvaluation.EmbeddingResult?>(null) }
    var aiChecked by remember { mutableStateOf(false) }
    val tooFew = stringResource(R.string.recognition_too_few)
    val analysing = stringResource(R.string.recognition_analysing)

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
        if (tree == null || running) return@rememberLauncherForActivityResult
        scope.launch {
            running = true
            report = null
            result = null
            aiResult = null
            aiChecked = false
            progress = 0f
            try {
                val photos = withContext(Dispatchers.IO) { listLabelledPhotos(context, tree) }
                val samples = ArrayList<RecognitionEvaluation.Sample>()
                val health = container.aiServer.health()
                val crops = ArrayList<Pair<String, android.graphics.Bitmap?>>()
                photos.forEachIndexed { i, (identity, name, uri) ->
                    status = String.format(Locale.getDefault(), analysing, i + 1, photos.size)
                    val started = System.currentTimeMillis()
                    val bitmap = runCatching { withContext(Dispatchers.IO) { ImageUtils.analysisBitmap(context, uri) } }.getOrNull()
                    val analysis = bitmap?.let { b -> runCatching { container.analyzer.analyze(b) }.getOrNull() }
                    if (health != null) crops += identity to bitmap?.let { b -> runCatching { container.analyzer.cropSubject(b) }.getOrNull() }
                    samples += RecognitionEvaluation.Sample(
                        identity, name.replace(',', ' '), analysis?.features.orEmpty(),
                        analysis?.coatColor?.key.orEmpty(), analysis?.pattern?.key.orEmpty(), System.currentTimeMillis() - started,
                    )
                    progress = (i + 1f) / photos.size
                }
                val r = withContext(Dispatchers.Default) { RecognitionEvaluation.evaluate(samples) }
                // The AI model, measured on exactly the same photos (embeddings from the server, compared here).
                var aiText = ""
                if (health != null) {
                    val vectors = ArrayList<Pair<String, List<Double>>>()
                    crops.chunked(16).forEach { chunk ->
                        val ok = chunk.filter { it.second != null }
                        val emb = if (ok.isEmpty()) null else container.aiServer.embed(ok.map { it.second!! })
                        if (emb != null) ok.zip(emb).forEach { (c, v) -> vectors += c.first to v }
                    }
                    aiResult = withContext(Dispatchers.Default) { RecognitionEvaluation.evaluateEmbeddings(vectors, health.same, health.maybe) }
                    aiResult?.let { aiText = "\n" + RecognitionEvaluation.embeddingReport(it, health.model, health.same, health.maybe) }
                }
                aiChecked = true
                result = r
                report = r?.let { RecognitionEvaluation.report(it, BuildConfig.VERSION_NAME).replaceFirst("\nidentity,", aiText + "\nidentity,") }
                status = if (r == null) tooFew else null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.w("CatMap", "Recognition test failed", e)
                status = tooFew
            } finally {
                running = false
            }
        }
    }

    Scaffold(topBar = { AppTopBar(stringResource(R.string.recognition_test), onBack = { nav.popBackStack() }) }) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.recognition_test_intro), style = MaterialTheme.typography.bodyMedium)
            Button(onClick = { pick.launch(null) }, enabled = !running, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.recognition_choose_folder))
            }
            if (running) LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            status?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            result?.let { r ->
                Card(colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceContainerHigh)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(R.string.recognition_dataset, r.identities, r.photos), style = MaterialTheme.typography.titleSmall)
                        Metric(stringResource(R.string.recognition_top1), r.top1)
                        Metric(stringResource(R.string.recognition_top3), r.top3)
                        Metric(stringResource(R.string.recognition_top1_best_photo), r.top1BestPhoto)
                        Metric(stringResource(R.string.recognition_false_suggestion), r.unseenAnySuggestion)
                        Metric(stringResource(R.string.recognition_false_strong), r.unseenStrong)
                        Text(stringResource(R.string.recognition_time, r.averageMillis.toInt()), style = MaterialTheme.typography.bodySmall)
                        aiResult?.let { ai ->
                            Metric(stringResource(R.string.recognition_ai_top1), ai.top1BestPhoto)
                        } ?: run { if (aiChecked) Text(stringResource(R.string.recognition_ai_unavailable), style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
            report?.let { text ->
                OutlinedButton(onClick = {
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
                    context.startActivity(Intent.createChooser(send, null))
                }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.recognition_share)) }
                Text(text.lineSequence().takeWhile { !it.startsWith("identity,") }.joinToString("\n"), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

@Composable
private fun Metric(label: String, value: Double) {
    androidx.compose.foundation.layout.Row {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(String.format(com.catmap.dubai.ui.components.currentLocale(), "%.1f%%", value * 100), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

/** (cat name, file name, uri) for every image one level below the chosen folder's sub-folders. Max 400. */
private fun listLabelledPhotos(context: Context, tree: Uri): List<Triple<String, String, Uri>> {
    val resolver = context.contentResolver
    fun children(parentId: String): List<Triple<String, String, String>> {
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
        val columns = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE)
        return resolver.query(uri, columns, null, null, null)?.use { c ->
            buildList { while (c.moveToNext()) add(Triple(c.getString(0), c.getString(1) ?: "", c.getString(2) ?: "")) }
        }.orEmpty()
    }
    val out = ArrayList<Triple<String, String, Uri>>()
    for ((folderId, folderName, mime) in children(DocumentsContract.getTreeDocumentId(tree)).sortedBy { it.second }) {
        if (mime != DocumentsContract.Document.MIME_TYPE_DIR) continue
        for ((fileId, fileName, fileMime) in children(folderId).sortedBy { it.second }) {
            if (!fileMime.startsWith("image/")) continue
            out += Triple(folderName, fileName, DocumentsContract.buildDocumentUriUsingTree(tree, fileId))
            if (out.size >= 400) return out
        }
    }
    return out
}

/** Admin > AI server: whether the CatMap AI Server on the owner's PC is online, and what it runs. */
@Composable
fun AiServerScreen(nav: NavHostController) {
    val container = LocalContainer.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }
    val online = stringResource(R.string.ai_server_ok, "%s")
    val offline = stringResource(R.string.ai_server_down)
    val never = stringResource(R.string.ai_server_never)
    fun check() {
        scope.launch {
            checking = true
            val h = container.aiServer.health()
            status = if (h != null) online.replace("%s", h.toString()) else {
                val seen = container.aiServer.lastSeen()
                if (seen == null) never else offline + " (" + android.text.format.DateUtils.getRelativeTimeSpanString(seen) + ")"
            }
            checking = false
        }
    }
    androidx.compose.runtime.LaunchedEffect(Unit) { check() }
    Scaffold(topBar = { AppTopBar(stringResource(R.string.ai_server), onBack = { nav.popBackStack() }) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.ai_server_intro), style = MaterialTheme.typography.bodyMedium)
            status?.let { Text(it, style = MaterialTheme.typography.titleSmall) }
            OutlinedButton(onClick = { check() }, enabled = !checking, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.ai_server_test)) }
        }
    }
}
