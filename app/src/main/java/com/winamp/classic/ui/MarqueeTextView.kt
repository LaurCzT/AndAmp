package com.winamp.classic.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.View

class MarqueeTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#070D09")
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00FF2A")
        typeface = Typeface.MONOSPACE
        isFakeBoldText = true
    }

    var text: String = "174. Oceana - Cry Cry (3:15)"
        set(value) {
            field = value
            scrollXPos = 0f
            invalidate()
        }

    private var scrollXPos = 0f
    private val handler = Handler(Looper.getMainLooper())
    private val scrollRunnable = object : Runnable {
        override fun run() {
            if (width > 0) {
                val textWidth = textPaint.measureText(text)
                if (textWidth > width) {
                    scrollXPos += 2f
                    if (scrollXPos > textWidth + width * 0.5f) {
                        scrollXPos = -width * 0.2f
                    }
                    invalidate()
                }
            }
            handler.postDelayed(this, 50)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        handler.post(scrollRunnable)
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacks(scrollRunnable)
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()

        canvas.drawRect(0f, 0f, w, h, bgPaint)

        textPaint.textSize = h * 0.65f
        val textY = (h / 2f) - ((textPaint.descent() + textPaint.ascent()) / 2f)

        canvas.save()
        canvas.clipRect(4f, 2f, w - 4f, h - 2f)
        canvas.drawText(text, 8f - scrollXPos, textY, textPaint)
        canvas.restore()
    }
}
