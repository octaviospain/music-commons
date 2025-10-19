# Music Commons FX

JavaFX integration module providing observable wrappers for music-commons-core components with automatic UI synchronization.

## Overview

This module bridges the reactive music-commons-core with JavaFX's property binding system, enabling seamless integration with JavaFX UI components. All core entities are wrapped with JavaFX observable properties that automatically update bound UI elements when data changes.

### Key Components

- **ObservableAudioLibrary** - Audio library with observable collections for TableView/ListView binding
- **ObservablePlaylistHierarchy** - Playlist manager with observable playlist sets
- **JavaFxPlayer** - Native JavaFX MediaPlayer wrapper with reactive playback events
- **WaveformPane** - Canvas component for visualizing audio waveforms

## Quick Start

### Observable Audio Library

```kotlin
val repository = JsonFileRepository(file, ObservableAudioItemMapSerializer)
val audioLibrary = ObservableAudioLibrary(repository)

// Bind to UI components
tableView.itemsProperty().bind(audioLibrary.audioItemsProperty)
emptyLabel.visibleProperty().bind(audioLibrary.emptyLibraryProperty)

// Create audio items
val audioItem = audioLibrary.createFromFile(audioFilePath)

// Property changes automatically update the UI
audioItem.titleProperty.set("New Title")    // TableView updates automatically
audioItem.title = "Another Title"           // Also triggers JavaFX property update
```

### Observable Playlists

```kotlin
val playlistRepository = JsonFileRepository(file, ObservablePlaylistMapSerializer)
val playlistHierarchy = ObservablePlaylistHierarchy(playlistRepository, audioLibrary)

// Bind playlist collection to UI
listView.itemsProperty().bind(playlistHierarchy.playlistsProperty)

// Create and manage playlists
val playlist = playlistHierarchy.createPlaylist("My Playlist")

// Bind individual playlist properties
playlistNameLabel.textProperty().bind(playlist.nameProperty)
playlistCoverImage.imageProperty().bind(
    Bindings.createObjectBinding(
        { playlist.coverImageProperty.get().orElse(null) },
        playlist.coverImageProperty
    )
)

// Audio items observable collection
playlistTableView.itemsProperty().bind(playlist.audioItemsProperty)
```

### Audio Playback

```kotlin
val player = JavaFxPlayer()

// Bind player properties to UI
playButton.disableProperty().bind(
    player.statusProperty.isEqualTo(AudioItemPlayer.Status.PLAYING)
)
volumeSlider.valueProperty().bindBidirectional(player.volumeProperty)
timeSlider.valueProperty().bind(
    Bindings.createDoubleBinding(
        { player.currentTimeProperty.get().toMillis() },
        player.currentTimeProperty
    )
)

// Play audio and emit events
player.play(audioItem)  // Automatically increments play count at 60% threshold

// Subscribe to playback events
audioLibrary.playerSubscriber.addOnNextEventAction(PLAYED) { event ->
    println("Played: ${event.entities.values.first().title}")
}
```

### Waveform Visualization

```kotlin
val waveformPane = WaveformPane()
waveformPane.widthProperty().bind(container.widthProperty())
waveformPane.heightProperty().set(200.0)

// Load and display waveform
val waveform = waveformRepository.getOrCreateWaveform(audioItem, 800, 200)
waveformPane.drawWaveformAsync(waveform, Color.CYAN, Color.BLACK)

// Automatically redraws when resized
```

## Reactive Integration

Observable components maintain bidirectional synchronization:

```kotlin
// Subscribe playlists and waveforms to library changes
audioLibrary.subscribe(playlistHierarchy.audioItemEventSubscriber)
audioLibrary.subscribe(waveformRepository.audioItemEventSubscriber)

// Changes propagate automatically
audioLibrary.remove(audioItem)
// → Playlist removes the audio item from its observable collection
// → TableView updates automatically
// → Waveform repository deletes the waveform
// → All observable properties notify listeners
```

## Property Binding Patterns

### Read-Only Properties

Most properties are read-only to prevent external modification bypassing validation:

```kotlin
val audioItem: ObservableAudioItem = // ...

// Correct: Modify through the entity
audioItem.title = "New Title"  // Triggers property update and change events

// Also works: Through the property (bidirectional binding)
audioItem.titleProperty.set("New Title")

// The property is observable
titleLabel.textProperty().bind(audioItem.titleProperty)
```

### Observable Collections

Collections automatically sync with entity modifications:

```kotlin
val playlist: ObservablePlaylist = // ...

// Add items through the entity API
playlist.addAudioItem(audioItem)

// Observable property reflects the change
playlist.audioItemsProperty.size  // Automatically increased
```

## Thread Safety

All JavaFX property modifications are executed on the JavaFX Application Thread:

```kotlin
// Safe from any thread
CoroutineScope(Dispatchers.IO).launch {
    audioItem.title = "New Title"
    // Internally calls Platform.runLater for property update
}
```

## Persistence

Observable components use the same serialization as core components:

```kotlin
// Serialize observable library (excludes JavaFX properties)
val json = Json.encodeToString(audioLibrary.findAll())

// Deserialize and restore JavaFX bindings
val restoredLibrary = ObservableAudioLibrary(
    JsonFileRepository(file, ObservableAudioItemMapSerializer)
)
```

## Dependencies

- **music-commons-core** - Core reactive audio management
- **JavaFX** - UI toolkit and property binding system
- **kotlinx-coroutines-javafx** - Coroutine integration with JavaFX thread

## Testing

JavaFX components require TestFX initialization:

```kotlin
beforeSpec {
    FxToolkit.registerPrimaryStage()
}

"Observable properties update correctly" {
    val audioItem = audioLibrary.createFromFile(path)

    audioItem.titleProperty.set("Test Title")
    audioItem.title shouldBe "Test Title"

    // Verify reactive updates
    audioLibrary.audioItemsProperty.contains(audioItem) shouldBe true
}
```

For complete JavaFX UI examples, refer to the parent project documentation.
