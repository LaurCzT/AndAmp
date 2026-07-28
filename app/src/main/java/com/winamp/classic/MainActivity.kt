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
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import com.winamp.classic.audio.AudioMetadataHelper
import com.winamp.classic.audio.AudioPlaybackService
import com.winamp.classic.databinding.ActivityMainBinding
import com.winamp.classic.model.Track
import java.io.InputStream

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var playbackService: AudioPlaybackService? = null
    private var isBound = false
    private var activeFilePathCallback: ValueCallback<Array<Uri>>? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as AudioPlaybackService.LocalBinder
            playbackService = binder.getService()
            isBound = true
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
            Toast.makeText(this, "Loading ${uris.size} tracks into Webamp...", Toast.LENGTH_SHORT).show()
            for (uri in uris) {
                try {
                    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                sendUriToWebamp(uri)
            }
        }
        activeFilePathCallback?.onReceiveValue(null)
        activeFilePathCallback = null
    }

    private val folderPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { folderUri: Uri? ->
        folderUri?.let { uri ->
            scanAndSendFolderToWebamp(uri)
        }
        activeFilePathCallback?.onReceiveValue(null)
        activeFilePathCallback = null
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.entries.all { it.value }
        if (granted) {
            Toast.makeText(this, "Permissions granted", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupWebView()
        checkPermissions()
        startAndBindService()
    }

    private fun setupWebView() {
        binding.webViewWebamp.apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = true
                allowContentAccess = true
                allowFileAccessFromFileURLs = true
                allowUniversalAccessFromFileURLs = true
                mediaPlaybackRequiresUserGesture = false
                cacheMode = WebSettings.LOAD_DEFAULT
            }

            webChromeClient = object : WebChromeClient() {
                override fun onShowFileChooser(
                    webView: WebView?,
                    filePathCallback: ValueCallback<Array<Uri>>?,
                    fileChooserParams: FileChooserParams?
                ): Boolean {
                    activeFilePathCallback?.onReceiveValue(null)
                    activeFilePathCallback = filePathCallback

                    showAddOptionsDialog()
                    return true
                }
            }

            webViewClient = object : WebViewClient() {}

            addJavascriptInterface(AndroidBridge(), "AndroidBridge")
            loadUrl("file:///android_asset/webamp/index.html")
        }
    }

    private fun showAddOptionsDialog() {
        val options = arrayOf("Add File(s)", "Add Folder")
        AlertDialog.Builder(this)
            .setTitle("Winamp - Add Music")
            .setItems(options) { dialog, which ->
                when (which) {
                    0 -> filePickerLauncher.launch(arrayOf(
                        "audio/*", "audio/mpeg", "audio/aac", "audio/mp4",
                        "audio/flac", "audio/wav", "audio/x-wav", "audio/ogg", "audio/vorbis"
                    ))
                    1 -> folderPickerLauncher.launch(null)
                }
            }
            .setOnCancelListener {
                activeFilePathCallback?.onReceiveValue(null)
                activeFilePathCallback = null
            }
            .show()
    }

    private fun scanAndSendFolderToWebamp(folderUri: Uri) {
        Toast.makeText(this, "Scanning folder for music...", Toast.LENGTH_SHORT).show()
        Thread {
            try {
                contentResolver.takePersistableUriPermission(folderUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            val root = DocumentFile.fromTreeUri(this, folderUri)
            if (root != null) {
                val audioFiles = mutableListOf<DocumentFile>()
                scanDirectory(root, audioFiles)

                runOnUiThread {
                    Toast.makeText(this, "Found ${audioFiles.size} music files in folder. Loading...", Toast.LENGTH_LONG).show()
                }

                for (file in audioFiles) {
                    sendUriToWebamp(file.uri)
                }
            } else {
                runOnUiThread {
                    Toast.makeText(this, "Failed to read folder", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun scanDirectory(dir: DocumentFile, outFiles: MutableList<DocumentFile>) {
        val files = dir.listFiles()
        for (file in files) {
            if (file.isDirectory) {
                scanDirectory(file, outFiles)
            } else {
                val name = file.name?.lowercase() ?: ""
                if (name.endsWith(".mp3") || name.endsWith(".aac") || name.endsWith(".m4a") ||
                    name.endsWith(".flac") || name.endsWith(".wav") || name.endsWith(".ogg") ||
                    name.endsWith(".opus")) {
                    outFiles.add(file)
                }
            }
        }
    }

    private fun sendUriToWebamp(uri: Uri) {
        Thread {
            try {
                val track = AudioMetadataHelper.extractTrackMetadata(this, uri)
                val inputStream: InputStream? = contentResolver.openInputStream(uri)
                val bytes = inputStream?.readBytes()
                inputStream?.close()

                if (bytes != null && bytes.isNotEmpty()) {
                    val base64Data = Base64.encodeToString(bytes, Base64.NO_WRAP)
                    val mimeType = contentResolver.getType(uri) ?: "audio/mp3"
                    val dataUrl = "data:$mimeType;base64,$base64Data"

                    runOnUiThread {
                        val safeTitle = track.title.replace("'", "\\'").replace("\"", "\\\"")
                        val safeArtist = track.artist.replace("'", "\\'").replace("\"", "\\\"")
                        val js = "addTrackToWebamp('$safeTitle', '$safeArtist', '$dataUrl');"
                        binding.webViewWebamp.evaluateJavascript(js, null)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }

    inner class AndroidBridge {
        @JavascriptInterface
        fun onWebampReady() {
            runOnUiThread {
                Toast.makeText(this@MainActivity, "Winamp ready!", Toast.LENGTH_SHORT).show()
            }
        }

        @JavascriptInterface
        fun onTrackChanged(title: String, artist: String) {
            runOnUiThread {
                val dummyTrack = Track(
                    id = System.currentTimeMillis(),
                    title = title,
                    artist = artist,
                    album = "Winamp",
                    durationMs = 180000L,
                    uri = Uri.EMPTY
                )
                playbackService?.playTrack(dummyTrack)
            }
        }

        @JavascriptInterface
        fun openFilePicker() {
            runOnUiThread {
                filePickerLauncher.launch(arrayOf("audio/*", "*/*"))
            }
        }

        @JavascriptInterface
        fun openFolderPicker() {
            runOnUiThread {
                folderPickerLauncher.launch(null)
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
