package net.digitalbc.leafreader.data.jellyfin

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class JellyfinLibraryEntryTest {
    @Test
    fun singleBooksLibraryOpensAutomaticallyRegardlessOfName() {
        val books = JellyfinView("books-id", "Graphic novels", "BOOKS")
        val views = listOf(JellyfinView("movies-id", "Comics", "movies"), books)
        assertEquals(books, JellyfinLibraryEntry.automaticLibrary(views))
        assertEquals(listOf(books), JellyfinLibraryEntry.comicLibraries(views))
    }

    @Test
    fun multipleBooksLibrariesRequireAChoice() {
        val views = listOf(
            JellyfinView("western", "Comics", "books"),
            JellyfinView("manga", "Manga", "books"),
            JellyfinView("music", "Music", "music"),
        )
        assertNull(JellyfinLibraryEntry.automaticLibrary(views))
        assertEquals(listOf("western", "manga"), JellyfinLibraryEntry.comicLibraries(views).map { it.id })
    }

    @Test
    fun absentOrMixedTypesNeverGuessALibraryFromItsTitle() {
        assertNull(JellyfinLibraryEntry.automaticLibrary(emptyList()))
        val views = listOf(JellyfinView("mixed", "Comics"), JellyfinView("video", "Books", "movies"))
        assertNull(JellyfinLibraryEntry.automaticLibrary(views))
        assertEquals(emptyList(), JellyfinLibraryEntry.comicLibraries(views))
    }
}
