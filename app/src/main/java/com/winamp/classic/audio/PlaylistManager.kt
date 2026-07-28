package com.winamp.classic.audio

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.winamp.classic.model.Track
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter

class PlaylistManager(private val context: Context) {

    val tracks = mutableListOf<Track>()
    var currentIndex: Int = -1
        private set

    var isShuffle: Boolean = false
    var isRepeat: Boolean = false

    var onPlaylistChangedListener: (() -> Unit)? = null
    var onTrackSelectedListener: ((Track?, Int) -> Unit)? = null

    init {
        tracks.clear()
        currentIndex = -1
    }

    fun getCurrentTrack(): Track? {
        return if (currentIndex in tracks.indices) tracks[currentIndex] else null
    }

    fun selectTrack(index: Int): Track? {
        if (index in tracks.indices) {
            currentIndex = index
            val track = tracks[currentIndex]
            onTrackSelectedListener?.invoke(track, currentIndex)
            return track
        }
        return null
    }

    fun nextTrack(): Track? {
        if (tracks.isEmpty()) return null
        if (isShuffle) {
            currentIndex = (tracks.indices).random()
        } else {
            currentIndex = (currentIndex + 1) % tracks.size
        }
        val track = tracks[currentIndex]
        onTrackSelectedListener?.invoke(track, currentIndex)
        return track
    }

    fun previousTrack(): Track? {
        if (tracks.isEmpty()) return null
        if (isShuffle) {
            currentIndex = (tracks.indices).random()
        } else {
            currentIndex = if (currentIndex - 1 < 0) tracks.size - 1 else currentIndex - 1
        }
        val track = tracks[currentIndex]
        onTrackSelectedListener?.invoke(track, currentIndex)
        return track
    }

    fun addTrackFromUri(uri: Uri) {
        val track = AudioMetadataHelper.extractTrackMetadata(context, uri)
        tracks.add(track)
        if (currentIndex == -1) {
            currentIndex = 0
        }
        onPlaylistChangedListener?.invoke()
    }

    fun addTracksFromUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        for (uri in uris) {
            val track = AudioMetadataHelper.extractTrackMetadata(context, uri)
            tracks.add(track)
        }
        if (currentIndex == -1 && tracks.isNotEmpty()) {
            currentIndex = 0
        }
        onPlaylistChangedListener?.invoke()
    }

    fun addTracks(newTracks: List<Track>) {
        if (newTracks.isEmpty()) return
        tracks.addAll(newTracks)
        if (currentIndex == -1 && tracks.isNotEmpty()) {
            currentIndex = 0
        }
        onPlaylistChangedListener?.invoke()
    }

    fun removeTrack(index: Int) {
        if (index in tracks.indices) {
            tracks.removeAt(index)
            if (tracks.isEmpty()) {
                currentIndex = -1
            } else if (currentIndex >= tracks.size) {
                currentIndex = tracks.size - 1
            }
            onPlaylistChangedListener?.invoke()
        }
    }

    fun clearPlaylist() {
        tracks.clear()
        currentIndex = -1
        onPlaylistChangedListener?.invoke()
    }

    fun sortTracksByTitle() {
        tracks.sortBy { it.title.lowercase() }
        if (tracks.isNotEmpty() && currentIndex !in tracks.indices) {
            currentIndex = 0
        }
        onPlaylistChangedListener?.invoke()
    }

    fun scanFolderForAudio(folderUri: Uri): List<Track> {
        val foundTracks = mutableListOf<Track>()
        val root = DocumentFile.fromTreeUri(context, folderUri) ?: return foundTracks
        scanDocumentDirectory(root, foundTracks)
        return foundTracks
    }

    private fun scanDocumentDirectory(dir: DocumentFile, outTracks: MutableList<Track>) {
        val files = dir.listFiles()
        for (file in files) {
            if (file.isDirectory) {
                scanDocumentDirectory(file, outTracks)
            } else {
                val name = file.name ?: ""
                val lowerName = name.lowercase()
                // Support MP3, AAC, FLAC, WAV, and Ogg Vorbis
                if (lowerName.endsWith(".mp3") ||
                    lowerName.endsWith(".aac") || lowerName.endsWith(".m4a") || lowerName.endsWith(".mp4") || lowerName.endsWith(".m4b") ||
                    lowerName.endsWith(".flac") ||
                    lowerName.endsWith(".wav") || lowerName.endsWith(".wave") ||
                    lowerName.endsWith(".ogg") || lowerName.endsWith(".oga") || lowerName.endsWith(".ogv") || lowerName.endsWith(".opus")) {

                    val track = AudioMetadataHelper.extractTrackMetadata(context, file.uri)
                    outTracks.add(track)
                }
            }
        }
    }

    fun exportToM3u(destinationUri: Uri): Boolean {
        return try {
            context.contentResolver.openOutputStream(destinationUri)?.use { outputStream ->
                OutputStreamWriter(outputStream).use { writer ->
                    writer.write("#EXTM3U\n")
                    for (track in tracks) {
                        writer.write("#EXTINF:${track.durationMs / 1000},${track.artist} - ${track.title}\n")
                        writer.write("${track.uri}\n")
                    }
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun importFromM3u(sourceUri: Uri): List<Track> {
        val imported = mutableListOf<Track>()
        try {
            context.contentResolver.openInputStream(sourceUri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        val trimmed = line!!.trim()
                        if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                            val trackUri = try { Uri.parse(trimmed) } catch (e: Exception) { Uri.EMPTY }
                            if (trackUri != Uri.EMPTY) {
                                val track = AudioMetadataHelper.extractTrackMetadata(context, trackUri)
                                imported.add(track)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return imported
    }

    fun getTotalDurationFormatted(): String {
        if (tracks.isEmpty()) return "0:00"
        var totalMs = 0L
        for (track in tracks) {
            totalMs += track.durationMs
        }
        val totalSeconds = totalMs / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }
}
