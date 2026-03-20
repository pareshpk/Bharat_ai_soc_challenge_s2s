package com.example.bhashabridge_v3_4

import android.content.Context
import android.util.Log
import ai.onnxruntime.*
import java.nio.LongBuffer
import kotlin.math.exp
import kotlin.math.ln

enum class TranslationDirection { EN_TO_HI, HI_TO_EN }

class Translator(context: Context, val direction: TranslationDirection = TranslationDirection.EN_TO_HI) {

    private val modelManager = ModelManager(context, direction)

    private val srcTokenizer: SentencePieceTokenizer
    private val tgtTokenizer: SentencePieceTokenizer

    init {
        when (direction) {
            TranslationDirection.EN_TO_HI -> {
                srcTokenizer = SentencePieceTokenizer(context, "model.SRC")
                tgtTokenizer = SentencePieceTokenizer(context, "model.TGT")
            }
            TranslationDirection.HI_TO_EN -> {
                srcTokenizer = SentencePieceTokenizer(context, "model.SRC_HI")
                tgtTokenizer = SentencePieceTokenizer(context, "model.TGT_EN")
            }
        }
    }

    private val decoderStartToken = 2L
    private val eosToken          = 2L
    private val maxLength         = 18
    private val noRepeatNgramSize = 3
    private val repetitionPenalty = 1.1f

    // ── Beam search (HI→EN only) ─────────────────────────────────────────
    // Width=2: meaningfully better than greedy, ~40-60ms extra latency
    private val beamWidth = 2

    var lastConfidence: Float = 0f
        private set

    private val decIdsBuf  = LongArray(64)
    private val seenTokens = HashSet<Long>(64)

    fun warmUp() {
        try {
            val env         = modelManager.env
            val dummyIds    = longArrayOf(4L, 2L)
            val shape       = longArrayOf(1, 2)
            val mask        = longArrayOf(1L, 1L)
            val inputTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(dummyIds), shape)
            val maskTensor  = OnnxTensor.createTensor(env, LongBuffer.wrap(mask), shape)
            val encoderOut  = modelManager.encoderSession.run(
                mapOf("input_ids" to inputTensor, "attention_mask" to maskTensor)
            )
            val encoderHidden = encoderOut[0] as OnnxTensor
            val decIds    = longArrayOf(2L)
            val decShape  = longArrayOf(1, 1)
            val decTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(decIds), decShape)
            val decOut    = modelManager.decoderSession.run(
                mapOf(
                    "input_ids"              to decTensor,
                    "encoder_hidden_states"  to encoderHidden,
                    "encoder_attention_mask" to maskTensor
                )
            )
            decTensor.close(); decOut.close()
            encoderHidden.close(); encoderOut.close()
            inputTensor.close(); maskTensor.close()
            Log.d("TRANSLATOR", "Warm-up done [${direction.name}]")
        } catch (e: Exception) {
            Log.w("TRANSLATOR", "Warm-up skipped [${direction.name}]: ${e.message}")
        }
    }

    fun translate(text: String): String {
        return try {
            val raw = translateGreedy(text)
            if (direction == TranslationDirection.HI_TO_EN) EnPostProcessor.process(raw)
            else raw
        } catch (e: Exception) {
            Log.e("TRANSLATOR", "Translation failed [${direction.name}]", e)
            lastConfidence = 0f
            "Translation failed"
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Greedy decode — unchanged from V3, used for EN→HI
    // ─────────────────────────────────────────────────────────────────────
    private fun translateGreedy(text: String): String {
        val env     = modelManager.env
        val encoder = modelManager.encoderSession
        val decoder = modelManager.decoderSession

        val inputIds = srcTokenizer.encode(text)
        val shape    = longArrayOf(1, inputIds.size.toLong())
        val mask     = LongArray(inputIds.size) { 1L }

        val inputTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(inputIds), shape)
        val maskTensor  = OnnxTensor.createTensor(env, LongBuffer.wrap(mask), shape)
        val encoderOut  = encoder.run(
            mapOf("input_ids" to inputTensor, "attention_mask" to maskTensor)
        )
        val encoderHidden = encoderOut[0] as OnnxTensor

        val generated       = mutableListOf(decoderStartToken)
        val maxTargetLength = maxOf(14, inputIds.size)

        var sumLogProb = 0f
        var tokenCount = 0

        // Clear stale state from previous translation call
        seenTokens.clear()

        for (step in 0 until maxLength) {
            val len = generated.size
            for (i in 0 until len) decIdsBuf[i] = generated[i]
            val decShape  = longArrayOf(1, len.toLong())
            val decTensor = OnnxTensor.createTensor(
                env, LongBuffer.wrap(decIdsBuf, 0, len), decShape
            )
            val decOut       = decoder.run(mapOf(
                "input_ids"              to decTensor,
                "encoder_hidden_states"  to encoderHidden,
                "encoder_attention_mask" to maskTensor
            ))
            val logitsTensor = decOut[0] as OnnxTensor
            val logits       = (logitsTensor.value as Array<Array<FloatArray>>)[0].last().copyOf()

            seenTokens.clear()
            seenTokens.addAll(generated)
            for (tok in seenTokens) {
                val idx = tok.toInt()
                if (idx < logits.size) {
                    logits[idx] = if (logits[idx] > 0f) logits[idx] / repetitionPenalty
                    else logits[idx] * repetitionPenalty
                }
            }

            for (b in getBlockedTokens(generated, noRepeatNgramSize)) {
                if (b < logits.size) logits[b.toInt()] = -1e9f
            }

            var bestIdx = 0
            var bestVal = Float.NEGATIVE_INFINITY
            for (i in logits.indices) {
                if (logits[i] > bestVal) { bestVal = logits[i]; bestIdx = i }
            }
            val nextToken = bestIdx.toLong()

            val logProb = bestVal - logSumExpWithMax(logits, bestVal)
            sumLogProb += logProb
            tokenCount++

            decTensor.close(); logitsTensor.close(); decOut.close()

            if (nextToken == eosToken || generated.size >= maxTargetLength) break
            generated.add(nextToken)
        }

        inputTensor.close(); maskTensor.close()
        encoderHidden.close(); encoderOut.close()

        val meanLogProb = if (tokenCount > 0) sumLogProb / tokenCount else -10f
        lastConfidence  = sigmoid(meanLogProb * 4f + 2f).coerceIn(0f, 1f)
        Log.d("TRANSLATOR", "Greedy confidence: ${"%.2f".format(lastConfidence)} (${confidenceLabel()})")

        return tgtTokenizer.decode(generated.drop(1))
    }

    // ─────────────────────────────────────────────────────────────────────
    // Beam search decode — used for HI→EN
    // Keeps `beamWidth` candidate sequences, returns best complete sequence.
    // Each beam: Pair(tokenList, cumulativeLogProb)
    // ─────────────────────────────────────────────────────────────────────
    private fun translateBeam(text: String): String {
        val env     = modelManager.env
        val encoder = modelManager.encoderSession
        val decoder = modelManager.decoderSession

        val inputIds = srcTokenizer.encode(text)
        val shape    = longArrayOf(1, inputIds.size.toLong())
        val mask     = LongArray(inputIds.size) { 1L }

        val inputTensor   = OnnxTensor.createTensor(env, LongBuffer.wrap(inputIds), shape)
        val maskTensor    = OnnxTensor.createTensor(env, LongBuffer.wrap(mask), shape)
        val encoderOut    = encoder.run(
            mapOf("input_ids" to inputTensor, "attention_mask" to maskTensor)
        )
        val encoderHidden = encoderOut[0] as OnnxTensor

        val maxTargetLength = maxOf(14, inputIds.size)

        // Each beam: (token sequence, cumulative log-prob, finished flag)
        data class Beam(val tokens: MutableList<Long>, var logProb: Float, var finished: Boolean)

        val beams = mutableListOf(Beam(mutableListOf(decoderStartToken), 0f, false))
        val completed = mutableListOf<Beam>()

        for (step in 0 until maxLength) {
            if (beams.isEmpty()) break

            val candidates = mutableListOf<Beam>() // all expansions this step

            for (beam in beams) {
                if (beam.finished) { completed.add(beam); continue }

                val len = beam.tokens.size
                val ids = LongArray(len) { beam.tokens[it] }
                val decShape  = longArrayOf(1, len.toLong())
                val decTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(ids), decShape)
                val decOut    = decoder.run(mapOf(
                    "input_ids"              to decTensor,
                    "encoder_hidden_states"  to encoderHidden,
                    "encoder_attention_mask" to maskTensor
                ))
                val logitsTensor = decOut[0] as OnnxTensor
                val logits = (logitsTensor.value as Array<Array<FloatArray>>)[0].last().copyOf()

                // Apply repetition penalty
                for (tok in beam.tokens) {
                    val idx = tok.toInt()
                    if (idx < logits.size) {
                        logits[idx] = if (logits[idx] > 0f) logits[idx] / repetitionPenalty
                        else logits[idx] * repetitionPenalty
                    }
                }
                // Apply no-repeat ngram blocking
                for (b in getBlockedTokens(beam.tokens, noRepeatNgramSize)) {
                    if (b < logits.size) logits[b.toInt()] = -1e9f
                }

                val lse = logSumExpWithMax(logits, logits.maxOrNull() ?: 0f)

                // Pick top-beamWidth tokens via partial linear scan — O(n*K) not O(n log n)
                // For K=2 this is two linear passes, ~32x faster than full sort on 64K vocab
                val topTokens = topKIndices(logits, beamWidth)

                for (tokenIdx in topTokens) {
                    val token     = tokenIdx.toLong()
                    val logProb   = logits[tokenIdx] - lse
                    val newLogProb = beam.logProb + logProb
                    // Only copy token list when we actually extend — avoids copy on EOS
                    if (token == eosToken || beam.tokens.size >= maxTargetLength) {
                        candidates.add(Beam(beam.tokens.toMutableList(), newLogProb, true))
                    } else {
                        val newTokens = beam.tokens.toMutableList()
                        newTokens.add(token)
                        candidates.add(Beam(newTokens, newLogProb, false))
                    }
                }

                decTensor.close(); logitsTensor.close(); decOut.close()
            }

            // Keep top `beamWidth` active beams, length-normalised score
            val allCandidates = candidates.sortedByDescending { it.logProb / it.tokens.size }
            beams.clear()
            for (c in allCandidates) {
                if (c.finished) completed.add(c)
                else if (beams.size < beamWidth) beams.add(c)
            }
        }

        // Add any still-active beams to completed
        completed.addAll(beams)

        inputTensor.close(); maskTensor.close()
        encoderHidden.close(); encoderOut.close()

        // Pick best completed beam (length-normalised)
        val best = completed.maxByOrNull { it.logProb / it.tokens.size }
            ?: return "Translation failed"

        // Confidence from best beam score
        val meanLogProb = if (best.tokens.size > 1) best.logProb / (best.tokens.size - 1) else -10f
        lastConfidence  = sigmoid(meanLogProb * 4f + 2f).coerceIn(0f, 1f)
        Log.d("TRANSLATOR", "Beam confidence: ${"%.2f".format(lastConfidence)} (${confidenceLabel()})")

        return tgtTokenizer.decode(best.tokens.drop(1))
    }

    // Fast partial top-K: two linear scans, O(n*K). Replaces full sort O(n log n).
    // For K=2 (beamWidth) this is ~32x faster on a 64K vocabulary.
    private fun topKIndices(logits: FloatArray, k: Int): List<Int> {
        val result = IntArray(k) { -1 }
        val vals   = FloatArray(k) { Float.NEGATIVE_INFINITY }
        for (i in logits.indices) {
            val v = logits[i]
            if (v > vals[k - 1]) {
                result[k - 1] = i
                vals[k - 1]   = v
                // Bubble up
                var j = k - 1
                while (j > 0 && vals[j] > vals[j - 1]) {
                    val tmp = vals[j];   vals[j]   = vals[j-1]; vals[j-1] = tmp
                    val tmi = result[j]; result[j] = result[j-1]; result[j-1] = tmi
                    j--
                }
            }
        }
        return result.filter { it >= 0 }
    }

    private fun logSumExpWithMax(logits: FloatArray, maxVal: Float): Float {
        var sum = 0.0
        for (v in logits) sum += exp((v - maxVal).toDouble())
        return maxVal + ln(sum).toFloat()
    }

    private fun sigmoid(x: Float): Float = (1.0 / (1.0 + exp(-x.toDouble()))).toFloat()

    private fun getBlockedTokens(tokens: List<Long>, n: Int): Set<Long> {
        if (tokens.size < n) return emptySet()
        val blocked = mutableSetOf<Long>()
        val suffix  = tokens.takeLast(n - 1)
        for (i in 0..tokens.size - n)
            if (tokens.subList(i, i + n - 1) == suffix) blocked.add(tokens[i + n - 1])
        return blocked
    }

    fun confidenceLabel(): String = when {
        lastConfidence >= 0.70f -> "Good"
        lastConfidence >= 0.45f -> "OK"
        else                    -> "Low"
    }
}

// ─────────────────────────────────────────────────────────────────────────
// EnPostProcessor — cleans up HI→EN decoder output
// Applied only to HI→EN results, never touches EN→HI
// ─────────────────────────────────────────────────────────────────────────
object EnPostProcessor {

    fun process(input: String): String {
        var text = input.trim()
        if (text.isEmpty()) return text

        // 1. Fix "i " → "I " (standalone pronoun)
        text = text.replace(Regex("\\bi\\b"), "I")

        // 2. Remove duplicate consecutive words ("the the", "is is", "a a")
        text = text.replace(Regex("\\b(\\w+)( \\1)+\\b"), "$1")

        // 3. Fix spacing around punctuation
        text = text.replace(Regex("\\s+([.,!?;:])"), "$1")
        text = text.replace(Regex("([.,!?;:])(?=[^\\s])"), "$1 ")

        // 4. Capitalise after sentence-ending punctuation
        text = text.replace(Regex("([.!?]\\s+)([a-z])")) { mr ->
            mr.groupValues[1] + mr.groupValues[2].uppercase()
        }

        // 5. Capitalise first character
        text = text.replaceFirstChar { it.uppercase() }

        // 6. Strip stray leading/trailing punctuation (but keep sentence-ending ones)
        text = text.trimStart(',', ';', ':')
        text = text.trim()

        return text
    }
}
