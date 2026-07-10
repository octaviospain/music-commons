# API Surface Audit

This document records the verdict for every public declaration across the six production modules,
derived from a full `explicitApiWarning()` scan plus review of the throwaway BCV `.api` files.

**Audit bias:** minimize the stable-ABI surface. Default verdict for anything whose consumer purpose
is unclear is **Hide** (make `internal`).

**Scope constraint:** within-package renames only. No cross-package moves.

**Base-class default:** abstract/open framework types default to `internal` unless consumers are
expected to subclass them.

---

## Legend

| Verdict | Meaning |
|---------|---------|
| Keep | Remains public as-is |
| Keep (final) | Remains public but `open` is removed |
| Rename | Rename within the same package |
| Hide | Make `internal` |
| Nullability-fix | Add/tighten null annotations |
| Error-contract | Add or clarify exception declarations |

---

## Module: `music-commons-api`

### Top-level / façade

| Declaration | File | Current | Verdict | Rationale |
|-------------|------|---------|---------|-----------|
| `MusicLibrary` | `MusicLibrary.kt` | `interface` | Keep | Primary consumer entry-point façade |

### `audio/`

| Declaration | File | Current | Verdict | Rationale |
|-------------|------|---------|---------|-----------|
| `ReactiveAudioItem` | `ReactiveAudioItem.kt` | `interface` | Keep | Core consumer contract |
| `ReactiveAudioLibrary` | `ReactiveAudioLibrary.kt` | `interface` | Keep | Core consumer contract |
| `ReactiveArtistCatalog` | `ReactiveArtistCatalog.kt` | `interface` | Keep | Core consumer contract |
| `ReactiveAlbum` | `ReactiveAlbum.kt` | `interface` | Keep | Core consumer contract |
| `ReactiveGenreIndex` | `ReactiveGenreIndex.kt` | `interface` | Keep | Core consumer contract |
| `Artist` | `Artist.kt` | `interface` | Keep | Domain value used in catalog API |
| `AlbumDetails` | `AlbumDetails.kt` | `data class` | Keep | Domain value returned by library API |
| `Genre` | `Genre.kt` | `sealed class` | Keep | Domain discriminated union for genres |
| `Label` | `Label.kt` | `interface` | Keep | Domain value on audio items |
| `AudioFileType` | `AudioFileType.kt` | `enum class` | Keep | File-type discrimination used by consumers |
| `AudioItemMetadata` | `AudioItemMetadata.kt` | `interface` | Keep | Property grouping on `ReactiveAudioItem` |
| `AudioItemManipulationException` | `AudioItemManipulationException.kt` | `class` | Keep | Public error contract for metadata writes |
| `AudioMetadataIO` | `AudioMetadataIO.kt` | `interface` | Keep | Used across modules (core + FX); must remain public |
| `HeaderInfo` | `HeaderInfo.kt` | `data class` | Keep | Used in core across module boundary; must remain public |

### `audio/` — Genre definitions (bulk)

All 370+ `GenreDefinitions.kt` objects (one per ID3 genre): Keep. They are the enumerated set of
`Genre` subtypes that consumers pattern-match against.

### `audio/` — Genre extensions

| Declaration | File | Current | Verdict | Rationale |
|-------------|------|---------|---------|-----------|
| `parseGenre` | `GenreExtensions.kt` | top-level `fun` | Keep | Utility needed to create `Genre` values from strings |
| `joinGenres` | `GenreExtensions.kt` | top-level `fun` | Keep | Utility needed to serialize `Genre` collections |

### `util/`

| Declaration | File | Current | Verdict | Rationale |
|-------------|------|---------|---------|-----------|
| `OsDetector` | `OsDetector.kt` | `object` | Keep | Used across modules in both production and test code; cannot be hidden |
| `WindowsPathValidator` | `WindowsPathValidator.kt` | `object` | Keep | Used across modules in core production code; cannot be hidden |
| `WindowsLongPathSupport` | `WindowsLongPathSupport.kt` | `object` | Keep | Used across modules in both production and test code; cannot be hidden |
| `PathJsonExtensions.kt` | `PathJsonExtensions.kt` | top-level funs | Keep | Used by media and persistence modules across module boundaries; must stay public |
| `InvalidAudioFilePathException` | `InvalidAudioFilePathException.kt` | `class` | Keep | Public error thrown when adding an invalid file |
| `WindowsPathException` | `WindowsPathException.kt` | `class` | Keep | Exposed via `InvalidAudioFilePathException`; used cross-module in test assertions |

### `itunes/`

| Declaration | File | Current | Verdict | Rationale |
|-------------|------|---------|---------|-----------|
| `ImportResult` | `ImportResult.kt` | `sealed class` | Keep | Return type of import operations |

### `player/`

| Declaration | File | Current | Verdict | Rationale |
|-------------|------|---------|---------|-----------|
| `AudioItemPlayer` | `AudioItemPlayer.kt` | `interface` | Keep | Player consumer contract |
| `UnsupportedAudioPlaybackException` | `UnsupportedAudioPlaybackException.kt` | `class` | Keep | Public error contract for playback |
| `AudioItemPlayerEvent` | `event/AudioItemPlayerEvent.kt` | `sealed class` | Keep | Event type used in player subscriptions |

### `playlist/`

| Declaration | File | Current | Verdict | Rationale |
|-------------|------|---------|---------|-----------|
| `AudioPlaylist` | `AudioPlaylist.kt` | `interface` | Keep | Base playlist consumer contract |
| `ReactiveAudioPlaylist` | `ReactiveAudioPlaylist.kt` | `interface` | Keep | Reactive playlist consumer contract |
| `ReactivePlaylistHierarchy` | `ReactivePlaylistHierarchy.kt` | `interface` | Keep | Hierarchy consumer contract |

### `waveform/`

| Declaration | File | Current | Verdict | Rationale |
|-------------|------|---------|---------|-----------|
| `AudioWaveform` | `AudioWaveform.kt` | `interface` | Keep | Waveform consumer contract |
| `AudioWaveformRepository` | `AudioWaveformRepository.kt` | `interface` | Keep | Repository consumer contract |
| `AudioWaveformProcessingException` | `AudioWaveformProcessingException.kt` | `class` | Keep | Public error contract |

---

## Module: `music-commons-core`

### Top-level / façade

| Declaration | File | Current | Verdict | Rationale |
|-------------|------|---------|---------|-----------|
| `CoreMusicLibrary` | `CoreMusicLibrary.kt` | `class` | Keep (final) | Headless entry-point; private constructor, no need to be open |

### `audio/`

| Declaration | File | Current | Verdict | Rationale |
|-------------|------|---------|---------|-----------|
| `AudioItem` | `MutableAudioItem.kt` | `interface` (inner) | Keep | Typed audio item for core consumers |
| `MutableAudioItem` | `MutableAudioItem.kt` | `internal class` | Already internal | No change |
| `ImmutableAlbum` | `ImmutableAlbum.kt` | `internal class` | Already internal | No change |
| `AudioLibraryBase` | `AudioLibraryBase.kt` | `abstract class` | Keep | Extended cross-module by FX module; cannot be made internal |
| `ArtistCatalogRegistryBase` | `ArtistCatalogRegistryBase.kt` | `abstract class` | Keep | Extended cross-module by FX module; cannot be made internal |
| `AlbumRegistryBase` | `AlbumRegistryBase.kt` | `abstract class` | Keep | Extended cross-module by FX module; cannot be made internal |
| `GenreIndexRegistryBase` | `GenreIndexRegistryBase.kt` | `abstract class` | Keep | Extended cross-module by FX module; cannot be made internal |
| `AudioLibrary` | `AudioLibrary.kt` | `interface` | Keep | Typed library interface for core consumers |
| `ArtistCatalog` | `ArtistCatalog.kt` | `interface` | Keep | Typed catalog interface for core consumers |
| `Album` | `Album.kt` | `interface` | Keep | Typed album interface for core consumers |
| `GenreIndex` | `GenreIndex.kt` | `interface` | Keep | Typed genre index interface for core consumers |
| `AlbumRepresentative` (funs) | `AlbumRepresentative.kt` | top-level `fun` | Keep | Used cross-module by FX module's `FXAlbumRegistry` |
| `JAudioTaggerMetadataIO` | `JAudioTaggerMetadataIO.kt` | `class` | Keep | Used cross-module as default value in FX module constructors |
| `AudioItemHelpers` (funs) | `AudioItemHelpers.kt` | top-level funs | Keep | Comparators and cover helpers used by consumers |

### `audio/event/`

| Declaration | File | Current | Verdict | Rationale |
|-------------|------|---------|---------|-----------|
| `AudioItemEventSubscriber` | `AudioItemEventSubscriber.kt` | `open class` | Keep | Used cross-module by FX module and waveform repository wiring |

### `event/`

| Declaration | File | Current | Verdict | Rationale |
|-------------|------|---------|---------|-----------|
| `PlayedEventSubscriber` | `PlayedEventSubscriber.kt` | `open class` | Keep | Used cross-module by FX library for play count tracking |

### `itunes/`

| Declaration | File | Current | Verdict | Rationale |
|-------------|------|---------|---------|-----------|
| `ItunesImportService` | `ItunesImportService.kt` | `class` | Keep | Consumer-facing import service |
| `ItunesImportPolicy` | `ItunesImportPolicy.kt` | `data class` | Keep | Configuration data class passed by consumers to the import service |
| `ImportProgress` | `ImportProgress.kt` | `data class` | Keep | Progress type emitted by the import service |
| `ItunesLibrary` | `ItunesLibrary.kt` | `data class` | Keep | Parameter of public `importAsync` — consumers receive it from `ItunesLibraryParser` |
| `ItunesTrack` | `ItunesTrack.kt` | `data class` | Keep | Exposed via `ItunesLibrary.tracks: Map<Int, ItunesTrack>` |
| `ItunesPlaylist` | `ItunesPlaylist.kt` | `data class` | Keep | Parameter of public `importAsync` — consumers select playlists from it |
| `ItunesLibraryParser` | `ItunesLibraryParser.kt` | `object` | Keep | Part of consumer workflow: consumers call `ItunesLibraryParser.parse(path)` first |

### `m3u/`

| Declaration | File | Current | Verdict | Rationale |
|-------------|------|---------|---------|-----------|
| `M3uImportService` | `M3uImportService.kt` | `class` | Keep | Consumer-facing M3U import service |
| `M3uImportException` | `M3uImportService.kt` | `class` | Keep | Public error contract for M3U import |
| `M3uParseException` | `M3uImportService.kt` | `class` | Keep | Public error contract for M3U parsing |
| `M3uCycleException` | `M3uImportService.kt` | `class` | Keep | Public error contract for cyclic M3U detection |

### `playlist/`

| Declaration | File | Current | Verdict | Rationale |
|-------------|------|---------|---------|-----------|
| `MutableAudioPlaylist` | `MutableAudioPlaylist.kt` | `interface` | Keep | Typed playlist for core consumers |
| `PlaylistHierarchy` | `PlaylistHierarchy.kt` | `interface` | Keep | Typed hierarchy for core consumers |
| `MutablePlaylistBase` | `MutablePlaylistBase.kt` | `abstract class` | Keep | Extended cross-module by FX playlist implementations |
| `PlaylistHierarchyBase` | `PlaylistHierarchyBase.kt` | `abstract class` | Keep | Extended cross-module by FX playlist hierarchy |

### `playlist/event/`

| Declaration | File | Current | Verdict | Rationale |
|-------------|------|---------|---------|-----------|
| `AudioPlaylistEventSubscriber` | `AudioPlaylistEventSubscriber.kt` | `open class` | Keep | Used cross-module by FX playlist hierarchy wiring |

---

## Module: `music-commons-media`

### `player/`

| Declaration | File | Current | Verdict | Rationale |
|-------------|------|---------|---------|-----------|
| `CoreAudioItemPlayer` | `CoreAudioItemPlayer.kt` | `open class` | Keep (final) | Concrete headless player; `open` serves no consumer purpose — seal it |

### `persistence/waveform/`

| Declaration | File | Current | Verdict | Rationale |
|-------------|------|---------|---------|-----------|
| `AudioWaveformMapSerializer()` | `AudioWaveformMapSerializer.kt` | top-level fun | Keep | Needed by consumers wiring `JsonFileRepository<Int, AudioWaveform>` |
| `AudioWaveformSerializer` | `AudioWaveformSerializer.kt` | `class` | Keep | Needed when consumers build a custom map serializer or register the module |

### `util/`

| Declaration | File | Current | Verdict | Rationale |
|-------------|------|---------|---------|-----------|
| `decodeToPcmStream` | `AudioDecoderUtil.kt` | top-level `fun` | Hide | Low-level SPI plumbing; not part of consumer contract |
| `readAudioFileFormat` | `AudioDecoderUtil.kt` | top-level `fun` | Hide | Low-level SPI plumbing; not part of consumer contract |

### `waveform/`

| Declaration | File | Current | Verdict | Rationale |
|-------------|------|---------|---------|-----------|
| `ScalableAudioWaveform` | `ScalableAudioWaveform.kt` | `class` | Keep | Concrete `AudioWaveform` implementation needed by consumers |
| `audioWaveformRepository()` | `DefaultAudioWaveformRepository.kt` | top-level `fun` | Keep | Factory function for building the waveform repository |

---

## Module: `music-commons-fx`

### Top-level / façade

| Declaration | File | Current | Verdict | Rationale |
|-------------|------|---------|---------|-----------|
| `FXMusicLibrary` | `FXMusicLibrary.kt` | `class` | Keep | JavaFX entry-point façade |

### `audio/`

| Declaration | File | Current | Verdict | Rationale |
|-------------|------|---------|---------|-----------|
| `ObservableAudioItem` | `ObservableAudioItem.kt` | `interface` | Keep | JavaFX consumer contract |
| `ObservableAudioLibrary` | `ObservableAudioLibrary.kt` | `interface` | Keep | JavaFX consumer contract |
| `ObservableArtistCatalog` | `ObservableArtistCatalog.kt` | `interface` | Keep | JavaFX consumer contract |
| `ObservableAlbum` | `ObservableAlbum.kt` | `interface` | Keep | JavaFX consumer contract |
| `ObservableGenreIndex` | `ObservableGenreIndex.kt` | `interface` | Keep | JavaFX consumer contract |
| `FXAudioItem` | `FXAudioItem.kt` | `class` | Keep | Concrete JavaFX audio item; consumers get instances from the library |

### `player/`

| Declaration | File | Current | Verdict | Rationale |
|-------------|------|---------|---------|-----------|
| `FXAudioItemPlayer` | `FXAudioItemPlayer.kt` | `class` | Keep (final) | Concrete JavaFX player; not intended for subclassing |

### `playlist/`

| Declaration | File | Current | Verdict | Rationale |
|-------------|------|---------|---------|-----------|
| `ObservablePlaylist` | `ObservablePlaylist.kt` | `interface` | Keep | JavaFX consumer contract |
| `ObservablePlaylistHierarchy` | `ObservablePlaylistHierarchy.kt` | `interface` | Keep | JavaFX consumer contract |

### `waveform/`

| Declaration | File | Current | Verdict | Rationale |
|-------------|------|---------|---------|-----------|
| `WaveformPane` | `WaveformPane.kt` | `class` | Keep | JavaFX UI component for consumers |
| `PlayableWaveformPane` | `PlayableWaveformPane.kt` | `class` | Keep | JavaFX UI component for consumers |
| `SeekEvent` | `SeekEvent.kt` | `class` | Keep | Event fired by `PlayableWaveformPane`; consumers wire listeners on it |

### `util/`

| Declaration | File | Current | Verdict | Rationale |
|-------------|------|---------|---------|-----------|
| `LazyObservationObjectProperty` | `LazyObservationObjectProperty.kt` | `class` | Hide | Internal observable property implementation; not a consumer API |

---

## Module: `music-commons-persistence`

### Top-level

| Declaration | File | Current | Verdict | Rationale |
|-------------|------|---------|---------|-----------|
| `rawConstruct` | `RawConstruction.kt` | top-level `fun` | Keep | Used cross-module by `music-commons-persistence-fx` to construct FX entities; cannot be hidden |
| `lirpSerializerFor` | `RawConstruction.kt` | top-level `fun` | Keep | Used cross-module by `music-commons-persistence-fx` serializers; cannot be hidden |

### `audio/` — contextual serializers

| Declaration | File | Current | Verdict | Rationale |
|-------------|------|---------|---------|-----------|
| `PathContextualSerializer` | `AudioItemContextualSerializers.kt` | `object` | Hide | Consumed indirectly via `audioItemSerializersModule`; not a standalone consumer API |
| `DurationContextualSerializer` | `AudioItemContextualSerializers.kt` | `object` | Hide | Same rationale |
| `LocalDateTimeContextualSerializer` | `AudioItemContextualSerializers.kt` | `object` | Hide | Same rationale |
| `CountryCodeContextualSerializer` | `AudioItemContextualSerializers.kt` | `object` | Hide | Same rationale |
| `GenreContextualSerializer` | `AudioItemContextualSerializers.kt` | `object` | Hide | Same rationale |
| `ArtistContextualSerializer` | `AudioItemContextualSerializers.kt` | `object` | Hide | Same rationale |
| `LabelContextualSerializer` | `AudioItemContextualSerializers.kt` | `object` | Hide | Same rationale |
| `AlbumContextualSerializer` | `AudioItemContextualSerializers.kt` | `object` | Hide | Same rationale |
| `AudioItemMetadataContextualSerializer` | `AudioItemContextualSerializers.kt` | `object` | Hide | Same rationale |
| `audioItemSerializersModule` | `AudioItemContextualSerializers.kt` | `val` | Keep | Consumer-facing: required when building `JsonFileRepository<Int, AudioItem>` |

### `audio/` — SQL infrastructure

| Declaration | File | Current | Verdict | Rationale |
|-------------|------|---------|---------|-----------|
| `MutableAudioItemSqlTableDef` | `MutableAudioItemSqlTableDef.kt` | `object` | Keep | Consumer-facing: passed to `SqliteRepository` by consumers wiring SQL persistence |
| `CountryConverter` | `CountryConverter.kt` | `object` | Keep | Needed alongside `MutableAudioItemSqlTableDef` for custom column wiring |
| `GenreConverter` | `GenreConverter.kt` | `object` | Keep | Needed alongside `MutableAudioItemSqlTableDef` for custom column wiring |
| `AudioItemMapSerializer()` | `AudioItemMapSerializer.kt` | top-level fun | Keep | Factory for the JSON map serializer; consumer-facing |

### `playlist/`

| Declaration | File | Current | Verdict | Rationale |
|-------------|------|---------|---------|-----------|
| `AudioPlaylistSqlTableDef` | `AudioPlaylistSqlTableDef.kt` | `object` | Keep | Consumer-facing: passed to `SqliteRepository` for playlist persistence |
| `AudioPlaylistMapSerializer()` | `AudioPlaylistMapSerializer.kt` | top-level fun | Keep | Factory for the JSON map serializer; consumer-facing |

---

## Module: `music-commons-persistence-fx`

| Declaration | File | Current | Verdict | Rationale |
|-------------|------|---------|---------|-----------|
| `FXAudioItemSqlTableDef` | `FXAudioItemSqlTableDef.kt` | `object` | Keep | Consumer-facing: passed to `SqliteRepository` for FX audio item persistence |
| `ObservableAudioItemMapSerializer()` | `ObservableAudioItemMapSerializer.kt` | top-level fun | Keep | Consumer-facing JSON map serializer factory |
| `ObservablePlaylistSqlTableDef` | `ObservablePlaylistSqlTableDef.kt` | `object` | Keep | Consumer-facing: passed to `SqliteRepository` for FX playlist persistence |
| `ObservablePlaylistMapSerializer()` | `ObservablePlaylistMapSerializer.kt` | top-level fun | Keep | Consumer-facing JSON map serializer factory |

---

## Surviving Public Base Classes

The following types remain public after the audit. All others that were `abstract class` or `open class`
without a genuine consumer subclassing story are made `internal` as part of the breaking-change application.

| Type | Module | Reason kept public |
|------|--------|-------------------|
| `AudioLibraryBase` | core | Extended by `music-commons-fx` across the module boundary |
| `ArtistCatalogRegistryBase` | core | Extended by `music-commons-fx` across the module boundary |
| `AlbumRegistryBase` | core | Extended by `music-commons-fx` across the module boundary |
| `GenreIndexRegistryBase` | core | Extended by `music-commons-fx` across the module boundary |
| `MutablePlaylistBase` | core | Extended by `music-commons-fx` across the module boundary |
| `PlaylistHierarchyBase` | core | Extended by `music-commons-fx` across the module boundary |
| `AudioItemEventSubscriber` | core | `open`; subscribed/extended by `music-commons-fx` |
| `PlayedEventSubscriber` | core | `open`; subscribed/extended by `music-commons-fx` |
| `AudioPlaylistEventSubscriber` | core | `open`; subscribed/extended by `music-commons-fx` |

These framework-base and subscriber classes are `public` because `music-commons-fx` extends or
subscribes to them across the module boundary, so Kotlin `internal` cannot hide them. They are not
intended for consumer extension; that intent is signalled explicitly in each class-header KDoc.

`@SubclassOptInRequired` was deliberately **not** applied to them. The only opt-in marker available at
the lock is `@MusicCommonsExperimentalApi`, whose contract is "this API is experimental and may change
or be removed." These base classes are stable, not experimental — pairing them with the experimental
marker would both mis-brand a stable contract and force an ERROR-level `@OptIn(MusicCommonsExperimentalApi::class)`
on every first-party subclass (`Default*` in core, `FX*` in fx), which contradicts the minimize-surface
intent. Introducing a second, non-experimental subclass-opt-in marker would expand and re-churn the frozen
`.api` baseline after the lock. The KDoc signal is therefore the correct guard for the stable-but-not-a-consumer-
extension-point case; a dedicated non-experimental subclass-opt-in marker can be added in a future minor
release if a hard compile-time guard is later judged necessary.

---

## Summary of Breaking Changes to Apply

### `music-commons-api`

No breaking visibility changes — all reviewed types are used cross-module and must stay public.
The types that were originally assessed as Hide are corrected to Keep after cross-module usage analysis.

### `music-commons-core`

No breaking visibility changes — all reviewed types are needed cross-module by `music-commons-fx`
(base classes, subscriber classes, `JAudioTaggerMetadataIO`, `deriveRepresentativeAlbumDetails`).
Making them `internal` breaks the FX module which extends/uses them across the module boundary.

**Note:** Sealing these from external subclassing is a separate concern to address via `@SubclassOptInRequired`
annotation in a future plan — that is the correct mechanism for "not intended for consumer extension"
when `internal` is not applicable due to cross-module usage.

### `music-commons-media`

- `decodeToPcmStream` → `internal`
- `readAudioFileFormat` → `internal`
- `CoreAudioItemPlayer` → remove `open` keyword (make effectively final)

### `music-commons-fx`

- `LazyObservationObjectProperty` → `internal class`
- `FXAudioItemPlayer` → remove `open` keyword if present (verify)

### `music-commons-persistence`

- `rawConstruct` → kept `public` (consumed by `music-commons-persistence-fx` across the module boundary)
- `lirpSerializerFor` → kept `public` (consumed by `music-commons-persistence-fx` across the module boundary)
- All contextual serializer objects (`PathContextualSerializer`, `DurationContextualSerializer`,
  `LocalDateTimeContextualSerializer`, `CountryCodeContextualSerializer`, `GenreContextualSerializer`,
  `ArtistContextualSerializer`, `LabelContextualSerializer`, `AlbumContextualSerializer`,
  `AudioItemMetadataContextualSerializer`) → `internal`
