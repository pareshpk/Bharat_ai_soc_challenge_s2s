package com.example.pipelineclean

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.*
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.pipelineclean.translator.ModelManager
import com.example.pipelineclean.translator.Translator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.io.FileOutputStream
import java.util.*
import kotlin.math.abs

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var modelManager: ModelManager
    private lateinit var translator: Translator

    private var voskModel: Model? = null
    private var tts: TextToSpeech? = null

    private val RECORD_REQUEST = 100
    private val SAMPLE_RATE = 16000

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#121212"))
            setPadding(40, 60, 40, 40)
        }

        val title = TextView(this).apply {
            text = "Easy Translate"
            textSize = 24f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
        }

        val input = EditText(this).apply {
            hint = "Speak or type English..."
            setHintTextColor(Color.parseColor("#757575"))
            setTextColor(Color.WHITE)
            background = createCardBackground("#1E1E1E")
            setPadding(30, 40, 30, 40)
        }

        val micButton = Button(this).apply {
            text = "🎤 Speak"
            setTextColor(Color.WHITE)
            background = createButtonBackground("#FF5252")
        }

        val translateButton = Button(this).apply {
            text = "Translate"
            setTextColor(Color.WHITE)
            background = createButtonBackground("#00ACC1")
        }

        val output = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 18f
            background = createCardBackground("#1E1E1E")
            setPadding(30, 40, 30, 40)
        }

        root.addView(title)
        root.addView(input)
        root.addView(micButton)
        root.addView(translateButton)
        root.addView(output)

        setContentView(root)

        tts = TextToSpeech(this, this)
        modelManager = ModelManager(this)
        translator = Translator(modelManager)

        lifecycleScope.launch(Dispatchers.IO) {
            modelManager.loadModels()
            translator.loadVocab(this@MainActivity)
            setupOfflineVosk()
        }

        translateButton.setOnClickListener {
            val text = input.text.toString()
            if (text.isBlank()) return@setOnClickListener

            lifecycleScope.launch {
                output.text = "Translating..."

                val hindi = withContext(Dispatchers.IO) {
                    val corrected = applyAdaptiveCorrection(text)
                    val ids = translator.tokenize(corrected)
                    val mask = LongArray(ids.size) { 1L }
                    val tokens = translator.translate(ids, mask)
                    translator.decode(tokens)
                }

                output.text = hindi
                speakHindi(hindi)
            }
        }

        micButton.setOnClickListener {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.RECORD_AUDIO
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.RECORD_AUDIO),
                    RECORD_REQUEST
                )
            } else {
                startPushToTalk(input, output)
            }
        }
    }

    // ===========================
    // Adaptive Correction Layer
    // ===========================

    private fun applyAdaptiveCorrection(text: String): String {

        var corrected = text.lowercase(Locale.US)

        // Pronoun corrections
        corrected = corrected.replace(Regex("^he want"), "i want")
        corrected = corrected.replace(Regex("^he am"), "i am")

        // Common phonetic confusions
        corrected = corrected.replace("travel plain", "travel plan")
        corrected = corrected.replace("there plan", "their plan")
        corrected = corrected.replace("foodd", "food")

        // Fix missing auxiliary verbs
        corrected = corrected.replace(Regex("\\bi going\\b"), "i am going")
        corrected = corrected.replace(Regex("\\bi doing\\b"), "i am doing")

        return corrected
    }

    // ===========================
    // Speech Recognition
    // ===========================

    private fun startPushToTalk(inputField: EditText, outputField: TextView) {

        lifecycleScope.launch(Dispatchers.IO) {

            val recognizer = Recognizer(voskModel, SAMPLE_RATE.toFloat())

            val bufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )

            val audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize * 2
            )

            val buffer = ByteArray(bufferSize)
            audioRecord.startRecording()

            var silenceFrames = 0

            while (true) {
                val read = audioRecord.read(buffer, 0, buffer.size)
                recognizer.acceptWaveForm(buffer, read)

                var energy = 0L
                for (i in 0 until read step 2) {
                    val sample =
                        (buffer[i].toInt() or (buffer[i + 1].toInt() shl 8))
                    energy += abs(sample)
                }

                val avg = energy / (read / 2)

                if (avg < 1000) silenceFrames++ else silenceFrames = 0
                if (silenceFrames > 25) break
            }

            audioRecord.stop()
            audioRecord.release()

            val result = recognizer.finalResult
            recognizer.close()

            val spokenText = try {
                JSONObject(result).getString("text")
            } catch (e: Exception) {
                ""
            }

            val corrected = applyAdaptiveCorrection(spokenText)

            withContext(Dispatchers.Main) {
                inputField.setText(corrected)
                outputField.text = corrected
            }
        }
    }

    private fun speakHindi(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tts1")
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale("hi", "IN")
        }
    }

    private fun createCardBackground(color: String): GradientDrawable {
        return GradientDrawable().apply {
            cornerRadius = 40f
            setColor(Color.parseColor(color))
        }
    }

    private fun createButtonBackground(color: String): GradientDrawable {
        return GradientDrawable().apply {
            cornerRadius = 60f
            setColor(Color.parseColor(color))
        }
    }

    private fun setupOfflineVosk() {
        val modelPath = File(filesDir, "model")
        if (!modelPath.exists()) {
            copyAssetFolder("model", modelPath.absolutePath)
        }
        voskModel = Model(modelPath.absolutePath)
    }

    private fun copyAssetFolder(assetPath: String, destPath: String) {
        val assetManager = assets
        val files = assetManager.list(assetPath) ?: return
        val destDir = File(destPath)
        if (!destDir.exists()) destDir.mkdirs()

        for (file in files) {
            val fullAssetPath = "$assetPath/$file"
            val fullDestPath = "$destPath/$file"
            val subFiles = assetManager.list(fullAssetPath)

            if (subFiles != null && subFiles.isNotEmpty()) {
                copyAssetFolder(fullAssetPath, fullDestPath)
            } else {
                assetManager.open(fullAssetPath).use { input ->
                    FileOutputStream(fullDestPath).use { output ->
                        input.copyTo(output)
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        voskModel?.close()
        tts?.shutdown()
        modelManager.close()
    }
}
