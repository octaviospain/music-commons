[![Ask DeepWiki](https://deepwiki.com/badge.svg)](https://deepwiki.com/octaviospain/music-commons)
![Maven Central Version](https://img.shields.io/maven-central/v/net.transgressoft/music-commons-core)
![GitHub Actions Workflow Status](https://img.shields.io/github/actions/workflow/status/octaviospain/music-commons/.github%2Fworkflows%2Fmaster.yml?logo=github)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=octaviospain_music-commons&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=octaviospain_music-commons)
[![Bugs](https://sonarcloud.io/api/project_badges/measure?project=octaviospain_music-commons&metric=bugs)](https://sonarcloud.io/summary/new_code?id=octaviospain_music-commons)
[![Code Smells](https://sonarcloud.io/api/project_badges/measure?project=octaviospain_music-commons&metric=code_smells)](https://sonarcloud.io/summary/new_code?id=octaviospain_music-commons)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=octaviospain_music-commons&metric=coverage)](https://sonarcloud.io/summary/new_code?id=octaviospain_music-commons)
[![Duplicated Lines (%)](https://sonarcloud.io/api/project_badges/measure?project=octaviospain_music-commons&metric=duplicated_lines_density)](https://sonarcloud.io/summary/new_code?id=octaviospain_music-commons)
# Music Commons

A modular, reactive Kotlin library for managing audio libraries, playlists, and waveform visualizations. Originally extracted from the [Musicott](https://github.com/octaviospain/Musicott) desktop application. Published to Maven Central under `net.transgressoft`.

See the [Wiki](https://github.com/octaviospain/music-commons/wiki) for detailed guides and architecture documentation.

## Overview

music-commons was born from the need to separate the core audio management logic from the Musicott desktop application into a standalone, reusable library. The goal was to create a foundation that could power not just Musicott, but any project that needs to manage audio files and related operations—whether that's handling playlists, generating waveforms, managing metadata, or implementing import/export functionality. It provides reusable core components for audio file management: metadata handling, playlist organization, waveform generation, and playback control. The library offers a clean, layered architecture with reactive event-driven updates and optional JavaFX integration, enabling both headless services and desktop applications.### Built on lirp

Music Commons leverages [lirp](https://github.com/octaviospain/lirp), which provides the foundational reactive entity framework, repository pattern, automatic JSON serialization, and event-driven architecture that Music Commons builds upon.

### Architecture

The library is organized into three main modules:

- **`music-commons-api`**: Interfaces and contracts defining the audio management domain model
- **`music-commons-core`**: Implementations with JSON persistence and reactive event subscriptions
- **`music-commons-fx`**: JavaFX integration layer with observable properties and UI components

## Requirements

- **Java**: 21 or higher
- **Kotlin**: 2.3.0
- **JavaFX**: 21.0.7 or higher (only required for `music-commons-fx` module)

## Installation

```kotlin
// Gradle (Kotlin DSL)
dependencies {
    implementation("net.transgressoft:music-commons-api:$version")
    implementation("net.transgressoft:music-commons-core:$version")
    implementation("net.transgressoft:music-commons-fx:$version") // Optional, for JavaFX apps
}
```

The core module depends on [lirp](https://github.com/octaviospain/lirp):

```kotlin
implementation("net.transgressoft:lirp-api:2.3.0")
implementation("net.transgressoft:lirp-core:2.3.0")
```

## Key Features

### Audio Library Management

- **Multi-format support**: MP3, M4A, WAV, and FLAC with automatic metadata extraction
- **Artist catalog indexing**: Automatic organization by artist and album with aggregated views
- **Batch operations**: Asynchronous batch creation via `CompletableFuture` API
- **Reactive updates**: CRUD operations publish events through Java Flow API

### Playlist Management

- **Hierarchical organization**: Nested playlist structures with directories
- **M3U export**: Export playlists preserving directory structure
- **Automatic synchronization**: Playlists update when audio items are modified or removed

### Waveform Visualization

- **Asynchronous generation**: Non-blocking waveform creation with configurable resolution
- **Repository caching**: Waveforms are cached and reused across requests
- **JavaFX component**: Custom `WaveformPane` canvas with automatic redraw on resize

### Audio Playback

- **Player abstraction**: `AudioItemPlayer` interface for custom playback engines
- **JavaFX implementation**: `JavaFxPlayer` wrapping native MediaPlayer with status monitoring
- **Play count tracking**: Automatic increment at 60% playback threshold

### Persistence

- **JSON file storage**: Powered by [lirp](https://github.com/octaviospain/lirp)'s `JsonFileRepository` with debounced file I/O
- **SQL storage**: Powered by [lirp-sql](https://github.com/octaviospain/lirp)'s `SqlRepository` with HikariCP connection pooling and JetBrains Exposed
- **Automatic serialization**: Built-in kotlinx-serialization serializers for all entities
- **Transparent persistence**: Entity changes are persisted without manual save operations

### JavaFX Integration

- **Observable properties**: Direct binding to JavaFX TableView, ListView, and other controls
- **Thread safety**: Audio item collections use `FxAggregateList` delegates that auto-dispatch listener notifications to the JavaFX Application Thread; artist catalog and album properties are updated via `Platform.runLater` from CRUD subscriptions
- **Custom controls**: `WaveformPane` component for waveform visualization

## Module Details

### music-commons-api

Defines contracts and interfaces for the audio management domain.

**Key Interfaces:**
- `ReactiveAudioItem<I>` -- Audio file representation with metadata
- `ReactiveAudioLibrary<I, AC>` -- Generic CRUD repository with reactive event publishing
- `ReactiveAudioPlaylist<I, P>` / `ReactivePlaylistHierarchy<I, P>` -- Generic playlist management with M3U export
- `AudioWaveform` / `AudioWaveformRepository` -- Waveform data and generation
- `AudioItemPlayer` -- Playback controls with status monitoring

### music-commons-core

Concrete implementations with JSON file persistence via [lirp](https://github.com/octaviospain/lirp) and reactive event subscriptions.

- `MusicLibrary` -- Unified facade for headless audio management (builder-based entry point)
- `AudioLibrary` -- Narrowed `ReactiveAudioLibrary` for `AudioItem` and `ArtistCatalog` types
- `PlaylistHierarchy` -- Narrowed `ReactivePlaylistHierarchy` for `AudioItem` and `MutableAudioPlaylist` types
- Internal implementations: `DefaultAudioLibrary`, `DefaultPlaylistHierarchy`, `DefaultAudioWaveformRepository`
- Event subscribers for reactive synchronization between components

### music-commons-fx

Bridges core module with JavaFX's property binding system.

- `FXMusicLibrary` -- Unified facade for JavaFX audio management with observable properties (builder-based entry point)
- `ObservableAudioLibrary` -- Narrowed `ReactiveAudioLibrary` with JavaFX observable properties for UI binding
- `ObservablePlaylistHierarchy` -- Narrowed `ReactivePlaylistHierarchy` with a JavaFX observable playlists collection
- `JavaFxPlayer` -- Native JavaFX MediaPlayer wrapper with reactive events
- `WaveformPane` -- Custom Canvas component for waveform visualization

## Usage Examples

### Core module (headless)

Use `MusicLibrary.builder()` as the single entry point for headless audio management:

```kotlin
import net.transgressoft.commons.music.MusicLibrary

// In-memory (volatile) storage -- no files needed
val library = MusicLibrary.builder().build()

// JSON file persistence
val library = MusicLibrary.builder()
    .audioLibraryJsonFile(File("audio-library.json"))
    .playlistHierarchyJsonFile(File("playlists.json"))
    .waveformRepositoryJsonFile(File("waveforms.json"))
    .build()

// SQL persistence (requires KSP-generated SqlTableDef for each entity)
val library = MusicLibrary.builder()
    .audioLibrarySql(dataSource, audioItemTableDef)
    .playlistHierarchySql(dataSource, playlistTableDef)
    .waveformRepositorySql(dataSource, waveformTableDef)
    .build()

// Add audio files
val audioItem = library.audioItemFromFile(Path.of("/path/to/song.mp3"))

// Batch import (returns CompletableFuture, default batch size: 500)
val audioItems = library.audioLibrary().createFromFileBatchAsync(listOfPaths).get()

// Create and manage playlists
val playlist = library.createPlaylist("Favorites")
library.playlistHierarchy().addAudioItemsToPlaylist(listOf(audioItem), "Favorites")

// Generate waveforms
val waveform = library
    .getOrCreateWaveformAsync(audioItem, 800.toShort(), 300.toShort())
    .get()

// Export playlist to M3U
playlist.exportToM3uFile(Path.of("favorites.m3u"))

// Lifecycle
library.close()
```

### JavaFX Integration

Use `FXMusicLibrary.builder()` for JavaFX applications with observable property bindings:

```kotlin
import net.transgressoft.commons.fx.music.FXMusicLibrary

val fxLibrary = FXMusicLibrary.builder()
    .audioLibraryJsonFile(File("audio-library.json"))
    .playlistHierarchyJsonFile(File("playlists.json"))
    .waveformRepositoryJsonFile(File("waveforms.json"))
    .build()

// SQL persistence
val fxLibrary = FXMusicLibrary.builder()
    .audioLibrarySql(dataSource, observableAudioItemTableDef)
    .playlistHierarchySql(dataSource, observablePlaylistTableDef)
    .waveformRepositorySql(dataSource, waveformTableDef)
    .build()

// Bind directly to JavaFX UI components
tableView.itemsProperty().bind(fxLibrary.audioItemsProperty)
label.textProperty().bind(fxLibrary.albumCountProperty.asString())

// Reactive playlist updates
fxLibrary.playlistsProperty.addListener { _, _, _ -> /* refresh UI */ }

// Create playlists
val playlist = fxLibrary.createPlaylist("Favorites")

// Add audio items
fxLibrary.audioItemFromFile(Path.of("/path/to/song.mp3"))

// Lifecycle
fxLibrary.close()
```

### Audio Playback

```kotlin
import net.transgressoft.commons.fx.music.player.JavaFxPlayer

val player = JavaFxPlayer()

// Play, pause, resume, stop
player.play(audioItem)
player.pause()
player.resume()
player.stop()

// Monitor status and position
player.statusProperty.addListener { _, _, newStatus -> println("Status: $newStatus") }
player.currentTimeProperty.addListener { _, _, time -> println("Time: $time") }

// Control volume and seek
player.setVolume(0.8)
player.seek(30000.0) // seek to 30 seconds

player.dispose()
```

### Waveform Visualization (JavaFX)

```kotlin
import net.transgressoft.commons.fx.music.waveform.WaveformPane
import javafx.scene.paint.Color

val waveformPane = WaveformPane()
waveformPane.drawWaveformAsync(waveform, Color.CYAN, Color.BLACK)

// Add to scene -- auto-redraws on resize
stackPane.children.add(waveformPane)
```

## Building the Project

```bash
# Build all modules
gradle build

# Run tests
gradle test

# Generate aggregated coverage report
gradle testCodeCoverageReport

# Check code formatting
gradle ktlintCheck

# Generate documentation
gradle dokkaHtml
```

> **Note:** Waveform transcoding requires FFmpeg native binaries that are only bundled for Linux 64-bit (`jave-nativebin-linux64`). Waveform-related tests will fail on macOS and Windows. Non-Linux developers can safely skip these tests locally and rely on CI for full test coverage.

## Contributing

Contributions are welcome! Please see [CONTRIBUTING.md](CONTRIBUTING.md) for details.

## License and Attributions

Copyright (c) 2025-2026 Octavio Calleya Garcia.

Music Commons is free software under GNU GPL version 3 license, available [here](https://www.gnu.org/licenses/gpl-3.0.en.html#license-text).

This project builds upon several excellent open-source libraries:

- **[JAudioTagger](https://github.com/ericfarng/jaudiotagger)**: Audio metadata reading and writing library
- **[Jave](https://github.com/a-schild/jave2)**: Java Audio Video Encoder for audio transcoding
- **[Kotlin Coroutines](https://github.com/Kotlin/kotlinx.coroutines)**: Library support for Kotlin coroutines
- **[kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization)**: Kotlin multiplatform serialization
- **[Guava](https://github.com/google/guava)**: Google Core Libraries for Java
- **[lirp](https://github.com/octaviospain/lirp)**: Reactive entity framework and persistence infrastructure
- **[Kotest](https://kotest.io/)**: Kotlin testing framework
- **[MockK](https://mockk.io/)**: Mocking library for Kotlin
- **[TestFX](https://github.com/TestFX/TestFX)**: JavaFX testing framework
