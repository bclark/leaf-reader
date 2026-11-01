package com.chakra.comicreader.data.archive

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ComicMetadataParserTest {

    @Test
    fun testParseFilenameWithYearAndIssue() {
        val meta = ComicMetadataParser.parseFilename("Deadpool (2016) 001.cbz")
        assertEquals("Deadpool", meta.series)
        assertEquals(2016, meta.year)
        assertEquals(1f, meta.issueNumber)
        assertNull(meta.volume)
    }

    @Test
    fun testParseFilenameWithVolumeAndIssue() {
        val meta = ComicMetadataParser.parseFilename("The Punisher Vol 2 #03.cbr")
        assertEquals("The Punisher", meta.series)
        assertEquals("Vol. 2", meta.volume)
        assertEquals(3f, meta.issueNumber)
    }

    @Test
    fun testParseFilenameWithVNotation() {
        val meta = ComicMetadataParser.parseFilename("Deadpool v3 015.cbz")
        assertEquals("Deadpool", meta.series)
        assertEquals("Vol. 3", meta.volume)
        assertEquals(15f, meta.issueNumber)
    }

    @Test
    fun testParseFilenameSimpleSeriesAndNumber() {
        val meta = ComicMetadataParser.parseFilename("Saga 054.cbz")
        assertEquals("Saga", meta.series)
        assertEquals(54f, meta.issueNumber)
    }

    @Test
    fun testParseFilenameStandaloneGraphicNovel() {
        val meta = ComicMetadataParser.parseFilename("Batman - The Killing Joke.cbz")
        assertEquals("Batman - The Killing Joke", meta.series)
        assertNull(meta.issueNumber)
    }

    @Test
    fun testParseComicInfoXml() {
        val xml = """
            <?xml version="1.0"?>
            <ComicInfo xmlns:xsd="http://www.w3.org/2001/XMLSchema" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
              <Title>The End of the Beginning</Title>
              <Series>Deadpool</Series>
              <Number>1</Number>
              <Volume>2</Volume>
              <Year>2016</Year>
            </ComicInfo>
        """.trimIndent()
        val meta = ComicMetadataParser.parseComicInfoXml(xml)
        assertEquals("Deadpool", meta.series)
        assertEquals("The End of the Beginning", meta.title)
        assertEquals("Vol. 2", meta.volume)
        assertEquals(1f, meta.issueNumber)
        assertEquals(2016, meta.year)
    }
}
