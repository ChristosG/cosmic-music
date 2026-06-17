# 🎵 Cosmic

A music player for Android that downloads songs from YouTube as audio and plays them offline — with playlists, lyrics, background playback, and smart shuffle.

## 📥 Download

### [⬇️ Download the latest APK](https://github.com/ChristosG/cosmic-music/releases/latest)

Tap the link above on your Android phone, download the `.apk`, and open it to install.

> First time installing an app outside the Play Store? Your phone may ask you to **allow installing from this source** — tap **Settings → Allow**, then open the file again. That's normal and safe.

## ▶️ How to use

1. Open **Cosmic** and go to the **Downloads** tab.
2. Paste a YouTube (or YouTube Music) link and tap **Download** — or use the **search** in the Library to find a song without leaving the app.
3. The song lands in your **Library**, ready to play offline.
4. Make **Playlists**, see **Lyrics**, and let **Smart Shuffle** build a mix for you.

Downloaded songs are saved to your phone's **Music/Cosmic** folder, so they stay even if you uninstall the app.

## ✨ Features

- Download from YouTube / YouTube Music as audio
- Offline playback with background play & a lock-screen player
- Playlists, lyrics, and an equalizer
- Smart shuffle that learns what you like
- Gapless crossfade between tracks

## 🔄 Updates

New versions are published on the [Releases page](https://github.com/ChristosG/cosmic-music/releases). Just download the newer `.apk` and install it over the old one — **your playlists, settings, and songs are kept**.

---

## 🛠️ For developers

Build a debug build to a connected device:

```bash
./gradlew :app:installDebug
```

First launch takes ~10–20s while the yt-dlp engine unpacks; after that it's instant. Every push to `main` builds a signed release APK via GitHub Actions; pushing a `v*` tag publishes it to Releases.

To bump the bundled yt-dlp engine:

```bash
./scripts/build-youtubedl-aar.sh 0.19.0   # or any upstream tag
```
