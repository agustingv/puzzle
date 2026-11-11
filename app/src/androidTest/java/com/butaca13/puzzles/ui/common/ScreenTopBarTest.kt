package com.butaca13.puzzles.ui.common

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScreenTopBarTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun theBarOpensTheSettingsWhereverTheyAreOffered() {
        var opened = 0
        rule.setContent {
            CompositionLocalProvider(LocalOpenSettings provides { opened++ }) {
                ScreenTopBar(title = "Juego", onBack = {})
            }
        }

        rule.onNodeWithContentDescription("Ajustes").performClick()

        assertEquals(1, opened)
    }

    @Test
    fun aGameTitleIsPlainTextEvenWithItsSecretLetter() {
        rule.setContent { ScreenTopBar(title = "Puzle Deslizante", onBack = {}, appHeader = false) }

        rule.onNodeWithText("Puzle Deslizante").assertExists()
    }

    @Test
    fun noButtonWhereTheyAreNotOffered() {
        rule.setContent { ScreenTopBar(title = "Ajustes", onBack = {}) }

        rule.onAllNodesWithContentDescription("Ajustes").assertCountEquals(0)
    }
}
