package dev.rec877.north

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performClick
import dev.rec877.north.ui.MediaPrivacyGate
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Rule
import org.junit.Assert.*
import org.junit.Test

class OnDeviceClassifierTest {
    @get:Rule val compose = createComposeRule()

    @Test fun bundledModelLoadsAndReturnsFiniteScoreWithoutNetwork() {
        val classifier = OnDeviceMediaClassifier(InstrumentationRegistry.getInstrumentation().targetContext)
        val bitmap = Bitmap.createBitmap(224, 224, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GREEN) }
        try {
            val score = classifier.score(bitmap)
            assertTrue(score.isFinite())
            assertTrue(score in 0f..1f)
        } finally { bitmap.recycle(); classifier.close() }
    }

    @Test fun hiddenContentIsNotComposedUntilExplicitReveal() {
        compose.setContent {
            MediaPrivacyGate(listOf(Media("sensitive", url = "https://invalid.example/image", sensitive = true)), OkHttpClient()) {
                Text("実際のメディア")
            }
        }
        compose.onNodeWithText("実際のメディア").assertDoesNotExist()
        compose.onNodeWithText("このメディアを表示").performClick()
        compose.onNodeWithText("実際のメディア").assertIsDisplayed()
    }

    @Test fun videosAndTaggedMediaNeverRequireDownloadForClassification() = runBlocking {
        val classifier = OnDeviceMediaClassifier(InstrumentationRegistry.getInstrumentation().targetContext)
        val client = OkHttpClient.Builder().addInterceptor { error("ネットワークを呼び出してはいけません") }.build()
        try {
            assertEquals(MediaVerdict.BLOCK, classifier.classify(Media("video", kind = "VIDEO", url = "https://invalid.example/video"), client))
            assertEquals(MediaVerdict.BLOCK, classifier.classify(Media("image", url = "https://invalid.example/image", sensitive = true), client))
        } finally { classifier.close() }
    }
}
