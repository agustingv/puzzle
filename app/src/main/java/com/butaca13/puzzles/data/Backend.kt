package com.butaca13.puzzles.data

/**
 * A server for the player and the global rankings, whichever it is. The rest of the app talks only to
 * these interfaces, so one server can take another's place, or a new one be added, without touching it:
 * a new server is a class like [HttpBackend], an entry in [BackendKind] and a branch in [Backends].
 *
 * What stays on the phone (the queue of times, the cached rankings, the player's state) is not here but
 * in [ScoreUploads], [GlobalRankings] and [PlayerAccount], the same for every server.
 */
interface Backend {
    /** Which server this is, as [BackendKind.id] names it. */
    val id: String
    val players: PlayerApi
    val deletion: PlayerDeletionApi
    val scores: ScoreApi
}

/** The servers a build can be made for, named as Gradle's `-PpuzzlesBackend=` takes them. */
enum class BackendKind(val id: String) {
    /** No server: the app plays on its own, as release builds until one is set up. */
    NONE("none"),

    /** The project's own API (Symfony), over HTTP. */
    API("api"),

    /** Firebase: Cloud Functions and Firestore, see [FirebaseBackend]. */
    FIREBASE("firebase"),
    ;

    companion object {
        fun of(id: String): BackendKind =
            entries.firstOrNull { it.id == id } ?: throw IllegalArgumentException("Servidor desconocido: $id")
    }
}

/** The project's own API, at [baseUrl] (ending in the version, as `…/api/v1/`). */
class HttpBackend(baseUrl: String, onFailure: (String, Throwable?) -> Unit = { _, _ -> }) : Backend {
    private val playerApi = HttpPlayerApi(baseUrl, onFailure)

    override val id = BackendKind.API.id
    override val players: PlayerApi = playerApi
    override val deletion: PlayerDeletionApi = playerApi
    override val scores: ScoreApi = HttpScoreApi(baseUrl, onFailure)
}

/**
 * Keeps the player and its times with the server this build talks to. No server has what another one
 * has, so when the app finds itself on a different kind of server than the player was last on, it
 * starts there as on a new one: the player is sent again, with the same id and secret; every best time
 * on this phone is queued again, in place of what was waiting for the old server; and the rankings
 * downloaded from the old one are dropped, so that its tables are not shown as the new one's.
 *
 * A build without a server changes nothing, so going back to the same server later finds everything
 * as it was. A new address for the same kind of server (the API in Docker, say) is not a change: if
 * that one has lost the player, the times find out, as ever ([SubmitResult.PlayerUnknown]).
 */
class ServerChange(private val store: KeyValueStore, private val account: PlayerAccount) {

    /**
     * Whether [backendId] (null for no server) is a change, now dealt with. Call it at start, before
     * any sync runs.
     */
    fun follow(backendId: String?, games: List<String>, variants: List<String>): Boolean {
        if (backendId == null) return false
        // Before this was noted, the API was the only server there was.
        val last = store.get(KEY) ?: BackendKind.API.id
        if (last == backendId) {
            store.put(KEY, backendId)
            return false
        }

        account.markMovedToAnotherServer()
        ScoreUploads(store).clear()
        Records(store).queueAll(games, variants)
        GlobalRankings(store, api = null).forgetAll(games, variants)
        store.put(KEY, backendId)
        return true
    }

    private companion object {
        const val KEY = "server/backend"
    }
}

/** Makes the server a build asks for. */
object Backends {
    /** Whether [create] makes a server: not for [BackendKind.NONE], nor for the API with no address to find it at. */
    fun configured(kind: BackendKind, apiUrl: String): Boolean = when (kind) {
        BackendKind.NONE -> false
        BackendKind.API -> apiUrl.isNotEmpty()
        BackendKind.FIREBASE -> true
    }

    /**
     * Null when not [configured]. [firebase] is only asked for by a build for Firebase, so that others
     * never start it. [onFailure] hears why a request failed, for the log.
     */
    fun create(
        kind: BackendKind,
        apiUrl: String,
        firebase: () -> FirebaseCalls,
        onFailure: (String, Throwable?) -> Unit = { _, _ -> },
    ): Backend? = when {
        !configured(kind, apiUrl) -> null
        kind == BackendKind.API -> HttpBackend(apiUrl, onFailure)
        kind == BackendKind.FIREBASE -> FirebaseBackend(firebase(), onFailure)
        else -> null
    }
}
