package com.example.bhashabridge_v3_4

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import java.io.File
import java.io.FileOutputStream

class SpeechManager(
    private val context: Context,
    private val onResult: (String) -> Unit,
    private val onError: (String) -> Unit
) {

    private var model: Model? = null
    private var recognizer: Recognizer? = null
    private var speechService: SpeechService? = null
    private var isListening = false

    private val mainHandler = Handler(Looper.getMainLooper())

    /* ------------------------------------------------------------- */
    /* MODEL INITIALIZATION */
    /* ------------------------------------------------------------- */

    fun init(onReady: () -> Unit) {
        Thread {
            try {

                val modelDir = copyModelToFiles()
                model = Model(modelDir)

                Log.d("VOSK", "Model loaded OK")

                mainHandler.post { onReady() }

            } catch (e: Exception) {

                Log.e("VOSK", "Init failed", e)

                mainHandler.post {
                    onError("ASR init failed: ${e.message}")
                }
            }
        }.start()
    }

    /* ------------------------------------------------------------- */
    /* MODEL RELOAD */
    /* ------------------------------------------------------------- */

    fun reloadModel(onReady: () -> Unit) {
        Thread {
            try {

                val modelDir = copyModelToFiles()
                model = Model(modelDir)

                Log.d("VOSK", "Model reloaded OK")

                mainHandler.post { onReady() }

            } catch (e: Exception) {

                Log.e("VOSK", "Reload failed", e)

                mainHandler.post {
                    onError("ASR reload failed: ${e.message}")
                }
            }
        }.start()
    }

    /* ------------------------------------------------------------- */
    /* MODEL FILE COPY */
    /* ------------------------------------------------------------- */

    private fun copyModelToFiles(): String {

        val dest = File(context.filesDir, "vosk-model")

        if (dest.exists() && dest.listFiles()?.isNotEmpty() == true)
            return dest.absolutePath

        dest.mkdirs()

        copyAssetFolder(context, "model", dest.absolutePath)

        return dest.absolutePath
    }

    private fun copyAssetFolder(context: Context, assetFolder: String, destFolder: String) {

        val children = context.assets.list(assetFolder) ?: return

        File(destFolder).mkdirs()

        for (child in children) {

            val srcPath = "$assetFolder/$child"
            val destPath = "$destFolder/$child"

            val subChildren = context.assets.list(srcPath)

            if (subChildren != null && subChildren.isNotEmpty()) {

                copyAssetFolder(context, srcPath, destPath)

            } else {

                context.assets.open(srcPath).use { input ->
                    FileOutputStream(destPath).use { input.copyTo(it) }
                }
            }
        }
    }

    /* ------------------------------------------------------------- */
    /* START LISTENING */
    /* ------------------------------------------------------------- */

    fun startListening() {

        val m = model ?: return

        if (isListening) return

        try {

            recognizer = Recognizer(m, 16000.0f)

            speechService = SpeechService(recognizer, 16000.0f)

            speechService?.startListening(object : RecognitionListener {

                override fun onPartialResult(hypothesis: String?) {
                    // Not used
                }

                override fun onResult(hypothesis: String?) {
                    // Not used
                }

                override fun onFinalResult(hypothesis: String?) {

                    Log.d("VOSK", "Final: $hypothesis")

                    val text = parseText(hypothesis) ?: return

                    mainHandler.post {

                        try {

                            Log.d("VOSK", "Dispatching result to UI: $text")

                            onResult(text)

                        } catch (e: Exception) {

                            Log.e("VOSK", "Callback failed", e)
                        }
                    }
                }

                override fun onError(e: Exception?) {

                    Log.e("VOSK", "Error", e)

                    mainHandler.post {
                        onError(e?.message ?: "ASR error")
                    }
                }

                override fun onTimeout() {

                    Log.d("VOSK", "Timeout")

                    stopListening()
                }
            })

            isListening = true

            Log.d("VOSK", "Listening started")

        } catch (e: Exception) {

            Log.e("VOSK", "Start failed", e)

            mainHandler.post {
                onError("Start failed: ${e.message}")
            }
        }
    }

    /* ------------------------------------------------------------- */
    /* PARSE VOSK JSON */
    /* ------------------------------------------------------------- */

    private fun parseText(hypothesis: String?): String? {

        hypothesis ?: return null

        return try {

            val text = JSONObject(hypothesis)
                .optString("text", "")
                .trim()

            if (text.isEmpty()) null else text

        } catch (_: Exception) {
            null
        }
    }

    /* ------------------------------------------------------------- */
    /* STOP LISTENING */
    /* ------------------------------------------------------------- */

    fun stopListening() {

        try {

            speechService?.stop()
            speechService?.shutdown()
            speechService = null

            recognizer?.close()
            recognizer = null

            isListening = false

            Log.d("VOSK", "Listening stopped")

        } catch (e: Exception) {

            Log.e("VOSK", "Stop error", e)
        }
    }

    fun isListening(): Boolean = isListening

    /* ------------------------------------------------------------- */
    /* DESTROY */
    /* ------------------------------------------------------------- */

    fun destroy() {

        stopListening()

        model?.close()
        model = null

        Log.d("VOSK", "Model destroyed")
    }
}