package com.butaca13.puzzles.ui.menu

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.butaca13.puzzles.R
import com.butaca13.puzzles.ui.common.LocalInTab
import com.butaca13.puzzles.ui.theme.Card
import com.butaca13.puzzles.ui.theme.PastelPink

/** The app's three places, in the bar at the bottom: the games, the player's avatar, and the settings. */
enum class MainTab(@StringRes val label: Int, @DrawableRes val icon: Int) {
    PLAY(R.string.nav_play, R.drawable.ic_nav_play),
    PROFILE(R.string.nav_profile, R.drawable.ic_nav_profile),
    SETTINGS(R.string.nav_settings, R.drawable.ic_nav_settings),
}

/**
 * [content] with the bar of [MainTab]s under it. The content gets the room above the bar and does not
 * pad itself again for the system bars the bar already keeps clear of.
 */
@Composable
fun WithMainNavBar(selected: MainTab, onSelect: (MainTab) -> Unit, content: @Composable () -> Unit) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = { MainNavBar(selected, onSelect) },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            CompositionLocalProvider(LocalInTab provides true, content = content)
        }
    }
}

@Composable
private fun MainNavBar(selected: MainTab, onSelect: (MainTab) -> Unit) {
    NavigationBar(containerColor = Card) {
        MainTab.entries.forEach { tab ->
            val here = tab == selected
            NavigationBarItem(
                selected = here,
                onClick = { if (!here) onSelect(tab) },
                icon = { Icon(painterResource(tab.icon), contentDescription = null) },
                label = { Text(stringResource(tab.label), fontWeight = if (here) FontWeight.Bold else FontWeight.Medium) },
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = PastelPink,
                    // The pink pill is light in both themes: the icon on it stays dark.
                    selectedIconColor = OnPill,
                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}

private val OnPill = Color(0xFF1C1C28)
