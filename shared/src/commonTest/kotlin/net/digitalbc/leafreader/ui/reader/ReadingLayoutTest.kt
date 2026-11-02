package net.digitalbc.leafreader.ui.reader

import kotlin.test.Test
import kotlin.test.assertEquals

class ReadingLayoutTest {
    @Test fun eachOrientationUsesOnlyItsOwnSavedLayout() {
        for (portrait in listOf(false, true)) for (landscape in listOf(false, true)) {
            assertEquals(portrait, resolveSpreadLayout(false, portrait, landscape))
            assertEquals(landscape, resolveSpreadLayout(true, portrait, landscape))
        }
    }
}
