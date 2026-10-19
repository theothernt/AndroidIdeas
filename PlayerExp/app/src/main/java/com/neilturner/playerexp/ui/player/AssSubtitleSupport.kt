package com.neilturner.playerexp.ui.player

import android.content.Context
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.RenderersFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.SubtitleView
import io.github.peerless2012.ass.media.AssHandler
import io.github.peerless2012.ass.media.AssHandlerConfig
import io.github.peerless2012.ass.media.kt.withAssMkvSupport
import io.github.peerless2012.ass.media.kt.withAssSupport
import io.github.peerless2012.ass.media.parser.AssSubtitleParserFactory
import io.github.peerless2012.ass.media.type.AssRenderType

/**
 * The ASS subtitle support for a player.
 *
 * Two things are needed for the styled subtitles to reach the screen. The player has to read the
 * ASS tracks of the file with libass, because the player's own subtitle parser throws the styling
 * away and leaves only the words. And the view that draws the subtitles has to have libass's
 * renderer in it, so that what is drawn is the styled subtitle and not a line of text.
 *
 * The handler is what holds the two together: it owns the libass instance, the tracks read out
 * of the file and the renderer. The player and the view therefore have to be given the same one,
 * which is why they are both given this.
 */
class AssSubtitleSupport(
    private val context: Context,
    val renderType: AssRenderType = AssRenderType.OVERLAY_CANVAS,
    config: AssHandlerConfig = AssHandlerConfig(maxRenderPixels = 1920 * 1080)
) {
    val handler = AssHandler(renderType, config)

    /**
     * Builds the player so that the ASS tracks in the file are read by libass.
     *
     * The parser factory is the one that knows about ASS; it passes every other kind of subtitle
     * on to the player's own factory, so the ordinary subtitles are unaffected.
     */
    fun buildPlayer(
        builder: ExoPlayer.Builder,
        dataSourceFactory: DataSource.Factory,
        extractorsFactory: ExtractorsFactory = DefaultExtractorsFactory(),
        renderersFactory: RenderersFactory = DefaultRenderersFactory(context)
    ): ExoPlayer {
        val parserFactory = AssSubtitleParserFactory(handler)
        val mediaSourceFactory = DefaultMediaSourceFactory(
            dataSourceFactory,
            extractorsFactory.withAssMkvSupport(parserFactory, handler)
        )
        mediaSourceFactory.setSubtitleParserFactory(parserFactory)
        return builder
            .setMediaSourceFactory(mediaSourceFactory)
            .setRenderersFactory(renderersFactory.withAssSupport(handler))
            .build()
            .also { handler.init(it) }
    }

    /**
     * Puts the renderer in the view that draws the subtitles, so that the styled subtitles are
     * drawn over the picture.
     */
    fun attachTo(subtitleView: SubtitleView) {
        subtitleView.withAssSupport(handler)
    }

    /** Releases the native libass instance and everything it holds. */
    fun release() {
        handler.release()
    }
}
