package com.example.bhashabridge_v3_4

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.math.abs
import kotlin.math.sqrt

class WaveformView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color       = Color.parseColor("#6EE7B7")
        strokeWidth = 4f
        strokeCap   = Paint.Cap.ROUND
    }

    private val bars       = 32
    private val amplitudes = FloatArray(bars) { 0.05f }
    private var isAnimating = false

    // Shift-register: new amplitude pushed to front, old ones shift right.
    // Called from the audio thread in MainActivity on every buffer read.
    fun pushAmplitude(rmsNormalised: Float) {
        // Shift right
        for (i in bars - 1 downTo 1) amplitudes[i] = amplitudes[i - 1]
        // Smooth new value: blend 70% new + 30% previous to avoid jitter
        amplitudes[0] = (rmsNormalised * 0.7f + amplitudes[0] * 0.3f).coerceIn(0.05f, 1f)
        invalidate()
    }

    fun startAnimation() {
        isAnimating = true
        // Don't fill with random — real data comes via pushAmplitude()
        // Just ensure bars are cleared to idle state
        for (i in amplitudes.indices) amplitudes[i] = 0.05f
        invalidate()
    }

    fun stopAnimation() {
        isAnimating = false
        // Fade bars back to baseline
        for (i in amplitudes.indices) amplitudes[i] = 0.05f
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w        = width.toFloat()
        val h        = height.toFloat()
        val barWidth = w / (bars * 2f)
        val spacing  = barWidth

        for (i in 0 until bars) {
            val x    = i * (barWidth + spacing) + barWidth / 2
            // Minimum 25% height + amplitude on top — always visible, scales with voice
            val barH = (0.25f + amplitudes[i] * 0.75f) * h * 0.85f
            val top    = (h - barH) / 2
            val bottom = top + barH
            val brightness = (amplitudes[i] * 255).toInt().coerceIn(80, 255)
            paint.color = Color.rgb(brightness / 2, brightness, brightness / 2 + 40)
            canvas.drawLine(x, top, x, bottom, paint)
        }
    }

    // ── Static helper: compute RMS of a ShortArray audio buffer ──────────────
    companion object {
        fun rmsNormalised(buffer: ShortArray, read: Int): Float {
            if (read <= 0) return 0f
            var sum = 0.0
            for (i in 0 until read) sum += buffer[i].toDouble() * buffer[i]
            val rms = sqrt(sum / read)
            // Short max is 32768 — normalise to 0..1
            return (rms / 32768.0).toFloat().coerceIn(0f, 1f)
        }
    }
}

