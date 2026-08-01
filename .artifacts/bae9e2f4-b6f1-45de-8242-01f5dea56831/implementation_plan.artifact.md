# Fix Reorder Stability and "Jump Back" Issues

I have identified that the drag-and-drop instability is caused by `LazyColumn` disposing of off-screen items (which kills the gesture) and the UI resetting its local state before the database confirms the new order.

## User Review Required

> [!NOTE]
> - I will be adding "optimistic updates" to the ViewModel. This means the UI will update instantly and *stay* updated while the database work happens in the background.
> - I will increase the "Off-screen Buffer" for your lists so that dragging a song far down won't cause it to be deleted/cancelled by the system.

## Proposed Changes

### UI & ViewModel Layer

#### [MODIFY] [MusicViewModel.kt](file:///C:/Users/Ulrik/Documents/Projects/Mp3Player/app/src/main/java/com/mp3player/ui/viewmodel/MusicViewModel.kt)
- Add `updatePlaylistSongsLocally(songs: List<SongEntity>)` to immediately override the StateFlow.
- Update `reorderSongInPlaylist` to use this local update first.
- Add similar logic for the `Play Queue`.

#### [MODIFY] [MainActivity.kt](file:///C:/Users/Ulrik/Documents/Projects/Mp3Player/app/src/main/java/com/mp3player/MainActivity.kt)
- Add `beyondBoundsItemCount = 10` to `LazyColumn` in both `PlaylistDetailView` and `QueueDialog`. This keeps dragged items "alive" even when they scroll far off-screen.
- Refactor `remember(songs)` to be more robust against accidental resets during transitions.
- Ensure `onDragEnd` and `onDragCancel` handle the final hand-off to the ViewModel correctly.

## Verification Plan

### Manual Verification
1. **Long Drag Test**: Open a long playlist. Drag the top song all the way to the bottom until it scrolls significantly. Verify the drag does not stop.
2. **Release Stability Test**: Release the song. Verify it stays at the new position immediately and never "jumps" back to the top.
3. **Queue Test**: Perform the same test in the Play Queue dialog.
