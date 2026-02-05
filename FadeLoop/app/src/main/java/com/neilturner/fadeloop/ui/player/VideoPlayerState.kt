package com.neilturner.fadeloop.ui.player

import android.content.Context
import android.util.Log
import androidx.annotation.OptIn
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.upstream.DefaultAllocator
import com.neilturner.fadeloop.data.cache.VideoCacheManager
import com.neilturner.fadeloop.data.model.Video
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAG = "VideoPlayer"

data class VideoPlayerConfig(
    val crossFadeDurationMs: Int = 2000,
    val preloadBufferMs: Long = 5000L,
    val preloadAtEnd: Boolean = false,
    val useMinimalBuffer: Boolean = true,
    val pollIntervalMs: Long = 100L,
    val transitionTriggerRemainingMs: Long = 200L,
    val startupFadeDurationMs: Int = 1000
)

@OptIn(UnstableApi::class)
class VideoPlayerState(
    private val context: Context,
    private val cacheManager: VideoCacheManager,
    private val scope: CoroutineScope,
    private val config: VideoPlayerConfig = VideoPlayerConfig()
) {

    private val cacheDataSourceFactory: CacheDataSource.Factory = CacheDataSource.Factory()
        .setCache(cacheManager.simpleCache)
        .setUpstreamDataSourceFactory(DefaultHttpDataSource.Factory())
        .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        .setEventListener(object : CacheDataSource.EventListener {
            private var lastLogTime = 0L
            override fun onCachedBytesRead(cacheSizeBytes: Long, cachedBytesRead: Long) {
                val now = System.currentTimeMillis()
                val totalMetaBytes = cachedBytesRead / (1024 * 1024)

                if (now - lastLogTime > 5000) {
                    Log.d(TAG, "Playing from Cache... (Cache item size: $totalMetaBytes MB)")
                    lastLogTime = now
                }
            }

            override fun onCacheIgnored(reason: Int) {
                Log.w(TAG, "Cache Ignored. Reason: $reason")
            }
        })

    private fun createLoadControl(): LoadControl {
        val builder = DefaultLoadControl.Builder()
        val allocator = DefaultAllocator(true, C.DEFAULT_BUFFER_SEGMENT_SIZE)
        builder.setAllocator(allocator)

        if (config.useMinimalBuffer) {
            builder.setBufferDurationsMs(
                2000,
                10000,
                500,
                1000
            )
        } else {
            builder.setBufferDurationsMs(
                50000,
                50000,
                2500,
                5000
            )
        }
        return builder.build()
    }

    private fun buildPlayer(
        initialVolume: Float,
        onFirstPlaying: (() -> Unit)? = null
    ): ExoPlayer {
        return ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(cacheDataSourceFactory))
            .setVideoScalingMode(C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING)
            .setLoadControl(createLoadControl())
            .build()
            .apply {
                volume = initialVolume
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        Log.d(TAG, "Player ${hashCode()} State: $playbackState")
                    }

                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        Log.d(TAG, "Player ${hashCode()} IsPlaying: $isPlaying")
                        if (isPlaying) {
                            onFirstPlaying?.invoke()
                        }
                    }
                })
            }
    }

    val player1: ExoPlayer = buildPlayer(initialVolume = 1f) {
        if (!hasStartedPlayback) {
            hasStartedPlayback = true
        }
    }

    val player2: ExoPlayer = buildPlayer(initialVolume = 0f)

    var activePlayerIs1 by mutableStateOf(true)
        private set

    var currentVideoIndex by mutableIntStateOf(0)
        private set

    var remainingSeconds by mutableLongStateOf(0L)
        private set

    var locationText by mutableStateOf("")
        private set

    var showLocation by mutableStateOf(false)
        private set

    val player1Alpha: Animatable<Float, *> = Animatable(1f)
    val player2Alpha: Animatable<Float, *> = Animatable(0f)

    val startupBlackAlpha: Animatable<Float, *> = Animatable(1f)

    private var hasStartedPlayback by mutableStateOf(false)
    private var isCrossFading by mutableStateOf(false)
    private var isNextPlayerPrepared by mutableStateOf(false)

    private fun preparePlayer(player: ExoPlayer, url: String) {
        Log.d(TAG, "Preparing player ${player.hashCode()} with $url")
        player.setMediaItem(MediaItem.fromUri(url))
        player.prepare()
        player.repeatMode = Player.REPEAT_MODE_OFF
    }

    fun startStartupFadeIfNeeded() {
        if (hasStartedPlayback) {
            scope.launch {
                if (startupBlackAlpha.value > 0f) {
                    Log.d(TAG, "First playback detected. Fading out black screen.")
                    startupBlackAlpha.animateTo(
                        0f,
                        animationSpec = tween(config.startupFadeDurationMs, easing = LinearEasing)
                    )
                }
            }
        }
    }

    fun initialSetup(videos: List<Video>) {
        Log.d(TAG, "Initial Setup Launched. Current Cache Size: ${cacheManager.getUsedSpace() / (1024 * 1024)} MB")
        preparePlayer(player1, videos[0].url)
        player1.playWhenReady = true

        if (!config.preloadAtEnd) {
            val nextIndex = (0 + 1) % videos.size
            scope.launch {
                Log.d(TAG, "Waiting 3s before preparing next player (Legacy Mode)...")
                delay(3000)
                preparePlayer(player2, videos[nextIndex].url)
                player2.playWhenReady = false
            }
            isNextPlayerPrepared = true
        } else {
            isNextPlayerPrepared = false
        }

        startStartupFadeIfNeeded()
    }

    fun launchPlaybackLoop(videos: List<Video>, useSurfaceView: Boolean) {
        scope.launch {
            Log.d(TAG, "State Changed: activePlayerIs1=$activePlayerIs1, index=$currentVideoIndex")

            val activePlayer = if (activePlayerIs1) player1 else player2
            val nextPlayer = if (activePlayerIs1) player2 else player1

            if (activePlayerIs1) {
                player1Alpha.snapTo(1f)
                player2Alpha.snapTo(0f)
                player1.volume = 1f
                player2.volume = 1f
            } else {
                player1Alpha.snapTo(0f)
                player2Alpha.snapTo(1f)
                player1.volume = 1f
                player2.volume = 1f
            }

            isCrossFading = false
            isNextPlayerPrepared = !config.preloadAtEnd

            scope.launch {
                val video = videos[currentVideoIndex]
                if (video.title.isNotEmpty()) {
                    locationText = video.title
                    delay(1000)
                    showLocation = true
                } else {
                    showLocation = false
                }
            }

            var lastCacheLogTime = 0L

            while (true) {
                delay(config.pollIntervalMs)

                val now = System.currentTimeMillis()
                if (now - lastCacheLogTime > 10000) {
                    val cacheSizeMb = cacheManager.getUsedSpace() / (1024 * 1024)
                    val currentUrl = videos[currentVideoIndex].url
                    val cachedPercent = cacheManager.getCachedPercentage(currentUrl)
                    Log.d(TAG, "Current Cache Size: $cacheSizeMb MB. Current Video Cached: $cachedPercent%")
                    lastCacheLogTime = now
                }

                val duration = activePlayer.duration
                val position = activePlayer.currentPosition
                val currentVideo = videos[currentVideoIndex]
                val videoLimit = currentVideo.durationMs ?: Long.MAX_VALUE

                if (duration > 0 && duration != C.TIME_UNSET) {
                    val logicalEnd = if (videoLimit < duration) videoLimit else duration
                    val remaining = logicalEnd - position

                    remainingSeconds = remaining / 1000

                    if (
                        config.preloadAtEnd &&
                        !isCrossFading &&
                        !isNextPlayerPrepared &&
                        remaining <= config.preloadBufferMs
                    ) {
                        Log.d(TAG, "Preload Triggered (End Mode). Remaining: $remaining")
                        isNextPlayerPrepared = true
                        val nextIndex = (currentVideoIndex + 1) % videos.size
                        preparePlayer(nextPlayer, videos[nextIndex].url)
                        nextPlayer.playWhenReady = false
                    }

                    if (!isCrossFading && remaining <= config.transitionTriggerRemainingMs) {
                        Log.d(TAG, "Transition Triggered. Remaining: $remaining")
                        isCrossFading = true

                        showLocation = false

                        activePlayer.pause()
                        nextPlayer.playWhenReady = true

                        scope.launch {
                            val fadeDuration = if (useSurfaceView) 0 else config.crossFadeDurationMs
                            Log.d(TAG, "Animating fade: duration=$fadeDuration")
                            if (activePlayerIs1) {
                                player2Alpha.animateTo(
                                    1f,
                                    animationSpec = tween(fadeDuration, easing = LinearEasing)
                                )
                            } else {
                                player1Alpha.animateTo(
                                    1f,
                                    animationSpec = tween(fadeDuration, easing = LinearEasing)
                                )
                            }
                        }
                    }
                }

                if (hasStartedPlayback) {
                    startStartupFadeIfNeeded()
                }

                if (isCrossFading) {
                    val waitTime = if (useSurfaceView) 0L else config.crossFadeDurationMs.toLong()
                    delay(waitTime)

                    Log.d(TAG, "Transition Complete. Switching State.")

                    if (activePlayerIs1) {
                        player1Alpha.snapTo(0f)
                    } else {
                        player2Alpha.snapTo(0f)
                    }

                    activePlayer.stop()
                    activePlayer.clearMediaItems()
                    Log.d(TAG, "Cleared media items for inactive player")

                    if (!config.preloadAtEnd) {
                        val nextNextIndex = (currentVideoIndex + 2) % videos.size
                        val videoUrl = videos[nextNextIndex].url
                        scope.launch {
                            Log.d(TAG, "Waiting 4s before preparing player for next video (Legacy Mode)...")
                            delay(4000)
                            preparePlayer(activePlayer, videoUrl)
                            activePlayer.playWhenReady = false
                        }
                    }

                    currentVideoIndex = (currentVideoIndex + 1) % videos.size
                    activePlayerIs1 = !activePlayerIs1

                    break
                }
            }
        }
    }

    fun release() {
        player1.release()
        player2.release()
    }
}
