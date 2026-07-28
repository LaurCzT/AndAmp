package com.winamp.classic.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
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

    fun getMainBackground(): Bitmap? = mainBmp
    fun getPlaylistBackground(): Bitmap? = pleditBmp
    fun getEqualizerBackground(): Bitmap? = eqmainBmp

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

    // Numbers 0-9 from NUMBERS.BMP (each digit is 9x13 px)
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
    fun getTransportButtonDrawable(context: Context, buttonIdx: Int, isPressed: Boolean): Drawable? {
        val src = cbuttonsBmp ?: return null
        // 0: Prev, 1: Play, 2: Pause, 3: Stop, 4: Next, 5: Eject
        val x = when (buttonIdx) {
            0 -> 0
            1 -> 23
            2 -> 46
            3 -> 69
            4 -> 92
            5 -> 114
            else -> 0
        }
        val w = if (buttonIdx == 5) 22 else 23
        val y = if (isPressed) 18 else 0
        val bmp = cropSprite(src, x, y, w, 18) ?: return null
        return BitmapDrawable(context.resources, bmp)
    }

    // Main seeker gold thumb handle from POSBAR.BMP
    fun getGoldSeekerThumb(context: Context): Drawable? {
        val src = posbarBmp ?: return null
        val bmp = cropSprite(src, 248, 0, 29, 10) ?: return null
        return BitmapDrawable(context.resources, bmp)
    }

    // Silver slider handle from VOLUME.BMP
    fun getSilverSliderThumb(context: Context): Drawable? {
        val src = volumeBmp ?: return null
        val bmp = cropSprite(src, 15, 422, 14, 11) ?: cropSprite(src, 0, 0, 14, 11) ?: return null
        return BitmapDrawable(context.resources, bmp)
    }
}
