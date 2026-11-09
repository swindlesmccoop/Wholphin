package com.github.damontecres.wholphin.ui.playback

import com.github.damontecres.wholphin.util.profile.Codec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.videoApi
import org.jellyfin.sdk.model.api.MediaAttachment
import org.jellyfin.sdk.model.api.MediaSourceInfo
import org.jellyfin.sdk.model.api.MediaStreamType
import timber.log.Timber
import java.util.UUID

private const val MAX_SUBTITLE_FONT_BYTES = 64 * 1024 * 1024
private const val SUBTITLE_FONT_TIMEOUT_MS = 10_000L

private val fontMimeTypes =
    setOf(
        "font/ttf",
        "font/otf",
        "font/sfnt",
        "font/woff",
        "font/woff2",
        "application/font-sfnt",
        "application/font-woff",
        "application/x-truetype-font",
        "application/vnd.ms-opentype",
        "application/x-font-ttf",
    )

private val fontFileExtensions = listOf(".ttf", ".otf", ".ttc")

internal fun isFontAttachment(attachment: MediaAttachment): Boolean =
    attachment.mimeType?.lowercase() in fontMimeTypes ||
        fontFileExtensions.any { attachment.fileName?.endsWith(it, ignoreCase = true) == true }

/**
 * Downloads the fonts attached to the media source for use by libass. A transcoded stream does not
 * include the attachments of the original file.
 *
 * Downloading is limited in size and time, so this may return only some of the fonts.
 */
internal suspend fun ApiClient.getSubtitleFonts(
    itemId: UUID,
    source: MediaSourceInfo,
): List<Pair<String, ByteArray>> {
    val mediaSourceId = source.id ?: return emptyList()
    val hasAssSubtitles =
        source.mediaStreams.orEmpty().any {
            it.type == MediaStreamType.SUBTITLE &&
                (it.codec.equals(Codec.Subtitle.ASS, true) || it.codec.equals(Codec.Subtitle.SSA, true))
        }
    if (!hasAssSubtitles) return emptyList()

    val fonts = mutableListOf<Pair<String, ByteArray>>()
    var totalBytes = 0
    withTimeoutOrNull(SUBTITLE_FONT_TIMEOUT_MS) {
        for (attachment in source.mediaAttachments.orEmpty().filter(::isFontAttachment)) {
            if (totalBytes >= MAX_SUBTITLE_FONT_BYTES) break
            try {
                val data = videoApi.getAttachment(itemId, mediaSourceId, attachment.index).content
                fonts.add((attachment.fileName ?: "font-${attachment.index}") to data)
                totalBytes += data.size
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Timber.w(ex, "Could not download font attachment %s", attachment.index)
            }
        }
    }
    Timber.d("Downloaded %s subtitle fonts, %s bytes", fonts.size, totalBytes)
    return fonts
}
