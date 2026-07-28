package com.winamp.classic.model

import android.net.Uri
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Track(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String = "Unknown Album",
    val durationMs: Long,
    val uri: Uri,
    val bitrateKbps: Int = 320,
    val sampleRateHz: Int = 44100,
    val isStereo: Boolean = true,
    val hasAlbumArt: Boolean = false
) : Parcelable {
    fun getFormattedDuration(): String {
        val totalSeconds = durationMs / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%d:%02d", minutes, seconds)
    }

    fun getDisplayName(index: Int): String {
        return "$index. $artist - $title (${getFormattedDuration()})"
    }
}
