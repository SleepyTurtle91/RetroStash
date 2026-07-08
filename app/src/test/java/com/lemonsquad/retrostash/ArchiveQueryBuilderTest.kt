package com.lemonsquad.retrostash

import com.lemonsquad.retrostash.data.remote.ArchiveCategory
import com.lemonsquad.retrostash.data.remote.ArchiveQueryBuilder
import org.junit.Assert.assertTrue
import org.junit.Test

class ArchiveQueryBuilderTest {

    @Test
    fun testBuildOptimizedQuery_All() {
        val query = ArchiveQueryBuilder.buildOptimizedQuery("sonic", ArchiveCategory.ALL)
        assertTrue(query.contains("title:(sonic*)"))
        assertTrue(query.contains("-subject:(soundtrack"))
    }

    @Test
    fun testBuildOptimizedQuery_Roms() {
        val query = ArchiveQueryBuilder.buildOptimizedQuery("mario", ArchiveCategory.ROMS)
        assertTrue(query.contains("mediatype:(software)"))
        assertTrue(query.contains("subject:(rom)"))
        assertTrue(query.contains("-subject:(soundtrack"))
    }

    @Test
    fun testBuildOptimizedQuery_Books() {
        val query = ArchiveQueryBuilder.buildOptimizedQuery("zelda", ArchiveCategory.BOOKS)
        assertTrue(query.contains("mediatype:(texts)"))
        // Should NOT contain junk filter for books
        assertTrue(!query.contains("-subject:(book)"))
    }

    @Test
    fun testBuildOptimizedQuery_Movies() {
        val query = ArchiveQueryBuilder.buildOptimizedQuery("batman", ArchiveCategory.MOVIES)
        assertTrue(query.contains("mediatype:(movies)"))
    }

    @Test
    fun testBuildOptimizedQuery_MultiWord() {
        val query = ArchiveQueryBuilder.buildOptimizedQuery("pokemon ultra moon", ArchiveCategory.ALL)
        // Check that terms are ANDed together
        assertTrue(query.contains("pokemon* AND ultra* AND moon*"))
    }

    @Test
    fun testBuildOptimizedQuery_PartialID() {
        val query = ArchiveQueryBuilder.buildOptimizedQuery("miiverse", ArchiveCategory.ALL)
        // Check that identifier uses double wildcards for partial matching
        assertTrue(query.contains("identifier:(*miiverse*)"))
    }
}
