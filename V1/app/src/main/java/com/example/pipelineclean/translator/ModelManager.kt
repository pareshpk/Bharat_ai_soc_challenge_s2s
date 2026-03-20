package com.example.pipelineclean.translator

import android.content.Context
import ai.onnxruntime.*
import java.io.File
import java.io.FileOutputStream

class ModelManager(private val context: Context) {

    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private var encoderSession: OrtSession? = null
    private var decoderSession: OrtSession? = null

    fun loadModels() {
        if (encoderSession != null) return

        val encoderPath = copyAsset("encoder_model_int8.onnx")
        val decoderPath = copyAsset("decoder_model_int8.onnx")

        encoderSession = env.createSession(encoderPath, OrtSession.SessionOptions())
        decoderSession = env.createSession(decoderPath, OrtSession.SessionOptions())
    }

    fun getEncoder(): OrtSession = encoderSession!!
    fun getDecoder(): OrtSession = decoderSession!!
    fun getEnv(): OrtEnvironment = env

    fun close() {
        encoderSession?.close()
        decoderSession?.close()
        env.close()
    }

    private fun copyAsset(name: String): String {
        val file = File(context.filesDir, name)
        if (!file.exists()) {
            context.assets.open(name).use { input ->
                FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
            }
        }
        return file.absolutePath
    }
}
