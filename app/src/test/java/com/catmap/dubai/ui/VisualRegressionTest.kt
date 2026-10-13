package com.catmap.dubai.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onLast
import androidx.compose.ui.unit.dp
import com.catmap.dubai.AppContainer
import com.catmap.dubai.data.FeedFilters
import com.catmap.dubai.data.model.Post
import com.catmap.dubai.ui.components.LocalContainer
import com.catmap.dubai.ui.components.PostCard
import com.catmap.dubai.ui.components.PostGridCard
import com.catmap.dubai.ui.components.StatusBadge
import com.catmap.dubai.ui.components.PostDetailHeader
import com.catmap.dubai.ui.explore.FilterSheet
import com.catmap.dubai.ui.icons.CatMapIcon
import com.catmap.dubai.ui.icons.CatMapIcons
import com.catmap.dubai.ui.theme.CatMapTheme
import com.catmap.dubai.util.PosterMaker
import com.catmap.dubai.util.PostLinks
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.Locale
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "en-rUS-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class VisualRegressionTest {
    @get:Rule val compose = createComposeRule()
    private val post = Post(id = "fixture", name = "Amber", category = "lost", animalType = "cat",
        description = "Orange coat, teal collar. Last seen near the park.", neighborhood = "deira",
        createdAt = 0, lastSeenAt = 0, reward = 250)

    @Test fun englishLightComponents() = components(false)
    @Test fun englishDarkComponents() = components(true)

    private fun components(dark: Boolean) {
        Locale.setDefault(Locale.US)
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Dubai"))
        val chosen = mutableIntStateOf(0)
        val names = listOf("post-card", "post-grid-card", "status-badge", "detail-header", "filter-sheet", "icons")
        compose.setContent {
            val container = remember { AppContainer(RuntimeEnvironment.getApplication()) }
            CompositionLocalProvider(LocalContainer provides container) {
                CatMapTheme(dark, false) {
                    Surface(Modifier.width(360.dp)) {
                        when (chosen.intValue) {
                            0 -> PostCard(post, false, {}, {})
                            1 -> PostGridCard(post, false, {}, {})
                            2 -> StatusBadge(post.copy(status = "reunited"))
                            3 -> Column(Modifier.padding(16.dp)) { PostDetailHeader(post) }
                            4 -> FilterSheet(FeedFilters(), {}, {}, hasLocation = false)
                            else -> Column {
                                CatMapIcons.all.take(12).forEach { (name, icon) ->
                                    androidx.compose.foundation.layout.Row { CatMapIcon(icon); Text(name) }
                                }
                            }
                        }
                    }
                }
            }
        }
        names.forEachIndexed { index, name ->
            compose.runOnIdle { chosen.intValue = index }
            compose.waitForIdle()
            compose.onAllNodes(isRoot(), useUnmergedTree = true).onLast().captureRoboImage("src/test/screenshots/${if (dark) "dark" else "light"}-$name.png")
        }
    }

    @Test fun posterAndFullIconContactSheet() {
        val context = RuntimeEnvironment.getApplication()
        Locale.setDefault(Locale.US)
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Dubai"))
        val poster = PosterMaker.make(context, post, null)
        poster.captureRoboImage("src/test/screenshots/poster.png")
        val qrPixels = IntArray(128 * 128)
        poster.getPixels(qrPixels, 0, 128, 888, 1210, 128, 128)
        val qr = BinaryBitmap(HybridBinarizer(RGBLuminanceSource(128, 128, qrPixels)))
        assertEquals(PostLinks.create(post.id), MultiFormatReader().decode(qr).text)
        val cols = 6
        val cellW = 220
        val cellH = 128
        val bitmap = Bitmap.createBitmap(cols * cellW, ((CatMapIcons.all.size + cols - 1) / cols) * cellH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 13f }
        CatMapIcons.all.forEachIndexed { index, (name, icon) ->
            val x = (index % cols) * cellW
            val y = (index / cols) * cellH
            for (dark in listOf(false, true)) {
                val left = x + if (dark) cellW / 2 else 0
                val background = if (dark) Color.rgb(26,20,16) else Color.rgb(255,251,247)
                canvas.drawRect(left.toFloat(), y.toFloat(), (left+cellW/2).toFloat(), (y+cellH).toFloat(), Paint().apply { color = background })
                for ((offset, size) in listOf(10 to 48, 74 to 16)) {
                    val drawable = requireNotNull(AppCompatResources.getDrawable(context, icon)).mutate()
                    if (!name.startsWith("badge_") && name !in listOf("cat","dog","bird","rabbit","paw")) drawable.setTint(if(dark) Color.rgb(255,182,141) else Color.rgb(169,83,26))
                    drawable.setBounds(left+offset, y+16, left+offset+size, y+16+size)
                    drawable.draw(canvas)
                }
                text.color = if(dark) Color.WHITE else Color.BLACK
                canvas.drawText(name, left+6f, y+101f, text)
            }
        }
        bitmap.captureRoboImage("src/test/screenshots/icon-contact-sheet.png")
        File("../docs").mkdirs()
        File("../docs/icons-preview.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
