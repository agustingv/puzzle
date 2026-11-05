package com.agustingv.puzzles.data

import android.content.Context
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.security.SecureRandom
import java.util.UUID
import kotlin.random.Random
import kotlin.random.asKotlinRandom

/** Names the app makes up, and the rule every name follows, the same as the server's. */
object PlayerNames {
    private const val MIN_LENGTH = 3
    private const val MAX_LENGTH = 24
    private val PATTERN = Regex("""^[\p{L}\p{N}][\p{L}\p{N} _-]*$""")

    /** The avatars' animals, so that every name has its avatar. The rest are masculine. */
    private val feminine = setOf(
        "ardilla", "nutria", "tortuga", "jirafa", "cebra", "ballena", "abeja", "hormiga", "rana", "foca",
        "liebre", "oveja", "mariposa", "pantera", "gallina", "llama",
    )

    /** Each animal with its gender, so that the adjective agrees with it. */
    private val animals = AvatarCatalog.animals.map { it.label to (it.id !in feminine) }

    /** Masculine and feminine forms. */
    private val adjectives = listOf(
        "Astuto" to "Astuta", "Veloz" to "Veloz", "Sabio" to "Sabia", "Valiente" to "Valiente",
        "Curioso" to "Curiosa", "Audaz" to "Audaz", "Tranquilo" to "Tranquila", "Feliz" to "Feliz",
        "Ágil" to "Ágil", "Atento" to "Atenta", "Genial" to "Genial", "Brillante" to "Brillante",
        "Paciente" to "Paciente", "Intrépido" to "Intrépida", "Risueño" to "Risueña",
        "Despierto" to "Despierta", "Sereno" to "Serena", "Listo" to "Lista",
    )

    fun random(random: Random = Random.Default): String {
        val (animal, masculine) = animals.random(random)
        val (he, she) = adjectives.random(random)
        return "$animal ${if (masculine) he else she} ${random.nextInt(1, 100)}"
    }

    /** Spaces at the ends do not count. */
    fun isValid(name: String): Boolean {
        val trimmed = name.trim()
        return trimmed.length in MIN_LENGTH..MAX_LENGTH && PATTERN.matches(trimmed)
    }

    /** Every name [random] can make. */
    internal fun all(): List<String> = animals.flatMap { (animal, masculine) ->
        adjectives.map { (he, she) -> "$animal ${if (masculine) he else she} 99" }
    }
}

/** How far the player is from being on the server. */
/** [PENDING] has not been tried yet, or is waiting for a connection; [FAILED] reached for the server and got no good answer. */
enum class SyncStatus { SYNCED, PENDING, FAILED, OFFLINE, NO_SERVER, NAME_REJECTED }

/**
 * The player on this phone. There is no sign-up: the first launch makes one up, with a random id and
 * name and a secret that proves to the server that the player is this phone's. Every change waits
 * here, marked as pending, until it reaches the server; see [PlayerSync].
 *
 * The state is Compose state, so the settings follow changes the sync makes in the background.
 */
@Stable
class PlayerAccount(private val store: KeyValueStore, private val random: Random = secureRandom()) {

    var id: String = store.get(ID_KEY) ?: create()
        private set

    /** Never shown: only sent to the server, which keeps a hash of it. */
    var secret: String = store.get(SECRET_KEY).orEmpty()
        private set

    var name by mutableStateOf(store.get(NAME_KEY).orEmpty())
        private set

    /** With this on, the app makes no connection at all. */
    var offline by mutableStateOf(store.get(OFFLINE_KEY) == YES)
        private set

    /** Something changed that the server has not been told yet. */
    var pending by mutableStateOf(store.get(PENDING_KEY) == YES)
        private set

    var nameRejected by mutableStateOf(store.get(REJECTED_KEY) == YES)
        private set

    /**
     * The server has this player: it has been sent at least once. The global rankings are seen, and
     * times sent, only from then on. A player synced before this was noted counts, since it was sent.
     */
    var registered by mutableStateOf(store.get(REGISTERED_KEY)?.let { it == YES } ?: (store.get(PENDING_KEY) == NO))
        private set

    /** The last attempt to send the pending changes got no answer, or a fault from the server. */
    var syncFailed by mutableStateOf(store.get(FAILED_KEY) == YES)
        private set

    var avatar by mutableStateOf(store.get(AVATAR_KEY)?.let(Avatar::decode) ?: Avatar.initial(name, id))
        private set

    init {
        // A player from before avatars, or with one this version can not read: it gets the initial
        // avatar, which the server has not seen yet.
        if (store.get(AVATAR_KEY)?.let(Avatar::decode) == null) {
            store.put(AVATAR_KEY, avatar.encode())
            updatePending(true)
        }
    }

    fun status(serverConfigured: Boolean): SyncStatus = when {
        offline -> SyncStatus.OFFLINE
        !serverConfigured -> SyncStatus.NO_SERVER
        nameRejected -> SyncStatus.NAME_REJECTED
        pending && syncFailed -> SyncStatus.FAILED
        pending -> SyncStatus.PENDING
        else -> SyncStatus.SYNCED
    }

    /** Whether the name was valid, and so taken. */
    fun rename(newName: String): Boolean {
        if (!PlayerNames.isValid(newName)) return false

        name = newName.trim()
        store.put(NAME_KEY, name)
        updatePending(true)
        updateRejected(false)
        return true
    }

    fun changeAvatar(newAvatar: Avatar) {
        if (newAvatar == avatar) return

        avatar = newAvatar
        store.put(AVATAR_KEY, newAvatar.encode())
        updatePending(true)
    }

    fun changeOffline(value: Boolean) {
        offline = value
        store.put(OFFLINE_KEY, if (value) YES else NO)
    }

    /** The server now has [sentName] and [sentAvatar]. A change made while they were being sent is still pending. */
    internal fun markSynced(sentName: String, sentAvatar: String = avatar.encode()) {
        updateFailed(false)
        if (!registered) {
            registered = true
            store.put(REGISTERED_KEY, YES)
        }
        if (name == sentName && avatar.encode() == sentAvatar) updatePending(false)
    }

    /**
     * The server has lost this player. It is sent again as it is, with the same id and secret, which
     * the server takes as a new registration; until then it is not known there.
     */
    internal fun markUnknownToServer() {
        registered = false
        store.put(REGISTERED_KEY, NO)
        updatePending(true)
    }

    internal fun markRejected() {
        updateFailed(false)
        updateRejected(true)
    }

    /** The server could not be reached, or failed: the changes stay pending and are tried again later. */
    internal fun markFailed() {
        updateFailed(true)
    }

    /**
     * The server deleted this player at its request. The name and avatar stay, under a new id and secret
     * that the server has never seen, and the app stops connecting until the player says otherwise.
     */
    internal fun forgetServerPlayer() {
        changeOffline(true)
        replaceIdentity()
    }

    /** The id was someone else's: an all but impossible clash of random ids. Start over with new ones. */
    internal fun replaceIdentity() {
        updateFailed(false)
        registered = false
        store.put(REGISTERED_KEY, NO)
        id = newIdentity()
        updatePending(true)
    }

    private fun create(): String {
        val newId = newIdentity()
        store.put(NAME_KEY, PlayerNames.random(random))
        store.put(PENDING_KEY, YES)
        return newId
    }

    private fun newIdentity(): String {
        val newId = UUID(random.nextLong(), random.nextLong()).let { raw ->
            // A version 4 UUID, as the server expects, from this account's own randomness.
            UUID(
                (raw.mostSignificantBits and -0xf001L) or 0x4000L,
                (raw.leastSignificantBits and 0x3fffffffffffffffL) or Long.MIN_VALUE,
            )
        }.toString()
        secret = random.nextBytes(SECRET_BYTES).joinToString("") { "%02x".format(it) }
        store.put(ID_KEY, newId)
        store.put(SECRET_KEY, secret)
        return newId
    }

    private fun updatePending(value: Boolean) {
        pending = value
        store.put(PENDING_KEY, if (value) YES else NO)
    }

    private fun updateFailed(value: Boolean) {
        syncFailed = value
        store.put(FAILED_KEY, if (value) YES else NO)
    }

    private fun updateRejected(value: Boolean) {
        nameRejected = value
        store.put(REJECTED_KEY, if (value) YES else NO)
    }

    companion object {
        private const val ID_KEY = "player/id"
        private const val SECRET_KEY = "player/secret"
        private const val NAME_KEY = "player/name"
        private const val OFFLINE_KEY = "player/offline"
        private const val PENDING_KEY = "player/pending"
        private const val REJECTED_KEY = "player/rejected"
        private const val FAILED_KEY = "player/sync-failed"
        private const val REGISTERED_KEY = "player/registered"
        private const val AVATAR_KEY = "player/avatar"
        private const val YES = "yes"
        private const val NO = "no"
        private const val SECRET_BYTES = 32

        private fun secureRandom(): Random = SecureRandom().asKotlinRandom()

        @Volatile
        private var instance: PlayerAccount? = null

        /** One for the whole app, so that the screen and the background sync share it. */
        fun of(context: Context): PlayerAccount = instance ?: synchronized(this) {
            instance ?: PlayerAccount(AppStorage.of(context)).also { instance = it }
        }
    }
}
