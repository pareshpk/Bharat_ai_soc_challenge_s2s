package com.example.bhashabridge_v3_4

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.*
import android.speech.tts.TextToSpeech
import android.text.Editable
import android.text.SpannableString
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.view.MotionEvent
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.util.*
import java.util.concurrent.Executors
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    companion object {
        private const val TAG            = "MAIN"
        private const val SAMPLE_RATE    = 16000
        private const val MIC_PERMISSION = 1001
        private const val SETUP_REQUEST    = 2001
        private const val AUDIO_PICK_REQUEST = 3001

        // ── Feature 7: Streaming translation tuning constants ──────────────
        private const val STREAM_MIN_WORDS   = 3
        private const val STREAM_THROTTLE_MS = 250L
    }

    // ── Feature 3: language preference ────────────────────────────────────
    private lateinit var prefs: SharedPreferences
    private var uiLanguage: String = "en"  // "en" or "hi"

    private lateinit var inputText:      EditText
    private lateinit var outputText:     TextView
    private lateinit var micButton:      ImageButton
    private lateinit var micStatus:      TextView
    private lateinit var waveformView:   WaveformView
    private lateinit var micPulse:       android.view.View
    private lateinit var swapBtn:        ImageButton
    private lateinit var langSrc:        TextView
    private lateinit var langTgt:        TextView
    private lateinit var labelInput:     TextView
    private lateinit var labelOutput:    TextView
    private lateinit var loadingOverlay: FrameLayout
    private lateinit var loadingStatus:  TextView
    // ── Feature 4: tricolour title TextViews ──────────────────────────────
    private var appTitle: android.widget.ImageView? = null
    private var loadingTitle: android.widget.ImageView? = null

    private lateinit var asrHintText:         TextView
    private lateinit var emergencyOverlay:    FrameLayout
    private lateinit var selectedPhrasePanel: android.view.View
    private lateinit var selectedEnglish:     TextView
    private lateinit var selectedHindi:       TextView
    private lateinit var phraseList:          RecyclerView
    private var currentCategory = EmergencyPhrases.Category.BASIC

    private lateinit var tts: TextToSpeech
    private var ttsReady = false
    private var hindiTtsAvailable = false

    private var currentDirection = TranslationDirection.EN_TO_HI

    private var modelEn:    Model? = null
    private var modelHi:    Model? = null
    private var recognizer: Recognizer? = null

    private var voskEnReady         = false
    private var voskHiReady         = false
    private var translatorEnHiReady = false
    private var translatorHiEnReady = false
    private var pendingDirection: TranslationDirection? = null

    private var translatorEnHi: Translator? = null
    private var translatorHiEn: Translator? = null

    private var audioRecord:     AudioRecord? = null
    private var isRecording      = false
    private var noiseSuppressor: NoiseSuppressor? = null
    private var echoCanceler:    AcousticEchoCanceler? = null
    private var gainControl:     AutomaticGainControl? = null
    @Volatile private var lastMidResult:    String = ""
    @Volatile private var audioLoopRunning = false
    @Volatile private var lastRawAsrText:   String = ""  // raw before correction

    // ── Feature 7: Streaming translation state ────────────────────────────
    @Volatile private var lastStreamedPartial: String  = ""
    @Volatile private var lastStreamTime:      Long    = 0L
    @Volatile private var isFinalPending:      Boolean = false

    // ── Upgrade 4: Translation history — last 5 pairs ───────────────────
    private val translationHistory = ArrayDeque<Pair<String, String>>(5)

    private val audioExecutor     = Executors.newSingleThreadExecutor()
    private val modelExecutor     = Executors.newSingleThreadExecutor()
    private val translateExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    // ── Feature 1: debounce runnable for typed input correction ──────────
    private var correctionRunnable: Runnable? = null
    private val CORRECTION_DEBOUNCE_MS = 1500L

    override fun onCreateOptionsMenu(menu: android.view.Menu): Boolean {
        menu.add(0, 1, 0, if (uiLanguage == "hi") "ऑडियो फ़ाइल आयात करें" else "Import Audio File")
            .setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_NEVER)
        return true
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        if (item.itemId == 1) { openAudioFilePicker(); return true }
        return super.onOptionsItemSelected(item)
    }

    private fun openAudioFilePicker() {
        val intent = android.content.Intent(android.content.Intent.ACTION_GET_CONTENT).apply {
            type = "audio/*"
            addCategory(android.content.Intent.CATEGORY_OPENABLE)
            putExtra(android.content.Intent.EXTRA_MIME_TYPES,
                arrayOf("audio/mpeg", "audio/mp4", "audio/ogg", "audio/wav",
                        "audio/x-wav", "audio/aac", "audio/3gpp"))
        }
        startActivityForResult(
            android.content.Intent.createChooser(intent,
                if (uiLanguage == "hi") "ऑडियो फ़ाइल चुनें" else "Select Audio File"),
            AUDIO_PICK_REQUEST)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ── Feature 3: check language preference before showing UI ────────
        prefs = getSharedPreferences("bb_prefs", MODE_PRIVATE)
        uiLanguage = prefs.getString("ui_language", null) ?: run {
            // First launch — show onboarding then setup
            val seenOnboarding = prefs.getBoolean("seen_onboarding", false)
            if (!seenOnboarding) {
                startActivityForResult(
                    Intent(this, OnboardingActivity::class.java),
                    SETUP_REQUEST
                )
            } else {
                startActivityForResult(
                    Intent(this, SetupActivity::class.java),
                    SETUP_REQUEST
                )
            }
            return
        }

        initMainUI()
    }

    // ── Feature 3: receive language choice from SetupActivity ─────────────
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == SETUP_REQUEST) {
            uiLanguage = prefs.getString("ui_language", "en") ?: "en"
            initMainUI(); return
        }
        if (requestCode == AUDIO_PICK_REQUEST && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            processAudioFile(uri)
        }
    }

    private fun processAudioFile(uri: android.net.Uri) {
        val m = modelEn ?: run {
            micStatus.text = if (uiLanguage == "hi") "ASR मॉडल तैयार नहीं" else "ASR model not ready"
            return
        }
        micStatus.text = if (uiLanguage == "hi") "ऑडियो प्रोसेस हो रहा है…" else "Processing audio file..."
        outputText.text = if (uiLanguage == "hi") "ऑडियो पढ़ा जा रहा है…" else "Reading audio..."
        outputText.setTextColor(android.graphics.Color.parseColor("#4A7A6A"))
        asrHintText.visibility = android.view.View.GONE
        val snap = currentDirection
        audioExecutor.execute {
            try {
                val transcript = AudioFileTranslator.transcribeFile(this, uri, m) { partial ->
                    mainHandler.post {
                        inputText.setText(partial)
                        micStatus.text = if (uiLanguage == "hi") "पहचाना जा रहा है…" else "Recognising..."
                    }
                }
                if (transcript.isBlank()) {
                    mainHandler.post {
                        micStatus.text = if (uiLanguage == "hi") "कोई वाणी नहीं मिली" else "No speech detected"
                        outputText.text = if (uiLanguage == "hi") "अनुवाद यहाँ दिखेगा…" else "Translation appears here..."
                        outputText.setTextColor(android.graphics.Color.parseColor("#2D4A3E"))
                    }
                    return@execute
                }
                val corrected = when (snap) {
                    TranslationDirection.EN_TO_HI -> ASRCorrector.correct(transcript)
                    TranslationDirection.HI_TO_EN -> ASRCorrector.correctHindi(transcript)
                }
                mainHandler.post {
                    inputText.setText(corrected)
                    micStatus.text = if (uiLanguage == "hi") "अनुवाद हो रहा है…" else "Translating..."
                    outputText.text = if (uiLanguage == "hi") "अनुवाद हो रहा है…" else "Translating..."
                }
                translateExecutor.execute { runTranslation(corrected, snap, isFinal = true) }
            } catch (e: Exception) {
                Log.e(TAG, "Audio file processing failed", e)
                mainHandler.post {
                    micStatus.text = if (uiLanguage == "hi") "ऑडियो त्रुटि" else "Audio processing failed"
                    outputText.text = "Could not read audio file"
                    outputText.setTextColor(android.graphics.Color.parseColor("#2D4A3E"))
                }
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initMainUI() {
        setContentView(R.layout.activity_main)

        inputText      = findViewById(R.id.inputText)
        outputText     = findViewById(R.id.outputText)
        micButton      = findViewById(R.id.micButton)
        micStatus      = findViewById(R.id.micStatus)
        waveformView   = findViewById(R.id.waveformView)
        micPulse       = findViewById(R.id.micPulse)
        swapBtn        = findViewById(R.id.swapBtn)
        langSrc        = findViewById(R.id.langSrc)
        langTgt        = findViewById(R.id.langTgt)
        labelInput     = findViewById(R.id.labelEnglish)
        labelOutput    = findViewById(R.id.labelHindi)
        loadingOverlay = findViewById(R.id.loadingOverlay)
        loadingStatus  = findViewById(R.id.loadingStatus)
        // ── Feature 4: bind title views ───────────────────────────────────
        appTitle = findViewById<android.widget.ImageView>(R.id.appTitle)
        loadingTitle = findViewById<android.widget.ImageView>(R.id.loadingTitle)
        asrHintText         = findViewById(R.id.asrHintText)
        emergencyOverlay    = findViewById(R.id.emergencyOverlay)
        selectedPhrasePanel = findViewById(R.id.selectedPhrasePanel)
        selectedEnglish     = findViewById(R.id.selectedEnglish)
        selectedHindi       = findViewById(R.id.selectedHindi)
        phraseList          = findViewById(R.id.phraseList)

        // ── Feature 4: apply tricolour Indian-flag style to both titles ───
        // appTitle is ImageView
        // loadingTitle is ImageView

        tts = TextToSpeech(this, this)
        micStatus.text      = "Tap mic to speak"
        micButton.isEnabled = false
        swapBtn.isEnabled   = false

        animateLoadingDots()
        loadVoskEnglish()

        translateExecutor.execute {
            try {
                val t = Translator(this, TranslationDirection.EN_TO_HI)
                t.warmUp()
                translatorEnHi = t
                Log.d(TAG, "EN→HI ready")
                mainHandler.post {
                    translatorEnHiReady = true
                    swapBtn.isEnabled   = true
                    loadingStatus.text = if (!voskEnReady)
                        (if (uiLanguage == "hi") "वाक् मॉडल लोड हो रहा है…" else "Loading speech model...")
                    else (if (uiLanguage == "hi") "तैयार!" else "Ready!")
                    checkAllReady()
                }
            } catch (e: Exception) {
                Log.e(TAG, "EN→HI failed", e)
                mainHandler.post { translatorEnHiReady = true; checkAllReady() }
            }
        }

        findViewById<Button>(R.id.translateButton).setOnClickListener {
            val typed = inputText.text.toString().trim()
            if (typed.isBlank()) return@setOnClickListener
            val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.hideSoftInputFromWindow(inputText.windowToken, 0)
            val direction = currentDirection
            micStatus.text = "Translating..."; outputText.text = "Translating..."
            translateExecutor.execute { runTranslation(typed, direction, isFinal = true) }
        }

        // ── Feature 1: TextWatcher — correct typed input after 600ms idle ─
        inputText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val raw = s?.toString() ?: return
                if (raw.isBlank()) return
                // Cancel any pending correction
                correctionRunnable?.let { mainHandler.removeCallbacks(it) }
                // Schedule new correction after debounce period
                correctionRunnable = Runnable {
                    if (raw.trim().length < 3) return@Runnable
                    val corrected = when (currentDirection) {
                        TranslationDirection.EN_TO_HI -> ASRCorrector.correct(raw)
                        TranslationDirection.HI_TO_EN -> ASRCorrector.correctHindi(raw)
                    }
                    if (corrected != raw && corrected.isNotBlank()) {
                        inputText.removeTextChangedListener(this)
                        inputText.setText(corrected)
                        inputText.setSelection(corrected.length)
                        inputText.addTextChangedListener(this)
                    }
                }
                mainHandler.postDelayed(correctionRunnable!!, CORRECTION_DEBOUNCE_MS)
            }
        })

        swapBtn.setOnClickListener {
            val newDir = if (currentDirection == TranslationDirection.EN_TO_HI)
                TranslationDirection.HI_TO_EN else TranslationDirection.EN_TO_HI
            if (newDir == TranslationDirection.HI_TO_EN && !translatorHiEnReady) {
                // Show loading overlay — keep current direction until models ready
                loadingStatus.text = if (uiLanguage == "hi")
                    "हिंदी मॉडल लोड हो रहा है…"
                else
                    "Loading Hindi model..."
                loadingOverlay.alpha = 1f
                loadingOverlay.visibility = android.view.View.VISIBLE
                swapBtn.isEnabled = false
                pendingDirection = newDir
                loadHiEnTranslatorAndVosk()
                return@setOnClickListener
            }
            currentDirection = newDir
            rebuildRecognizer()
            updateLangUI()
            inputText.setText("")
            outputText.text = "Translation appears here..."
            outputText.setTextColor(android.graphics.Color.parseColor("#2D4A3E"))
        }

        // ── Upgrade 2: Tap-to-toggle mic ─────────────────────────────────
        micButton.setOnClickListener {
            if (isRecording) {
                stopRecordingAndFlush()
            } else {
                if (checkMicPermission()) startRecording()
            }
        }

        findViewById<Button>(R.id.emergencyButton).setOnClickListener { openEmergencyMode() }
        findViewById<Button>(R.id.emergencyClose).setOnClickListener { emergencyOverlay.visibility = android.view.View.GONE }
        findViewById<Button>(R.id.speakPhraseBtn).setOnClickListener {
            speakOutput(selectedHindi.text.toString(), TranslationDirection.EN_TO_HI)
        }

        val tabMap = mapOf(
            R.id.tabBasic to EmergencyPhrases.Category.BASIC,
            R.id.tabMedical to EmergencyPhrases.Category.MEDICAL,
            R.id.tabSafety to EmergencyPhrases.Category.SAFETY,
            R.id.tabLocation to EmergencyPhrases.Category.LOCATION
        )
        tabMap.forEach { (id, cat) ->
            findViewById<TextView>(id).setOnClickListener {
                currentCategory = cat; updateCategoryTabs(tabMap); loadPhrases(cat)
            }
        }

        // ── Menu button — opens bottom tray ──────────────────────────────
        findViewById<Button>(R.id.menuBtn).setOnClickListener {
            showMenuTray()
        }

        // Apply UI language strings on startup
        applyUiLanguage()
        setupDrawer()
    }

    // ── Feature 3: apply ALL UI strings based on saved language pref ─────
    // Called once on startup and again when user changes language in dialog
    private fun applyUiLanguage() {
        val h = uiLanguage == "hi"

        // ── App title: Hindi = "भाषासेतु V3.4", English = tricolour "BhashaBridge V3.4"
        if (h) {
            val hindiTitle = android.text.SpannableString("भाषासेतु V3.4")
            hindiTitle.setSpan(
                android.text.style.ForegroundColorSpan(android.graphics.Color.parseColor("#FF9933")),
                0, 4, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)   // "भाषा"
            hindiTitle.setSpan(
                android.text.style.ForegroundColorSpan(android.graphics.Color.parseColor("#138808")),
                4, 8, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)   // "सेतु"
            hindiTitle.setSpan(
                android.text.style.ForegroundColorSpan(android.graphics.Color.parseColor("#FFFFFF")),
                8, hindiTitle.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) // " V3"
        // [logo is ImageView] appTitle.text = hindiTitle
        } else {
        // appTitle is ImageView
        }

        // ── Lang bar (English ⇄ Hindi labels) ────────────────────────────
        // These are reset by updateLangUI() but seed them correctly here too
        if (currentDirection == TranslationDirection.EN_TO_HI) {
            langSrc.text = if (h) "अंग्रेज़ी" else "English"
            langTgt.text = if (h) "हिन्दी"   else "Hindi"
            labelInput.text  = if (h) "अंग्रेज़ी" else "ENGLISH"
            labelOutput.text = if (h) "हिन्दी"   else "HINDI"
            inputText.hint   = if (h) "अंग्रेज़ी में टाइप करें या बोलें…" else "Type or speak in English..."
        } else {
            langSrc.text = if (h) "हिन्दी"   else "Hindi"
            langTgt.text = if (h) "अंग्रेज़ी" else "English"
            labelInput.text  = if (h) "हिन्दी"   else "HINDI"
            labelOutput.text = if (h) "अंग्रेज़ी" else "ENGLISH"
            inputText.hint   = "हिंदी में बोलें या टाइप करें..."
        }

        // ── Output box ───────────────────────────────────────────────────
        outputText.text = if (h) "अनुवाद यहाँ दिखेगा…" else "Translation appears here..."
        outputText.setTextColor(android.graphics.Color.parseColor("#2D4A3E"))

        // ── Mic status ───────────────────────────────────────────────────
        micStatus.text = when {
            currentDirection == TranslationDirection.HI_TO_EN && voskHiReady && h ->
                "हिंदी में माइक टैप करें"
            currentDirection == TranslationDirection.HI_TO_EN && voskHiReady ->
                "Tap mic to speak in Hindi"
            currentDirection == TranslationDirection.HI_TO_EN && h ->
                "ऊपर हिंदी टाइप करें, फिर अनुवाद करें"
            currentDirection == TranslationDirection.HI_TO_EN ->
                "Type Hindi above, then tap Translate"
            h    -> "माइक टैप करें"
            else -> "Tap mic to speak"
        }

        // ── Translate button ─────────────────────────────────────────────
        findViewById<Button>(R.id.translateButton).text =
            if (h) "अनुवाद करें" else "Translate"

        // ── Speech mode label ────────────────────────────────────────────
        findViewById<TextView>(R.id.speechModeLabel).text =
            if (h) "वाक् मोड" else "SPEECH MODE"

        // ── Emergency button ─────────────────────────────────────────────
        findViewById<Button>(R.id.emergencyButton).text =
            if (h) "🆘  आपातकालीन वाक्य" else "🆘  EMERGENCY PHRASES"

        // ── Menu button ───────────────────────────────────────────────────
        findViewById<Button>(R.id.menuBtn).text =
            if (h) "☰  मेनू" else "☰  MENU"

        // ── Loading screen ───────────────────────────────────────────────
        if (h) {
            val hindiLoadTitle = android.text.SpannableString("भाषासेतु V3.4")
            hindiLoadTitle.setSpan(
                android.text.style.ForegroundColorSpan(android.graphics.Color.parseColor("#FF9933")),
                0, 4, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            hindiLoadTitle.setSpan(
                android.text.style.ForegroundColorSpan(android.graphics.Color.parseColor("#138808")),
                4, 8, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            hindiLoadTitle.setSpan(
                android.text.style.ForegroundColorSpan(android.graphics.Color.parseColor("#FFFFFF")),
                8, hindiLoadTitle.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        // loadingTitle is ImageView — no text
        } else {
        // loadingTitle is ImageView
        }
        // Loading subtitle (the "Offline Speech Translation" line — no id, skip)
        // loadingStatus is updated dynamically during load, seed it here
        if (loadingOverlay.visibility == android.view.View.VISIBLE) {
            loadingStatus.text = if (h) "मॉडल लोड हो रहे हैं…" else "Initialising models..."
        }
    }

    // ── Feature 3: language change dialog from main screen ───────────────
    private fun showLanguageDialog() {
        val isHindi = uiLanguage == "hi"
        AlertDialog.Builder(this)
            .setTitle(if (isHindi) "ऐप भाषा" else "App Language")
            .setMessage(if (isHindi) "इंटरफेस भाषा चुनें" else "Choose interface language")
            .setPositiveButton(if (isHindi) "English" else "English") { _, _ ->
                uiLanguage = "en"
                prefs.edit().putString("ui_language", "en").apply()
                applyUiLanguage()
                updateLangUI()
            }
            .setNegativeButton("हिंदी") { _, _ ->
                uiLanguage = "hi"
                prefs.edit().putString("ui_language", "hi").apply()
                applyUiLanguage()
                updateLangUI()
            }
            .setNeutralButton(if (isHindi) "रद्द करें" else "Cancel", null)
            .show()
    }

    // ─────────────────────────────────────────────────────────────────────
    // Feature 4: Tricolour title helper
    // "Bhasha" → saffron (#FF9933), "Bridge" → India green (#138808), " V3" → white
    // ─────────────────────────────────────────────────────────────────────
    private fun applyTricolourTitle(tv: TextView) {
        val full = "BhashaBridge V3.4"
        val span = SpannableString(full)
        span.setSpan(
            ForegroundColorSpan(android.graphics.Color.parseColor("#FF9933")),
            0, 6,   // "Bhasha"
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        span.setSpan(
            ForegroundColorSpan(android.graphics.Color.parseColor("#138808")),
            6, 12,  // "Bridge"
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        span.setSpan(
            ForegroundColorSpan(android.graphics.Color.parseColor("#FFFFFF")),
            12, full.length, // " V3.4"
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        tv.text = span
    }

    private fun openEmergencyMode() {
        emergencyOverlay.visibility    = android.view.View.VISIBLE
        selectedPhrasePanel.visibility = android.view.View.GONE
        currentCategory = EmergencyPhrases.Category.BASIC
        val tabMap = mapOf(
            R.id.tabBasic to EmergencyPhrases.Category.BASIC,
            R.id.tabMedical to EmergencyPhrases.Category.MEDICAL,
            R.id.tabSafety to EmergencyPhrases.Category.SAFETY,
            R.id.tabLocation to EmergencyPhrases.Category.LOCATION)
        updateCategoryTabs(tabMap)
        loadPhrases(EmergencyPhrases.Category.BASIC)
    }

    private fun updateCategoryTabs(tabs: Map<Int, EmergencyPhrases.Category>) {
        tabs.forEach { (id, cat) ->
            val tab = findViewById<TextView>(id)
            if (cat == currentCategory) {
                tab.setTextColor(android.graphics.Color.parseColor("#FF6666"))
                tab.setBackgroundResource(R.drawable.bg_tab_selected)
            } else {
                tab.setTextColor(android.graphics.Color.parseColor("#888888"))
                tab.background = null
            }
        }
    }

    private fun loadPhrases(category: EmergencyPhrases.Category) {
        val phrases = EmergencyPhrases.byCategory(category)
        phraseList.layoutManager = LinearLayoutManager(this)
        phraseList.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            inner class PhraseVH(val root: android.widget.LinearLayout) : RecyclerView.ViewHolder(root)
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                val row = android.widget.LinearLayout(this@MainActivity).apply {
                    orientation = android.widget.LinearLayout.VERTICAL
                    setPadding(48, 28, 48, 28)
                    layoutParams = RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT)
                }
                val eng = TextView(this@MainActivity).apply { setTextColor(android.graphics.Color.parseColor("#BBBBBB")); textSize = 13f; tag = "eng" }
                val hin = TextView(this@MainActivity).apply { setTextColor(android.graphics.Color.parseColor("#EEEEEE")); textSize = 17f; setPadding(0,6,0,0); tag = "hin" }
                val div = android.view.View(this@MainActivity).apply {
                    setBackgroundColor(android.graphics.Color.parseColor("#1E1E1E"))
                    layoutParams = android.widget.LinearLayout.LayoutParams(android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 1).also { it.topMargin = 8 }
                }
                row.addView(eng); row.addView(hin); row.addView(div)
                return PhraseVH(row)
            }
            override fun getItemCount() = phrases.size
            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                val phrase = phrases[position]
                val row = (holder as PhraseVH).root
                (row.findViewWithTag("eng") as TextView).text = phrase.english
                (row.findViewWithTag("hin") as TextView).text = phrase.hindi
                row.isClickable = true; row.isFocusable = true
                row.setBackgroundResource(android.R.drawable.list_selector_background)
                row.setOnClickListener {
                    selectedEnglish.text = phrase.english
                    selectedHindi.text   = phrase.hindi
                    selectedPhrasePanel.visibility = android.view.View.VISIBLE
                    inputText.setText(phrase.english)
                    outputText.setTextColor(android.graphics.Color.parseColor("#FF6666"))
                    outputText.text = phrase.hindi
                    micStatus.text  = "Emergency phrase selected"
                    speakOutput(phrase.hindi, TranslationDirection.EN_TO_HI)
                }
            }
        }
    }

    private var hiEnLoading = false
    private fun loadHiEnTranslatorAndVosk() {
        if (hiEnLoading) return; hiEnLoading = true
        val translatorDone = java.util.concurrent.atomic.AtomicBoolean(false)
        val voskDone = java.util.concurrent.atomic.AtomicBoolean(false)

        fun checkBothReady() {
            if (!translatorDone.get() || !voskDone.get()) return
            mainHandler.post {
                hiEnLoading = false
                swapBtn.isEnabled = true
                pendingDirection?.let {
                    currentDirection = it
                    pendingDirection = null
                    updateLangUI()
                    inputText.setText("")
                    outputText.text = if (uiLanguage == "hi") "अनुवाद यहाँ दिखेगा…" else "Translation appears here..."
                }
                rebuildRecognizer()
                micButton.visibility = android.view.View.VISIBLE
                micStatus.text = if (uiLanguage == "hi") "हिंदी में माइक टैप करें" else "Tap mic to speak in Hindi"
                loadingOverlay.animate().alpha(0f).setDuration(400).withEndAction {
                    loadingOverlay.visibility = android.view.View.GONE
                    loadingOverlay.alpha = 1f
                }.start()
            }
        }

        translateExecutor.execute {
            try {
                val t = Translator(this, TranslationDirection.HI_TO_EN)
                t.warmUp(); translatorHiEn = t; translatorHiEnReady = true
                Log.d(TAG, "HI→EN translator ready")
                translatorDone.set(true)
                checkBothReady()
            } catch (e: Exception) {
                Log.e(TAG, "HI→EN failed", e)
                hiEnLoading = false
            }
        }
        modelExecutor.execute {
            try {
                val path = FileUtils.copyAssetFolder(this, "model-hi")
                modelHi = Model(path); voskHiReady = true
                Log.d(TAG, "VOSK Hindi ready")
                voskDone.set(true)
                checkBothReady()
            } catch (e: Exception) {
                Log.e(TAG, "VOSK Hindi failed: ${e.message}")
                hiEnLoading = false
            }
        }
    }


    private fun loadVoskEnglish() {
        modelExecutor.execute {
            try {
                val path = FileUtils.copyAssetFolder(this, "model")
                modelEn = Model(path); recognizer = Recognizer(modelEn, SAMPLE_RATE.toFloat())
                Log.d(TAG, "VOSK English ready")
                mainHandler.post { micButton.isEnabled = true; voskEnReady = true
                    loadingStatus.text = if (!translatorEnHiReady)
                        (if (uiLanguage == "hi") "AI मॉडल लोड हो रहा है…" else "Loading AI model...")
                    else (if (uiLanguage == "hi") "तैयार!" else "Ready!")
                    checkAllReady() }
            } catch (e: Exception) { Log.e(TAG, "VOSK English failed", e); mainHandler.post { micStatus.text = "ASR load failed" } }
        }
    }

    private fun rebuildRecognizer() {
        recognizer?.close(); recognizer = null
        val m = if (currentDirection == TranslationDirection.EN_TO_HI) modelEn else modelHi
        if (m != null) recognizer = Recognizer(m, SAMPLE_RATE.toFloat())
    }

    private fun updateLangUI() {
        val isHindi = uiLanguage == "hi"
        when (currentDirection) {
            TranslationDirection.EN_TO_HI -> {
                langSrc.text = if (isHindi) "अंग्रेज़ी" else "English"
                langTgt.text = if (isHindi) "हिंदी" else "Hindi"
                labelInput.text  = if (isHindi) "अंग्रेज़ी" else "ENGLISH"
                labelOutput.text = if (isHindi) "हिंदी" else "HINDI"
                inputText.hint = if (isHindi) "अंग्रेज़ी में टाइप करें या बोलें…" else "Type or speak in English..."
                inputText.inputType = android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                    android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                micButton.visibility = android.view.View.VISIBLE
                micStatus.text = if (isHindi) "माइक टैप करें" else "Tap mic to speak"
            }
            TranslationDirection.HI_TO_EN -> {
                langSrc.text = if (isHindi) "हिंदी" else "Hindi"
                langTgt.text = if (isHindi) "अंग्रेज़ी" else "English"
                labelInput.text  = if (isHindi) "हिंदी" else "HINDI"
                labelOutput.text = if (isHindi) "अंग्रेज़ी" else "ENGLISH"
                inputText.hint = "हिंदी में बोलें या टाइप करें..."
                inputText.inputType = android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
                micButton.visibility = if (voskHiReady) android.view.View.VISIBLE else android.view.View.GONE
                micStatus.text = when {
                    voskHiReady && isHindi -> "हिंदी में माइक टैप करें"
                    voskHiReady            -> "Tap mic to speak in Hindi"
                    isHindi                -> "ऊपर हिंदी टाइप करें, फिर अनुवाद करें"
                    else                   -> "Type Hindi above, then tap Translate"
                }
            }
        }
    }

    private fun startRecording() {
        if (isRecording || audioLoopRunning) return
        val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        audioRecord = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize * 2)
        val sid = audioRecord!!.audioSessionId
        if (NoiseSuppressor.isAvailable())      { noiseSuppressor = NoiseSuppressor.create(sid);      noiseSuppressor?.enabled = true }
        if (AcousticEchoCanceler.isAvailable()) { echoCanceler    = AcousticEchoCanceler.create(sid); echoCanceler?.enabled    = true }
        if (AutomaticGainControl.isAvailable()) { gainControl     = AutomaticGainControl.create(sid); gainControl?.enabled     = true }
        audioRecord?.startRecording()
        isRecording = true
        audioLoopRunning = true
        // ── Feature 7: reset streaming state at start of each recording ──
        isFinalPending     = false
        lastStreamedPartial = ""
        lastStreamTime      = 0L
        setMicActive()
        audioExecutor.execute {
            val buffer = ShortArray(4096)
            try {
                while (isRecording) {
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: break
                    if (read <= 0) continue
                    val rms = (WaveformView.rmsNormalised(buffer, read) * 5f).coerceIn(0f, 1f)
                    mainHandler.post { waveformView.pushAmplitude(rms) }
                    try {
                        if (recognizer?.acceptWaveForm(buffer, read) == true) {
                            // Vosk detected a sentence boundary — get the completed sub-phrase
                            val result = recognizer?.result
                            if (result != null) {
                                val text = JSONObject(result).optString("text", "").trim()
                                if (text.isNotBlank()) {
                                    Log.d(TAG, "Mid: $text")
                                    lastMidResult = text
                                    // ── Feature 7: stream this sub-phrase immediately ──
                                    maybeStreamTranslate(text)
                                }
                            }
                        } else {
                            // ── Feature 7: also poll partialResult for word-by-word feel ──
                            val partial = recognizer?.partialResult
                            if (partial != null) {
                                val partialText = JSONObject(partial).optString("partial", "").trim()
                                if (partialText.isNotBlank()) {
                                    maybeStreamTranslate(partialText)
                                }
                            }
                        }
                    } catch (e: Exception) { Log.e(TAG, "VOSK loop error", e) }
                }
            } finally { audioLoopRunning = false; Log.d(TAG, "Audio loop exited cleanly") }
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Feature 7: Streaming translation gate
    // Called from audio thread. Checks word count, throttle, and change
    // detection before submitting a partial translate job.
    // ─────────────────────────────────────────────────────────────────────
    private fun maybeStreamTranslate(text: String) {
        if (isFinalPending) return
        val wordCount     = text.trim().split("\\s+".toRegex()).size
        val now           = System.currentTimeMillis()
        val timeSinceLast = now - lastStreamTime
        val textChanged   = text != lastStreamedPartial

        if (wordCount >= STREAM_MIN_WORDS && timeSinceLast >= STREAM_THROTTLE_MS && textChanged) {
            lastStreamedPartial = text
            lastStreamTime      = now
            val snap = currentDirection
            val corrected = when (snap) {
                TranslationDirection.EN_TO_HI -> ASRCorrector.correct(text)
                TranslationDirection.HI_TO_EN -> ASRCorrector.correctHindi(text)
            }
            translateExecutor.execute { runTranslation(corrected, snap, isFinal = false) }
        }
    }

    private fun stopRecordingAndFlush() {
        if (!isRecording) return
        isRecording = false
        // ── Feature 7: mark final pending so no more partials overwrite result ──
        isFinalPending = true
        try { audioRecord?.stop() } catch (_: Exception) {}
        val snap = currentDirection
        audioExecutor.execute {
            try {
                val finalJson = recognizer?.finalResult ?: "{}"
                var text = JSONObject(finalJson).optString("text", "").trim()
                if (text.isBlank()) { text = JSONObject(recognizer?.partialResult ?: "{}").optString("partial", "").trim() }
                if (text.isBlank()) text = lastMidResult
                lastMidResult = ""
                mainHandler.post { setMicIdle() }
                if (text.isNotBlank()) {
                    lastRawAsrText = text  // store raw before correction
                    val corrected = when (snap) {
                        TranslationDirection.EN_TO_HI -> ASRCorrector.correct(text)
                        TranslationDirection.HI_TO_EN -> ASRCorrector.correctHindi(text)
                    }
                    Log.d(TAG, "ASR final [$snap]: $corrected")
                    translateExecutor.execute { runTranslation(corrected, snap, isFinal = true) }
                } else {
                    // No speech detected — clear pending flag
                    isFinalPending = false
                }
                recognizer?.reset()
            } catch (e: Exception) {
                Log.e(TAG, "Flush failed", e)
                isFinalPending = false
                mainHandler.post { setMicIdle() }
            } finally {
                noiseSuppressor?.release(); noiseSuppressor = null
                echoCanceler?.release(); echoCanceler = null
                gainControl?.release(); gainControl = null
                audioRecord?.release(); audioRecord = null
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // runTranslation — extended with:
    //   Feature 1: latency display
    //   Feature 7: isFinal flag controls TTS + output style
    // ─────────────────────────────────────────────────────────────────────
    private fun runTranslation(text: String, direction: TranslationDirection, isFinal: Boolean = true) {
        if (isFinal) {
            mainHandler.post {
                inputText.setText(text)
                micStatus.text = "Translating..."
                outputText.text = "Translating..."
                outputText.setTypeface(null, android.graphics.Typeface.NORMAL)
            }
        } else {
            // For partial/streaming: don't overwrite inputText, just show "translating..." quietly
            if (isFinalPending) return  // double-check: final arrived while queued
        }
        try {
            val translator = when (direction) {
                TranslationDirection.EN_TO_HI -> {
                    if (translatorEnHi == null) translatorEnHi = Translator(this, direction)
                    translatorEnHi!!
                }
                TranslationDirection.HI_TO_EN -> {
                    translatorHiEn ?: run {
                        if (isFinal) mainHandler.post { outputText.text = "HI→EN model not ready"; micStatus.text = "Tap mic to speak" }
                        return
                    }
                }
            }

            // ── Feature 1: measure translation latency ───────────────────
            val startMs = System.currentTimeMillis()
            val result  = translator.translate(text)
            val latencyMs = System.currentTimeMillis() - startMs

            Log.d(TAG, "[$direction] ${if (isFinal) "FINAL" else "STREAM"} $text → $result (${latencyMs}ms)")

            mainHandler.post {
                if (!isFinal && isFinalPending) return@post  // final arrived — discard this partial result

                if (isFinal) {
                    outputText.setTypeface(null, android.graphics.Typeface.NORMAL)
                    outputText.setTextColor(android.graphics.Color.parseColor("#6EE7B7"))
                    outputText.text = result
                    micStatus.text = if (uiLanguage == "hi") "अनुवाद तैयार" else "Translation ready"
                    // ── Upgrade 1: show ASR heard vs corrected hint ───────────
                    val raw = lastRawAsrText
                    if (raw.isNotBlank() && raw.lowercase().trim() != text.lowercase().trim()) {
                                                asrHintText.text = if (uiLanguage == "hi") "सुना: $raw" else "Heard: $raw"
                        asrHintText.visibility = android.view.View.VISIBLE
                    } else {
                        asrHintText.visibility = android.view.View.GONE
                    }
                    addToHistory(text, result)
                    speakOutput(result, direction)
                    isFinalPending = false
                } else {
                    // ── Streaming partial: dimmed italic, no TTS ────────────
                    outputText.setTypeface(null, android.graphics.Typeface.ITALIC)
                    outputText.setTextColor(android.graphics.Color.parseColor("#4A7A6A"))
                    outputText.text = result
                    micStatus.text = if (uiLanguage == "hi") "अनुवाद हो रहा है…" else "Translating..."
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Translation failed [$direction] isFinal=$isFinal", e)
            if (isFinal) {
                isFinalPending = false
                mainHandler.post {
                    outputText.setTypeface(null, android.graphics.Typeface.NORMAL)
                    outputText.setTextColor(android.graphics.Color.parseColor("#2D4A3E"))
                    outputText.text = "Translation failed"
                    micStatus.text  = "Tap mic to speak"
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Feature 6: TTS fallback
    // Always speak if TTS initialised, even if target language data is
    // missing — Android will use its default voice rather than silently
    // doing nothing.
    // ─────────────────────────────────────────────────────────────────────
    private fun speakOutput(text: String, direction: TranslationDirection) {
        if (!ttsReady) return
        if (!ttsEnabled) return
        try {
            val locale = when (direction) {
                TranslationDirection.EN_TO_HI -> Locale("hi", "IN")
                TranslationDirection.HI_TO_EN -> Locale.US
            }
            val result = tts.setLanguage(locale)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                // Try English as a safe fallback so audio always plays
                tts.setLanguage(Locale.ENGLISH)
                Log.w(TAG, "TTS: target language unavailable, falling back to English voice")
            }
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tts_out")
        } catch (e: Exception) {
            Log.e(TAG, "TTS speak failed", e)
            // Never surface TTS errors to the user — translation result stays visible
        }
    }

    private fun setMicActive() {
        micStatus.text = "Listening..."
        waveformView.visibility = android.view.View.VISIBLE; waveformView.startAnimation()
        micPulse.visibility = android.view.View.VISIBLE
        AlphaAnimation(1.0f, 0.2f).apply { duration = 600; repeatMode = Animation.REVERSE; repeatCount = Animation.INFINITE; micPulse.startAnimation(this) }
    }

    private fun setMicIdle() {
        waveformView.stopAnimation(); waveformView.visibility = android.view.View.INVISIBLE
        micPulse.clearAnimation(); micPulse.visibility = android.view.View.INVISIBLE
        micStatus.text = if (uiLanguage == "hi") "माइक टैप करें" else "Tap mic to speak"
        // ── Feature 7: reset streaming state when mic goes idle ──────────
        lastStreamedPartial = ""
        lastStreamTime      = 0L
        // Note: isFinalPending is reset in runTranslation after final completes
    }

    // ─────────────────────────────────────────────────────────────────────
    // Feature 6: onInit — TTS engine init with Google TTS fallback
    // ttsReady = true as long as TextToSpeech.SUCCESS, so speakOutput()
    // always gets a chance to fire (it handles language fallback internally)
    // ─────────────────────────────────────────────────────────────────────
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            // Try to set Hindi as default language; if data is missing we
            // still mark ready — speakOutput() handles per-speak fallback
            val langResult = tts.setLanguage(Locale("hi", "IN"))
            hindiTtsAvailable = langResult != TextToSpeech.LANG_MISSING_DATA
                             && langResult != TextToSpeech.LANG_NOT_SUPPORTED
            ttsReady = true
            mainHandler.post { updateTtsIndicator() }
            Log.d(TAG, "TTS init OK — Hindi available: $hindiTtsAvailable")
        } else {
            // TTS engine itself failed to load (very rare) — try Google TTS explicitly
            Log.w(TAG, "Default TTS engine failed, trying Google TTS")
            try {
                tts = TextToSpeech(this, { googleStatus ->
                    if (googleStatus == TextToSpeech.SUCCESS) {
                        ttsReady = true
                        Log.d(TAG, "Google TTS fallback initialised")
                    } else {
                        Log.e(TAG, "All TTS engines failed")
                        ttsReady = false
                    }
                }, "com.google.android.tts")
            } catch (e: Exception) {
                Log.e(TAG, "TTS fallback init failed", e)
                ttsReady = false
            }
        }
    }

    private fun checkAllReady() {
        if (voskEnReady && translatorEnHiReady) {
            loadingStatus.text = if (uiLanguage == "hi") "तैयार!" else "Ready!"
            mainHandler.postDelayed({ loadingOverlay.animate().alpha(0f).setDuration(250).withEndAction { loadingOverlay.visibility = android.view.View.GONE }.start() }, 300)
        }
    }

    private fun animateLoadingDots() {
        val dots = arrayOf(findViewById<android.view.View>(R.id.dot1), findViewById(R.id.dot2), findViewById(R.id.dot3))
        var step = 0
        val r = object : Runnable { override fun run() { if (loadingOverlay.visibility != android.view.View.VISIBLE) return; dots.forEachIndexed { i, d -> d.alpha = if (i == step % 3) 1f else 0.3f }; step++; mainHandler.postDelayed(this, 300) } }
        mainHandler.post(r)
    }

    private fun checkMicPermission(): Boolean {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) return true
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), MIC_PERMISSION); return false
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == MIC_PERMISSION && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED && !isRecording && !audioLoopRunning) startRecording()
    }

    // ── Upgrade 4: History management ──────────────────────────────────
    private fun addToHistory(input: String, output: String) {
        if (input.isBlank() || output.isBlank()) return
        if (translationHistory.size >= 5) translationHistory.removeFirst()
        translationHistory.addLast(Pair(input, output))
    }

    private fun showHistoryDialog() {
        if (translationHistory.isEmpty()) {
            android.widget.Toast.makeText(this,
                if (uiLanguage == "hi") "कोई इतिहास नहीं" else "No history yet",
                android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        val items = translationHistory.map { (src, tgt) ->
            "${src.take(30)}${if (src.length > 30) "…" else ""} → ${tgt.take(30)}${if (tgt.length > 30) "…" else ""}"
        }.reversed().toTypedArray()
        android.app.AlertDialog.Builder(this)
            .setTitle(if (uiLanguage == "hi") "हाल के अनुवाद" else "Recent Translations")
            .setItems(items) { _, idx ->
                val actual = translationHistory.toList().reversed()[idx]
                inputText.setText(actual.first)
                outputText.text = actual.second
                outputText.setTextColor(android.graphics.Color.parseColor("#6EE7B7"))
            }
            .setNegativeButton(if (uiLanguage == "hi") "बंद करें" else "Close") { d, _ -> d.dismiss() }
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        correctionRunnable?.let { mainHandler.removeCallbacks(it) }
        isRecording = false; audioLoopRunning = false; isFinalPending = false
        try { audioRecord?.stop(); audioRecord?.release() } catch (_: Exception) {}
        modelEn?.close(); modelHi?.close(); recognizer?.close(); tts.shutdown()
        audioExecutor.shutdown(); modelExecutor.shutdown(); translateExecutor.shutdown()
    }
    private var ttsEnabled = true

    private fun showMenuTray() {
        val drawer = findViewById<androidx.drawerlayout.widget.DrawerLayout>(R.id.drawerLayout)
        val audioItem = findViewById<android.widget.TextView>(R.id.trayAudio)
        audioItem.text = "   Import Audio"
        drawer.openDrawer(androidx.core.view.GravityCompat.START)
    }

    private fun setupDrawer() {
        val drawer = findViewById<androidx.drawerlayout.widget.DrawerLayout>(R.id.drawerLayout)
        val audioItem = findViewById<android.widget.TextView>(R.id.trayAudio)
        findViewById<android.widget.TextView>(R.id.trayHistory).setOnClickListener {
            drawer.closeDrawers(); showHistoryDialog()
        }
        findViewById<android.widget.TextView>(R.id.trayLanguage).setOnClickListener {
            drawer.closeDrawers(); showLanguageDialog()
        }
        audioItem.setOnClickListener {
            drawer.closeDrawers()
            openAudioFilePicker()
        }
    }

    private fun updateTtsIndicator() {
        val indicator = findViewById<android.widget.TextView?>(R.id.ttsStatusText) ?: return
        if (!hindiTtsAvailable) {
            indicator.text = if (uiLanguage == "hi")
                "Hindi voice not installed — tap to fix"
            else
                "Hindi voice not installed — tap to fix"
            indicator.visibility = android.view.View.VISIBLE
            indicator.setOnClickListener {
                startActivity(android.content.Intent(
                    android.speech.tts.TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA))
            }
        } else {
            indicator.visibility = android.view.View.GONE
        }
    }

}
