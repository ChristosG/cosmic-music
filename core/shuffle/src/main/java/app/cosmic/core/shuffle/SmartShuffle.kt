package app.cosmic.core.shuffle

import app.cosmic.core.db.dao.PlayHistoryDao
import app.cosmic.core.db.dao.TagDao
import app.cosmic.core.db.dao.TrackDao
import app.cosmic.core.db.entity.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.random.Random

/**
 * Weighted-history smart shuffle. Builds a fixed-size queue starting from a
 * seed track (current/last played). Each candidate gets a score; we then
 * sample without replacement with probability ∝ score.
 *
 * Score components (per the original design doc):
 * - playCountNorm: 0..1, log-normalised against the library max
 * - recencyDecay: songs played in the last 24h get a 0.1x penalty,
 *   songs not heard for 30+ days get a 1.5x boost (resurfacing buried gems)
 * - tagAffinity: count of tags shared with the seed / max(seed_tags, 1)
 * - skipPenalty: skipCount / (playCount+skipCount), clamped 0..1
 *
 * Default weights: w1=0.35 plays, w2=0.25 recency, w3=0.30 tags, w4=0.10 skips.
 * Plus an epsilon=0.05 randomness floor so unfamiliar tracks aren't starved.
 *
 * The output queue starts with the seed (so playback continues from the user's
 * action), followed by `count - 1` weighted-sampled tracks, no duplicates.
 */
@Singleton
class SmartShuffle @Inject constructor(
    private val trackDao: TrackDao,
    private val playHistoryDao: PlayHistoryDao,
    private val tagDao: TagDao,
) {
    data class Weights(
        val playCount: Double = 0.35,
        val recency: Double = 0.25,
        val tagAffinity: Double = 0.30,
        val skipPenalty: Double = 0.10,
        val epsilon: Double = 0.05,
    )

    suspend fun buildQueue(
        seed: Track,
        library: List<Track>,
        count: Int = 50,
        weights: Weights = Weights(),
        random: Random = Random.Default,
    ): List<Track> = withContext(Dispatchers.Default) {
        if (library.isEmpty()) return@withContext emptyList()
        val pool = library.filter { it.id != seed.id }
        if (pool.isEmpty()) return@withContext listOf(seed)

        val stats = playHistoryDao.stats().associateBy { it.trackId }
        val maxPlays = stats.values.maxOfOrNull { it.playCount } ?: 0
        val seedTags = tagDao.tagsFor(seed.id).toSet()
        val now = System.currentTimeMillis()

        val candidates = pool.map { t ->
            val s = stats[t.id]
            val pc = s?.playCount ?: 0
            val sc = s?.skipCount ?: 0
            val lastPlayed = s?.lastPlayedAt

            val playCountNorm = if (maxPlays > 0) ln(1.0 + pc) / ln(1.0 + maxPlays) else 0.0
            val recency = recencyMultiplier(now, lastPlayed)
            val skipRatio = if (pc + sc > 0) sc.toDouble() / (pc + sc).toDouble() else 0.0
            val tagAffinity = computeTagAffinity(t.id, seedTags, tagDao)

            val score = weights.playCount * playCountNorm +
                weights.recency * recency +
                weights.tagAffinity * tagAffinity -
                weights.skipPenalty * skipRatio +
                weights.epsilon
            t to max(score, weights.epsilon)
        }

        val out = ArrayList<Track>(count)
        out += seed
        // Roulette-wheel sample without replacement.
        val mutable = candidates.toMutableList()
        var totalWeight = mutable.sumOf { it.second }
        repeat(count - 1) {
            if (mutable.isEmpty() || totalWeight <= 0.0) return@repeat
            var roll = random.nextDouble() * totalWeight
            var pickedIdx = -1
            for (i in mutable.indices) {
                roll -= mutable[i].second
                if (roll <= 0.0) { pickedIdx = i; break }
            }
            if (pickedIdx == -1) pickedIdx = mutable.lastIndex
            val (track, w) = mutable.removeAt(pickedIdx)
            out += track
            totalWeight -= w
        }
        out
    }

    /**
     * Multiplier in roughly [0.1, 1.5]. Played in the last 24h → 0.1.
     * Unplayed → 1.0. Played 30+ days ago → 1.5 (resurface buried gems).
     */
    private fun recencyMultiplier(now: Long, lastPlayedAt: Long?): Double {
        if (lastPlayedAt == null) return 1.0
        val hours = (now - lastPlayedAt) / 3_600_000.0
        return when {
            hours < 24 -> 0.1
            hours < 7 * 24 -> 0.5 + 0.5 * (hours - 24) / (6 * 24) // ramp 0.5→1.0 over a week
            hours < 30 * 24 -> 1.0
            else -> 1.5 - 0.5 * exp(-(hours - 30 * 24) / (60.0 * 24)) // asymptote to ~1.5
        }.coerceIn(0.1, 1.5)
    }

    private suspend fun computeTagAffinity(trackId: Long, seedTags: Set<Long>, tagDao: TagDao): Double {
        if (seedTags.isEmpty()) return 0.0
        val tTags = tagDao.tagsFor(trackId).toSet()
        if (tTags.isEmpty()) return 0.0
        val shared = tTags.intersect(seedTags).size
        return shared.toDouble() / seedTags.size.toDouble()
    }
}
