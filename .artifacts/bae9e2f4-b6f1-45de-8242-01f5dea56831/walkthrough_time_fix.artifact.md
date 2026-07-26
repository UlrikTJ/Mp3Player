# Walkthrough - Fixing Invalid Playback Time and Seek Issues

I have resolved the issue where the song's playback time would display nonsensical large numbers and prevent seeking.

## Changes Made

### 1. Robust Duration Handling
- **CrossfadePlayerManager.kt**: Updated `getDuration()` to explicitly check for `C.TIME_UNSET` (a very large negative constant used by Media3) and return `0L` if the duration is unknown.
- **Why?**: The media player returns a specific negative value when it hasn't finished loading the file's metadata. Previously, this value was being passed directly to the UI, leading to calculation overflows.

### 2. Sane Time Formatting
- **MainActivity.kt**: Updated the `formatTime` helper function to return "00:00" for any negative input.
- **Why?**: This provides a clean fallback UI while the player is transitioning between tracks, preventing the "insane numbers" from appearing.

### 3. Seek Stability
- **MainActivity.kt**: Added safety checks to the progress slider. The slider now only allows seeking once a valid, positive duration is known.
- **Why?**: Attempting to calculate a seek position based on an invalid duration would cause the player to jump to an unpredictable position or crash.

## Verification Results

### Build Success
The project builds successfully:
- `gradle_build`: **Succeeded**

### Manual Verification Recommended
1. **Play a new song**: Verify the timer starts at 00:00 and updates to the correct length once loaded.
2. **Seek mid-song**: Verify the progress slider works as expected and doesn't "jump" during transitions.
