package net.digitalbc.leafreader.detection

import android.content.Context
import android.graphics.Bitmap

/**
 * Placeholder for on-device ML panel detection when the TFLite/LiteRT runtime and bundled model
 * are excluded from the Android build. Mirrors the iOS `LiteRTPanelDetector` fallback so callers
 * cleanly fall back to [NoopPanelSource] / whole-page reading without packaging native AI binaries
 * or model weights.
 */
class MlPanelDetector private constructor() : PanelSource {

    override fun detect(page: Bitmap, rightToLeft: Boolean): List<Panel> =
        listOf(Panel.FULL_PAGE)

    fun close() = Unit

    companion object {
        fun tryCreate(@Suppress("UNUSED_PARAMETER") context: Context): MlPanelDetector? = null
    }
}
