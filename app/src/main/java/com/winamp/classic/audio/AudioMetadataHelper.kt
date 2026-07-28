package com.winamp.classic.audio

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.documentfile.provider.DocumentFile
import com.winamp.classic.model.Track

object AudioMetadataHelper {

    fun extractTrackMetadata(context: Context, uri: Uri): Track {
        val retriever = MediaMetadataRetriever()
        var pfd: ParcelFileDescriptor? = null
        var title: String? = null
        var artist: String? = null
        var album: String? = null
        var durationMs = 0L
        var bitrateKbps = 320
        var sampleRateHz = 44100
        var hasArt = false

        try {
            if (uri.scheme == "content") {
                pfd = context.contentResolver.openFileDescriptor(uri, "r")
                pfd?.let { retriever.setDataSource(it.fileDescriptor) }
            } else {
                retriever.setDataSource(context, uri)
            }

            title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
            album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)

            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            if (!durationStr.isNullOrEmpty()) {
                durationMs = durationStr.toLongOrNull() ?: 0L
            }

            val bitrateStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
            if (!bitrateStr.isNullOrEmpty()) {
                val b = bitrateStr.toIntOrNull() ?: 320000
                bitrateKbps = b / 1000
            }

            val sampleRateStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)
            if (!sampleRateStr.isNullOrEmpty()) {
                sampleRateHz = sampleRateStr.toIntOrNull() ?: 44100
            }

            val artBytes = retriever.embeddedPicture
            if (artBytes != null && artBytes.isNotEmpty()) {
                hasArt = true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try { pfd?.close() } catch (e: Exception) {}
            try { retriever.release() } catch (e: Exception) {}
        }

        // Clean up fallback title if missing or contains SAF path prefix like primary:Music/
        if (title.isNullOrBlank() || title.contains("primary:") || title.contains("/")) {
            val file = DocumentFile.fromSingleUri(context, uri)
            var name = file?.name ?: uri.lastPathSegment ?: "Track"
            if (name.contains("/")) {
                name = name.substringAfterLast("/")
            }
            if (name.contains(":")) {
                name = name.substringAfterLast(":")
            }
            title = name.substringBeforeLast(".")
        }

        if (artist.isNullOrBlank() || artist.contains("primary:")) {
            artist = "Unknown Artist"
        }

        if (album.isNullOrBlank()) {
            album = "Unknown Album"
        }

        return Track(
            id = System.currentTimeMillis() + (0..9999).random(),
            title = title,
            artist = artist,
            album = album,
            durationMs = durationMs,
            uri = uri,
            bitrateKbps = if (bitrateKbps <= 0) 320 else bitrateKbps,
            sampleRateHz = if (sampleRateHz <= 0) 44100 else sampleRateHz,
            isStereo = true,
            hasAlbumArt = hasArt
        )
    }

    fun loadAlbumArt(context: Context, uri: Uri): Bitmap? {
        val retriever = MediaMetadataRetriever()
        var pfd: ParcelFileDescriptor? = null
        return try {
            if (uri.scheme == "content") {
                pfd = context.contentResolver.openFileDescriptor(uri, "r")
                pfd?.let { retriever.setDataSource(it.fileDescriptor) }
            } else {
                retriever.setDataSource(context, uri)
            }
            val picture = retriever.embeddedPicture
            if (picture != null && picture.isNotEmpty()) {
                BitmapFactory.decodeByteArray(picture, 0, picture.size)
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            try { pfd?.close() } catch (e: Exception) {}
            try { retriever.release() } catch (e: Exception) {}
        }
    }
}
