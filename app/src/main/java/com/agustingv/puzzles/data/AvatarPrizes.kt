package com.agustingv.puzzles.data

import com.agustingv.puzzles.game.Difficulty
import com.agustingv.puzzles.game.word.WordList

/**
 * The prize pieces this phone has won, and the rules that win them:
 * - corona: solving the secret message;
 * - trofeo: winning every game at least once;
 * - birrete: winning a game on [Difficulty.EXPERT];
 * - gafas: beating one's own best time;
 * - chistera: winning [WINS_FOR_TOP_HAT] games.
 *
 * Kept on the phone, like the records it is worked out from. Once won, a prize stays.
 */
class AvatarPrizes(private val store: KeyValueStore) {

    private val records = Records(store)

    init {
        catchUp()
    }

    private val won: Set<String>
        get() = store.get(WON_KEY).orEmpty().split(",").filter { it.isNotEmpty() }.toSet()

    /** Games won since prizes exist, plus those still in the records from before. */
    val wins: Int
        get() = store.get(WINS_KEY)?.toIntOrNull() ?: 0

    /** How many of the games have been won at least once. */
    val gamesWon: Int
        get() = GAMES.count { game -> VARIANTS.any { records.top(game, it).isNotEmpty() } }

    fun has(piece: AvatarPiece): Boolean = !piece.prize || piece.id in won

    /** Whether [piece] was won just now: false if it was won already, or is not a prize. */
    fun win(piece: AvatarPiece): Boolean {
        if (has(piece)) return false
        store.put(WON_KEY, (won + piece.id).sorted().joinToString(","))
        return true
    }

    /** The prizes a game just won earns, in the catalog's order. Called once its [record] is filed. */
    fun recordWin(variant: String, record: RecordResult): List<AvatarPiece> {
        store.put(WINS_KEY, (wins + 1).toString())

        return listOfNotNull(
            prize(TROPHY).takeIf { gamesWon == GAMES.size },
            prize(TOP_HAT).takeIf { wins >= WINS_FOR_TOP_HAT },
            prize(GLASSES).takeIf { record.improved },
            prize(CAP).takeIf { variant == EXPERT },
        ).filter(::win).sortedBy(AvatarCatalog.accessories::indexOf)
    }

    /** The crown, if solving the message has just won it. */
    fun recordSecretSolved(): AvatarPiece? = prize(CROWN).takeIf(::win)

    /**
     * Once, for a player from before prizes: what their records and the message already earn. A best
     * time beaten can not be told from the records, and wins that fell out of them are not counted.
     */
    private fun catchUp() {
        if (store.get(CAUGHT_UP_KEY) != null) return

        val filed = GAMES.sumOf { game -> VARIANTS.sumOf { records.top(game, it).size } }
        store.put(WINS_KEY, maxOf(wins, filed).toString())

        if (gamesWon == GAMES.size) win(prize(TROPHY))
        if (wins >= WINS_FOR_TOP_HAT) win(prize(TOP_HAT))
        if (GAMES.any { records.top(it, EXPERT).isNotEmpty() }) win(prize(CAP))
        if (SecretProgress(store).solved) win(prize(CROWN))

        store.put(CAUGHT_UP_KEY, "yes")
    }

    companion object {
        const val CROWN = "corona"
        const val TROPHY = "trofeo"
        const val CAP = "birrete"
        const val GLASSES = "gafas"
        const val TOP_HAT = "chistera"
        const val WINS_FOR_TOP_HAT = 50

        /** Every game: each one has its letter in the message. */
        val GAMES: List<String> = SecretMessage.clues.map { it.gameId }

        private val EXPERT = Difficulty.EXPERT.name

        /** What records are filed under: a difficulty, or a word length. */
        private val VARIANTS = Difficulty.entries.map { it.name } + WordList.LENGTHS.map { it.toString() }

        private const val WON_KEY = "avatar/won"
        private const val WINS_KEY = "avatar/wins"
        private const val CAUGHT_UP_KEY = "avatar/caught-up"

        private fun prize(id: String) = checkNotNull(AvatarCatalog.accessory(id)) { id }
    }
}
