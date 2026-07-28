package com.winamp.classic.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable

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

    // Windows Background Bitmaps
    fun getMainBackgroundBitmap(): Bitmap? {
        val bmp = mainBmp ?: return null
        return cropSprite(bmp, 0, 0, 275, 116) ?: bmp
    }

    fun getEqualizerBackgroundBitmap(): Bitmap? {
        val bmp = eqmainBmp ?: return null
        return cropSprite(bmp, 0, 0, 275, 116) ?: bmp
    }

    fun getPlaylistTopBarBitmap(): Bitmap? {
        val src = pleditBmp ?: return null
        return cropSprite(src, 0, 0, 275, 20)
    }

    fun getPlaylistBottomBarBitmap(): Bitmap? {
        val src = pleditBmp ?: return null
        return cropSprite(src, 0, 78, 275, 38)
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

    // Transport buttons from CBUTTONS.BMP
    fun getTransportBitmap(buttonIdx: Int, isPressed: Boolean): Bitmap? {
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
        val y = if (isPressed) 18 else 0
        val w = if (buttonIdx == 5) 22 else 23
        return cropSprite(src, x, y, w, 18)
    }

    // Shuffle & Repeat Bitmaps from SHUFREP.BMP
    fun getShuffleBitmap(isSelected: Boolean, isPressed: Boolean): Bitmap? {
        val src = shufrepBmp ?: return null
        val y = when {
            isPressed -> 30
            isSelected -> 15
            else -> 0
        }
        return cropSprite(src, 28, y, 46, 15)
    }

    fun getRepeatBitmap(isSelected: Boolean, isPressed: Boolean): Bitmap? {
        val src = shufrepBmp ?: return null
        val y = when {
            isPressed -> 30
            isSelected -> 15
            else -> 0
        }
        return cropSprite(src, 0, y, 28, 15)
    }

    // EQ & PL Toggle Bitmaps from TITLEBAR.BMP
    fun getEqToggleBitmap(isSelected: Boolean): Bitmap? {
        val src = titlebarBmp ?: return null
        val y = if (isSelected) 73 else 61
        return cropSprite(src, 0, y, 23, 12)
    }

    fun getPlToggleBitmap(isSelected: Boolean): Bitmap? {
        val src = titlebarBmp ?: return null
        val y = if (isSelected) 73 else 61
        return cropSprite(src, 23, y, 23, 12)
    }

    // Playlist Action Buttons from PLEDIT.BMP
    fun getPlaylistActionBitmap(btnIdx: Int, isPressed: Boolean): Bitmap? {
        val src = pleditBmp ?: return null
        if (!isPressed) {
            val (x, w) = when (btnIdx) {
                0 -> Pair(14, 25)   // ADD unpressed
                1 -> Pair(43, 25)   // REM unpressed
                2 -> Pair(72, 23)   // SEL unpressed
                3 -> Pair(99, 25)   // MISC unpressed
                4 -> Pair(216, 45)  // LIST OPTS unpressed
                else -> Pair(14, 25)
            }
            return cropSprite(src, x, 88, w, 18)
        } else {
            val (x, w) = when (btnIdx) {
                0 -> Pair(0, 25)     // ADD pressed
                1 -> Pair(54, 25)    // REM pressed
                2 -> Pair(104, 23)   // SEL pressed
                3 -> Pair(154, 25)   // MISC pressed
                4 -> Pair(204, 45)   // LIST OPTS pressed
                else -> Pair(0, 25)
            }
            return cropSprite(src, x, 149, w, 18) ?: cropSprite(src, x, 88, w, 18)
        }
    }

    // Equalizer Buttons from EQMAIN.BMP
    fun getEqButtonBitmap(btnIdx: Int, isSelected: Boolean): Bitmap? {
        val src = eqmainBmp ?: return null
        val (x, y, w, h, selX) = when (btnIdx) {
            0 -> Triple5(10, 119, 26, 12, 69)   // ON
            1 -> Triple5(36, 119, 32, 12, 95)   // AUTO
            2 -> Triple5(224, 164, 44, 12, 224) // PRESETS
            else -> Triple5(10, 119, 26, 12, 69)
        }
        val finalX = if (isSelected && btnIdx != 2) selX else x
        val finalY = if (isSelected && btnIdx == 2) 177 else y
        return cropSprite(src, finalX, finalY, w, h)
    }

    // EQ Slider Thumb Handle from EQMAIN.BMP
    fun getEqSliderThumbBitmap(): Bitmap? {
        val src = eqmainBmp ?: return null
        return cropSprite(src, 0, 164, 11, 11) ?: cropSprite(src, 13, 164, 14, 11)
    }

    // Main Seeker Gold Thumb Handle from POSBAR.BMP
    fun getGoldSeekerThumbBitmap(): Bitmap? {
        val src = posbarBmp ?: return null
        return cropSprite(src, 248, 0, 29, 10)
    }

    // Silver Slider Handle from VOLUME.BMP
    fun getSilverSliderThumbBitmap(): Bitmap? {
        val src = volumeBmp ?: return null
        return cropSprite(src, 15, 422, 14, 11) ?: cropSprite(src, 0, 0, 14, 11)
    }

    // Drawables for compatibility if needed
    fun getMainBackground(context: Context): Drawable? {
        val bmp = getMainBackgroundBitmap() ?: return null
        return BitmapDrawable(context.resources, bmp)
    }

    fun getPlaylistBackground(context: Context): Drawable? {
        val bmp = pleditBmp ?: return null
        return BitmapDrawable(context.resources, bmp)
    }

    private data class Triple5(val first: Int, val second: Int, val third: Int, val fourth: Int, val fifth: Int)
}
