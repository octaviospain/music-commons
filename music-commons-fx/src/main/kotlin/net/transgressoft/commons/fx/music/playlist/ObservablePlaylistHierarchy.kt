/******************************************************************************
 * Copyright (C) 2025  Octavio Calleya Garcia                                 *
 *                                                                            *
 * This program is free software: you can redistribute it and/or modify       *
 * it under the terms of the GNU General Public License as published by       *
 * the Free Software Foundation, either version 3 of the License, or          *
 * (at your option) any later version.                                        *
 *                                                                            *
 * This program is distributed in the hope that it will be useful,            *
 * but WITHOUT ANY WARRANTY; without even the implied warranty of             *
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the              *
 * GNU General Public License for more details.                               *
 *                                                                            *
 * You should have received a copy of the GNU General Public License          *
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.     *
 ******************************************************************************/

package net.transgressoft.commons.fx.music.playlist

import net.transgressoft.commons.entity.toIds
import net.transgressoft.commons.event.CrudEvent.Type.CREATE
import net.transgressoft.commons.event.CrudEvent.Type.DELETE
import net.transgressoft.commons.event.CrudEvent.Type.UPDATE
import net.transgressoft.commons.fx.music.audio.ObservableAudioItem
import net.transgressoft.commons.fx.music.audio.ObservableAudioLibrary
import net.transgressoft.commons.music.audio.AudioItemManipulationException
import net.transgressoft.commons.music.playlist.AudioPlaylist
import net.transgressoft.commons.music.playlist.PlaylistHierarchyBase
import net.transgressoft.commons.music.playlist.event.AudioPlaylistEventSubscriber
import net.transgressoft.commons.persistence.Repository
import net.transgressoft.commons.persistence.VolatileRepository
import com.google.common.base.Objects
import javafx.application.Platform
import javafx.beans.property.ReadOnlyBooleanProperty
import javafx.beans.property.ReadOnlyListProperty
import javafx.beans.property.ReadOnlyObjectProperty
import javafx.beans.property.ReadOnlySetProperty
import javafx.beans.property.ReadOnlyStringProperty
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleListProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.property.SimpleSetProperty
import javafx.beans.property.SimpleStringProperty
import javafx.collections.FXCollections
import javafx.collections.ObservableSet
import javafx.scene.image.Image
import mu.KotlinLogging
import java.util.*
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic

/**
 * JavaFX-compatible playlist hierarchy with observable playlist collections.
 *
 * Maintains an observable set of all playlists in the hierarchy that automatically syncs
 * with repository changes. Enables JavaFX UI components to bind directly to the playlist
 * collection and receive automatic updates when playlists are created, modified, or removed.
 * All modifications are executed on the JavaFX Application Thread for thread safety.
 */
class ObservablePlaylistHierarchy
    @JvmOverloads
    constructor(
        repository: Repository<Int, ObservablePlaylist> = VolatileRepository("ObservablePlaylistHierarchy"),
        // Only needed when the provided repository is not empty
        audioLibrary: ObservableAudioLibrary? = null
    ): PlaylistHierarchyBase<ObservableAudioItem, ObservablePlaylist>(repository) {

        private val logger = KotlinLogging.logger {}

        private val observablePlaylistsSet: ObservableSet<ObservablePlaylist> = FXCollections.observableSet()

        val playlistsProperty: ReadOnlySetProperty<ObservablePlaylist> =
            SimpleSetProperty(this, "playlists", FXCollections.observableSet(observablePlaylistsSet))

        private val playlistChangesSubscriber =
            AudioPlaylistEventSubscriber<ObservablePlaylist, ObservableAudioItem>("InternalAudioPlaylistSubscriber").apply {
                addOnNextEventAction(CREATE, UPDATE) { event ->
                    synchronized(playlistsProperty) {
                        Platform.runLater { observablePlaylistsSet.addAll(event.entities.values) }
                    }
                }
                addOnNextEventAction(DELETE) { event ->
                    synchronized(playlistsProperty) {
                        Platform.runLater { observablePlaylistsSet.removeAll(event.entities.values.toSet()) }
                    }
                }
            }

        init {
            require(repository.isEmpty || (repository.isEmpty.not() && audioLibrary != null)) {
                "AudioLibrary is required when loading a non empty playlistHierarchy"
            }

            disableEvents(CREATE, UPDATE, DELETE) // disable events until the initial load from the file is completed
            runForAll {
                val playlistWithAudioItems = FXPlaylist(it.id, it.isDirectory, it.name, mapAudioItemsFromIds(it.audioItems.toIds(), audioLibrary!!))
                repository.addOrReplace(playlistWithAudioItems)
            }
            runForAll {
                val playlistMissingPlaylists =
                    repository.findById(it.id)
                        .orElseThrow { IllegalStateException("Playlist ID ${it.id} not found after initial processing") }
                val foundPlaylists = findDeserializedPlaylistsFromIds(it.playlists.toIds(), repository)
                playlistMissingPlaylists.addPlaylists(foundPlaylists)
            }
            repository.runForAll {
                Platform.runLater { observablePlaylistsSet.add(it) }
            }

            activateEvents(CREATE, UPDATE, DELETE)

            subscribe(playlistChangesSubscriber)
        }

        private fun mapAudioItemsFromIds(
            audioItemIds: List<Int>,
            audioLibrary: ObservableAudioLibrary
        ) = audioItemIds.map {
            audioLibrary.findById(it)
                .orElseThrow { AudioItemManipulationException("AudioItem with id $it not found during deserialization") }
        }.toList()

        private fun findDeserializedPlaylistsFromIds(
            playlists: Set<Int>,
            repository: Repository<Int, ObservablePlaylist>
        ): List<ObservablePlaylist> =
            playlists.stream().map {
                repository.findById(it)
                    .orElseThrow { AudioItemManipulationException("AudioPlaylist with id $it not found during deserialization") }
            }.toList()

        override fun createPlaylist(name: String): ObservablePlaylist = createPlaylist(name, emptyList())

        override fun createPlaylist(
            name: String,
            audioItems: List<ObservableAudioItem>
        ): ObservablePlaylist {
            require(findByName(name).isEmpty) { "Playlist with name '$name' already exists" }
            return FXPlaylist(newId(), false, name, audioItems).also {
                logger.debug { "Created playlist $it" }
                add(it)
            }
        }

        override fun createPlaylistDirectory(name: String): ObservablePlaylist = createPlaylistDirectory(name, emptyList())

        override fun createPlaylistDirectory(
            name: String,
            audioItems: List<ObservableAudioItem>
        ): ObservablePlaylist {
            require(findByName(name).isEmpty) { "Playlist with name '$name' already exists" }
            return FXPlaylist(newId(), true, name, audioItems).also {
                logger.debug { "Created playlist directory $it" }
                add(it)
            }
        }

        override fun toString() = "observablePlaylistHierarchy(playlistsCount=${size()})"

        /**
         * JavaFX playlist implementation with bidirectional observable property bindings.
         *
         * Implemented as an inner class to access the enclosing hierarchy's parent tracking.
         * All property modifications execute on the JavaFX Application Thread, and changes to
         * nested playlists or audio items automatically update the recursive audio items property.
         */
        private inner class FXPlaylist(
            id: Int,
            isDirectory: Boolean,
            name: String,
            audioItems: List<ObservableAudioItem> = listOf(),
            playlists: Set<ObservablePlaylist> = setOf()
        ): MutablePlaylistBase(id, isDirectory, name, audioItems, playlists), ObservablePlaylist {
            private val logger = KotlinLogging.logger {}

            private val _nameProperty =
                SimpleStringProperty(this, "name", name).apply {
                    addListener { _, oldValue, newValue ->
                        setAndNotify(newValue, oldValue)
                    }
                }

            override val nameProperty: ReadOnlyStringProperty = _nameProperty

            override var name: String
                get() = nameProperty.get()
                set(value) {
                    Platform.runLater { _nameProperty.set(value) }
                }

            private val _isDirectoryProperty =
                SimpleBooleanProperty(this, "isDirectory", isDirectory).apply {
                    addListener { _, oldValue, newValue ->
                        setAndNotify(newValue, oldValue)
                    }
                }

            override val isDirectoryProperty: ReadOnlyBooleanProperty = _isDirectoryProperty

            override var isDirectory: Boolean
                get() = isDirectoryProperty.get()
                set(value) {
                    Platform.runLater { _isDirectoryProperty.set(value) }
                }

            private val _audioItemsProperty =
                SimpleListProperty(this, "audioItems", FXCollections.observableArrayList(ArrayList(audioItems))).apply {
                    addListener { _, oldValue, newValue ->
                        setAndNotify(newValue, oldValue) {
                            Platform.runLater { replaceRecursiveAudioItems() }
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
                    }
                )
            }

            private fun changePlaylistCover() {
                audioItems.stream()
                    .map { it.coverImageProperty.get() }
                    .filter { it.isPresent }
                    .findAny()
                    .ifPresentOrElse(
                        { Platform.runLater { _coverImageProperty.set(it) } }
                    ) { Platform.runLater { _coverImageProperty.set(Optional.empty()) } }
            }

            override val audioItemsProperty: ReadOnlyListProperty<ObservableAudioItem> = _audioItemsProperty

            override val audioItems: MutableList<ObservableAudioItem>
                get() = audioItemsProperty

            private val _playlistsProperty = SimpleSetProperty(this, "playlists", FXCollections.observableSet(HashSet(playlists)))

            override val playlistsProperty: ReadOnlySetProperty<ObservablePlaylist> = _playlistsProperty

            override val playlists: MutableSet<ObservablePlaylist>
                get() = playlistsProperty

            private val _audioItemsRecursiveProperty =
                SimpleListProperty(
                    this,
                    "audioItemsRecursive",
                    FXCollections.observableArrayList(
                        buildList {
                            addAll(audioItems)
                            addAll(playlists.stream().flatMap { it.audioItemsRecursive.stream() }.toList())
                        }
                    )
                )

            override val audioItemsRecursiveProperty: ReadOnlyListProperty<ObservableAudioItem> = _audioItemsRecursiveProperty

            override val audioItemsRecursive: List<ObservableAudioItem>
                get() = audioItemsRecursiveProperty.get()

            private val _coverImageProperty =
                SimpleObjectProperty(
                    this,
                    "coverImage",
                    this.audioItems.stream().filter {
                        it.coverImageProperty.get().isPresent
                    }.findFirst().map { it.coverImageProperty.get().get() }
                )

            override val coverImageProperty: ReadOnlyObjectProperty<Optional<Image>> = _coverImageProperty

            override fun addAudioItems(audioItems: Collection<ObservableAudioItem>) =
                this.audioItems.stream().anyMatch(audioItems::contains).not().also {
                    Platform.runLater { _audioItemsProperty.addAll(audioItems) }
                    logger.debug { "Added $audioItems to playlist $uniqueId" }
                }

            override fun removeAudioItems(audioItems: Collection<ObservableAudioItem>) =
                this.audioItems.stream().anyMatch(audioItems::contains).also {
                    Platform.runLater { _audioItemsProperty.removeAll(audioItems.toSet()) }
                    logger.debug { "Removed $audioItems from playlist $uniqueId" }
                }

            @Suppress("INAPPLICABLE_JVM_NAME")
            @JvmName("removeAudioItemIds")
            override fun removeAudioItems(audioItemIds: Collection<Int>) =
                this.audioItems.stream().anyMatch { it.id in audioItemIds }.also {
                    Platform.runLater { _audioItemsProperty.removeAll { it.id in audioItemIds } }
                    logger.debug { "Removed audio items with ids $audioItemIds from playlist $uniqueId" }
                }

            override fun addPlaylists(playlists: Collection<ObservablePlaylist>): Boolean {
                playlists.forEach {
                    findParentPlaylist(it).ifPresent { parentPlaylist: ObservablePlaylist ->
                        parentPlaylist.removePlaylist(it)
                        logger.debug { "Playlist '${it.name}' removed from '$parentPlaylist'" }
                    }
                }
                val result = _playlistsProperty.stream().anyMatch(playlists::contains).not()
                setAndNotify(_playlistsProperty + playlists, _playlistsProperty) {
                    _playlistsProperty.addAll(playlists)
                    replaceRecursiveAudioItems()
                    repeat(playlists.size) {
                        putAllPlaylistInHierarchy(uniqueId, playlists)
                    }
                    logger.debug { "Added $playlists to playlist $uniqueId" }
                }
                return result
            }

            override fun removePlaylists(playlists: Collection<ObservablePlaylist>): Boolean {
                val result = _playlistsProperty.stream().anyMatch(playlists::contains)
                setAndNotify(_playlistsProperty - playlists.toSet(), _playlistsProperty) {
                    _playlistsProperty.removeAll(playlists.toSet())
                    playlists.forEach { playlist ->
                        removePlaylistFromHierarchy(uniqueId, playlist)
                    }
                    logger.debug { "Removed $playlists from playlist $uniqueId" }
                }
                return result
            }

            @Suppress("INAPPLICABLE_JVM_NAME")
            @JvmName("removePlaylistIds")
            override fun removePlaylists(playlistIds: Collection<Int>): Boolean {
                val result = _playlistsProperty.stream().anyMatch(playlists::contains)
                val playlistsToRemove = playlistIds.map { findById(it) }.filter { it.isPresent }.map { it.get() }
                setAndNotify(_playlistsProperty - playlistsToRemove.toSet(), _playlistsProperty) {
                    _playlistsProperty.removeAll { playlistIds.contains(it.id) }
                    playlistIds.forEach { playlistId ->
                        findById(playlistId).ifPresent { playlist ->
                            removePlaylistFromHierarchy(uniqueId, playlist)
                        }
                    }
                    logger.debug { "Removed playlists with ids $playlistIds from playlist $uniqueId" }
                }
                return result
            }

            override fun clearAudioItems() {
                Platform.runLater { _audioItemsProperty.clear() }
                logger.debug { "Cleared audio items from playlist $uniqueId" }
            }

            override fun clearPlaylists() {
                if (_playlistsProperty.isNotEmpty()) {
                    val playlistsBeforeClear = _playlistsProperty.toSet()
                    setAndNotify(emptySet(), playlistsBeforeClear) {
                        _playlistsProperty.clear()
                        playlistsBeforeClear.forEach { playlist ->
                            removePlaylistFromHierarchy(uniqueId, playlist)
                        }
                        logger.debug { "Cleared playlists from playlist $uniqueId" }
                    }
                }
            }

            override fun compareTo(other: AudioPlaylist<ObservableAudioItem>): Int =
                if (nameProperty.get() == other.name) {
                    val size = playlists.size + audioItemsRecursive.size
                    val objectSize = other.playlists.size + other.audioItemsRecursive.size
                    size - objectSize
                } else {
                    nameProperty.get().compareTo(other.name)
                }

            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (other == null || javaClass != other.javaClass) return false
                val that = other as FXPlaylist
                return Objects.equal(name, that.name) && Objects.equal(id, that.id)
            }

            override fun hashCode() = Objects.hashCode(name, id)

            override fun clone(): FXPlaylist = FXPlaylist(id, isDirectory, name, audioItems.toList(), playlists.toSet())

            private fun <T> formatCollectionWithIndentation(collection: Collection<T>): String {
                if (collection.isEmpty()) return "[]"
                return collection.joinToString(separator = ",\n\t", prefix = "[\n\t", postfix = "\n]") { item ->
                    item.toString().split("\n").joinToString("\n\t")
                }
            }

            override fun toString(): String {
                val formattedAudioItems = formatCollectionWithIndentation(audioItems)
                val formattedPlaylists = formatCollectionWithIndentation(playlists)
                return "FXPlaylist(id=$id, isDirectory=$isDirectory, name='$name', audioItems=$formattedAudioItems, playlists=$formattedPlaylists)"
            }
        }
    }

val observablePlaylistSerializersModule =
    SerializersModule {
        polymorphic(ObservablePlaylist::class, ObservablePlaylistSerializer)
    }