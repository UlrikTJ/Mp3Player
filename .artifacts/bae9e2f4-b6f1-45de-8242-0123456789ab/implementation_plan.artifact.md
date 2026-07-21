# Fix Crash on Stream Playback and Improve Search UI

The application crashes when attempting to log playback statistics for temporary YouTube streams (which have an ID of -1), triggering a database foreign key constraint violation. Additionally, the Search screen UI doesn't reflect the playback state for these streams.

## User Review Required

> [!IMPORTANT]
> - Playback statistics (skips, plays, keepers) will **not** be recorded for temporary YouTube streams. Statistics are only tracked for songs saved in your local library.
> - The Search screen "Stream Preview" button will now toggle between Play and Pause icons for the currently playing track.

## Proposed Changes

### Playback Layer

#### [MODIFY] [PlaybackStatsTracker.kt](file:///C:/Users/Ulrik/Documents/Projects/Mp3Player/app/src/main/java/com/mp3player/playback/PlaybackStatsTracker.kt)
- Add a check in `onTrackEnded` to skip database logging if the song ID is less than or equal to 0.

### UI & ViewModel Layer

#### [MODIFY] [MusicViewModel.kt](file:///C:/Users/Ulrik/Documents/Projects/Mp3Player/app/src/main/java/com/mp3player/ui/viewmodel/MusicViewModel.kt)
- Update `streamYouTubeTrack` to toggle play/pause if the requested track is already the current stream.

#### [MODIFY] [MainActivity.kt](file:///C:/Users/Ulrik/Documents/Projects/Mp3Player/app/src/main/java/com/mp3player/MainActivity.kt)
- Update `SearchScreen` to show the `Pause` icon for the "Stream Preview" button if that track is currently playing.

## Verification Plan

### Manual Verification
- Deploy the app.
- Search for a song.
- Click "Stream Preview": Verify it plays and the icon changes to Pause.
- Click "Stream Preview" again: Verify it pauses and does **not** crash.
- Verify that statistics for library songs are still recorded correctly.
