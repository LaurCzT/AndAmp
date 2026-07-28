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

    var progressRatio: Float = 0f // 0.0 to 1.0
        set(value) {
            field = value.coerceIn(0f, 1f)
            postInvalidate()
        }

    var volumeRatio: Float = 0.8f // 0.0 to 1.0
        set(value) {
            field = value.coerceIn(0f, 1f)
            postInvalidate()
        }

    var balanceRatio: Float = 0.5f // 0.0 to 1.0 (0.5 center)
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
        val mainBg = WinampSkinManager.getMainBackground(context)
        mainBg?.setBounds(0, 0, w.toInt(), h.toInt())
        mainBg?.draw(canvas)

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

        // 3. Draw Marquee Text inside (111, 27, 153, 13)
        greenTextPaint.textSize = 9f * scaleY
        val marqueeX = 111f * scaleX
        val marqueeY = 36f * scaleY
        canvas.drawText(marqueeText.take(24), marqueeX, marqueeY, greenTextPaint)

        // 4. Draw Specs: Bitrate (111, 43), kHz (156, 43)
        canvas.drawText("$bitrateText", 111f * scaleX, 52f * scaleY, greenTextPaint)
        canvas.drawText("$sampleRateText", 156f * scaleX, 52f * scaleY, greenTextPaint)

        // 5. Draw Volume Silver Slider Thumb at (107 + volumeRatio * 54, 57)
        val volThumb = WinampSkinManager.getSilverSliderThumb(context)
        if (volThumb != null) {
            val vx = (107f + volumeRatio * 54f) * scaleX
            val vy = 57f * scaleY
            val dest = RectF(vx, vy, vx + (14f * scaleX), vy + (11f * scaleY))
            val bitmap = (volThumb as? android.graphics.drawable.BitmapDrawable)?.bitmap
            if (bitmap != null) canvas.drawBitmap(bitmap, null, dest, null)
        }

        // 6. Draw Balance Silver Slider Thumb at (177 + balanceRatio * 24, 57)
        val balThumb = WinampSkinManager.getSilverSliderThumb(context)
        if (balThumb != null) {
            val bx = (177f + balanceRatio * 24f) * scaleX
            val by = 57f * scaleY
            val dest = RectF(bx, by, bx + (14f * scaleX), by + (11f * scaleY))
            val bitmap = (balThumb as? android.graphics.drawable.BitmapDrawable)?.bitmap
            if (bitmap != null) canvas.drawBitmap(bitmap, null, dest, null)
        }

        // 7. Draw EQ & PL Toggle Buttons at (219, 58) and (242, 58)
        val eqDr = WinampSkinManager.getEqToggleDrawable(context)
        if (eqDr != null) {
            eqDr.state = if (isEqVisible) intArrayOf(android.R.attr.state_selected) else intArrayOf()
            val dest = RectF(219f * scaleX, 58f * scaleY, 242f * scaleX, 70f * scaleY)
            val bitmap = (eqDr as? android.graphics.drawable.BitmapDrawable)?.bitmap
            if (bitmap != null) canvas.drawBitmap(bitmap, null, dest, null)
        }

        val plDr = WinampSkinManager.getPlToggleDrawable(context)
        if (plDr != null) {
            plDr.state = if (isPlVisible) intArrayOf(android.R.attr.state_selected) else intArrayOf()
            val dest = RectF(242f * scaleX, 58f * scaleY, 265f * scaleX, 70f * scaleY)
            val bitmap = (plDr as? android.graphics.drawable.BitmapDrawable)?.bitmap
            if (bitmap != null) canvas.drawBitmap(bitmap, null, dest, null)
        }

        // 8. Draw Main Seeker Gold Thumb at (16 + progressRatio * 219, 72)
        val seekerThumb = WinampSkinManager.getGoldSeekerThumb(context)
        if (seekerThumb != null) {
            val sx = (16f + progressRatio * 219f) * scaleX
            val sy = 72f * scaleY
            val dest = RectF(sx, sy, sx + (29f * scaleX), sy + (10f * scaleY))
            val bitmap = (seekerThumb as? android.graphics.drawable.BitmapDrawable)?.bitmap
            if (bitmap != null) canvas.drawBitmap(bitmap, null, dest, null)
        }

        // 9. Draw Transport Buttons (Prev, Play, Pause, Stop, Next, Eject) at y: 88
        val transportX = listOf(16f, 39f, 62f, 85f, 108f, 136f)
        for (i in 0..5) {
            val dr = WinampSkinManager.getTransportStateListDrawable(context, i)
            if (dr != null) {
                dr.state = if (pressedBtnIdx == i) intArrayOf(android.R.attr.state_pressed) else intArrayOf()
                val tx = transportX[i] * scaleX
                val ty = 88f * scaleY
                val tw = (if (i == 5) 22f else 23f) * scaleX
                val th = 18f * scaleY
                val dest = RectF(tx, ty, tx + tw, ty + th)
                val bitmap = (dr as? android.graphics.drawable.BitmapDrawable)?.bitmap
                if (bitmap != null) canvas.drawBitmap(bitmap, null, dest, null)
            }
        }

        // 10. Draw Shuffle (164, 89) and Repeat (210, 89) Buttons
        val shufDr = WinampSkinManager.getShuffleStateListDrawable(context)
        if (shufDr != null) {
            shufDr.state = if (pressedBtnIdx == 6) intArrayOf(android.R.attr.state_pressed) else if (isShuffle) intArrayOf(android.R.attr.state_selected) else intArrayOf()
            val dest = RectF(164f * scaleX, 89f * scaleY, 210f * scaleX, 104f * scaleY)
            val bitmap = (shufDr as? android.graphics.drawable.BitmapDrawable)?.bitmap
            if (bitmap != null) canvas.drawBitmap(bitmap, null, dest, null)
        }

        val repDr = WinampSkinManager.getRepeatStateListDrawable(context)
        if (repDr != null) {
            repDr.state = if (pressedBtnIdx == 7) intArrayOf(android.R.attr.state_pressed) else if (isRepeat) intArrayOf(android.R.attr.state_selected) else intArrayOf()
            val dest = RectF(210f * scaleX, 89f * scaleY, 238f * scaleX, 104f * scaleY)
            val bitmap = (repDr as? android.graphics.drawable.BitmapDrawable)?.bitmap
            if (bitmap != null) canvas.drawBitmap(bitmap, null, dest, null)
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
                // Seeker Dragging (x: 16..264, y: 70..84)
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
