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

package net.transgressoft.commons.music.playlist

import net.transgressoft.commons.entity.ReactiveEntityBase
import net.transgressoft.commons.event.CrudEvent
import net.transgressoft.commons.event.CrudEvent.Type.CREATE
import net.transgressoft.commons.event.CrudEvent.Type.DELETE
import net.transgressoft.commons.event.CrudEvent.Type.UPDATE
import net.transgressoft.commons.event.StandardCrudEvent.Create
import net.transgressoft.commons.event.StandardCrudEvent.Update
import net.transgressoft.commons.event.TransEventSubscriber
import net.transgressoft.commons.music.audio.ReactiveAudioItem
import net.transgressoft.commons.music.audio.event.AudioItemEventSubscriber
import net.transgressoft.commons.persistence.Repository
import net.transgressoft.commons.persistence.VolatileRepository
import mu.KotlinLogging
import org.jetbrains.kotlin.com.google.common.collect.Multimap
import org.jetbrains.kotlin.com.google.common.collect.MultimapBuilder
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.stream.Collectors.partitioningBy
import kotlin.properties.Delegates.observable

/**
 * Base implementation for [PlaylistHierarchy] managing hierarchical playlist structures.
 *
 * Maintains a multimap to track parent-child relationships between playlists and provides
 * reactive change notifications for all playlist modifications. Automatically synchronizes
 * with audio item deletion events to remove deleted items from all playlists.
 */
abstract class PlaylistHierarchyBase<I: ReactiveAudioItem<I>, P: ReactiveAudioPlaylist<I, P>>(
    protected val repository: Repository<Int, P> = VolatileRepository("PlaylistHierarchy")
): PlaylistHierarchy<I, P>, Repository<Int, P> by repository {

    init {
        repository.disableEvents(CREATE, UPDATE, DELETE)
        activateEvents(CREATE, UPDATE, DELETE)
    }

    private val logger = KotlinLogging.logger {}

    private val playlistsHierarchyMultiMap: Multimap<String, P> = MultimapBuilder.treeKeys().treeSetValues().build()

    private val idCounter: AtomicInteger = AtomicInteger(1)

    protected fun newId(): Int {
        var id: Int
        do {
            id = idCounter.getAndIncrement()
        } while (contains(id))
        return id
    }

    override val audioItemEventSubscriber: TransEventSubscriber<I, CrudEvent.Type, CrudEvent<Int, I>> =
        AudioItemEventSubscriber<I>(this.toString()).apply {
            addOnNextEventAction(DELETE) { event ->
                runForAll {
                    it.removeAudioItems(event.entities.keys)
                }
            }
        }

    override fun add(entity: P): Boolean = addInternal(entity)

    private fun addInternal(playlist: P): Boolean {
        var added = repository.add(playlist)
        for (p in playlist.playlists) {
            playlistsHierarchyMultiMap.put(playlist.uniqueId, p)
            added = added or addInternal(p)
        }
        return added
    }

    override fun addOrReplace(entity: P) = addOrReplaceAll(setOf(entity))

    override fun addOrReplaceAll(entities: Set<P>): Boolean {
        val entitiesBeforeUpdate = mutableListOf<P>()

        val addedAndReplaced =
            entities.stream().filter { it != null && repository.contains(it.id) }.collect(
                partitioningBy { entity ->
                    val entityBefore =
                        repository.findById(entity.id).apply {
                            ifPresent {
                                entitiesBeforeUpdate.add(it)
                            }
                        }
                    repository.addOrReplace(entity)
                    return@partitioningBy entityBefore.isPresent
                }
            )

        addedAndReplaced[true]?.let {
            if (it.isNotEmpty()) {
                repository.emitAsync(Create(it))
                logger.debug { "${it.size} entities were added: $it" }
            }
        }
        addedAndReplaced[false]?.let {
            if (it.isNotEmpty()) {
                repository.emitAsync(Update(it, entitiesBeforeUpdate))
                logger.debug { "${it.size} entities were replaced: $it" }
            }
        }

        return addedAndReplaced.values.stream().flatMap { it.stream() }.findAny().isPresent
    }

    override fun remove(entity: P): Boolean =
        repository.remove(entity).also { removed ->
            if (removed) {
                removeFromPlaylistsHierarchy(entity)
            }
        }

    private fun removeFromPlaylistsHierarchy(playlist: P) {
        playlistsHierarchyMultiMap.removeAll(playlist.uniqueId)
        findParentPlaylist(playlist).ifPresent { parentPlaylist ->
            parentPlaylist.removePlaylist(playlist)
            playlistsHierarchyMultiMap.remove(parentPlaylist, playlist)
        }
        removeAll(playlist.playlists)
    }

    override fun removeAll(entities: Collection<P>): Boolean =
        repository.removeAll(entities).also { removed ->
            if (removed) {
                entities.forEach(::removeFromPlaylistsHierarchy)
            }
        }

    override fun findByName(name: String): Optional<out P> = findFirst { it.name == name }

    override fun findParentPlaylist(playlist: ReactiveAudioPlaylist<I, P>): Optional<P> =
        if (playlistsHierarchyMultiMap.containsValue(playlist)) {
            playlistsHierarchyMultiMap.entries().stream().filter { playlist == it.value }.map { findByUniqueId(it.key).get() }.findFirst()
        } else {
            Optional.empty()
        }

    override fun movePlaylist(playlistNameToMove: String, destinationPlaylistName: String) {
        val playlistToMove = findByName(playlistNameToMove)
        val destinationPlaylist = findByName(destinationPlaylistName)

        require(playlistToMove.isPresent) { "Playlist '$playlistNameToMove' does not exist" }
        require(destinationPlaylist.isPresent) { "Playlist '$destinationPlaylistName' does not exist" }

        findParentPlaylist(playlistToMove.get()).ifPresent { parentPlaylist: P ->
            parentPlaylist.removePlaylist(playlistToMove.get())
            logger.debug { "Playlist '$playlistNameToMove' removed from '$parentPlaylist'" }
        }

        destinationPlaylist.get().addPlaylist(playlistToMove.get())
        logger.debug { "Playlist '$playlistNameToMove' moved to '$destinationPlaylistName'" }
    }

    override fun addAudioItemsToPlaylist(audioItems: Collection<I>, playlistName: String): Boolean =
        findByName(playlistName).let {
            require(it.isPresent) { "Playlist '$playlistName' does not exist" }
            it.get().addAudioItems(audioItems)
        }

    override fun removeAudioItemsFromPlaylist(audioItems: Collection<I>, playlistName: String): Boolean =
        findByName(playlistName).let {
            require(it.isPresent) { "Playlist '$playlistName' does not exist" }
            it.get().removeAudioItems(audioItems)
        }

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("removeAudioItemIdsFromPlaylist")
    override fun removeAudioItemsFromPlaylist(audioItemIds: Collection<Int>, playlistName: String): Boolean =
        findByName(playlistName).let {
            require(it.isPresent) { "Playlist '$playlistName' does not exist" }
            it.get().removeAudioItems(audioItemIds)
        }

    override fun addPlaylistsToDirectory(playlistsToAdd: Set<P>, directoryName: String): Boolean =
        findByName(directoryName).let {
            require(it.isPresent) { "Directory '$directoryName' does not exist" }
            require(it.get().isDirectory) { "Playlist '$directoryName' is not a directory" }
            it.get().addPlaylists(playlistsToAdd).also { added ->
                if (added) {
                    playlistsHierarchyMultiMap.putAll(
                        it.get().uniqueId, playlistsToAdd
                    )
                }
            }
        }

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("addPlaylistNamesToDirectory")
    override fun addPlaylistsToDirectory(playlistNamesToAdd: Set<String>, directoryName: String): Boolean =
        findByName(directoryName).let {
            require(it.isPresent) { "Directory '$directoryName' does not exist" }
            require(it.get().isDirectory) { "Playlist '$directoryName' is not a directory" }
            playlistNamesToAdd.stream().map { playlistName ->
                findByName(playlistName)
                    .orElseThrow { IllegalArgumentException("Playlist '$playlistName' does not exist") }
            }.toList().let { playlistsToAdd ->
                it.get().addPlaylists(playlistsToAdd).also { added ->
                    if (added) {
                        playlistsHierarchyMultiMap.putAll(
                            it.get().uniqueId, playlistsToAdd
                        )
                    }
                }
            }
        }

    override fun removePlaylistsFromDirectory(playlistsToRemove: Set<P>, directoryName: String): Boolean =
        findByName(directoryName).let {
            require(it.isPresent) { "Directory '$directoryName' does not exist" }
            it.get().removePlaylists(playlistsToRemove).also { removed ->
                if (removed) {
                    removeAll(playlistsToRemove)
                    playlistsToRemove.forEach { playlist -> playlistsHierarchyMultiMap.remove(it.get().uniqueId, playlist) }
                }
            }
        }

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("removePlaylistNamesFromDirectory")
    override fun removePlaylistsFromDirectory(playlistsNamesToRemove: Set<String>, directoryName: String): Boolean =
        findByName(directoryName).let {
            require(it.isPresent) { "Directory '$directoryName' does not exist" }
            playlistsNamesToRemove.stream().map { playlistName ->
                findByName(playlistName)
                    .orElseThrow { IllegalArgumentException("Playlist '$playlistName' does not exist") }
            }.toList().let { playlistsToRemove ->
                it.get().removePlaylists(playlistsToRemove).also { removed ->
                    if (removed) {
                        removeAll(playlistsToRemove.toSet())
                        playlistsToRemove.forEach { playlist -> playlistsHierarchyMultiMap.remove(it.get().uniqueId, playlist) }
                    }
                }
            }
        }

    override fun numberOfPlaylists() = repository.search { it.isDirectory.not() }.count()

    override fun numberOfPlaylistDirectories() = repository.search { it.isDirectory }.count()

    protected fun putAllPlaylistInHierarchy(parentPlaylistUniqueId: String, playlist: Collection<P>) {
        playlistsHierarchyMultiMap.putAll(parentPlaylistUniqueId, playlist)
    }

    protected fun removePlaylistFromHierarchy(parentPlaylistUniqueId: String, playlist: P) {
        playlistsHierarchyMultiMap.remove(parentPlaylistUniqueId, playlist)
    }

    /**
     * Base reactive playlist implementation providing change notification and hierarchy management.
     *
     * Implemented as an inner class to access the enclosing [PlaylistHierarchyBase] instance's
     * hierarchy tracking methods, enabling playlists to update their parent relationships
     * when nested playlists are added or removed without requiring explicit parent references.
     */
    protected abstract inner class MutablePlaylistBase(
        override val id: Int,
        isDirectory: Boolean,
        name: String,
        audioItems: List<I> = listOf(),
        playlists: Set<P> = setOf()
    ): ReactiveEntityBase<Int, P>(), ReactiveAudioPlaylist<I, P> {

        private val logger = KotlinLogging.logger {}

        override val audioItems: MutableList<I> = ArrayList(audioItems)
        override val playlists: MutableSet<P> = HashSet(playlists)

        override var isDirectory: Boolean by observable(isDirectory) { _, oldValue, newValue ->
            if (newValue != oldValue) {
                setAndNotify(newValue, oldValue)
                logger.trace { "Playlist $uniqueId changed isDirectory from $oldValue to $newValue" }
            }
        }

        override var name: String by observable(name) { _, oldValue, newValue ->
            require(findByName(newValue).isPresent) { "Playlist with name '$newValue' already exists" }
            if (newValue != oldValue) {
                setAndNotify(newValue, oldValue)
                logger.trace { "Playlist $uniqueId changed name from $oldValue to $newValue" }
            }
        }

        override fun addAudioItems(audioItems: Collection<I>): Boolean {
            val result = this.audioItems.stream().anyMatch { it !in audioItems }
            setAndNotify(this.audioItems + audioItems, this.audioItems) {
                this.audioItems.addAll(audioItems)
                logger.debug { "Added $audioItems to playlist $uniqueId" }
            }
            return result
        }

        override fun removeAudioItems(audioItems: Collection<I>): Boolean {
            val result = this.audioItems.stream().anyMatch(audioItems::contains)
            setAndNotify(this.audioItems - audioItems.toSet(), this.audioItems) {
                this.audioItems.removeAll(audioItems)
                logger.debug { "Removed $audioItems from playlist $uniqueId" }
            }
            return result
        }

        @Suppress("INAPPLICABLE_JVM_NAME")
        @JvmName("removeAudioItemIds")
        override fun removeAudioItems(audioItemIds: Collection<Int>): Boolean {
            val result = this.audioItems.stream().anyMatch { it.id in audioItemIds }
            setAndNotify(this.audioItems - audioItems.toSet(), this.audioItems) {
                this.audioItems.removeAll { it.id in audioItemIds }
                logger.debug { "Removed audio items with ids $audioItemIds from playlist $uniqueId" }
            }
            return result
        }

        override fun addPlaylists(playlists: Collection<P>): Boolean {
            playlists.forEach {
                findParentPlaylist(it).ifPresent { parentPlaylist: ReactiveAudioPlaylist<I, P> ->
                    parentPlaylist.removePlaylist(it)
                    logger.debug { "Playlist '${it.name}' removed from '$parentPlaylist'" }
                }
            }
            val result = this.playlists.stream().anyMatch(playlists::contains).not()
            setAndNotify(this.playlists + playlists, this.playlists) {
                this.playlists.addAll(playlists).also {
                    if (it) {
                        putAllPlaylistInHierarchy(uniqueId, playlists)
                        logger.debug { "Added $playlists to playlist $uniqueId" }
                    }
                }
            }
            return result
        }

        override fun removePlaylists(playlists: Collection<P>): Boolean {
            val result = this.playlists.stream().anyMatch(playlists::contains)
            setAndNotify(this.playlists - playlists.toSet(), this.playlists) {
                this.playlists.removeAll(playlists.toSet()).also {
                    if (it) {
                        playlists.forEach { playlist ->
                            removePlaylistFromHierarchy(uniqueId, playlist)
                        }
                        logger.debug { "Removed $playlists from playlist $uniqueId" }
                    }
                }
            }
            return result
        }

        @Suppress("INAPPLICABLE_JVM_NAME")
        @JvmName("removePlaylistIds")
        override fun removePlaylists(playlistIds: Collection<Int>): Boolean {
            val result = this.playlists.stream().anyMatch { it.id in playlistIds }
            this.playlists.removeAll { playlist -> playlist.id in playlistIds }.also {
                if (it) {
                    playlistIds.forEach { playlistId ->
                        findById(playlistId).ifPresent { playlist ->
                            removePlaylistFromHierarchy(uniqueId, playlist)
                        }
                    }
                    logger.debug { "Removed playlists with ids $playlistIds from playlist $uniqueId" }
                }
            }
            return result
        }

        override fun clearAudioItems() {
            if (audioItems.isNotEmpty()) {
                val audioItemsSize = audioItems.size
                setAndNotify(emptyList(), audioItems) {
                    audioItems.clear()
                }
                logger.debug { "Cleared $audioItemsSize audio items from playlist $uniqueId" }
            }
        }

        override fun clearPlaylists() {
            if (playlists.isNotEmpty()) {
                val playlistSize = playlists.size
                setAndNotify(emptyList(), playlists) {
                    playlists.clear()
                }
                logger.debug { "Cleared $playlistSize playlists from playlist $uniqueId" }
            }
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as PlaylistHierarchyBase<*, *>.MutablePlaylistBase

            if (isDirectory != other.isDirectory) return false
            if (name != other.name) return false
            if (audioItems != other.audioItems) return false
            if (playlists != other.playlists) return false

            return true
        }

        override fun hashCode(): Int {
            var result = isDirectory.hashCode()
            result = 31 * result + name.hashCode()
            result = 31 * result + audioItems.hashCode()
            result = 31 * result + playlists.hashCode()
            return result
        }

        private fun <T> formatCollectionWithIndentation(collection: Collection<T>): String {
            if (collection.isEmpty()) return "[]"
            return collection.joinToString(separator = ",\n\t", prefix = "[\n\t", postfix = "\n]") { item ->
                item.toString().split("\n").joinToString("\n\t")
            }
        }

        override fun toString(): String {
            val formattedAudioItems = formatCollectionWithIndentation(audioItems)
            val formattedPlaylists = formatCollectionWithIndentation(playlists)
            return "MutablePlaylist(id=$id, isDirectory=$isDirectory, name='$name', audioItems=$formattedAudioItems, playlists=$formattedPlaylists)"
        }
    }
}