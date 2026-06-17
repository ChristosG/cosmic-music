package app.cosmic.core.common

/**
 * Helpers for cleaning up song metadata that was lifted from messy sources
 * (YouTube video titles, "<unknown>" MediaStore artist sentinels, etc.)
 * before sending it to lookup APIs like LRCLIB.
 *
 * The transformations here are deliberately *lossy* — they assume the goal
 * is "good enough for a fuzzy lookup," not "preserve every character."
 */
object TitleCleaner {

    private val junkBracket = Regex(
        """\s*[\[(](?:""" +
            "official\\s*(?:music\\s*)?(?:video|audio|lyric(?:s)?\\s*video|visualizer)|" +
            "audio|video|hd|hq|4k|m/v|mv|lyric(?:s)?|" +
            "lyrical\\s*video|visual(?:izer)?|live|live\\s*performance|" +
            "remastered(?:\\s+\\d{4})?|extended\\s*(?:version|mix)|radio\\s*edit|" +
            "feat\\.[^)\\]]*|featuring[^)\\]]*" +
            """)[\])]\s*""",
        RegexOption.IGNORE_CASE,
    )

    private val unknownArtistMarkers = setOf(
        "<unknown>", "unknown", "unknown artist", "untitled",
    )

    private val artistTitleSeparator = Regex("""\s+[-–—:]\s+""")

    /** True if the value is null/blank/MediaStore's "<unknown>" sentinel. */
    fun isUnknownArtist(s: String?): Boolean {
        if (s.isNullOrBlank()) return true
        val trimmed = s.trim().lowercase()
        return trimmed.startsWith("<") || trimmed in unknownArtistMarkers
    }

    /** Strips trailing "[Official Audio]", "(HD)", etc. and collapses whitespace. */
    fun cleanSongTitle(title: String): String =
        title
            .replace(junkBracket, " ")
            .replace(Regex("""\s+"""), " ")
            .trim()

    /**
     * Best-effort split of a string like "Artist - Song Title (Official Audio)"
     * into a (artist, title) pair. Returns null if no separator was found.
     *
     * Heuristic: if the LEFT side looks like a channel name (e.g. ends with
     * "VEVO", "Records", "Music"), treat that as the artist anyway — it's
     * still our best signal.
     */
    fun splitArtistAndTitle(raw: String): Pair<String, String>? {
        val cleaned = cleanSongTitle(raw)
        val parts = cleaned.split(artistTitleSeparator, limit = 2)
        if (parts.size != 2) return null
        val left = parts[0].trim()
        val right = parts[1].trim()
        if (left.isBlank() || right.isBlank()) return null
        return left to right
    }

    /**
     * Normalises a (rawArtist, rawTitle) pair for lookup: strips junk from
     * the title, recovers the artist from "Artist - Title" patterns when
     * the artist field is unknown.
     */
    fun normaliseForLookup(rawArtist: String?, rawTitle: String): Pair<String?, String> {
        val cleanedTitle = cleanSongTitle(rawTitle)
        if (isUnknownArtist(rawArtist)) {
            splitArtistAndTitle(rawTitle)?.let { (a, t) ->
                return a to cleanSongTitle(t)
            }
        }
        return rawArtist to cleanedTitle
    }
}
