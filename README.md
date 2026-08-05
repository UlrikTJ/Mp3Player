# 🎵 Mp3Player

> A modern, high-fidelity Android Music Player built with **Jetpack Compose**, **ExoPlayer**, **Room**, and a custom **FastAPI** backend. 

Mp3Player is not just another music app. It features seamless 5-second crossfading playback, smart weighted shuffle statistics, wireless earbud/headset gestures, lock-screen controls, a 3-section queue manager, system equalizer integration, interactive widgets, and background audio downloading via YouTube Music.

---

## ✨ Key Highlights

- **Seamless Crossfading**: Dual-ExoPlayer engine seamlessly crossfades between ending and upcoming tracks (5s default).
- **Advanced 3-Section Queue**: A unified queue manager featuring *Now Playing*, *Added to Queue* (manual additions), and *Next from {Playlist}*. Drag-and-drop support across all sections.
- **Smart Weighted Shuffle**: Tracks you skip frequently are penalized. Tracks you manually skip to get a selection bonus. You can also fine-tune song likelihood multipliers manually!
- **Rich Interactive Widgets**: High-performance home screen widgets with zero-flicker progress updates, direct 1-tap play intents, 3x3 playlist collages, and upcoming queue previews.
- **Library Stats & Leaderboards**: Track your most played songs, skip rates, and "Keepers" across the global library or specific playlists.
- **Hardware Integration**: Full support for wireless earbud gestures, lock-screen controls, auto-pause on disconnect, and native System Equalizer integration.
- **Persistent Playback State**: Close the app and come back later. Your entire queue, current track, and playback position are fully restored exactly as you left them.
- **Automated CI/CD Releases**: Integrated GitHub Actions pipeline automatically builds debug/release APKs and attaches them to published GitHub Releases.

---

## 🏗️ Architecture Overview

```text
 ┌────────────────────────┐         Network         ┌────────────────────────┐
 │   Android Frontend     │ ──────────────────────> │    FastAPI Backend     │
 │ (Jetpack Compose, Room)│ <────────────────────── │ (yt-dlp, ffmpeg, tags) │
 └────────────────────────┘                         └────────────────────────┘
```

### 1. Android Client
- **Tech Stack**: 100% Kotlin, Jetpack Compose, Room DB, Coroutines & Flow.
- **Playback Engine**: Bound `AudioService` managing two `ExoPlayer` instances for gapless/crossfade playback.
- **State Management**: Reactive state management with `StateFlow` and `combine` operators.
- **Image Pipeline**: `PlaylistCoverManager` caches 3x3 grid collages into local PNGs for 0ms load times and 100% visual consistency across the app and system widgets.
- **Background Sync**: Asynchronous Room queries ensure widgets display the correct context even when the app is dead.
- **CI/CD Pipeline**: GitHub Actions (`.github/workflows/release.yml`) builds and publishes APKs automatically on GitHub releases.

### 2. Python Backend
- **Tech Stack**: Python 3.10+, FastAPI, `yt-dlp`, `ffmpeg`, `mutagen`.
- **Functionality**: 
  - Resolves direct audio stream URLs for instant preview playback.
  - Downloads and extracts high-quality `.mp3` audio.
  - Scrapes high-res album art and tags files with ID3 metadata automatically.

---

## 📱 Detailed Features

### 🎧 Playback & Hardware Controls
- **Earbud / Headset Gestures**: Single-tap to play/pause, double-tap to skip tracks via OS `MediaSessionCompat.Callback`.
- **Auto-Pause on Disconnect**: Automatically pauses playback when Bluetooth or wired headphones are unplugged (`AUDIO_BECOMING_NOISY`).
- **Lock-Screen Transport**: Broadcasts `PlaybackStateCompat` and active media notifications to the system lock screen.
- **System Equalizer**: Launch the device's native `AudioEffect` control panel directly from the app.

### 📋 Queue Management
- **Sectioned Overview**: Clearly see what's playing now, what you manually queued, and what's coming up next from your playlist.
- **Sequential Mid-Playlist Start**: Tapping song #89 builds the queue sequentially from 89 through the end, looping back to the beginning.
- **Full Queue Persistence**: SharedPreferences-backed `PlaybackStateManager` saves your exact queue state and position on exit.

### 🎨 Modern UI & Widgets
- **Home Screen (`HomeScreen`)**: Features a greeting banner, a hero quick-resume card, quick action chips (Shuffle All, Stats, Queue), and horizontal scrolling rows for recent/my playlists.
- **Interactive Widgets**: 
  - **1-Tap Idle Play**: Shows a 3x3 cover collage when idle; tap Play to instantly start your top playlist.
  - **Queue Previews**: Displays thumbnail slots for upcoming tracks with direct play intents.
  - **Zero-Flicker Updates**: Employs `partiallyUpdateAppWidget` for 500ms progress ticks without re-rendering artwork.
  - **Unified Collage Engine**: The app and widgets share the exact same `PlaylistCoverManager` logic to guarantee visual sync.

---

## 📦 Automated Releases & CI/CD

This repository includes a pre-configured **GitHub Actions** CI/CD pipeline (`.github/workflows/release.yml`). 

Whenever you publish a release on GitHub:
1. The workflow triggers automatically on Ubuntu runners with JDK 17.
2. It builds both Debug and Release APK variants (`./gradlew assembleDebug assembleRelease`).
3. The resulting `.apk` files are automatically uploaded and attached directly to the GitHub Release assets.

---

## 🚀 Getting Started

### 1. Run the FastAPI Backend
*Requirements: Python 3.10+ and ffmpeg.*

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

---

*Built with ❤️ for true music lovers.*
