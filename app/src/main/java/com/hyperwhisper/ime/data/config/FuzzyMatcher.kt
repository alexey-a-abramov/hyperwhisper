package com.hyperwhisper.data.config

/**
 * Fuzzy string matching used to resolve free-form spoken values ("ocean deep",
 * "Spanish") onto canonical config values. Extracted from the retired
 * VoiceCommandProcessor so patch validation and language resolution share one
 * implementation.
 */
object FuzzyMatcher {

    /**
     * Find the item whose candidate names best match [query].
     * Resolution order: exact (case-insensitive) → substring containment →
     * Levenshtein distance within max(3, 50% of query length). Returns null
     * when nothing is reasonably close.
     */
    fun <T> closest(query: String, items: List<T>, namesOf: (T) -> List<String>): T? {
        val q = query.lowercase().trim()
        if (q.isEmpty()) return null

        var bestSubstring: T? = null
        var bestSubstringDistance = Int.MAX_VALUE
        var bestFuzzy: T? = null
        var bestFuzzyDistance = Int.MAX_VALUE

        for (item in items) {
            for (name in namesOf(item)) {
                val n = name.lowercase().trim()
                if (n.isEmpty()) continue
                if (n == q) return item // exact wins immediately

                val distance = levenshteinDistance(q, n)
                if (n.contains(q) || q.contains(n)) {
                    if (distance < bestSubstringDistance) {
                        bestSubstringDistance = distance
                        bestSubstring = item
                    }
                } else if (distance < bestFuzzyDistance) {
                    bestFuzzyDistance = distance
                    bestFuzzy = item
                }
            }
        }

        if (bestSubstring != null) return bestSubstring

        val threshold = (q.length * 0.5).toInt().coerceAtLeast(3)
        return if (bestFuzzyDistance <= threshold) bestFuzzy else null
    }

    /** Classic Levenshtein edit distance. */
    fun levenshteinDistance(s1: String, s2: String): Int {
        val m = s1.length
        val n = s2.length
        val dp = Array(m + 1) { IntArray(n + 1) }

        for (i in 0..m) dp[i][0] = i
        for (j in 0..n) dp[0][j] = j

        for (i in 1..m) {
            for (j in 1..n) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,      // deletion
                    dp[i][j - 1] + 1,      // insertion
                    dp[i - 1][j - 1] + cost // substitution
                )
            }
        }

        return dp[m][n]
    }
}
