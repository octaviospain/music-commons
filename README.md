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

music-commons was born from the need to separate the core audio management logic from the Musicott desktop application into a standalone, reusable library. The goal was to create a foundation that could power not just Musicott, but any project that needs to manage audio files and related operations—whether that's handling playlists, generating waveforms, managing metadata, or implementing import/export functionality. It provides reusable core components for audio file management: metadata handling, playlist organization, waveform generation, and playback control. The library offers a clean, layered architecture with reactive event-driven updates and optional JavaFX integration, enabling both headless services and desktop applications.

### Built on lirp

Music Commons leverages [lirp](https://github.com/octaviospain/lirp), which provides the foundational reactive entity framework, repository pattern, automatic JSON serialization, and event-driven architecture that Music Commons builds upon.

### Architecture

The library is split into a reactive core and a set of opt-in persistence mapping modules. The
reactive modules manage the domain entirely in memory through lirp's reactive/event model and
ship no JSON or SQL persistence code. Persistence is a consumer choice, layered on top.

**Reactive modules:**

- **`music-commons-api`**: Interfaces and contracts defining the audio management domain model
- **`music-commons-core`**: Reactive implementations with in-memory storage and event subscriptions
- **`music-commons-fx`**: JavaFX integration layer with observable properties and UI components
- **`music-commons-media`**: JavaFX-free audio playback engine and waveform generation

**Persistence mapping modules (opt-in):**

- **`music-commons-persistence`**: JSON serializers and SQL table definitions for the core entities
- **`music-commons-persistence-fx`**: JSON serializers and SQL table definitions for the JavaFX entities

The persistence modules are mapping-only: they define no entities, only serializers,
`SqlTableDef`s, and `ColumnConverter`s for the real reactive entities. A consumer enables
persistence by injecting a concrete lirp `Repository` wired with these mappings, or skips them
entirely and subscribes to library events to persist in their own model.

Waveforms persist to JSON only. Their serializer (`AudioWaveformMapSerializer`, package
`net.transgressoft.commons.media.persistence.waveform`) ships directly from `music-commons-media`
alongside the `ScalableAudioWaveform` entity, so no separate waveform-persistence module is needed.

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
    implementation("net.transgressoft:music-commons-fx:$version")    // Optional, for JavaFX apps
    implementation("net.transgressoft:music-commons-media:$version")  // Optional, for headless playback + waveforms

    // Persistence is opt-in — add only the mapping module(s) you wire into a repository
    implementation("net.transgressoft:music-commons-persistence:$version")        // core-tier serializers + SQL table defs
    implementation("net.transgressoft:music-commons-persistence-fx:$version")     // JavaFX-tier serializers + SQL table defs
    // The waveform JSON serializer ships from music-commons-media — no extra module needed
}
```

The reactive modules depend on [lirp](https://github.com/octaviospain/lirp) 3.0.0 for the
reactive/event model only (`lirp-api`, `lirp-core`). The version is declared once in
`gradle/libs.versions.toml` and shared across all modules:

```kotlin
implementation("net.transgressoft:lirp-api:3.0.0")
implementation("net.transgressoft:lirp-core:3.0.0")
```

SQL persistence additionally pulls in `lirp-sql` (transitively, through the persistence modules)
plus a JDBC driver of your choice. The reactive modules never see `lirp-sql` on their classpath.

## Key Features

### Genre Handling

`Genre` is a sealed class with ~370 predefined genre constants (data objects) and a `Custom` variant for arbitrary genre strings. Audio items carry multiple genres as a `Set<Genre>`, populated by parsing comma-separated tags from audio file metadata.

The `parseGenre` and `joinGenres` helpers are **top-level functions** in `net.transgressoft.commons.music.audio`; import them directly:

```kotlin
import net.transgressoft.commons.music.audio.parseGenre
import net.transgressoft.commons.music.audio.joinGenres

val genres: Set<Genre> = parseGenre("Rock, Jazz")  // setOf(Rock, Jazz)
val custom: Set<Genre> = parseGenre("Vaporwave")   // setOf(Genre.Custom("Vaporwave"))
val tag: String = joinGenres(genres)               // "Jazz, Rock"
```

Unknown genre strings are preserved as `Genre.Custom(name)` instead of being discarded. Serialization uses a JSON array: `"genres": ["Rock", "Jazz"]`

### Audio Library Management

- **Multi-format support**: MP3, M4A (AAC and ALAC), WAV, FLAC, and OGG (Vorbis and Opus) with automatic metadata extraction
- **Artist catalog indexing**: Automatic organization by artist and album with aggregated views. An item is indexed under every artist it involves (primary, album, and featured artists), so a collaboration appears in each contributor's catalog. The catalog is backed by a lirp multi-key registry projection — artist buckets update incrementally as items are added, modified, or removed, with no manual CRUD dispatch
- **Album indexing**: Flat per-album bucket view backed by a lirp single-key value-transform registry projection. Each item lands in exactly one album bucket (the album it belongs to). Buckets update incrementally as items are added, moved to a different album, or removed — use `getAlbum(album)`, `containsAudioItemWithAlbum(albumName)`, `getRandomAudioItemsFromAlbum(album)`, and `albumPublisher` to query the index
- **Genre index indexing**: Flat per-genre bucket view backed by a lirp multi-key registry projection. An item with multiple genres appears in every matching genre bucket simultaneously; an item with an empty genres set is in no genre bucket. Buckets update incrementally as items are added, their genres change, or items are removed — use `getGenreIndex(genre)`, `containsAudioItemWithGenre(genreName)`, `getRandomAudioItemsFromGenre(genre)`, and `genreIndexPublisher` to query the index
- **Batch operations**: Asynchronous batch creation via `CompletableFuture` API
- **Reactive updates**: CRUD operations publish events through Java Flow API
- **Lazy cover-art loading**: `coverImageBytes` on `ReactiveAudioItem` is loaded on first access and cached — a full-library import retains no cover bytes until they are explicitly read. The getter and setter return and store the internal array reference directly; callers must treat the returned array as immutable and must not modify its contents. All mutations must go through the setter so reactive change notifications are published. For JavaFX consumers, observing or binding `ObservableAudioItem.coverImageProperty` also triggers the lazy load on first observation — an `ImageView` bound to `coverImageProperty` therefore loads its cover on demand when the cell first displays the track, without any explicit call to `coverImageBytes`.

### Playlist Management

- **Hierarchical organization**: Nested playlist structures with directories
- **M3U export**: Export playlists preserving directory structure
- **M3U import**: Import `.m3u` and `.m3u8` playlists, including nested playlist references and cycle detection
- **Automatic synchronization**: Playlists update when audio items are modified or removed

### Waveform Visualization

- **Asynchronous generation**: Non-blocking waveform creation with configurable resolution
- **Repository caching**: Waveforms are cached and reused across requests
- **Normalized amplitude caching**: Serialized waveforms cache normalized amplitudes with their display width — same-width requests return instantly without audio file I/O, and height-only changes apply linear scaling from cache
- **JavaFX component**: Custom `WaveformPane` canvas with automatic redraw on resize
- **Playback-aware visualization**: `PlayableWaveformPane` adds two-color progress fill, playhead line, click-to-seek with drag-to-scrub, and a shimmer loading animation

### Audio Playback

- **Player abstraction**: `AudioItemPlayer` interface for custom playback engines
- **JavaFX implementation**: `JavaFxPlayer` wrapping native MediaPlayer with status monitoring
- **Play count tracking**: Automatic increment at 60% playback threshold

### Persistence

The reactive modules store everything in memory and never write to disk on their own. Persistence
is enabled by injecting a concrete repository, and there are two ways to do it:

**Library-managed persistence (lirp path)** — inject a lirp `Repository` wired with the mappings
from the opt-in persistence modules. lirp persists and reloads the library's real entities:

- **JSON file storage**: inject `JsonFileRepository(file, <MapSerializer>)` for debounced file I/O,
  using the map serializers from `music-commons-persistence` / `music-commons-persistence-fx`, plus
  `AudioWaveformMapSerializer` from `music-commons-media` for waveforms
- **SQL storage**: inject `SqliteRepository.fileBacked(db, <SqlTableDef>)` (HikariCP + JetBrains
  Exposed), using the table definitions from the persistence modules — add a JDBC driver
  (e.g. `org.xerial:sqlite-jdbc`) to your build. Audio items and playlists have both JSON and SQL
  options; waveforms are JSON-only
- **Transparent persistence**: entity changes are persisted without manual save operations
- **Async error observability**: wire a `LirpErrorHandler` on each repository to observe flush and
  event-drain failures that would otherwise be log-only

**Bring-your-own persistence** — construct the library with no repository arguments. Every
aggregate defaults to an in-memory repository, and you subscribe to library and entity events to
persist in your own model and format. No persistence module is required for this path.

#### Observing Async Persistence Failures

Repositories flush entity changes and drain event channels asynchronously. Failures in these
paths are logged by the framework but are not surfaced to application code by default. To observe
them — for example, to increment a metric or trigger an alert — pass a `LirpErrorHandler` via
the `onError` parameter at repository construction time:

```kotlin
val audioRepo = JsonFileRepository(
    file = File("audio-library.json"),
    mapSerializer = AudioItemMapSerializer,
    onError = LirpErrorHandler { throwable, ctx ->
        // ctx.operation is FLUSH (persistence write failure) or EMIT (event-drain failure)
        // ctx.repository is the repository name passed at construction time
        // ctx.entityIds contains affected entity IDs (empty for flush-cycle failures)
        logger.error("${ctx.repository}/${ctx.operation} failed", throwable)
        metrics.increment("persistence.error", "repo" to ctx.repository)
    }
)

val library = CoreMusicLibrary.builder()
    .audioRepository(audioRepo)
    .build()
```

The handler is **notify-only**: the framework logs the failure first, then invokes the handler.
The handler cannot retry or alter control flow — the library keeps running regardless of what
the handler does. Handler exceptions are swallowed.

The `LirpErrorContext` passed to the handler carries only entity identity information
(`operation`, `entityIds`, `repository`). Entity field values are never included, avoiding
sensitive data in error paths.

### JavaFX Integration

- **Observable properties**: Direct binding to JavaFX TableView, ListView, and other controls
- **Thread safety**: JavaFX-facing library and catalog projections coalesce burst updates onto the JavaFX Application Thread. The FX artist, album, and genre catalogs use lirp 3.0.0's two-phase projection approach (`registryFxMultiKeyProjection` for artist/genre, `registryFxProjection` for album) — background data transformation followed by FX-thread `fxFactory` construction — keeping large imports responsive while converging to the correct observable state
- **Enriched catalog fields**: `ObservableArtistCatalog` exposes `artistName` alongside the full `Artist` value object. `ObservableAlbum` delegates `albumName`, `albumArtist`, `isCompilation`, `year`, and `label` directly onto the bucket so binding code does not need to navigate into the `AlbumDetails` object. `ReactiveAlbum` also exposes `coverImageBytes: ByteArray?` (lazily loaded from the first item in the bucket that carries cover data). `ObservableAlbum` adds `coverProperty: ReadOnlyObjectProperty<Optional<Image>>` — a softly cached JavaFX image property resolved on the FX Application Thread, safe to bind directly to an `ImageView`
- **Custom controls**: `WaveformPane` for static waveforms, `PlayableWaveformPane` for playback-aware visualization with seek

### Typed Path Validation Exceptions

Audio-item construction and iTunes import now throw typed exceptions for invalid paths:

- `InvalidAudioFilePathException` -- any of: file does not exist, path is not a regular
  file, file is not readable. Subclass of `AudioItemManipulationException`, so existing
  catch blocks continue to work.
- `WindowsPathException` (subclass of `InvalidAudioFilePathException`) -- thrown only when
  the JVM runs on Windows. Carries a `WindowsViolation` reason: `ForbiddenChar`,
  `ReservedName`, `TrailingDotOrSpace`, or `ExceedsMaxPath`.

On a real Windows JVM, `Paths.get("bad|name.mp3")` throws `java.nio.file.InvalidPathException`
at parse time, before the validator even runs. `WindowsPathException` surfaces for inputs that
parse successfully but fail validation: reserved names, trailing dot/space, MAX_PATH overflow,
or paths assembled from already-parsed segments.

```kotlin
try {
    // Reserved name -- parses fine on Windows, but the validator rejects it.
    library.audioItemFromFile(Paths.get("C:\\music\\NUL.mp3"))
} catch (e: WindowsPathException) {
    println("Windows-specific: ${e.violation}")
} catch (e: InvalidAudioFilePathException) {
    println("Invalid path: ${e.message}")
}
```

### iTunes Library Import

The `music-commons-core` module includes an iTunes library XML import engine for migrating
tracks and playlists from Apple Music / iTunes. `ItunesImportService` accepts any `MusicLibrary<*, *>`
implementation, so it works with both `CoreMusicLibrary` (headless) and `FXMusicLibrary` (JavaFX).

**Two-step import flow:**

1. **Parse** the iTunes `library.xml` file:
   ```kotlin
   val itunesLibrary = ItunesLibraryParser.parse(Paths.get("/path/to/library.xml"))
   // Inspect itunesLibrary.playlists to let the user select which to import
   ```

2. **Import** selected playlists with a configurable policy:
   ```kotlin
   val service = ItunesImportService(musicLibrary)
   val future = service.importAsync(
       selectedPlaylists = chosen,
       itunesLibrary = itunesLibrary,
       policy = ItunesImportPolicy(useFileMetadata = false, holdPlayCount = true),
       onProgress = { progress -> println("${progress.itemsProcessed}/${progress.totalItems}") }
   )
   val result = future.get()
   result.imported                // List<ReactiveAudioItem<*>> -- successful imports
   result.unresolved              // List<UnresolvedTrack> -- could not resolve
   result.rejectedPlaylistNames   // List<RejectedPlaylistName> -- Windows-incompatible names
   ```

#### Structured Import Results

`ItunesImportService.importAsync()` returns a structured `ImportResult` with three typed buckets
so consumers can handle every failure mode individually:

```kotlin
val result = itunesImport.importAsync(...).await()
result.imported                // List<ReactiveAudioItem<*>> -- successful imports
result.unresolved              // List<UnresolvedTrack> -- could not resolve (FileNotFound,
                               //   UnsupportedType, ImportError)
result.rejectedPlaylistNames   // List<RejectedPlaylistName> -- playlist name incompatible
                               //   with Windows filesystems (only rejected when JVM is
                               //   running on Windows)
```

Filenames in iTunes XML are also normalized to Unicode NFC form after URI decoding, which
fixes the common case of a macOS-origin library failing to resolve tracks on Linux or
Windows due to NFD-encoded accented characters.

**Import policy options:**

| Option | Default | Description |
|--------|---------|-------------|
| `useFileMetadata` | `true` | When `true`, metadata comes from audio file tags. When `false`, user-facing fields come from iTunes data. |
| `holdPlayCount` | `true` | Transfers play counts from iTunes to imported items. |
| `writeMetadata` | `true` | Writes iTunes metadata to audio file tags after import. |
| `acceptedFileTypes` | All | Filters tracks by audio file type (MP3, M4A, WAV, FLAC). |

Tracks whose files don't exist on disk are always recorded in `ImportResult.unresolved`
with reason `UnresolvedReason.FileNotFound`; consumers decide how to surface them.

## Module Details

### music-commons-api

Defines contracts and interfaces for the audio management domain.

**Key Interfaces:**
- `MusicLibrary<I, P>` -- Unified facade interface implemented by both `CoreMusicLibrary` and `FXMusicLibrary`, enabling library-agnostic consumers like `ItunesImportService`
- `ReactiveAudioItem<I>` -- Audio file representation with metadata
- `ReactiveAudioLibrary<I, AC, RA, GI>` -- Generic CRUD repository with reactive event publishing and `createAudioItem(factory)` for ID-encapsulated construction; type parameters bind the artist-catalog (`AC`), album (`RA`), and genre-index (`GI`) types
- `ReactiveAlbum<RA, I>` -- Per-album bucket with `tracks: List<I>` (ordered by disc then track number), `album: AlbumDetails`, and lazy `coverImageBytes`
- `ReactiveGenreIndex<GI, I>` -- Per-genre bucket with `tracks: List<I>` (ordered by artist then album then track), `genre: Genre`
- `AlbumDetails` -- Album value type (replaces the former `Album`); carries `name`, `artist`, `year`, `isCompilation`, and `label`
- `ReactiveAudioPlaylist<I, P>` / `ReactivePlaylistHierarchy<I, P>` -- Generic playlist management with M3U export and ID-based `createPlaylist` overload
- `AudioWaveform` / `AudioWaveformRepository` -- Waveform data and generation
- `AudioItemPlayer` -- Playback controls with status monitoring, including `STALLED` for sustained streaming starvation

### music-commons-core

Reactive, in-memory implementations built on [lirp](https://github.com/octaviospain/lirp) 3.0.0's
reactive/event model. Ships no JSON or SQL persistence code — persistence is supplied by the opt-in
`music-commons-persistence` module.

- `CoreMusicLibrary` -- Unified facade for headless audio management implementing `MusicLibrary<AudioItem, MutableAudioPlaylist>` (builder-based entry point)
- `AudioLibrary` -- Narrowed `ReactiveAudioLibrary` for `AudioItem`, `ArtistCatalog`, `Album`, and `GenreIndex` types
- `PlaylistHierarchy` -- Narrowed `ReactivePlaylistHierarchy` for `AudioItem` and `MutableAudioPlaylist` types
- Internal implementations: `DefaultAudioLibrary`, `DefaultPlaylistHierarchy`, `DefaultAudioWaveformRepository`
- **Artist catalog** -- multi-key registry projection (`registryMultiKeyProjection`); each item is bucketed under every artist it involves; buckets update incrementally as audio items change. Query via `artistCatalogs()`, `getArtistCatalog(artist)`, `containsAudioItemWithArtist(name)`, `getRandomAudioItemsFromArtist(artist)`, `artistCatalogPublisher`
- **Album index** -- single-key value-transform registry projection (`registryProjection`); each item lands in exactly one album bucket keyed by its `album` field. Query via `albums()`, `getAlbum(album)`, `containsAudioItemWithAlbum(name)`, `getRandomAudioItemsFromAlbum(album)`, `albumPublisher`; navigate from an item via `item.albumIn(library)`
- **Genre index** -- multi-key registry projection (`registryMultiKeyProjection`); an item with multiple genres appears in each matching genre bucket; an item with an empty genres set is in no bucket. Query via `genreIndexes()`, `getGenreIndex(genre)`, `containsAudioItemWithGenre(name)`, `getRandomAudioItemsFromGenre(genre)`, `genreIndexPublisher`
- Event subscribers for reactive synchronization between components

### music-commons-fx

Bridges core module with JavaFX's property binding system.

- `FXMusicLibrary` -- Unified facade for JavaFX audio management implementing `MusicLibrary<ObservableAudioItem, ObservablePlaylist>` with observable properties (builder-based entry point)
- `ObservableAudioLibrary` -- Narrowed `ReactiveAudioLibrary` with JavaFX observable properties for UI binding; exposes `audioItemsProperty`, `emptyLibraryProperty`, `artistCatalogsProperty`, `albumsProperty`, and `genreIndexesProperty`; flat sets and counts are derived from these properties (e.g. `albumsProperty.map { it.album }.toSet()`, `albumsProperty.sizeProperty()`)
- `ObservablePlaylistHierarchy` -- Narrowed `ReactivePlaylistHierarchy` with a JavaFX observable playlists collection
- `FXAudioItemPlayer` -- JavaFX wrapper around the bounded-streaming `CoreAudioItemPlayer`, exposing volume, status, and current-time as observable properties
- `WaveformPane` -- Custom Canvas component for static waveform visualization
- `PlayableWaveformPane` -- Region component with progress fill, playhead, seek, and shimmer loading
- `SeekEvent` -- Custom JavaFX event fired on click-to-seek and drag-to-scrub interactions

### music-commons-media

JavaFX-free audio playback engine based on `javax.sound.sampled` SPI decoders, plus reactive
waveform generation. Also ships the JSON serializer for waveforms (waveforms are JSON-only),
co-located with the `ScalableAudioWaveform` entity.

- `CoreAudioItemPlayer` -- Headless audio player supporting MP3, FLAC, OGG, AAC/M4A, and WAV via SPI decoders. Bounded PCM streaming pipeline with `SourceDataLine` output and format-specific seek (see [Seek precision](#seek-precision))
- `ScalableAudioWaveform` -- Reactive waveform entity with on-demand amplitude generation and cached scaling
- `AudioWaveformMapSerializer` -- map serializer for `JsonFileRepository`, preserving cached width and amplitudes (package `net.transgressoft.commons.media.persistence.waveform`)
- `StallDetector` -- Detects and recovers from PCM read stalls in the pump loop
- `DurationProber` -- Resolves playable duration including full-decode fallback for AAC/M4A containers
- `PcmVolume` -- In-place linear gain application supporting 8/16/24/32-bit PCM
- `FlacPcmStreamSeeker`, `Mp3PcmStreamSeeker`, `OggPcmStreamSeeker` -- Format-specific seek implementations

### music-commons-persistence

Opt-in JSON and SQL mapping for the core-tier entities. Mapping-only — defines no entity classes.

- `AudioItemMapSerializer` / `AudioPlaylistMapSerializer` -- kotlinx-serialization map serializers for `JsonFileRepository`
- `audioItemSerializersModule` -- contextual `SerializersModule` for the domain value types (artist, album, label, genre, metadata), so the domain stays annotation-free
- `MutableAudioItemSqlTableDef` / `AudioPlaylistSqlTableDef` -- SQL table definitions for `SqliteRepository`
- `CountryConverter`, `GenreConverter` -- column converters for SQL persistence (duration and path columns reuse lirp's built-in converters)

### music-commons-persistence-fx

Opt-in JSON and SQL mapping for the JavaFX-tier entities. Mapping-only — reuses the core-tier
contextual serializers and column converters.

- `ObservableAudioItemMapSerializer` / `ObservablePlaylistMapSerializer` -- map serializers for `JsonFileRepository`
- `FXAudioItemSqlTableDef` / `ObservablePlaylistSqlTableDef` -- SQL table definitions for `SqliteRepository`

## Usage Examples

### Core module (headless)

Use `CoreMusicLibrary.builder()` as the single entry point for headless audio management:

```kotlin
import net.transgressoft.commons.music.CoreMusicLibrary
import net.transgressoft.commons.music.m3u.M3uImportService
import net.transgressoft.commons.persistence.music.audio.AudioItemMapSerializer
import net.transgressoft.commons.persistence.music.audio.MutableAudioItemSqlTableDef
import net.transgressoft.commons.persistence.music.playlist.AudioPlaylistMapSerializer
import net.transgressoft.commons.media.persistence.waveform.AudioWaveformMapSerializer
import net.transgressoft.lirp.persistence.json.JsonFileRepository
import net.transgressoft.lirp.persistence.sql.SqliteRepository

// Bring-your-own persistence: no repository arguments -> in-memory storage.
// Subscribe to library/entity events to persist in your own model and format.
val library = CoreMusicLibrary.builder().build()

// Library-managed JSON persistence -- inject JsonFileRepository instances wired with the
// serializers from music-commons-persistence (audio items, playlists) and music-commons-media (waveforms)
val library = CoreMusicLibrary.builder()
    .audioRepository(JsonFileRepository(File("audio-library.json"), AudioItemMapSerializer))
    .playlistRepository(JsonFileRepository(File("playlists.json"), AudioPlaylistMapSerializer))
    .waveformRepository(JsonFileRepository(File("waveforms.json"), AudioWaveformMapSerializer))
    .build()

// Library-managed SQL persistence -- add a JDBC driver (e.g. org.xerial:sqlite-jdbc) to your build;
// MutableAudioItemSqlTableDef (from music-commons-persistence) is a ready SqlTableDef<AudioItem>
// that reconstructs the real entity through lirp's construction SPI -- no factory needed
val library = CoreMusicLibrary.builder()
    .audioRepository(SqliteRepository.fileBacked(Path.of("audio-library.db"), MutableAudioItemSqlTableDef))
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

// Import playlist from M3U (M3uImportService is AutoCloseable — close to release its coroutine scope)
val importedPlaylist = M3uImportService(library).use { importer ->
    importer.import(Path.of("/path/to/favorites.m3u"))
}

// Lifecycle
library.close()
```

### JavaFX Integration

Use `FXMusicLibrary.builder()` for JavaFX applications with observable property bindings:

```kotlin
import net.transgressoft.commons.fx.music.FXMusicLibrary
import net.transgressoft.commons.persistence.fx.music.audio.FXAudioItemSqlTableDef
import net.transgressoft.commons.persistence.fx.music.audio.ObservableAudioItemMapSerializer
import net.transgressoft.commons.persistence.fx.music.playlist.ObservablePlaylistMapSerializer
import net.transgressoft.commons.media.persistence.waveform.AudioWaveformMapSerializer
import net.transgressoft.lirp.persistence.json.JsonFileRepository
import net.transgressoft.lirp.persistence.sql.SqliteRepository

val fxLibrary = FXMusicLibrary.builder()
    .audioRepository(JsonFileRepository(File("audio-library.json"), ObservableAudioItemMapSerializer))
    .playlistRepository(JsonFileRepository(File("playlists.json"), ObservablePlaylistMapSerializer, loadOnInit = false))
    .waveformRepository(JsonFileRepository(File("waveforms.json"), AudioWaveformMapSerializer))
    .build()

// Library-managed SQL persistence -- add a JDBC driver (e.g. org.xerial:sqlite-jdbc) to your build;
// FXAudioItemSqlTableDef (from music-commons-persistence-fx) is a ready SqlTableDef<ObservableAudioItem>
val fxLibrary = FXMusicLibrary.builder()
    .audioRepository(SqliteRepository.fileBacked(Path.of("audio-library.db"), FXAudioItemSqlTableDef))
    .build()

// Bind directly to JavaFX UI components
tableView.itemsProperty().bind(fxLibrary.audioItemsProperty)

// Derive counts and flat sets from the album/genre index set properties
val albumCount = fxLibrary.albumsProperty.sizeProperty()
albumCountLabel.textProperty().bind(albumCount.asString())
val genreCount = fxLibrary.genreIndexesProperty.sizeProperty()
genreCountLabel.textProperty().bind(genreCount.asString())

// Flat sets (e.g. for filtering or display): derived on demand, not live-bound properties
val allAlbums = fxLibrary.albumsProperty.map { it.album }.toSet()
val allGenres = fxLibrary.genreIndexesProperty.map { it.genre }.toSet()

// Album and genre index sets are observable for list/grid binding
fxLibrary.albumsProperty.addListener { _, _, _ -> /* refresh album browser */ }
fxLibrary.genreIndexesProperty.addListener { _, _, _ -> /* refresh genre browser */ }

// Large create bursts are coalesced before the FX-facing projections refresh,
// so bound controls converge after import bursts instead of repainting per item.

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

#### Seek precision

The `CoreAudioItemPlayer` (and `FXAudioItemPlayer`) resolve seek positions differently for each codec:

| Format | Seek mechanism | Typical precision |
|--------|---------------|-------------------|
| **FLAC** | Byte-stream bisection to the nearest FLAC frame | ≤ 1 frame (~4096 samples ≈ 85 ms at 48 kHz) |
| **MP3** | Xing/Info TOC lookup when present; frame-scan fallback for CBR | ≤ 1 MP3 frame (~26 ms at 48 kHz) |
| **OGG Vorbis** | Ogg page bisection validates the target; PCM decoded forward to the exact offset | Sample-accurate, but seek cost grows linearly with position |
| **WAV / AIFF** | Byte-aligned to the nearest PCM frame | Effectively sample-accurate (4 bytes for 16-bit stereo) |
| **AAC / M4A** | Full-decode skip from the start (no TOC in the container) | Exact byte offset, but seek cost grows linearly with position |

All formats clamp seek positions to `[0, totalDuration]`. The seek target is consumed by the pump
thread on its next iteration, so there is a small delay between calling `seek()` and the position
change becoming audible.

### Waveform Visualization (JavaFX)

```kotlin
import net.transgressoft.commons.fx.music.waveform.WaveformPane
import javafx.scene.paint.Color

// Static waveform (no playback controls)
val waveformPane = WaveformPane()
waveformPane.drawWaveformAsync(waveform, Color.CYAN, Color.BLACK)
stackPane.children.add(waveformPane)
```

#### Playback-Aware Waveform

`PlayableWaveformPane` adds progress visualization, seek interaction, and a shimmer loading animation:

```kotlin
import net.transgressoft.commons.fx.music.waveform.PlayableWaveformPane
import net.transgressoft.commons.fx.music.waveform.SeekEvent

val playablePane = PlayableWaveformPane()
playablePane.loadWaveform(waveform)

// Bind progress to player (0.0-1.0)
player.currentTimeProperty.addListener { _, _, newTime ->
    val total = player.totalDuration.toMillis()
    if (total > 0) playablePane.progressProperty.set(newTime.toMillis() / total)
}

// Handle seek events (fires on mouse release)
playablePane.addEventHandler(SeekEvent.SEEK) { event ->
    player.seek(event.seekRatio * player.totalDuration.toMillis())
}
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

## Migration Notes

### Album API Breaking Changes

The album and genre bucket types were renamed and simplified. If you are upgrading from an earlier version, update your source code according to the table below. **The JSON persistence format is unchanged** — existing library files remain readable without any data migration.

#### Renamed Types

| Old name | New name | Notes |
|----------|----------|-------|
| `Album` (value type) | `AlbumDetails` | The `album` property on `ReactiveAudioItem` still exists; only its type changed |
| `ReactiveAlbumCatalog` | `ReactiveAlbum` | Album bucket interface |
| `ReactiveGenreCatalog` | `ReactiveGenreIndex` | Genre bucket interface |
| `ObservableAlbumCatalog` | `ObservableAlbum` | FX album bucket interface |
| `ObservableGenreCatalog` | `ObservableGenreIndex` | FX genre bucket interface |
| `AlbumSet` | removed | Was a helper; replaced by `tracks: List<I>` on the bucket |
| `AlbumView` | removed | Was a helper; replaced by `tracks: List<I>` on the bucket |

#### Renamed Accessors and Publishers

| Old accessor / publisher | New accessor / publisher |
|--------------------------|--------------------------|
| `getAlbumCatalog(album)` | `getAlbum(album)` |
| `getGenreCatalog(genre)` | `getGenreIndex(genre)` |
| `albumCatalogPublisher` | `albumPublisher` |
| `genreCatalogPublisher` | `genreIndexPublisher` |
| `albumCatalogsProperty` (FX) | `albumsProperty` |
| `genreCatalogsProperty` (FX) | `genreIndexesProperty` |

> The **Artist axis intentionally keeps** its `*Catalog` naming (`ReactiveArtistCatalog`,
> `getArtistCatalog`, `artistCatalogPublisher`, `ObservableArtistCatalog`, etc.). Only the
> Album and Genre axes were renamed.

#### Bucket Members: `Set` → `List`

`ReactiveAlbum.tracks` and `ReactiveGenreIndex.tracks` return `List<I>` instead of the former `Set`-based container. The list is ordered and deduplicated by lirp before it reaches the bucket — callers no longer need to sort or deduplicate the members.

- Album buckets: ordered by disc number, then track number
- Genre index buckets: ordered by artist name, then album name, then track number

#### Full-Value Album Identity

`AlbumDetails` uses full structural equality (all fields). The former `Album` type used name-keyed equality within an artist, meaning two albums with the same name but different artists or years were considered equal. Under the new identity, each distinct combination of `(name, artist, year, isCompilation, label)` is a separate bucket.

#### Navigation Helper

A convenience extension function `item.albumIn(library)` is available to navigate from an audio item to its album bucket:

```kotlin
import net.transgressoft.commons.music.audio.albumIn

val album: Optional<out ReactiveAlbum<*, AudioItem>> = item.albumIn(library)
```

## Supply Chain

Each GitHub Release includes a CycloneDX SBOM artifact (`music-commons-sbom-cyclonedx`) — an aggregated `bom.json` describing the runtime classpaths of all six modules (`api`, `core`, `fx`, `media`, `test`, `fx-test`). Per-PR builds run GitHub Dependency Review and fail on HIGH+ severity CVEs. A weekly OSV-Scanner job uploads SARIF results to the repository Security tab.

The build also enforces SHA-256 dependency verification via `gradle/verification-metadata.xml`: every resolved artifact is checked against its locked checksum on every Gradle invocation, defeating compromised-mirror and typosquat attacks. See [CONTRIBUTING.md](CONTRIBUTING.md) for the regeneration workflow contributors must run when bumping a dependency.

Dependency hygiene is automated via [Renovate](https://docs.renovatebot.com/) (`renovate.json` at the repo root). Renovate opens weekly grouped pull requests — `gradle-libraries` (library bumps in `gradle/libs.versions.toml` and `build.gradle`), `kotlin` (Kotlin plugin + stdlib + KSP in lockstep), `github-actions` (workflow action SHAs, preserving the SHA-pin policy via the `helpers:pinGitHubActionDigests` preset), and `gradle-wrapper`. CVE-driven security PRs (`vulnerabilityAlerts`) bypass the weekly window and open at any time. Auto-merge is **disabled**: every Renovate PR is manually reviewed and merged.

Renovate coexists with the global Transgressoft init script (`~/.gradle/init.d/transgressoft-updates.gradle`) without conflict — Renovate updates **declarations** in `gradle/libs.versions.toml` and `build.gradle`, while the init script operates at **dependency-resolution time** to align Transgressoft-published artifacts. The two act on different layers and never compete.

**Owner action (post-merge, one-time):** for `renovate.json` to take effect, the [Renovate GitHub App](https://github.com/apps/renovate) must be installed on `octaviospain/music-commons`. Until installed, the configuration file is inert and no PRs will be opened. After installation, Renovate publishes a "Renovate: dependency dashboard" issue and begins its weekly cadence.

## Contributing

Contributions are welcome! Please see [CONTRIBUTING.md](CONTRIBUTING.md) for details.

## License and Attributions

Copyright (c) 2025-2026 Octavio Calleya Garcia.

Music Commons is free software under GNU GPL version 3 license, available [here](https://www.gnu.org/licenses/gpl-3.0.en.html#license-text).

This project builds upon several excellent open-source libraries:

- **[JAudioTagger](https://github.com/ericfarng/jaudiotagger)**: Audio metadata reading and writing library
- **JavaSound SPI providers** ([mp3spi](https://github.com/umjammer/mp3spi), [javasound-flac](https://github.com/Tianscar/javasound-flac), [javasound-vorbis](https://github.com/Tianscar/javasound-vorbis), [javasound-aac](https://github.com/Tianscar/javasound-aac), [JAAD](https://github.com/Almax/jaad), [javasound-alac](https://github.com/Tianscar/javasound-alac), [jse-spi-opus](https://github.com/jseproject/jse-spi)): pure-Java audio decoders for MP3, FLAC, OGG Vorbis, AAC/M4A, ALAC/M4A, and Opus/OGG; metadata and decoding use prioritized provider fallback, native FLAC seeks use the decoder's random-access path, generic compressed seeks discard decoded PCM to stay decoder-aligned, and playback volume scales signed PCM sample widths up to 32-bit. Opus-in-M4A is not supported (no pure-Java Maven Central SPI for Opus in ISO-BMFF/M4A containers).
- **[Kotlin Coroutines](https://github.com/Kotlin/kotlinx.coroutines)**: Library support for Kotlin coroutines
- **[kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization)**: Kotlin multiplatform serialization
- **[lirp](https://github.com/octaviospain/lirp)**: Reactive entity framework and persistence infrastructure
- **[Kotest](https://kotest.io/)**: Kotlin testing framework
- **[MockK](https://mockk.io/)**: Mocking library for Kotlin
- **[TestFX](https://github.com/TestFX/TestFX)**: JavaFX testing framework
