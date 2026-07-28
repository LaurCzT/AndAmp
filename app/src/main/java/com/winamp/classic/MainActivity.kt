package com.winamp.classic

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.view.View
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.winamp.classic.audio.AudioMetadataHelper
import com.winamp.classic.audio.AudioPlaybackService
import com.winamp.classic.audio.PlaylistManager
import com.winamp.classic.databinding.ActivityMainBinding
import com.winamp.classic.model.Track
import com.winamp.classic.ui.WinampSkinManager

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var playlistManager: PlaylistManager

    private var playbackService: AudioPlaybackService? = null
    private var isBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as AudioPlaybackService.LocalBinder
            val srv = binder.getService()
            playbackService = srv
            isBound = true

            playlistManager = srv.playlistManager
            setupServiceListeners()

            val current = srv.currentTrack ?: playlistManager.getCurrentTrack()
            if (current != null) {
                updateTrackDisplay(current, playlistManager.currentIndex)
                if (srv.isPlaying) {
                    binding.winampMainCanvasPlayer.isPlaying = true
                    binding.winampMainCanvasPlayer.isPaused = false
                }
            } else {
                updateEmptyDisplay()
            }
            updatePlaylistState()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            playbackService = null
            isBound = false
        }
    }

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            for (uri in uris) {
                try {
                    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            playlistManager.addTracksFromUris(uris)
            updatePlaylistState()
            Toast.makeText(this, "Added ${uris.size} tracks to Winamp", Toast.LENGTH_SHORT).show()
        }
    }

    private val folderPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { folderUri: Uri? ->
        folderUri?.let { uri ->
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            Toast.makeText(this, "Scanning folder for music...", Toast.LENGTH_SHORT).show()
            val found = playlistManager.scanFolderForAudio(uri)
            if (found.isNotEmpty()) {
                playlistManager.addTracks(found)
                updatePlaylistState()
                Toast.makeText(this, "Found & added ${found.size} tracks from folder", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "No supported audio files found in selected folder", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val loadM3uLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { m3uUri: Uri? ->
        m3uUri?.let { uri ->
            val imported = playlistManager.importFromM3u(uri)
            if (imported.isNotEmpty()) {
                playlistManager.addTracks(imported)
                updatePlaylistState()
                Toast.makeText(this, "Loaded ${imported.size} tracks from playlist file", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Failed to parse playlist file", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val saveM3uLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("audio/x-mpegurl")
    ) { destUri: Uri? ->
        destUri?.let { uri ->
            val success = playlistManager.exportToM3u(uri)
            if (success) {
                Toast.makeText(this, "Playlist saved as .m3u successfully", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Error saving playlist", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.entries.all { it.value }
        if (granted) {
            Toast.makeText(this, "Audio permissions granted", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        WinampSkinManager.loadSkin(this)
        playlistManager = PlaylistManager(this)

        setupCanvasPlayerControls()
        setupCanvasPlaylistControls()

        checkPermissions()
        startAndBindService()
        updateEmptyDisplay()
    }

    private fun setupCanvasPlayerControls() {
        binding.winampMainCanvasPlayer.apply {
            onPlayClickListener = {
                val track = playlistManager.getCurrentTrack()
                if (track != null) {
                    if (playbackService?.currentTrack?.id == track.id && playbackService?.isPlaying == false) {
                        playbackService?.resume()
                    } else {
                        playbackService?.playTrack(track)
                    }
                    updateTrackDisplay(track, playlistManager.currentIndex)
                } else {
                    Toast.makeText(this@MainActivity, "Playlist is empty. Add music files first!", Toast.LENGTH_SHORT).show()
                }
            }

            onPauseClickListener = {
                playbackService?.pause()
                isPlaying = false
                isPaused = true
            }

            onStopClickListener = {
                playbackService?.stop()
                isPlaying = false
                isPaused = false
                progressRatio = 0f
                timeText = "00:00"
            }

            onNextClickListener = {
                val track = playlistManager.nextTrack()
                if (track != null) {
                    updateTrackDisplay(track, playlistManager.currentIndex)
                    playbackService?.playTrack(track)
                }
            }

            onPrevClickListener = {
                val track = playlistManager.previousTrack()
                if (track != null) {
                    updateTrackDisplay(track, playlistManager.currentIndex)
                    playbackService?.playTrack(track)
                }
            }

            onEjectClickListener = { showAddPopupMenu(this) }
            onShuffleToggleListener = {
                playlistManager.isShuffle = isShuffle
                Toast.makeText(this@MainActivity, "Shuffle: ${if (isShuffle) "ON" else "OFF"}", Toast.LENGTH_SHORT).show()
            }
            onRepeatToggleListener = {
                playlistManager.isRepeat = isRepeat
                Toast.makeText(this@MainActivity, "Repeat: ${if (isRepeat) "ON" else "OFF"}", Toast.LENGTH_SHORT).show()
            }

            onEqToggleListener = {
                val vis = binding.winampEqualizerWindow.visibility
                binding.winampEqualizerWindow.visibility = if (vis == View.VISIBLE) View.GONE else View.VISIBLE
            }

            onPlToggleListener = {
                val vis = binding.winampPlaylistCanvas.visibility
                binding.winampPlaylistCanvas.visibility = if (vis == View.VISIBLE) View.GONE else View.VISIBLE
            }

            onVolumeChangeListener = { volRatio ->
                playbackService?.setVolume(volRatio)
            }

            onBalanceChangeListener = { balRatio ->
                val bal = (balRatio - 0.5f) * 2f
                playbackService?.setBalance(bal)
            }

            onSeekChangeListener = { ratio ->
                playbackService?.currentTrack?.let { track ->
                    val targetMs = (ratio * track.durationMs).toLong()
                    playbackService?.seekTo(targetMs)
                }
            }
        }
    }

    private fun setupCanvasPlaylistControls() {
        binding.winampPlaylistCanvas.apply {
            onAddClickListener = { showAddPopupMenu(this) }

            onRemClickListener = {
                val currIndex = playlistManager.currentIndex
                if (currIndex in playlistManager.tracks.indices) {
                    val removedTrack = playlistManager.tracks[currIndex]
                    playlistManager.removeTrack(currIndex)
                    updatePlaylistState()
                    Toast.makeText(this@MainActivity, "Removed: ${removedTrack.title}", Toast.LENGTH_SHORT).show()
                }
            }

            onSelClickListener = { showSelPopupMenu(this) }
            onMiscClickListener = { showMiscPopupMenu(this) }
            onListOptsClickListener = { showListOptsPopupMenu(this) }

            onTrackSelectedListener = { clickedIdx ->
                val track = playlistManager.selectTrack(clickedIdx)
                track?.let {
                    updateTrackDisplay(it, clickedIdx)
                    playbackService?.playTrack(it)
                }
            }
        }

        playlistManager.onPlaylistChangedListener = {
            updatePlaylistState()
        }
    }

    private fun showAddPopupMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menu.add("File")
        popup.menu.add("Folder")

        popup.setOnMenuItemClickListener { item ->
            when (item.title) {
                "File" -> filePickerLauncher.launch(arrayOf(
                    "audio/*", "audio/mpeg", "audio/aac", "audio/mp4",
                    "audio/flac", "audio/wav", "audio/x-wav", "audio/ogg", "audio/vorbis"
                ))
                "Folder" -> folderPickerLauncher.launch(null)
            }
            true
        }
        popup.show()
    }

    private fun showSelPopupMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menu.add("All")
        popup.menu.add("Current")

        popup.setOnMenuItemClickListener { item ->
            when (item.title) {
                "All" -> {
                    Toast.makeText(this, "Selected ${playlistManager.tracks.size} tracks", Toast.LENGTH_SHORT).show()
                }
                "Current" -> {
                    val curr = playlistManager.currentIndex
                    if (curr in playlistManager.tracks.indices) {
                        binding.winampPlaylistCanvas.selectedIndex = curr
                    }
                }
            }
            true
        }
        popup.show()
    }

    private fun showMiscPopupMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menu.add("File Info")
        popup.menu.add("Sort by Title")

        popup.setOnMenuItemClickListener { item ->
            when (item.title) {
                "File Info" -> showFileInfoDialog()
                "Sort by Title" -> {
                    playlistManager.sortTracksByTitle()
                    updatePlaylistState()
                    Toast.makeText(this, "Playlist sorted alphabetically", Toast.LENGTH_SHORT).show()
                }
            }
            true
        }
        popup.show()
    }

    private fun showListOptsPopupMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menu.add("Save Playlist (.m3u)")
        popup.menu.add("Clear Playlist")
        popup.menu.add("Load Playlist (.m3u)")

        popup.setOnMenuItemClickListener { item ->
            when (item.title) {
                "Save Playlist (.m3u)" -> {
                    if (playlistManager.tracks.isEmpty()) {
                        Toast.makeText(this, "Playlist is empty!", Toast.LENGTH_SHORT).show()
                    } else {
                        saveM3uLauncher.launch("Winamp_Playlist.m3u")
                    }
                }
                "Clear Playlist" -> {
                    playlistManager.clearPlaylist()
                    playbackService?.stop()
                    updatePlaylistState()
                    Toast.makeText(this, "Playlist cleared", Toast.LENGTH_SHORT).show()
                }
                "Load Playlist (.m3u)" -> {
                    loadM3uLauncher.launch(arrayOf("audio/x-mpegurl", "audio/mpegurl", "*/*"))
                }
            }
            true
        }
        popup.show()
    }

    private fun showFileInfoDialog() {
        val track = playlistManager.getCurrentTrack()
        if (track == null) {
            Toast.makeText(this, "No track currently loaded", Toast.LENGTH_SHORT).show()
            return
        }

        val infoMsg = """
            Title: ${track.title}
            Artist: ${track.artist}
            Album: ${track.album}
            Duration: ${track.getFormattedDuration()}
            Bitrate: ${track.bitrateKbps} kbps
            Sample Rate: ${track.sampleRateHz} Hz
            Path/URI: ${track.uri}
        """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle("WINAMP - Track Info")
            .setMessage(infoMsg)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun updatePlaylistState() {
        binding.winampPlaylistCanvas.updateTracks(playlistManager.tracks, playlistManager.currentIndex)
        binding.winampPlaylistCanvas.totalTimeText = "${playlistManager.getTotalDurationFormatted()}"

        val currentTrack = playlistManager.getCurrentTrack()
        if (currentTrack != null) {
            updateTrackDisplay(currentTrack, playlistManager.currentIndex)
        } else {
            updateEmptyDisplay()
        }
    }

    private fun updateTrackDisplay(track: Track, index: Int) {
        val displayName = track.getDisplayName(index + 1)
        binding.winampMainCanvasPlayer.marqueeText = displayName
        binding.winampMainCanvasPlayer.bitrateText = "${track.bitrateKbps} kbps"
        binding.winampMainCanvasPlayer.sampleRateText = "${track.sampleRateHz / 1000} kHz"
        binding.winampMainCanvasPlayer.isStereo = track.isStereo

        val artBitmap = AudioMetadataHelper.loadAlbumArt(this, track.uri)
        binding.winampAlbumArtView.setAlbumArt(artBitmap)
    }

    private fun updateEmptyDisplay() {
        binding.winampMainCanvasPlayer.marqueeText = "WINAMP 5.662 (NO TRACK LOADED)"
        binding.winampMainCanvasPlayer.bitrateText = "---"
        binding.winampMainCanvasPlayer.sampleRateText = "--"
        binding.winampMainCanvasPlayer.isStereo = true
        binding.winampMainCanvasPlayer.timeText = "00:00"
        binding.winampMainCanvasPlayer.isPlaying = false
        binding.winampMainCanvasPlayer.isPaused = false
        binding.winampMainCanvasPlayer.progressRatio = 0f
        binding.winampAlbumArtView.setAlbumArt(null)
    }

    private fun setupServiceListeners() {
        playbackService?.onProgressUpdateListener = { currentMs, totalMs ->
            if (totalMs > 0) {
                binding.winampMainCanvasPlayer.progressRatio = currentMs.toFloat() / totalMs
            }
            val seconds = (currentMs / 1000) % 60
            val minutes = (currentMs / 1000) / 60
            binding.winampMainCanvasPlayer.timeText = String.format("%02d:%02d", minutes, seconds)
            binding.winampMainCanvasPlayer.isPlaying = true
            binding.winampMainCanvasPlayer.isPaused = false
        }

        playbackService?.onWaveformUpdateListener = { bytes ->
            binding.milkdropVisualizerView.updateWaveform(bytes)
        }

        playbackService?.onCompletionListener = {
            if (playlistManager.isRepeat) {
                playbackService?.currentTrack?.let { playbackService?.playTrack(it) }
            } else {
                val nextTrk = playlistManager.nextTrack()
                if (nextTrk != null) {
                    updateTrackDisplay(nextTrk, playlistManager.currentIndex)
                    playbackService?.playTrack(nextTrk)
                }
            }
        }
    }

    private fun checkPermissions() {
        val perms = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.READ_MEDIA_AUDIO)
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            perms.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        perms.add(Manifest.permission.RECORD_AUDIO)

        val ungranted = perms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (ungranted.isNotEmpty()) {
            permissionLauncher.launch(ungranted.toTypedArray())
        }
    }

    private fun startAndBindService() {
        val intent = Intent(this, AudioPlaybackService::class.java)
        startService(intent)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    override fun onDestroy() {
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
        super.onDestroy()
    }
}
