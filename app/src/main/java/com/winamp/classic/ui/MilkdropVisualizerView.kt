package com.winamp.classic.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.sin

class MilkdropVisualizerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#090A14")
    }

    private val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }

    private val synthPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private var waveData: ByteArray? = null
    private var phase = 0f
    private var mode = 0 // 0: Fluid Nebula, 1: Synth Waveform, 2: Spectrum Bars

    private val handler = Handler(Looper.getMainLooper())
    private val animRunnable = object : Runnable {
        override fun run() {
            phase += 0.08f
            invalidate()
            handler.postDelayed(this, 30)
        }
    }

    init {
        setOnClickListener {
            mode = (mode + 1) % 3
            invalidate()
        }
    }

    fun updateWaveform(bytes: ByteArray) {
        waveData = bytes
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        handler.post(animRunnable)
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacks(animRunnable)
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()

        if (w == 0f || h == 0f) return

        canvas.drawRect(0f, 0f, w, h, bgPaint)

        // Draw dynamic visualizer based on mode
        when (mode) {
            0 -> drawFluidNebula(canvas, w, h)
            1 -> drawSynthWaveform(canvas, w, h)
            2 -> drawSpectrumBars(canvas, w, h)
        }
    }

    private fun drawFluidNebula(canvas: Canvas, w: Float, h: Float) {
        val cx = w / 2f
        val cy = h / 2f
        val radius = (w.coerceAtMost(h) / 3f)

        val gradient = LinearGradient(
            0f, 0f, w, h,
            intArrayOf(
                Color.parseColor("#8E44AD"),
                Color.parseColor("#3498DB"),
                Color.parseColor("#2ECC71"),
                Color.parseColor("#F1C40F"),
                Color.parseColor("#E74C3C")
            ),
            null, Shader.TileMode.MIRROR
        )
        synthPaint.shader = gradient
        wavePaint.shader = gradient

        val path = Path()
        val points = 72
        for (i in 0..points) {
            val angle = (i * (360f / points)) * (Math.PI.toFloat() / 180f)
            val waveVal = waveData?.getOrNull(i % (waveData?.size ?: 1))?.toFloat() ?: 0f
            val rOffset = sin(angle * 5f + phase) * 40f + (waveVal * 0.3f)
            val currR = radius + rOffset
            val x = cx + currR * cos(angle)
            val y = cy + currR * sin(angle)

            if (i == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }
        path.close()

        canvas.drawPath(path, synthPaint)
        canvas.drawPath(path, wavePaint)
    }

    private fun drawSynthWaveform(canvas: Canvas, w: Float, h: Float) {
        val cy = h / 2f
        val gradient = LinearGradient(
            0f, 0f, w, 0f,
            Color.parseColor("#00FFFF"),
            Color.parseColor("#FF00FF"),
            Shader.TileMode.CLAMP
        )
        wavePaint.shader = gradient
        wavePaint.strokeWidth = 6f

        val path = Path()
        val data = waveData
        if (data != null && data.isNotEmpty()) {
            val step = w / data.size
            for (i in data.indices) {
                val x = i * step
                val y = cy + (data[i].toFloat() / 128f) * (h / 3f)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
        } else {
            val points = 100
            val step = w / points
            for (i in 0..points) {
                val x = i * step
                val y = cy + sin(phase + i * 0.15f) * (h / 4f)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
        }
        canvas.drawPath(path, wavePaint)
    }

    private fun drawSpectrumBars(canvas: Canvas, w: Float, h: Float) {
        val barCount = 32
        val barWidth = (w / barCount) - 4f
        val gradient = LinearGradient(
            0f, h, 0f, 0f,
            Color.parseColor("#00FF2A"),
            Color.parseColor("#FFD700"),
            Shader.TileMode.CLAMP
        )
        synthPaint.shader = gradient

        for (i in 0 until barCount) {
            val x = i * (barWidth + 4f) + 2f
            val dataVal = waveData?.getOrNull((i * 4) % (waveData?.size ?: 1))?.let { Math.abs(it.toInt()) } ?: 0
            val barHeight = (sin(phase + i * 0.3f) * 0.4f + 0.5f) * (h * 0.8f) + (dataVal * 0.5f)
            val top = (h - barHeight).coerceAtLeast(10f)
            canvas.drawRect(x, top, x + barWidth, h - 10f, synthPaint)
        }
    }
}
