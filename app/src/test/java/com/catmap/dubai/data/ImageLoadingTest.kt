package com.catmap.dubai.data

import android.app.Application
import android.graphics.Bitmap
import com.catmap.dubai.util.ImageUtils
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ImageLoadingTest {
    @Test fun targetSizeAndMemoryDiskHits()=runBlocking {
        val context=RuntimeEnvironment.getApplication()
        var fetches=0
        val fixture=ImageUtils.compress(Bitmap.createBitmap(360,360,Bitmap.Config.ARGB_8888).apply {eraseColor(android.graphics.Color.rgb(180,80,30))},85)
        val repository=ImageRepository(context,null) {fetches++; fixture}
        repository.clearCache()
        val first=requireNotNull(repository.bitmap("cacheFixture",true,90))
        assertEquals(90,first.width)
        assertSame(first,repository.bitmap("cacheFixture",true,90))
        assertEquals(1,fetches)
        val restarted=ImageRepository(context,null) {fetches++; fixture}
        assertNotNull(restarted.bitmap("cacheFixture",true,90))
        assertEquals("disk cache avoids another fetch",1,fetches)
        restarted.clearCache()
    }
}
