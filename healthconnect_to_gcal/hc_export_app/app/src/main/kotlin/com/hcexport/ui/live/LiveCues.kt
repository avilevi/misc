package com.hcexport.ui.live

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * The live workout's two out-of-band cues: a short beep and a buzz.
 *
 * They are deliberately simple — a resting athlete is looking at the bar, not
 * the phone — and every call is guarded: a device without a tone generator or
 * a vibrator must never break the timer.
 */
class LiveCues(
    context: Context,
    private val soundEnabled: Boolean,
    private val vibrationEnabled: Boolean,
) {
    private val appContext = context.applicationContext

    private val tone: ToneGenerator? by lazy {
        runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 80) }.getOrNull()
    }

    private val vibrator: Vibrator? by lazy {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE)
                    as? VibratorManager)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        }.getOrNull()
    }

    /** The last three seconds of a countdown. */
    fun tick() {
        if (soundEnabled) {
            runCatching { tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 90) }
        }
        buzz(45)
    }

    /** A timer reached zero. */
    fun go() {
        if (soundEnabled) {
            runCatching { tone?.startTone(ToneGenerator.TONE_PROP_ACK, 260) }
        }
        buzz(220)
    }

    fun release() {
        runCatching { tone?.release() }
    }

    private fun buzz(millis: Long) {
        if (!vibrationEnabled) return
        runCatching {
            val effect = VibrationEffect.createOneShot(
                millis,
                VibrationEffect.DEFAULT_AMPLITUDE,
            )
            vibrator?.vibrate(effect)
        }
    }
}
