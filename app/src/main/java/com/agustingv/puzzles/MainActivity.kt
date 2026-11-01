package com.agustingv.puzzles

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.agustingv.puzzles.data.AppStorage
import com.agustingv.puzzles.data.Records
import com.agustingv.puzzles.game.Difficulty
import com.agustingv.puzzles.ui.crossword.CrosswordScreen
import com.agustingv.puzzles.ui.crossword.CrosswordViewModel
import com.agustingv.puzzles.ui.hangman.HangmanScreen
import com.agustingv.puzzles.ui.hangman.HangmanViewModel
import com.agustingv.puzzles.ui.kakuro.KakuroScreen
import com.agustingv.puzzles.ui.kakuro.KakuroViewModel
import com.agustingv.puzzles.ui.memorama.MemoramaScreen
import com.agustingv.puzzles.ui.memorama.MemoramaViewModel
import com.agustingv.puzzles.ui.menu.GameInfo
import com.agustingv.puzzles.ui.menu.MenuScreen
import com.agustingv.puzzles.ui.menu.RecordsScreen
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
    var playing by rememberSaveable { mutableStateOf<GameInfo?>(null) }
    var recordsOf by rememberSaveable { mutableStateOf<GameInfo?>(null) }

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

    val context = LocalContext.current
    val records = remember { Records(AppStorage.of(context)) }
    val toMenu = { playing = null }
    val game = playing
    val recordsGame = recordsOf

    when {
        recordsGame != null -> RecordsScreen(recordsGame, records, onExit = { recordsOf = null })
        game == null -> MenuScreen(
            saved = { info ->
                when (info) {
                    GameInfo.RACE -> race.session.saved
                    GameInfo.HANGMAN -> hangman.session.saved
                    GameInfo.WORD -> word.session.saved
                    GameInfo.WORD_SEARCH -> wordSearch.session.saved
                    GameInfo.CROSSWORD -> crossword.session.saved
                    GameInfo.SUDOKU -> sudoku.session.saved
                    GameInfo.PATH -> path.session.saved
                    GameInfo.KAKURO -> kakuro.session.saved
                    GameInfo.MEMORAMA -> memorama.session.saved
                    GameInfo.SLIDING -> sliding.session.saved
                }
            },
            onNewGame = { info, variant ->
                when (info) {
                    GameInfo.RACE -> race.newGame(variant.toInt())
                    GameInfo.HANGMAN -> hangman.newGame(variant.toInt())
                    GameInfo.WORD -> word.newGame(variant.toInt())
                    GameInfo.WORD_SEARCH -> wordSearch.newGame(Difficulty.valueOf(variant))
                    GameInfo.CROSSWORD -> crossword.newGame(Difficulty.valueOf(variant))
                    GameInfo.SUDOKU -> sudoku.newGame(Difficulty.valueOf(variant))
                    GameInfo.PATH -> path.newGame(Difficulty.valueOf(variant))
                    GameInfo.KAKURO -> kakuro.newGame(Difficulty.valueOf(variant))
                    GameInfo.MEMORAMA -> memorama.newGame(Difficulty.valueOf(variant))
                    GameInfo.SLIDING -> sliding.newGame(Difficulty.valueOf(variant))
                }
                playing = info
            },
            onContinue = { playing = it },
            onRecords = { recordsOf = it },
        )
        else -> when (game) {
            GameInfo.RACE -> RaceScreen(race, toMenu)
            GameInfo.HANGMAN -> HangmanScreen(hangman, toMenu)
            GameInfo.WORD -> WordScreen(word, toMenu)
            GameInfo.WORD_SEARCH -> WordSearchScreen(wordSearch, toMenu)
            GameInfo.CROSSWORD -> CrosswordScreen(crossword, toMenu)
            GameInfo.SUDOKU -> SudokuScreen(sudoku, toMenu)
            GameInfo.PATH -> PathScreen(path, toMenu)
            GameInfo.KAKURO -> KakuroScreen(kakuro, toMenu)
            GameInfo.MEMORAMA -> MemoramaScreen(memorama, toMenu)
            GameInfo.SLIDING -> SlidingScreen(sliding, toMenu)
        }
    }
}
