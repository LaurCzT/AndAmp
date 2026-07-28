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
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

class MilkdropVisualizerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#05060C")
    }

    private val mainPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private var waveData: ByteArray? = null
    private var phase = 0f
    private var presetIndex = 0

    // MilkDrop 3 Beat & Motion Variables
    private var bass = 1.0f
    private var mid = 1.0f
    private var treb = 1.0f
    private var isBeat = false
    private var zoom = 1.0f
    private var rot = 0.0f

    private val handler = Handler(Looper.getMainLooper())
    private val animRunnable = object : Runnable {
        override fun run() {
            updateMilkdropMotion()
            invalidate()
            handler.postDelayed(this, 25)
        }
    }

    init {
        setOnClickListener {
            presetIndex = (presetIndex + 1) % 5
            invalidate()
        }
    }

    fun updateWaveform(bytes: ByteArray) {
        waveData = bytes
        analyzeAudioEnergy(bytes)
        invalidate()
    }

    private fun analyzeAudioEnergy(bytes: ByteArray) {
        if (bytes.isEmpty()) return
        var bassSum = 0f
        var midSum = 0f
        var trebSum = 0f

        val third = bytes.size / 3
        for (i in 0 until third) bassSum += abs(bytes[i].toInt())
        for (i in third until third * 2) midSum += abs(bytes[i].toInt())
        for (i in third * 2 until bytes.size) trebSum += abs(bytes[i].toInt())

        val newBass = (bassSum / third) / 64f + 0.5f
        val newMid = (midSum / third) / 64f + 0.5f
        val newTreb = (trebSum / (bytes.size - third * 2)) / 64f + 0.5f

        isBeat = newBass > bass * 1.35f && newBass > 1.2f
        bass = newBass
        mid = newMid
        treb = newTreb
    }

    private fun updateMilkdropMotion() {
        phase += 0.06f
        rot += if (isBeat) 0.08f else 0.01f
        zoom = if (isBeat) 1.15f else 1.0f + (sin(phase) * 0.05f)
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

        // Render MilkDrop 3 Preset
        when (presetIndex) {
            0 -> renderCosmicPlasmaTunnel(canvas, w, h)
            1 -> renderSynthwaveFluidBars(canvas, w, h)
            2 -> renderMilkdropHyperKaleidoscope(canvas, w, h)
            3 -> renderOscilloscopeMesh3D(canvas, w, h)
            4 -> renderNeonCyberGridWarp(canvas, w, h)
        }
    }

    // MilkDrop 3 Preset 0: Cosmic Plasma Tunnel
    private fun renderCosmicPlasmaTunnel(canvas: Canvas, w: Float, h: Float) {
        val cx = w / 2f
        val cy = h / 2f
        val maxR = (w.coerceAtLeast(h)) / 2f

        canvas.save()
        canvas.scale(zoom, zoom, cx, cy)
        canvas.rotate(rot * 20f, cx, cy)

        val rings = 8
        for (rIdx in 1..rings) {
            val r = (maxR / rings) * rIdx * (bass * 0.8f)
            val hue = (phase * 30f + rIdx * 45f) % 360f
            mainPaint.color = Color.HSVToColor(floatArrayOf(hue, 0.9f, 1.0f))
            mainPaint.strokeWidth = 3f + rIdx

            val path = Path()
            val points = 36
            for (p in 0..points) {
                val angle = (p * (360f / points)) * (Math.PI.toFloat() / 180f)
                val waveVal = waveData?.getOrNull((p * 3) % (waveData?.size ?: 1))?.toFloat() ?: 0f
                val currR = r + sin(angle * 6f + phase) * 25f + (waveVal * 0.3f * bass)
                val x = cx + currR * cos(angle)
                val y = cy + currR * sin(angle)

                if (p == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
            canvas.drawPath(path, mainPaint)
        }

        canvas.restore()
    }

    // MilkDrop 3 Preset 1: Synthwave Fluid Bars
    private fun renderSynthwaveFluidBars(canvas: Canvas, w: Float, h: Float) {
        val barCount = 32
        val barWidth = (w / barCount) - 3f
        val gradient = LinearGradient(
            0f, h, 0f, 0f,
            intArrayOf(
                Color.parseColor("#00FF2A"),
                Color.parseColor("#00FFFF"),
                Color.parseColor("#FF00FF"),
                Color.parseColor("#FFD700")
            ), null, Shader.TileMode.CLAMP
        )
        fillPaint.shader = gradient

        for (i in 0 until barCount) {
            val x = i * (barWidth + 3f) + 1.5f
            val dataVal = waveData?.getOrNull((i * 4) % (waveData?.size ?: 1))?.let { abs(it.toInt()) } ?: 0
            val barHeight = (sin(phase + i * 0.25f) * 0.3f + 0.4f) * (h * 0.75f) + (dataVal * 0.6f * treb)
            val top = (h - barHeight).coerceAtLeast(15f)

            canvas.drawRect(x, top, x + barWidth, h - 5f, fillPaint)

            // Neon peak dots
            particlePaint.color = Color.WHITE
            canvas.drawCircle(x + barWidth / 2f, top - 6f, 3f, particlePaint)
        }
    }

    // MilkDrop 3 Preset 2: MilkDrop Hyper-Kaleidoscope
    private fun renderMilkdropHyperKaleidoscope(canvas: Canvas, w: Float, h: Float) {
        val cx = w / 2f
        val cy = h / 2f

        val gradient = LinearGradient(
            0f, 0f, w, h,
            Color.parseColor("#FF007F"),
            Color.parseColor("#7F00FF"),
            Shader.TileMode.MIRROR
        )
        fillPaint.shader = gradient
        mainPaint.shader = gradient
        mainPaint.strokeWidth = 6f

        canvas.save()
        canvas.scale(zoom, zoom, cx, cy)

        val petals = 12
        val path = Path()
        for (i in 0..petals * 2) {
            val angle = (i * (180f / petals)) * (Math.PI.toFloat() / 180f)
            val dataVal = waveData?.getOrNull(i % (waveData?.size ?: 1))?.toFloat() ?: 0f
            val r = (w / 3f) * (1f + sin(angle * 4f + phase) * 0.4f) + (dataVal * 0.4f * mid)
            val x = cx + r * cos(angle)
            val y = cy + r * sin(angle)

            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()

        canvas.drawPath(path, fillPaint)
        canvas.drawPath(path, mainPaint)

        canvas.restore()
    }

    // MilkDrop 3 Preset 3: 3D Oscilloscope Frequency Mesh
    private fun renderOscilloscopeMesh3D(canvas: Canvas, w: Float, h: Float) {
        val cy = h / 2f
        val gradient = LinearGradient(
            0f, 0f, w, 0f,
            Color.parseColor("#00FF88"),
            Color.parseColor("#0088FF"),
            Shader.TileMode.CLAMP
        )
        mainPaint.shader = gradient
        mainPaint.strokeWidth = 5f

        val lines = 5
        for (l in 0 until lines) {
            val offset = (l - 2) * 20f
            val path = Path()
            val data = waveData

            if (data != null && data.isNotEmpty()) {
                val step = w / data.size
                for (i in data.indices) {
                    val x = i * step
                    val y = cy + offset + (data[i].toFloat() / 128f) * (h / 3.5f) * (1f + l * 0.15f)
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
            }
            canvas.drawPath(path, mainPaint)
        }
    }

    // MilkDrop 3 Preset 4: Neon Cyber-Grid Warp
    private fun renderNeonCyberGridWarp(canvas: Canvas, w: Float, h: Float) {
        val cx = w / 2f
        val cy = h * 0.4f

        mainPaint.shader = null
        mainPaint.color = Color.parseColor("#00E5FF")
        mainPaint.strokeWidth = 2f

        // Draw perspective grid lines
        val vLines = 16
        for (i in 0..vLines) {
            val targetX = (w / vLines) * i
            canvas.drawLine(cx, cy, targetX, h, mainPaint)
        }

        val hLines = 8
        for (j in 1..hLines) {
            val yRatio = (j.toFloat() / hLines)
            val y = cy + (h - cy) * (yRatio * yRatio)
            canvas.drawLine(0f, y, w, y, mainPaint)
        }

        // Draw pulsating bass particles
        particlePaint.color = if (isBeat) Color.parseColor("#FF0055") else Color.parseColor("#FFE600")
        val particles = 20
        for (p in 0 until particles) {
            val px = (sin(phase + p * 0.5f) * 0.4f + 0.5f) * w
            val py = (cos(phase * 1.2f + p) * 0.3f + 0.4f) * h
            val size = 4f + (bass * 6f)
            canvas.drawCircle(px, py, size, particlePaint)
        }
    }
}
