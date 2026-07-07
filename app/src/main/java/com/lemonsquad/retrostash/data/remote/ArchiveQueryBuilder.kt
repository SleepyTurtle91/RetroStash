package com.lemonsquad.retrostash.data.remote

object ArchiveQueryBuilder {

    /**
     * Transforms a raw user search string into an optimized Archive.org Lucene query.
     */
    fun buildOptimizedQuery(userInput: String): String {
        var sanitizedInput = userInput.trim()
        
        if (sanitizedInput.isEmpty()) {
            return "mediatype:(software)"
        }

        // Check for "all:" prefix for wide search
        val isWideSearch = sanitizedInput.startsWith("all:", ignoreCase = true)
        if (isWideSearch) {
            sanitizedInput = sanitizedInput.substring(4).trim()
        }

        // If the user is already using advanced syntax (contains a colon), return as is
        if (sanitizedInput.contains(":") && !isWideSearch) {
            return sanitizedInput
        }

        // Support partial matching by wrapping each term in wildcards if not already present
        val searchTerms = sanitizedInput.split("\\s+".toRegex())
            .filter { it.isNotBlank() }
            .joinToString(" ") { term ->
                if (term.contains("*") || term.contains(":")) {
                    term
                } else {
                    "$term*"
                }
            }

        // 1. The Lucene Boost: Forces the API to prioritize titles, identifiers, and subjects
        val boostedTerms = "(title:($searchTerms)^100 OR identifier:($searchTerms)^100 OR subject:($searchTerms)^50 OR ($searchTerms))"

        if (isWideSearch) {
            return boostedTerms
        }

        // 2. The Media Filter: Restricts results to software/games
        val mediaFilter = "mediatype:(software)"

        // 3. The Junk Filter: Hides common non-game files
        val excludeJunk = "-subject:(soundtrack OR manual OR magazine OR book)"

        // Combine everything into the final query string
        return "$boostedTerms AND $mediaFilter AND $excludeJunk"
    }
}
