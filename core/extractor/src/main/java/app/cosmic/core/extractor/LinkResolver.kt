package app.cosmic.core.extractor

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Classifies a pasted URL into a SourceKind so the right Extractor runs.
 * Pure host/path matching — no network calls. Anything we don't recognise
 * is treated as DIRECT (the DirectUrlExtractor will then sniff content-type).
 */
@Singleton
class LinkResolver @Inject constructor() {

    fun resolve(rawUrl: String): SourceKind {
        val u = runCatching { java.net.URI(rawUrl.trim()) }.getOrNull() ?: return SourceKind.UNKNOWN
        val host = (u.host ?: "").lowercase().removePrefix("www.").removePrefix("m.")
        val path = u.path ?: ""

        return when {
            host == "music.youtube.com" -> when {
                path.startsWith("/playlist") -> SourceKind.YOUTUBE_PLAYLIST
                else -> SourceKind.YT_MUSIC
            }
            host == "youtube.com" || host == "youtu.be" -> when {
                path.startsWith("/playlist") || u.query.orEmpty().contains("list=") && !u.query.orEmpty().contains("v=") ->
                    SourceKind.YOUTUBE_PLAYLIST
                else -> SourceKind.YOUTUBE
            }
            host.endsWith("soundcloud.com") -> SourceKind.SOUNDCLOUD
            host.endsWith("bandcamp.com") -> SourceKind.BANDCAMP
            u.scheme in listOf("http", "https") -> SourceKind.DIRECT
            else -> SourceKind.UNKNOWN
        }
    }
}
