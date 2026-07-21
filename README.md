# Mp3Player Application

Android Music Player built with Jetpack Compose, which lets you search and download tracks locally using a custom FastAPI backend, while also featuring a crossfading media player, custom playlist management, search suggestions, and a smart, statistics-driven customizable weighted shuffle engine.

---

## Architecture Overview

```
 ┌────────────────────────┐         Network         ┌────────────────────────┐
 │   Android Frontend     │ ──────────────────────> │    FastAPI Backend     │
 │ (Jetpack Compose, Room)│ <────────────────────── │ (yt-dlp, ffmpeg, tags) │
 └────────────────────────┘                         └────────────────────────┘
```

1. **Android Client (Jetpack Compose)**:
   - Employs a single-activity layout with Room Database storing local tracks, playlists, playback stats, and skip events.
   - Leverages a bound media playback service controlling two `ExoPlayer` instances to provide seamless 5-second crossfades.
   - Directly streams resolved audio links for search result previews, caching them locally in-memory to prevent repeat scrapes.
   - Implements drag-to-reorder, inline playlist renaming, song weight adjustments, and queue additions.
   - Includes music-targeted autocomplete suggestions matching search input.

2. **Python Backend (FastAPI)**:
   - Queries YouTube Music search queries using `yt-dlp`.
   - Resolves direct streaming audio URLs dynamically for instant client preview playback.
   - Downloads, extracts audio tracks to `.mp3`, scrapes high-resolution cover artwork, writes tags (`title`, `artist`, `album`, `APIC` cover) using `mutagen`, and sends the tagged file to the client.

---

## Features

### 🎧 Seamless Playback
- **Preview Streams**: Search result previews play direct stream URLs instantly without eating up local device storage.
- **Preplay Caching**: Cache-resolves YouTube stream links for the top search result in advance, ensuring instant play on click.
- **5s Crossfading**: Fades out the ending track while fading in the next track over a 5-second interval.

### 🔀 Smart Weighted Shuffle
Unlike plain random shuffles, this player calculates track weight probability dynamically based on user skips and what the user skips to:
- **Skip Penalty**: High-frequency skips reduce the song's select probability that you can customize.
- **"Skipped to" Log**: Logs skips occurring sequentially. If a track is skipped immediately in favor of another song, the other song receives a selection bonus.
- **Weights Adjuster**: Manually fine-tune song likelihood multipliers (`0.1x` to `5.0x` in a slider or custom input).
- **Statistics Reset**: Clear play logs and reset all track probabilities back to default (`1.0x`) with a single click.

### 📂 Custom Playlist Management
- **Full-Screen Spotify View**: High-fidelity detail views showing custom banner colors, track counts, and play controls.
- **Interactive Drag-Reorder**: Reorder playlist tracks inline dynamically by clicking and dragging the drag handle icon.
- **Three-Dot Song Actions**: Dropdown menu on each song card supporting:
  - Add to Queue
  - Change Weight (via popup slider)
  - Remove from Playlist
- **Playlist Renaming**: Rename custom playlists inline without breaking database relations.

---

## Getting Started

### 1. Run the FastAPI Backend
To host the downloader backend, you need `Python 3.10+` and `ffmpeg` installed on the host system (e.g., Ubuntu Server).

1. Navigate to the `server` directory:
   ```bash
   cd server
   ```
2. Create and activate a virtual environment:
   ```bash
   python3 -m venv venv
   source venv/bin/activate
   ```
3. Install dependencies:
   ```bash
   pip install -r requirements.txt
   ```
4. Start the server using Uvicorn:
   ```bash
   uvicorn main:app --host 0.0.0.0 --port 8000 --reload
   ```

### 2. Launch the Android App
1. Open the project folder in Android Studio.
2. Build and run the app on your Android device.
3. Connect your device to your Tailscale network or local network.
4. Go to **Settings** in the app and input your server's IP address (e.g. `100.x.x.x`).
5. Head over to the **Search** screen, type a query, and start playing!
