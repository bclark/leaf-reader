package com.chakra.comicreader.data.archive

/**
 * Metadata extracted from a comic archive (via ComicInfo.xml or parsed filename).
 */
data class ComicMetadata(
    val series: String? = null,
    val volume: String? = null,
    val issueNumber: Float? = null,
    val year: Int? = null,
    val title: String? = null,
)

object ComicMetadataParser {

    /**
     * Parses a comic filename into structured metadata as a heuristic fallback.
     * Handles common patterns like:
     * - "Deadpool (2016) 001.cbz" -> series: "Deadpool", year: 2016, issueNumber: 1f
     * - "Deadpool v2 004.cbr" -> series: "Deadpool", volume: "v2", issueNumber: 4f
     * - "The Punisher Vol 2 #03.cbz" -> series: "The Punisher", volume: "Vol 2", issueNumber: 3f
     * - "Saga 015.cbz" -> series: "Saga", issueNumber: 15f
     * - "Batman - The Killing Joke.cbz" -> title / series: "Batman - The Killing Joke"
     */
    fun parseFilename(filename: String): ComicMetadata {
        val base = filename.substringBeforeLast('.', filename).trim()
        if (base.isEmpty()) return ComicMetadata()

        // 1. Extract year if present in parentheses e.g. (2016), (1998)
        val yearRegex = Regex("""\((19\d\d|20\d\d)\)""")
        val yearMatch = yearRegex.find(base)
        val year = yearMatch?.groupValues?.get(1)?.toIntOrNull()
        var clean = if (yearMatch != null) {
            base.removeRange(yearMatch.range).trim()
        } else {
            base
        }

        // 2. Extract volume if present e.g. "Vol 2", "Vol. 2", "v2", "Volume 3", "(Vol 2)"
        val volumeRegex = Regex("""(?i)(?:\(?\b(?:vol\.?|volume|v)\s*(\d+)\)?)""")
        val volumeMatch = volumeRegex.find(clean)
        val volume = volumeMatch?.let {
            val num = it.groupValues[1]
            "Vol. $num"
        }
        if (volumeMatch != null) {
            clean = clean.removeRange(volumeMatch.range).trim()
        }

        // 3. Extract issue number e.g. "#03", "#3.5", "001", "015", " 12 "
        // Look for '#' followed by numbers, or trailing numbers at the end of string or before delimiters
        val issueRegex = Regex("""(?:#\s*|(?<=\s))(\d+(?:\.\d+)?)(?:\s*(?:of\s*\d+|\([^\)]*\))|\s*$)""")
        val issueMatch = issueRegex.find(clean)

        var issueNumber: Float? = null
        var series: String? = null

        if (issueMatch != null) {
            issueNumber = issueMatch.groupValues[1].toFloatOrNull()
            series = clean.substring(0, issueMatch.range.first)
                .trim('-', ' ', '_', '#')
                .trim()
        }

        // If series couldn't be cleanly separated, use clean string
        if (series.isNullOrBlank()) {
            series = clean.trim('-', ' ', '_').trim()
        }

        // Normalize series: collapse multiple spaces/underscores
        series = series.replace(Regex("""[_\s]+"""), " ").trim()

        return ComicMetadata(
            series = series.ifBlank { null },
            volume = volume,
            issueNumber = issueNumber,
            year = year,
            title = base,
        )
    }

    /**
     * Parses ComicInfo.xml content (UTF-8 string).
     * Extracts <Series>, <Volume>, <Number>, <Year>, <Title>.
     */
    fun parseComicInfoXml(xml: String): ComicMetadata {
        val series = extractTag(xml, "Series")
        val volumeRaw = extractTag(xml, "Volume")
        val volume = volumeRaw?.let {
            if (it.startsWith("Vol", ignoreCase = true)) it else "Vol. $it"
        }
        val numberRaw = extractTag(xml, "Number")
        val issueNumber = numberRaw?.toFloatOrNull()
        val year = extractTag(xml, "Year")?.toIntOrNull()
        val title = extractTag(xml, "Title")

        return ComicMetadata(
            series = series,
            volume = volume,
            issueNumber = issueNumber,
            year = year,
            title = title,
        )
    }

    private fun extractTag(xml: String, tag: String): String? {
        val regex = Regex("""<$tag>(.*?)</$tag>""", RegexOption.DOT_MATCHES_ALL)
        return regex.find(xml)?.groupValues?.get(1)?.trim()?.ifEmpty { null }
    }
}
