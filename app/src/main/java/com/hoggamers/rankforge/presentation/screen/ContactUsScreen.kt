package com.hoggamers.rankforge.presentation.screen

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hoggamers.rankforge.R
import com.hoggamers.rankforge.presentation.component.PointIqHomeSystemBars
import com.hoggamers.rankforge.presentation.component.PointIqPageHeader
import com.hoggamers.rankforge.presentation.component.pointIqHomeBackground
import kotlinx.coroutines.launch

private const val CONTACT_EMAIL = "pointiq.officials@gmail.com"
private const val INSTAGRAM_URL =
    "https://www.instagram.com/esporton_official?stkn=enNjdHRweXZzZGhz"
private const val WHATSAPP_URL =
    "https://whatsapp.com/channel/0029VbAWtjZ9mrGaqlCXna24"
private const val INSTAGRAM_PACKAGE = "com.instagram.android"
private const val WHATSAPP_PACKAGE = "com.whatsapp"

private val ContactUsIcon = Color(0xFF5AAEFF)
private val ContactUsText = Color(0xFFF6F8FF)
private val ContactUsSeparator = Color(0xFF176AF7).copy(alpha = 0.55f)

private enum class ContactIcon(@get:DrawableRes val drawableRes: Int) {
    Email(R.drawable.ic_contact_gmail),
    Instagram(R.drawable.ic_contact_instagram),
    WhatsApp(R.drawable.ic_contact_whatsapp),
}

const val CONTACT_US_SCREEN_TEST_TAG = "contact_us_screen"
const val CONTACT_US_EMAIL_ROW_TEST_TAG = "contact_us_email_row"
const val CONTACT_US_INSTAGRAM_ROW_TEST_TAG = "contact_us_instagram_row"
const val CONTACT_US_WHATSAPP_ROW_TEST_TAG = "contact_us_whatsapp_row"

@Composable
fun ContactUsRoute(
    onHome: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val launchFailureMessage = stringResource(R.string.contact_us_open_failure)

    fun showLaunchFailure() {
        scope.launch {
            snackbarHostState.showSnackbar(launchFailureMessage)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        ContactUsScreen(
            onHome = onHome,
            onBack = onBack,
            onEmailClick = {
                if (!openEmail(context)) showLaunchFailure()
            },
            onInstagramClick = {
                if (!openSocialLink(context, INSTAGRAM_URL, INSTAGRAM_PACKAGE)) {
                    showLaunchFailure()
                }
            },
            onWhatsAppClick = {
                if (!openSocialLink(context, WHATSAPP_URL, WHATSAPP_PACKAGE)) {
                    showLaunchFailure()
                }
            },
        )
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
fun ContactUsScreen(
    onHome: () -> Unit,
    onBack: () -> Unit,
    onEmailClick: () -> Unit = {},
    onInstagramClick: () -> Unit = {},
    onWhatsAppClick: () -> Unit = {},
) {
    BackHandler(onBack = onBack)
    PointIqHomeSystemBars()

    Column(
        modifier = Modifier
            .testTag(CONTACT_US_SCREEN_TEST_TAG)
            .fillMaxSize()
            .pointIqHomeBackground()
            .padding(start = 24.dp, top = 28.dp, end = 24.dp),
    ) {
        PointIqPageHeader(
            title = stringResource(R.string.contact_us_title),
            onBack = onBack,
            backTestTag = CONTACT_US_SCREEN_TEST_TAG + "_back",
        )

        Spacer(modifier = Modifier.height(24.dp))

        ContactUsRow(
            icon = ContactIcon.Email,
            text = stringResource(R.string.contact_us_email),
            testTag = CONTACT_US_EMAIL_ROW_TEST_TAG,
            onClick = onEmailClick,
        )
        ContactUsSeparator()
        ContactUsRow(
            icon = ContactIcon.Instagram,
            text = stringResource(R.string.contact_us_instagram),
            testTag = CONTACT_US_INSTAGRAM_ROW_TEST_TAG,
            onClick = onInstagramClick,
        )
        ContactUsSeparator()
        ContactUsRow(
            icon = ContactIcon.WhatsApp,
            text = stringResource(R.string.contact_us_whatsapp),
            testTag = CONTACT_US_WHATSAPP_ROW_TEST_TAG,
            onClick = onWhatsAppClick,
        )
        ContactUsSeparator()
    }
}

@Composable
private fun ContactUsRow(
    icon: ContactIcon,
    text: String,
    testTag: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clickable(onClick = onClick)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(icon.drawableRes),
            contentDescription = null,
            tint = ContactUsIcon,
            modifier = Modifier.size(28.dp),
        )
        Spacer(modifier = Modifier.size(16.dp))
        Text(
            text = text,
            color = ContactUsText,
            fontSize = 18.sp,
            lineHeight = 24.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun ContactUsSeparator() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(ContactUsSeparator),
    )
}

private fun openEmail(context: Context): Boolean {
    return tryLaunchIntent(buildEmailIntent()) { intent ->
        context.startActivity(intent)
    }
}

private fun openSocialLink(
    context: Context,
    url: String,
    packageName: String,
): Boolean {
    val appIntent = buildSocialAppIntent(url, packageName)
    val browserIntent = buildBrowserIntent(url)

    return launchWithFallback(appIntent, browserIntent) { intent ->
        context.startActivity(intent)
    }
}

internal fun buildEmailIntent(): Intent =
    Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$CONTACT_EMAIL"))

internal fun buildSocialAppIntent(
    url: String,
    packageName: String,
): Intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).setPackage(packageName)

internal fun buildBrowserIntent(url: String): Intent =
    Intent(Intent.ACTION_VIEW, Uri.parse(url))

internal fun tryLaunchIntent(
    intent: Intent,
    launch: (Intent) -> Unit,
): Boolean {
    return try {
        launch(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
}

internal fun launchWithFallback(
    primary: Intent,
    fallback: Intent,
    launch: (Intent) -> Unit,
): Boolean = tryLaunchIntent(primary, launch) || tryLaunchIntent(fallback, launch)
