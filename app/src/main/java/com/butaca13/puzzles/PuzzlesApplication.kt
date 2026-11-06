package com.butaca13.puzzles

import android.app.Application
import com.butaca13.puzzles.data.AppStorage
import com.butaca13.puzzles.data.PlayerAccount
import com.butaca13.puzzles.data.PlayerSyncScheduler
import com.butaca13.puzzles.data.Records
import com.butaca13.puzzles.ui.menu.GameInfo

class PuzzlesApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // The first launch makes up the player; every launch catches up with the server if needed.
        PlayerAccount.of(this)
        // Times kept from before the rankings join the queue for them, once: after that they have ids.
        Records(AppStorage.of(this)).upgradeAll(
            games = GameInfo.entries.map { it.id },
            variants = GameInfo.entries.flatMap { it.variants }.distinct(),
        )
        PlayerSyncScheduler.request(this)
    }
}
