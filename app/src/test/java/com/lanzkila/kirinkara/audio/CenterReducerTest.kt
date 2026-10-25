package com.lanzkila.kirinkara.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CenterReducerTest {
    @Test fun fullReductionCancelsIdenticalStereo() {
        val samples = shortArrayOf(12000, 12000, -4000, -4000, 120, 120)
        CenterReducer(44100, 1f, false).processInterleavedStereo(samples)
        assertArrayEquals(shortArrayOf(0, 0, 0, 0, 0, 0), samples)
    }

    @Test fun sideSignalSurvives() {
        val samples = shortArrayOf(9000, -9000)
        CenterReducer(44100, 1f, false).processInterleavedStereo(samples)
        assertArrayEquals(shortArrayOf(9000, -9000), samples)
    }

    @Test fun preservedBassRetainsSomeCentre() {
        val samples = ShortArray(4410 * 2) { 5000 }
        CenterReducer(44100, 1f, true).processInterleavedStereo(samples)
        assertTrue(samples.last() > 4000)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnpairedSamples() {
        CenterReducer(44100, 0.7f, false).processInterleavedStereo(shortArrayOf(1))
    }
}
