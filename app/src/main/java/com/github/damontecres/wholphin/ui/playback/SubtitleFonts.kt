package com.github.damontecres.wholphin.ui.playback

import com.github.damontecres.wholphin.util.profile.Codec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.videoApi
import org.jellyfin.sdk.model.api.MediaAttachment
import org.jellyfin.sdk.model.api.MediaSourceInfo
import org.jellyfin.sdk.model.api.MediaStreamType
import timber.log.Timber
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue

private const val MAX_CONCURRENT_FONT_DOWNLOADS = 4

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

    val fonts = ConcurrentLinkedQueue<Pair<String, ByteArray>>()
    val semaphore = Semaphore(MAX_CONCURRENT_FONT_DOWNLOADS)
    coroutineScope {
        source.mediaAttachments.orEmpty().filter(::isFontAttachment).forEach { attachment ->
            launch {
                semaphore.withPermit {
                    try {
                        val data = videoApi.getAttachment(itemId, mediaSourceId, attachment.index).content
                        fonts.add((attachment.fileName ?: "font-${attachment.index}") to data)
                    } catch (ex: CancellationException) {
                        throw ex
                    } catch (ex: Exception) {
                        Timber.w(ex, "Could not download font attachment %s", attachment.index)
                    } catch (ex: OutOfMemoryError) {
                        Timber.w(ex, "Not enough memory for font attachment %s", attachment.index)
                    }
                }
            }
        }
    }
    Timber.d("Downloaded %s subtitle fonts", fonts.size)
    return fonts.toList()
}
