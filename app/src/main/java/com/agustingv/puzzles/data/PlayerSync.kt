package com.agustingv.puzzles.data

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.agustingv.puzzles.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/** What the server said to a player being saved. */
sealed interface SaveResult {
    data class Saved(val name: String) : SaveResult

    /** The id belongs to another player. */
    data object Taken : SaveResult

    /** The server does not accept what was sent, so sending it again will not help. */
    data object Rejected : SaveResult

    /** No answer, or a fault on the server's side: worth trying again later. */
    data object Failed : SaveResult
}

/** The server, behind an interface so that the sync can be tested without one. Calls block. */
fun interface PlayerApi {
    /** [avatar] as [Avatar.encode] writes it. */
    fun save(id: String, name: String, avatar: String, secret: String): SaveResult
}

/**
 * Registers or updates a player with one idempotent request: PUT /players/{id}. The API takes it as
 * a registration the first time and as a change after that, and a repeated request does no harm.
 */
class HttpPlayerApi(
    private val baseUrl: String,
    /** Why a request failed, for the log: failures are retried quietly, but should not be invisible. */
    private val onFailure: (String, Throwable?) -> Unit = { _, _ -> },
) : PlayerApi {

    override fun save(id: String, name: String, avatar: String, secret: String): SaveResult {
        val connection = try {
            URL("${baseUrl}players/$id").openConnection() as HttpURLConnection
        } catch (e: IOException) {
            onFailure("Dirección del API no válida", e)
            return SaveResult.Failed
        }

        return try {
            connection.requestMethod = "PUT"
            connection.connectTimeout = TIMEOUT_MILLIS
            connection.readTimeout = TIMEOUT_MILLIS
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json")

            val body = JSONObject().put("name", name).put("avatar", avatar).put("secret", secret).toString()
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

            when (connection.responseCode) {
                in 200..299 -> {
                    val answer = connection.inputStream.bufferedReader().use { it.readText() }
                    SaveResult.Saved(JSONObject(answer).getString("name"))
                }
                409 -> SaveResult.Taken
                400, 422 -> SaveResult.Rejected
                else -> SaveResult.Failed.also { onFailure("El API respondió ${connection.responseCode}", null) }
            }
        } catch (e: IOException) {
            onFailure("Sin respuesta del API", e)
            SaveResult.Failed
        } catch (e: org.json.JSONException) {
            onFailure("Respuesta del API ilegible", e)
            SaveResult.Failed
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val TIMEOUT_MILLIS = 15_000
    }
}

/** Brings the server up to date with the player on this phone. */
class PlayerSync(private val account: PlayerAccount, private val api: PlayerApi?) {

    enum class Outcome { DONE, TRY_LATER }

    fun run(): Outcome {
        repeat(MAX_ROUNDS) {
            if (account.offline || api == null || !account.pending || account.nameRejected) return Outcome.DONE

            val name = account.name
            val avatar = account.avatar.encode()
            when (api.save(account.id, name, avatar, account.secret)) {
                // A change made meanwhile leaves it pending, and the next round sends it.
                is SaveResult.Saved -> account.markSynced(name, avatar)
                SaveResult.Taken -> account.replaceIdentity()
                SaveResult.Rejected -> account.markRejected()
                SaveResult.Failed -> {
                    account.markFailed()
                    return Outcome.TRY_LATER
                }
            }
        }
        return if (account.pending && !account.nameRejected) Outcome.TRY_LATER else Outcome.DONE
    }

    private companion object {
        const val MAX_ROUNDS = 3
    }
}

/**
 * Runs the sync in the background, when there is a connection, even after the app is closed: first
 * the player, then the times waiting for the rankings, which the server takes only from a player it knows.
 */
class PlayerSyncWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val outcome = FullSync(
            account = PlayerAccount.of(applicationContext),
            playerApi = PlayerSyncScheduler.api(),
            uploads = ScoreUploads(AppStorage.of(applicationContext)),
            scoreApi = PlayerSyncScheduler.scoreApi(),
        ).run()
        if (outcome == PlayerSync.Outcome.TRY_LATER) Result.retry() else Result.success()
    }
}

object PlayerSyncScheduler {
    private const val WORK_NAME = "player-sync"
    private const val FIRST_RETRY_SECONDS = 30L
    private const val LOG_TAG = "PlayerSync"

    /** Empty when this build has no server, as release builds until one is set up. */
    val serverConfigured: Boolean get() = BuildConfig.API_URL.isNotEmpty()

    fun api(): PlayerApi? = if (!serverConfigured) {
        null
    } else {
        HttpPlayerApi(BuildConfig.API_URL) { reason, error -> Log.w(LOG_TAG, reason, error) }
    }

    fun scoreApi(): ScoreApi? = if (!serverConfigured) {
        null
    } else {
        HttpScoreApi(BuildConfig.API_URL) { reason, error -> Log.w(LOG_TAG, reason, error) }
    }

    /**
     * Asks for a sync, when the player or some times are waiting to be sent, or calls off any waiting
     * one when the player wants no connections.
     */
    fun request(context: Context) {
        val account = PlayerAccount.of(context)
        val work = WorkManager.getInstance(context)

        if (account.offline || !serverConfigured) {
            work.cancelUniqueWork(WORK_NAME)
            return
        }
        if (!account.pending && !ScoreUploads(AppStorage.of(context)).isNotEmpty()) return

        val request = OneTimeWorkRequestBuilder<PlayerSyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, FIRST_RETRY_SECONDS, TimeUnit.SECONDS)
            .build()
        work.enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }
}
