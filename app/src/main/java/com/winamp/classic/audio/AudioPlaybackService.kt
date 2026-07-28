package com.winamp.classic.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import com.winamp.classic.MainActivity
import com.winamp.classic.R
import com.winamp.classic.model.Track

class AudioPlaybackService : Service() {

    private val binder = LocalBinder()
    private lateinit var mediaSession: MediaSessionCompat

    lateinit var playlistManager: PlaylistManager
        private set

    var isPlaying: Boolean = false
        private set

    var currentTrack: Track? = null
        private set

    var onMediaControlListener: ((action: String) -> Unit)? = null

    inner class LocalBinder : Binder() {
        fun getService(): AudioPlaybackService = this@AudioPlaybackService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        playlistManager = PlaylistManager(this)
        createNotificationChannel()

        mediaSession = MediaSessionCompat(this, "WinampMediaSession").apply {
            setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS)
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() { onMediaControlListener?.invoke("PLAY") }
                override fun onPause() { onMediaControlListener?.invoke("PAUSE") }
                override fun onSkipToNext() { onMediaControlListener?.invoke("NEXT") }
                override fun onSkipToPrevious() { onMediaControlListener?.invoke("PREVIOUS") }
                override fun onStop() { onMediaControlListener?.invoke("STOP") }
            })
            isActive = true
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY_PAUSE -> onMediaControlListener?.invoke("PLAY_PAUSE")
            ACTION_NEXT -> onMediaControlListener?.invoke("NEXT")
            ACTION_PREVIOUS -> onMediaControlListener?.invoke("PREVIOUS")
            ACTION_STOP -> onMediaControlListener?.invoke("STOP")
        }
        return START_STICKY
    }

    fun updateWebampState(title: String, artist: String, playing: Boolean) {
        isPlaying = playing
        val track = Track(
            id = System.currentTimeMillis(),
            title = title,
            artist = artist,
            album = "Winamp",
            durationMs = 180000L,
            uri = android.net.Uri.EMPTY
        )
        currentTrack = track

        updateMediaSessionMetadata(track)
        updateMediaSessionState()
        startForegroundNotification(track)
    }

    private fun updateMediaSessionMetadata(track: Track) {
        val metadataBuilder = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, track.title)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, track.artist)
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, track.album)
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, track.durationMs)

        mediaSession.setMetadata(metadataBuilder.build())
    }

    private fun updateMediaSessionState() {
        val state = if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
        val playbackState = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_PLAY_PAUSE or
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                PlaybackStateCompat.ACTION_STOP
            )
            .setState(state, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1.0f)
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

        val mediaStyle = androidx.media.app.NotificationCompat.MediaStyle()
            .setMediaSession(mediaSession.sessionToken)
            .setShowActionsInCompactView(0, 1, 2)

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(track.title)
            .setContentText(track.artist)
            .setSubText(track.album)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setLargeIcon(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888))
            .setContentIntent(contentPendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setStyle(mediaStyle)
            .addAction(android.R.drawable.ic_media_previous, "Previous", prevIntent)
            .addAction(playPauseIcon, if (isPlaying) "Pause" else "Play", playPauseIntent)
            .addAction(android.R.drawable.ic_media_next, "Next", nextIntent)
            .setOngoing(isPlaying)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        mediaSession.release()
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "winamp_playback_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_PLAY_PAUSE = "com.winamp.classic.ACTION_PLAY_PAUSE"
        const val ACTION_NEXT = "com.winamp.classic.ACTION_NEXT"
        const val ACTION_PREVIOUS = "com.winamp.classic.ACTION_PREVIOUS"
        const val ACTION_STOP = "com.winamp.classic.ACTION_STOP"
    }
}
