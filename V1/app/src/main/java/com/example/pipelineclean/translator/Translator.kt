package com.example.pipelineclean.translator

import ai.onnxruntime.*
import android.content.Context
import org.json.JSONObject
import java.nio.LongBuffer
import java.text.Normalizer
import kotlin.math.abs
import kotlin.math.min

class Translator(private val modelManager: ModelManager) {

    private val eosToken = 0L
    private val padToken = 61949L
    private val maxLength = 40
    private val minLength = 4

    private val temperature = 0.85f
    private val repetitionPenalty = 0.4f
    private val earlyEosPenalty = 3.0f
    private val rerankThreshold = 0.05f

    private lateinit var tokenToId: Map<String, Int>
    private lateinit var idToToken: Map<Int, String>

    // =============================
    // Load vocab.json
    // =============================

    fun loadVocab(context: Context) {

        val json = context.assets.open("vocab.json")
            .bufferedReader()
            .use { it.readText() }

        val obj = JSONObject(json)

        val forward = mutableMapOf<String, Int>()
        val reverse = mutableMapOf<Int, String>()

        val keys = obj.keys()
        while (keys.hasNext()) {
            val token = keys.next()
            val id = obj.getInt(token)
            forward[token] = id
            reverse[id] = token
        }

        tokenToId = forward
        idToToken = reverse
    }

    // =============================
    // Optimized Tokenizer
    // =============================

    fun tokenize(text: String): LongArray {

        val normalized =
            Normalizer.normalize(text.lowercase(), Normalizer.Form.NFKC)

        val words = normalized.trim().split("\\s+".toRegex())

        val ids = mutableListOf<Long>()

        for (word in words) {

            var remaining = word
            var first = true

            while (remaining.isNotEmpty()) {

                var matched: String? = null

                for (i in remaining.length downTo 1) {

                    val piece = remaining.substring(0, i)
                    val candidate =
                        if (first) "▁$piece" else piece

                    if (tokenToId.containsKey(candidate)) {
                        matched = candidate
                        break
                    }
                }

                if (matched == null) {
                    ids.add(eosToken)
                    break
                }

                ids.add(tokenToId[matched]!!.toLong())

                remaining =
                    remaining.removePrefix(matched.removePrefix("▁"))

                first = false
            }
        }

        ids.add(eosToken)
        return ids.toLongArray()
    }

    // =============================
    // Decode
    // =============================

    fun decode(ids: List<Long>): String {

        val tokens = ids
            .filter { it != eosToken && it != padToken }
            .mapNotNull { idToToken[it.toInt()] }

        return tokens.joinToString("")
            .replace("▁", " ")
            .trim()
    }

    // =============================
    // Enhanced Fast Greedy Decoder
    // =============================

    fun translate(inputIds: LongArray, attentionMask: LongArray): List<Long> {

        val env = modelManager.getEnv()
        val encoder = modelManager.getEncoder()
        val decoder = modelManager.getDecoder()

        val inputShape = longArrayOf(1, inputIds.size.toLong())

        val inputTensor =
            OnnxTensor.createTensor(env, LongBuffer.wrap(inputIds), inputShape)
        val maskTensor =
            OnnxTensor.createTensor(env, LongBuffer.wrap(attentionMask), inputShape)

        val encoderOutputs = encoder.run(
            mapOf(
                "input_ids" to inputTensor,
                "attention_mask" to maskTensor
            )
        )

        val encoderHiddenStates = encoderOutputs[0] as OnnxTensor

        val generated = mutableListOf<Long>()
        generated.add(eosToken)

        for (step in 0 until maxLength) {

            val decoderInput = generated.toLongArray()
            val decoderShape =
                longArrayOf(1, decoderInput.size.toLong())

            val decoderInputTensor =
                OnnxTensor.createTensor(
                    env,
                    LongBuffer.wrap(decoderInput),
                    decoderShape
                )

            val decoderOutputs = decoder.run(
                mapOf(
                    "input_ids" to decoderInputTensor,
                    "encoder_hidden_states" to encoderHiddenStates,
                    "encoder_attention_mask" to maskTensor
                )
            )

            val logitsTensor = decoderOutputs[0] as OnnxTensor
            val logits =
                logitsTensor.value as Array<Array<FloatArray>>

            val lastIndex =
                min(generated.size - 1, logits[0].size - 1)

            val lastLogits = logits[0][lastIndex]

            var bestIndex = 0
            var bestScore = Float.NEGATIVE_INFINITY

            var secondIndex = 0
            var secondScore = Float.NEGATIVE_INFINITY

            for (i in lastLogits.indices) {

                var score = lastLogits[i] / temperature

                // discourage early EOS
                if (generated.size < minLength &&
                    i.toLong() == eosToken
                ) {
                    score -= earlyEosPenalty
                }

                // light repetition penalty
                if (generated.contains(i.toLong())) {
                    score -= repetitionPenalty
                }

                // immediate repetition stronger block
                if (generated.isNotEmpty() &&
                    i.toLong() == generated.last()
                ) {
                    score -= 1.5f
                }

                if (score > bestScore) {
                    secondScore = bestScore
                    secondIndex = bestIndex

                    bestScore = score
                    bestIndex = i
                } else if (score > secondScore) {
                    secondScore = score
                    secondIndex = i
                }
            }

            val chosenToken =
                if (abs(bestScore - secondScore) < rerankThreshold &&
                    !generated.contains(secondIndex.toLong())
                ) {
                    secondIndex.toLong()
                } else {
                    bestIndex.toLong()
                }

            if (chosenToken == eosToken) break

            generated.add(chosenToken)

            decoderInputTensor.close()
            logitsTensor.close()
            decoderOutputs.close()
        }

        inputTensor.close()
        maskTensor.close()
        encoderHiddenStates.close()
        encoderOutputs.close()

        return generated
    }
}
