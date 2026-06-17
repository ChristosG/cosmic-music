package app.cosmic.core.prefs

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
    AMOLED,
    /** Light theme — pale dark-orange primary on warm white-gray. */
    SOLAR,
    /** Dark true-black OLED theme with an electric-yellow accent. */
    ELECTRIC,
}

enum class ReplayGainMode { OFF, TRACK, ALBUM }

/**
 * 5 bands × baked-in slider values — corresponds to the standard ExoPlayer
 * [Equalizer] band layout. Custom is stored as raw band gains in millibels.
 */
enum class EqPreset(val label: String, val gainsMillibels: IntArray) {
    FLAT("Flat",      intArrayOf(0, 0, 0, 0, 0)),
    BASS("Bass",      intArrayOf(600, 400, 0, 0, -200)),
    TREBLE("Treble",  intArrayOf(-200, 0, 0, 400, 600)),
    VOCAL("Vocal",    intArrayOf(-200, -100, 300, 200, -100)),
    ROCK("Rock",      intArrayOf(400, 200, -200, 200, 400)),
    POP("Pop",        intArrayOf(-200, 200, 400, 200, -200)),
    CUSTOM("Custom",  intArrayOf(0, 0, 0, 0, 0)),
}

data class CosmicPrefs(
    val theme: ThemeMode = ThemeMode.AMOLED,
    val dynamicColor: Boolean = true,
    val eqEnabled: Boolean = false,
    val eqPreset: EqPreset = EqPreset.FLAT,
    val eqCustomGains: IntArray = IntArray(5),
    val bassBoostStrength: Int = 0,         // 0..1000 (Android BassBoost.setStrength range)
    val crossfadeMs: Int = 0,                // 0 disables crossfade
    val replayGain: ReplayGainMode = ReplayGainMode.OFF,
    val smartShuffleEnabled: Boolean = false,
    val scanWholeMusicDir: Boolean = false,  // false = only Music/Cosmic; true = whole Music tree
) {
    // IntArray needs explicit equals/hashCode so data-class semantics behave.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CosmicPrefs) return false
        return theme == other.theme &&
            dynamicColor == other.dynamicColor &&
            eqEnabled == other.eqEnabled &&
            eqPreset == other.eqPreset &&
            eqCustomGains.contentEquals(other.eqCustomGains) &&
            bassBoostStrength == other.bassBoostStrength &&
            crossfadeMs == other.crossfadeMs &&
            replayGain == other.replayGain &&
            smartShuffleEnabled == other.smartShuffleEnabled &&
            scanWholeMusicDir == other.scanWholeMusicDir
    }

    override fun hashCode(): Int {
        var r = theme.hashCode()
        r = 31 * r + dynamicColor.hashCode()
        r = 31 * r + eqEnabled.hashCode()
        r = 31 * r + eqPreset.hashCode()
        r = 31 * r + eqCustomGains.contentHashCode()
        r = 31 * r + bassBoostStrength
        r = 31 * r + crossfadeMs
        r = 31 * r + replayGain.hashCode()
        r = 31 * r + smartShuffleEnabled.hashCode()
        r = 31 * r + scanWholeMusicDir.hashCode()
        return r
    }
}
