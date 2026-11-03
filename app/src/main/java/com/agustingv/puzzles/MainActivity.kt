package com.agustingv.puzzles

import android.media.AudioManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.lifecycle.viewmodel.compose.viewModel
import com.agustingv.puzzles.data.AppStorage
import com.agustingv.puzzles.data.AvatarPrizes
import com.agustingv.puzzles.data.GameEvent
import com.agustingv.puzzles.data.PlayerAccount
import com.agustingv.puzzles.data.PlayerSyncScheduler
import com.agustingv.puzzles.data.Records
import com.agustingv.puzzles.data.SavedGame
import com.agustingv.puzzles.data.SecretProgress
import com.agustingv.puzzles.game.Difficulty
import com.agustingv.puzzles.ui.common.Confetti
import com.agustingv.puzzles.ui.common.GameSounds
import com.agustingv.puzzles.ui.common.AvatarShortcut
import com.agustingv.puzzles.ui.common.LocalAvatarShortcut
import com.agustingv.puzzles.ui.common.LocalOpenSettings
import com.agustingv.puzzles.ui.crossword.CrosswordScreen
import com.agustingv.puzzles.ui.crossword.CrosswordViewModel
import com.agustingv.puzzles.ui.garden.GardenScreen
import com.agustingv.puzzles.ui.garden.GardenViewModel
import com.agustingv.puzzles.ui.hangman.HangmanScreen
import com.agustingv.puzzles.ui.hangman.HangmanViewModel
import com.agustingv.puzzles.ui.kakuro.KakuroScreen
import com.agustingv.puzzles.ui.kakuro.KakuroViewModel
import com.agustingv.puzzles.ui.memorama.MemoramaScreen
import com.agustingv.puzzles.ui.memorama.MemoramaViewModel
import com.agustingv.puzzles.ui.menu.AvatarScreen
import com.agustingv.puzzles.ui.menu.GameInfo
import com.agustingv.puzzles.ui.menu.GameDetailScreen
import com.agustingv.puzzles.ui.menu.HomeScreen
import com.agustingv.puzzles.ui.menu.PrivacyScreen
import com.agustingv.puzzles.ui.menu.SecretDialog
import com.agustingv.puzzles.ui.menu.SettingsScreen
import com.agustingv.puzzles.ui.path.PathScreen
import com.agustingv.puzzles.ui.path.PathViewModel
import com.agustingv.puzzles.ui.race.RaceScreen
import com.agustingv.puzzles.ui.race.RaceViewModel
import com.agustingv.puzzles.ui.sliding.SlidingScreen
import com.agustingv.puzzles.ui.sliding.SlidingViewModel
import com.agustingv.puzzles.ui.sudoku.SudokuScreen
import com.agustingv.puzzles.ui.sudoku.SudokuViewModel
import com.agustingv.puzzles.ui.theme.FractalBackground
import com.agustingv.puzzles.ui.theme.Paper
import com.agustingv.puzzles.ui.theme.PuzzlesTheme
import com.agustingv.puzzles.ui.theme.animationsEnabled
import com.agustingv.puzzles.ui.word.WordScreen
import com.agustingv.puzzles.ui.word.WordViewModel
import com.agustingv.puzzles.ui.wordsearch.WordSearchScreen
import com.agustingv.puzzles.ui.wordsearch.WordSearchViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // The volume keys adjust the game's sounds, not the ringer, even between one sound and the next.
        volumeControlStream = AudioManager.STREAM_MUSIC
        setContent {
            PuzzlesTheme {
                Box(modifier = Modifier.fillMaxSize().background(Paper)) {
                    FractalBackground()
                    PuzzlesApp()
                }
            }
        }
    }
}

@Composable
private fun PuzzlesApp() {
    // Home, then a game's page, then playing it. A game in progress skips its page both ways: its
    // card goes straight to it and leaving it goes home. Its page comes back once no game is running.
    var opened by rememberSaveable { mutableStateOf<GameInfo?>(null) }
    var playing by rememberSaveable { mutableStateOf(false) }
    var showPrivacy by rememberSaveable { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showAvatar by rememberSaveable { mutableStateOf(false) }
    var showSecret by rememberSaveable { mutableStateOf(false) }
    var secretBursts by remember { mutableIntStateOf(0) }
    val screens = rememberSaveableStateHolder()

    val race: RaceViewModel = viewModel()
    val hangman: HangmanViewModel = viewModel()
    val word: WordViewModel = viewModel()
    val wordSearch: WordSearchViewModel = viewModel()
    val crossword: CrosswordViewModel = viewModel()
    val sudoku: SudokuViewModel = viewModel()
    val garden: GardenViewModel = viewModel()
    val path: PathViewModel = viewModel()
    val kakuro: KakuroViewModel = viewModel()
    val memorama: MemoramaViewModel = viewModel()
    val sliding: SlidingViewModel = viewModel()

    val context = LocalContext.current
    val storage = remember { AppStorage.of(context) }
    val records = remember { Records(storage) }
    val sounds = remember { GameSounds.of(context) }
    val secret = remember { SecretProgress(storage) }
    val account = remember { PlayerAccount.of(context) }
    val prizes = remember { AvatarPrizes(storage) }
    var secretSolved by remember { mutableStateOf(secret.solved) }
    val haptics = LocalHapticFeedback.current
    val animate = remember { animationsEnabled(context) }
    val game = opened

    fun savedGame(info: GameInfo): SavedGame? = when (info) {
        GameInfo.RACE -> race.session.saved
        GameInfo.HANGMAN -> hangman.session.saved
        GameInfo.WORD -> word.session.saved
        GameInfo.WORD_SEARCH -> wordSearch.session.saved
        GameInfo.CROSSWORD -> crossword.session.saved
        GameInfo.SUDOKU -> sudoku.session.saved
        GameInfo.GARDEN -> garden.session.saved
        GameInfo.PATH -> path.session.saved
        GameInfo.KAKURO -> kakuro.session.saved
        GameInfo.MEMORAMA -> memorama.session.saved
        GameInfo.SLIDING -> sliding.session.saved
    }

    fun stopPlaying() {
        if (game?.let(::savedGame) != null) opened = null
        playing = false
    }

    fun startGame(info: GameInfo, variant: String) {
        when (info) {
            GameInfo.RACE -> race.newGame(variant.toInt())
            GameInfo.HANGMAN -> hangman.newGame(variant.toInt())
            GameInfo.WORD -> word.newGame(variant.toInt())
            GameInfo.WORD_SEARCH -> wordSearch.newGame(Difficulty.valueOf(variant))
            GameInfo.CROSSWORD -> crossword.newGame(Difficulty.valueOf(variant))
            GameInfo.SUDOKU -> sudoku.newGame(Difficulty.valueOf(variant))
            GameInfo.GARDEN -> garden.newGame(Difficulty.valueOf(variant))
            GameInfo.PATH -> path.newGame(Difficulty.valueOf(variant))
            GameInfo.KAKURO -> kakuro.newGame(Difficulty.valueOf(variant))
            GameInfo.MEMORAMA -> memorama.newGame(Difficulty.valueOf(variant))
            GameInfo.SLIDING -> sliding.newGame(Difficulty.valueOf(variant))
        }
        playing = true
    }

    // The avatar comes over the home screen or the settings, and goes back to the one it came from.
    if (showAvatar) {
        AvatarScreen(
            avatar = account.avatar,
            name = account.name,
            prizes = prizes,
            onChange = { avatar ->
                account.changeAvatar(avatar)
                PlayerSyncScheduler.request(context)
            },
            onExit = { showAvatar = false },
        )
        return
    }

    // The settings replace whatever is on screen, which stops its clocks. That screen comes back as
    // it was, because its state stays in the holder while it is gone.
    if (showSettings) {
        SettingsScreen(
            sounds = sounds,
            account = account,
            serverConfigured = PlayerSyncScheduler.serverConfigured,
            onRename = { name -> account.rename(name).also { if (it) PlayerSyncScheduler.request(context) } },
            onOfflineChange = { offline ->
                account.changeOffline(offline)
                PlayerSyncScheduler.request(context)
            },
            onAvatar = { showAvatar = true },
            onExit = { showSettings = false },
        )
        return
    }

    fun guessSecret(guess: String): Boolean {
        val right = secret.solve(guess)
        if (right) {
            showSecret = false
            secretSolved = true
            sounds.play(GameEvent.WIN)
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            if (animate) secretBursts++
        } else {
            sounds.play(GameEvent.MISS)
            haptics.performHapticFeedback(HapticFeedbackType.Reject)
        }
        return right
    }

    screens.SaveableStateProvider("screens") {
        CompositionLocalProvider(
            LocalOpenSettings provides { showSettings = true },
            LocalAvatarShortcut provides AvatarShortcut(account.avatar) { showAvatar = true },
        ) {
            when {
                showPrivacy -> PrivacyScreen(onExit = { showPrivacy = false })
                game == null -> HomeScreen(
                    saved = ::savedGame,
                    onOpen = {
                        opened = it
                        playing = savedGame(it) != null
                    },
                    onPrivacy = { showPrivacy = true },
                    secretSolved = secretSolved,
                    onSecret = { showSecret = true },
                )
                !playing -> GameDetailScreen(
                    info = game,
                    records = records,
                    storage = storage,
                    onPlay = { variant -> startGame(game, variant) },
                    onBack = { opened = null },
                )
                else -> when (game) {
                    GameInfo.RACE -> RaceScreen(race, ::stopPlaying)
                    GameInfo.HANGMAN -> HangmanScreen(hangman, ::stopPlaying)
                    GameInfo.WORD -> WordScreen(word, ::stopPlaying)
                    GameInfo.WORD_SEARCH -> WordSearchScreen(wordSearch, ::stopPlaying)
                    GameInfo.CROSSWORD -> CrosswordScreen(crossword, ::stopPlaying)
                    GameInfo.SUDOKU -> SudokuScreen(sudoku, ::stopPlaying)
                    GameInfo.GARDEN -> GardenScreen(garden, ::stopPlaying)
                    GameInfo.PATH -> PathScreen(path, ::stopPlaying)
                    GameInfo.KAKURO -> KakuroScreen(kakuro, ::stopPlaying)
                    GameInfo.MEMORAMA -> MemoramaScreen(memorama, ::stopPlaying)
                    GameInfo.SLIDING -> SlidingScreen(sliding, ::stopPlaying)
                }
            }

            if (showSecret) {
                SecretDialog(
                    found = secret.found,
                    onGuess = ::guessSecret,
                    onDismiss = { showSecret = false },
                )
            }
            Confetti(secretBursts)
        }
    }
}
