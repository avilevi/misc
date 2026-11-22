package com.hcexport.wear.live

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** The wrist's countdown voice: short ticks for 3–2–1, a longer pattern at go. */
class WatchCues(context: Context) : LiveCues {

    private val vibrator: Vibrator? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }.getOrNull()

    override fun tick() {
        runCatching {
            vibrator?.vibrate(VibrationEffect.createOneShot(55L, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }

    override fun go() {
        runCatching {
            vibrator?.vibrate(
                VibrationEffect.createWaveform(
                    longArrayOf(0L, 120L, 70L, 200L),
                    intArrayOf(0, 255, 0, 255),
                    -1,
                )
            )
        }
    }

    fun release() {
        runCatching { vibrator?.cancel() }
    }
}
