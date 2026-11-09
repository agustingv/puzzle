package com.butaca13.puzzles

import android.media.AudioManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.butaca13.puzzles.R
import com.butaca13.puzzles.data.AppStorage
import com.butaca13.puzzles.data.AvatarCatalog
import com.butaca13.puzzles.data.AvatarPrizes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.unit.dp
import com.butaca13.puzzles.data.GameEvent
import com.butaca13.puzzles.data.PlayerAccount
import com.butaca13.puzzles.data.GlobalRankings
import com.butaca13.puzzles.data.PlayerDeletion
import com.butaca13.puzzles.ui.menu.productId
import com.butaca13.puzzles.ui.menu.PAID_GAME_PRODUCTS
import com.butaca13.puzzles.ui.menu.GameShop
import com.butaca13.puzzles.data.Products
import com.butaca13.puzzles.data.Billing
import com.butaca13.puzzles.data.Favorites
import com.butaca13.puzzles.ui.menu.GameGroup
import com.butaca13.puzzles.ui.menu.GroupScreen
import com.butaca13.puzzles.ui.menu.FakeStoreSheet
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.activity.compose.LocalActivity
import com.butaca13.puzzles.data.PlayerSyncScheduler
import com.butaca13.puzzles.data.ScoreUploads
import com.butaca13.puzzles.data.Records
import com.butaca13.puzzles.data.SavedGame
import com.butaca13.puzzles.data.SecretProgress
import com.butaca13.puzzles.game.Difficulty
import com.butaca13.puzzles.ui.common.Confetti
import com.butaca13.puzzles.ui.common.GameSounds
import com.butaca13.puzzles.ui.common.AvatarShortcut
import com.butaca13.puzzles.ui.common.LocalAvatarShortcut
import com.butaca13.puzzles.ui.common.LocalOpenSettings
import com.butaca13.puzzles.ui.common.LocalOnSettings
import com.butaca13.puzzles.ui.common.LocalSecretSolved
import com.butaca13.puzzles.ui.common.PrizeNote
import com.butaca13.puzzles.ui.crossword.CrosswordScreen
import com.butaca13.puzzles.ui.crossword.CrosswordViewModel
import com.butaca13.puzzles.ui.garden.GardenScreen
import com.butaca13.puzzles.ui.garden.GardenViewModel
import com.butaca13.puzzles.ui.hangman.HangmanScreen
import com.butaca13.puzzles.ui.hangman.HangmanViewModel
import com.butaca13.puzzles.ui.kakuro.KakuroScreen
import com.butaca13.puzzles.ui.kakuro.KakuroViewModel
import com.butaca13.puzzles.ui.memorama.MemoramaScreen
import com.butaca13.puzzles.ui.memorama.MemoramaViewModel
import com.butaca13.puzzles.ui.klondike.KlondikeScreen
import com.butaca13.puzzles.ui.klondike.KlondikeViewModel
import com.butaca13.puzzles.game.klondike.KlondikeGame
import com.butaca13.puzzles.game.freecell.FreeCellGame
import com.butaca13.puzzles.game.pyramid.PyramidGame
import com.butaca13.puzzles.game.tripeaks.TriPeaksGame
import com.butaca13.puzzles.game.yukon.YukonGame
import com.butaca13.puzzles.game.scorpion.ScorpionGame
import com.butaca13.puzzles.game.bakersdozen.BakersDozenGame
import com.butaca13.puzzles.ui.panal.PanalScreen
import com.butaca13.puzzles.ui.panal.PanalViewModel
import com.butaca13.puzzles.ui.crossletters.CrossLettersScreen
import com.butaca13.puzzles.ui.crossletters.CrossLettersViewModel
import com.butaca13.puzzles.ui.bakersdozen.BakersDozenScreen
import com.butaca13.puzzles.ui.bakersdozen.BakersDozenViewModel
import com.butaca13.puzzles.ui.scorpion.ScorpionScreen
import com.butaca13.puzzles.ui.scorpion.ScorpionViewModel
import com.butaca13.puzzles.ui.yukon.YukonScreen
import com.butaca13.puzzles.ui.yukon.YukonViewModel
import com.butaca13.puzzles.ui.tripeaks.TriPeaksScreen
import com.butaca13.puzzles.ui.tripeaks.TriPeaksViewModel
import com.butaca13.puzzles.ui.pyramid.PyramidScreen
import com.butaca13.puzzles.ui.pyramid.PyramidViewModel
import com.butaca13.puzzles.ui.freecell.FreeCellScreen
import com.butaca13.puzzles.ui.freecell.FreeCellViewModel
import com.butaca13.puzzles.ui.menu.AvatarScreen
import com.butaca13.puzzles.ui.menu.GameInfo
import com.butaca13.puzzles.ui.menu.GameDetailScreen
import com.butaca13.puzzles.ui.menu.HomeScreen
import com.butaca13.puzzles.ui.menu.PrivacyScreen
import com.butaca13.puzzles.ui.menu.SecretDialog
import com.butaca13.puzzles.ui.starfall.StarfallScreen
import com.butaca13.puzzles.ui.starfall.StarfallViewModel
import com.butaca13.puzzles.ui.menu.SettingsScreen
import com.butaca13.puzzles.ui.path.PathScreen
import com.butaca13.puzzles.ui.path.PathViewModel
import com.butaca13.puzzles.ui.race.RaceScreen
import com.butaca13.puzzles.ui.race.RaceViewModel
import com.butaca13.puzzles.ui.sliding.SlidingScreen
import com.butaca13.puzzles.ui.sliding.SlidingViewModel
import com.butaca13.puzzles.ui.sudoku.SudokuScreen
import com.butaca13.puzzles.ui.sudoku.SudokuViewModel
import com.butaca13.puzzles.ui.theme.Paper
import com.butaca13.puzzles.ui.theme.PuzzlesTheme
import com.butaca13.puzzles.ui.theme.animationsEnabled
import com.butaca13.puzzles.ui.word.WordScreen
import com.butaca13.puzzles.ui.word.WordViewModel
import com.butaca13.puzzles.ui.wordsearch.WordSearchScreen
import com.butaca13.puzzles.ui.wordsearch.WordSearchViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // The volume keys adjust the game's sounds, not the ringer, even between one sound and the next.
        volumeControlStream = AudioManager.STREAM_MUSIC
        setContent {
            PuzzlesTheme {
                Box(modifier = Modifier.fillMaxSize().background(Paper)) {
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
    // The group whose games are listed, if one was opened: a game's page goes back to it. A favourite,
    // opened from home, goes back home.
    var group by rememberSaveable { mutableStateOf<GameGroup?>(null) }
    var playing by rememberSaveable { mutableStateOf(false) }
    var showPrivacy by rememberSaveable { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showStarfall by rememberSaveable { mutableStateOf(false) }
    var showAvatar by rememberSaveable { mutableStateOf(false) }
    var showSecret by rememberSaveable { mutableStateOf(false) }
    // A prize won outside a game, by solving the message: shown on its own, as there is no result.
    var newPrize by rememberSaveable { mutableStateOf<String?>(null) }
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
    val klondike: KlondikeViewModel = viewModel()
    val freeCell: FreeCellViewModel = viewModel()
    val pyramid: PyramidViewModel = viewModel()
    val triPeaks: TriPeaksViewModel = viewModel()
    val yukon: YukonViewModel = viewModel()
    val scorpion: ScorpionViewModel = viewModel()
    val bakersDozen: BakersDozenViewModel = viewModel()
    val panal: PanalViewModel = viewModel()
    val crossLetters: CrossLettersViewModel = viewModel()
    val sliding: SlidingViewModel = viewModel()

    val context = LocalContext.current
    val storage = remember { AppStorage.of(context) }
    val records = remember { Records(storage) }
    val rankings = remember { GlobalRankings(storage, PlayerSyncScheduler.scoreApi()) }
    val sounds = remember { GameSounds.of(context) }
    val secret = remember { SecretProgress(storage) }
    val account = remember { PlayerAccount.of(context) }
    val prizes = remember { AvatarPrizes(storage) }
    val favorites = remember { Favorites(storage) }
    val billing = remember { Billing.of(context) }
    val entitlements = remember { Billing.entitlementsOf(context) }
    val activity = LocalActivity.current
    // Google Play is asked again on every return to the app: a payment completed meanwhile, or a refund,
    // shows at once.
    LifecycleResumeEffect(billing) {
        billing.refresh(PAID_GAME_PRODUCTS)
        onPauseOrDispose {}
    }
    val shop = remember(billing, entitlements, activity) {
        GameShop(
            unlocked = { entitlements.unlocked(it.productId) },
            price = { billing.prices[it] },
            pending = { it in billing.pending },
            ownsAll = { Products.ALL_GAMES in entitlements.owned },
            buy = { productId -> activity?.let { billing.buy(it, productId) } },
            restore = { billing.refresh(PAID_GAME_PRODUCTS) },
        )
    }
    // The simulated store's payment sheet, over whatever screen it was opened from.
    Billing.fakeStore?.let { FakeStoreSheet(it) }
    var secretSolved by remember { mutableStateOf(secret.solved) }
    val haptics = LocalHapticFeedback.current
    val animate = remember { animationsEnabled(context) }
    val game = opened

    // A win that makes the player's ten best goes to the rankings at once, connection allowing. Every
    // game's last record counts, not only the first one found: each game keeps its own until a new game
    // of that kind, so a win in another game must change this too. A new game clears the last record,
    // so the next one counts again even with the same values.
    val rankedWins = listOf(
        race.session, hangman.session, word.session, wordSearch.session, crossword.session, sudoku.session,
        garden.session, path.session, kakuro.session, memorama.session, sliding.session, klondike.session,
        freeCell.session, pyramid.session, triPeaks.session,
        yukon.session, scorpion.session,
        bakersDozen.session,
        panal.session, crossLetters.session,
    ).map { session -> session.lastRecord?.takeIf { it.rank != null } }
    LaunchedEffect(rankedWins) {
        if (rankedWins.any { it != null }) PlayerSyncScheduler.request(context)
    }

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
        GameInfo.KLONDIKE -> klondike.session.saved
        GameInfo.FREECELL -> freeCell.session.saved
        GameInfo.PYRAMID -> pyramid.session.saved
        GameInfo.TRIPEAKS -> triPeaks.session.saved
        GameInfo.YUKON -> yukon.session.saved
        GameInfo.SCORPION -> scorpion.session.saved
        GameInfo.BAKERS_DOZEN -> bakersDozen.session.saved
        GameInfo.PANAL -> panal.session.saved
        GameInfo.CROSS_LETTERS -> crossLetters.session.saved
        GameInfo.SLIDING -> sliding.session.saved
    }

    fun openGame(info: GameInfo) {
        opened = info
        // A game no longer owned, refunded say, opens on its page, which offers to buy it.
        playing = savedGame(info) != null && shop.unlocked(info)
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
            GameInfo.KLONDIKE -> klondike.newGame(KlondikeGame.drawCountOf(variant))
            GameInfo.FREECELL -> freeCell.newGame(FreeCellGame.cellCountOf(variant))
            GameInfo.PYRAMID -> pyramid.newGame(PyramidGame.passesOf(variant))
            GameInfo.TRIPEAKS -> triPeaks.newGame(TriPeaksGame.wrapsOf(variant))
            GameInfo.YUKON -> yukon.newGame(YukonGame.sameSuitOf(variant))
            GameInfo.SCORPION -> scorpion.newGame(ScorpionGame.anyOnEmptyOf(variant))
            GameInfo.BAKERS_DOZEN -> bakersDozen.newGame(BakersDozenGame.kingsOnEmptyOf(variant))
            GameInfo.PANAL -> panal.newGame(Difficulty.valueOf(variant))
            GameInfo.CROSS_LETTERS -> crossLetters.newGame(Difficulty.valueOf(variant))
            GameInfo.SLIDING -> sliding.newGame(Difficulty.valueOf(variant))
        }
        playing = true
    }

    // The header's avatar and settings, on every screen. On their own screen each shows disabled, and
    // each leads to the other: from the avatar to the settings, and from the settings, or the privacy
    // policy they open, to the avatar.
    fun avatarShortcut(open: (() -> Unit)?) = AvatarShortcut(
        avatar = account.avatar,
        open = open,
        wear = { piece ->
            account.changeAvatar(account.avatar.wear(piece))
            PlayerSyncScheduler.request(context)
        },
    )

    // The avatar comes over the home screen or the settings, and goes back to the one it came from.
    if (showAvatar) {
        CompositionLocalProvider(
            LocalSecretSolved provides secretSolved,
            LocalAvatarShortcut provides avatarShortcut(open = null),
            LocalOpenSettings provides {
                showAvatar = false
                showPrivacy = false
                showSettings = true
            },
        ) {
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
        }
        return
    }

    // The secret message's prize: a game of its own, over everything, from home or the Various group.
    if (showStarfall) {
        val starfall: StarfallViewModel = viewModel()
        // The medal won at the end can be put on straight away, as any prize.
        CompositionLocalProvider(
            LocalAvatarShortcut provides AvatarShortcut(
                avatar = account.avatar,
                open = { showAvatar = true },
                wear = { piece ->
                    account.changeAvatar(account.avatar.wear(piece))
                    PlayerSyncScheduler.request(context)
                },
            ),
        ) {
            StarfallScreen(starfall, onExit = { showStarfall = false })
        }
        return
    }

    // The privacy policy opens from the settings and goes back to them.
    if (showPrivacy) {
        CompositionLocalProvider(
            LocalSecretSolved provides secretSolved,
            LocalAvatarShortcut provides avatarShortcut(open = { showAvatar = true }),
            LocalOpenSettings provides { showPrivacy = false },
        ) {
            PrivacyScreen(onExit = { showPrivacy = false })
        }
        return
    }

    // The settings replace whatever is on screen, which stops its clocks. That screen comes back as
    // it was, because its state stays in the holder while it is gone.
    if (showSettings) {
        CompositionLocalProvider(
            LocalSecretSolved provides secretSolved,
            LocalAvatarShortcut provides avatarShortcut(open = { showAvatar = true }),
            LocalOnSettings provides true,
        ) {
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
                onDeletePlayer = {
                    PlayerSyncScheduler.cancel(context)
                    val gone = withContext(Dispatchers.IO) {
                        PlayerDeletion(account, ScoreUploads(storage), PlayerSyncScheduler.deletionApi()).run()
                    }
                    // Not deleted: whatever was waiting to be sent still is.
                    if (!gone) PlayerSyncScheduler.request(context)
                    gone
                },
                onPrivacy = { showPrivacy = true },
                onExit = { showSettings = false },
                shop = shop,
                fakeStore = Billing.fakeStore,
            )
        }
        return
    }

    fun revealSecret() {
        showSecret = false
        if (!secret.reveal()) return
        secretSolved = true
        sounds.play(GameEvent.WIN)
        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        if (animate) secretBursts++
        newPrize = prizes.recordSecretSolved()?.id
    }

    screens.SaveableStateProvider("screens") {
        CompositionLocalProvider(
            LocalOpenSettings provides { showSettings = true },
            LocalSecretSolved provides secretSolved,
            LocalAvatarShortcut provides AvatarShortcut(
                avatar = account.avatar,
                open = { showAvatar = true },
                wear = { piece ->
                    account.changeAvatar(account.avatar.wear(piece))
                    PlayerSyncScheduler.request(context)
                },
            ),
        ) {
            when {
                game == null && group == null -> HomeScreen(
                    saved = ::savedGame,
                    onOpenGroup = { group = it },
                    onOpen = ::openGame,
                    favorites = favorites.ids.mapNotNull { id -> GameInfo.entries.find { it.id == id } },
                    secretSolved = secretSolved,
                    onSecret = { showSecret = true },
                    shop = shop,
                    onStarfall = { showStarfall = true },
                )
                game == null -> GroupScreen(
                    group = group!!,
                    saved = ::savedGame,
                    onOpen = ::openGame,
                    onBack = { group = null },
                    shop = shop,
                    onStarfall = { showStarfall = true }.takeIf { secretSolved && group == GameGroup.VARIOUS },
                )
                !playing -> GameDetailScreen(
                    info = game,
                    records = records,
                    rankings = rankings,
                    account = account,
                    storage = storage,
                    onPlay = { variant -> startGame(game, variant) },
                    onBack = { opened = null },
                    shop = shop,
                    favorite = game.id in favorites,
                    onFavorite = { favorites.toggle(game.id) },
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
                    GameInfo.KLONDIKE -> KlondikeScreen(klondike, ::stopPlaying)
                    GameInfo.FREECELL -> FreeCellScreen(freeCell, ::stopPlaying)
                    GameInfo.PYRAMID -> PyramidScreen(pyramid, ::stopPlaying)
                    GameInfo.TRIPEAKS -> TriPeaksScreen(triPeaks, ::stopPlaying)
                    GameInfo.YUKON -> YukonScreen(yukon, ::stopPlaying)
                    GameInfo.SCORPION -> ScorpionScreen(scorpion, ::stopPlaying)
                    GameInfo.BAKERS_DOZEN -> BakersDozenScreen(bakersDozen, ::stopPlaying)
                    GameInfo.PANAL -> PanalScreen(panal, ::stopPlaying)
                    GameInfo.CROSS_LETTERS -> CrossLettersScreen(crossLetters, ::stopPlaying)
                    GameInfo.SLIDING -> SlidingScreen(sliding, ::stopPlaying)
                }
            }

            if (showSecret) {
                SecretDialog(
                    found = secret.found,
                    onReveal = ::revealSecret,
                    onDismiss = { showSecret = false },
                )
            }
            newPrize?.let(AvatarCatalog::accessory)?.let { piece ->
                // The crown comes with Starfall: the dialog says so, and can go straight to it.
                val withStarfall = piece.id == AvatarPrizes.CROWN
                AlertDialog(
                    onDismissRequest = { newPrize = null },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            PrizeNote(piece)
                            if (withStarfall) Text(stringResource(R.string.starfall_unlocked))
                        }
                    },
                    confirmButton = {
                        if (withStarfall) {
                            TextButton(onClick = {
                                newPrize = null
                                showStarfall = true
                            }) { Text(stringResource(R.string.starfall_go)) }
                        } else {
                            TextButton(onClick = { newPrize = null }) { Text(stringResource(R.string.secret_close)) }
                        }
                    },
                    dismissButton = if (withStarfall) {
                        { TextButton(onClick = { newPrize = null }) { Text(stringResource(R.string.secret_close)) } }
                    } else {
                        null
                    },
                )
            }
            Confetti(secretBursts)
        }
    }
}
