# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

The 0.7.0 line simplifies the album/genre projection API and completes the
persistence-agnostic split. Breaking changes are listed below; **the JSON
persistence format is unchanged**, so existing library files remain readable
without any data migration.

### Added
- Album and Genre projections on the audio library. Albums are a single-key
  projection keyed by canonical album identity; genres are a multi-key
  projection, with untagged tracks surfaced in a dedicated `Genre.None` bucket
  (#151, #164). Projection buckets are ordered by title (#166).
- FX `FXAlbum.coverProperty` triggers a lazy cover-art load on first
  observation, so an `ImageView` bound to it loads on demand (#154, #163).

### Changed
- **BREAKING:** Album API simplified. The `Album` value type became
  `AlbumDetails`; `ReactiveAlbumCatalog`/`ReactiveGenreCatalog` became
  `ReactiveAlbum`/`ReactiveGenreIndex` (FX: `ObservableAlbum`/
  `ObservableGenreIndex`); the `AlbumSet`/`AlbumView` helpers were removed in
  favor of `tracks: List<I>` on the bucket. Accessors and publishers were
  renamed to match (`getAlbum`, `getGenreIndex`, `albumPublisher`,
  `genreIndexPublisher`; FX `albumsProperty`, `genreIndexesProperty`). The
  **Artist axis intentionally keeps** its `*Catalog` naming (#156).
- **BREAKING:** FX `albumsProperty` and `genreIndexesProperty` changed from
  `ReadOnlySetProperty` to `ReadOnlyListProperty` to expose ordered,
  index-addressable buckets. `artistCatalogsProperty` remains a set (#156, #166).
- Album buckets merge on canonical album identity, so per-track variance in
  year, label, album artist, or compilation flag no longer fragments one
  logical album (#162).
- Upgraded to lirp 3.0.0; base modules are fully persistence-agnostic and ship
  no JSON or SQL code (#150).

### Fixed
- JSON timestamp and play-count values are no longer lost on reload (#150).

### Migration

| Old symbol | New symbol |
|------------|------------|
| `Album` (value type) | `AlbumDetails` |
| `ReactiveAlbumCatalog` | `ReactiveAlbum` |
| `ReactiveGenreCatalog` | `ReactiveGenreIndex` |
| `ObservableAlbumCatalog` | `ObservableAlbum` |
| `ObservableGenreCatalog` | `ObservableGenreIndex` |
| `AlbumSet` / `AlbumView` | removed — use `tracks: List<I>` |
| `getAlbumCatalog(album)` | `getAlbum(album)` |
| `getGenreCatalog(genre)` | `getGenreIndex(genre)` |
| `albumCatalogPublisher` | `albumPublisher` |
| `genreCatalogPublisher` | `genreIndexPublisher` |
| `albumCatalogsProperty` (FX) | `albumsProperty` (now `ReadOnlyListProperty`) |
| `genreCatalogsProperty` (FX) | `genreIndexesProperty` (now `ReadOnlyListProperty`) |

`ReactiveAlbum.tracks` and `ReactiveGenreIndex.tracks` return an ordered,
deduplicated `List<I>` — callers no longer sort or deduplicate. Album buckets
are ordered by disc then track number; genre buckets by artist, album, then
track number. When wiring FX change listeners against the property type,
`ListChangeListener` replaces `SetChangeListener` for albums and genre indexes.

## [0.6.0] — 2026-06-09

### Added
- Headless `CoreAudioItemPlayer` and the `music-commons-media` module: a
  JavaFX-free playback engine over JavaSound SPI decoders, with ALAC and Opus
  support and format-specific seeking.
- M3U playlist import (`M3uImportService`) with nested-reference and
  cycle detection.
- Supply-chain hardening: aggregated CycloneDX SBOM, OSV-Scanner workflow, and
  SHA-256 dependency verification.
- Lazy cover-art loading — a full-library import no longer retains cover bytes
  until they are read.

### Changed
- Persistence migrated to lirp's SQL repository via persistence-mapping modules.
- JavaFX observable library updates are coalesced during large imports.
- Repository-agnostic library refactor around `AudioItemMetadata`.

### Fixed
- Playback seek regressions, M4A decode fallback, and MP3 SPI provider ordering.

## [0.5.2] — 2026-05-02

### Fixed
- Recursive audio-item changes propagate from descendant playlists to their
  ancestors.

## [0.5.1] — 2026-05-01

### Fixed
- Folder hierarchy and cover image are preserved when only leaf playlists are
  selected for export.

## [0.5.0] — 2026-04-27

### Added
- iTunes library XML import: parser, metadata policy, and library integration.
- `PlayableWaveformPane` with progress fill, playhead, and click-to-seek;
  normalized-amplitude caching in waveform serialization.
- Cross-platform test fixtures and Windows path handling.

### Changed
- **BREAKING:** `AudioLibrary`/`PlaylistHierarchy` narrowed into `Reactive*`
  interfaces; introduced the `MusicLibrary` facade.
- `Genre` refactored from an enum to a sealed class.
- Adopted lirp reactive delegates, projection maps, and collection events.

### Fixed
- Player `isPlayable()` NPE; `MediaException` wrapped in `play()`.
- Cover image hydrated for JSON-loaded playlists.

## [0.4.0] — 2026-03-27

### Added
- `ObservableArtistCatalog`; lifecycle management via `close()`.

### Changed
- Kotlin upgrade; adopted lirp 1.2.0; Gradle versions moved to a version catalog.
- Renamed `createTagTag()` to `createTag()`.

### Fixed
- `addAudioItem()` behavior; `getRandomAudioItemsFromArtist` honoring the size
  parameter.

## [0.3.0] — 2025-11-23

### Added
- Artist-catalog subscription support.

### Fixed
- `ObservableAudioLibrary` defects.

## [0.2.0] — 2025-11-15

### Fixed
- Artist-catalog initialization from a non-empty repository.
- Removed a hardcoded dispatcher in `ScalableAudioWaveform`.

## [0.1.0] — 2025-10-22

### Added
- Initial release: reactive audio library, artist-catalog registry, playlists,
  waveform generation, JavaFX integration, and asynchronous batch import.