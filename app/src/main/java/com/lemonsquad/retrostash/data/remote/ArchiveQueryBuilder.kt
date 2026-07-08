package com.lemonsquad.retrostash.data.remote

object ArchiveQueryBuilder {

    /**
     * Transforms a raw user search string into an optimized Archive.org Lucene query.
     */
    fun buildOptimizedQuery(userInput: String, category: ArchiveCategory = ArchiveCategory.ALL): String {
        var sanitizedInput = userInput.trim()
        
        if (sanitizedInput.isEmpty()) {
            return applyCategoryFilter("mediatype:(software)", category)
        }

        // Check for "all:" prefix for wide search (legacy support)
        val isWideSearch = sanitizedInput.startsWith("all:", ignoreCase = true) || category == ArchiveCategory.ALL
        if (sanitizedInput.startsWith("all:", ignoreCase = true)) {
            sanitizedInput = sanitizedInput.substring(4).trim()
        }

        // If the user is already using advanced syntax (contains a colon), return as is, but still wrap if needed
        if (sanitizedInput.contains(":") && !isWideSearch) {
            return applyCategoryFilter(sanitizedInput, category)
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

        return applyCategoryFilter(boostedTerms, category)
    }

    private fun applyCategoryFilter(query: String, category: ArchiveCategory): String {
        val categoryFilter = when (category) {
            ArchiveCategory.ALL -> ""
            ArchiveCategory.SOFTWARE -> "AND mediatype:(software)"
            ArchiveCategory.ROMS -> "AND mediatype:(software) AND (subject:(rom) OR subject:(roms))"
            ArchiveCategory.PC_GAMES -> "AND mediatype:(software) AND subject:(\"pc games\")"
            ArchiveCategory.BOOKS -> "AND mediatype:(texts)"
            ArchiveCategory.MOVIES -> "AND mediatype:(movies)"
            ArchiveCategory.AUDIO -> "AND (mediatype:(audio) OR mediatype:(etree))"
            ArchiveCategory.IMAGES -> "AND mediatype:(image)"
            ArchiveCategory.DATA -> "AND mediatype:(data)"
        }

        // The Junk Filter: Hides common non-game files, but disable it for non-software categories
        val excludeJunk = if (category == ArchiveCategory.SOFTWARE || category == ArchiveCategory.ROMS || category == ArchiveCategory.PC_GAMES || category == ArchiveCategory.ALL) {
            "AND -subject:(soundtrack OR manual OR magazine OR book)"
        } else {
            ""
        }

        return "$query $categoryFilter $excludeJunk".trim().replace("\\s+".toRegex(), " ")
    }
}
