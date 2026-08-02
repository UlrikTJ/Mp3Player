package com.mp3player.playback

import android.content.Context
import android.content.Intent
import android.media.audiofx.AudioEffect
import android.media.audiofx.Equalizer

data class EqualizerBand(
    val band: Short,
    val centerFreqHz: Int,
    var level: Short,
    val minLevel: Short,
    val maxLevel: Short
)

object EqualizerManager {
    private var equalizerA: Equalizer? = null
    private var equalizerB: Equalizer? = null

    var isEnabled: Boolean = true
        private set

    fun init(context: Context, sessionA: Int, sessionB: Int) {
        release()

        try {
            // Broadcast open session so system/third-party EQ apps detect it
            if (sessionA != 0) {
                val intentA = Intent(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION)
                intentA.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, sessionA)
                intentA.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
                context.sendBroadcast(intentA)

                try {
                    equalizerA = Equalizer(0, sessionA).apply { enabled = isEnabled }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            if (sessionB != 0) {
                val intentB = Intent(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION)
                intentB.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, sessionB)
                intentB.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
                context.sendBroadcast(intentB)

                try {
                    equalizerB = Equalizer(0, sessionB).apply { enabled = isEnabled }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun setEnabled(enabled: Boolean) {
        isEnabled = enabled
        try {
            equalizerA?.enabled = enabled
            equalizerB?.enabled = enabled
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getBands(): List<EqualizerBand> {
        val eq = equalizerA ?: equalizerB ?: return emptyList()
        return try {
            val numBands = eq.numberOfBands
            val range = eq.bandLevelRange
            val result = mutableListOf<EqualizerBand>()
            for (i in 0 until numBands) {
                val band = i.toShort()
                val freqHz = eq.getCenterFreq(band) / 1000
                val level = eq.getBandLevel(band)
                result.add(EqualizerBand(band, freqHz, level, range[0], range[1]))
            }
            result
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    fun setBandLevel(band: Short, level: Short) {
        try {
            equalizerA?.setBandLevel(band, level)
            equalizerB?.setBandLevel(band, level)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getPresets(): List<String> {
        val eq = equalizerA ?: equalizerB ?: return emptyList()
        return try {
            val numPresets = eq.numberOfPresets
            val presets = mutableListOf<String>()
            for (i in 0 until numPresets) {
                presets.add(eq.getPresetName(i.toShort()))
            }
            presets
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    fun usePreset(presetIndex: Short) {
        try {
            equalizerA?.usePreset(presetIndex)
            equalizerB?.usePreset(presetIndex)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun release() {
        try {
            equalizerA?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        try {
            equalizerB?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        equalizerA = null
        equalizerB = null
    }
}
