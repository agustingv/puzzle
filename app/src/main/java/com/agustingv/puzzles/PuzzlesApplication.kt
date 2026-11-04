package com.agustingv.puzzles

import android.app.Application
import com.agustingv.puzzles.data.AppStorage
import com.agustingv.puzzles.data.PlayerAccount
import com.agustingv.puzzles.data.PlayerSyncScheduler
import com.agustingv.puzzles.data.Records
import com.agustingv.puzzles.ui.menu.GameInfo

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
