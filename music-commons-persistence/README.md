# Music Commons Persistence

Opt-in JSON and SQL persistence mapping for the **core-tier** entities. This module is
mapping-only: it defines no entity classes, only serializers, SQL table definitions, and column
converters for the real reactive entities in `music-commons-core`.

## Overview

The reactive modules (`api`, `core`, `fx`, `media`) keep all state in memory and ship no
persistence code. A consumer enables persistence by injecting a concrete lirp `Repository` wired
with the mappings from this module — or skips it entirely and subscribes to library events to
persist in their own model. Audio items and playlists can be persisted to JSON or SQL; waveforms
are JSON-only and their serializer ships from `music-commons-media`.

## Key Components

### JSON serialization

- **`AudioItemMapSerializer`** / **`AudioPlaylistMapSerializer`** — kotlinx-serialization map
  serializers for a lirp `JsonFileRepository`.
- **`audioItemSerializersModule`** — a contextual `SerializersModule` for the domain value types
  (artist, album, label, genre, country, metadata, and the `Path`/`Duration`/`LocalDateTime`
  scalars), so the domain model itself stays annotation-free.

### SQL persistence

- **`MutableAudioItemSqlTableDef`** / **`AudioPlaylistSqlTableDef`** — table definitions for a lirp
  `SqliteRepository`. They reconstruct the real entities through lirp's construction SPI, so no
  hand-written factory is required.
- **`CountryConverter`**, **`GenreConverter`** — column converters for SQL persistence (duration
  and path columns reuse lirp's built-in converters).

## Usage

Inject a repository built from these mappings through `CoreMusicLibrary.builder()`:

```kotlin
import net.transgressoft.commons.music.CoreMusicLibrary
import net.transgressoft.commons.persistence.music.audio.AudioItemMapSerializer
import net.transgressoft.commons.persistence.music.audio.MutableAudioItemSqlTableDef
import net.transgressoft.lirp.persistence.json.JsonFileRepository
import net.transgressoft.lirp.persistence.sql.SqliteRepository

// JSON file storage
val jsonLibrary = CoreMusicLibrary.builder()
    .audioRepository(JsonFileRepository(File("audio-library.json"), AudioItemMapSerializer))
    .build()

// SQL storage — add a JDBC driver (e.g. org.xerial:sqlite-jdbc) to your build
val sqlLibrary = CoreMusicLibrary.builder()
    .audioRepository(SqliteRepository.fileBacked(Path.of("audio-library.db"), MutableAudioItemSqlTableDef))
    .build()
```

See the [SQL Persistence](https://github.com/octaviospain/music-commons/wiki/SQL-Persistence) wiki
page for the full wiring, including error observability via `LirpErrorHandler`.

## Dependencies

- **music-commons-core** — the entities being mapped
- **lirp-api**, **lirp-sql-api** — repository and SQL table-definition contracts (exposed in this
  module's public API)
- **lirp-sql** — SQL repository implementation

## License

Copyright © 2025-2026 Octavio Calleya Garcia.

This program is free software under the terms of the GNU General Public License version 3 or
(at your option) any later version.
