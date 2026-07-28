package com.winamp.classic.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.audiofx.Equalizer
import android.media.audiofx.Visualizer
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import com.winamp.classic.MainActivity
import com.winamp.classic.R
import com.winamp.classic.model.Track
import kotlin.math.sin

class AudioPlaybackService : Service() {

    private val binder = LocalBinder()
    private var mediaPlayer: MediaPlayer? = null
    private var equalizerEffect: Equalizer? = null
    private var visualizer: Visualizer? = null
    private lateinit var mediaSession: MediaSessionCompat

    lateinit var playlistManager: PlaylistManager
        private set

    var isPlaying: Boolean = false
        private set

    var currentTrack: Track? = null
        private set

    var volume: Float = 1.0f
        private set

    var balanceValue: Float = 0.0f
        private set

    var onProgressUpdateListener: ((currentMs: Long, totalMs: Long) -> Unit)? = null
    var onWaveformUpdateListener: ((ByteArray) -> Unit)? = null
    var onCompletionListener: (() -> Unit)? = null

    private val handler = Handler(Looper.getMainLooper())
    private val progressRunnable = object : Runnable {
        override fun run() {
            mediaPlayer?.let { player ->
                if (isPlaying) {
                    try {
                        val currentMs = player.currentPosition.toLong()
                        val totalMs = player.duration.toLong()
                        val finalTotalMs = if (totalMs > 0) totalMs else currentTrack?.durationMs ?: 0L
                        onProgressUpdateListener?.invoke(currentMs, finalTotalMs)
                        updateMediaSessionState(currentMs)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    handler.postDelayed(this, 200)
                }
            }
        }
    }

    private val synthVisualizerRunnable = object : Runnable {
        private var phase = 0f
        override fun run() {
            if (isPlaying && visualizer == null) {
                val waveform = ByteArray(128)
                phase += 0.15f
                for (i in waveform.indices) {
                    val valNorm = sin(phase + i * 0.1f) * 127
                    waveform[i] = valNorm.toInt().toByte()
                }
                onWaveformUpdateListener?.invoke(waveform)
                handler.postDelayed(this, 40)
            }
        }
    }

    inner class LocalBinder : Binder() {
        fun getService(): AudioPlaybackService = this@AudioPlaybackService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        playlistManager = PlaylistManager(this)
        createNotificationChannel()
        setupMediaSession()
    }

    private fun setupMediaSession() {
        mediaSession = MediaSessionCompat(this, "WinampMediaSession").apply {
            setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS)
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() { resume() }
                override fun onPause() { pause() }
                override fun onSkipToNext() { nextTrack() }
                override fun onSkipToPrevious() { previousTrack() }
                override fun onStop() { stop() }
                override fun onSeekTo(pos: Long) { this@AudioPlaybackService.seekTo(pos) }
            })
            isActive = true
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.action?.let { action ->
            when (action) {
                ACTION_PLAY -> resume()
                ACTION_PAUSE -> pause()
                ACTION_PLAY_PAUSE -> if (isPlaying) pause() else resume()
                ACTION_NEXT -> nextTrack()
                ACTION_PREVIOUS -> previousTrack()
                ACTION_STOP -> stop()
            }
        }
        return START_STICKY
    }

    fun playTrack(track: Track) {
        currentTrack = track
        stopPlayerOnly()

        try {
            if (track.uri.toString().isNotEmpty()) {
                mediaPlayer = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .build()
                    )
                    if (track.uri.scheme == "content") {
                        val pfd = applicationContext.contentResolver.openFileDescriptor(track.uri, "r")
                        if (pfd != null) {
                            setDataSource(pfd.fileDescriptor)
                            pfd.close()
                        } else {
                            setDataSource(applicationContext, track.uri)
                        }
                    } else {
                        setDataSource(applicationContext, track.uri)
                    }
                    prepare()
                }
            } else {
                mediaPlayer = MediaPlayer()
            }

            mediaPlayer?.setOnCompletionListener {
                isPlaying = false
                handler.removeCallbacks(progressRunnable)
                updateMediaSessionState(0)
                onCompletionListener?.invoke()
            }

            updateVolumeBalance()
            setupEqualizer()
            setupVisualizer()

            mediaPlayer?.start()
            isPlaying = true

            updateMediaSessionMetadata(track)
            updateMediaSessionState(0)

            handler.post(progressRunnable)
            handler.post(synthVisualizerRunnable)
            startForegroundNotification(track)
        } catch (e: Exception) {
            e.printStackTrace()
            isPlaying = true
            handler.post(progressRunnable)
            handler.post(synthVisualizerRunnable)
        }
    }

    fun resume() {
        mediaPlayer?.let {
            if (!it.isPlaying) {
                it.start()
                isPlaying = true
                currentTrack?.let { tr -> startForegroundNotification(tr) }
                handler.post(progressRunnable)
                handler.post(synthVisualizerRunnable)
            }
        } ?: run {
            currentTrack?.let { playTrack(it) } ?: run {
                val tr = playlistManager.getCurrentTrack()
                tr?.let { playTrack(it) }
            }
        }
    }

    fun pause() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
            }
        }
        isPlaying = false
        currentTrack?.let { updateMediaSessionMetadata(it) }
        updateMediaSessionState(mediaPlayer?.currentPosition?.toLong() ?: 0L)
        currentTrack?.let { startForegroundNotification(it) }
        handler.removeCallbacks(progressRunnable)
        handler.removeCallbacks(synthVisualizerRunnable)
    }

    fun stop() {
        stopPlayerOnly()
        currentTrack = null
        isPlaying = false
        updateMediaSessionState(0)
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun stopPlayerOnly() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        mediaPlayer = null
        releaseEqualizer()
        releaseVisualizer()
        handler.removeCallbacks(progressRunnable)
        handler.removeCallbacks(synthVisualizerRunnable)
    }

    fun nextTrack() {
        val next = playlistManager.nextTrack()
        next?.let { playTrack(it) }
    }

    fun previousTrack() {
        val prev = playlistManager.previousTrack()
        prev?.let { playTrack(it) }
    }

    fun seekTo(positionMs: Long) {
        try {
            mediaPlayer?.seekTo(positionMs.toInt())
            updateMediaSessionState(positionMs)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun setVolume(vol: Float) {
        volume = vol.coerceIn(0f, 1f)
        updateVolumeBalance()
    }

    fun setBalance(bal: Float) {
        balanceValue = bal.coerceIn(-1f, 1f)
        updateVolumeBalance()
    }

    fun setBandLevel(bandIndex: Int, levelDb: Int) {
        try {
            equalizerEffect?.let { eq ->
                if (bandIndex in 0 until eq.numberOfBands) {
                    val millibels = (levelDb * 100).toShort()
                    eq.setBandLevel(bandIndex.toShort(), millibels)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun setEqEnabled(enabled: Boolean) {
        try {
            equalizerEffect?.enabled = enabled
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateVolumeBalance() {
        val leftVol = volume * (if (balanceValue > 0) 1f - balanceValue else 1f)
        val rightVol = volume * (if (balanceValue < 0) 1f + balanceValue else 1f)
        try {
            mediaPlayer?.setVolume(leftVol, rightVol)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setupEqualizer() {
        releaseEqualizer()
        val player = mediaPlayer ?: return
        try {
            val audioSessionId = player.audioSessionId
            if (audioSessionId != 0) {
                equalizerEffect = Equalizer(0, audioSessionId).apply {
                    enabled = true
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            equalizerEffect = null
        }
    }

    private fun releaseEqualizer() {
        try {
            equalizerEffect?.enabled = false
            equalizerEffect?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        equalizerEffect = null
    }

    private fun setupVisualizer() {
        releaseVisualizer()
        val player = mediaPlayer ?: return
        try {
            val audioSessionId = player.audioSessionId
            if (audioSessionId != 0) {
                visualizer = Visualizer(audioSessionId).apply {
                    captureSize = Visualizer.getCaptureSizeRange()[0]
                    setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                        override fun onWaveFormDataCapture(
                            viz: Visualizer?,
                            waveform: ByteArray?,
                            samplingRate: Int
                        ) {
                            waveform?.let { onWaveformUpdateListener?.invoke(it) }
                        }

                        override fun onFftDataCapture(
                            viz: Visualizer?,
                            fft: ByteArray?,
                            samplingRate: Int
                        ) {
                        }
                    }, Visualizer.getMaxCaptureRate() / 2, true, false)
                    enabled = true
                }
            }
        } catch (e: Exception) {
            visualizer = null
        }
    }

    private fun releaseVisualizer() {
        try {
            visualizer?.enabled = false
            visualizer?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        visualizer = null
    }

    private fun updateMediaSessionMetadata(track: Track) {
        val artBitmap = AudioMetadataHelper.loadAlbumArt(this, track.uri)
        val metadataBuilder = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, track.title)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, track.artist)
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, track.album)
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, track.durationMs)

        if (artBitmap != null) {
            metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, artBitmap)
        }
        mediaSession.setMetadata(metadataBuilder.build())
    }

    private fun updateMediaSessionState(positionMs: Long) {
        val state = if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
        val playbackState = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_PLAY_PAUSE or
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                PlaybackStateCompat.ACTION_SEEK_TO or
                PlaybackStateCompat.ACTION_STOP
            )
            .setState(state, positionMs, 1.0f)
            .build()
        mediaSession.setPlaybackState(playbackState)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Winamp Playback",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun startForegroundNotification(track: Track) {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val prevIntent = PendingIntent.getService(
            this, 1, Intent(this, AudioPlaybackService::class.java).apply { action = ACTION_PREVIOUS },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val playPauseIntent = PendingIntent.getService(
            this, 2, Intent(this, AudioPlaybackService::class.java).apply { action = ACTION_PLAY_PAUSE },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val nextIntent = PendingIntent.getService(
            this, 3, Intent(this, AudioPlaybackService::class.java).apply { action = ACTION_NEXT },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playPauseIcon = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        val playPauseTitle = if (isPlaying) "Pause" else "Play"

        val artBitmap: Bitmap? = AudioMetadataHelper.loadAlbumArt(this, track.uri)

        val mediaStyle = androidx.media.app.NotificationCompat.MediaStyle()
            .setMediaSession(mediaSession.sessionToken)
            .setShowActionsInCompactView(0, 1, 2)

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(track.title)
            .setContentText(track.artist)
            .setSubText(track.album)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setLargeIcon(artBitmap)
            .setContentIntent(contentPendingIntent)
            .setStyle(mediaStyle)
            .setOngoing(isPlaying)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(android.R.drawable.ic_media_previous, "Previous", prevIntent)
            .addAction(playPauseIcon, playPauseTitle, playPauseIntent)
            .addAction(android.R.drawable.ic_media_next, "Next", nextIntent)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        stop()
        mediaSession.release()
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "winamp_playback_channel"
        const val NOTIFICATION_ID = 1997

        const val ACTION_PLAY = "com.winamp.classic.ACTION_PLAY"
        const val ACTION_PAUSE = "com.winamp.classic.ACTION_PAUSE"
        const val ACTION_PLAY_PAUSE = "com.winamp.classic.ACTION_PLAY_PAUSE"
        const val ACTION_NEXT = "com.winamp.classic.ACTION_NEXT"
        const val ACTION_PREVIOUS = "com.winamp.classic.ACTION_PREVIOUS"
        const val ACTION_STOP = "com.winamp.classic.ACTION_STOP"
    }
}
