package com.agustingv.puzzles

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.agustingv.puzzles.ui.menu.MenuScreen
import com.agustingv.puzzles.ui.path.PathScreen
import com.agustingv.puzzles.ui.path.PathViewModel
import com.agustingv.puzzles.ui.sliding.SlidingScreen
import com.agustingv.puzzles.ui.sliding.SlidingViewModel
import com.agustingv.puzzles.ui.theme.PuzzlesTheme
import com.agustingv.puzzles.ui.word.WordScreen
import com.agustingv.puzzles.ui.word.WordViewModel

private enum class Route { MENU, WORD, PATH, SLIDING }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            PuzzlesTheme { PuzzlesApp() }
        }
    }
}

@Composable
private fun PuzzlesApp() {
    var route by rememberSaveable { mutableStateOf(Route.MENU) }
    val word: WordViewModel = viewModel()
    val path: PathViewModel = viewModel()
    val sliding: SlidingViewModel = viewModel()
    val toMenu = { route = Route.MENU }

    when (route) {
        Route.MENU -> MenuScreen(
            onPlayWord = { length ->
                word.newGame(length)
                route = Route.WORD
            },
            onPlayPath = { difficulty ->
                path.newGame(difficulty)
                route = Route.PATH
            },
            onPlaySliding = { difficulty ->
                sliding.newGame(difficulty)
                route = Route.SLIDING
            },
        )
        Route.WORD -> WordScreen(viewModel = word, onExit = toMenu)
        Route.PATH -> PathScreen(viewModel = path, onExit = toMenu)
        Route.SLIDING -> SlidingScreen(viewModel = sliding, onExit = toMenu)
    }
}
