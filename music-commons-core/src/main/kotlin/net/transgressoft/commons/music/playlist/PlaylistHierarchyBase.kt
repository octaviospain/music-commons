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
import net.transgressoft.lirp.entity.IdentifiableEntity
import net.transgressoft.lirp.entity.LirpEntity
import net.transgressoft.lirp.entity.ReactiveEntityBase
import net.transgressoft.lirp.event.CrudEvent
import net.transgressoft.lirp.event.CrudEvent.Type.CREATE
import net.transgressoft.lirp.event.CrudEvent.Type.DELETE
import net.transgressoft.lirp.event.CrudEvent.Type.UPDATE
import net.transgressoft.lirp.event.LirpEventSubscriber
import net.transgressoft.lirp.event.LirpEventSubscription
import net.transgressoft.lirp.persistence.Aggregate
import net.transgressoft.lirp.persistence.RegistryBase
import net.transgressoft.lirp.persistence.Repository
import net.transgressoft.lirp.persistence.aggregateList
import com.google.common.collect.Multimap
import com.google.common.collect.MultimapBuilder
import mu.KotlinLogging
import java.util.*
import java.util.concurrent.Flow
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Consumer
import java.util.function.Predicate
import java.util.stream.Stream
import kotlinx.coroutines.flow.SharedFlow

/**
 * A mutable delegate wrapper for [Repository] that allows the backing repository to be
 * replaced at runtime via [switchTo].
 *
 * Used by [PlaylistHierarchyBase] to support two-phase construction: a volatile repository
 * is set as the initial backing store, then swapped to a JSON-backed repository after
 * deserialization completes. All [Repository] operations are forwarded to [backing].
 */
class RepositoryDelegate<K : Comparable<K>, T : IdentifiableEntity<K>>(
    initial: Repository<K, T>
) : Repository<K, T> {

    internal var backing: Repository<K, T> = initial

    internal fun switchTo(newRepo: Repository<K, T>) {
        backing = newRepo
    }

    override fun add(entity: T) = backing.add(entity)

    override fun remove(entity: T) = backing.remove(entity)

    override fun removeAll(entities: Collection<T>) = backing.removeAll(entities)

    override fun clear() = backing.clear()

    override fun contains(id: K) = backing.contains(id)

    override fun contains(predicate: Predicate<in T>) = backing.contains(predicate)

    override fun lazySearch(predicate: Predicate<in T>): Sequence<T> = backing.lazySearch(predicate)

    override fun searchStream(predicate: Predicate<in T>): Stream<T> = backing.searchStream(predicate)

    override fun search(predicate: Predicate<in T>) = backing.search(predicate)

    override fun search(size: Int, predicate: Predicate<in T>) = backing.search(size, predicate)

    override fun findFirst(predicate: Predicate<in T>) = backing.findFirst(predicate)

    override fun findById(id: K) = backing.findById(id)

    override fun findByUniqueId(uniqueId: String) = backing.findByUniqueId(uniqueId)

    override fun findByIndex(indexName: String, value: Any) = backing.findByIndex(indexName, value)

    override fun findFirstByIndex(indexName: String, value: Any) = backing.findFirstByIndex(indexName, value)

    override fun size() = backing.size()

    override val isEmpty get() = backing.isEmpty

    override fun iterator() = backing.iterator()

    override val changes: SharedFlow<CrudEvent<K, T>> get() = backing.changes
    override val isClosed get() = backing.isClosed
    override val subscriberCount get() = backing.subscriberCount

    override fun emitAsync(event: CrudEvent<K, T>) = backing.emitAsync(event)

    override fun subscribe(action: suspend (CrudEvent<K, T>) -> Unit): LirpEventSubscription<in LirpEntity, CrudEvent.Type, CrudEvent<K, T>> =
        backing.subscribe(action)

    override fun subscribe(action: Consumer<in CrudEvent<K, T>>): LirpEventSubscription<in LirpEntity, CrudEvent.Type, CrudEvent<K, T>> =
        backing.subscribe(action)

    override fun subscribe(vararg eventTypes: CrudEvent.Type, action: suspend (CrudEvent<K, T>) -> Unit):
        LirpEventSubscription<in LirpEntity, CrudEvent.Type, CrudEvent<K, T>> =
        backing.subscribe(*eventTypes, action = action)

    override fun subscribe(p0: Flow.Subscriber<in CrudEvent<K, T>>?) = backing.subscribe(p0)

    override fun activateEvents(vararg types: CrudEvent.Type) = backing.activateEvents(*types)

    override fun disableEvents(vararg types: CrudEvent.Type) = backing.disableEvents(*types)

    override fun close() = backing.close()
}

/**
 * Base implementation for [PlaylistHierarchy] managing hierarchical playlist structures.
 *
 * Maintains a multimap to track parent-child relationships between playlists and provides
 * reactive change notifications for all playlist modifications. Automatically synchronizes
 * with audio item deletion events to remove deleted items from all playlists.
 *
 * Audio item IDs are stored in [MutablePlaylistBase.audioItemIds] for use by concrete subclasses
 * that declare an [@Aggregate][net.transgressoft.lirp.persistence.Aggregate] delegate for lazy resolution.
 */
abstract class PlaylistHierarchyBase<I: ReactiveAudioItem<I>, P: ReactiveAudioPlaylist<I, P>>(
    protected val repositoryDelegate: RepositoryDelegate<Int, P>,
    private val audioItemEventSubscriber: AudioItemEventSubscriber<I> = AudioItemEventSubscriber("PlaylistHierarchySubscriber")
): PlaylistHierarchy<I, P>,
    Repository<Int, P> by repositoryDelegate,
    LirpEventSubscriber<I, CrudEvent.Type, CrudEvent<Int, I>> by audioItemEventSubscriber {

    init {
        repositoryDelegate.disableEvents(CREATE, UPDATE, DELETE)
        activateEvents(CREATE, UPDATE, DELETE)
        addOnNextEventAction(DELETE) { event ->
            forEach {
                it.removeAudioItems(event.entities.keys)
            }
        }
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

    override fun add(entity: P): Boolean = addInternal(entity)

    private fun addInternal(playlist: P): Boolean {
        var added = repositoryDelegate.add(playlist)
        for (p in playlist.playlists) {
            playlistsHierarchyMultiMap.put(playlist.uniqueId, p)
            added = added or addInternal(p)
        }
        return added
    }

    override fun remove(entity: P): Boolean =
        repositoryDelegate.remove(entity).also { removed ->
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
        repositoryDelegate.removeAll(entities).also { removed ->
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

    override fun numberOfPlaylists() = search { it.isDirectory.not() }.count()

    override fun numberOfPlaylistDirectories() = search { it.isDirectory }.count()

    // Looks up the repository registered for [entityClass] in the default LirpContext via reflection,
    // since LirpContext.registryFor() is internal to the lirp module.
    // The method is mangled to registryFor$lirp_core by the Kotlin compiler's internal visibility rules.
    private fun findRegisteredRepositoryFor(entityClass: Class<*>): Any? =
        try {
            val contextCompanion =
                Class.forName("net.transgressoft.lirp.persistence.LirpContext")
                    .getDeclaredField("Companion")
                    .apply { isAccessible = true }
                    .get(null)
            val defaultContextGetter =
                contextCompanion.javaClass
                    .getDeclaredMethod("getDefault")
                    .apply { isAccessible = true }
            val context = defaultContextGetter.invoke(contextCompanion)
            context?.javaClass
                ?.getDeclaredMethod("registryFor\$lirp_core", Class::class.java)
                ?.apply { isAccessible = true }
                ?.invoke(context, entityClass)
        } catch (_: Exception) {
            null
        }

    /**
     * Cancels the audio item event subscription used to synchronize playlists with audio item deletions.
     */
    override fun close() {
        audioItemEventSubscriber.cancelSubscription()
    }

    protected fun putAllPlaylistInHierarchy(parentPlaylistUniqueId: String, playlist: Collection<P>) {
        playlistsHierarchyMultiMap.putAll(parentPlaylistUniqueId, playlist)
    }

    protected fun removePlaylistFromHierarchy(parentPlaylistUniqueId: String, playlist: P) {
        playlistsHierarchyMultiMap.remove(parentPlaylistUniqueId, playlist)
    }

    /**
     * Creates a playlist instance from deserialized properties.
     *
     * Concrete subclasses override this to delegate construction to their inner playlist class,
     * enabling companion-serializer-based deserialization without requiring public constructors.
     */
    protected abstract fun createPlaylistFromProperties(
        id: Int,
        isDirectory: Boolean,
        name: String,
        initialAudioItemIds: List<Int>
    ): P

    /**
     * Registers the current backing store in LirpContext and resolves any deserialized playlist data.
     *
     * This variant uses [repositoryDelegate.backing] as both the LirpContext registration target
     * and the source of playlists to resolve, which is the correct form for use in `init` blocks
     * where the hierarchy is constructed directly with its intended repository.
     */
    protected fun bindInitialRepository(playlistEntityClass: Class<P>, audioItemEntityClass: Class<*>) {
        bindRepository(repositoryDelegate.backing, playlistEntityClass, audioItemEntityClass, switchBacking = false)
    }

    /**
     * Registers [repo] in LirpContext and resolves deserialized audio item IDs and nested playlist IDs
     * into live object references by iterating [repo] directly.
     *
     * Deregisters any previously registered repository for [playlistEntityClass] before registering [repo].
     * If [switchBacking] is true, the [RepositoryDelegate]'s backing is also replaced with [repo] so
     * subsequent operations target [repo] rather than the original backing store.
     */
    protected open fun bindRepository(
        repo: Repository<Int, P>,
        playlistEntityClass: Class<P>,
        audioItemEntityClass: Class<*>,
        switchBacking: Boolean = false
    ) {
        RegistryBase.deregisterRepository(playlistEntityClass)
        RegistryBase.registerRepository(playlistEntityClass, repo)

        disableEvents(CREATE, UPDATE, DELETE)

        val audioItemRepo = findRegisteredRepositoryFor(audioItemEntityClass)
        repo.toList().forEach { playlist ->
            val audioItemIds = getPlaylistAudioItemIds(playlist)
            if (audioItemIds.isNotEmpty() && audioItemRepo != null) {
                @Suppress("UNCHECKED_CAST")
                val typedRepo = audioItemRepo as? Repository<Int, I>
                val resolvedItems =
                    audioItemIds.mapNotNull { id ->
                        typedRepo?.findById(id)?.orElse(null)
                    }
                playlist.addAudioItems(resolvedItems)
            }
        }

        val allPlaylists = repo.toList()
        allPlaylists.forEach { playlist ->
            val nestedPlaylistIds = getPlaylistNestedIds(playlist)
            if (nestedPlaylistIds.isNotEmpty()) {
                val foundPlaylists = nestedPlaylistIds.mapNotNull { id -> repo.findById(id).orElse(null) }
                playlist.addPlaylists(foundPlaylists)
            }
        }

        if (switchBacking) {
            repositoryDelegate.switchTo(repo)
        }

        activateEvents(CREATE, UPDATE, DELETE)
    }

    /**
     * Resolves deserialized playlists from [newRepo], then switches the [RepositoryDelegate]'s
     * backing store to [newRepo] so all subsequent CRUD operations target it.
     *
     * This enables two-phase construction: create the hierarchy with a volatile repository (which
     * sets [DefaultPlaylistHierarchy.Companion.instance] for deserialization), then call this method
     * to bind the loaded persistent repository and make it the active backing store.
     */
    protected fun switchToRepository(
        newRepo: Repository<Int, P>,
        playlistEntityClass: Class<P>,
        audioItemEntityClass: Class<*>
    ) {
        bindRepository(newRepo, playlistEntityClass, audioItemEntityClass, switchBacking = true)
    }

    /**
     * Extracts the audio item IDs from [playlist] for use during post-deserialization resolution.
     */
    protected open fun getPlaylistAudioItemIds(playlist: P): List<Int> {
        val mutableBase = playlist as? PlaylistHierarchyBase<I, P>.MutablePlaylistBase
        return mutableBase?.audioItemIds ?: playlist.audioItems.map { it.id }
    }

    /**
     * Extracts the nested playlist IDs from [playlist] for use during post-deserialization resolution.
     */
    protected open fun getPlaylistNestedIds(playlist: P): Set<Int> = playlist.playlists.map { it.id }.toSet()

    /**
     * Base reactive playlist implementation providing change notification and hierarchy management.
     *
     * Implemented as an inner class to access the enclosing [PlaylistHierarchyBase] instance's
     * hierarchy tracking methods, enabling playlists to update their parent relationships
     * when nested playlists are added or removed without requiring explicit parent references.
     *
     * [audioItemIds] stores referenced audio item IDs for lazy cross-registry resolution via
     * the [@Aggregate][Aggregate] + [aggregateList] delegate that concrete subclasses must declare.
     * Concrete subclasses should expose an aggregate delegate property for ID-based resolution:
     * ```
     * val audioItemsAggregate by aggregateList<Int, ConcreteAudioItem> { audioItemIds }
     * ```
     *
     * Due to a KSP limitation with generic inner classes, the [@Aggregate][Aggregate] annotation
     * and [aggregateList] delegate must be declared in concrete (non-generic) inner subclasses.
     * The `_LirpRefAccessor` for the concrete class must be provided manually.
     */
    protected abstract inner class MutablePlaylistBase(
        override val id: Int,
        isDirectory: Boolean,
        name: String,
        audioItems: List<I> = listOf(),
        playlists: Set<P> = setOf(),
        initialAudioItemIds: List<Int> = audioItems.map { it.id }
    ): ReactiveEntityBase<Int, P>(), ReactiveAudioPlaylist<I, P> {

        private val logger = KotlinLogging.logger {}

        /**
         * Referenced audio item IDs used by the [@Aggregate][Aggregate] + [aggregateList] delegate
         * declared in concrete subclasses for lazy resolution from [net.transgressoft.lirp.persistence.LirpContext].
         */
        var audioItemIds: List<Int> = initialAudioItemIds

        override val audioItems: MutableList<I> = ArrayList(audioItems)
        override val playlists: MutableSet<P> = HashSet(playlists)

        override var isDirectory: Boolean by reactiveProperty(isDirectory)

        override var name: String = name
            set(value) {
                require(!findByName(value).isPresent || findByName(value).get() === this) {
                    "Playlist with name '$value' already exists"
                }
                mutateAndPublish { field = value }
            }

        override fun addAudioItems(audioItems: Collection<I>): Boolean {
            val result = this.audioItems.stream().anyMatch { it !in audioItems }
            mutateAndPublish {
                this.audioItems.addAll(audioItems)
                logger.debug { "Added $audioItems to playlist $uniqueId" }
            }
            return result
        }

        override fun removeAudioItems(audioItems: Collection<I>): Boolean {
            val result = this.audioItems.stream().anyMatch(audioItems::contains)
            mutateAndPublish {
                this.audioItems.removeAll(audioItems)
                logger.debug { "Removed $audioItems from playlist $uniqueId" }
            }
            return result
        }

        @Suppress("INAPPLICABLE_JVM_NAME")
        @JvmName("removeAudioItemIds")
        override fun removeAudioItems(audioItemIds: Collection<Int>): Boolean {
            val result = this.audioItems.stream().anyMatch { it.id in audioItemIds }
            mutateAndPublish {
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
            mutateAndPublish {
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
            mutateAndPublish {
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
                mutateAndPublish {
                    audioItems.clear()
                }
                logger.debug { "Cleared $audioItemsSize audio items from playlist $uniqueId" }
            }
        }

        override fun clearPlaylists() {
            if (playlists.isNotEmpty()) {
                val playlistSize = playlists.size
                mutateAndPublish {
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