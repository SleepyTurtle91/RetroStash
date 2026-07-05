package com.lemonsquad.retrostash.data.remote

object ArchiveQueryBuilder {

    /**
     * Transforms a raw user search string into an optimized Archive.org Lucene query.
     */
    fun buildOptimizedQuery(userInput: String): String {
        val sanitizedInput = userInput.trim()
        
        if (sanitizedInput.isEmpty()) {
            return "mediatype:(software)"
        }

        // If the user is already using advanced syntax (contains a colon), return as is
        if (sanitizedInput.contains(":")) {
            return sanitizedInput
        }

        // 1. The Lucene Boost: Forces the API to prioritize titles, identifiers, and subjects
        val boostedTerms = "(title:(${sanitizedInput})^100 OR identifier:(${sanitizedInput})^100 OR subject:(${sanitizedInput})^50 OR (${sanitizedInput}))"

        // 2. The Media Filter: Restricts results to software/games
        val mediaFilter = "mediatype:(software)"

        // 3. The Junk Filter: Hides common non-game files
        val excludeJunk = "-subject:(soundtrack OR manual OR magazine OR book)"

        // Combine everything into the final query string
        return "$boostedTerms AND $mediaFilter AND $excludeJunk"
    }
}
