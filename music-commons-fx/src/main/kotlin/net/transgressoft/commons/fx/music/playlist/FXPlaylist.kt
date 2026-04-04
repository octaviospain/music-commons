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

import net.transgressoft.commons.fx.music.audio.ObservableAudioItem
import net.transgressoft.commons.music.playlist.AudioPlaylist
import net.transgressoft.commons.music.playlist.MutablePlaylistBase
import net.transgressoft.lirp.persistence.mutableAggregateList
import net.transgressoft.lirp.persistence.mutableAggregateSet
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
import javafx.scene.image.Image
import mu.KotlinLogging
import java.util.Optional

/**
 * JavaFX playlist implementation with bidirectional observable property bindings.
 *
 * All property modifications execute on the JavaFX Application Thread, and changes to
 * nested playlists or audio items automatically update the recursive audio items property.
 *
 * Audio item and nested playlist references are tracked as lirp mutable aggregate delegates
 * ([_audioItems], [_playlists]) for ID persistence and lazy entity resolution, while the
 * observable [audioItemsProperty] and [playlistsProperty] serve as the FX-visible counterpart.
 *
 * The manual [FXPlaylist_LirpRefAccessor] registers the aggregate delegates with lirp so
 * that registry binding and mutation event wiring work correctly at runtime.
 */
internal class FXPlaylist(
    id: Int,
    name: String,
    isDirectory: Boolean,
    initialAudioItemIds: List<Int> = emptyList(),
    initialPlaylistIds: Set<Int> = emptySet()
) : MutablePlaylistBase<ObservableAudioItem, ObservablePlaylist>(id, name, isDirectory),
    ObservablePlaylist {

    private val logger = KotlinLogging.logger {}

    // lirp aggregate delegates for ID tracking and serialization.
    // @Aggregate is intentionally omitted — a manual FXPlaylist_LirpRefAccessor is provided
    // because KSP cannot process internal classes.
    override val audioItems by mutableAggregateList<Int, ObservableAudioItem>(initialAudioItemIds)

    override val playlists by mutableAggregateSet<Int, ObservablePlaylist>(initialPlaylistIds)

    /**
     * Populates [_audioItemsProperty] and [_playlistsProperty] from the resolved aggregate
     * delegates. Called after the entity registries are bound so that deserialized playlists
     * loaded from a persistent store reflect live entity references in their JavaFX properties.
     *
     * Updates are applied on the calling thread and also dispatched to the JavaFX Application
     * Thread via [Platform.runLater] to keep FX bindings consistent.
     */
    fun syncObservablePropertiesFromAggregates() {
        // Aggregate proxies may not be bound if the referenced entity registry is missing
        // (e.g. no AudioLibrary registered). Resolve gracefully: unbound proxies yield empty.
        val resolvedItems =
            try {
                audioItems.toList()
            } catch (_: NoSuchElementException) {
                emptyList()
            }
        val resolvedPlaylists =
            try {
                playlists.toList()
            } catch (_: NoSuchElementException) {
                emptyList()
            }
        // Populate synchronously so non-FX-thread callers see the resolved state immediately
        synchronized(_audioItemsProperty) {
            if (_audioItemsProperty.isEmpty() && resolvedItems.isNotEmpty()) {
                _audioItemsProperty.clear()
                _audioItemsProperty.addAll(resolvedItems)
            }
        }
        if (_playlistsProperty.isEmpty() && resolvedPlaylists.isNotEmpty()) {
            _playlistsProperty.addAll(resolvedPlaylists)
        }
        // Also schedule on the FX thread to ensure bound UI components refresh
        Platform.runLater {
            synchronized(_audioItemsProperty) {
                if (_audioItemsProperty.size != resolvedItems.size) {
                    _audioItemsProperty.clear()
                    _audioItemsProperty.addAll(resolvedItems)
                }
            }
            if (_playlistsProperty.size != resolvedPlaylists.size) {
                _playlistsProperty.clear()
                _playlistsProperty.addAll(resolvedPlaylists)
            }
        }
    }

    private val _nameProperty = SimpleStringProperty(this, "name", name)

    override val nameProperty: ReadOnlyStringProperty = _nameProperty

    override var name: String by reactiveProperty({ _nameProperty.get() }, { _nameProperty.set(it) })

    private val _isDirectoryProperty = SimpleBooleanProperty(this, "isDirectory", isDirectory)

    override val isDirectoryProperty: ReadOnlyBooleanProperty = _isDirectoryProperty

    override var isDirectory: Boolean by reactiveProperty({ _isDirectoryProperty.get() }, { _isDirectoryProperty.set(it) })

    private val _audioItemsProperty =
        SimpleListProperty(this, "audioItems", FXCollections.observableArrayList<ObservableAudioItem>()).apply {
            addListener { _, _, _ ->
                replaceRecursiveAudioItems()
                changePlaylistCover()
            }
        }

    private fun replaceRecursiveAudioItems() {
        val currentPlaylists = _playlistsProperty.toList()
        val currentAudioItems = _audioItemsProperty.toList()

        Platform.runLater {
            _audioItemsRecursiveProperty.clear()
            _audioItemsRecursiveProperty.addAll(
                buildList<ObservableAudioItem> {
                    addAll(currentAudioItems)
                    addAll(currentPlaylists.flatMap { it.audioItemsRecursive })
                }
            )
        }
    }

    private fun changePlaylistCover() {
        val newCover =
            _audioItemsProperty.stream()
                .map { it.coverImageProperty.get() }
                .filter { it.isPresent }
                .findAny()

        Platform.runLater {
            if (newCover.isPresent) {
                _coverImageProperty.set(newCover.get())
            } else {
                _coverImageProperty.set(Optional.empty())
            }
        }
    }

    override val audioItemsProperty: ReadOnlyListProperty<ObservableAudioItem> = _audioItemsProperty

    private val _playlistsProperty = SimpleSetProperty(this, "playlists", FXCollections.observableSet<ObservablePlaylist>())

    override val playlistsProperty: ReadOnlySetProperty<ObservablePlaylist> = _playlistsProperty

    private val _audioItemsRecursiveProperty =
        SimpleListProperty(
            this,
            "audioItemsRecursive",
            FXCollections.observableArrayList<ObservableAudioItem>()
        )

    override val audioItemsRecursiveProperty: ReadOnlyListProperty<ObservableAudioItem> = _audioItemsRecursiveProperty

    override val audioItemsRecursive: List<ObservableAudioItem>
        get() = audioItemsRecursiveProperty.get()

    private val _coverImageProperty =
        SimpleObjectProperty(
            this,
            "coverImage",
            Optional.empty<Image>()
        )

    override val coverImageProperty: ReadOnlyObjectProperty<Optional<Image>> = _coverImageProperty

    override fun audioItemsAllMatch(predicate: java.util.function.Predicate<ObservableAudioItem>): Boolean =
        synchronized(_audioItemsProperty) { _audioItemsProperty.toList() }.stream().allMatch { predicate.test(it) }

    override fun audioItemsAnyMatch(predicate: java.util.function.Predicate<ObservableAudioItem>): Boolean =
        synchronized(_audioItemsProperty) { _audioItemsProperty.toList() }.stream().anyMatch { predicate.test(it) }

    override fun addAudioItems(audioItems: Collection<ObservableAudioItem>): Boolean {
        val currentIds: Set<Int> = synchronized(_audioItemsProperty) { _audioItemsProperty.toList().map { it.id }.toSet() }
        val itemsToAdd = audioItems.filter { it.id !in currentIds }
        if (itemsToAdd.isEmpty()) return false
        this.audioItems.addAll(itemsToAdd)
        Platform.runLater {
            synchronized(_audioItemsProperty) {
                _audioItemsProperty.addAll(itemsToAdd)
            }
        }
        logger.debug { "Added $itemsToAdd to playlist $uniqueId" }
        return true
    }

    override fun removeAudioItems(audioItems: Collection<ObservableAudioItem>): Boolean {
        val itemsToRemove = audioItems.toSet()
        val currentItems = synchronized(_audioItemsProperty) { _audioItemsProperty.toSet() }
        val hasItems = itemsToRemove.any { it in currentItems }
        this.audioItems.removeAll(audioItems.toSet())
        Platform.runLater {
            synchronized(_audioItemsProperty) {
                _audioItemsProperty.removeAll(itemsToRemove)
            }
        }
        if (hasItems) {
            logger.debug { "Removed $itemsToRemove from playlist $uniqueId" }
        }
        return hasItems
    }

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("removeAudioItemIds")
    override fun removeAudioItems(audioItemIds: Collection<Int>): Boolean {
        val idsToRemove = audioItemIds.toSet()
        val currentItems = synchronized(_audioItemsProperty) { _audioItemsProperty.toList() }
        val toRemove = currentItems.filter { it.id in idsToRemove }
        if (toRemove.isEmpty())
            return false

        this.audioItems.removeAll(toRemove.toSet())
        Platform.runLater {
            synchronized(_audioItemsProperty) {
                _audioItemsProperty.removeAll { it.id in idsToRemove }
            }
        }
        logger.debug { "Removed audio items with ids $idsToRemove from playlist $uniqueId" }
        return true
    }

    override fun addPlaylists(playlists: Collection<ObservablePlaylist>): Boolean {
        val newPlaylists = playlists.filter { it !in _playlistsProperty }
        if (newPlaylists.isEmpty())
            return false

        this.playlists.addAll(newPlaylists)
        Platform.runLater {
            _playlistsProperty.addAll(newPlaylists)
            replaceRecursiveAudioItems()
        }
        logger.debug { "Added $playlists to playlist $uniqueId" }
        return true
    }

    override fun removePlaylists(playlists: Collection<ObservablePlaylist>): Boolean {
        val containsPlaylists = playlists.any { it in _playlistsProperty }
        if (containsPlaylists) {
            this.playlists.removeAll(playlists.toSet())
            Platform.runLater {
                _playlistsProperty.removeAll(playlists.toSet())
            }
            logger.debug { "Removed $playlists from playlist $uniqueId" }
        }
        return containsPlaylists
    }

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("removePlaylistIds")
    override fun removePlaylists(playlistIds: Collection<Int>): Boolean {
        val idsToRemove = playlistIds.toSet()
        val toRemove = _playlistsProperty.filter { it.id in idsToRemove }
        if (toRemove.isEmpty()) return false
        this.playlists.removeAll(toRemove.toSet())
        Platform.runLater {
            _playlistsProperty.removeAll { it.id in idsToRemove }
        }
        logger.debug { "Removed playlists with ids $playlistIds from playlist $uniqueId" }
        return true
    }

    override fun clearAudioItems() {
        if (synchronized(_audioItemsProperty) { _audioItemsProperty.isNotEmpty() }) {
            val size = synchronized(_audioItemsProperty) { _audioItemsProperty.size }
            this.audioItems.clear()
            Platform.runLater { synchronized(_audioItemsProperty) { _audioItemsProperty.clear() } }
            logger.debug { "Cleared $size audio items from playlist $uniqueId" }
        }
    }

    override fun clearPlaylists() {
        if (_playlistsProperty.isNotEmpty()) {
            val size = _playlistsProperty.size
            this.playlists.clear()
            Platform.runLater {
                _playlistsProperty.clear()
            }
            logger.debug { "Cleared $size playlists from playlist $uniqueId" }
        }
    }

    override fun compareTo(other: AudioPlaylist<ObservableAudioItem>): Int =
        if (nameProperty.get() == other.name) {
            val size = _playlistsProperty.size + audioItemsRecursive.size
            val objectSize = other.playlists.size + other.audioItemsRecursive.size
            size - objectSize
        } else {
            nameProperty.get().compareTo(other.name)
        }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val that = other as FXPlaylist
        val thisAudioItems = synchronized(_audioItemsProperty) { _audioItemsProperty.toList() }
        val thatAudioItems = synchronized(that._audioItemsProperty) { that._audioItemsProperty.toList() }
        return id == that.id &&
            isDirectory == that.isDirectory &&
            name == that.name &&
            thisAudioItems == thatAudioItems &&
            _playlistsProperty == that._playlistsProperty
    }

    override fun hashCode() =
        Objects.hashCode(
            id, isDirectory, name,
            synchronized(_audioItemsProperty) {
                _audioItemsProperty.toList()
            },
            _playlistsProperty.toSet()
        )

    override fun clone(): FXPlaylist = FXPlaylist(id, name, isDirectory, audioItems.referenceIds.toList(), LinkedHashSet(playlists.referenceIds))

    private fun <T> formatCollectionWithIndentation(collection: Collection<T>): String {
        if (collection.isEmpty()) return "[]"
        return collection.joinToString(separator = ",\n\t", prefix = "[\n\t", postfix = "\n]") { item ->
            item.toString().split("\n").joinToString("\n\t")
        }
    }

    override fun toString(): String {
        val items = synchronized(_audioItemsProperty) { _audioItemsProperty.toList() }
        val formattedAudioItems = formatCollectionWithIndentation(items)
        val formattedPlaylists = formatCollectionWithIndentation(_playlistsProperty.toList())
        return "FXPlaylist(id=$id, isDirectory=$isDirectory, name='$name', audioItems=$formattedAudioItems, playlists=$formattedPlaylists)"
    }

    @Suppress("UNCHECKED_CAST")
    override fun asJsonKeyValue(): String {
        val audioItemRefIds = audioItems.referenceIds
        val playlistRefIds = playlists.referenceIds
        val audioItemsString =
            buildString {
                append("[")
                audioItemRefIds.forEachIndexed { index, id ->
                    append(id)
                    if (index < audioItemRefIds.size - 1) append(",")
                }
                append("],")
            }
        val playlistIdsStr =
            buildString {
                append("[")
                playlistRefIds.forEachIndexed { index, id ->
                    append(id)
                    if (index < playlistRefIds.size - 1) append(",")
                }
                append("]")
            }
        return """
            "$id": {
                "id": $id,
                "audioItems": $audioItemsString
                "playlists": $playlistIdsStr,
                "isDirectory": $isDirectory,
                "name": "$name"
            }"""
    }
}