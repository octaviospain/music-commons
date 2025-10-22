[![Ask DeepWiki](https://deepwiki.com/badge.svg)](https://deepwiki.com/octaviospain/music-commons)
![Maven Central Version](https://img.shields.io/maven-central/v/net.transgressoft/music-commons-core)
![GitHub Actions Workflow Status](https://img.shields.io/github/actions/workflow/status/octaviospain/music-commons/.github%2Fworkflows%2Fmaster.yml?logo=github)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=octaviospain_music-commons&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=octaviospain_music-commons)
[![Bugs](https://sonarcloud.io/api/project_badges/measure?project=octaviospain_music-commons&metric=bugs)](https://sonarcloud.io/summary/new_code?id=octaviospain_music-commons)
[![Code Smells](https://sonarcloud.io/api/project_badges/measure?project=octaviospain_music-commons&metric=code_smells)](https://sonarcloud.io/summary/new_code?id=octaviospain_music-commons)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=octaviospain_music-commons&metric=coverage)](https://sonarcloud.io/summary/new_code?id=octaviospain_music-commons)
[![Duplicated Lines (%)](https://sonarcloud.io/api/project_badges/measure?project=octaviospain_music-commons&metric=duplicated_lines_density)](https://sonarcloud.io/summary/new_code?id=octaviospain_music-commons)
# Music Commons

A modular, reactive Kotlin library for managing audio libraries, playlists, and waveform visualizations. Originally extracted from the [Musicott](https://github.com/octaviospain/Musicott) desktop application to provide reusable core components for audio file management across multiple projects.

## Overview

Music Commons was born from the need to separate the core audio management logic from the Musicott desktop application into a standalone, reusable library. The goal was to create a foundation that could power not just Musicott, but any project that needs to manage audio files and related operations—whether that's handling playlists, generating waveforms, managing metadata, or implementing import/export functionality.

By extracting these components into a dedicated library, Music Commons enables developers to build various types of audio management applications without being tied to a specific UI framework or implementation. Whether you're creating a desktop music player, a headless audio processing service, a DJ application, or any other audio-centric tool, Music Commons provides the building blocks you need.

The library provides a clean, layered architecture with comprehensive metadata handling, reactive event-driven updates, and optional JavaFX integration. It is designed with flexibility in mind, allowing developers to use only the components they need while maintaining consistent behavior across different UI frameworks or headless environments.

### Built on Transgressoft Commons

Music Commons leverages [Transgressoft Commons](https://github.com/octaviospain/transgressoft-commons), another library extracted from the Musicott refactoring effort, which provides the foundational reactive entity framework and persistence infrastructure. Transgressoft Commons handles the repository pattern, automatic JSON serialization, and event-driven architecture that Music Commons builds upon. This separation allows Music Commons to focus on audio-specific functionality while delegating core reactive and persistence concerns to a proven, reusable foundation.

### Architecture

The library is organized into three main modules:

- **`music-commons-api`**: Core interfaces and contracts defining the audio management domain model
- **`music-commons-core`**: Concrete implementations with JSON persistence and reactive event subscriptions
- **`music-commons-fx`**: JavaFX integration layer with observable properties and UI components

This modular design enables you to:
- Build headless audio management services using only the core module
- Create desktop applications with full JavaFX integration
- Implement custom backends by providing your own implementations of the API interfaces

## Requirements

- **Java**: 17 or higher
- **Kotlin**: 2.1.10 (or compatible version)
- **JavaFX**: 22.0.1 or higher (only required for `music-commons-fx` module)

## Key Features

### Audio Library Management

- **Comprehensive Metadata Support**: Track title, artist, album, genre, label, year, BPM, play count, ratings, and custom tags
- **Multi-Format Support**: MP3, M4A, WAV, and FLAC file types with automatic metadata extraction
- **Reactive Updates**: All entities implement reactive property change notifications via `ReactiveEntity`
- **Event-Driven Architecture**: CRUD operations publish events through Java Flow API for automatic synchronization
- **Artist Catalog Indexing**: Automatic organization of audio items by artist and album with aggregated views
- **Batch Operations**: Asynchronous batch creation and modification of audio items with coroutine dispatchers
- **Type-Safe Generics**: Compile-time safety through parameterized types for audio item implementations

### Playlist Management

- **Hierarchical Organization**: Support for nested playlist structures with arbitrary depth
- **M3U Export**: Export playlists to M3U format with directory structure preservation
- **Reactive Synchronization**: Automatic updates when audio items are modified or removed from the library
- **Observable Collections**: JavaFX integration provides observable playlist sets for UI binding
- **Folder-Like Structure**: Organize playlists in a tree hierarchy similar to filesystem directories

### Waveform Visualization

- **Asynchronous Generation**: Non-blocking waveform creation with `CompletableFuture` API
- **Scalable Processing**: Generate waveforms at any resolution with configurable width and height
- **Image Export**: Create visual representations as `BufferedImage` for rendering or export
- **Repository Pattern**: Centralized waveform creation and caching through `AudioWaveformRepository`
- **JavaFX Integration**: Custom `WaveformPane` component with automatic canvas redraw on resize

### Audio Playback

- **Player Abstraction**: `AudioItemPlayer` interface for implementing custom playback engines
- **Status Monitoring**: Track playback state transitions (READY, PLAYING, PAUSED, STOPPED, etc.)
- **Event Notifications**: Receive player events for synchronizing UI or triggering actions
- **JavaFX Player Implementation**: Ready-to-use `JavaFxPlayer` wrapping native JavaFX MediaPlayer
- **Play Count Tracking**: Automatic play count increment at 60% playback threshold (JavaFX implementation)

### Persistence & Serialization

- **JSON File Storage**: Powered by [Transgressoft Commons](https://github.com/octaviospain/transgressoft-commons)'s `JsonFileRepository`, providing automatic, thread-safe JSON persistence with debounced file I/O
- **Automatic Serialization**: Built-in serializers for audio items, playlists, and waveforms using kotlinx-serialization
- **Transparent Persistence**: Changes to entities are automatically persisted without manual save operations

### JavaFX Integration

- **Observable Properties**: Bidirectional binding with JavaFX property system for automatic UI updates
- **Thread Safety**: All JavaFX property modifications executed on the Application Thread
- **TableView/ListView Support**: Observable collections integrate seamlessly with standard JavaFX controls
- **Custom Controls**: `WaveformPane` component for embedding waveform visualizations
- **Property Binding**: Changes in core data structures automatically propagate to UI components

## Module Details

### music-commons-api

Defines the contracts and interfaces for the audio management domain without any implementation details. This module is ideal for:
- Defining your own implementations with custom backends
- Ensuring loose coupling between layers
- Building abstractions that work with any implementation

**Key Interfaces:**
- `ReactiveAudioItem<I>`: Audio file representation with comprehensive metadata
- `AudioLibrary<I>`: Repository for CRUD operations with reactive event publishing
- `AudioItemPlayer`: Playback controls with status monitoring
- `AudioPlaylist<I>` / `ReactiveAudioPlaylist<I>`: Playlist management with M3U export
- `PlaylistHierarchy<I, P>`: Nested playlist structure repository
- `AudioWaveform`: Waveform amplitude data and image generation
- `AudioWaveformRepository`: Asynchronous waveform retrieval and creation

### music-commons-core

Provides concrete implementations of all API interfaces with JSON file persistence (via [Transgressoft Commons](https://github.com/octaviospain/transgressoft-commons)) and reactive event subscriptions. This module includes:
- `DefaultAudioLibrary`: Full-featured audio library with artist catalog indexing and automatic JSON persistence
- `DefaultPlaylistHierarchy`: Hierarchical playlist management with automatic synchronization
- `DefaultAudioWaveformRepository`: Waveform generation and caching with dispatcher support
- JSON serializers for all entities using kotlinx-serialization
- Event subscribers for automatic reactive synchronization between components
- Integration with Transgressoft Commons repository infrastructure for transparent persistence

**Use this module for:**
- Building headless audio management services
- Command-line tools for audio metadata manipulation
- Backend services that don't require UI integration

### music-commons-fx

Bridges the reactive core module with JavaFX's property binding system, providing:
- `ObservableAudioLibrary`: JavaFX observable collections for TableView/ListView binding
- `ObservablePlaylist`: Observable playlist sets with property bindings
- `JavaFxPlayer`: Native JavaFX MediaPlayer wrapper with reactive playback events
- `WaveformPane`: Custom Canvas component for audio waveform visualization
- Automatic UI synchronization through bidirectional property binding

**Use this module for:**
- Building JavaFX desktop applications
- Creating rich, reactive user interfaces for audio management
- Applications that need automatic UI updates when data changes

## Usage Examples

### Creating an Audio Library

```kotlin
import net.transgressoft.commons.music.audio.DefaultAudioLibrary
import net.transgressoft.commons.core.JsonFileRepository

// Create a repository backed by a JSON file
val repository = JsonFileRepository(
    File("audio-library.json"),
    AudioItemMapSerializer
)

// Initialize the library
val audioLibrary = DefaultAudioLibrary(repository)

// Add audio files
val audioItem = audioLibrary.createFromFile(Paths.get("/path/to/song.mp3"))

// Batch import
val audioItems = audioLibrary.createFromFileBatchAsync(listOfPaths).await()
```

### Working with Playlists

```kotlin
import net.transgressoft.commons.music.playlist.DefaultPlaylistHierarchy

// Create a playlist hierarchy
val playlistRepository = JsonFileRepository(
    File("playlists.json"),
    PlaylistMapSerializer
)
val hierarchy = DefaultPlaylistHierarchy(playlistRepository, audioLibrary)

// Create a new playlist
val playlist = hierarchy.create("Favorites")
playlist.add(audioItem)

// Export to M3U
playlist.exportToM3uFile(Paths.get("favorites.m3u"))
```

### Generating Waveforms

```kotlin
import net.transgressoft.commons.music.waveform.DefaultAudioWaveformRepository

val waveformRepo = DefaultAudioWaveformRepository()

// Generate waveform asynchronously
val waveform = waveformRepo
    .getOrCreateWaveformAsync(audioItem, width = 800, height = 300)
    .get()

// Get the waveform image
val image = waveform.getWaveformImage()
```

### JavaFX Integration

```kotlin
import net.transgressoft.commons.fx.music.audio.ObservableAudioLibrary
import javafx.scene.control.TableView

// Create observable library
val observableLibrary = ObservableAudioLibrary(repository)

// Bind to TableView
tableView.itemsProperty().bind(observableLibrary.audioItemsProperty)

// Changes to the library automatically update the table
observableLibrary.createFromFile(Paths.get("/path/to/song.mp3"))
```

### Audio Playback

```kotlin
import net.transgressoft.commons.fx.music.player.JavaFxPlayer

// Create a player
val player = JavaFxPlayer()

// Subscribe to playback events
player.events().subscribe { event ->
    when (event) {
        is AudioItemPlayerEvent.StatusChanged ->
            println("Status: ${event.newStatus}")
        is AudioItemPlayerEvent.ProgressUpdate ->
            println("Progress: ${event.currentTime}")
    }
}

// Play an audio item
player.play(audioItem)
```

## Building the Project

```bash
# Build all modules
./gradlew build

# Run tests
./gradlew test

# Generate code coverage report
./gradlew jacocoTestReport

# Check code formatting
./gradlew ktlintCheck

# Generate documentation
./gradlew dokkaHtml
```

## 🤝 Contributing

Contributions are welcome! Please see [CONTRIBUTING.md](CONTRIBUTING.md) for details.

## 📄 License and Attributions

Copyright (c) 2025 Octavio Calleya García.

Transgressoft Commons is free software under GNU GPL version 3 license and is available [here](https://www.gnu.org/licenses/gpl-3.0.en.html#license-text).

This project builds upon several excellent open-source libraries:

- **[JAudioTagger](https://github.com/ericfarng/jaudiotagger)**: Audio metadata reading and writing library
- **[Jave](https://github.com/a-schild/jave2)**: Java Audio Video Encoder for audio transcoding
- **[Kotlin Coroutines](https://github.com/Kotlin/kotlinx.coroutines)**: Library support for Kotlin coroutines
- **[kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization)**: Kotlin multiplatform serialization
- **[Guava](https://github.com/google/guava)**: Google Core Libraries for Java
- **[Kotest](https://kotest.io/)**: Kotlin testing framework
- **[MockK](https://mockk.io/)**: Mocking library for Kotlin
- **[TestFX](https://github.com/TestFX/TestFX)**: JavaFX testing framework

Special thanks to the contributors of these projects for making this library possible.
