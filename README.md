# Mp3Player Application

A modern, high-fidelity Android Music Player built with **Jetpack Compose**, **ExoPlayer**, **Room**, and a custom **FastAPI** backend. It features seamless 5-second crossfading playback, smart weighted shuffle statistics, wireless earbud/headset gestures, lock-screen controls, a 3-section queue manager, system equalizer integration, interactive widgets, and background audio downloading via YouTube Music.

---

## 🏗️ Architecture Overview

```
 ┌────────────────────────┐         Network         ┌────────────────────────┐
 │   Android Frontend     │ ──────────────────────> │    FastAPI Backend     │
 │ (Jetpack Compose, Room)│ <────────────────────── │ (yt-dlp, ffmpeg, tags) │
 └────────────────────────┘                         └────────────────────────┘
```

1. **Android Client (Jetpack Compose & Room)**:
   - Single-Activity architecture powered by Jetpack Compose, Room DB, and Kotlin Coroutines.
   - Bound `AudioService` running two `ExoPlayer` instances for seamless 5-second crossfading playback.
   - Reactive state management with `StateFlow` and `combine` operators.
   - Local PNG cover caching (`PlaylistCoverManager`) for 0ms cover loading and 100% visual consistency across app UI and system widgets.

2. **Python Backend (FastAPI)**:
   - Searches YouTube Music using `yt-dlp`.
   - Resolves direct audio stream URLs for instant preview playback.
   - Downloads, extracts `.mp3` audio, scrapes high-res album art, and tags files with ID3 metadata using `mutagen`.

---

## ✨ Features

### 🎧 Playback & Earbud Gestures
- **Wireless Earbud / Headset Controls**: Single-tap to play/pause, double-tap to skip tracks via OS `MediaSessionCompat.Callback`.
- **Auto-Pause on Disconnect**: `AUDIO_BECOMING_NOISY` receiver automatically pauses playback when Bluetooth or wired headphones are unplugged.
- **Lock-Screen Transport Controls**: Broadcasts `PlaybackStateCompat` and active media notifications directly to the system lock screen.
- **5s Crossfading**: Dual-ExoPlayer engine seamlessly crossfades between ending and upcoming tracks.

### 📋 3-Section Queue Logic
- **Sectioned Queue Overview**:
  1. **Now Playing**: Highlights the active track.
  2. **Added to Queue**: Manually enqueued tracks with distinct styling.
  3. **Next from {Playlist}**: Auto-populated sequential playlist queue.
- **Mid-Playlist Sequential Playback**: Tapping song #89 in a playlist builds the queue sequentially from 89 (89 → 90 → 91... → 0 → 88).
- **Cross-Section Drag-and-Drop**: Drag songs across sections to customize playback sequence.

### 📱 High-Performance Home Screen Widgets
- **1-Tap Idle Play**: Shows top playlist title (*"My Playlist"*) and 3×3 cover collage when idle; tapping Play (`▶`) starts the top playlist.
- **6-Upcoming Queue Tracks Row**: Displays thumbnail slots for the next 6 upcoming tracks with 1-tap direct play intents.
- **3×3 Playlist Grid Collage**: Renders top 9 most-played tracks as a 3×3 grid cover.
- **Zero-Flicker Progress Updates**: Employs `partiallyUpdateAppWidget` for 500ms progress ticks without re-rendering artwork or causing image flickering.
- **Soft Rounded Corners**: Masking transformations apply soft 24px/32px rounded corners to all widget images.

### 🏠 Modern Homepage (`HomeScreen`)
- **Greeting Banner & Quick Resume**: Hero quick-resume card for your current or last-played playlist.
- **Quick Action Chips**: Instant shortcuts for 🔀 *Shuffle All*, 🎵 *All Tracks*, 📊 *Library Stats*, and ⚡ *Queue*.
- **Horizontal Scroll Rows**: Recently Played horizontal scroll row and My Playlists section.

### 📊 Playlist & Global Library Statistics
- **Sortable Stats Screen**: Multi-column sorting by **Title**, **Plays**, **Skips**, **Skip Rate (%)**, and **Keepers**.
- **Playlist & Global Scopes**: Dedicated stats view accessible directly from playlist details or globally from Settings.

### 🔀 Smart Weighted Shuffle Engine
- **Skip Penalty**: High-frequency skips reduce a song's selection probability.
- **Skipped-To Bonus**: Tracks selected immediately after a skip receive a probability selection bonus.
- **Manual Weight Adjuster**: Fine-tune song likelihood multipliers (`0.1x` to `5.0x`).

### 🎚️ System Equalizer Integration
- Exposed `audioSessionId` directly from `CrossfadePlayerManager` to launch the device's native system `AudioEffect` control panel.

### 🖼️ Static PNG Cover Architecture (`PlaylistCoverManager`)
- Pre-renders 360×360 px 3×3 playlist grid collages into cached PNG files (`playlist_{id}_cover.png`).
- Guarantees 0ms cover load times and identical visual appearance across Jetpack Compose views and Android widgets.

---

## 🚀 Getting Started

### 1. Run the FastAPI Backend
Requires **Python 3.10+** and **ffmpeg**.

```bash
cd server
python3 -m venv venv
source venv/bin/activate
pip install -r requirements.txt
uvicorn main:app --host 0.0.0.0 --port 8000 --reload
```

### 2. Launch the Android App
1. Open the project folder in **Android Studio**.
2. Build and run the app on your Android device (`./gradlew assembleDebug`).
3. Open **Settings** in the app and input your server's IP address (e.g. `100.x.x.x` or `192.168.x.x`).
4. Head over to **Home** or **Search**, pick a track, and enjoy!
