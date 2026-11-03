package com.agustingv.puzzles

import android.app.Application
import com.agustingv.puzzles.data.PlayerAccount
import com.agustingv.puzzles.data.PlayerSyncScheduler

class PuzzlesApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // The first launch makes up the player; every launch catches up with the server if needed.
        PlayerAccount.of(this)
        PlayerSyncScheduler.request(this)
    }
}
