package com.example.bhashabridge_v3_4

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import org.vosk.Model
import org.vosk.Recognizer
import org.json.JSONObject
import java.nio.ByteOrder

object AudioFileTranslator {

    private const val TAG = "AUDIOFILE"
    private const val TARGET_SAMPLE_RATE = 16000
    private const val MAX_DURATION_MS = 60_000L
    private const val CHUNK_SIZE = 4096

    // ─────────────────────────────────────────────────────────────────────
    // transcribeFile — decodes audio file → PCM → Vosk → transcript
    // onPartial called with interim text as recognition progresses
    // ─────────────────────────────────────────────────────────────────────
    fun transcribeFile(
        context: Context,
        uri: Uri,
        model: Model,
        onPartial: (String) -> Unit
    ): String {
        val pcm = decodeToVoskPCM(context, uri) ?: return ""
        val recognizer = Recognizer(model, TARGET_SAMPLE_RATE.toFloat())
        val buffer = ShortArray(CHUNK_SIZE)
        var offset = 0
        val sb = StringBuilder()

        while (offset < pcm.size) {
            val end = minOf(offset + CHUNK_SIZE, pcm.size)
            val chunk = pcm.copyOfRange(offset, end)

            if (recognizer.acceptWaveForm(chunk, chunk.size)) {
                val result = JSONObject(recognizer.result).optString("text", "").trim()
                if (result.isNotBlank()) {
                    if (sb.isNotEmpty()) sb.append(" ")
                    sb.append(result)
                    onPartial(sb.toString())
                }
            } else {
                val partial = JSONObject(recognizer.partialResult).optString("partial", "").trim()
                if (partial.isNotBlank()) {
                    val preview = if (sb.isNotEmpty()) "$sb $partial" else partial
                    onPartial(preview)
                }
            }
            offset = end
        }

        // Get final result
        val finalResult = JSONObject(recognizer.finalResult).optString("text", "").trim()
        if (finalResult.isNotBlank()) {
            if (sb.isNotEmpty()) sb.append(" ")
            sb.append(finalResult)
        }

        recognizer.close()
        Log.d(TAG, "Transcription complete: ${sb.toString().take(80)}")
        return sb.toString().trim()
    }

    // ─────────────────────────────────────────────────────────────────────
    // decodeToVoskPCM — decodes any audio file to 16kHz mono PCM ShortArray
    // Supports: MP3, M4A/AAC, OGG (Vorbis), WAV
    // ─────────────────────────────────────────────────────────────────────
    private fun decodeToVoskPCM(context: Context, uri: Uri): ShortArray? {
        return try {
            val extractor = MediaExtractor()
            extractor.setDataSource(context, uri, null)

            var audioTrackIndex = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val trackFormat = extractor.getTrackFormat(i)
                val mime = trackFormat.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    format = trackFormat
                    break
                }
            }

            if (audioTrackIndex == -1 || format == null) {
                Log.e(TAG, "No audio track found")
                return null
            }

            val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION))
                format.getLong(MediaFormat.KEY_DURATION) else Long.MAX_VALUE
            if (durationUs > 0 && durationUs / 1000 > MAX_DURATION_MS) {
                Log.e(TAG, "Audio too long: ${durationUs / 1_000_000}s")
                return null
            }

            val sourceSampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            val mime = format.getString(MediaFormat.KEY_MIME)!!

            extractor.selectTrack(audioTrackIndex)

            val codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val pcmSamples = mutableListOf<Short>()
            val bufferInfo = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false

            while (!outputDone) {
                if (!inputDone) {
                    val inputIdx = codec.dequeueInputBuffer(10_000)
                    if (inputIdx >= 0) {
                        val inputBuf = codec.getInputBuffer(inputIdx)!!
                        val sampleSize = extractor.readSampleData(inputBuf, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(inputIdx, 0, 0, 0,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inputIdx, 0, sampleSize,
                                extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                val outputIdx = codec.dequeueOutputBuffer(bufferInfo, 10_000)
                if (outputIdx >= 0) {
                    val outputBuf = codec.getOutputBuffer(outputIdx)!!
                    outputBuf.order(ByteOrder.LITTLE_ENDIAN)
                    val shortBuf = outputBuf.asShortBuffer()
                    while (shortBuf.hasRemaining()) pcmSamples.add(shortBuf.get())
                    codec.releaseOutputBuffer(outputIdx, false)
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0)
                        outputDone = true
                }
            }

            codec.stop(); codec.release()
            extractor.release()

            // Downmix to mono
            val mono = if (channelCount > 1) {
                ShortArray(pcmSamples.size / channelCount) { i ->
                    var sum = 0L
                    for (ch in 0 until channelCount) sum += pcmSamples[i * channelCount + ch]
                    (sum / channelCount).toShort()
                }
            } else pcmSamples.toShortArray()

            // Resample to 16kHz if needed
            if (sourceSampleRate == TARGET_SAMPLE_RATE) mono
            else resample(mono, sourceSampleRate, TARGET_SAMPLE_RATE)

        } catch (e: Exception) {
            Log.e(TAG, "Decode failed", e)
            null
        }
    }

    private fun resample(input: ShortArray, srcRate: Int, dstRate: Int): ShortArray {
        if (srcRate == dstRate) return input
        val ratio = srcRate.toDouble() / dstRate.toDouble()
        val outputSize = (input.size / ratio).toInt()
        return ShortArray(outputSize) { i ->
            val srcPos = i * ratio
            val idx = srcPos.toInt().coerceIn(0, input.size - 1)
            val frac = srcPos - idx
            val s0 = input[idx].toDouble()
            val s1 = input[(idx + 1).coerceIn(0, input.size - 1)].toDouble()
            (s0 + frac * (s1 - s0)).toInt().toShort()
        }
    }
}
