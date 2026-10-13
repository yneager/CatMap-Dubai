package com.catmap.dubai.ui.admin

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.catmap.dubai.R
import com.catmap.dubai.data.AdminAnalytics
import com.catmap.dubai.ui.components.LocalContainer
import com.catmap.dubai.ui.components.dateOnly
import kotlinx.coroutines.launch

@Composable
fun AnalyticsPanel() {
    val container=LocalContainer.current
    val scope=rememberCoroutineScope()
    var analytics by remember { mutableStateOf<AdminAnalytics?>(null) }
    var error by remember { mutableStateOf(false) }
    fun load() { scope.launch { error=false; runCatching { container.analytics.load() }.onSuccess {analytics=it}.onFailure {error=true} } }
    LaunchedEffect(Unit) { load() }
    Column(Modifier.fillMaxWidth().padding(top=12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.admin_analytics),style=MaterialTheme.typography.titleMedium)
        when {
            error -> TextButton(onClick={load()}) { Text(stringResource(R.string.try_again)) }
            analytics==null -> LinearProgressIndicator(Modifier.fillMaxWidth())
            else -> analytics?.let { data ->
                Text(stringResource(R.string.reunion_rate,"%.1f".format(data.reunionRate)),style=MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.reunion_rate_basis),style=MaterialTheme.typography.bodySmall)
                val postsColor=MaterialTheme.colorScheme.primary
                val membersColor=MaterialTheme.colorScheme.secondary
                val axis=MaterialTheme.colorScheme.onSurfaceVariant
                val description=stringResource(R.string.analytics_accessibility,data.weeks.joinToString {it.posts.toString()},data.weeks.joinToString {it.members.toString()})
                Row(horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                    Text(stringResource(R.string.weekly_posts),color=postsColor,style=MaterialTheme.typography.labelSmall)
                    Text(stringResource(R.string.weekly_members),color=membersColor,style=MaterialTheme.typography.labelSmall)
                }
                Canvas(Modifier.fillMaxWidth().height(140.dp).semantics {contentDescription=description}) {
                    val max=data.weeks.maxOf {maxOf(it.posts,it.members)}.coerceAtLeast(1)
                    val cell=size.width/data.weeks.size
                    drawLine(axis,Offset(0f,size.height-1),Offset(size.width,size.height-1))
                    data.weeks.forEachIndexed {i,week ->
                        val p=(week.posts.toFloat()/max)*size.height
                        val m=(week.members.toFloat()/max)*size.height
                        drawRect(postsColor,Offset(i*cell+cell*.12f,size.height-p),Size(cell*.3f,p))
                        drawRect(membersColor,Offset(i*cell+cell*.52f,size.height-m),Size(cell*.3f,m))
                    }
                }
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                    Text(dateOnly(data.weeks.first().start),style=MaterialTheme.typography.labelSmall)
                    Text(dateOnly(data.weeks.last().start),style=MaterialTheme.typography.labelSmall)
                }
                Text(stringResource(R.string.analytics_axis),style=MaterialTheme.typography.labelSmall)
            }
        }
    }
}
