# Music Commons Core

Core implementation module providing reactive audio library management, playlist hierarchies, and waveform generation.

## Overview

This module contains the concrete implementations of the interfaces defined in `music-commons-api`. It provides three main components that work together through reactive event subscriptions:

- **Audio Library** - Manages audio file metadata with artist catalog indexing
- **Playlist Hierarchy** - Organizes playlists with support for nested structures
- **Waveform Repository** - Generates and caches audio waveform visualizations

## Quick Start

### Creating an Audio Library

`CoreMusicLibrary.builder()` is the entry point; the `Default*` implementations are `internal` and
are not instantiated directly. With no repository arguments the library is fully in-memory.

```kotlin
// In-memory, or inject a JSON/SQL repository from music-commons-persistence
val library = CoreMusicLibrary.builder()
    .audioRepository(JsonFileRepository(file, AudioItemMapSerializer))
    .build()

// Create audio items from files
val audioItem = library.audioItemFromFile(audioFilePath)

// Query by artist and album
library.findAlbumAudioItems(artist, albumName)

// Batch creation with async support (default batch size: 500)
library.audioLibrary().createFromFileBatchAsync(filePaths).get()
```

### Managing Playlists

```kotlin
val library = CoreMusicLibrary.builder()
    .playlistRepository(JsonFileRepository(file, AudioPlaylistMapSerializer))
    .build()
val playlistHierarchy = library.playlistHierarchy()

// Create playlists and directories (createPlaylist takes audio-item IDs)
val playlist = playlistHierarchy.createPlaylist("My Playlist", audioItems.map { it.id })
val directory = playlistHierarchy.createPlaylistDirectory("Rock")

// Build hierarchies
playlistHierarchy.addPlaylistToDirectory(playlist, directory.name)
playlistHierarchy.movePlaylist(playlistName, destinationDirectoryName)

// Query playlists
playlistHierarchy.findByName("Rock")
```

### Generating Waveforms

```kotlin
val waveformRepository = library.waveformRepository()

// Generate waveform asynchronously
val waveform = waveformRepository
    .getOrCreateWaveformAsync(audioItem, width = 800, height = 300, dispatcher)
    .get()

// Extract amplitude data
val amplitudes = waveform.amplitudes(width, height)

// Create visualization image
waveform.createImage(outputFile, waveformColor, backgroundColor, width, height)
```

## Reactive Integration

The components automatically synchronize through event subscriptions:

```kotlin
// Subscribe repositories to audio library events
audioLibrary.subscribe(playlistHierarchy.audioItemEventSubscriber)
audioLibrary.subscribe(waveformRepository.audioItemEventSubscriber)

// Changes propagate automatically
audioLibrary.remove(audioItem)
// → Playlist removes the audio item
// → Waveform repository deletes the waveform
```

### Metadata Changes

Audio item modifications trigger reactive updates:

```kotlin
audioItem.title = "New Title"
// → Repository is updated
// → Persisted to storage
// → Artist catalog refreshes if artist/album changed
```

## Key Features

### Artist Catalog Indexing
The audio library maintains an internal catalog organizing tracks by artist and album, enabling efficient queries for artist discographies without manual indexing.

### Hierarchical Playlists
Playlists can contain both audio items and nested playlists, allowing arbitrary organizational structures. Parent-child relationships are tracked automatically.

### Memory Optimization
Artist and label instances use the flyweight pattern, ensuring only one instance exists per unique entity to minimize memory usage in large libraries.

### Persistence
All components support JSON serialization through kotlinx.serialization, with automatic persistence when backed by `JsonFileRepository`.

## Dependencies

- **JAudioTagger** - Audio file metadata reading/writing
- **lirp** - Reactive entity framework, repository pattern, and event-driven infrastructure

This module ships no JSON or SQL persistence code — persistence is supplied by the opt-in
`music-commons-persistence` module. Audio decoding and waveform PCM generation live in the
separate `music-commons-media` module (JavaSound SPI).

## Testing

See the test suite for complete usage examples:
- `MusicLibraryIntegrationTest` - Full integration scenarios
- `DefaultAudioLibraryTest` - Audio library operations
- `DefaultPlaylistHierarchyTest` - Playlist management
- `ScalableAudioWaveformTest` - Waveform generation

For comprehensive API documentation, refer to the parent project documentation.
