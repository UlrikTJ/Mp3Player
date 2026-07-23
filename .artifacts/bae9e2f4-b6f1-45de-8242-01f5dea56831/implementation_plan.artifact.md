# Fix Reorder Stability and Drag Issues

The drag-and-drop reordering functionality is currently unstable, with drags cancelling prematurely and items resetting to their original positions after release.

## User Review Required

> [!IMPORTANT]
> - I will be refactoring the database update logic to be atomic (using transactions). This will prevent the "jumping" behavior on release.
> - I will stabilize the drag detection by using unique song IDs instead of volatile list indices as gesture keys.
> - I will improve the auto-scroll behavior during drag to be more responsive.

## Proposed Changes

### Data Layer

#### [MODIFY] [MusicDao.kt](file:///C:/Users/Ulrik/Documents/Projects/Mp3Player/app/src/main/java/com/mp3player/data/dao/MusicDao.kt)
- Add `insertPlaylistSongCrossRefs` to support bulk updates.
- Add a `@Transaction` method to update all positions in a playlist safely.

### UI & ViewModel Layer

#### [MODIFY] [MusicViewModel.kt](file:///C:/Users/Ulrik/Documents/Projects/Mp3Player/app/src/main/java/com/mp3player/ui/viewmodel/MusicViewModel.kt)
- Update `reorderSongInPlaylist` to use the new atomic database update.
- Ensure `moveQueueItem` updates the `StateFlow` only once per operation.

#### [MODIFY] [MainActivity.kt](file:///C:/Users/Ulrik/Documents/Projects/Mp3Player/app/src/main/java/com/mp3player/MainActivity.kt)
- **PlaylistDetailDialog**:
    - Change `pointerInput(index)` to `pointerInput(song.id)` for gesture stability.
    - Ensure `onDragCancel` also triggers the persistence logic.
    - Adjust `currentTargetIndex` logic to be more robust.
- **QueueDialog**:
    - Change `pointerInput(index)` to a more stable identifier.
    - Improve the floating overlay and list item animations.

## Verification Plan

### Manual Verification
1. **Drag Test**: Open a playlist, enter reorder mode, and drag a song from the top to the bottom. Verify the drag does not cancel.
2. **Release Test**: Release the song at the new position. Verify it stays there and doesn't jump back.
3. **Queue Test**: Perform the same reorder in the Play Queue and verify stability.
4. **Auto-scroll Test**: Drag an item to the edge of the list and verify it scrolls smoothly.
