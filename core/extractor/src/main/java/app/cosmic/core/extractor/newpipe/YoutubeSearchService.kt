package app.cosmic.core.extractor.newpipe

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.search.SearchExtractor
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-app YouTube search via NewPipeExtractor.
 *
 * Note: even though our YT *download* path uses yt-dlp (NewPipe loses the
 * stream-extraction arms race), the *search* endpoint is much more stable
 * because it doesn't have the same anti-bot fortification — it's the
 * normal search-results page YouTube serves to web crawlers.
 */
@Singleton
class YoutubeSearchService @Inject constructor(
    private val initializer: NewPipeInitializer,
) {

    data class SearchResult(
        val title: String,
        val uploader: String?,
        val durationSec: Long,
        val viewCount: Long?,
        val thumbnailUrl: String?,
        val url: String,
    )

    /**
     * Runs a single page of search. Returns first ~20 results. Caller can
     * dispatch additional pages via the optional next-page extractor in the
     * future; for now we keep it simple — search is a quick entry point,
     * not a deep browse.
     */
    suspend fun search(query: String): List<SearchResult> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.isEmpty()) return@withContext emptyList()
        initializer.ensureInitialized()

        val service = NewPipe.getService(ServiceList.YouTube.serviceId)
        val handlerFactory = service.searchQHFactory
        // Restrict to videos so we don't get channel/playlist hits for a music search.
        val contentFilters = listOf("videos")
        val handler = handlerFactory.fromQuery(q, contentFilters, "")
        val extractor: SearchExtractor = service.getSearchExtractor(handler)
        extractor.fetchPage()

        extractor.initialPage.items.mapNotNull { item ->
            val stream = item as? StreamInfoItem ?: return@mapNotNull null
            val url = stream.url ?: return@mapNotNull null
            SearchResult(
                title = stream.name ?: "",
                uploader = stream.uploaderName,
                durationSec = stream.duration.takeIf { it > 0 } ?: 0L,
                viewCount = stream.viewCount.takeIf { it >= 0 },
                thumbnailUrl = stream.thumbnails?.firstOrNull()?.url,
                url = url,
            )
        }
    }
}
