package com.lanzkila.kirinkara.audio

import java.io.Closeable
import java.io.File
import java.io.RandomAccessFile

/** Streams little-endian 16-bit stereo PCM to a WAV file without keeping songs in RAM. */
class WavWriter(file: File, private val sampleRate: Int) : Closeable {
    private val output = RandomAccessFile(file, "rw")
    private var dataBytes = 0L
    private var finished = false

    init {
        require(sampleRate > 0)
        output.setLength(0)
        output.write(ByteArray(44))
    }

    fun write(samples: ShortArray) {
        check(!finished)
        require(samples.size % 2 == 0)
        val byteCount = samples.size.toLong() * 2
        require(dataBytes + byteCount <= 0xFFFFFFFFL - 36L) {
            "Fail WAV terlalu besar (had format WAV 4 GB)"
        }
        val bytes = ByteArray(samples.size * 2)
        for (i in samples.indices) {
            val s = samples[i].toInt()
            bytes[i * 2] = s.toByte()
            bytes[i * 2 + 1] = (s shr 8).toByte()
        }
        output.write(bytes)
        dataBytes += byteCount
    }

    fun finish() {
        if (finished) return
        require(dataBytes > 0) { "Tiada data audio dijumpai" }
        output.seek(0)
        output.writeBytes("RIFF")
        writeUInt32(dataBytes + 36)
        output.writeBytes("WAVE")
        output.writeBytes("fmt ")
        writeUInt32(16)
        writeUInt16(1) // PCM
        writeUInt16(2) // Stereo
        writeUInt32(sampleRate.toLong())
        writeUInt32(sampleRate.toLong() * 4)
        writeUInt16(4) // Block align
        writeUInt16(16) // Bits per sample
        output.writeBytes("data")
        writeUInt32(dataBytes)
        finished = true
    }

    private fun writeUInt16(value: Int) {
        output.write(value and 255)
        output.write((value ushr 8) and 255)
    }

    private fun writeUInt32(value: Long) {
        for (shift in 0..24 step 8) output.write(((value ushr shift) and 255).toInt())
    }

    override fun close() = output.close()
}
