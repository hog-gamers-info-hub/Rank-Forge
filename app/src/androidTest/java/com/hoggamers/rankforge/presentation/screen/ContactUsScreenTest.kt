package com.hoggamers.rankforge.presentation.screen

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hoggamers.rankforge.R
import com.hoggamers.rankforge.presentation.theme.RankForgeTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ContactUsScreenTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun showsExactlyTheRequestedContactMethods() {
        composeTestRule.setContent {
            RankForgeTheme {
                ContactUsScreen(
                    onHome = {},
                    onBack = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(CONTACT_US_SCREEN_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.contact_us_title)).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.contact_us_email)).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.contact_us_instagram)).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.contact_us_whatsapp)).assertIsDisplayed()
    }

    @Test
    fun everyContactRowIsFullyClickable() {
        var emailClicks = 0
        var instagramClicks = 0
        var whatsAppClicks = 0

        composeTestRule.setContent {
            RankForgeTheme {
                ContactUsScreen(
                    onHome = {},
                    onBack = {},
                    onEmailClick = { emailClicks += 1 },
                    onInstagramClick = { instagramClicks += 1 },
                    onWhatsAppClick = { whatsAppClicks += 1 },
                )
            }
        }

        composeTestRule.onNodeWithTag(CONTACT_US_EMAIL_ROW_TEST_TAG).performClick()
        composeTestRule.onNodeWithTag(CONTACT_US_INSTAGRAM_ROW_TEST_TAG).performClick()
        composeTestRule.onNodeWithTag(CONTACT_US_WHATSAPP_ROW_TEST_TAG).performClick()
        composeTestRule.waitForIdle()

        composeTestRule.runOnIdle {
            assertEquals(1, emailClicks)
            assertEquals(1, instagramClicks)
            assertEquals(1, whatsAppClicks)
        }
    }

    @Test
    fun contactIntentsUseTheRequestedRecipientAndUrls() {
        val emailIntent = buildEmailIntent()
        val instagramUrl = "https://www.instagram.com/esporton_official?stkn=enNjdHRweXZzZGhz"
        val whatsAppUrl = "https://whatsapp.com/channel/0029VbAWtjZ9mrGaqlCXna24"

        assertEquals(Intent.ACTION_SENDTO, emailIntent.action)
        assertEquals(Uri.parse("mailto:pointiq.officials@gmail.com"), emailIntent.data)
        assertEquals(
            Uri.parse(instagramUrl),
            buildSocialAppIntent(instagramUrl, "com.instagram.android").data,
        )
        assertEquals(
            "com.instagram.android",
            buildSocialAppIntent(instagramUrl, "com.instagram.android").`package`,
        )
        assertEquals(Uri.parse(whatsAppUrl), buildBrowserIntent(whatsAppUrl).data)
    }

    @Test
    fun appSpecificFailureFallsBackToBrowserIntent() {
        val appIntent = buildSocialAppIntent(
            "https://www.instagram.com/esporton_official?stkn=enNjdHRweXZzZGhz",
            "com.instagram.android",
        )
        val browserIntent = buildBrowserIntent(
            "https://www.instagram.com/esporton_official?stkn=enNjdHRweXZzZGhz",
        )
        val launched = mutableListOf<Intent>()

        val didLaunch = launchWithFallback(appIntent, browserIntent) { intent ->
            if (intent === appIntent) throw ActivityNotFoundException()
            launched += intent
        }

        assertTrue(didLaunch)
        assertEquals(listOf(browserIntent), launched)
    }

    @Test
    fun noExternalHandlerFailsWithoutThrowing() {
        val didLaunch = launchWithFallback(
            buildSocialAppIntent("https://example.com", "com.example.missing"),
            buildBrowserIntent("https://example.com"),
        ) {
            throw ActivityNotFoundException()
        }

        assertFalse(didLaunch)
    }
}
