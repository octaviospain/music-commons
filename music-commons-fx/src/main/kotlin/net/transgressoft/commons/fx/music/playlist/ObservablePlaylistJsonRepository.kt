package net.transgressoft.commons.fx.music.playlist

import net.transgressoft.commons.fx.music.audio.ObservableAudioItem
import net.transgressoft.commons.music.playlist.AudioPlaylist
import net.transgressoft.commons.music.playlist.AudioPlaylistRepositoryBase
import com.google.common.base.Objects
import javafx.beans.property.*
import javafx.collections.FXCollections
import javafx.scene.image.Image
import mu.KotlinLogging
import java.io.File
import java.util.*
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic

class ObservablePlaylistJsonRepository(name: String, file: File) :
    AudioPlaylistRepositoryBase<ObservableAudioItem, ObservablePlaylist>(name, file, ObservablePlaylistSerializer, observablePlaylistSerializersModule) {

    private val logger = KotlinLogging.logger {}

    override fun createPlaylist(name: String): ObservablePlaylist  = createPlaylist(name, emptyList())

    override fun createPlaylist(name: String, audioItems: List<ObservableAudioItem>): ObservablePlaylist {
        require(findByName(name).isEmpty) { "Playlist with name '$name' already exists" }
        return FXPlaylist(newId(), false, name, audioItems).also {
            logger.debug { "Created playlist $it" }
            add(it)
        }
    }

    override fun createPlaylistDirectory(name: String): ObservablePlaylist = createPlaylistDirectory(name, emptyList())

    override fun createPlaylistDirectory(name: String, audioItems: List<ObservableAudioItem>): ObservablePlaylist {
        require(findByName(name).isEmpty) { "Playlist with name '$name' already exists" }
        return FXPlaylist(newId(), true, name, audioItems).also {
            logger.debug { "Created playlist directory $it" }
            add(it)
        }
    }

    private inner class FXPlaylist(
        id: Int,
        isDirectory: Boolean,
        name: String,
        audioItems: List<ObservableAudioItem> = listOf(),
        playlists: Set<ObservablePlaylist> = setOf()
    ) : MutablePlaylistBase(id, isDirectory, name, audioItems, playlists), ObservablePlaylist {

        private val logger = KotlinLogging.logger {}

        private val _nameProperty = SimpleStringProperty(this, "name", name)
            .apply {
                addListener { _, oldValue, newValue ->
                    setAndNotify(newValue, oldValue)
                }
            }

        override val nameProperty: ReadOnlyStringProperty = _nameProperty

        override var name: String
            get() = nameProperty.get()
            set(value) {
                _nameProperty.set(value)
            }

        private val _isDirectoryProperty = SimpleBooleanProperty(this, "isDirectory", isDirectory)
            .apply {
                addListener { _, oldValue, newValue ->
                    setAndNotify(newValue, oldValue)
                }
            }

        override val isDirectoryProperty: ReadOnlyBooleanProperty = _isDirectoryProperty

        override var isDirectory: Boolean
            get() = isDirectoryProperty.get()
            set(value) {
                _isDirectoryProperty.set(value)
            }

        private val _audioItemsProperty = SimpleListProperty(this, "audioItems", FXCollections.observableArrayList(audioItems))
            .apply {
                addListener { _, oldValue, newValue ->
                    setAndNotify(newValue, oldValue) {
                        replaceRecursiveAudioItems()
                        changePlaylistCover()
                    }
                }
            }

        private fun replaceRecursiveAudioItems() {
            _audioItemsRecursiveProperty.clear()
            _audioItemsRecursiveProperty.addAll(
                buildList<ObservableAudioItem> {
                    addAll(audioItems)
                    addAll(playlists.stream().flatMap { it.audioItemsRecursive.stream() }.toList())
                })
        }

        private fun changePlaylistCover() {
            audioItems.stream()
                .map { it.coverImageProperty.get() }
                .filter { it.isPresent }
                .findAny()
                .ifPresentOrElse(
                    { _coverImageProperty.set(it) }
                ) { _coverImageProperty.set(Optional.empty()) }
        }

        override val audioItemsProperty: ReadOnlyListProperty<ObservableAudioItem> = _audioItemsProperty

        override val audioItems: MutableList<ObservableAudioItem>
            get() = audioItemsProperty.get()

        private val _playlistsProperty = SimpleSetProperty(this, "playlists", FXCollections.observableSet(playlists))
            .apply {
                addListener { _, oldValue, newValue ->
                    replaceRecursiveAudioItems()
                    setAndNotify(newValue, oldValue)
                }
            }

        override val playlistsProperty: ReadOnlySetProperty<ObservablePlaylist> = _playlistsProperty

        override val playlists: MutableSet<ObservablePlaylist>
            get() = playlistsProperty.get()

        private val _audioItemsRecursiveProperty = SimpleListProperty(
            this, "audioItemsRecursive", FXCollections.observableArrayList(
                buildList {
                    addAll(audioItems)
                    addAll(playlists.stream().flatMap { it.audioItemsRecursive.stream() }.toList())
                })
        )

        override val audioItemsRecursiveProperty: ReadOnlyListProperty<ObservableAudioItem> = _audioItemsRecursiveProperty

        override val audioItemsRecursive: List<ObservableAudioItem>
            get() = audioItemsRecursiveProperty.get()

        private val _coverImageProperty = SimpleObjectProperty<Optional<Image>>(this, "coverImage",
            this.audioItems.stream().filter { it.coverImageProperty.get().isPresent }.findFirst().map { it.coverImageProperty.get().get() })

        override val coverImageProperty: ReadOnlyObjectProperty<Optional<Image>> = _coverImageProperty

        override fun addAudioItems(audioItems: Collection<ObservableAudioItem>) =
            this.audioItems.stream().anyMatch { !audioItems.contains(it) }.also {
                _audioItemsProperty.get().addAll(audioItems)
                logger.debug { "Added $audioItems to playlist $uniqueId" }
            }

        override fun removeAudioItems(audioItems: Collection<ObservableAudioItem>) =
            this.audioItems.stream().anyMatch { audioItems.contains(it) }.also {
                _audioItemsProperty.get().removeAll(audioItems)
                logger.debug { "Removed $audioItems from playlist $uniqueId" }
            }

        @Suppress("INAPPLICABLE_JVM_NAME")
        @JvmName("removeAudioItemIds")
        override fun removeAudioItems(audioItemIds: Collection<Int>) =
            this.audioItems.stream().anyMatch { audioItemIds.contains(it.id) }.also {
                _audioItemsProperty.get().removeAll { audioItemIds.contains(it.id) }
                logger.debug { "Removed audio items with ids $audioItemIds from playlist $uniqueId" }
            }

        override fun addPlaylists(playlists: Collection<ObservablePlaylist>) =
            this.playlists.stream().anyMatch { !playlists.contains(it) }.also {
                _playlistsProperty.get().addAll(playlists)
                logger.debug { "Added $playlists to playlist $uniqueId" }
            }

        override fun removePlaylists(playlists: Collection<ObservablePlaylist>) =
            this.playlists.stream().anyMatch(playlists::contains).also {
                _playlistsProperty.get().removeAll(playlists.toSet())
                logger.debug { "Removed $playlists from playlist $uniqueId" }
            }

        @Suppress("INAPPLICABLE_JVM_NAME")
        @JvmName("removePlaylistIds")
        override fun removePlaylists(playlistIds: Collection<Int>) =
            this.playlists.stream().anyMatch { playlistIds.contains(it.id) }.also {
                _playlistsProperty.get().removeAll { playlistIds.contains(it.id) }
                logger.debug { "Removed playlists with ids $playlistIds from playlist $uniqueId" }
            }

        override fun clearAudioItems() {
            _audioItemsProperty.get().clear()
            logger.debug { "Cleared audio items from playlist $uniqueId" }
        }

        override fun clearPlaylists() {
            _playlistsProperty.get().clear()
            logger.debug { "Cleared playlists from playlist $uniqueId" }
        }

        override fun compareTo(other: AudioPlaylist<ObservableAudioItem>): Int {
            return if (nameProperty.get() == other.name) {
                val size = playlists.size + audioItemsRecursive.size
                val objectSize = other.playlists.size + other.audioItemsRecursive.size
                size - objectSize
            } else {
                nameProperty.get().compareTo(other.name)
            }
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || javaClass != other.javaClass) return false
            val that = other as FXPlaylist
            return Objects.equal(name, that.name) && Objects.equal(id, that.id)
        }

        override fun hashCode() = Objects.hashCode(name, id)

        override fun toString() = "FXPlaylist(id=$id, isDirectory=$isDirectory, name='$name', audioItems=$audioItems, playlists=$playlists)"
    }
}

val observablePlaylistSerializersModule = SerializersModule {
    polymorphic(ObservablePlaylist::class, ObservablePlaylistSerializer)
}

