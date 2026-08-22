package com.hoggamers.rankforge.presentation.component

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import com.hoggamers.rankforge.R

private val PointIqHomeHeaderNavy = Color(0xFF071B3E)
private val PointIqHomeHeaderBlue = Color(0xFF176AF7)
private val PointIqHomeBackgroundTop = Color(0xFFFDFEFF)
private val PointIqHomeBackgroundBottom = Color(0xFFF3FAFF)
private val PointIqMenuCard = Color(0xFFF9FBFF)
private val PointIqMenuCardBorder = Color(0xFFE5EDF9)
private val PointIqMenuMuted = Color(0xFFA3AFC4)

const val LOGGED_IN_HOME_MENU_BUTTON_TEST_TAG = "logged_in_home_menu_button"
const val LOGGED_IN_HOME_BACK_ITEM_TEST_TAG = "logged_in_home_back_item"
const val LOGGED_IN_HOME_DRAWER_TEST_TAG = "logged_in_home_drawer"
const val LOGGED_IN_HOME_ACCOUNT_ITEM_TEST_TAG = "logged_in_home_account_item"
const val LOGGED_IN_HOME_ALL_TOURNAMENTS_ITEM_TEST_TAG =
    "logged_in_home_all_tournaments_item"
const val LOGGED_IN_HOME_SUBSCRIPTION_ITEM_TEST_TAG =
    "logged_in_home_subscription_item"
const val LOGGED_IN_HOME_NOTIFICATIONS_ITEM_TEST_TAG =
    "logged_in_home_notifications_item"
const val LOGGED_IN_HOME_SETTINGS_ITEM_TEST_TAG =
    "logged_in_home_settings_item"

@Composable
fun LoggedInHomeMenuShell(
    onOpenAccount: () -> Unit,
    onOpenAllTournaments: () -> Unit,
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

    if (isMenuOpen) {
        PointIqFullScreenMenu(
            onBack = { isMenuOpen = false },
            onOpenAccount = {
                isMenuOpen = false
                onOpenAccount()
            },
            onOpenAllTournaments = {
                isMenuOpen = false
                onOpenAllTournaments()
            },
        )
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(pointIqHomeBackgroundBrush()),
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
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(pointIqHomeBackgroundBrush())
            .testTag(LOGGED_IN_HOME_DRAWER_TEST_TAG)
            .padding(horizontal = 24.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 26.dp, bottom = 30.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.logged_in_home_menu_title),
                color = PointIqHomeHeaderNavy,
                fontSize = 30.sp,
                lineHeight = 34.sp,
                fontWeight = FontWeight.Bold,
            )

            TextButton(
                onClick = onBack,
                modifier = Modifier.testTag(LOGGED_IN_HOME_BACK_ITEM_TEST_TAG),
            ) {
                Text(
                    text = stringResource(R.string.back_action),
                    color = PointIqHomeHeaderBlue,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }

        PointIqMenuPrimaryItem(
            text = stringResource(R.string.auth_account_section_title),
            testTag = LOGGED_IN_HOME_ACCOUNT_ITEM_TEST_TAG,
            onClick = onOpenAccount,
        )

        Spacer(modifier = Modifier.height(14.dp))

        PointIqMenuPrimaryItem(
            text = stringResource(R.string.logged_in_home_all_tournaments),
            testTag = LOGGED_IN_HOME_ALL_TOURNAMENTS_ITEM_TEST_TAG,
            onClick = onOpenAllTournaments,
        )

        Spacer(modifier = Modifier.height(28.dp))

        PointIqMenuDisabledItem(
            text = stringResource(R.string.logged_in_home_subscription),
            testTag = LOGGED_IN_HOME_SUBSCRIPTION_ITEM_TEST_TAG,
        )

        Spacer(modifier = Modifier.height(26.dp))

        PointIqMenuDisabledItem(
            text = stringResource(R.string.logged_in_home_notifications),
            testTag = LOGGED_IN_HOME_NOTIFICATIONS_ITEM_TEST_TAG,
        )

        Spacer(modifier = Modifier.height(26.dp))

        PointIqMenuDisabledItem(
            text = stringResource(R.string.logged_in_home_settings),
            testTag = LOGGED_IN_HOME_SETTINGS_ITEM_TEST_TAG,
        )
    }
}

@Composable
private fun PointIqMenuPrimaryItem(
    text: String,
    testTag: String,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(68.dp)
            .shadow(
                elevation = 4.dp,
                shape = shape,
                ambientColor = PointIqHomeHeaderBlue.copy(alpha = 0.06f),
                spotColor = PointIqHomeHeaderBlue.copy(alpha = 0.08f),
            )
            .clip(shape)
            .background(PointIqMenuCard)
            .border(1.dp, PointIqMenuCardBorder, shape)
            .clickable(onClick = onClick)
            .testTag(testTag)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = text,
            color = PointIqHomeHeaderNavy,
            fontSize = 18.sp,
            lineHeight = 22.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun PointIqMenuDisabledItem(
    text: String,
    testTag: String,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp)
            .testTag(testTag)
            .alpha(0.9f)
            .semantics {
                disabled()
            }
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = text,
            color = PointIqMenuMuted,
            fontSize = 17.sp,
            lineHeight = 21.sp,
            fontWeight = FontWeight.Normal,
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
            Column(
                verticalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier.width(28.dp),
            ) {
                repeat(3) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.5.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(PointIqHomeHeaderNavy),
                    )
                }
            }
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
                    color = PointIqHomeHeaderNavy,
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

private fun pointIqHomeBackgroundBrush(): Brush =
    Brush.verticalGradient(
        colors = listOf(
            PointIqHomeBackgroundTop,
            PointIqHomeBackgroundBottom,
        ),
    )
