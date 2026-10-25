package com.lanzkila.kirinkara.audio

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.io.File
import java.nio.ByteOrder

/** Decode a device-supported audio track, process it in blocks and write stereo WAV. */
class AudioConverter(private val context: Context) {
    class Cancelled : Exception("Proses dibatalkan")

    fun convert(
        source: Uri,
        destination: File,
        amount: Float,
        preserveBass: Boolean,
        cancelled: () -> Boolean,
        onProgress: (Int) -> Unit,
    ) {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        var writer: WavWriter? = null
        try {
            extractor.setDataSource(context, source, null)
            val track = (0 until extractor.trackCount).firstOrNull { index ->
                extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)
                    ?.startsWith("audio/") == true
            } ?: error("Fail ini tiada trek audio yang boleh dibaca")

            val format = extractor.getTrackFormat(track)
            val mime = format.getString(MediaFormat.KEY_MIME)
                ?: error("Format audio tidak diketahui")
            extractor.selectTrack(track)
            val decoder = MediaCodec.createDecoderByType(mime)
            codec = decoder
            decoder.configure(format, null, null, 0)
            decoder.start()

            val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) {
                format.getLong(MediaFormat.KEY_DURATION)
            } else 0L
            val bufferInfo = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            var reducer: CenterReducer? = null
            var outputRate = 0
            var lastProgress = -1

            fun configureOutput() {
                val pcmFormat = decoder.outputFormat
                val channels = pcmFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                if (channels != 2) {
                    error("Mod asas memerlukan lagu stereo. Fail ini mempunyai $channels saluran")
                }
                val encoding = if (pcmFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                    pcmFormat.getInteger(MediaFormat.KEY_PCM_ENCODING)
                } else AudioFormat.ENCODING_PCM_16BIT
                if (encoding != AudioFormat.ENCODING_PCM_16BIT) {
                    error("Decoder menghasilkan PCM bukan 16-bit; tidak disokong oleh mod asas")
                }
                val rate = pcmFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                if (writer != null && outputRate != rate) {
                    error("Kadar sampel berubah semasa pengekodan")
                }
                if (writer == null) {
                    outputRate = rate
                    reducer = CenterReducer(rate, amount, preserveBass)
                    writer = WavWriter(destination, rate)
                }
            }

            while (!outputDone) {
                if (cancelled()) throw Cancelled()
                if (!inputDone) {
                    val inputIndex = decoder.dequeueInputBuffer(10_000L)
                    if (inputIndex >= 0) {
                        val input = decoder.getInputBuffer(inputIndex)
                            ?: error("Buffer input decoder tiada")
                        input.clear()
                        val sampleSize = extractor.readSampleData(input, 0)
                        if (sampleSize < 0) {
                            decoder.queueInputBuffer(
                                inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            inputDone = true
                        } else {
                            decoder.queueInputBuffer(inputIndex, 0, sampleSize, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                when (val outputIndex = decoder.dequeueOutputBuffer(bufferInfo, 10_000L)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> configureOutput()
                    MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                    else -> if (outputIndex >= 0) {
                        if (bufferInfo.size > 0) {
                            if (writer == null) configureOutput()
                            val buffer = decoder.getOutputBuffer(outputIndex)
                                ?: error("Buffer output decoder tiada")
                            buffer.position(bufferInfo.offset)
                            buffer.limit(bufferInfo.offset + bufferInfo.size)
                            val pcm = buffer.slice().order(ByteOrder.nativeOrder())
                            require(pcm.remaining() % 4 == 0) {
                                "Blok PCM stereo bukan sejajar 16-bit"
                            }
                            val samples = ShortArray(pcm.remaining() / 2)
                            pcm.asShortBuffer().get(samples)
                            reducer!!.processInterleavedStereo(samples)
                            writer!!.write(samples)
                            if (durationUs > 0) {
                                val progress = (bufferInfo.presentationTimeUs * 100 / durationUs)
                                    .toInt().coerceIn(0, 99)
                                if (progress > lastProgress) {
                                    lastProgress = progress
                                    onProgress(progress)
                                }
                            }
                        }
                        outputDone = bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        decoder.releaseOutputBuffer(outputIndex, false)
                    }
                }
            }
            (writer ?: error("Tiada audio yang boleh dinyahkod")).finish()
            onProgress(100)
        } catch (failure: Exception) {
            destination.delete() // Only delete this conversion's new cache output.
            throw failure
        } finally {
            try { writer?.close() } catch (_: Exception) {}
            try { codec?.stop() } catch (_: Exception) {}
            codec?.release()
            extractor.release()
        }
    }
}
