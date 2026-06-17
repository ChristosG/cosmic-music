package app.cosmic.core.lyrics

/** One synchronised lyric line. */
data class LyricLine(val timestampMs: Long, val text: String)

/**
 * Parser for the LRC format (e.g. `[mm:ss.xx] some text`). Tolerates:
 * - multiple stamps on one line (`[00:01.00][00:05.00] hello`)
 * - metadata stamps like `[ti:Title]`, `[ar:Artist]` (skipped)
 * - 2-digit and 3-digit centisecond/millisecond fractions
 *
 * Invalid lines are skipped; parser never throws.
 */
object LrcParser {
    private val stamp = Regex("""\[(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?]""")

    fun parse(lrc: String): List<LyricLine> {
        val lines = mutableListOf<LyricLine>()
        for (raw in lrc.lineSequence()) {
            val matches = stamp.findAll(raw).toList()
            if (matches.isEmpty()) continue
            val text = raw.substring(matches.last().range.last + 1).trim()
            for (m in matches) {
                val mm = m.groupValues[1].toIntOrNull() ?: continue
                val ss = m.groupValues[2].toIntOrNull() ?: continue
                val frac = m.groupValues[3]
                val fracMs = when (frac.length) {
                    0 -> 0
                    1 -> frac.toInt() * 100
                    2 -> frac.toInt() * 10
                    3 -> frac.toInt()
                    else -> 0
                }
                val ts = mm * 60_000L + ss * 1_000L + fracMs
                lines += LyricLine(ts, text)
            }
        }
        return lines.sortedBy { it.timestampMs }
    }
}
