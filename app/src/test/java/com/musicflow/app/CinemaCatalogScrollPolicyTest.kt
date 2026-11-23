package com.musicflow.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** UI contract: new catalogue data cannot retain the old LazyColumn anchor. */
class CinemaCatalogScrollPolicyTest {
    private fun identity(
        query: String = "",
        section: String = "Главная",
        source: String = "all",
        shelf: String? = null,
        hasContent: Boolean = false,
        searching: Boolean = false
    ) = CinemaCatalogScrollPolicy.identity(
        query = query, section = section, sourceId = source, shelfUrl = shelf,
        hasVisibleContent = hasContent, searching = searching
    )

    @Test fun openingCinemaResetsWhenHomeRowsArriveAsynchronously() {
        val first = identity(hasContent = false)
        val loaded = identity(hasContent = true)
        assertNotEquals("The footer must not remain the anchored first row", first, loaded)
        assertEquals("ready", loaded.phase)
    }

    @Test fun searchResetsWhenQueryIsTypedAndWhenResultsArrive() {
        val home = identity(hasContent = true)
        val pending = identity(query = "Интерстеллар", hasContent = false)
        val loading = identity(query = "Интерстеллар", searching = true)
        val results = identity(query = "Интерстеллар", hasContent = true)
        assertNotEquals(home, pending)
        assertNotEquals(pending, loading)
        assertNotEquals(loading, results)
        assertEquals("ready", results.phase)
    }

    @Test fun aNewSearchDoesNotPreservePreviousSearchPosition() {
        val old = identity(query = "Форсаж", hasContent = true)
        val next = identity(query = "Аватар", hasContent = true)
        assertNotEquals(old, next)
    }

    @Test fun changingTabShelfAndSourceForcesFreshTopPosition() {
        val current = identity(hasContent = true)
        assertNotEquals(current, identity(section = "Сериалы", hasContent = true))
        assertNotEquals(current, identity(shelf = "/collection/test", hasContent = true))
        assertNotEquals(current, identity(source = "custom", hasContent = true))
    }

    @Test fun stableResultsAndPaginationDoNotMoveUserBackToTop() {
        val first = identity(query = "Аватар", hasContent = true)
        val afterPagination = identity(query = "Аватар", hasContent = true)
        assertEquals(first, afterPagination)
    }

    @Test fun clearingQueryReturnsToStartOfHome() {
        val results = identity(query = "Аватар", hasContent = true)
        val cleared = identity(hasContent = true)
        assertNotEquals(results, cleared)
    }
}
