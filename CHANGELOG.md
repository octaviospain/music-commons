# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [1.0.0] — 2026-07-11

First stable release. The public API is frozen and guarded by binary-compatibility
validation and Kotlin strict `explicitApi()`; subsequent 1.x releases follow
semantic versioning. This release also hardens the lifecycle, concurrency, and
corner-case behavior of the three facades. **The JSON persistence format is
unchanged**, so existing library files remain readable without migration.

### Added
- `@MusicCommonsExperimentalApi` opt-in marker for the pre-stable API surface,
  with a documented deprecation and `@since` policy (#203).
- Optional per-library `instanceName` builder setter, surfaced as a
  `libraryInstance` MDC key across library operations and import scopes (#201).
- Dedup-on-add duplicate-file policy: adding an item whose `uniqueId` already
  exists returns the existing item instead of creating a second (#201).

### Changed
- **Public API frozen for 1.0.** Strict `explicitApi()` is enabled on every
  production module and the Binary Compatibility Validator gates unintended
  public-API changes. Non-contract types were internalized (PCM/format decoders,
  `LazyObservationObjectProperty`, persistence contextual serializers) and
  `CoreAudioItemPlayer` was finalized (#203).
- **Single live instance per JVM enforced.** Constructing a second live library
  over the same registry slot now fails fast with `IllegalStateException` instead
  of silently overwriting the first library's aggregate resolution;
  construct → close → construct-again is fully supported. Cross-instance identity
  is defined by `uniqueId` and content equality — ids are repository-local (#197).
- **Lifecycle contracts hardened.** `close()` is idempotent and use-after-close
  is rejected: mutations and id-lookups throw, while predicate and introspection
  queries stay readable against the frozen state. Import services are cancelled on
  close, so an in-flight import completing after close adds nothing (#199).
- Audio-item `uniqueId` is now a physical-identity key
  (`fileName-durationSeconds-bitRate`), dropping the mutable title term so
  re-tagging the same file keeps one identity (#201).
- Thread-safety, multi-instance, identity, and lifecycle contracts are documented
  as consumer-facing KDoc on the three facades, with README and wiki summaries
  (#203, #201).

### Fixed
- FXAudioLibrary artist-catalog projection lost-update race that left
  `artistCatalogsProperty` intermittently missing the final update during large,
  involved-artist-heavy imports (#195).
- iTunes import dropped the artist catalog of any artist that appeared only in a
  track's title (featured or remix credits). Items were registered from their
  file tag and then mutated in place, re-keying the artist-catalog projection
  while the entity was already live, so a projection read racing the mutation
  could permanently drop a title-only artist's bucket. Imported items are now
  built with their final metadata before registration — a new `metadataTransform`
  overload on `createFromFile`/`audioItemFromFile` — so they enter the library
  complete, in a single registration, with no post-add re-key (#206).
- Registry-overwrite corruption when a second library instance shared a store
  (#197).
- Id generation fails fast on counter overflow instead of handing out negative
  ids; `createPlaylist` guards against unassigned ids; the playlist audio-item
  delete subscriber is non-bypassable; and a GC-evicted `ImmutableAlbum` cover
  reloads on next access (#201).

## [0.7.1] — 2026-07-07

A stability and quality release. Most changes harden the test suite, CI matrix,
and diagnostics; the user-facing fixes below address memory and FX-refresh
behavior during large imports.

### Changed
- FXAudioLibrary catalog refresh is now incremental — each debounce tick applies
  only the delta instead of clearing and rebuilding all three FX collections,
  removing the O(n²) FX-thread work that caused non-convergence on slower
  machines during large imports (#176).
- Logging levels right-sized: per-entity and hot-path statements moved from DEBUG
  to TRACE, coarse operation summaries added at DEBUG, and MDC keys
  (`importSessionId`, `audioItemId`, `catalogRebuildId`, `playlistId`) wired
  across the major flows (#193).

### Fixed
- Bounded PCM accumulation in `ScalableAudioWaveform` to prevent memory
  exhaustion when generating waveforms for long files (#190).

### Documentation
- Documented the AAC/M4A seek limitation and added real-fixture MP3/OGG seek
  tests (#183).

## [0.7.0] — 2026-07-05

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