/*
 * Copyright (C) 2026 Răzvan Albu
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>
 */

package com.stario.launcher.activities.launcher.widgets.glance.extensions.media

import android.annotation.SuppressLint
import android.app.ActivityOptions
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.AnimatedVectorDrawable
import android.media.AudioAttributes
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.FloatRange
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.bumptech.glide.Glide
import com.bumptech.glide.load.MultiTransformation
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.transition.DrawableCrossFadeFactory
import com.stario.launcher.BuildConfig
import com.stario.launcher.R
import com.stario.launcher.Stario
import com.stario.launcher.activities.launcher.widgets.glance.GlanceDialogExtension
import com.stario.launcher.activities.launcher.widgets.glance.GlanceViewExtension
import com.stario.launcher.preferences.Vibrations
import com.stario.launcher.services.NotificationService
import com.stario.launcher.ui.common.glance.GlanceConstraintLayout
import com.stario.launcher.ui.common.media.SliderComposeView
import com.stario.launcher.ui.utils.animation.Animation
import com.stario.launcher.utils.Utils
import com.stario.launcher.utils.media.AccentBitmapTransformation
import com.stario.launcher.utils.media.BlurBitmapTransformation
import java.io.FileInputStream
import java.util.ArrayList
import java.util.HashMap
import jp.wasabeef.glide.transformations.CropSquareTransformation
import androidx.core.net.toUri
import androidx.core.view.isEmpty

open class Media : GlanceDialogExtension() {
    private val preview = MediaPreview()
    private val handler = Handler(Looper.getMainLooper())
    private val controllerCallbacks = HashMap<MediaController, MediaController.Callback>()
    private val sessionsChangedListener = MediaSessionManager.OnActiveSessionsChangedListener { handler.post { update() } }

    private var mediaSessionManager: MediaSessionManager? = null
    private var sessionsListenerRegistered = false
    private lateinit var coverParent: ConstraintLayout
    private lateinit var slider: SliderComposeView
    private var session: MediaController? = null
    private lateinit var interactions: ViewGroup
    private lateinit var playPause: ImageView
    private var skipUpdate = false
    private lateinit var forward: ImageView
    private var lastArtist = ""
    private var lastCover: Bitmap? = null
    private lateinit var rewind: ImageView
    private var lastSong = ""
    private lateinit var cover: ImageView
    private lateinit var artist: TextView
    private lateinit var skip: ImageView
    private lateinit var song: TextView

    override fun getTAG(): String = TAG

    override fun getViewExtensionPreview(): GlanceViewExtension = preview

    public override fun isEnabled(): Boolean = session != null

    override fun updateScaling(@FloatRange(from = 0.0, to = 1.0) fraction: Float, scale: Float) {
        cover.scaleY = scale
        interactions.scaleY = scale
        coverParent.alpha = fraction
        interactions.alpha = fraction
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        mediaSessionManager = activity.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        attemptSessionListenerRegistration()
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun inflateExpanded(inflater: LayoutInflater, container: ConstraintLayout): GlanceConstraintLayout {
        val root = inflater.inflate(R.layout.media, container, false) as GlanceConstraintLayout

        interactions = root.findViewById(R.id.interactions)
        cover = root.findViewById(R.id.album_cover)
        artist = root.findViewById(R.id.artist)
        song = root.findViewById(R.id.song)
        playPause = root.findViewById(R.id.play_pause)
        rewind = root.findViewById(R.id.rewind)
        skip = root.findViewById(R.id.skip)
        forward = root.findViewById(R.id.forward)
        slider = root.findViewById(R.id.slider)
        coverParent = cover.parent as ConstraintLayout
        playPause.tag = PAUSED

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                update()
            }
        }

        if (Utils.isMinimumSDK(Build.VERSION_CODES.TIRAMISU)) {
            activity.registerReceiver(receiver, IntentFilter(NotificationService.UPDATE_NOTIFICATIONS), Context.RECEIVER_NOT_EXPORTED)
        } else {
            activity.registerReceiver(receiver, IntentFilter(NotificationService.UPDATE_NOTIFICATIONS))
        }

        val lifecycle = activity.lifecycle
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                try {
                    activity.unregisterReceiver(receiver)
                    disable()
                } catch (exception: Exception) {
                    Log.e(TAG, "Receiver not registered")
                }
                lifecycle.removeObserver(this)
            }
        })

        root.setOnClickListener {
            val currentSession = session
            if (currentSession != null) {
                val intent = activity.packageManager.getLaunchIntentForPackage(currentSession.packageName)
                if (intent != null) {
                    Vibrations.getInstance().vibrate()
                    activity.startActivity(intent, ActivityOptions.makeScaleUpAnimation(root, 0, 0, root.width, root.height).toBundle())
                }
            }
        }

        update()
        return root
    }

    override fun onStart() {
        super.onStart()
        attemptSessionListenerRegistration()
    }

    private fun attemptSessionListenerRegistration() {
        val manager = mediaSessionManager ?: return
        try {
            manager.addOnActiveSessionsChangedListener(sessionsChangedListener, ComponentName(activity, NotificationService::class.java))
            sessionsListenerRegistered = true
        } catch (exception: SecurityException) {
            Log.e(TAG, "Cannot register media session listener")
        }
    }

    override fun onDestroy() {
        if (mediaSessionManager != null && sessionsListenerRegistered) {
            mediaSessionManager!!.removeOnActiveSessionsChangedListener(sessionsChangedListener)
            sessionsListenerRegistered = false
        }

        for ((controller, callback) in controllerCallbacks) {
            controller.unregisterCallback(callback)
        }

        controllerCallbacks.clear()
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun update() {
        if (!isActivityInitialized() ||
            !NotificationManagerCompat.getEnabledListenerPackages(activity).contains(BuildConfig.APPLICATION_ID) ||
            !(activity.applicationContext as Stario).getSettings().getBoolean(PREFERENCE_ENTRY, false)
        ) {
            disable()
            return
        }

        val activeSessions = try {
            mediaSessionManager!!.getActiveSessions(ComponentName(activity, NotificationService::class.java))
        } catch (exception: SecurityException) {
            Log.e(TAG, "Cannot get active media sessions")
            return
        }

        val inactiveControllers = ArrayList<MediaController>()
        for (trackedController in controllerCallbacks.keys) {
            var stillActive = false
            for (active in activeSessions) {
                if (active.sessionToken == trackedController.sessionToken) {
                    stillActive = true
                    break
                }
            }
            if (!stillActive) {
                inactiveControllers.add(trackedController)
            }
        }

        for (controller in inactiveControllers) {
            val callback = controllerCallbacks.remove(controller)
            if (callback != null) {
                controller.unregisterCallback(callback)
            }
        }

        for (controller in activeSessions) {
            var isNew = true
            for (tracked in controllerCallbacks.keys) {
                if (tracked.sessionToken == controller.sessionToken) {
                    isNew = false
                    break
                }
            }

            if (isNew) {
                val callback = MediaControllerCallback(controller)
                controller.registerCallback(callback)
                controllerCallbacks[controller] = callback
            }
        }

        updateActiveSession(activeSessions)
        preview.setEnabled(isEnabled())
    }

    private fun updateActiveSession(controllers: List<MediaController>) {
        if (controllers.isEmpty()) {
            disable()
            return
        }

        var activeSessionCandidate: MediaController? = null

        for (controller in controllers) {
            val state = controller.playbackState
            val attrs = controller.playbackInfo.audioAttributes
            if (attrs != null && attrs.usage == AudioAttributes.USAGE_MEDIA && state != null && state.state == PlaybackState.STATE_PLAYING && validateMetadata(controller.metadata)) {
                activeSessionCandidate = controller
                break
            }
        }

        if (activeSessionCandidate == null && session != null) {
            for (controller in controllers) {
                val attrs = controller.playbackInfo.audioAttributes
                if (attrs != null && attrs.usage == AudioAttributes.USAGE_MEDIA && controller.sessionToken == session!!.sessionToken && validateMetadata(controller.metadata)) {
                    activeSessionCandidate = controller
                    break
                }
            }
        }

        if (activeSessionCandidate == null) {
            for (controller in controllers) {
                val attrs = controller.playbackInfo.audioAttributes
                if (attrs != null && attrs.usage == AudioAttributes.USAGE_MEDIA && validateMetadata(controller.metadata)) {
                    activeSessionCandidate = controller
                    break
                }
            }
        }

        if (session !== activeSessionCandidate) {
            session = activeSessionCandidate
            handler.removeCallbacksAndMessages(null)
        }

        updateSession()
    }

    private fun validateMetadata(metadata: MediaMetadata?): Boolean {
        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
        return !(title == null || title.isEmpty())
    }

    protected override fun show() {
        super.show()
        updateSession()
    }

    @SuppressLint("ClickableViewAccessibility")
    fun updateSession() {
        val currentSession = session
        if (currentSession == null) {
            disable()
            return
        }

        if (!isShowing()) {
            reset()
            return
        }

        val metadata = currentSession.metadata ?: return

        playPause.setOnClickListener { view ->
            session?.let {
                Vibrations.getInstance().vibrate()
                val playbackState = it.playbackState
                if (playbackState != null) {
                    if (playbackState.state == PlaybackState.STATE_PLAYING) {
                        it.transportControls.pause()
                    } else {
                        it.transportControls.play()
                    }
                    view.startAnimation(AnimationUtils.loadAnimation(activity, R.anim.bounce))
                }
            }
        }

        rewind.setOnClickListener { view ->
            session?.let {
                Vibrations.getInstance().vibrate()
                it.transportControls.skipToPrevious()
                skipUpdate = true
                view.startAnimation(AnimationUtils.loadAnimation(activity, R.anim.bounce_left))
            }
        }

        skip.setOnClickListener { view ->
            session?.let {
                Vibrations.getInstance().vibrate()
                it.transportControls.skipToNext()
                skipUpdate = true
                view.startAnimation(AnimationUtils.loadAnimation(activity, R.anim.bounce_right))
            }
        }

        forward.setOnClickListener { view ->
            session?.let {
                Vibrations.getInstance().vibrate()
                val state = it.playbackState
                if (state != null) {
                    val position = state.position + SEEK_TIME
                    if (position < metadata.getLong(MediaMetadata.METADATA_KEY_DURATION)) {
                        it.transportControls.seekTo(position)
                    } else {
                        it.transportControls.skipToNext()
                    }
                } else {
                    it.transportControls.skipToNext()
                }
                skipUpdate = true
                view.startAnimation(AnimationUtils.loadAnimation(activity, R.anim.rotate_small))
            }
        }

        var title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE)
        title = title?.trim() ?: ""
        if (title.contentEquals(lastSong)) {
        } else {
            lastSong = title
            val finalTitle = title
            song.animate().alpha(0f).setDuration(Animation.MEDIUM.getDuration().toLong()).withEndAction {
                song.text = finalTitle
                song.post { song.animate().alpha(1f).setDuration(Animation.MEDIUM.getDuration().toLong()) }
            }
        }

        var artistStr = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
        artistStr = if (artistStr == null || artistStr.isEmpty()) {
            activity.resources.getString(R.string.unknown_artist)
        } else {
            artistStr.trim()
        }

        if (!artistStr.contentEquals(lastArtist)) {
            lastArtist = artistStr
            val finalArtist = artistStr
            artist.animate().alpha(0f).setDuration(Animation.MEDIUM.getDuration().toLong()).withEndAction {
                artist.text = finalArtist
                artist.post { artist.animate().alpha(0.85f).setDuration(Animation.MEDIUM.getDuration().toLong()) }
            }
        }

        handler.removeCallbacksAndMessages(null)
        updateSlider(metadata)

        slider.listener = object : SliderComposeView.OnProgressChanged {
            override fun changing() {
                skipUpdate = true
            }

            override fun progressChanged(progress: Float) {
                session?.transportControls?.seekTo((progress * metadata.getLong(MediaMetadata.METADATA_KEY_DURATION)).toLong())
            }
        }

        var coverUri = metadata.getString(MediaMetadata.METADATA_KEY_ART_URI)
        if (coverUri.isNullOrBlank()) {
            coverUri = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
        }
        if (coverUri.isNullOrBlank()) {
            coverUri = metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI)
        }

        var result = false
        if (!coverUri.isNullOrBlank()) {
            result = updateCover(coverUri)
        }

        if (!result) {
            var coverBmp = metadata.getBitmap(MediaMetadata.METADATA_KEY_ART)
            if (coverBmp == null || coverBmp.width < MIN_BITMAP_SIZE || coverBmp.height < MIN_BITMAP_SIZE) {
                coverBmp = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            }
            if (coverBmp == null || coverBmp.width < MIN_BITMAP_SIZE || coverBmp.height < MIN_BITMAP_SIZE) {
                coverBmp = metadata.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
            }
            updateCover(coverBmp)
        }

        updatePlaybackState()
    }

    private fun reset() {
        playPause.setOnClickListener(null)
        rewind.setOnClickListener(null)
        skip.setOnClickListener(null)
        forward.setOnClickListener(null)
        song.text = null
        artist.text = null
        cover.setImageDrawable(null)
        lastArtist = ""
        lastSong = ""
        lastCover = null
    }

    fun updateCover(stringUri: String?): Boolean {
        if (stringUri != null) {
            try {
                val uri = stringUri.toUri()
                val inputStream = if ("content" == uri.scheme) {
                    activity.contentResolver.openInputStream(uri)
                } else {
                    FileInputStream(stringUri)
                }
                updateCover(BitmapFactory.decodeStream(inputStream))
                return true
            } catch (exception: Exception) {
                return false
            }
        }
        return false
    }

    fun updateCover(bitmapInput: Bitmap?) {
        var bitmap = bitmapInput
        if (bitmap == null && lastCover == null) {
            return
        }

        if (bitmap != null && lastCover != null && !lastCover!!.isRecycled && bitmap.sameAs(lastCover)) {
            return
        }

        if (bitmap != null) {
            var config = bitmap.config
            if (config == null) {
                config = Bitmap.Config.ARGB_8888
            }

            bitmap = bitmap.copy(config, false)
            val factory = DrawableCrossFadeFactory.Builder().setCrossFadeEnabled(true).build()

            if (!activity.isDestroyed) {
                Glide.with(activity)
                    .load(bitmap)
                    .apply(RequestOptions.bitmapTransform(MultiTransformation(CropSquareTransformation(), BlurBitmapTransformation(5), AccentBitmapTransformation())))
                    .placeholder(cover.drawable)
                    .transition(DrawableTransitionOptions.withCrossFade(factory))
                    .into(cover)
            }

            lastCover = bitmap
        } else {
            lastCover = null
        }
    }

    private fun updateSlider(metadata: MediaMetadata) {
        if (session != null) {
            val playbackState = session!!.playbackState
            if (playbackState != null && !skipUpdate) {
                val duration = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION)
                var progress = if (duration > 0) playbackState.position.toFloat() / duration else 0f
                if (progress.isNaN() || progress.isInfinite()) {
                    progress = 0f
                }
                slider.progress.floatValue = progress
            }
            handler.postDelayed({ updateSlider(metadata) }, 200)
        }
    }

    fun updatePlaybackState() {
        val playbackState = session?.playbackState
        if (playbackState != null) {
            if (playbackState.state == PlaybackState.STATE_PLAYING) {
                if (PAUSED == playPause.tag) {
                    val drawable = ResourcesCompat.getDrawable(activity.resources, R.drawable.ic_play_pause, activity.theme) as AnimatedVectorDrawable?
                    if (drawable != null) {
                        playPause.setImageDrawable(drawable)
                        drawable.start()
                    }
                    playPause.tag = PLAYING
                    slider.isPlaying.value = true
                }
            } else if (PLAYING == playPause.tag) {
                val drawable = ResourcesCompat.getDrawable(activity.resources, R.drawable.ic_pause_play, activity.theme) as AnimatedVectorDrawable?
                if (drawable != null) {
                    playPause.setImageDrawable(drawable)
                    drawable.start()
                }
                playPause.tag = PAUSED
                slider.isPlaying.value = false
            }
        }
    }

    fun disable() {
        session = null
        preview.setEnabled(isEnabled())

        if (isShowing()) {
            addTransitionListener(object : TransitionListener {
                override fun onProgressFraction(fraction: Float) {
                    if (fraction == 0f) {
                        reset()
                    }
                    removeTransitionListener(this)
                }
            })
            urgentHide()
        }
    }

    private inner class MediaControllerCallback(private val controller: MediaController) : MediaController.Callback() {
        override fun onSessionDestroyed() {
            update()
            super.onSessionDestroyed()
        }

        override fun onPlaybackStateChanged(state: PlaybackState?) {
            skipUpdate = false

            if (state == null) {
                return
            }

            val isCurrentSession = session != null && session!!.sessionToken == controller.sessionToken
            if (state.state == PlaybackState.STATE_PLAYING && !isCurrentSession) {
                update()
            } else if (isCurrentSession) {
                updatePlaybackState()
            }

            super.onPlaybackStateChanged(state)
        }

        override fun onMetadataChanged(metadata: MediaMetadata?) {
            if (session != null && session!!.sessionToken == controller.sessionToken) {
                updateSession()
            }
            super.onMetadataChanged(metadata)
        }
    }

    companion object {
        const val PREFERENCE_ENTRY = "com.stario.Media.MEDIA"
        private const val TAG = "com.stario.launcher.media"
        private const val SEEK_TIME = 5000L
        private const val MIN_BITMAP_SIZE = 256
        private const val PLAYING = 1
        private const val PAUSED = 0
    }
}
