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

class WinampCanvasPlayerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // Callbacks
    var onPlayClickListener: (() -> Unit)? = null
    var onPauseClickListener: (() -> Unit)? = null
    var onStopClickListener: (() -> Unit)? = null
    var onNextClickListener: (() -> Unit)? = null
    var onPrevClickListener: (() -> Unit)? = null
    var onEjectClickListener: (() -> Unit)? = null
    var onShuffleToggleListener: (() -> Unit)? = null
    var onRepeatToggleListener: (() -> Unit)? = null
    var onEqToggleListener: (() -> Unit)? = null
    var onPlToggleListener: (() -> Unit)? = null
    var onSeekChangeListener: ((progressRatio: Float) -> Unit)? = null
    var onVolumeChangeListener: ((volumeRatio: Float) -> Unit)? = null
    var onBalanceChangeListener: ((balanceRatio: Float) -> Unit)? = null

    // State
    var marqueeText: String = "WINAMP 5.662 (NO TRACK LOADED)"
        set(value) {
            field = value
            postInvalidate()
        }

    var bitrateText: String = "---"
        set(value) {
            field = value
            postInvalidate()
        }

    var sampleRateText: String = "--"
        set(value) {
            field = value
            postInvalidate()
        }

    var isStereo: Boolean = true
        set(value) {
            field = value
            postInvalidate()
        }

    var timeText: String = "00:00"
        set(value) {
            field = value
            postInvalidate()
        }

    var isPlaying: Boolean = false
        set(value) {
            field = value
            postInvalidate()
        }

    var isPaused: Boolean = false
        set(value) {
            field = value
            postInvalidate()
        }

    var isShuffle: Boolean = false
        set(value) {
            field = value
            postInvalidate()
        }

    var isRepeat: Boolean = false
        set(value) {
            field = value
            postInvalidate()
        }

    var isEqVisible: Boolean = true
        set(value) {
            field = value
            postInvalidate()
        }

    var isPlVisible: Boolean = true
        set(value) {
            field = value
            postInvalidate()
        }

    var progressRatio: Float = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            postInvalidate()
        }

    var volumeRatio: Float = 0.8f
        set(value) {
            field = value.coerceIn(0f, 1f)
            postInvalidate()
        }

    var balanceRatio: Float = 0.5f
        set(value) {
            field = value.coerceIn(0f, 1f)
            postInvalidate()
        }

    private var pressedBtnIdx: Int = -1

    private val greenTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00FF2A")
        typeface = Typeface.MONOSPACE
        textSize = 10f
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

        // 1. Draw MAIN.BMP Background (275 x 116)
        val mainBgBmp = WinampSkinManager.getMainBackgroundBitmap()
        if (mainBgBmp != null) {
            canvas.drawBitmap(mainBgBmp, null, RectF(0f, 0f, w, h), null)
        }

        // 2. Draw LED Time Digits inside (36, 26, 60, 32)
        val timeStr = if (timeText.length < 5) timeText.padStart(5, '0') else timeText
        val digitW = 9f * scaleX
        val digitH = 13f * scaleY
        val startX = 48f * scaleX
        val startY = 26f * scaleY

        for (i in timeStr.indices) {
            val char = timeStr[i]
            val bmp = WinampSkinManager.getDigitBitmap(char)
            if (bmp != null) {
                val dx = startX + (i * digitW)
                val dest = RectF(dx, startY, dx + digitW, startY + digitH)
                canvas.drawBitmap(bmp, null, dest, null)
            }
        }

        // 3. Draw Marquee Text inside (111, 27)
        greenTextPaint.textSize = 9f * scaleY
        val marqueeX = 111f * scaleX
        val marqueeY = 36f * scaleY
        canvas.drawText(marqueeText.take(24), marqueeX, marqueeY, greenTextPaint)

        // 4. Draw Specs: Bitrate (111, 43), kHz (156, 43)
        canvas.drawText("$bitrateText", 111f * scaleX, 52f * scaleY, greenTextPaint)
        canvas.drawText("$sampleRateText", 156f * scaleX, 52f * scaleY, greenTextPaint)

        // 5. Draw Volume Silver Slider Thumb at (107 + volumeRatio * 54, 57)
        val volBmp = WinampSkinManager.getSilverSliderThumbBitmap()
        if (volBmp != null) {
            val vx = (107f + volumeRatio * 54f) * scaleX
            val vy = 57f * scaleY
            val dest = RectF(vx, vy, vx + (14f * scaleX), vy + (11f * scaleY))
            canvas.drawBitmap(volBmp, null, dest, null)
        }

        // 6. Draw Balance Silver Slider Thumb at (177 + balanceRatio * 24, 57)
        val balBmp = WinampSkinManager.getSilverSliderThumbBitmap()
        if (balBmp != null) {
            val bx = (177f + balanceRatio * 24f) * scaleX
            val by = 57f * scaleY
            val dest = RectF(bx, by, bx + (14f * scaleX), by + (11f * scaleY))
            canvas.drawBitmap(balBmp, null, dest, null)
        }

        // 7. Draw EQ & PL Toggle Buttons at (219, 58) and (242, 58)
        val eqBmp = WinampSkinManager.getEqToggleBitmap(isEqVisible)
        if (eqBmp != null) {
            val dest = RectF(219f * scaleX, 58f * scaleY, 242f * scaleX, 70f * scaleY)
            canvas.drawBitmap(eqBmp, null, dest, null)
        }

        val plBmp = WinampSkinManager.getPlToggleBitmap(isPlVisible)
        if (plBmp != null) {
            val dest = RectF(242f * scaleX, 58f * scaleY, 265f * scaleX, 70f * scaleY)
            canvas.drawBitmap(plBmp, null, dest, null)
        }

        // 8. Draw Main Seeker Gold Thumb at (16 + progressRatio * 219, 72)
        val seekerBmp = WinampSkinManager.getGoldSeekerThumbBitmap()
        if (seekerBmp != null) {
            val sx = (16f + progressRatio * 219f) * scaleX
            val sy = 72f * scaleY
            val dest = RectF(sx, sy, sx + (29f * scaleX), sy + (10f * scaleY))
            canvas.drawBitmap(seekerBmp, null, dest, null)
        }

        // 9. Draw Transport Buttons (Prev, Play, Pause, Stop, Next, Eject) at y: 88
        val transportX = listOf(16f, 39f, 62f, 85f, 108f, 136f)
        for (i in 0..5) {
            val isPressed = pressedBtnIdx == i
            val btnBmp = WinampSkinManager.getTransportBitmap(i, isPressed)
            if (btnBmp != null) {
                val tx = transportX[i] * scaleX
                val ty = 88f * scaleY
                val tw = (if (i == 5) 22f else 23f) * scaleX
                val th = 18f * scaleY
                val dest = RectF(tx, ty, tx + tw, ty + th)
                canvas.drawBitmap(btnBmp, null, dest, null)
            }
        }

        // 10. Draw Shuffle (164, 89) and Repeat (210, 89) Buttons
        val shufBmp = WinampSkinManager.getShuffleBitmap(isShuffle, pressedBtnIdx == 6)
        if (shufBmp != null) {
            val dest = RectF(164f * scaleX, 89f * scaleY, 210f * scaleX, 104f * scaleY)
            canvas.drawBitmap(shufBmp, null, dest, null)
        }

        val repBmp = WinampSkinManager.getRepeatBitmap(isRepeat, pressedBtnIdx == 7)
        if (repBmp != null) {
            val dest = RectF(210f * scaleX, 89f * scaleY, 238f * scaleX, 104f * scaleY)
            canvas.drawBitmap(repBmp, null, dest, null)
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
                // Seeker Dragging (x: 16..264, y: 68..84)
                if (touchY in 68f..84f && touchX in 16f..264f) {
                    val ratio = ((touchX - 16f) / 219f).coerceIn(0f, 1f)
                    progressRatio = ratio
                    onSeekChangeListener?.invoke(ratio)
                    return true
                }
                // Volume Dragging (x: 107..175, y: 52..70)
                if (touchY in 52f..70f && touchX in 107f..175f) {
                    val ratio = ((touchX - 107f) / 54f).coerceIn(0f, 1f)
                    volumeRatio = ratio
                    onVolumeChangeListener?.invoke(ratio)
                    return true
                }
                // Balance Dragging (x: 177..215, y: 52..70)
                if (touchY in 52f..70f && touchX in 177f..215f) {
                    val ratio = ((touchX - 177f) / 24f).coerceIn(0f, 1f)
                    balanceRatio = ratio
                    onBalanceChangeListener?.invoke(ratio)
                    return true
                }

                // Pressed states
                if (touchY in 85f..106f) {
                    pressedBtnIdx = when {
                        touchX in 16f..38f -> 0
                        touchX in 39f..61f -> 1
                        touchX in 62f..84f -> 2
                        touchX in 85f..107f -> 3
                        touchX in 108f..130f -> 4
                        touchX in 136f..158f -> 5
                        touchX in 164f..210f -> 6
                        touchX in 210f..238f -> 7
                        else -> -1
                    }
                    postInvalidate()
                }
            }

            MotionEvent.ACTION_UP -> {
                pressedBtnIdx = -1
                postInvalidate()

                // Transport buttons at y: 85..106
                if (touchY in 85f..106f) {
                    when {
                        touchX in 16f..38f -> onPrevClickListener?.invoke()
                        touchX in 39f..61f -> onPlayClickListener?.invoke()
                        touchX in 62f..84f -> onPauseClickListener?.invoke()
                        touchX in 85f..107f -> onStopClickListener?.invoke()
                        touchX in 108f..130f -> onNextClickListener?.invoke()
                        touchX in 136f..158f -> onEjectClickListener?.invoke()
                        touchX in 164f..210f -> {
                            isShuffle = !isShuffle
                            onShuffleToggleListener?.invoke()
                        }
                        touchX in 210f..238f -> {
                            isRepeat = !isRepeat
                            onRepeatToggleListener?.invoke()
                        }
                    }
                }
                // EQ / PL Toggles at y: 55..72
                if (touchY in 55f..72f) {
                    if (touchX in 219f..241f) {
                        isEqVisible = !isEqVisible
                        onEqToggleListener?.invoke()
                    } else if (touchX in 242f..265f) {
                        isPlVisible = !isPlVisible
                        onPlToggleListener?.invoke()
                    }
                }
            }
        }
        return true
    }
}
