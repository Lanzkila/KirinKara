package com.lanzkila.kirinkara.audio

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.roundToInt

/**
 * A simple stereo mid/side vocal attenuator, NOT AI source separation.
 * Voices and other sounds panned to the centre can be attenuated together.
 * One instance must be kept across all decoded blocks of a song.
 */
class CenterReducer(
    sampleRate: Int,
    private val amount: Float,
    private val preserveBass: Boolean,
) {
    init {
        require(sampleRate > 0)
        require(amount in 0f..1f)
    }

    private val lowPassCoefficient = 1.0 - exp(-2.0 * PI * 180.0 / sampleRate)
    private var lowCentre = 0.0

    fun processInterleavedStereo(samples: ShortArray) {
        require(samples.size % 2 == 0) { "PCM stereo mesti mempunyai bilangan sampel genap" }
        for (i in samples.indices step 2) {
            val left = samples[i].toDouble()
            val right = samples[i + 1].toDouble()
            val centre = (left + right) * 0.5
            val side = (left - right) * 0.5
            lowCentre += lowPassCoefficient * (centre - lowCentre)

            val remainingCentre = if (preserveBass) {
                lowCentre + (centre - lowCentre) * (1.0 - amount)
            } else {
                centre * (1.0 - amount)
            }
            samples[i] = clamp16(remainingCentre + side)
            samples[i + 1] = clamp16(remainingCentre - side)
        }
    }

    private fun clamp16(value: Double): Short =
        value.roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
}
