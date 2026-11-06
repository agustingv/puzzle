package com.butaca13.puzzles.data

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** What a session needs to know about one game. */
class GameRules<G : Any>(
    val id: String,
    val encode: (G) -> String,
    val decode: (String) -> G,
    val isOver: (G) -> Boolean,
    val isWon: (G) -> Boolean,
    /** The difficulty or word length: what its records are filed under. */
    val variantOf: (G) -> String,
    /** What a move meant, for the player's feedback. Winning and losing are told apart already. */
    val eventFor: (previous: G, next: G) -> GameEvent? = { _, _ -> null },
)

/** Something worth feeling or seeing in the game. */
enum class GameEvent { TICK, HIT, MISS, WIN, LOSE }

/** The latest event. Its [id] tells two equal events apart, so that each one is handled. */
data class GameFeedback(val event: GameEvent, val id: Int)

/** An unfinished game, as the menu offers it. */
data class SavedGame(val variant: String, val elapsedSeconds: Int)

/**
 * The running game of one kind: holds it, keeps it on disk through every change, so it survives the
 * app being closed, and files a record the moment it is won. A game restored already won is not
 * filed again, because only the transition to won counts.
 */
@Stable
class GameSession<G : Any>(private val store: KeyValueStore, private val rules: GameRules<G>) {

    private val records = Records(store)
    private val secret = SecretProgress(store)
    private val prizes = AvatarPrizes(store)

    /** Null until the first game, or while one is being generated. */
    var game by mutableStateOf(load())
        private set

    var elapsedSeconds by mutableIntStateOf(store.get(key(ELAPSED))?.toIntOrNull() ?: 0)
        private set

    /** The record just filed. Not saved: after a restart the result simply shows no ranking. */
    var lastRecord by mutableStateOf<RecordResult?>(null)
        private set

    /** What this win revealed of the secret message. Not saved: the message's own screen keeps it. */
    var lastClue by mutableStateOf<SecretClue?>(null)
        private set

    /** The avatar prizes this win earned. Not saved, like [lastClue]: the avatar keeps them. */
    var lastPrizes by mutableStateOf<List<AvatarPiece>>(emptyList())
        private set

    /** Not saved: a game restored after a restart has nothing new to celebrate. */
    var feedback by mutableStateOf<GameFeedback?>(null)
        private set

    private var nextFeedbackId = 0

    /** What a generation in progress was asked for, so a killed process can finish the job. */
    val pendingVariant: String? get() = store.get(key(PENDING))

    val saved: SavedGame?
        get() = game?.takeIf { !rules.isOver(it) }?.let { SavedGame(rules.variantOf(it), elapsedSeconds) }

    fun start(newGame: G) {
        prepareNew()
        store.remove(key(PENDING))
        update(newGame)
    }

    /** Clears the current game before a slow generation, noting what is being generated. */
    fun startGenerating(variant: String) {
        prepareNew()
        store.put(key(PENDING), variant)
    }

    /** Drops the running game for good, with nothing filed: as if it had never been started. */
    fun discard() = prepareNew()

    fun update(next: G) {
        val previous = game
        game = next
        store.put(key(GAME), rules.encode(next))
        store.put(key(ELAPSED), elapsedSeconds.toString())

        val wasWon = previous != null && rules.isWon(previous)
        if (rules.isWon(next) && !wasWon) {
            val variant = rules.variantOf(next)
            val record = records.add(rules.id, variant, elapsedSeconds)
            lastRecord = record
            lastClue = secret.recordWin(rules.id)
            lastPrizes = prizes.recordWin(variant, record)
        }

        if (previous != null) {
            val event = when {
                rules.isWon(next) && !wasWon -> GameEvent.WIN
                rules.isOver(next) && !rules.isOver(previous) -> GameEvent.LOSE
                else -> rules.eventFor(previous, next)
            }
            event?.let(::signal)
        }
    }

    /** For an event that leaves the game as it was, such as a wrong answer. */
    fun signal(event: GameEvent) {
        feedback = GameFeedback(event, nextFeedbackId++)
    }

    fun tick() {
        val current = game ?: return
        if (rules.isOver(current)) return

        elapsedSeconds++
        store.put(key(ELAPSED), elapsedSeconds.toString())
    }

    /** Extra state outside the game itself, such as letters typed but not yet submitted. */
    fun loadExtra(): String? = store.get(key(EXTRA))

    fun saveExtra(value: String) = store.put(key(EXTRA), value)

    private fun prepareNew() {
        game = null
        lastRecord = null
        lastClue = null
        lastPrizes = emptyList()
        elapsedSeconds = 0
        store.remove(key(GAME))
        store.remove(key(EXTRA))
        store.put(key(ELAPSED), "0")
    }

    /** Saved data that can not be read, say after an update changes its format, is not fatal. */
    private fun load(): G? {
        val json = store.get(key(GAME)) ?: return null

        return try {
            rules.decode(json)
        } catch (_: Exception) {
            store.remove(key(GAME))
            null
        }
    }

    private fun key(name: String) = "game/${rules.id}/$name"

    private companion object {
        const val GAME = "state"
        const val ELAPSED = "elapsed"
        const val PENDING = "pending"
        const val EXTRA = "extra"
    }
}
