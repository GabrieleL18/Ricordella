package com.ricordella.app.data.repository

/** Trasforma un testo di ricerca in un pattern LIKE sicuro (con escape di %, _ e \). */
internal fun likePattern(query: String): String {
    val escaped = query.trim()
        .replace("\\", "\\\\")
        .replace("%", "\\%")
        .replace("_", "\\_")
    return "%$escaped%"
}
