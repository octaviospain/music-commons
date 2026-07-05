# Music Commons Persistence FX

Opt-in JSON and SQL persistence mapping for the **JavaFX-tier** entities. Like
`music-commons-persistence`, this module is mapping-only — it defines no entity classes and reuses
the core-tier contextual serializers and column converters.

## Overview

`music-commons-fx` keeps its observable entities in memory and ships no persistence code. This
module supplies the serializers and SQL table definitions needed to persist `ObservableAudioItem`
and `ObservablePlaylist` through the `audioRepository(...)` and `playlistRepository(...)` hooks on
`FXMusicLibrary.builder()`.

## Key Components

### JSON serialization

- **`ObservableAudioItemMapSerializer`** / **`ObservablePlaylistMapSerializer`** — map serializers
  for a lirp `JsonFileRepository`, reusing `audioItemSerializersModule` from
  `music-commons-persistence` for the shared domain value types.

### SQL persistence

- **`FXAudioItemSqlTableDef`** / **`ObservablePlaylistSqlTableDef`** — table definitions for a lirp
  `SqliteRepository`, reconstructing the observable entities through lirp's construction SPI.

## Usage

```kotlin
import net.transgressoft.commons.fx.music.FXMusicLibrary
import net.transgressoft.commons.persistence.fx.music.audio.FXAudioItemSqlTableDef
import net.transgressoft.commons.persistence.fx.music.audio.ObservableAudioItemMapSerializer
import net.transgressoft.lirp.persistence.json.JsonFileRepository
import net.transgressoft.lirp.persistence.sql.SqliteRepository

// JSON file storage
val jsonLibrary = FXMusicLibrary.builder()
    .audioRepository(JsonFileRepository(File("audio-library.json"), ObservableAudioItemMapSerializer))
    .build()

// SQL storage — add a JDBC driver (e.g. org.xerial:sqlite-jdbc) to your build
val sqlLibrary = FXMusicLibrary.builder()
    .audioRepository(SqliteRepository.fileBacked(Path.of("audio-library.db"), FXAudioItemSqlTableDef))
    .build()
```

See the [SQL Persistence](https://github.com/octaviospain/music-commons/wiki/SQL-Persistence) wiki
page for details.

## Dependencies

- **music-commons-fx** — the observable entities being mapped
- **music-commons-persistence** — reused core-tier contextual serializers and column converters
- **lirp-api**, **lirp-sql-api** — repository and SQL table-definition contracts (exposed in this
  module's public API)
- **lirp-sql**, **javafx-controls**

## License

Copyright © 2025-2026 Octavio Calleya Garcia.

This program is free software under the terms of the GNU General Public License version 3 or
(at your option) any later version.
