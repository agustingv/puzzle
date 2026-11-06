package com.butaca13.puzzles.ui.theme

import android.content.Context
import android.provider.Settings

/** False when the system's animations are off, as some people need for motion sensitivity. */
fun animationsEnabled(context: Context): Boolean =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
