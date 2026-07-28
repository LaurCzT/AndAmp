package com.winamp.classic.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View

class LedDigitView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#070D09")
    }

    private val ledGreenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00FF2A")
        typeface = Typeface.MONOSPACE
        isFakeBoldText = true
    }

    private val ledDimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#002808")
        typeface = Typeface.MONOSPACE
    }

    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00FF2A")
        style = Paint.Style.FILL
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

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()

        if (w == 0f || h == 0f) return

        // Dark green digital LED box background
        canvas.drawRect(0f, 0f, w, h, bgPaint)

        // Draw Status Icon (Play Triangle or Pause Bars) on the left
        val iconLeft = w * 0.05f
        val iconTop = h * 0.32f
        val iconSize = h * 0.36f

        if (isPlaying && !isPaused) {
            // Play Green Triangle
            val playPath = Path().apply {
                moveTo(iconLeft, iconTop)
                lineTo(iconLeft + iconSize * 0.75f, iconTop + iconSize / 2f)
                lineTo(iconLeft, iconTop + iconSize)
                close()
            }
            canvas.drawPath(playPath, iconPaint)
        } else if (isPaused) {
            // Pause Green Bars
            val barW = iconSize * 0.25f
            canvas.drawRect(iconLeft, iconTop, iconLeft + barW, iconTop + iconSize, iconPaint)
            canvas.drawRect(iconLeft + barW * 1.5f, iconTop, iconLeft + barW * 2.5f, iconTop + iconSize, iconPaint)
        }

        // Draw LED Text (88:88 background pattern + bright green active digits)
        val formattedTime = if (timeText.length == 4 && !timeText.contains(":")) {
            timeText.substring(0, 2) + ":" + timeText.substring(2)
        } else if (timeText.length < 5) {
            timeText.padStart(5, '0')
        } else {
            timeText
        }

        // Calculate size dynamically to scale perfectly without clipping
        val textX = w * 0.22f
        val maxAvailableWidth = w - textX - (w * 0.04f)

        var textSize = h * 0.50f
        ledGreenPaint.textSize = textSize
        var measuredW = ledGreenPaint.measureText("88:88")

        while (measuredW > maxAvailableWidth && textSize > 10f) {
            textSize -= 1f
            ledGreenPaint.textSize = textSize
            measuredW = ledGreenPaint.measureText("88:88")
        }

        ledDimPaint.textSize = textSize

        val textY = (h / 2f) - ((ledGreenPaint.descent() + ledGreenPaint.ascent()) / 2f)

        // 1. Draw 88:88 dim segment background
        canvas.drawText("88:88", textX, textY, ledDimPaint)

        // 2. Draw active green digits
        canvas.drawText(formattedTime, textX, textY, ledGreenPaint)
    }
}
