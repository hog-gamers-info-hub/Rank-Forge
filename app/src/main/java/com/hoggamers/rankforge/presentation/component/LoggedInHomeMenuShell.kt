package com.hoggamers.rankforge.presentation.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.hoggamers.rankforge.R
import com.hoggamers.rankforge.presentation.theme.RankForgeSpacing
import kotlinx.coroutines.launch

const val LOGGED_IN_HOME_MENU_BUTTON_TEST_TAG = "logged_in_home_menu_button"
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
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val openMenuDescription = stringResource(R.string.logged_in_home_open_menu)
    var skipCloseAfterOpenRequest by remember { mutableStateOf(false) }

    LaunchedEffect(openDrawerOnEnter) {
        if (openDrawerOnEnter) {
            drawerState.open()
            skipCloseAfterOpenRequest = true
            onDrawerOpenRequestConsumed()
        } else if (skipCloseAfterOpenRequest) {
            skipCloseAfterOpenRequest = false
        } else {
            drawerState.snapTo(DrawerValue.Closed)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.testTag(LOGGED_IN_HOME_DRAWER_TEST_TAG),
            ) {
                Text(
                    text = stringResource(R.string.logged_in_home_menu_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(RankForgeSpacing.Medium),
                )

                NavigationDrawerItem(
                    label = {
                        Text(
                            text = stringResource(R.string.auth_account_section_title),
                        )
                    },
                    selected = false,
                    onClick = {
                        scope.launch {
                            drawerState.close()
                            onOpenAccount()
                        }
                    },
                    modifier = Modifier.testTag(
                        LOGGED_IN_HOME_ACCOUNT_ITEM_TEST_TAG,
                    ),
                )

                NavigationDrawerItem(
                    label = {
                        Text(
                            text = stringResource(
                                R.string.logged_in_home_all_tournaments,
                            ),
                        )
                    },
                    selected = false,
                    onClick = {
                        onOpenAllTournaments()
                    },
                    modifier = Modifier.testTag(
                        LOGGED_IN_HOME_ALL_TOURNAMENTS_ITEM_TEST_TAG,
                    ),
                )

                NavigationDrawerItem(
                    label = {
                        Text(
                            text = stringResource(
                                R.string.logged_in_home_subscription,
                            ),
                        )
                    },
                    selected = false,
                    onClick = {},
                    modifier = Modifier
                        .testTag(LOGGED_IN_HOME_SUBSCRIPTION_ITEM_TEST_TAG)
                        .alpha(0.38f)
                        .semantics {
                            disabled()
                        },
                )

                NavigationDrawerItem(
                    label = {
                        Text(
                            text = stringResource(
                                R.string.logged_in_home_notifications,
                            ),
                        )
                    },
                    selected = false,
                    onClick = {},
                    modifier = Modifier
                        .testTag(LOGGED_IN_HOME_NOTIFICATIONS_ITEM_TEST_TAG)
                        .alpha(0.38f)
                        .semantics {
                            disabled()
                        },
                )

                NavigationDrawerItem(
                    label = {
                        Text(
                            text = stringResource(
                                R.string.logged_in_home_settings,
                            ),
                        )
                    },
                    selected = false,
                    onClick = {},
                    modifier = Modifier
                        .testTag(LOGGED_IN_HOME_SETTINGS_ITEM_TEST_TAG)
                        .alpha(0.38f)
                        .semantics {
                            disabled()
                        },
                )
            }
        },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = RankForgeSpacing.Large,
                        top = RankForgeSpacing.Large,
                    ),
                horizontalArrangement = Arrangement.Start,
            ) {
                IconButton(
                    onClick = {
                        scope.launch {
                            drawerState.open()
                        }
                    },
                    modifier = Modifier
                        .testTag(LOGGED_IN_HOME_MENU_BUTTON_TEST_TAG)
                        .semantics {
                            contentDescription = openMenuDescription
                        },
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.width(24.dp),
                    ) {
                        repeat(3) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(2.dp)
                                    .background(
                                        MaterialTheme.colorScheme.onSurface,
                                    ),
                            )
                        }
                    }
                }
            }

            content()
        }
    }
}
