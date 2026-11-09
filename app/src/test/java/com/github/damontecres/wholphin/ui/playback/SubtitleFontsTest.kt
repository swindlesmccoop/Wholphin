package com.github.damontecres.wholphin.ui.playback

import org.jellyfin.sdk.model.api.MediaAttachment
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleFontsTest {
    @Test
    fun `font mime type is a font`() {
        assertTrue(isFontAttachment(MediaAttachment(index = 0, mimeType = "application/x-truetype-font")))
    }

    @Test
    fun `font file extension is a font when mime type is generic`() {
        assertTrue(
            isFontAttachment(
                MediaAttachment(index = 0, mimeType = "application/octet-stream", fileName = "ERASDUST.TTF"),
            ),
        )
    }

    @Test
    fun `other attachments are not fonts`() {
        assertFalse(isFontAttachment(MediaAttachment(index = 0, mimeType = "image/jpeg", fileName = "cover.jpg")))
        assertFalse(isFontAttachment(MediaAttachment(index = 0)))
    }
}
