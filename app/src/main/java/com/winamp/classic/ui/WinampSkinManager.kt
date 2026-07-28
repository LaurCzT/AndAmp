package com.winamp.classic.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.StateListDrawable

object WinampSkinManager {

    private var mainBmp: Bitmap? = null
    private var cbuttonsBmp: Bitmap? = null
    private var titlebarBmp: Bitmap? = null
    private var numbersBmp: Bitmap? = null
    private var playpausBmp: Bitmap? = null
    private var posbarBmp: Bitmap? = null
    private var volumeBmp: Bitmap? = null
    private var balanceBmp: Bitmap? = null
    private var shufrepBmp: Bitmap? = null
    private var pleditBmp: Bitmap? = null
    private var eqmainBmp: Bitmap? = null
    private var monosterBmp: Bitmap? = null

    fun loadSkin(context: Context) {
        val am = context.assets
        try {
            am.open("skin/MAIN.BMP").use { mainBmp = BitmapFactory.decodeStream(it) }
            am.open("skin/CBUTTONS.BMP").use { cbuttonsBmp = BitmapFactory.decodeStream(it) }
            am.open("skin/TITLEBAR.BMP").use { titlebarBmp = BitmapFactory.decodeStream(it) }
            am.open("skin/NUMBERS.BMP").use { numbersBmp = BitmapFactory.decodeStream(it) }
            am.open("skin/PLAYPAUS.BMP").use { playpausBmp = BitmapFactory.decodeStream(it) }
            am.open("skin/POSBAR.BMP").use { posbarBmp = BitmapFactory.decodeStream(it) }
            am.open("skin/VOLUME.BMP").use { volumeBmp = BitmapFactory.decodeStream(it) }
            am.open("skin/BALANCE.BMP").use { balanceBmp = BitmapFactory.decodeStream(it) }
            am.open("skin/SHUFREP.BMP").use { shufrepBmp = BitmapFactory.decodeStream(it) }
            am.open("skin/PLEDIT.BMP").use { pleditBmp = BitmapFactory.decodeStream(it) }
            am.open("skin/EQMAIN.BMP").use { eqmainBmp = BitmapFactory.decodeStream(it) }
            am.open("skin/MONOSTER.BMP").use { monosterBmp = BitmapFactory.decodeStream(it) }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun cropSprite(src: Bitmap?, x: Int, y: Int, w: Int, h: Int): Bitmap? {
        if (src == null) return null
        return try {
            val safeX = x.coerceIn(0, src.width - 1)
            val safeY = y.coerceIn(0, src.height - 1)
            val safeW = w.coerceAtMost(src.width - safeX)
            val safeH = h.coerceAtMost(src.height - safeY)
            if (safeW <= 0 || safeH <= 0) null
            else Bitmap.createBitmap(src, safeX, safeY, safeW, safeH)
        } catch (e: Exception) {
            null
        }
    }

    fun getMainBackground(context: Context): Drawable? {
        val bmp = mainBmp ?: return null
        val sub = cropSprite(bmp, 0, 0, 275, 116) ?: bmp
        return BitmapDrawable(context.resources, sub)
    }

    fun getEqualizerBackground(context: Context): Drawable? {
        val bmp = eqmainBmp ?: return null
        val sub = cropSprite(bmp, 0, 0, 275, 116) ?: bmp
        return BitmapDrawable(context.resources, sub)
    }

    fun getPlaylistBackground(context: Context): Drawable? {
        val bmp = pleditBmp ?: return null
        return BitmapDrawable(context.resources, bmp)
    }

    fun getPlaylistTopBar(context: Context): Drawable? {
        val src = pleditBmp ?: return null
        val sub = cropSprite(src, 0, 0, 275, 20) ?: return null
        return BitmapDrawable(context.resources, sub)
    }

    fun getPlaylistBottomBar(context: Context): Drawable? {
        val src = pleditBmp ?: return null
        val sub = cropSprite(src, 0, 78, 275, 38) ?: return null
        return BitmapDrawable(context.resources, sub)
    }

    // Digital LED Digits from NUMBERS.BMP (9x13 px each)
    fun getDigitBitmap(digit: Char): Bitmap? {
        val src = numbersBmp ?: return null
        val idx = when (digit) {
            in '0'..'9' -> digit - '0'
            '-' -> 10
            else -> 11 // Blank
        }
        return cropSprite(src, idx * 9, 0, 9, 13)
    }

    // Transport buttons StateListDrawable from CBUTTONS.BMP
    fun getTransportStateListDrawable(context: Context, buttonIdx: Int): Drawable? {
        val src = cbuttonsBmp ?: return null
        val x = when (buttonIdx) {
            0 -> 0   // Prev
            1 -> 23  // Play
            2 -> 46  // Pause
            3 -> 69  // Stop
            4 -> 92  // Next
            5 -> 114 // Eject
            else -> 0
        }
        val w = if (buttonIdx == 5) 22 else 23
        val normalBmp = cropSprite(src, x, 0, w, 18) ?: return null
        val pressedBmp = cropSprite(src, x, 18, w, 18) ?: normalBmp

        val sld = StateListDrawable()
        sld.addState(intArrayOf(android.R.attr.state_pressed), BitmapDrawable(context.resources, pressedBmp))
        sld.addState(intArrayOf(), BitmapDrawable(context.resources, normalBmp))
        return sld
    }

    // Shuffle & Repeat StateListDrawables from SHUFREP.BMP
    fun getShuffleStateListDrawable(context: Context): Drawable? {
        val src = shufrepBmp ?: return null
        val normalBmp = cropSprite(src, 28, 0, 46, 15) ?: return null
        val activeBmp = cropSprite(src, 28, 15, 46, 15) ?: normalBmp
        val pressedBmp = cropSprite(src, 28, 30, 46, 15) ?: activeBmp

        val sld = StateListDrawable()
        sld.addState(intArrayOf(android.R.attr.state_selected), BitmapDrawable(context.resources, activeBmp))
        sld.addState(intArrayOf(android.R.attr.state_pressed), BitmapDrawable(context.resources, pressedBmp))
        sld.addState(intArrayOf(), BitmapDrawable(context.resources, normalBmp))
        return sld
    }

    fun getRepeatStateListDrawable(context: Context): Drawable? {
        val src = shufrepBmp ?: return null
        val normalBmp = cropSprite(src, 0, 0, 28, 15) ?: return null
        val activeBmp = cropSprite(src, 0, 15, 28, 15) ?: normalBmp
        val pressedBmp = cropSprite(src, 0, 30, 28, 15) ?: activeBmp

        val sld = StateListDrawable()
        sld.addState(intArrayOf(android.R.attr.state_selected), BitmapDrawable(context.resources, activeBmp))
        sld.addState(intArrayOf(android.R.attr.state_pressed), BitmapDrawable(context.resources, pressedBmp))
        sld.addState(intArrayOf(), BitmapDrawable(context.resources, normalBmp))
        return sld
    }

    // EQ & PL Toggle Buttons from TITLEBAR.BMP
    fun getEqToggleDrawable(context: Context): Drawable? {
        val src = titlebarBmp ?: return null
        val normalBmp = cropSprite(src, 0, 61, 23, 12) ?: return null
        val activeBmp = cropSprite(src, 0, 73, 23, 12) ?: normalBmp

        val sld = StateListDrawable()
        sld.addState(intArrayOf(android.R.attr.state_selected), BitmapDrawable(context.resources, activeBmp))
        sld.addState(intArrayOf(android.R.attr.state_pressed), BitmapDrawable(context.resources, activeBmp))
        sld.addState(intArrayOf(), BitmapDrawable(context.resources, normalBmp))
        return sld
    }

    fun getPlToggleDrawable(context: Context): Drawable? {
        val src = titlebarBmp ?: return null
        val normalBmp = cropSprite(src, 23, 61, 23, 12) ?: return null
        val activeBmp = cropSprite(src, 23, 73, 23, 12) ?: normalBmp

        val sld = StateListDrawable()
        sld.addState(intArrayOf(android.R.attr.state_selected), BitmapDrawable(context.resources, activeBmp))
        sld.addState(intArrayOf(android.R.attr.state_pressed), BitmapDrawable(context.resources, activeBmp))
        sld.addState(intArrayOf(), BitmapDrawable(context.resources, normalBmp))
        return sld
    }

    // Playlist Action Buttons from PLEDIT.BMP
    fun getPlaylistActionDrawable(context: Context, btnIdx: Int): Drawable? {
        val src = pleditBmp ?: return null
        val (x, y, w) = when (btnIdx) {
            0 -> Triple(0, 149, 22)    // ADD
            1 -> Triple(54, 149, 22)   // REM
            2 -> Triple(104, 149, 22)  // SEL
            3 -> Triple(154, 149, 22)  // MISC
            4 -> Triple(204, 149, 22)  // LIST OPTS
            else -> Triple(0, 149, 22)
        }
        val normalBmp = cropSprite(src, x, y, w, 18) ?: return null
        val pressedBmp = cropSprite(src, x + 23, y, w, 18) ?: normalBmp

        val sld = StateListDrawable()
        sld.addState(intArrayOf(android.R.attr.state_pressed), BitmapDrawable(context.resources, pressedBmp))
        sld.addState(intArrayOf(), BitmapDrawable(context.resources, normalBmp))
        return sld
    }

    // Equalizer Action Buttons from EQMAIN.BMP
    fun getEqButtonDrawable(context: Context, btnIdx: Int): Drawable? {
        val src = eqmainBmp ?: return null
        val (x, y, w, h, selX, selY) = when (btnIdx) {
            0 -> Triple6(10, 119, 26, 12, 69, 119)   // ON
            1 -> Triple6(36, 119, 32, 12, 95, 119)   // AUTO
            2 -> Triple6(224, 164, 44, 12, 224, 177) // PRESETS
            else -> Triple6(10, 119, 26, 12, 69, 119)
        }
        val normalBmp = cropSprite(src, x, y, w, h) ?: return null
        val activeBmp = cropSprite(src, selX, selY, w, h) ?: normalBmp

        val sld = StateListDrawable()
        sld.addState(intArrayOf(android.R.attr.state_selected), BitmapDrawable(context.resources, activeBmp))
        sld.addState(intArrayOf(android.R.attr.state_pressed), BitmapDrawable(context.resources, activeBmp))
        sld.addState(intArrayOf(), BitmapDrawable(context.resources, normalBmp))
        return sld
    }

    // EQ Silver Slider Thumb Handle from EQMAIN.BMP
    fun getEqSliderThumb(context: Context): Drawable? {
        val src = eqmainBmp ?: return null
        val bmp = cropSprite(src, 0, 164, 11, 11) ?: cropSprite(src, 13, 164, 14, 11) ?: return null
        return BitmapDrawable(context.resources, bmp)
    }

    // Main Seeker Gold Thumb Handle from POSBAR.BMP
    fun getGoldSeekerThumb(context: Context): Drawable? {
        val src = posbarBmp ?: return null
        val bmp = cropSprite(src, 248, 0, 29, 10) ?: return null
        return BitmapDrawable(context.resources, bmp)
    }

    // Silver Slider Handle from VOLUME.BMP
    fun getSilverSliderThumb(context: Context): Drawable? {
        val src = volumeBmp ?: return null
        val bmp = cropSprite(src, 15, 422, 14, 11) ?: cropSprite(src, 0, 0, 14, 11) ?: return null
        return BitmapDrawable(context.resources, bmp)
    }

    private data class Triple6(val first: Int, val second: Int, val third: Int, val fourth: Int, val fifth: Int, val sixth: Int)
}
