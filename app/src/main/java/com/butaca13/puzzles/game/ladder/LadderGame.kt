package com.butaca13.puzzles.game.ladder

import com.butaca13.puzzles.game.Difficulty
import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

/**
 * The words a ladder can step on, and how they link: two words link when they differ in one letter. All
 * of them plain, lowercase and without accents. [common] are the app's own words, which puzzles are made
 * from; a step may be any word of [words].
 */
class LadderWords(words: Collection<String>, common: Collection<String>) {
    val words: Set<String> = words.toSet()
    val common: Set<String> = common.filter { it in this.words }.toSet()

    /** Words by a letter left out, "ga_o" for "gato": those that share one are each other's neighbours. */
    private val buckets: Map<String, List<String>> = this.words
        .flatMap { w -> w.indices.map { i -> pattern(w, i) to w } }
        .groupBy({ it.first }, { it.second })

    private fun pattern(word: String, i: Int) = word.substring(0, i) + "_" + word.substring(i + 1)

    fun neighbours(word: String, within: Set<String> = words): List<String> =
        word.indices.flatMap { i -> buckets[pattern(word, i)].orEmpty() }.filter { it != word && it in within }.distinct()

    fun linked(a: String, b: String) = a.length == b.length && a.indices.count { a[it] != b[it] } == 1

    /** The fewest steps from [from] to every word it reaches, through [within]. */
    fun distances(from: String, within: Set<String> = words): Map<String, Int> {
        val dist = hashMapOf(from to 0)
        val queue = ArrayDeque(listOf(from))
        while (queue.isNotEmpty()) {
            val w = queue.removeFirst()
            for (n in neighbours(w, within)) {
                if (n !in dist) {
                    dist[n] = dist.getValue(w) + 1
                    queue.addLast(n)
                }
            }
        }
        return dist
    }

    /** The next word on a shortest way from [from] to [to], or null when there is none. */
    fun nextStep(from: String, to: String): String? {
        val back = distances(to)
        val here = back[from] ?: return null
        return neighbours(from).filter { back[it] == here - 1 }.minByOrNull { if (it in common) 0 else 1 }
    }

    /**
     * A puzzle for [difficulty]: a start and a target among the common words, a number of steps apart
     * that the level sets. Easy and medium go by four letters, and only through common words, so that
     * every step of the shortest way is a word everyone knows; hard goes by five, through any word.
     */
    fun puzzle(difficulty: Difficulty, random: Random = Random.Default): Pair<String, String>? {
        val (length, steps, commonOnly) = when (difficulty) {
            Difficulty.EASY -> Triple(4, 3..4, true)
            Difficulty.MEDIUM -> Triple(4, 5..6, true)
            else -> Triple(5, 4..6, false)
        }
        val starts = common.filter { it.length == length }.shuffled(random)
        val within = if (commonOnly) common else words
        for (start in starts.take(200)) {
            val targets = distances(start, within).filter { (w, d) -> d in steps && w in common }.keys
            if (targets.isNotEmpty()) return start to targets.random(random)
        }
        return null
    }
}

/**
 * A word ladder: from [start] to [target], changing one letter at a time, every step a word. The way
 * so far is [chain]; [best] is the fewest steps it can take, shown as the par. Hints show the next
 * word of a shortest way from where the player is, and are counted.
 */
data class LadderGame(
    val start: String,
    val target: String,
    val best: Int,
    val difficulty: Difficulty,
    val chain: List<String> = listOf(start),
    val hints: Int = 0,
    val misses: Int = 0,
) {
    val current: String get() = chain.last()

    val steps: Int get() = chain.size - 1

    val isWon: Boolean get() = current == target

    /**
     * The step to [word]: taken if it is a word one letter away from the current one and not already on
     * the way, else counted as a miss. A word already on the way goes back to it instead.
     */
    fun step(word: String, words: LadderWords): LadderGame {
        if (isWon || word == current) return this
        val earlier = chain.indexOf(word)
        if (earlier >= 0) return copy(chain = chain.take(earlier + 1))
        if (!words.linked(current, word) || word !in words.words) return copy(misses = misses + 1)
        return copy(chain = chain + word)
    }

    /** One step back. */
    fun undo(): LadderGame = if (isWon || chain.size == 1) this else copy(chain = chain.dropLast(1))

    /** Takes the next step of a shortest way for the player, counting it as a hint. */
    fun hint(words: LadderWords): LadderGame {
        if (isWon) return this
        val next = words.nextStep(current, target) ?: return this
        return copy(chain = chain + next, hints = hints + 1)
    }

    fun toJson(): String = JSONObject()
        .put("start", start)
        .put("target", target)
        .put("best", best)
        .put("difficulty", difficulty.name)
        .put("chain", JSONArray(chain))
        .put("hints", hints)
        .put("misses", misses)
        .toString()

    companion object {
        fun of(start: String, target: String, difficulty: Difficulty, words: LadderWords): LadderGame =
            LadderGame(start, target, words.distances(start)[target] ?: 0, difficulty)

        fun fromJson(json: String): LadderGame {
            val o = JSONObject(json)
            val chain = o.getJSONArray("chain")
            return LadderGame(
                start = o.getString("start"),
                target = o.getString("target"),
                best = o.getInt("best"),
                difficulty = Difficulty.valueOf(o.getString("difficulty")),
                chain = List(chain.length()) { chain.getString(it) },
                hints = o.getInt("hints"),
                misses = o.getInt("misses"),
            )
        }
    }
}
