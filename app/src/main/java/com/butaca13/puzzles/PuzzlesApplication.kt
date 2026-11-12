package com.butaca13.puzzles

import android.app.Application
import com.butaca13.puzzles.data.AppStorage
import com.butaca13.puzzles.data.PlayerAccount
import com.butaca13.puzzles.data.PlayerSyncScheduler
import com.butaca13.puzzles.data.Records
import com.butaca13.puzzles.data.ServerChange
import com.butaca13.puzzles.ui.menu.GameInfo

class PuzzlesApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // The first launch makes up the player; every launch catches up with the server if needed.
        val account = PlayerAccount.of(this)
        val storage = AppStorage.of(this)
        val games = GameInfo.entries.map { it.id }
        val variants = GameInfo.entries.flatMap { it.variants }.distinct()
        // Times kept from before the rankings join the queue for them, once: after that they have ids.
        Records(storage).upgradeAll(games, variants)
        // A build for another server starts the player and its times over there.
        ServerChange(storage, account).follow(PlayerSyncScheduler.backend(this)?.id, games, variants)
        PlayerSyncScheduler.request(this)
    }
}
