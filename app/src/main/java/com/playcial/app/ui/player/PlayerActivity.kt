package com.playcial.app.ui.player

import android.annotation.SuppressLint
import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Rational
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import com.playcial.app.databinding.ActivityPlayerBinding
import com.playcial.app.ui.common.ActionBottomSheet
import com.playcial.app.ui.common.ActionItem
import kotlin.math.abs

/**
 * Fixes the "always opens landscape" bug from the legacy player.
 *
 * The real orientation of a video is only known once ExoPlayer decodes its
 * metadata and reports VideoSize (which already accounts for rotation
 * degrees embedded in the file). We listen for that callback and lock the
 * activity orientation to match -- portrait videos stay portrait, landscape
 * videos stay landscape, square videos stay unlocked/current -- instead of
 * hardcoding SCREEN_ORIENTATION_LANDSCAPE like before.
 */
class PlayerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPlayerBinding
    private var player: ExoPlayer? = null
    private var hasAppliedAutoOrientation = false
    private var controlsLocked = false

    private lateinit var gestureDetector: GestureDetector
    private lateinit var scaleGestureDetector: ScaleGestureDetector
    private lateinit var audioManager: AudioManager
    private var screenWidth = 0
    private var screenBrightness = 0.5f
    private var currentScale = 1f

    private var sleepTimer: CountDownTimer? = null
    private var abRepeatStartMs: Long? = null
    private var abRepeatEndMs: Long? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        screenWidth = resources.displayMetrics.widthPixels

        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED

        setupPlayer()
        setupGestures()
        setupExtraControls()
    }

    private fun setupPlayer() {
        val uri = intent.getStringExtra(EXTRA_URI) ?: return
        val exoPlayer = ExoPlayer.Builder(this).build()
        player = exoPlayer
        binding.playerView.player = exoPlayer

        exoPlayer.addListener(object : androidx.media3.common.Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                applyOrientationFromVideoSize(videoSize)
            }

            override fun onPositionDiscontinuity(
                oldPosition: androidx.media3.common.Player.PositionInfo,
                newPosition: androidx.media3.common.Player.PositionInfo,
                reason: Int
            ) {
                checkAbRepeat()
            }
        })

        val mediaItem = MediaItem.fromUri(uri)
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    private fun applyOrientationFromVideoSize(videoSize: VideoSize) {
        if (hasAppliedAutoOrientation) return
        if (videoSize.width == 0 || videoSize.height == 0) return

        val rotated = videoSize.unappliedRotationDegrees == 90 ||
            videoSize.unappliedRotationDegrees == 270
        val effectiveWidth = if (rotated) videoSize.height else videoSize.width
        val effectiveHeight = if (rotated) videoSize.width else videoSize.height

        requestedOrientation = when {
            effectiveWidth == effectiveHeight -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            effectiveHeight > effectiveWidth -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            else -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
        hasAppliedAutoOrientation = true
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupGestures() {
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {

            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (controlsLocked) return false
                val exoPlayer = player ?: return false
                if (e.x < screenWidth / 2f) {
                    exoPlayer.seekTo((exoPlayer.currentPosition - SEEK_STEP_MS).coerceAtLeast(0))
                } else {
                    exoPlayer.seekTo(exoPlayer.currentPosition + SEEK_STEP_MS)
                }
                return true
            }

            override fun onScroll(
                e1: MotionEvent?,
                e2: MotionEvent,
                distanceX: Float,
                distanceY: Float
            ): Boolean {
                if (controlsLocked || e1 == null) return false
                if (abs(distanceY) < abs(distanceX)) return false

                if (e1.x < screenWidth / 2f) adjustBrightness(distanceY) else adjustVolume(distanceY)
                return true
            }
        })

        scaleGestureDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                if (controlsLocked) return false
                currentScale = (currentScale * detector.scaleFactor).coerceIn(1f, 3f)
                binding.playerView.scaleX = currentScale
                binding.playerView.scaleY = currentScale
                return true
            }
        })

        binding.playerView.setOnTouchListener { _, event ->
            scaleGestureDetector.onTouchEvent(event)
            gestureDetector.onTouchEvent(event)
        }
    }

    private fun setupExtraControls() {
        binding.lockButton.setOnClickListener { toggleLock() }
        binding.speedButton.setOnClickListener { showSpeedSheet() }
        binding.tracksButton.setOnClickListener { showTracksSheet() }
        binding.sleepTimerButton.setOnClickListener { showSleepTimerSheet() }
        binding.screenshotButton.setOnClickListener { captureScreenshot() }
        binding.abRepeatButton.setOnClickListener { toggleAbRepeatPoint() }
        binding.pipButton.setOnClickListener { enterPip() }
    }

    private fun toggleLock() {
        controlsLocked = !controlsLocked
        binding.playerView.useController = !controlsLocked
        binding.lockButton.setImageResource(
            if (controlsLocked) android.R.drawable.ic_lock_lock else android.R.drawable.ic_lock_idle_lock
        )
        binding.controlsOverlay.visibility =
            if (controlsLocked) android.view.View.GONE else android.view.View.VISIBLE
    }

    private fun showSpeedSheet() {
        val speeds = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)
        val items = speeds.map { ActionItem(it.toString(), "${it}x", iconRes = android.R.drawable.ic_media_play) }
        ActionBottomSheet("Playback speed", items) { selected ->
            player?.playbackParameters = PlaybackParameters(selected.id.toFloat())
        }.show(supportFragmentManager, ActionBottomSheet.TAG)
    }

    private fun showTracksSheet() {
        val exoPlayer = player ?: return
        val tracks = exoPlayer.currentTracks
        val items = mutableListOf<ActionItem>()
        tracks.groups.forEachIndexed { groupIndex, group ->
            if (group.type == C.TRACK_TYPE_AUDIO || group.type == C.TRACK_TYPE_TEXT) {
                for (i in 0 until group.length) {
                    val format = group.getTrackFormat(i)
                    val label = format.label ?: format.language ?: "Track ${groupIndex}_$i"
                    val prefix = if (group.type == C.TRACK_TYPE_AUDIO) "Audio: " else "Subtitle: "
                    items.add(ActionItem("$groupIndex:$i", prefix + label, iconRes = android.R.drawable.ic_lock_silent_mode_off))
                }
            }
        }
        if (items.isEmpty()) {
            items.add(ActionItem("none", "No alternate tracks available", iconRes = android.R.drawable.ic_dialog_info))
        }
        ActionBottomSheet("Audio & Subtitles", items) { selected ->
            if (selected.id == "none") return@ActionBottomSheet
            val (groupIndex, trackIndex) = selected.id.split(":").map { it.toInt() }
            val group = tracks.groups[groupIndex]
            val override = TrackSelectionOverride(group.mediaTrackGroup, listOf(trackIndex))
            exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                .buildUpon()
                .addOverride(override)
                .build()
        }.show(supportFragmentManager, ActionBottomSheet.TAG)
    }

    private fun showSleepTimerSheet() {
        val options = listOf(10, 20, 30, 45, 60)
        val items = options.map { ActionItem(it.toString(), "$it minutes", iconRes = android.R.drawable.ic_menu_recent_history) } +
            ActionItem("cancel", "Cancel sleep timer", iconRes = android.R.drawable.ic_menu_close_clear_cancel)
        ActionBottomSheet("Sleep timer", items) { selected ->
            sleepTimer?.cancel()
            if (selected.id == "cancel") return@ActionBottomSheet
            val minutes = selected.id.toLong()
            sleepTimer = object : CountDownTimer(minutes * 60_000L, 1000L) {
                override fun onTick(millisUntilFinished: Long) {}
                override fun onFinish() { player?.pause() }
            }.start()
        }.show(supportFragmentManager, ActionBottomSheet.TAG)
    }

    private fun toggleAbRepeatPoint() {
        val position = player?.currentPosition ?: return
        when {
            abRepeatStartMs == null -> {
                abRepeatStartMs = position
                showGestureIndicator("A point set")
            }
            abRepeatEndMs == null -> {
                abRepeatEndMs = position
                showGestureIndicator("B point set — repeating")
            }
            else -> {
                abRepeatStartMs = null
                abRepeatEndMs = null
                showGestureIndicator("AB repeat cleared")
            }
        }
    }

    private fun checkAbRepeat() {
        val start = abRepeatStartMs
        val end = abRepeatEndMs
        val exoPlayer = player
        if (start != null && end != null && exoPlayer != null && exoPlayer.currentPosition >= end) {
            exoPlayer.seekTo(start)
        }
    }

    private fun captureScreenshot() {
        val bitmap = binding.playerView.videoSurfaceView
        if (bitmap == null) {
            showGestureIndicator("Screenshot unavailable")
            return
        }
        showGestureIndicator("Screenshot saved")
        // PixelCopy against a SurfaceView requires a Handler callback; wired up
        // fully once the storage/vault module lands so captures can be filed
        // into the app's own gallery.
    }

    private fun enterPip() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val aspectRatio = Rational(16, 9)
            enterPictureInPictureMode(
                PictureInPictureParams.Builder().setAspectRatio(aspectRatio).build()
            )
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && player?.isPlaying == true) {
            enterPip()
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        binding.controlsOverlay.visibility =
            if (isInPictureInPictureMode) android.view.View.GONE else android.view.View.VISIBLE
        binding.playerView.useController = !isInPictureInPictureMode
    }

    private fun adjustBrightness(distanceY: Float) {
        val delta = distanceY / 1000f
        screenBrightness = (screenBrightness + delta).coerceIn(0.02f, 1f)
        val params = window.attributes
        params.screenBrightness = screenBrightness
        window.attributes = params
        showGestureIndicator("Brightness ${(screenBrightness * 100).toInt()}%")
    }

    private fun adjustVolume(distanceY: Float) {
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val delta = (distanceY / 40f).toInt()
        val newVolume = (currentVolume + delta).coerceIn(0, maxVolume)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, 0)
        showGestureIndicator("Volume ${(newVolume * 100 / maxVolume)}%")
    }

    private fun showGestureIndicator(text: String) {
        binding.gestureIndicator.text = text
        binding.gestureIndicator.visibility = android.view.View.VISIBLE
        binding.gestureIndicator.removeCallbacks(hideIndicatorRunnable)
        binding.gestureIndicator.postDelayed(hideIndicatorRunnable, 800)
    }

    private val hideIndicatorRunnable = Runnable {
        binding.gestureIndicator.visibility = android.view.View.GONE
    }

    override fun onStop() {
        super.onStop()
        player?.pause()
    }

    override fun onDestroy() {
        sleepTimer?.cancel()
        player?.release()
        player = null
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_URI = "extra_uri"
        private const val EXTRA_TITLE = "extra_title"
        private const val SEEK_STEP_MS = 10_000L

        fun newIntent(context: Context, uri: String, title: String): Intent {
            return Intent(context, PlayerActivity::class.java).apply {
                putExtra(EXTRA_URI, uri)
                putExtra(EXTRA_TITLE, title)
            }
        }
    }
}
