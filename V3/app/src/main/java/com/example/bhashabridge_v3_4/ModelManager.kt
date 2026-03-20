package com.example.bhashabridge_v3_4

import ai.onnxruntime.*
import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors

// ─────────────────────────────────────────────────────────────────────────────
// V3 ModelManager — direction-aware
//
// EN→HI : encoder_model_int8.onnx  + decoder_model_int8.onnx   (V2 assets)
// HI→EN : hi_en_encoder_int8.onnx  + hi_en_decoder_int8.onnx   (V3 assets)
//
// Session options identical to V2 (DO NOT CHANGE):
//   intraOpThreads = 4
//   interOpThreads = 2
//   optimization   = ALL_OPT
// ─────────────────────────────────────────────────────────────────────────────
class ModelManager(
    private val context: Context,
    private val direction: TranslationDirection = TranslationDirection.EN_TO_HI
) {

    val env: OrtEnvironment = OrtEnvironment.getEnvironment()

    private var _encoderSession: OrtSession? = null
    private var _decoderSession: OrtSession? = null

    private val encoderAsset = when (direction) {
        TranslationDirection.EN_TO_HI -> "encoder_model_int8.onnx"
        TranslationDirection.HI_TO_EN -> "hi_en_encoder_int8.onnx"
    }
    private val decoderAsset = when (direction) {
        TranslationDirection.EN_TO_HI -> "decoder_model_int8.onnx"
        TranslationDirection.HI_TO_EN -> "hi_en_decoder_int8.onnx"
    }

    // Encoder and decoder load in parallel — cuts init time ~50% (V2 behaviour)
    init {
        val ex = Executors.newFixedThreadPool(2)
        val encFuture = ex.submit<OrtSession> { createSession(encoderAsset) }
        val decFuture = ex.submit<OrtSession> { createSession(decoderAsset) }
        _encoderSession = encFuture.get()
        _decoderSession = decFuture.get()
        ex.shutdown()
    }

    val encoderSession: OrtSession get() = _encoderSession!!
    val decoderSession: OrtSession get() = _decoderSession!!

    private fun createSession(name: String): OrtSession {
        val opts = OrtSession.SessionOptions().apply {
            setIntraOpNumThreads(4)
            setInterOpNumThreads(2)
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
        }
        return env.createSession(copyAsset(name), opts)
    }

    private fun copyAsset(name: String): String {
        val file = File(context.filesDir, name)
        if (!file.exists()) {
            // 256KB buffer — 32x fewer I/O calls for large ONNX files (V2 optimisation)
            val buf = ByteArray(262144)
            context.assets.open(name).use { input ->
                FileOutputStream(file).use { output ->
                    var n: Int
                    while (input.read(buf).also { n = it } != -1) output.write(buf, 0, n)
                }
            }
        }
        return file.absolutePath
    }

    fun release() {
        _encoderSession?.close()
        _decoderSession?.close()
        _encoderSession = null
        _decoderSession = null
    }
}