# Cosmic

Android music player with multi-source download (YouTube, SoundCloud, Bandcamp, direct URL),
Media3-powered background playback, lyrics, EQ, smart shuffle, and a polished UX. Sideload-only.

See `/home/chris/.claude/plans/ok-so-i-want-cosmic-widget.md` for the full design.

## Status — slice 11 (the real two-player crossfade)

`CrossfadingMediaPlayer` — a `SimpleBasePlayer` subclass that wraps two
ExoPlayer instances and exposes them as a single `Player` to MediaSession.
This is the architecture serious Android music players use; previous slices
had two players but bolted them on top of a primary that owned the queue,
which caused a tiny seek-hop artifact at every crossfade boundary. Now:

- MediaSession sees one `Player`. Internally we route to slot A or slot B.
- Crossfade is true overlap: both slots play simultaneously during the fade
  window with mirrored volume curves.
- At boundary, we just **swap which slot is "current"**. No seek, no re-queue,
  no underlying-player auto-transition involved. The audible result is
  continuous: slot A fades to silence while slot B has been fading in to
  full volume; we then pause/clear slot A and slot B continues.
- ReplayGain factor is published into the wrapper directly via
  `setReplayGainFactor()` and composed with the fade-fraction math; the old
  VolumeMixer + standalone CrossfadeController are deleted.
- EQ + BassBoost effects bind to BOTH slots' audio session ids so they
  don't drop out across a crossfade.

Files dropped: `VolumeMixer.kt`, `CrossfadeController.kt`. Files added/rewritten:
`CrossfadingMediaPlayer.kt`, `CosmicPlaybackService.kt`,
`ReplayGainController.kt`, `AudioEffectsController.kt`.

## Status — slice 10 (deferred-list cleanup)

The four "next round" items are all in:

- **True overlap crossfade** — `CrossfadeController` rewritten with a secondary `ExoPlayer` lazily allocated at first crossfade need. Plays the overlap window of the next track at fade-in volume while primary fades out. On primary's auto-transition we seek primary to where secondary is and tear secondary down. Real overlap during the fade window; residual ~10–30ms artifact at the seek-hop boundary (the user-space ceiling without a custom `SimpleBasePlayer` wrapper).
- **Tag editor** — `TagWriter` in `:core:metadata` writes title/artist/album back to the file via JAudioTagger, updates the Track row immediately, pushes the change to MediaStore via ContentResolver, pings MediaScannerConnection so other music apps see the edit. Wired into the long-press menu's **Track details** action via a bottom sheet. Fixes `<unknown>` artists on old downloads.
- **Drag-to-reorder queue** — `sh.calvin.reorderable` library; long-press the drag handle on a queue row to grab + drag. Wired through `PlaybackController.moveQueueItem` → `MediaController.moveMediaItem` so the underlying ExoPlayer queue updates in lockstep with the visual reorder.
- **Slide-up Now Playing transition** — `composable` `enterTransition` / `popExit` etc. configured on the NowPlaying route. Tap mini-player → Now Playing rises from the bottom; back → it slides down.

Still pending (real future-slice scope):
- Visual shared-element album-art morph between mini-player and Now Playing — needs MiniPlayer inside a `composable` scope, not `Scaffold.bottomBar`.
- Cover-art-aware backdrop ("hot" colors extracted from the actual album art) — needs cover-art loading first.

## Status — slice 9 (visual signature + Now Playing redesign + queue)

The app now has its own look instead of stock Material You. Lots of small wins compounding:

- **Custom typography**: Outfit (body) + Space Grotesk (display) via Compose downloadable fonts. Distinctive, modern, geometric — the title-on-Now-Playing now feels intentional instead of system-default.
- **Cosmic palette**: deep violet-black background with electric-violet primary + soft cyan accent + rose tertiary. Dynamic Material You still active when SDK 31+, falls back to Cosmic palette otherwise. AMOLED variant for true-black.
- **Now Playing redesign**: per-track gradient backdrop (deterministic hash → consistent vibe per track, varies between tracks), large album-art placeholder with primary-tinted ambient shadow, marquee for long titles, slim Track / Queue / Lyrics tab switcher with animated content swap.
- **Queue tab**: shows the upcoming queue with current-track highlight; tap any item to jump, X to remove. `PlaybackController.queue` exposes a Flow of QueueItem so future drag-to-reorder UI plugs in cleanly.
- **TrackRow**: gradient album-art tile (per-track-id seed), press-scale animation (97% on tap), highlighted-row marquee. Library/Search/Playlist Detail all share it.
- **MiniPlayer**: gradient album-art tile matches the row design — visual continuity across rows ↔ mini-player ↔ Now Playing backdrop.
- **YT search caching**: 5-min LRU keyed by `query::count` so repeated searches don't pay the yt-dlp cost.

Still pending (next round):
- True overlap crossfade (dual-ExoPlayer SimpleBasePlayer wrapper)
- Tag editor / re-scan for `<unknown>` artists from old downloads
- Drag-to-reorder queue
- Now Playing → mini-player shared-element transition (Compose 1.7+ SharedTransitionLayout)

## Status — slice 8 (crossfade + YT search + lyrics fixes)

- **Crossfade engine wired** — `VolumeMixer` multiplexes `replayGain × crossfade × ...` so RG and crossfade controllers never fight over `ExoPlayer.volume`. `CrossfadeController` ramps the crossfade factor 1→0 in the last `crossfadeMs` of a track and 0→1 in the first `crossfadeMs` of the next. **Honest scope note:** this is fade-out + fade-in via single ExoPlayer, not true overlap. There's a brief silence at the boundary. True overlap needs a dual-ExoPlayer SimpleBasePlayer wrapper — a focused future slice.
- **In-app YouTube search** — new `YoutubeSearchScreen` reachable via "Search YouTube" FAB on the Downloads tab. NewPipeExtractor-driven (works fine for search even though we use yt-dlp for stream extraction); Coil 3 + OkHttp loads thumbnails; per-row "Download" tonal-icon button enqueues to the existing pipeline.
- **Lyrics: request coalescing** — per-trackId `Mutex` in `LyricsRepository` so 8 concurrent observers (MiniPlayer + Now Playing + recompose triggers) make 1 LRCLIB call, not 8.
- **Lyrics: dirty-metadata cleanup** — `TitleCleaner` in `:core:common` strips junk (`[Official Audio]`, `(HD)`, etc.), recovers artist from `Artist - Title` when the field is `<unknown>` (MediaStore sentinel for unscanned files), and feeds normalised inputs to LRCLIB. Also fixed: LRCLIB returning 200 with empty content no longer short-circuits — falls through to `/api/search` properly.
- **Coil 3 wired to shared OkHttp** — `CosmicApp` is now a `SingletonImageLoader.Factory` providing an ImageLoader that reuses our app-wide OkHttp instance (cookies, connection pool, timeouts).

## Status — slice 7 (the big one)

This slice swaps out the broken `youtubedl-android` jitpack dep, modernises the entire
build stack, refines the UI across the app, adds playlists with a shared long-press menu,
ships a real smart-shuffle algorithm, and wires ReplayGain end-to-end.

### YouTube downloads — actually working now

The previous chain (NewPipe → 0.15.0 wrapper → bundled Python 3.8 → modern yt-dlp) was a
dead end: NewPipe loses the YT bot war, jitpack can't build wrapper >= 0.16 (JDK 17
missing on their builders), and modern yt-dlp requires Python 3.10+. So:

- We **build the `youtubedl-android` 0.18.1 wrapper locally** (Python 3.11 bundled) and
  ship the AAR in `core/extractor/libs/`. APK grows ~57MB but YT downloads just work.
- `YtDlpInitializer.warmUp()` runs at `Application.onCreate()` — extracts the Python
  tarball on first launch (~10s) then **self-updates yt-dlp from upstream** so the YT
  parser stays current without rebuilds.
- `ExtractorRegistry` routes `YOUTUBE` / `YT_MUSIC` / `YOUTUBE_PLAYLIST` to yt-dlp;
  `SOUNDCLOUD` stays on NewPipe (works fine there); `BANDCAMP` stays on Jsoup; direct URL
  unchanged.
- `scripts/build-youtubedl-aar.sh` rebuilds the AAR from any upstream tag. Run it to bump.

### Stack bumped to current stable (May 2026)

| Component | From | To |
|---|---|---|
| AGP | 8.5.2 | 8.13.2 |
| Kotlin | 2.0.20 | 2.3.21 |
| KSP | 2.0.20-1.0.25 | 2.3.7 |
| JVM target | 17 | 21 |
| compileSdk / targetSdk | 35 / 34 | 36 / 36 |
| Compose BOM | 2024.09.03 | 2025.12.01 |
| Media3 | 1.4.1 | 1.10.0 |
| Hilt | 2.52 | 2.59.2 |
| Room | 2.6.1 | 2.8.4 |
| WorkManager | 2.9.1 | 2.11.2 |
| Navigation | 2.8.2 | 2.9.8 |
| Lifecycle | 2.8.6 | 2.10.0 |
| Activity Compose | 1.9.2 | 1.13.0 |
| DataStore | 1.1.1 | 1.2.1 |
| OkHttp | 4.12.0 | 5.3.2 |
| Coroutines | 1.9.0 | 1.10.2 |
| kotlinx-serialization | 1.7.2 | 1.10.0 |
| Coil | 2.7.0 | 3.4.0 |
| Gradle wrapper | 8.9 | 8.14.3 |

### UI polish

- **Library**: `LargeTopAppBar` with collapsing behavior, track count under the title,
  cleaner icon layout (Search · Smart shuffle · Rescan); track rows now use a reusable
  `TrackRow` with album-art tile and subtitle; long-press → `TrackContextMenu`.
- **Search**: clean inline-clear button; same `TrackRow` for visual consistency.
- **Downloads**: full-width URL input with smart trailing icon (paste-from-clipboard
  when empty, Download FAB-style icon when filled); helper subtitle lists supported
  sources; long-press a failed row's error to copy the full Python traceback.
- **Theme**: prefs-reactive — flip from System / Light / Dark / AMOLED in Settings and
  the whole app recomposes instantly.

### Playlists (`:feature:playlists`)

- `PlaylistsScreen` — bottom-nav destination, ExtendedFAB to create, per-row hue-shifted
  gradient placeholder, opens detail on tap.
- `PlaylistDetailScreen` — Play / Shuffle prominent buttons, track count, long-press a
  track row to remove/move/etc., Edit + Delete in the top bar.
- `AddToPlaylistSheet` (in `:feature:common`) — surfaces from the long-press menu's
  "Add to playlist…" action, with "+ New playlist" creating + adding in one step.

### Long-press song menu (`:feature:common`)

`TrackContextMenu` is a `ModalBottomSheet` with: Play now · Play next · Add to queue ·
Add to playlist… · Tag… · Track details · Remove from device. Lives in `:feature:common`
so Library, Search, and Playlists all share one implementation. (Play next / Add to
queue / Tag / Details / Delete still wired as TODO callbacks — UI is consistent, engine
hooks come next slice.)

### Smart shuffle — real algorithm (`:core:shuffle`)

Replaces the `.shuffled()` stub. `SmartShuffle.buildQueue()` scores each candidate as:

```
score = 0.35·playCountNorm
      + 0.25·recencyDecay     # 0.1× if played in last 24h, ramp to 1.5× after 30 days
      + 0.30·tagAffinity      # shared-tags / seed-tags
      − 0.10·skipPenalty
      + 0.05·ε                # baseline so unfamiliar tracks aren't starved
```

Then roulette-wheel samples without replacement. Library smart-shuffle button now uses
this. Weights tunable from Settings (currently fixed; sliders are a 1-line addition).

### ReplayGain end-to-end

- `TagReader` (`:core:metadata`) reads `REPLAYGAIN_TRACK_GAIN` / `REPLAYGAIN_ALBUM_GAIN`
  via JAudioTagger when `MediaStoreScanner` first encounters a track.
- `ReplayGainController` (`:core:player`) listens to track transitions, looks up the
  stored gain, computes `volume = 10^(gain_dB/20)`, applies via `ExoPlayer.volume`.
- Hooked into `CosmicPlaybackService` alongside the existing `AudioEffectsController`.
- Mode (Off / Track / Album) reads from `PreferencesRepository` and re-applies live.

### Crossfade — UI ready, engine pending

Settings already saves the crossfade duration. The actual engine (dual-ExoPlayer
adapter that wraps Media3's `Player` interface and ramp-fades volumes at boundaries)
is the next slice — it's a legit chunk of state-machine work that deserves a focused
session, not an end-of-night sprint.

## Build

```bash
cd /mnt/nvme2TB/mp3mobile
./gradlew :app:installDebug
```

First-run cold start: ~10–20s for the yt-dlp Python tarball to extract + the
self-update to fetch fresh yt-dlp. Subsequent launches: ~1s. Build itself: first time
needs all the Maven/jitpack deps (~3-5 min), incremental builds are seconds.

If you ever want to bump the bundled wrapper (e.g. Python 3.12 lands upstream):

```bash
./scripts/build-youtubedl-aar.sh 0.19.0   # or whatever tag
./gradlew :app:installDebug
```

## Layout

```
app/                          # MainActivity, Application, theme, nav, res
core/
  common/                     # shared constants
  db/                         # Room: entities, DAOs, database, DI module
  metadata/                   # MediaStoreScanner + TagReader (RG)
  player/                     # CosmicPlaybackService, PlaybackController,
                              # AudioEffectsController, ReplayGainController
  prefs/                      # DataStore-Preferences PreferencesRepository
  shuffle/                    # SmartShuffle weighted scoring
  download/                   # DownloadWorker + MediaStoreWriter + repository
  extractor/                  # ExtractorRegistry
    libs/                     # local AARs: youtubedl-android-{library,common}-0.18.1.aar
    ytdlp/                    # YtDlpInitializer + YtDlp{,Music,Playlist}Extractor
    newpipe/                  # NewPipe-based SoundCloud extractor (kept; old YT ones inert)
    bandcamp/                 # Jsoup scraper for free-stream Bandcamp pages
  lyrics/                     # LRCLIB client + LRC parser + cache
feature/
  common/                     # TrackRow, TrackContextMenu, AddToPlaylistSheet
  library/                    # LibraryScreen + LibraryViewModel
  search/                     # SearchScreen (fuzzywuzzy local search)
  playlists/                  # PlaylistsScreen + PlaylistDetailScreen
  download/                   # DownloadScreen + DownloadViewModel
  nowplaying/                 # NowPlayingScreen + MiniPlayer + LyricsView
  settings/                   # SettingsScreen + SettingsViewModel
scripts/
  build-youtubedl-aar.sh      # one-shot rebuild of the local yt-dlp wrapper AAR
```

## Modules' bottom nav

Library · Playlists · Downloads · Settings (Search lives behind a magnifier in the
Library top bar; Now Playing lives behind the mini-player above the bottom nav).
