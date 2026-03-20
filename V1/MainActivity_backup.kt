package com.example.pipelineclean

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import ai.onnxruntime.*
import java.io.File
import java.io.FileOutputStream
import java.nio.FloatBuffer

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val statusText = TextView(this)
        setContentView(statusText)

        try {
            val env = OrtEnvironment.getEnvironment()
            val modelPath = copyAssetToInternalStorage("test_model.onnx")
            val session = env.createSession(modelPath, OrtSession.SessionOptions())

            val inputData = floatArrayOf(
                1f, 2f,
                3f, 4f,
                5f, 6f
            )

            val inputShape = longArrayOf(3, 2)

            val inputTensor = OnnxTensor.createTensor(
                env,
                FloatBuffer.wrap(inputData),
                inputShape
            )

            val results = session.run(inputMap)

            val labelValue = results["label"]
            val probValue = results["probabilities"]

            val labelTensor = labelValue as ai.onnxruntime.OnnxTensor
            val probTensor = probValue as ai.onnxruntime.OnnxTensor

            val labels = labelTensor.value as Array<LongArray>
            val probabilities = probTensor.value as Array<FloatArray>

            statusText.text = """
Prediction: ${labels[0][0]}
Probabilities: ${probabilities[0].joinToString()}
""".trimIndent()

            labelTensor.close()
            probTensor.close()
            results.close()
            session.close()
            env.close()

        } catch (e: Exception) {
            statusText.text = "Runtime Error:\n${e.message}"
        }
    }

    private fun copyAssetToInternalStorage(assetName: String): String {
        val file = File(filesDir, assetName)

        if (!file.exists()) {
            assets.open(assetName).use { input ->
                FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
            }
        }

        return file.absolutePath
    }
}
