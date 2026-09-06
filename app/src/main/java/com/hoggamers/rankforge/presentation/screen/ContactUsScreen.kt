package com.hoggamers.rankforge.presentation.screen

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hoggamers.rankforge.R
import com.hoggamers.rankforge.presentation.component.RankForgeScreenContainer
import com.hoggamers.rankforge.presentation.theme.RankForgeSpacing
import kotlinx.coroutines.launch

private const val CONTACT_EMAIL = "pointiq.officials@gmail.com"
private const val INSTAGRAM_URL =
    "https://www.instagram.com/esporton_official?stkn=enNjdHRweXZzZGhz"
private const val WHATSAPP_URL =
    "https://whatsapp.com/channel/0029VbAWtjZ9mrGaqlCXna24"
private const val INSTAGRAM_PACKAGE = "com.instagram.android"
private const val WHATSAPP_PACKAGE = "com.whatsapp"

private val ContactUsNavy = Color(0xFF071B3E)
private val ContactUsBlue = Color(0xFF176AF7)

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

    RankForgeScreenContainer(
        modifier = Modifier.testTag(CONTACT_US_SCREEN_TEST_TAG),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.Top,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onHome) {
                Text(
                    text = stringResource(R.string.auth_home_action),
                    color = ContactUsBlue,
                    style = MaterialTheme.typography.labelLarge,
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            TextButton(onClick = onBack) {
                Text(
                    text = stringResource(R.string.back_action),
                    color = ContactUsBlue,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }

        Spacer(modifier = Modifier.height(RankForgeSpacing.Medium))

        Text(
            text = stringResource(R.string.contact_us_title),
            style = MaterialTheme.typography.headlineMedium,
            color = ContactUsNavy,
        )

        Spacer(modifier = Modifier.height(RankForgeSpacing.Large))

        ContactUsRow(
            icon = ContactIcon.Email,
            text = stringResource(R.string.contact_us_email),
            testTag = CONTACT_US_EMAIL_ROW_TEST_TAG,
            onClick = onEmailClick,
        )
        ContactUsRow(
            icon = ContactIcon.Instagram,
            text = stringResource(R.string.contact_us_instagram),
            testTag = CONTACT_US_INSTAGRAM_ROW_TEST_TAG,
            onClick = onInstagramClick,
        )
        ContactUsRow(
            icon = ContactIcon.WhatsApp,
            text = stringResource(R.string.contact_us_whatsapp),
            testTag = CONTACT_US_WHATSAPP_ROW_TEST_TAG,
            onClick = onWhatsAppClick,
        )
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
            .clip(RoundedCornerShape(RankForgeSpacing.Small))
            .clickable(onClick = onClick)
            .testTag(testTag)
            .padding(horizontal = RankForgeSpacing.Medium, vertical = RankForgeSpacing.Medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(icon.drawableRes),
            contentDescription = null,
            modifier = Modifier.size(24.dp),
        )
        Spacer(modifier = Modifier.size(RankForgeSpacing.Medium))
        Text(
            text = text,
            color = ContactUsNavy,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
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
