package com.neilturner.playerexp.data.plex

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.os.Build

/**
 * The decoder capabilities that this installed app can honestly advertise to Plex.
 *
 * Containers are limited to formats that the app's Media3 dependencies can demux. Video is
 * limited to hardware decoders on Android 10+ because a software decoder does not guarantee TV
 * playback performance.
 */
data class PlexDeviceCapabilities(
    val videoCodecs: List<PlexVideoCodecCapability>,
    val audioCodecs: List<PlexAudioCodecCapability>
) {
    fun playbackProfile(): PlexPlaybackProfile {
        val videoCodecNames = videoCodecs.map { it.plexName }.distinct().sorted()
        val audioCodecNames = audioCodecs.map { it.plexName }.distinct().sorted()
        require("h264" in videoCodecNames && "aac" in audioCodecNames) {
            "Device is missing the H.264/AAC HLS transcode fallback. " +
                "Found video: $videoCodecNames, audio: $audioCodecNames"
        }

        val directives = buildList {
            add(
                "add-direct-play-profile(type=videoProfile&container=" +
                    "mp4,m4v,mov,mkv,matroska,webm,mpegts&videoCodec=" +
                    videoCodecNames.joinToString(",") +
                    "&audioCodec=${audioCodecNames.joinToString(",")}&subtitleCodec=*)"
            )
            videoCodecs.forEach { codec ->
                addVideoUpperBound(codec.plexName, "video.width", codec.maximumWidth)
                addVideoUpperBound(codec.plexName, "video.height", codec.maximumHeight)
                addVideoUpperBound(codec.plexName, "video.frameRate", codec.maximumFrameRate)
            }
            // This replaces Generic's streaming target with a format bundled Media3 can play.
            add(
                "add-transcode-target(type=videoProfile&context=streaming&protocol=hls" +
                    "&container=mpegts&videoCodec=h264&audioCodec=aac&replace=true)"
            )
        }
        return PlexPlaybackProfile(directives.joinToString("+"))
    }

    private fun MutableList<String>.addVideoUpperBound(codec: String, name: String, value: Int) {
        if (value > 0) {
            add(
                "add-limitation(scope=videoCodec&scopeName=$codec&type=upperBound" +
                    "&name=$name&value=$value&isRequired=false)"
            )
        }
    }
}

data class PlexVideoCodecCapability(
    val plexName: String,
    val maximumWidth: Int,
    val maximumHeight: Int,
    val maximumFrameRate: Int
)

data class PlexAudioCodecCapability(
    val plexName: String,
    val maximumChannelCount: Int
)

data class PlexPlaybackProfile(val clientProfileExtra: String) {
    companion object {
        const val GENERIC_PROFILE_NAME = "Generic"
    }
}

object AndroidPlexCapabilityProbe {
    private val videoMimeToPlexCodec = mapOf(
        "video/avc" to "h264",
        "video/hevc" to "hevc",
        "video/x-vnd.on2.vp8" to "vp8",
        "video/x-vnd.on2.vp9" to "vp9",
        "video/av01" to "av1",
        "video/mpeg2" to "mpeg2video",
        "video/mp4v-es" to "mpeg4"
    )
    private val audioMimeToPlexCodec = mapOf(
        "audio/mp4a-latm" to "aac",
        "audio/mpeg" to "mp3",
        "audio/ac3" to "ac3",
        "audio/eac3" to "eac3",
        "audio/vnd.dts" to "dca",
        "audio/vnd.dts.hd" to "dca",
        "audio/opus" to "opus",
        "audio/vorbis" to "vorbis",
        "audio/flac" to "flac"
    )

    fun probe(): PlexDeviceCapabilities {
        val video = mutableMapOf<String, PlexVideoCodecCapability>()
        val audio = mutableMapOf<String, PlexAudioCodecCapability>()

        MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos
            .asSequence()
            .filterNot(MediaCodecInfo::isEncoder)
            .forEach { codecInfo ->
                codecInfo.supportedTypes.forEach { mimeType ->
                    val capabilities = runCatching {
                        codecInfo.getCapabilitiesForType(mimeType)
                    }.getOrNull() ?: return@forEach

                    videoMimeToPlexCodec[mimeType]?.let { plexName ->
                        if (!isUsableVideoDecoder(codecInfo)) return@let
                        val videoCapabilities = capabilities.videoCapabilities ?: return@let
                        val candidate = PlexVideoCodecCapability(
                            plexName = plexName,
                            maximumWidth = videoCapabilities.supportedWidths.upper,
                            maximumHeight = videoCapabilities.supportedHeights.upper,
                            maximumFrameRate = videoCapabilities.supportedFrameRates.upper
                        )
                        video[plexName] = video[plexName]?.merge(candidate) ?: candidate
                    }
                    audioMimeToPlexCodec[mimeType]?.let { plexName ->
                        if (!isUsableAudioDecoder(codecInfo)) return@let
                        val candidate = PlexAudioCodecCapability(
                            plexName = plexName,
                            maximumChannelCount = capabilities.audioCapabilities?.maxInputChannelCount ?: 0
                        )
                        audio[plexName] = audio[plexName]?.merge(candidate) ?: candidate
                    }
                }
            }

        return PlexDeviceCapabilities(
            videoCodecs = video.values.sortedBy(PlexVideoCodecCapability::plexName),
            audioCodecs = audio.values.sortedBy(PlexAudioCodecCapability::plexName)
        )
    }

    private fun isUsableVideoDecoder(codecInfo: MediaCodecInfo): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            (!codecInfo.isAlias && (codecInfo.isHardwareAccelerated || isEmulator()))

    private fun isUsableAudioDecoder(codecInfo: MediaCodecInfo): Boolean =
        !codecInfo.isAlias

    private fun isEmulator(): Boolean =
        Build.FINGERPRINT.startsWith("generic") ||
            Build.FINGERPRINT.startsWith("unknown") ||
            Build.MODEL.contains("google_sdk") ||
            Build.MODEL.contains("Emulator") ||
            Build.MODEL.contains("Android SDK built for") ||
            Build.HARDWARE.contains("goldfish") ||
            Build.HARDWARE.contains("ranchu")

    private fun PlexVideoCodecCapability.merge(other: PlexVideoCodecCapability) = copy(
        maximumWidth = maxOf(maximumWidth, other.maximumWidth),
        maximumHeight = maxOf(maximumHeight, other.maximumHeight),
        maximumFrameRate = maxOf(maximumFrameRate, other.maximumFrameRate)
    )

    private fun PlexAudioCodecCapability.merge(other: PlexAudioCodecCapability) = copy(
        maximumChannelCount = maxOf(maximumChannelCount, other.maximumChannelCount)
    )
}
