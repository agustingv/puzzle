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
import com.agustingv.puzzles.ui.crossword.CrosswordScreen
import com.agustingv.puzzles.ui.crossword.CrosswordViewModel
import com.agustingv.puzzles.ui.hangman.HangmanScreen
import com.agustingv.puzzles.ui.hangman.HangmanViewModel
import com.agustingv.puzzles.ui.kakuro.KakuroScreen
import com.agustingv.puzzles.ui.kakuro.KakuroViewModel
import com.agustingv.puzzles.ui.memorama.MemoramaScreen
import com.agustingv.puzzles.ui.memorama.MemoramaViewModel
import com.agustingv.puzzles.ui.menu.MenuScreen
import com.agustingv.puzzles.ui.path.PathScreen
import com.agustingv.puzzles.ui.path.PathViewModel
import com.agustingv.puzzles.ui.race.RaceScreen
import com.agustingv.puzzles.ui.race.RaceViewModel
import com.agustingv.puzzles.ui.sliding.SlidingScreen
import com.agustingv.puzzles.ui.sliding.SlidingViewModel
import com.agustingv.puzzles.ui.sudoku.SudokuScreen
import com.agustingv.puzzles.ui.sudoku.SudokuViewModel
import com.agustingv.puzzles.ui.theme.PuzzlesTheme
import com.agustingv.puzzles.ui.word.WordScreen
import com.agustingv.puzzles.ui.word.WordViewModel
import com.agustingv.puzzles.ui.wordsearch.WordSearchScreen
import com.agustingv.puzzles.ui.wordsearch.WordSearchViewModel

private enum class Route { MENU, RACE, HANGMAN, WORD, WORD_SEARCH, CROSSWORD, SUDOKU, PATH, KAKURO, MEMORAMA, SLIDING }

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
    val race: RaceViewModel = viewModel()
    val hangman: HangmanViewModel = viewModel()
    val word: WordViewModel = viewModel()
    val wordSearch: WordSearchViewModel = viewModel()
    val crossword: CrosswordViewModel = viewModel()
    val sudoku: SudokuViewModel = viewModel()
    val path: PathViewModel = viewModel()
    val kakuro: KakuroViewModel = viewModel()
    val memorama: MemoramaViewModel = viewModel()
    val sliding: SlidingViewModel = viewModel()
    val toMenu = { route = Route.MENU }

    when (route) {
        Route.MENU -> MenuScreen(
            onPlayRace = { length ->
                race.newGame(length)
                route = Route.RACE
            },
            onPlayHangman = { length ->
                hangman.newGame(length)
                route = Route.HANGMAN
            },
            onPlayWord = { length ->
                word.newGame(length)
                route = Route.WORD
            },
            onPlayWordSearch = { difficulty ->
                wordSearch.newGame(difficulty)
                route = Route.WORD_SEARCH
            },
            onPlayCrossword = { difficulty ->
                crossword.newGame(difficulty)
                route = Route.CROSSWORD
            },
            onPlaySudoku = { difficulty ->
                sudoku.newGame(difficulty)
                route = Route.SUDOKU
            },
            onPlayPath = { difficulty ->
                path.newGame(difficulty)
                route = Route.PATH
            },
            onPlayKakuro = { difficulty ->
                kakuro.newGame(difficulty)
                route = Route.KAKURO
            },
            onPlayMemorama = { difficulty ->
                memorama.newGame(difficulty)
                route = Route.MEMORAMA
            },
            onPlaySliding = { difficulty ->
                sliding.newGame(difficulty)
                route = Route.SLIDING
            },
        )
        Route.RACE -> RaceScreen(viewModel = race, onExit = toMenu)
        Route.HANGMAN -> HangmanScreen(viewModel = hangman, onExit = toMenu)
        Route.WORD -> WordScreen(viewModel = word, onExit = toMenu)
        Route.WORD_SEARCH -> WordSearchScreen(viewModel = wordSearch, onExit = toMenu)
        Route.CROSSWORD -> CrosswordScreen(viewModel = crossword, onExit = toMenu)
        Route.SUDOKU -> SudokuScreen(viewModel = sudoku, onExit = toMenu)
        Route.PATH -> PathScreen(viewModel = path, onExit = toMenu)
        Route.KAKURO -> KakuroScreen(viewModel = kakuro, onExit = toMenu)
        Route.MEMORAMA -> MemoramaScreen(viewModel = memorama, onExit = toMenu)
        Route.SLIDING -> SlidingScreen(viewModel = sliding, onExit = toMenu)
    }
}
