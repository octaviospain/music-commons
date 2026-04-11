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

import net.transgressoft.commons.music.audio.ReactiveAudioItem
import net.transgressoft.commons.music.audio.event.AudioItemEventSubscriber
import net.transgressoft.lirp.entity.subscribeToCollectionChanges
import net.transgressoft.lirp.event.CrudEvent
import net.transgressoft.lirp.event.CrudEvent.Type.CREATE
import net.transgressoft.lirp.event.CrudEvent.Type.DELETE
import net.transgressoft.lirp.event.CrudEvent.Type.UPDATE
import net.transgressoft.lirp.event.LirpEventSubscriber
import net.transgressoft.lirp.event.LirpEventSubscription
import net.transgressoft.lirp.persistence.Repository
import com.google.common.collect.Multimap
import com.google.common.collect.MultimapBuilder
import mu.KotlinLogging
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.reflect.KClass

/**
 * Base implementation for [ReactivePlaylistHierarchy] managing hierarchical playlist structures.
 *
 * Accepts a plain [Repository] as its backing store.
 * Maintains a multimap to track parent-child relationships between playlists and provides
 * reactive change notifications for all playlist modifications. Automatically synchronizes
 * with audio item deletion events to remove deleted items from all playlists.
 *
 * Subscribes to mutation events on each playlist to keep [playlistsHierarchyMultiMap] in sync
 * when nested playlists are added or removed directly on a playlist instance.
 */
abstract class PlaylistHierarchyBase<I : ReactiveAudioItem<I>, P : ReactiveAudioPlaylist<I, P>>(
    private val repository: Repository<Int, P>,
    private val audioItemEventSubscriber: AudioItemEventSubscriber<I> = AudioItemEventSubscriber("PlaylistHierarchySubscriber")
) : ReactivePlaylistHierarchy<I, P>,
    Repository<Int, P> by repository,
    LirpEventSubscriber<I, CrudEvent.Type, CrudEvent<Int, I>> by audioItemEventSubscriber {

    /**
     * The runtime type of playlist elements in this hierarchy, used for typed collection change subscriptions.
     */
    protected abstract val playlistElementType: KClass<P>

    init {
        repository.disableEvents(CREATE, UPDATE, DELETE)
        activateEvents(CREATE, UPDATE, DELETE)
        addOnNextEventAction(DELETE) { event ->
            forEach {
                it.removeAudioItems(event.entities.values)
            }
        }
    }

    private val logger = KotlinLogging.logger {}

    private val playlistsHierarchyMultiMap: Multimap<String, P> = MultimapBuilder.treeKeys().treeSetValues().build()

    // Tracks per-playlist mutation subscriptions so they can be cancelled on remove/close.
    private val playlistMutationSubscriptions = ConcurrentHashMap<Int, LirpEventSubscription<*, *, *>>()

    private val idCounter: AtomicInteger = AtomicInteger(1)

    protected fun newId(): Int {
        var id: Int
        do {
            id = idCounter.getAndIncrement()
        } while (contains(id))
        return id
    }

    override fun add(entity: P): Boolean {
        require(findByName(entity.name).isEmpty || findByName(entity.name).get() === entity) {
            "Playlist with name '${entity.name}' already exists"
        }
        return addInternal(entity)
    }

    private fun addInternal(playlist: P): Boolean {
        var added = repository.add(playlist)
        for (p in playlist.playlists) {
            playlistsHierarchyMultiMap.put(playlist.uniqueId, p)
            added = added or addInternal(p)
        }
        subscribeToPlaylistMutations(playlist)
        return added
    }

    /**
     * Subscribes to collection change events on [playlist] to keep [playlistsHierarchyMultiMap] in sync
     * when nested playlists are added or removed directly on the playlist instance.
     */
    private fun subscribeToPlaylistMutations(playlist: P) {
        if (playlistMutationSubscriptions.containsKey(playlist.id)) return
        val subscription =
            playlist.subscribeToCollectionChanges(playlistElementType, "playlists") { event ->
                for (added in event.added) {
                    findParentPlaylist(added).ifPresent { oldParent ->
                        if (oldParent.id != playlist.id) {
                            playlistsHierarchyMultiMap.remove(oldParent.uniqueId, added)
                            oldParent.removePlaylists(listOf(added))
                        }
                    }
                    playlistsHierarchyMultiMap.put(playlist.uniqueId, added)
                }
                for (removed in event.removed) {
                    playlistsHierarchyMultiMap.remove(playlist.uniqueId, removed)
                }
            }
        playlistMutationSubscriptions[playlist.id] = subscription
    }

    override fun remove(entity: P): Boolean =
        repository.remove(entity).also { removed ->
            if (removed) {
                cancelPlaylistMutationSubscription(entity.id)
                removeFromPlaylistsHierarchy(entity)
            }
        }

    private fun cancelPlaylistMutationSubscription(playlistId: Int) {
        playlistMutationSubscriptions.remove(playlistId)?.cancel()
    }

    private fun removeFromPlaylistsHierarchy(playlist: P) {
        findParentPlaylist(playlist).ifPresent { parentPlaylist ->
            parentPlaylist.removePlaylist(playlist)
            playlistsHierarchyMultiMap.remove(parentPlaylist, playlist)
        }
        playlistsHierarchyMultiMap.removeAll(playlist.uniqueId)
        // Use referenceIds to collect nested playlist IDs without iterating the proxy.
        // The proxy may no longer resolve after the parent entity was removed from the repo.
        val nestedRefIds =
            (playlist.playlists as? net.transgressoft.lirp.persistence.AggregateCollectionRef<*, *>)
                ?.referenceIds?.mapNotNull { findById(it as Int).orElse(null) } ?: emptyList()
        if (nestedRefIds.isNotEmpty()) {
            playlist.clearPlaylists()
            removeAll(nestedRefIds)
        }
    }

    override fun removeAll(entities: Collection<P>): Boolean {
        // Materialize before removal: aggregate proxies resolve lazily from the registry,
        // so iterating them after repository.removeAll() would throw NoSuchElementException.
        val materialized = entities.toList()
        return repository.removeAll(materialized).also { removed ->
            if (removed) {
                materialized.forEach {
                    cancelPlaylistMutationSubscription(it.id)
                    removeFromPlaylistsHierarchy(it)
                }
            }
        }
    }

    override fun findByName(name: String): Optional<out P> = findFirst { it.name == name }

    override fun findParentPlaylist(playlist: ReactiveAudioPlaylist<I, P>): Optional<P> =
        if (playlistsHierarchyMultiMap.containsValue(playlist)) {
            playlistsHierarchyMultiMap.entries().stream()
                .filter { playlist == it.value }
                .map { findByUniqueId(it.key).get() }
                .findFirst()
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
                    playlistsHierarchyMultiMap.putAll(it.get().uniqueId, playlistsToAdd)
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
                        playlistsHierarchyMultiMap.putAll(it.get().uniqueId, playlistsToAdd)
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
                    playlistsToRemove.forEach { playlist ->
                        playlistsHierarchyMultiMap.remove(it.get().uniqueId, playlist)
                    }
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
                        playlistsToRemove.forEach { playlist ->
                            playlistsHierarchyMultiMap.remove(it.get().uniqueId, playlist)
                        }
                    }
                }
            }
        }

    override fun numberOfPlaylists() = search { it.isDirectory.not() }.count()

    override fun numberOfPlaylistDirectories() = search { it.isDirectory }.count()

    /**
     * Cancels the audio item event subscription and all per-playlist mutation subscriptions.
     */
    override fun close() {
        audioItemEventSubscriber.cancelSubscription()
        playlistMutationSubscriptions.values.forEach { it.cancel() }
        playlistMutationSubscriptions.clear()
    }
}