# Walkthrough - Fixing Invalid URL Crash

I have fixed the crash that occurred when the application was initialized with an invalid or empty server IP address.

## Changes Made

### Data Layer

#### [ApiService.kt](file:///C:/Users/Ulrik/Documents/Projects/Mp3Player/app/src/main/java/com/mp3player/data/network/ApiService.kt)
- Updated `getInstance` to return `ApiService?` (nullable).
- Added basic URL validation: checks if the URL is blank or doesn't start with `http`.
- Added a `try-catch` block around `Retrofit.Builder().baseUrl()` to catch `IllegalArgumentException` from invalid hostnames.
- Improved the singleton pattern to track the `currentBaseUrl` and avoid unnecessary re-initialization if the URL hasn't changed.

### UI Layer

#### [MusicViewModel.kt](file:///C:/Users/Ulrik/Documents/Projects/Mp3Player/app/src/main/java/com/mp3player/ui/viewmodel/MusicViewModel.kt)
- Updated `apiService` to be nullable.
- Added a null check (`apiService ?: return@launch`) in all network-related functions (`searchYouTube`, `streamYouTubeTrack`, `downloadYouTubeTrack`) to prevent calls when the service isn't initialized.
- Added validation in `updateServerIp` to ignore blank IP inputs.
- Ensured that a new `apiService` is only assigned if initialization is successful.

## Verification Results

### Build Status
- [x] `gradle_build` (app:assembleDebug) passed successfully.

### Manual Verification Recommended
- Launch the app with an empty server IP in settings. It should no longer crash.
- Enter a valid IP and verify that Search and Playback functions (streaming/downloading) work as expected.
