package com.lanzkila.kirinkara.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class WavWriterTest {
    @Test fun writesValidWaveHeaderAndSamples() {
        val destination = File.createTempFile("kirinkara", ".wav")
        try {
            WavWriter(destination, 44100).use {
                it.write(shortArrayOf(100, -100, Short.MAX_VALUE, Short.MIN_VALUE))
                it.finish()
            }
            val bytes = destination.readBytes()
            assertEquals(52, bytes.size)
            assertEquals("RIFF", String(bytes.copyOfRange(0, 4)))
            assertEquals("WAVE", String(bytes.copyOfRange(8, 12)))
            assertEquals("data", String(bytes.copyOfRange(36, 40)))
            assertEquals(8, bytes[40].toInt() and 255)
            assertTrue(bytes[44] != 0.toByte())
        } finally {
            destination.delete()
        }
    }
}
