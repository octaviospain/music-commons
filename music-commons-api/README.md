# Music Commons API

Core interfaces and abstractions for building music library applications.

## Overview

This module provides a reactive, repository-based API for managing music libraries, playlists, playback, and waveform visualization. It defines contracts without implementation details, allowing for flexible backend integrations.

## Packages

### `audio`
Core music library abstractions for managing audio items and their metadata.

- **`ReactiveAudioItem`** - Audio file representation with comprehensive metadata (artist, album, genre, etc.)
- **`AudioLibrary`** - Repository for CRUD operations on audio items with reactive event publishing
- **`Artist`**, **`Album`**, **`Label`**, **`Genre`** - Metadata domain models
- **`ArtistView`**, **`AlbumView`** - Aggregated views of artist catalogs and albums
- **`AudioFileType`** - Supported formats: MP3, M4A, WAV, FLAC

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
// Create audio library instance (implementation-specific)
val library: AudioLibrary<MyAudioItem> = ...

// Subscribe to library changes
library.subscribe(object : Flow.Subscriber<CrudEvent<Int, MyAudioItem>> {
    override fun onNext(event: CrudEvent<Int, MyAudioItem>) {
        // React to audio item changes
    }
})

// Load audio files asynchronously
val audioItems = library.createFromFileBatchAsync(paths).get()

// Query artist catalog
val artistView = library.getArtistCatalog(artist)

// Create and manage playlists
val hierarchy: PlaylistHierarchy<MyAudioItem, MyPlaylist> = ...
val playlist = hierarchy.createPlaylist("My Favorites", audioItems)
playlist.exportToM3uFile(outputPath)

// Play audio
val player: AudioItemPlayer = ...
player.play(audioItem)
player.volumeProperty.set(0.8)
```

## License

Copyright (C) 2025 Octavio Calleya Garcia

This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
