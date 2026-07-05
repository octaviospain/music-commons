[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.4.0-blue?logo=kotlin)](https://kotlinlang.org)
[![Ask DeepWiki](https://deepwiki.com/badge.svg)](https://deepwiki.com/octaviospain/music-commons)
[![music-commons-api javadoc](https://javadoc.io/badge2/net.transgressoft/music-commons-api/javadoc.svg?label=music-commons-api%20javadoc)](https://javadoc.io/doc/net.transgressoft/music-commons-api)
[![music-commons-core javadoc](https://javadoc.io/badge2/net.transgressoft/music-commons-core/javadoc.svg?label=music-commons-core%20javadoc)](https://javadoc.io/doc/net.transgressoft/music-commons-core)
[![music-commons-fx javadoc](https://javadoc.io/badge2/net.transgressoft/music-commons-fx/javadoc.svg?label=music-commons-fx%20javadoc)](https://javadoc.io/doc/net.transgressoft/music-commons-fx)
[![music-commons-media javadoc](https://javadoc.io/badge2/net.transgressoft/music-commons-media/javadoc.svg?label=music-commons-media%20javadoc)](https://javadoc.io/doc/net.transgressoft/music-commons-media)
[![music-commons-persistence javadoc](https://javadoc.io/badge2/net.transgressoft/music-commons-persistence/javadoc.svg?label=music-commons-persistence%20javadoc)](https://javadoc.io/doc/net.transgressoft/music-commons-persistence)
[![music-commons-persistence-fx javadoc](https://javadoc.io/badge2/net.transgressoft/music-commons-persistence-fx/javadoc.svg?label=music-commons-persistence-fx%20javadoc)](https://javadoc.io/doc/net.transgressoft/music-commons-persistence-fx)
![Maven Central Version](https://img.shields.io/maven-central/v/net.transgressoft/music-commons-core)
[![Bugs](https://sonarcloud.io/api/project_badges/measure?project=octaviospain_music-commons&metric=bugs)](https://sonarcloud.io/summary/new_code?id=octaviospain_music-commons)
[![Code Smells](https://sonarcloud.io/api/project_badges/measure?project=octaviospain_music-commons&metric=code_smells)](https://sonarcloud.io/summary/new_code?id=octaviospain_music-commons)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=octaviospain_music-commons&metric=coverage)](https://sonarcloud.io/summary/new_code?id=octaviospain_music-commons)
[![Lines of Code](https://sonarcloud.io/api/project_badges/measure?project=octaviospain_music-commons&metric=ncloc)](https://sonarcloud.io/summary/new_code?id=octaviospain_music-commons)
[![Technical Debt](https://sonarcloud.io/api/project_badges/measure?project=octaviospain_music-commons&metric=sqale_index)](https://sonarcloud.io/summary/new_code?id=octaviospain_music-commons)
[![Vulnerabilities](https://sonarcloud.io/api/project_badges/measure?project=octaviospain_music-commons&metric=vulnerabilities)](https://sonarcloud.io/summary/new_code?id=octaviospain_music-commons)
![GitHub Actions Workflow Status](https://img.shields.io/github/actions/workflow/status/octaviospain/music-commons/.github%2Fworkflows%2Fmaster.yml?logo=github)

# Music Commons

A modular, reactive Kotlin library for managing audio libraries, playlists, and waveform
visualizations. Domain state changes — metadata edits, imports, removals — propagate
automatically to every dependent view (artist catalogs, album and genre projections, playlists,
waveforms, and JavaFX bindings) with no manual wiring. Extracted from the
[Musicott](https://github.com/octaviospain/Musicott) desktop application and published to Maven
Central under `net.transgressoft`.

📖 **Full documentation lives in the [Wiki](https://github.com/octaviospain/music-commons/wiki)** —
architecture, per-feature guides, and a [Glossary](https://github.com/octaviospain/music-commons/wiki/Glossary).

## Contents

- [Why this project](#why-this-project)
- [Modules](#modules)
- [Requirements](#requirements)
- [Installation](#installation)
- [Quickstart](#quickstart)
- [Features](#features)
- [Building](#building)
- [Feature index](#feature-index)
- [Contributing](#contributing)
- [License and attributions](#license-and-attributions)

## Why this project

Musicott's audio-management logic was entangled with its desktop UI. Music Commons extracts that
logic into a standalone, layered library that can power both headless services and JavaFX
applications. It builds on [lirp](https://github.com/octaviospain/lirp) for the reactive entity
framework, repository pattern, and event-driven architecture, and layers a music-specific domain
model — audio metadata, artist/album/genre projections, playlists, waveforms, and playback — on
top. See the [Architecture](https://github.com/octaviospain/music-commons/wiki/Architecture) and
[Reactive Events](https://github.com/octaviospain/music-commons/wiki/Reactive-Events) wiki pages
for the design.

## Modules

The library splits into a reactive core and a set of opt-in persistence-mapping modules. The
reactive modules manage the domain entirely in memory and ship **no** JSON or SQL persistence
code; persistence is a consumer choice layered on top.

**Reactive modules**

| Module | Purpose |
|--------|---------|
| `music-commons-api` | Interfaces and contracts for the domain model |
| `music-commons-core` | Reactive in-memory implementations and event subscriptions |
| `music-commons-fx` | JavaFX integration — observable properties and UI components |
| `music-commons-media` | JavaFX-free playback engine and waveform generation |

**Persistence-mapping modules (opt-in, mapping-only — no entities)**

| Module | Purpose |
|--------|---------|
| `music-commons-persistence` | JSON serializers + SQL table definitions for core entities |
| `music-commons-persistence-fx` | JSON serializers + SQL table definitions for JavaFX entities |

A consumer enables persistence by injecting a concrete lirp `Repository` wired with these
mappings, or skips them entirely and subscribes to library events to persist in their own model.
Waveforms persist to JSON only; their serializer ships from `music-commons-media`. See
[SQL Persistence](https://github.com/octaviospain/music-commons/wiki/SQL-Persistence).

## Requirements

- **Java** 21 or higher
- **Kotlin** 2.4.0
- **JavaFX** 21.0.11 or higher (only for the `music-commons-fx` module)

## Installation

```kotlin
dependencies {
    implementation("net.transgressoft:music-commons-api:$version")
    implementation("net.transgressoft:music-commons-core:$version")
    implementation("net.transgressoft:music-commons-fx:$version")     // Optional — JavaFX apps
    implementation("net.transgressoft:music-commons-media:$version")  // Optional — headless playback + waveforms

    // Persistence is opt-in — add only the mapping module(s) you wire into a repository
    implementation("net.transgressoft:music-commons-persistence:$version")     // core-tier
    implementation("net.transgressoft:music-commons-persistence-fx:$version")  // JavaFX-tier
}
```

The reactive modules depend on [lirp](https://github.com/octaviospain/lirp) `lirp-api`/`lirp-core`
for the reactive/event model only. SQL persistence additionally pulls in `lirp-sql` (transitively,
through the persistence modules) plus a JDBC driver of your choice; the reactive modules never see
`lirp-sql` on their classpath.

## Quickstart

`CoreMusicLibrary.builder()` is the single entry point for headless use. With no repository
arguments the library is fully in-memory — subscribe to events to persist in your own model.

```kotlin
import net.transgressoft.commons.music.CoreMusicLibrary
import java.nio.file.Path

val library = CoreMusicLibrary.builder().build()

// Add an audio file — metadata is extracted automatically
val song = library.audioItemFromFile(Path.of("/music/song.mp3"))

// Projections update reactively — no manual dispatch
library.getArtistCatalog(song.artist)          // artist bucket
library.audioLibrary().getAlbum(song.album)    // album bucket (canonical identity)

// Playlists
val favorites = library.createPlaylist("Favorites")
library.playlistHierarchy().addAudioItemsToPlaylist(listOf(song), "Favorites")
favorites.exportToM3uFile(Path.of("favorites.m3u"))

// Waveform (async)
val waveform = library.getOrCreateWaveformAsync(song, 800, 300).get()

library.close()
```

To persist through the library, inject a repository built from the persistence-mapping modules —
JSON via `JsonFileRepository(file, <MapSerializer>)` or SQL via
`SqliteRepository.fileBacked(path, <SqlTableDef>)`:

```kotlin
val library = CoreMusicLibrary.builder()
    .audioRepository(JsonFileRepository(File("audio-library.json"), AudioItemMapSerializer))
    .playlistRepository(JsonFileRepository(File("playlists.json"), AudioPlaylistMapSerializer))
    .build()
```

For JavaFX, `FXMusicLibrary.builder()` exposes the same domain as observable properties bindable
directly to `TableView`/`ListView` and friends. See the
[Audio Library](https://github.com/octaviospain/music-commons/wiki/Audio-Library) wiki page.

## Features

Each feature has a dedicated wiki page with the full API and examples — this section is a summary.

### Reactive audio library

Audio items are indexed into three live projections that update incrementally as items are added,
changed, or removed:

- **Artist catalog** — multi-key: an item is bucketed under every artist it involves (primary,
  album, and featured), so collaborations appear in each contributor's catalog.
- **Album projection** — single-key: buckets are keyed by a canonical album identity (normalized
  name + compilation-aware album artist), so per-track variance in year, label, or compilation
  flag no longer fragments one logical album. Buckets are ordered by title.
- **Genre index** — multi-key: multi-genre items appear in every matching bucket; untagged items
  surface in a dedicated `Genre.None` bucket rather than being dropped.

`Genre` is a sealed class with ~370 predefined constants plus a `Custom` variant that preserves
unknown tags. Cover art is loaded lazily on first access. See
[Audio Library](https://github.com/octaviospain/music-commons/wiki/Audio-Library).

### Playlists

Hierarchical playlist directories with M3U import/export (nested references and cycle detection),
kept synchronized automatically when items change or are removed. See
[Playlists](https://github.com/octaviospain/music-commons/wiki/Playlists).

### Waveforms

Asynchronous, cached waveform generation with normalized-amplitude caching (same-width requests
return without audio I/O). JavaFX renders via `WaveformPane`, or `PlayableWaveformPane` for
progress fill, playhead, and click/drag-to-seek. See
[Waveforms](https://github.com/octaviospain/music-commons/wiki/Waveforms).

### Audio playback

`AudioItemPlayer` abstracts playback; `music-commons-media` provides a headless
`CoreAudioItemPlayer` over JavaSound SPI decoders (MP3, FLAC, OGG Vorbis/Opus, AAC/ALAC-M4A, WAV)
with format-specific seek precision, and `music-commons-fx` wraps it as `FXAudioItemPlayer` with
observable `volumeProperty`/`statusProperty`/`currentTimeProperty`. Play count auto-increments at
a 60% playback threshold.

```kotlin
import net.transgressoft.commons.fx.music.player.FXAudioItemPlayer
import javafx.util.Duration

val player = FXAudioItemPlayer()
player.play(song)
player.setVolume(0.8)
player.seek(Duration.seconds(30.0))
player.statusProperty.addListener { _, _, status -> println("Status: $status") }
player.dispose()
```

See [Audio Playback](https://github.com/octaviospain/music-commons/wiki/Audio-Playback).

### JavaFX integration

`FXMusicLibrary` exposes the domain as bindable observable properties — `audioItemsProperty`,
`artistCatalogsProperty` (set), and the ordered `albumsProperty`/`genreIndexesProperty`
(`ReadOnlyListProperty`, index-addressable). Burst updates during large imports are coalesced onto
the JavaFX Application Thread so bound controls converge after the import instead of repainting per
item.

### Persistence and error observability

Persistence is transparent once a repository is injected — entity changes are written without
manual save calls. Repositories flush and drain events asynchronously; wire a `LirpErrorHandler`
via the `onError` constructor parameter to observe flush/event-drain failures that are otherwise
log-only. The handler is notify-only and carries entity identity (not field values). See
[SQL Persistence](https://github.com/octaviospain/music-commons/wiki/SQL-Persistence).

### Typed path validation

Audio-item construction and iTunes import throw typed exceptions for invalid paths —
`InvalidAudioFilePathException` (missing/unreadable/non-regular file) and, on Windows,
`WindowsPathException` carrying a `WindowsViolation` reason. See
[Typed Exceptions](https://github.com/octaviospain/music-commons/wiki/Typed-Exceptions) and
[Cross-Platform Paths](https://github.com/octaviospain/music-commons/wiki/Cross-Platform-Paths).

### iTunes import

`ItunesImportService` migrates tracks and playlists from an Apple Music / iTunes `library.xml`,
returning a structured `ImportResult` (`imported`, `unresolved`, `rejectedPlaylistNames`).
Filenames are normalized to Unicode NFC so macOS-origin libraries resolve on Linux and Windows.
See [iTunes Import](https://github.com/octaviospain/music-commons/wiki/iTunes-Import).

## Building

```bash
gradle build                    # Build all modules (compile + test + coverage)
gradle test                     # Run tests
gradle testCodeCoverageReport   # Aggregated coverage report
gradle ktlintCheck              # Code style
gradle dokkaGenerate            # HTML documentation
```

Each GitHub Release ships an aggregated CycloneDX SBOM scoped to the runtime classpaths of the
published modules, and the build enforces SHA-256 dependency verification. Contributors bumping a
dependency must regenerate the verification metadata — see
[CONTRIBUTING.md](CONTRIBUTING.md) for the workflow and the supply-chain (Renovate, OSV-Scanner)
details.

## Feature index

Capabilities documented in the wiki rather than in this README:

| Feature | Wiki page |
|---------|-----------|
| Layered architecture and module dependencies | [Architecture](https://github.com/octaviospain/music-commons/wiki/Architecture) |
| Reactive event flow and subscribers | [Reactive Events](https://github.com/octaviospain/music-commons/wiki/Reactive-Events) |
| Album/genre projection ordering and lookups | [Audio Library](https://github.com/octaviospain/music-commons/wiki/Audio-Library) |
| JSON and SQL persistence wiring | [SQL Persistence](https://github.com/octaviospain/music-commons/wiki/SQL-Persistence) |
| Seek precision per codec | [Audio Playback](https://github.com/octaviospain/music-commons/wiki/Audio-Playback) |
| Windows path rules and validation | [Windows Validation](https://github.com/octaviospain/music-commons/wiki/Windows-Validation) |
| Cross-platform path handling | [Cross-Platform Paths](https://github.com/octaviospain/music-commons/wiki/Cross-Platform-Paths) |
| Domain terminology | [Glossary](https://github.com/octaviospain/music-commons/wiki/Glossary) |
| Version history and migration notes | [CHANGELOG.md](CHANGELOG.md) |

## Contributing

Contributions are welcome — see [CONTRIBUTING.md](CONTRIBUTING.md) for the development setup, test
commands, branching/PR conventions, and code style.

## License and attributions

Copyright © 2025-2026 Octavio Calleya Garcia.

Music Commons is free software under the
[GNU GPL version 3](https://www.gnu.org/licenses/gpl-3.0.en.html#license-text).

It builds on several open-source libraries, including:

- **[lirp](https://github.com/octaviospain/lirp)** — reactive entity framework and persistence infrastructure
- **[JAudioTagger](https://www.jthink.net/jaudiotagger/)** — audio metadata reading and writing
- **JavaSound SPI providers** ([mp3spi](https://github.com/umjammer/mp3spi),
  [javasound-flac](https://github.com/Tianscar/javasound-flac),
  [javasound-vorbis](https://github.com/Tianscar/javasound-vorbis),
  [javasound-aac](https://github.com/Tianscar/javasound-aac),
  [javasound-alac](https://github.com/Tianscar/javasound-alac),
  [jse-spi-opus](https://github.com/jseproject/jse-spi)) — pure-Java decoders for MP3, FLAC,
  OGG Vorbis, AAC/ALAC-M4A, and Opus/OGG
- **[Kotlin Coroutines](https://github.com/Kotlin/kotlinx.coroutines)** and
  **[kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization)**
- **[Kotest](https://kotest.io/)**, **[MockK](https://mockk.io/)**, and
  **[TestFX](https://github.com/TestFX/TestFX)** for testing
