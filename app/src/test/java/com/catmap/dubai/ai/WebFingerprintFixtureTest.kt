package com.catmap.dubai.ai

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.catmap.dubai.util.ImageUtils
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.Base64

/**
 * Export actual Android descriptors for the browser port; never changes application code. Only runs when asked
 * (`CATMAP_EXPORT_FINGERPRINTS=1`), so ordinary test runs don't rewrite web/tests/fixtures.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WebFingerprintFixtureTest {
    @Test fun exportAndroidFingerprints() {
        org.junit.Assume.assumeTrue(System.getenv("CATMAP_EXPORT_FINGERPRINTS") == "1")
        var root = File(requireNotNull(System.getProperty("user.dir")))
        while (!File(root, "firestore.rules").exists()) root = root.parentFile ?: error("Project root missing")
        val inputs = listOf("tools/testdata/orange_tabby_1.jpg", "tools/testdata/orange_tabby_2.jpg", "tools/testdata/tuxedo_1.jpg", "tools/seed/photos/cat_orange.jpg")
            .filter { File(root, it).isFile }
        val analyzer = CatAnalyzer()
        val weightsMethod = CatAnalyzer::class.java.getDeclaredMethod("centreWeights", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, FloatArray::class.java).apply { isAccessible = true }
        val describeMethod = CatAnalyzer::class.java.getDeclaredMethod("describe", IntArray::class.java, FloatArray::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType).apply { isAccessible = true }
        fun pixels(bitmap: Bitmap): ByteArray {
            val argb = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(argb, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            return ByteArray(argb.size * 4).also { out -> argb.forEachIndexed { i, color -> out[i*4]=(color shr 16).toByte(); out[i*4+1]=(color shr 8).toByte(); out[i*4+2]=color.toByte(); out[i*4+3]=(color ushr 24).toByte() } }
        }
        val fixtures = inputs.map { path ->
            val source = requireNotNull(BitmapFactory.decodeFile(File(root, path).absolutePath))
            val post = ImageUtils.scaleToMax(source, 128)
            val analysis = ImageUtils.scaleToMax(source, 512)
            val argb = IntArray(analysis.width * analysis.height)
            analysis.getPixels(argb,0,analysis.width,0,0,analysis.width,analysis.height)
            val weights = FloatArray(argb.size)
            weightsMethod.invoke(analyzer,analysis.width,analysis.height,weights)
            val result = describeMethod.invoke(analyzer,argb,weights,analysis.width,analysis.height) as CatAnalysis
            val fingerprint = PostAppearance.describe(source)
            assertEquals(27,fingerprint.size)
            assertEquals(117,result.features.size)
            """{"file":"$path","post":{"width":${post.width},"height":${post.height},"rgba":"${Base64.getEncoder().encodeToString(pixels(post))}","values":${fingerprint.joinToString(",","[","]")}},"analysis":{"width":${analysis.width},"height":${analysis.height},"rgba":"${Base64.getEncoder().encodeToString(pixels(analysis))}","values":${result.features.joinToString(",","[","]")}}}"""
        }
        val output = File(root,"web/tests/fixtures/android-fingerprints.json")
        requireNotNull(output.parentFile).mkdirs()
        output.writeText(fixtures.joinToString(",\n","[\n","\n]\n"))
    }
}
