package com.winamp.classic.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class WinampCanvasEqualizerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var onBandLevelChangedListener: ((bandIndex: Int, levelDb: Int) -> Unit)? = null
    var onEqEnabledChangedListener: ((Boolean) -> Unit)? = null
    var onPresetClickListener: (() -> Unit)? = null

    var isEqOn: Boolean = true
        set(value) {
            field = value
            postInvalidate()
        }

    var isAutoOn: Boolean = false
        set(value) {
            field = value
            postInvalidate()
        }

    val bandLevelsDb = IntArray(10) { 0 } // -12 to +12
    var preampLevelDb: Int = 0           // -12 to +12

    private val yellowLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E0A000")
        strokeWidth = 2f
    }

    private val greenTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00FF2A")
        typeface = Typeface.MONOSPACE
        textSize = 9f
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = (width * (116f / 275f)).toInt()
        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        if (w == 0f || h == 0f) return

        val scaleX = w / 275f
        val scaleY = h / 116f

        // 1. Draw EQMAIN.BMP Background (275 x 116)
        val eqBgBmp = WinampSkinManager.getEqualizerBackgroundBitmap()
        if (eqBgBmp != null) {
            canvas.drawBitmap(eqBgBmp, null, RectF(0f, 0f, w, h), null)
        }

        // 2. Draw ON, AUTO, PRESETS Buttons
        val onBmp = WinampSkinManager.getEqButtonBitmap(0, isEqOn)
        if (onBmp != null) {
            val dest = RectF(14f * scaleX, 18f * scaleY, 40f * scaleX, 30f * scaleY)
            canvas.drawBitmap(onBmp, null, dest, null)
        }

        val autoBmp = WinampSkinManager.getEqButtonBitmap(1, isAutoOn)
        if (autoBmp != null) {
            val dest = RectF(40f * scaleX, 18f * scaleY, 72f * scaleX, 30f * scaleY)
            canvas.drawBitmap(autoBmp, null, dest, null)
        }

        val presetsBmp = WinampSkinManager.getEqButtonBitmap(2, false)
        if (presetsBmp != null) {
            val dest = RectF(224f * scaleX, 18f * scaleY, 268f * scaleX, 30f * scaleY)
            canvas.drawBitmap(presetsBmp, null, dest, null)
        }

        // 3. Draw Preamp Slider Handle (x: 21)
        val thumbBmp = WinampSkinManager.getEqSliderThumbBitmap()
        if (thumbBmp != null) {
            val preampRatio = ((12 - preampLevelDb) / 24f).coerceIn(0f, 1f)
            val px = 21f * scaleX
            val py = (38f + (preampRatio * 52f)) * scaleY
            val dest = RectF(px, py, px + (14f * scaleX), py + (11f * scaleY))
            canvas.drawBitmap(thumbBmp, null, dest, null)
        }

        // 4. Draw 10 Band Vertical Sliders (x: 78 + i * 18)
        val bandStartX = 78f
        val bandStepX = 18f

        for (i in 0..9) {
            val lvl = bandLevelsDb[i].coerceIn(-12, 12)
            val ratio = ((12 - lvl) / 24f).coerceIn(0f, 1f)
            val bx = (bandStartX + (i * bandStepX)) * scaleX
            val by = (38f + (ratio * 52f)) * scaleY

            // Draw Silver Thumb Handle
            if (thumbBmp != null) {
                val dest = RectF(bx, by, bx + (14f * scaleX), by + (11f * scaleY))
                canvas.drawBitmap(thumbBmp, null, dest, null)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w == 0f || h == 0f) return super.onTouchEvent(event)

        val scaleX = w / 275f
        val scaleY = h / 116f
        val touchX = event.x / scaleX
        val touchY = event.y / scaleY

        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                // Preamp Slider (x: 18..35, y: 35..95)
                if (touchX in 18f..35f && touchY in 35f..95f) {
                    val ratio = ((touchY - 38f) / 52f).coerceIn(0f, 1f)
                    preampLevelDb = (12 - (ratio * 24f)).toInt()
                    postInvalidate()
                    return true
                }

                // 10 Band Sliders (x: 70..260, y: 35..95)
                if (touchY in 35f..95f && touchX >= 70f) {
                    val bandIdx = ((touchX - 74f) / 18f).toInt().coerceIn(0, 9)
                    val ratio = ((touchY - 38f) / 52f).coerceIn(0f, 1f)
                    val levelDb = (12 - (ratio * 24f)).toInt()
                    bandLevelsDb[bandIdx] = levelDb
                    onBandLevelChangedListener?.invoke(bandIdx, levelDb)
                    postInvalidate()
                    return true
                }
            }

            MotionEvent.ACTION_UP -> {
                // Header Buttons
                if (touchY in 15f..32f) {
                    when {
                        touchX in 14f..39f -> {
                            isEqOn = !isEqOn
                            onEqEnabledChangedListener?.invoke(isEqOn)
                        }
                        touchX in 40f..72f -> {
                            isAutoOn = !isAutoOn
                        }
                        touchX in 220f..270f -> {
                            onPresetClickListener?.invoke()
                        }
                    }
                }
            }
        }
        return true
    }
}
