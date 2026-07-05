# Music Commons API

Core interfaces and abstractions for building music library applications.

## Overview

This module provides a reactive, repository-based API for managing music libraries, playlists, playback, and waveform visualization. It defines contracts without implementation details, allowing for flexible backend integrations.

## Packages

### `audio`
Core music library abstractions for managing audio items and their metadata.

- **`MusicLibrary`** - Unified facade contract implemented by both the headless and JavaFX libraries, enabling library-agnostic consumers
- **`ReactiveAudioItem`** - Audio file representation with comprehensive metadata (artist, album, genre, etc.)
- **`ReactiveAudioLibrary`** - Repository for CRUD operations on audio items with reactive event publishing
- **`Artist`**, **`AlbumDetails`**, **`Label`**, **`Genre`** - Metadata domain models
- **`ReactiveArtistCatalog`**, **`ReactiveAlbum`**, **`ReactiveGenreIndex`** - Live per-artist, per-album, and per-genre projections of the library
- **`AudioFileType`** - Supported formats: MP3, M4A (AAC and ALAC), WAV, FLAC, and OGG (Vorbis and Opus)

### `player`
Audio playback control interfaces.

- **`AudioItemPlayer`** - Playback controls (play, pause, resume, stop) with status monitoring
- **`AudioItemPlayerEvent`** - Events emitted during playback

### `playlist`
Hierarchical playlist management.

- **`AudioPlaylist`** - Playlist interface with M3U export support
- **`ReactiveAudioPlaylist`** - Mutable playlists with add/remove operations
- **`PlaylistHierarchy`** - Repository for managing nested playlist structures

### `waveform`
Audio waveform visualization.

- **`AudioWaveform`** - Generate amplitude data and visual waveform images
- **`AudioWaveformRepository`** - Async waveform retrieval and creation

## Key Features

- **Reactive**: All entities implement `ReactiveEntity` for property change observation
- **Event-Driven**: Repositories publish CRUD events via `Flow.Publisher`
- **Async-First**: Batch operations and waveform processing use coroutines and `CompletableFuture`
- **Type-Safe**: Generics ensure compile-time safety across audio items and playlists
- **Export**: Built-in M3U playlist export with directory structure preservation

## Usage Pattern

```kotlin
// Obtain a library through a concrete facade (CoreMusicLibrary / FXMusicLibrary)
val library: ReactiveAudioLibrary<MyAudioItem, *, *, *> = ...

// Subscribe to library changes
library.subscribe(object : Flow.Subscriber<CrudEvent<Int, MyAudioItem>> {
    override fun onNext(event: CrudEvent<Int, MyAudioItem>) {
        // React to audio item changes
    }
})

// Load audio files asynchronously (default batch size: 500)
val audioItems = library.createFromFileBatchAsync(paths).get()

// Query the artist catalog projection
val artistCatalog = library.getArtistCatalog(artist)

// Create and manage playlists (createPlaylist takes audio-item IDs)
val hierarchy: ReactivePlaylistHierarchy<MyAudioItem, MyPlaylist> = ...
val playlist = hierarchy.createPlaylist("My Favorites", audioItems.map { it.id })
playlist.exportToM3uFile(outputPath)

// Play audio
val player: AudioItemPlayer = ...
player.play(audioItem)
player.setVolume(0.8)
```

## License

Copyright (C) 2025 Octavio Calleya Garcia

This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
