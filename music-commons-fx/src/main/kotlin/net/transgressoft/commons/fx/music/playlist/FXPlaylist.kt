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
import net.transgressoft.lirp.persistence.fx.fxAggregateList
import net.transgressoft.lirp.persistence.fx.fxAggregateSet
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
import javafx.collections.ListChangeListener
import javafx.collections.ObservableList
import javafx.collections.ObservableSet
import javafx.collections.SetChangeListener
import javafx.scene.image.Image
import mu.KotlinLogging
import java.util.Optional
import java.util.function.Predicate

/**
 * JavaFX playlist implementation backed by lirp-fx aggregate collection delegates.
 *
 * Audio items and nested playlists are tracked via [fxAggregateList] and [fxAggregateSet]
 * delegates that serve as both the lirp ID-tracking aggregate and the [ObservableList]/[ObservableSet]
 * exposed through [ObservablePlaylist]. The delegates auto-dispatch listener notifications to
 * the JavaFX Application Thread and auto-sync with the lirp registry, eliminating the need
 * for manual [javafx.application.Platform.runLater] calls and separate backing properties.
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

    // @Aggregate is intentionally omitted — a manual FXPlaylist_LirpRefAccessor is provided
    // because KSP cannot process internal classes.
    // Only audioItems/playlists use by-delegation so delegateRegistry discovers them for serialization.
    // The backing aggregates use = assignment to avoid duplicate entries in delegateRegistry.
    internal val audioItemsAggregate = fxAggregateList<Int, ObservableAudioItem>(initialAudioItemIds)

    override val audioItems by audioItemsAggregate

    override val audioItemsProperty: ReadOnlyListProperty<ObservableAudioItem> = SimpleListProperty(this, "audioItems", audioItemsAggregate)

    internal val playlistsAggregate = fxAggregateSet<Int, ObservablePlaylist>(initialPlaylistIds)

    override val playlists by playlistsAggregate

    override val playlistsProperty: ReadOnlySetProperty<ObservablePlaylist> = SimpleSetProperty(this, "playlists", playlistsAggregate)

    private val _nameProperty = SimpleStringProperty(this, "name", name)

    override val nameProperty: ReadOnlyStringProperty = _nameProperty

    override var name: String
        get() = _nameProperty.value
        set(value) {
            mutateAndPublish {
                _nameProperty.set(value)
                value
            }
        }

    private val _isDirectoryProperty = SimpleBooleanProperty(this, "isDirectory", isDirectory)

    override val isDirectoryProperty: ReadOnlyBooleanProperty = _isDirectoryProperty

    override var isDirectory: Boolean
        get() = _isDirectoryProperty.value
        set(value) {
            mutateAndPublish {
                _isDirectoryProperty.set(value)
                value
            }
        }

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

    init {
        audioItemsAggregate.addListener(
            ListChangeListener {
                replaceRecursiveAudioItems()
                changePlaylistCover()
            }
        )
        playlistsAggregate.addListener { _: SetChangeListener.Change<out ObservablePlaylist> ->
            replaceRecursiveAudioItems()
            changePlaylistCover()
        }
        // Best-effort initial computation. Effective only for non-hydrated paths where audio items
        // are already resolvable at construction time. For JSON-hydrated playlists the aggregates
        // bind to their registries after init runs; FXPlaylistHierarchy invokes
        // [triggerCoverHydration] post-load to recompute the cover once aggregates are bound.
        replaceRecursiveAudioItems()
        changePlaylistCover()
    }

    /**
     * Recomputes the recursive audio item view and cover image after the aggregate registries
     * have been bound and synchronized. Intended to be called by [FXPlaylistHierarchy] once
     * JSON-hydrated playlists have completed their lirp registry binding pass — at that point
     * the audio item aggregate can resolve its referenced entities and the cover image can be
     * derived from them. No-op for playlists whose aggregates are already populated.
     */
    internal fun triggerCoverHydration() {
        replaceRecursiveAudioItems()
        changePlaylistCover()
    }

    private fun replaceRecursiveAudioItems() {
        val currentAudioItems = audioItemsAggregate.toList()
        val currentPlaylists = playlistsAggregate.toList()
        _audioItemsRecursiveProperty.clear()
        _audioItemsRecursiveProperty.addAll(
            buildList<ObservableAudioItem> {
                addAll(currentAudioItems)
                addAll(currentPlaylists.flatMap { it.audioItemsRecursive })
            }
        )
    }

    private fun changePlaylistCover() {
        val newCover =
            audioItemsAggregate.stream()
                .map { it.coverImageProperty.get() }
                .filter { it.isPresent }
                .findFirst()

        if (newCover.isPresent) {
            _coverImageProperty.set(newCover.get())
        } else {
            _coverImageProperty.set(Optional.empty())
        }
    }

    override fun audioItemsAllMatch(predicate: Predicate<ObservableAudioItem>): Boolean =
        audioItemsAggregate.toList().stream().allMatch { predicate.test(it) }

    override fun audioItemsAnyMatch(predicate: Predicate<ObservableAudioItem>): Boolean =
        audioItemsAggregate.toList().stream().anyMatch { predicate.test(it) }

    override fun addAudioItems(audioItems: Collection<ObservableAudioItem>): Boolean {
        val currentIds = audioItemsAggregate.map { it.id }.toSet()
        val itemsToAdd = audioItems.filter { it.id !in currentIds }
        if (itemsToAdd.isEmpty()) return false
        audioItemsAggregate.addAll(itemsToAdd)
        logger.debug { "Added $itemsToAdd to playlist $uniqueId" }
        return true
    }

    override fun removeAudioItems(audioItems: Collection<ObservableAudioItem>): Boolean {
        val hasItems = audioItems.any { it in audioItemsAggregate }
        if (hasItems) {
            audioItemsAggregate.removeAll(audioItems.toSet())
            logger.debug { "Removed $audioItems from playlist $uniqueId" }
        }
        return hasItems
    }

    // @JvmName required on generic interface methods to avoid JVM signature clashes with Java callers
    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("removeAudioItemIds")
    override fun removeAudioItems(audioItemIds: Collection<Int>): Boolean {
        val idsToRemove = audioItemIds.toSet()
        val toRemove = audioItemsAggregate.filter { it.id in idsToRemove }
        if (toRemove.isEmpty()) return false
        audioItemsAggregate.removeAll(toRemove.toSet())
        logger.debug { "Removed audio items with ids $idsToRemove from playlist $uniqueId" }
        return true
    }

    override fun addPlaylists(playlists: Collection<ObservablePlaylist>): Boolean {
        val newPlaylists = playlists.filter { it !in playlistsAggregate }
        if (newPlaylists.isEmpty()) return false
        playlistsAggregate.addAll(newPlaylists)
        logger.debug { "Added $playlists to playlist $uniqueId" }
        return true
    }

    override fun removePlaylists(playlists: Collection<ObservablePlaylist>): Boolean {
        val containsPlaylists = playlists.any { it in playlistsAggregate }
        if (containsPlaylists) {
            playlistsAggregate.removeAll(playlists.toSet())
            logger.debug { "Removed $playlists from playlist $uniqueId" }
        }
        return containsPlaylists
    }

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("removePlaylistIds")
    override fun removePlaylists(playlistIds: Collection<Int>): Boolean {
        val idsToRemove = playlistIds.toSet()
        val toRemove = playlistsAggregate.filter { it.id in idsToRemove }
        if (toRemove.isEmpty()) return false
        playlistsAggregate.removeAll(toRemove.toSet())
        logger.debug { "Removed playlists with ids $playlistIds from playlist $uniqueId" }
        return true
    }

    override fun clearAudioItems() {
        if (audioItemsAggregate.isNotEmpty()) {
            val size = audioItemsAggregate.size
            audioItemsAggregate.clear()
            logger.debug { "Cleared $size audio items from playlist $uniqueId" }
        }
    }

    override fun clearPlaylists() {
        if (playlistsAggregate.isNotEmpty()) {
            val size = playlistsAggregate.size
            playlistsAggregate.clear()
            logger.debug { "Cleared $size playlists from playlist $uniqueId" }
        }
    }

    override fun compareTo(other: AudioPlaylist<ObservableAudioItem>): Int =
        if (nameProperty.get() == other.name) {
            val size = playlistsAggregate.size + audioItemsRecursive.size
            val objectSize = other.playlists.size + other.audioItemsRecursive.size
            size - objectSize
        } else {
            nameProperty.get().compareTo(other.name)
        }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val that = other as FXPlaylist
        return id == that.id &&
            isDirectory == that.isDirectory &&
            name == that.name &&
            audioItemsAggregate.toList() == that.audioItemsAggregate.toList() &&
            playlistsAggregate == that.playlistsAggregate
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + isDirectory.hashCode()
        result = 31 * result + name.hashCode()
        return result
    }

    override fun clone(): FXPlaylist =
        FXPlaylist(
            id, name, isDirectory,
            audioItemsAggregate.referenceIds.toList(),
            LinkedHashSet(playlistsAggregate.referenceIds)
        )

    private fun <T> formatCollectionWithIndentation(collection: Collection<T>): String {
        if (collection.isEmpty()) return "[]"
        return collection.joinToString(separator = ",\n\t", prefix = "[\n\t", postfix = "\n]") { item ->
            item.toString().split("\n").joinToString("\n\t")
        }
    }

    override fun toString(): String {
        val formattedAudioItems = formatCollectionWithIndentation(audioItemsAggregate.toList())
        val formattedPlaylists = formatCollectionWithIndentation(playlistsAggregate.toList())
        return "FXPlaylist(id=$id, isDirectory=$isDirectory, name='$name', audioItems=$formattedAudioItems, playlists=$formattedPlaylists)"
    }

    // Safe cast: generic type erased at runtime but guaranteed by the builder/serializer contract
    @Suppress("UNCHECKED_CAST")
    override fun asJsonKeyValue(): String {
        val audioItemRefIds = audioItemsAggregate.referenceIds
        val playlistRefIds = playlistsAggregate.referenceIds
        val audioItemsString =
            buildString {
                append("[")
                audioItemRefIds.forEachIndexed { index, id ->
                    append(id)
                    if (index < audioItemRefIds.size - 1) append(",")
                }
                append("]")
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
        val escapedName = kotlinx.serialization.json.JsonPrimitive(name)
        return """
            "$id": {
                "id": $id,
                "audioItems": $audioItemsString,
                "playlists": $playlistIdsStr,
                "isDirectory": $isDirectory,
                "name": $escapedName
            }"""
    }
}