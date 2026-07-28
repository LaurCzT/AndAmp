package com.winamp.classic.ui

import android.content.Context
import android.graphics.Bitmap
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.LinearLayout
import com.winamp.classic.R

class WinampAlbumArtView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val ivAlbumArt: ImageView

    init {
        orientation = VERTICAL
        LayoutInflater.from(context).inflate(R.layout.view_winamp_album_art, this, true)
        ivAlbumArt = findViewById(R.id.ivAlbumArt)
    }

    fun setAlbumArt(bitmap: Bitmap?) {
        if (bitmap != null) {
            ivAlbumArt.setImageBitmap(bitmap)
            ivAlbumArt.scaleType = ImageView.ScaleType.FIT_CENTER
        } else {
            ivAlbumArt.setImageResource(R.drawable.ic_launcher_foreground)
            ivAlbumArt.scaleType = ImageView.ScaleType.CENTER_INSIDE
        }
    }
}
