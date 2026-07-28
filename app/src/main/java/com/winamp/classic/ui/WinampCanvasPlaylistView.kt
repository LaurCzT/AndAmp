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
import com.winamp.classic.model.Track

class WinampCanvasPlaylistView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var onAddClickListener: (() -> Unit)? = null
    var onRemClickListener: (() -> Unit)? = null
    var onSelClickListener: (() -> Unit)? = null
    var onMiscClickListener: (() -> Unit)? = null
    var onListOptsClickListener: (() -> Unit)? = null
    var onTrackSelectedListener: ((index: Int) -> Unit)? = null

    val tracks = mutableListOf<Track>()
    var selectedIndex: Int = -1
    var totalTimeText: String = "0:00/0:00"

    private val bgBlackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
    }

    private val selectionBluePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#0000A8")
    }

    private val trackGreenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00FF2A")
        typeface = Typeface.MONOSPACE
        textSize = 12f
    }

    private val trackSelectedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = Typeface.MONOSPACE
        isFakeBoldText = true
        textSize = 12f
    }

    private var pressedBtnIdx: Int = -1

    fun updateTracks(newTracks: List<Track>, currentIdx: Int) {
        tracks.clear()
        tracks.addAll(newTracks)
        selectedIndex = currentIdx
        postInvalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        if (w == 0f || h == 0f) return

        val scaleX = w / 275f

        // 1. Draw PLEDIT.BMP Top Bar (275 x 20)
        val topBmp = WinampSkinManager.getPlaylistTopBarBitmap()
        val topY = 20f * (w / 275f)
        if (topBmp != null) {
            canvas.drawBitmap(topBmp, null, RectF(0f, 0f, w, topY), null)
        }

        // 2. Draw Middle Track List Black Container (y: 20 .. h - 38)
        val bottomY = h - (38f * (w / 275f))
        canvas.drawRect(0f, topY, w, bottomY, bgBlackPaint)

        // 3. Draw Tracks
        val itemHeight = 18f * (w / 275f)
        trackGreenPaint.textSize = 11f * (w / 275f)
        trackSelectedPaint.textSize = 11f * (w / 275f)

        for (i in tracks.indices) {
            val itemTop = topY + (i * itemHeight)
            if (itemTop + itemHeight > bottomY) break

            val track = tracks[i]
            val isSelected = i == selectedIndex

            if (isSelected) {
                canvas.drawRect(4f * scaleX, itemTop, w - (4f * scaleX), itemTop + itemHeight, selectionBluePaint)
            }

            val paint = if (isSelected) trackSelectedPaint else trackGreenPaint
            val textY = itemTop + (itemHeight * 0.75f)
            val titleText = "${i + 1}. ${track.artist} - ${track.title}"
            val durationText = track.getFormattedDuration()

            canvas.drawText(titleText.take(30), 10f * scaleX, textY, paint)
            canvas.drawText(durationText, w - (45f * scaleX), textY, paint)
        }

        // 4. Draw PLEDIT.BMP Bottom Bar (275 x 38) at bottom
        val bottomBmp = WinampSkinManager.getPlaylistBottomBarBitmap()
        if (bottomBmp != null) {
            canvas.drawBitmap(bottomBmp, null, RectF(0f, bottomY, w, h), null)
        }

        // 5. Draw Playlist Action Buttons ONLY when pressed
        val btnY = bottomY + (10f * (w / 275f))
        val btnH = 18f * (w / 275f)

        val btnXList = listOf(14f, 43f, 72f, 99f, 216f)
        val btnWList = listOf(25f, 25f, 23f, 25f, 45f)

        for (i in 0..4) {
            if (pressedBtnIdx == i) {
                val btnBmp = WinampSkinManager.getPlaylistActionBitmap(i, true)
                if (btnBmp != null) {
                    val bx = btnXList[i] * scaleX
                    val bw = btnWList[i] * scaleX
                    val dest = RectF(bx, btnY, bx + bw, btnY + btnH)
                    canvas.drawBitmap(btnBmp, null, dest, null)
                }
            }
        }

        // 6. Draw Total Time Text (130, 88)
        val timeX = 130f * scaleX
        canvas.drawText(totalTimeText, timeX, btnY + (btnH * 0.75f), trackGreenPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w == 0f || h == 0f) return super.onTouchEvent(event)

        val scaleX = w / 275f
        val touchX = event.x / scaleX
        val touchY = event.y

        val topY = 20f * (w / 275f)
        val bottomY = h - (38f * (w / 275f))
        val itemHeight = 18f * (w / 275f)

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                // Track list item click
                if (touchY in topY..bottomY) {
                    val clickedIdx = ((touchY - topY) / itemHeight).toInt()
                    if (clickedIdx in tracks.indices) {
                        selectedIndex = clickedIdx
                        onTrackSelectedListener?.invoke(clickedIdx)
                        postInvalidate()
                    }
                }
                // Action buttons at bottom bar
                if (touchY >= bottomY) {
                    pressedBtnIdx = when {
                        touchX in 12f..38f -> 0
                        touchX in 40f..66f -> 1
                        touchX in 68f..94f -> 2
                        touchX in 96f..124f -> 3
                        touchX in 210f..265f -> 4
                        else -> -1
                    }
                    postInvalidate()
                }
            }

            MotionEvent.ACTION_UP -> {
                pressedBtnIdx = -1
                postInvalidate()

                // Action buttons at bottom bar
                if (touchY >= bottomY) {
                    when {
                        touchX in 12f..38f -> onAddClickListener?.invoke()
                        touchX in 40f..66f -> onRemClickListener?.invoke()
                        touchX in 68f..94f -> onSelClickListener?.invoke()
                        touchX in 96f..124f -> onMiscClickListener?.invoke()
                        touchX in 210f..265f -> onListOptsClickListener?.invoke()
                    }
                }
            }
        }
        return true
    }
}
