package com.hoggamers.rankforge.presentation.component

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.hoggamers.rankforge.R

private val PointIqHomeBackground = Color(0xFF031225)
private val PointIqHomeAmbientBlue = Color(0xFF0B386F)
private val PointIqHomeHeader = Color(0xFFF6F8FF)
private val PointIqHomeHeaderBlue = Color(0xFF176AF7)
private val PointIqMenuEnabledIcon = Color(0xFF5AAEFF)
private val PointIqMenuDisabled = Color(0xFF7D9DCE)
private val PointIqMenuSeparator = Color(0xFF176AF7).copy(alpha = 0.55f)

const val LOGGED_IN_HOME_MENU_BUTTON_TEST_TAG = "logged_in_home_menu_button"
const val LOGGED_IN_HOME_BACK_ITEM_TEST_TAG = "logged_in_home_back_item"
const val LOGGED_IN_HOME_DRAWER_TEST_TAG = "logged_in_home_drawer"
const val LOGGED_IN_HOME_ACCOUNT_ITEM_TEST_TAG = "logged_in_home_account_item"
const val LOGGED_IN_HOME_ALL_TOURNAMENTS_ITEM_TEST_TAG =
    "logged_in_home_all_tournaments_item"
const val LOGGED_IN_HOME_NOTIFICATIONS_ITEM_TEST_TAG =
    "logged_in_home_notifications_item"
const val LOGGED_IN_HOME_SETTINGS_ITEM_TEST_TAG =
    "logged_in_home_settings_item"
const val LOGGED_IN_HOME_CONTACT_US_ITEM_TEST_TAG =
    "logged_in_home_contact_us_item"

@Composable
fun LoggedInHomeMenuShell(
    onOpenAccount: () -> Unit,
    onOpenAllTournaments: () -> Unit,
    onOpenContactUs: () -> Unit = {},
    content: @Composable () -> Unit,
    openDrawerOnEnter: Boolean = false,
    onDrawerOpenRequestConsumed: () -> Unit = {},
) {
    val openMenuDescription = stringResource(R.string.logged_in_home_open_menu)
    var isMenuOpen by remember { mutableStateOf(false) }
    var skipCloseAfterOpenRequest by remember { mutableStateOf(false) }

    LaunchedEffect(openDrawerOnEnter) {
        if (openDrawerOnEnter) {
            isMenuOpen = true
            skipCloseAfterOpenRequest = true
            onDrawerOpenRequestConsumed()
        } else if (skipCloseAfterOpenRequest) {
            skipCloseAfterOpenRequest = false
        } else {
            isMenuOpen = false
        }
    }

    BackHandler(enabled = isMenuOpen) {
        isMenuOpen = false
    }

    PointIqHomeSystemBars()

    if (isMenuOpen) {
        PointIqFullScreenMenu(
            onBack = { isMenuOpen = false },
            onOpenAccount = {
                onOpenAccount()
            },
            onOpenAllTournaments = {
                onOpenAllTournaments()
            },
            onOpenContactUs = {
                onOpenContactUs()
            },
        )
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .pointIqHomeBackground(),
        ) {
            PointIqHomeHeader(
                onMenuClick = { isMenuOpen = true },
                openMenuDescription = openMenuDescription,
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                content()
            }
        }
    }
}

@Composable
private fun PointIqFullScreenMenu(
    onBack: () -> Unit,
    onOpenAccount: () -> Unit,
    onOpenAllTournaments: () -> Unit,
    onOpenContactUs: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .pointIqHomeBackground()
            .testTag(LOGGED_IN_HOME_DRAWER_TEST_TAG)
            .padding(start = 24.dp, top = 28.dp, end = 24.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .offset(x = (-8).dp),
            verticalAlignment = Alignment.Top,
        ) {
            val backDescription = stringResource(R.string.back_action)
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clickable(onClick = onBack)
                    .testTag(LOGGED_IN_HOME_BACK_ITEM_TEST_TAG)
                    .semantics {
                        contentDescription = backDescription
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "<",
                    color = PointIqHomeHeader,
                    fontSize = 36.sp,
                    lineHeight = 40.sp,
                    fontWeight = FontWeight.Normal,
                    modifier = Modifier.offset(y = (-6).dp),
                )
            }
            Text(
                text = stringResource(R.string.logged_in_home_menu_title),
                color = PointIqHomeHeader,
                fontSize = 24.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        PointIqMenuPrimaryItem(
            icon = Icons.Filled.Person,
            text = stringResource(R.string.auth_account_section_title),
            testTag = LOGGED_IN_HOME_ACCOUNT_ITEM_TEST_TAG,
            onClick = onOpenAccount,
        )
        PointIqMenuSeparator()

        PointIqMenuPrimaryItem(
            icon = Icons.AutoMirrored.Filled.List,
            text = stringResource(R.string.logged_in_home_all_tournaments),
            testTag = LOGGED_IN_HOME_ALL_TOURNAMENTS_ITEM_TEST_TAG,
            onClick = onOpenAllTournaments,
        )
        PointIqMenuSeparator()

        PointIqMenuPrimaryItem(
            icon = Icons.Filled.Email,
            text = stringResource(R.string.logged_in_home_contact_us),
            testTag = LOGGED_IN_HOME_CONTACT_US_ITEM_TEST_TAG,
            onClick = onOpenContactUs,
        )
        PointIqMenuSeparator()

        PointIqMenuDisabledItem(
            icon = Icons.Filled.Notifications,
            text = stringResource(R.string.logged_in_home_notifications),
            testTag = LOGGED_IN_HOME_NOTIFICATIONS_ITEM_TEST_TAG,
        )
        PointIqMenuSeparator()

        PointIqMenuDisabledItem(
            icon = Icons.Filled.Settings,
            text = stringResource(R.string.logged_in_home_settings),
            testTag = LOGGED_IN_HOME_SETTINGS_ITEM_TEST_TAG,
        )
        PointIqMenuSeparator()
    }
}

@Composable
private fun PointIqMenuPrimaryItem(
    icon: ImageVector,
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
            imageVector = icon,
            contentDescription = null,
            tint = PointIqMenuEnabledIcon,
            modifier = Modifier.size(28.dp),
        )
        Spacer(modifier = Modifier.size(16.dp))
        Text(
            text = text,
            color = PointIqHomeHeader,
            fontSize = 18.sp,
            lineHeight = 24.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = PointIqMenuEnabledIcon,
            modifier = Modifier.size(28.dp),
        )
    }
}

@Composable
private fun PointIqMenuSeparator() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(PointIqMenuSeparator),
    )
}

@Composable
private fun PointIqMenuDisabledItem(
    icon: ImageVector,
    text: String,
    testTag: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .testTag(testTag)
            .semantics {
                disabled()
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = PointIqMenuDisabled,
            modifier = Modifier.size(28.dp),
        )
        Spacer(modifier = Modifier.size(16.dp))
        Text(
            text = text,
            color = PointIqMenuDisabled,
            fontSize = 18.sp,
            lineHeight = 24.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun PointIqHomeHeader(
    onMenuClick: () -> Unit,
    openMenuDescription: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = 20.dp,
                top = 16.dp,
                end = 24.dp,
                bottom = 8.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onMenuClick,
            modifier = Modifier
                .testTag(LOGGED_IN_HOME_MENU_BUTTON_TEST_TAG)
                .semantics {
                    contentDescription = openMenuDescription
                },
        ) {
            Icon(
                imageVector = Icons.Filled.Menu,
                contentDescription = null,
                tint = PointIqHomeHeader,
                modifier = Modifier.size(28.dp),
            )
        }

        Spacer(modifier = Modifier.size(12.dp))

        Image(
            painter = painterResource(R.drawable.pointiq_brand_mark),
            contentDescription = null,
            modifier = Modifier.size(40.dp),
        )

        Spacer(modifier = Modifier.size(10.dp))

        val brandText = buildAnnotatedString {
            withStyle(
                SpanStyle(
                    color = PointIqHomeHeader,
                    fontWeight = FontWeight.Bold,
                ),
            ) {
                append(stringResource(R.string.pointiq_brand_point))
            }
            withStyle(
                SpanStyle(
                    color = PointIqHomeHeaderBlue,
                    fontWeight = FontWeight.Bold,
                ),
            ) {
                append(stringResource(R.string.pointiq_brand_iq))
            }
        }

        Text(
            text = brandText,
            fontSize = 23.sp,
            lineHeight = 27.sp,
        )
    }
}

@Composable
private fun PointIqHomeSystemBars() {
    val view = LocalView.current
    val window = (view.context as? Activity)?.window

    DisposableEffect(window, view) {
        if (window == null) {
            onDispose { }
        } else {
            val windowInsetsController = WindowCompat.getInsetsController(window, view)
            val previousStatusBarColor = window.statusBarColor
            val previousNavigationBarColor = window.navigationBarColor
            val previousLightStatusBars = windowInsetsController.isAppearanceLightStatusBars
            val previousLightNavigationBars = windowInsetsController.isAppearanceLightNavigationBars

            window.statusBarColor = PointIqHomeBackground.toArgb()
            window.navigationBarColor = PointIqHomeBackground.toArgb()
            windowInsetsController.isAppearanceLightStatusBars = false
            windowInsetsController.isAppearanceLightNavigationBars = false

            onDispose {
                window.statusBarColor = previousStatusBarColor
                window.navigationBarColor = previousNavigationBarColor
                windowInsetsController.isAppearanceLightStatusBars = previousLightStatusBars
                windowInsetsController.isAppearanceLightNavigationBars = previousLightNavigationBars
            }
        }
    }
}

private fun Modifier.pointIqHomeBackground(): Modifier =
    background(PointIqHomeBackground).drawBehind {
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    PointIqHomeAmbientBlue.copy(alpha = 0.42f),
                    PointIqHomeAmbientBlue.copy(alpha = 0.16f),
                    Color.Transparent,
                ),
                center = Offset(-size.width * 0.04f, size.height * 0.34f),
                radius = size.width * 0.78f,
            ),
        )
    }
