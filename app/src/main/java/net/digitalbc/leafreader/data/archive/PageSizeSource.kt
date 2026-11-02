package net.digitalbc.leafreader.data.archive

/** Header-only dimensions; avoids extracting or rasterizing pages just to plan spreads. */
interface PageSizeSource {
    fun pageSize(index: Int): IntArray
}
