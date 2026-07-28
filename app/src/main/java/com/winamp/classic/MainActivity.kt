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
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.winamp.classic.audio.AudioMetadataHelper
import com.winamp.classic.audio.AudioPlaybackService
import com.winamp.classic.audio.PlaylistManager
import com.winamp.classic.databinding.ActivityMainBinding
import com.winamp.classic.model.Track
import com.winamp.classic.ui.PlaylistAdapter
import com.winamp.classic.ui.WinampSkinManager

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var playlistManager: PlaylistManager
    private lateinit var playlistAdapter: PlaylistAdapter

    private var playbackService: AudioPlaybackService? = null
    private var isBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as AudioPlaybackService.LocalBinder
            val srv = binder.getService()
            playbackService = srv
            isBound = true

            playlistManager = srv.playlistManager
            setupPlaylistAdapter()

            setupServiceListeners()

            val current = srv.currentTrack ?: playlistManager.getCurrentTrack()
            if (current != null) {
                updateTrackDisplay(current, playlistManager.currentIndex)
                if (srv.isPlaying) {
                    binding.ledDigitView.isPlaying = true
                    binding.ledDigitView.isPaused = false
                }
            } else {
                updateEmptyDisplay()
            }
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

        // Load classic Winamp skin BMP spritesheets
        WinampSkinManager.loadSkin(this)

        playlistManager = PlaylistManager(this)
        setupPlaylistAdapter()
        setupUIControls()
        applySkinDrawables()
        checkPermissions()
        startAndBindService()
        updateEmptyDisplay()
    }

    private fun applySkinDrawables() {
        // Apply BMP thumbs to seekbars
        val goldThumb = WinampSkinManager.getGoldSeekerThumb(this)
        if (goldThumb != null) {
            binding.seekProgress.thumb = goldThumb
        }

        val silverThumb = WinampSkinManager.getSilverSliderThumb(this)
        if (silverThumb != null) {
            binding.seekVolume.thumb = silverThumb
            binding.seekBalance.thumb = silverThumb
        }

        // Apply transport button BMP drawables
        val prevDr = WinampSkinManager.getTransportButtonDrawable(this, 0, false)
        if (prevDr != null) binding.btnPrev.background = prevDr

        val playDr = WinampSkinManager.getTransportButtonDrawable(this, 1, false)
        if (playDr != null) binding.btnPlay.background = playDr

        val pauseDr = WinampSkinManager.getTransportButtonDrawable(this, 2, false)
        if (pauseDr != null) binding.btnPause.background = pauseDr

        val stopDr = WinampSkinManager.getTransportButtonDrawable(this, 3, false)
        if (stopDr != null) binding.btnStop.background = stopDr

        val nextDr = WinampSkinManager.getTransportButtonDrawable(this, 4, false)
        if (nextDr != null) binding.btnNext.background = nextDr

        val ejectDr = WinampSkinManager.getTransportButtonDrawable(this, 5, false)
        if (ejectDr != null) binding.btnEject.background = ejectDr
    }

    private fun setupPlaylistAdapter() {
        playlistAdapter = PlaylistAdapter(
            tracks = playlistManager.tracks,
            selectedIndex = playlistManager.currentIndex
        ) { clickedIndex ->
            val track = playlistManager.selectTrack(clickedIndex)
            track?.let {
                updateTrackDisplay(it, clickedIndex)
                playbackService?.playTrack(it)
            }
        }

        binding.rvPlaylist.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = playlistAdapter
        }

        playlistManager.onPlaylistChangedListener = {
            updatePlaylistState()
        }
    }

    private fun setupUIControls() {
        // Main Transport Controls
        binding.btnPlay.setOnClickListener {
            val track = playlistManager.getCurrentTrack()
            if (track != null) {
                if (playbackService?.currentTrack?.id == track.id && playbackService?.isPlaying == false) {
                    playbackService?.resume()
                } else {
                    playbackService?.playTrack(track)
                }
                updateTrackDisplay(track, playlistManager.currentIndex)
            } else {
                Toast.makeText(this, "Playlist is empty. Add music files first!", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnPause.setOnClickListener {
            playbackService?.pause()
            binding.ledDigitView.isPlaying = false
            binding.ledDigitView.isPaused = true
        }

        binding.btnStop.setOnClickListener {
            playbackService?.stop()
            binding.ledDigitView.isPlaying = false
            binding.ledDigitView.isPaused = false
            binding.seekProgress.progress = 0
            binding.ledDigitView.timeText = "00:00"
        }

        binding.btnNext.setOnClickListener {
            val track = playlistManager.nextTrack()
            if (track != null) {
                playlistAdapter.setSelectedIndex(playlistManager.currentIndex)
                binding.rvPlaylist.scrollToPosition(playlistManager.currentIndex)
                updateTrackDisplay(track, playlistManager.currentIndex)
                playbackService?.playTrack(track)
            }
        }

        binding.btnPrev.setOnClickListener {
            val track = playlistManager.previousTrack()
            if (track != null) {
                playlistAdapter.setSelectedIndex(playlistManager.currentIndex)
                binding.rvPlaylist.scrollToPosition(playlistManager.currentIndex)
                updateTrackDisplay(track, playlistManager.currentIndex)
                playbackService?.playTrack(track)
            }
        }

        binding.btnEject.setOnClickListener { showAddPopupMenu(it) }

        // Playlist Actions
        binding.btnAdd.setOnClickListener { showAddPopupMenu(it) }

        binding.btnRem.setOnClickListener {
            val currIndex = playlistManager.currentIndex
            if (currIndex in playlistManager.tracks.indices) {
                val removedTrack = playlistManager.tracks[currIndex]
                playlistManager.removeTrack(currIndex)
                updatePlaylistState()
                Toast.makeText(this, "Removed: ${removedTrack.title}", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "No track selected to remove", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnSel.setOnClickListener { showSelPopupMenu(it) }
        binding.btnMisc.setOnClickListener { showMiscPopupMenu(it) }
        binding.btnListOpts.setOnClickListener { showListOptsPopupMenu(it) }

        // Window Visibility Toggles (EQ, PL, ART, VIS)
        binding.btnEqToggle.setOnClickListener {
            val vis = binding.winampEqualizerWindow.visibility
            binding.winampEqualizerWindow.visibility = if (vis == View.VISIBLE) View.GONE else View.VISIBLE
        }

        binding.btnPlToggle.setOnClickListener {
            val vis = binding.winampPlaylistWindow.visibility
            binding.winampPlaylistWindow.visibility = if (vis == View.VISIBLE) View.GONE else View.VISIBLE
        }

        binding.btnArtToggle.setOnClickListener {
            val vis = binding.winampAlbumArtWindow.visibility
            binding.winampAlbumArtWindow.visibility = if (vis == View.VISIBLE) View.GONE else View.VISIBLE
        }

        binding.btnVisToggle.setOnClickListener {
            val vis = binding.milkdropWindow.visibility
            binding.milkdropWindow.visibility = if (vis == View.VISIBLE) View.GONE else View.VISIBLE
        }

        // Equalizer Controls Listener
        binding.winampEqualizerWindow.onBandLevelChangedListener = { bandIdx, levelDb ->
            playbackService?.setBandLevel(bandIdx, levelDb)
        }

        binding.winampEqualizerWindow.onEqEnabledChangedListener = { enabled ->
            playbackService?.setEqEnabled(enabled)
        }

        // Toggles
        binding.btnShuffle.setOnClickListener {
            playlistManager.isShuffle = !playlistManager.isShuffle
            binding.btnShuffle.isSelected = playlistManager.isShuffle
            Toast.makeText(this, "Shuffle: ${if (playlistManager.isShuffle) "ON" else "OFF"}", Toast.LENGTH_SHORT).show()
        }

        binding.btnRepeat.setOnClickListener {
            playlistManager.isRepeat = !playlistManager.isRepeat
            binding.btnRepeat.isSelected = playlistManager.isRepeat
            Toast.makeText(this, "Repeat: ${if (playlistManager.isRepeat) "ON" else "OFF"}", Toast.LENGTH_SHORT).show()
        }

        // Sliders
        binding.seekVolume.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) playbackService?.setVolume(progress / 100f)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        binding.seekBalance.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val bal = (progress - 50) / 50f
                    playbackService?.setBalance(bal)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        binding.seekProgress.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    playbackService?.currentTrack?.let { track ->
                        val targetMs = (progress / 1000f * track.durationMs).toLong()
                        playbackService?.seekTo(targetMs)
                    }
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
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
                        binding.rvPlaylist.scrollToPosition(curr)
                        playlistAdapter.setSelectedIndex(curr)
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
        playlistAdapter.updateTracks(playlistManager.tracks, playlistManager.currentIndex)
        val currentTrack = playlistManager.getCurrentTrack()
        if (currentTrack != null) {
            updateTrackDisplay(currentTrack, playlistManager.currentIndex)
        } else {
            updateEmptyDisplay()
        }
    }

    private fun updateTrackDisplay(track: Track, index: Int) {
        val displayName = track.getDisplayName(index + 1)
        binding.marqueeTextView.text = displayName
        binding.tvBitrate.text = "${track.bitrateKbps} kbps"
        binding.tvSampleRate.text = "${track.sampleRateHz / 1000} kHz"
        binding.tvChannels.text = if (track.isStereo) "stereo" else "mono"
        binding.tvTotalPlaylistTime.text = "${track.getFormattedDuration()}/${playlistManager.getTotalDurationFormatted()}"

        val artBitmap = AudioMetadataHelper.loadAlbumArt(this, track.uri)
        binding.winampAlbumArtWindow.setAlbumArt(artBitmap)
    }

    private fun updateEmptyDisplay() {
        binding.marqueeTextView.text = "WINAMP 5.662 (NO TRACK LOADED)"
        binding.tvBitrate.text = "--- kbps"
        binding.tvSampleRate.text = "-- kHz"
        binding.tvChannels.text = "stereo"
        binding.ledDigitView.timeText = "00:00"
        binding.ledDigitView.isPlaying = false
        binding.ledDigitView.isPaused = false
        binding.seekProgress.progress = 0
        binding.tvTotalPlaylistTime.text = "0:00/0:00"
        binding.winampAlbumArtWindow.setAlbumArt(null)
    }

    private fun setupServiceListeners() {
        playbackService?.onProgressUpdateListener = { currentMs, totalMs ->
            if (totalMs > 0) {
                val progressRatio = (currentMs.toFloat() / totalMs) * 1000
                binding.seekProgress.progress = progressRatio.toInt()
            }
            val seconds = (currentMs / 1000) % 60
            val minutes = (currentMs / 1000) / 60
            binding.ledDigitView.timeText = String.format("%02d:%02d", minutes, seconds)
            binding.ledDigitView.isPlaying = true
            binding.ledDigitView.isPaused = false
        }

        playbackService?.onWaveformUpdateListener = { bytes ->
            binding.milkdropVisualizerView.updateWaveform(bytes)
        }

        playbackService?.onCompletionListener = {
            if (playlistManager.isRepeat) {
                playbackService?.currentTrack?.let { playbackService?.playTrack(it) }
            } else {
                binding.btnNext.performClick()
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
