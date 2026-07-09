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
import net.transgressoft.commons.util.SortedMultimap
import net.transgressoft.lirp.entity.subscribeToCollectionChanges
import net.transgressoft.lirp.event.CrudEvent
import net.transgressoft.lirp.event.CrudEvent.Type.CREATE
import net.transgressoft.lirp.event.CrudEvent.Type.DELETE
import net.transgressoft.lirp.event.CrudEvent.Type.UPDATE
import net.transgressoft.lirp.event.LirpEventSubscriber
import net.transgressoft.lirp.event.LirpEventSubscription
import net.transgressoft.lirp.persistence.Repository
import mu.KotlinLogging
import mu.withLoggingContext
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.reflect.KClass

private const val MAX_ID_SEARCH_ATTEMPTS = 10_000

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
 *
 * **Subscription requirement:** this hierarchy must be subscribed to an audio library's event
 * publisher before any mutating operation is performed. The audio item delete subscriber keeps
 * playlists in sync when items are removed from the library — without it, deleted items remain
 * silently in playlists. Use the library builder, which wires the subscription automatically.
 * Calling a mutating operation before the subscription is established throws [IllegalStateException].
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

    /**
     * Tracks whether this hierarchy has been closed. Once set to `true`, mutations and queries throw
     * [IllegalStateException]. Protected so subtypes can read and transition it while external callers
     * cannot reopen a closed hierarchy.
     */
    protected val closed = AtomicBoolean(false)

    /**
     * Tracks whether this hierarchy's audio item delete subscriber has been wired.
     * Set to `true` inside the subscriber's own [onSubscribe] hook when the builder calls
     * `audioLibrary.subscribe(hierarchy)`. Mutating operations check this flag to detect
     * hierarchies that were constructed outside the builder and therefore lack the delete-sync subscriber.
     *
     * Subclasses may call [markDeleteSubscriberWired] to clear this guard in controlled testing
     * scenarios where the subscriber wiring is handled differently.
     */
    private val subscriptionEstablished = AtomicBoolean(false)

    /**
     * Asserts that this playlist hierarchy has not been closed.
     *
     * @throws IllegalStateException if [close] has already been called on this hierarchy.
     */
    protected fun checkOpen() {
        check(!closed.get()) { "This playlist hierarchy has been closed and can no longer be used." }
    }

    /**
     * Asserts that the audio item delete subscriber is wired.
     *
     * @throws IllegalStateException if the hierarchy was not constructed via the library builder,
     * which is the only supported construction path that wires the delete subscriber.
     */
    private fun checkSubscribed() {
        check(subscriptionEstablished.get()) {
            "This playlist hierarchy must be constructed via the library builder so its audio item " +
                "delete subscriber is wired. Without it, deleted audio items are never removed from playlists."
        }
    }

    /**
     * Marks the audio item delete subscriber as wired, clearing the first-use subscription guard.
     *
     * The guard is set automatically when the library builder calls `audioLibrary.subscribe(hierarchy)`.
     * This method is available to subclasses and same-module test code for controlled scenarios —
     * such as test doubles or tests that exercise standalone hierarchy behavior — where the
     * subscription is established through a different mechanism or is not required.
     */
    internal fun markDeleteSubscriberWired() {
        subscriptionEstablished.set(true)
    }

    /**
     * Subclass-visible bridge to [markDeleteSubscriberWired].
     *
     * [markDeleteSubscriberWired] is module-internal, so subclasses defined in other modules cannot
     * reach it. This protected delegate lets such a subclass expose its own module-internal test hook
     * for standalone-hierarchy scenarios where the delete subscriber is wired through a different mechanism.
     */
    protected fun markDeleteSubscriberWiredForTesting() {
        markDeleteSubscriberWired()
    }

    init {
        repository.disableEvents(CREATE, UPDATE, DELETE)
        activateEvents(CREATE, UPDATE, DELETE)
        addOnNextEventAction(DELETE) { event ->
            forEach {
                it.removeAudioItems(event.entities.values)
            }
        }
        // Set the subscription-established flag when the audio library's publisher delivers its
        // onSubscribe callback to this subscriber — this happens inside the builder's
        // audioLibrary.subscribe(hierarchy) call. No builder-file edit is needed.
        addOnSubscribeEventAction { subscriptionEstablished.set(true) }
    }

    private val logger = KotlinLogging.logger {}

    private val playlistsHierarchyMultiMap = SortedMultimap<String, P>()

    // Tracks per-playlist mutation subscriptions so they can be cancelled on remove/close.
    private val playlistMutationSubscriptions = ConcurrentHashMap<Int, LirpEventSubscription<*, *, *>>()

    // Seeded above the highest id already present so allocation stays O(1) on a hierarchy loaded
    // with existing playlists; the retry cap then only guards a genuinely saturated id space rather
    // than tripping on a dense run of low ids. Seeding is deferred until first allocation so the
    // repository is fully populated by the time the maximum id is read.
    private val idCounter: AtomicInteger by lazy {
        AtomicInteger((search { true }.maxOfOrNull { it.id } ?: 0) + 1)
    }

    protected fun newId(): Int {
        var id: Int
        var attempts = 0
        do {
            check(attempts++ < MAX_ID_SEARCH_ATTEMPTS) {
                "Playlist id space exhausted: no available id found after $MAX_ID_SEARCH_ATTEMPTS attempts"
            }
            id = idCounter.getAndIncrement()
            check(id > 0) {
                "Playlist id space exhausted: the id counter overflowed Int.MAX_VALUE"
            }
        } while (contains(id))
        return id
    }

    override fun add(entity: P): Boolean {
        checkOpen()
        checkSubscribed()
        require(findByName(entity.name).isEmpty || findByName(entity.name).get().id == entity.id) {
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
                    require(added.id != playlist.id) { "Cannot add playlist to itself" }
                    require(!isDescendant(added, playlist)) {
                        "Cannot add ancestor playlist '${added.name}' as child — would create a cycle"
                    }
                    findParentPlaylist(added).ifPresent { oldParent ->
                        if (oldParent.id != playlist.id) {
                            playlistsHierarchyMultiMap.remove(oldParent.uniqueId, added)
                            oldParent.removePlaylists(listOf(added))
                        }
                    }
                    playlistsHierarchyMultiMap.put(playlist.uniqueId, added)
                    if (!contains(added.id)) {
                        addInternal(added)
                    }
                }
                for (removed in event.removed) {
                    playlistsHierarchyMultiMap.remove(playlist.uniqueId, removed)
                }
            }
        playlistMutationSubscriptions[playlist.id] = subscription
    }

    override fun remove(entity: P): Boolean {
        checkOpen()
        checkSubscribed()
        return repository.remove(entity).also { removed ->
            if (removed) {
                cancelPlaylistMutationSubscription(entity.id)
                removeFromPlaylistsHierarchy(entity)
            }
        }
    }

    private fun cancelPlaylistMutationSubscription(playlistId: Int) {
        playlistMutationSubscriptions.remove(playlistId)?.cancel()
    }

    private fun removeFromPlaylistsHierarchy(playlist: P) {
        findParentPlaylist(playlist).ifPresent { parentPlaylist ->
            parentPlaylist.removePlaylist(playlist)
            playlistsHierarchyMultiMap.remove(parentPlaylist.uniqueId, playlist)
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
        checkOpen()
        checkSubscribed()
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

    override fun findByName(name: String): Optional<out P> {
        checkOpen()
        return findFirst { it.name == name }
    }

    override fun findParentPlaylist(playlist: ReactiveAudioPlaylist<I, P>): Optional<P> {
        checkOpen()
        return if (playlistsHierarchyMultiMap.containsValue(playlist)) {
            playlistsHierarchyMultiMap.entries().stream()
                .filter { playlist == it.value }
                .map { findByUniqueId(it.key) }
                .filter { it.isPresent }
                .map { it.get() }
                .findFirst()
        } else {
            Optional.empty()
        }
    }

    override fun movePlaylist(playlistNameToMove: String, destinationPlaylistName: String) {
        checkOpen()
        checkSubscribed()
        val playlistToMove = findByName(playlistNameToMove)
        val destinationPlaylist = findByName(destinationPlaylistName)

        require(playlistToMove.isPresent) { "Playlist '$playlistNameToMove' does not exist" }
        require(destinationPlaylist.isPresent) { "Playlist '$destinationPlaylistName' does not exist" }
        require(playlistNameToMove != destinationPlaylistName) { "Cannot move playlist into itself" }
        require(!isDescendant(playlistToMove.get(), destinationPlaylist.get())) {
            "Cannot move playlist '$playlistNameToMove' into its own descendant '$destinationPlaylistName'"
        }

        withLoggingContext("playlistId" to playlistToMove.get().id.toString()) {
            findParentPlaylist(playlistToMove.get()).ifPresent { parentPlaylist: P ->
                parentPlaylist.removePlaylist(playlistToMove.get())
                logger.trace { "Playlist '$playlistNameToMove' removed from '$parentPlaylist'" }
            }

            destinationPlaylist.get().addPlaylist(playlistToMove.get())
            logger.trace { "Playlist '$playlistNameToMove' moved to '$destinationPlaylistName'" }
        }
    }

    private fun isDescendant(parent: P, candidate: P): Boolean {
        val children = playlistsHierarchyMultiMap[parent.uniqueId].toList()
        return candidate in children || children.any { isDescendant(it, candidate) }
    }

    override fun addAudioItemsToPlaylist(audioItems: Collection<I>, playlistName: String): Boolean {
        checkSubscribed()
        return findByName(playlistName).let {
            require(it.isPresent) { "Playlist '$playlistName' does not exist" }
            it.get().addAudioItems(audioItems)
        }
    }

    override fun removeAudioItemsFromPlaylist(audioItems: Collection<I>, playlistName: String): Boolean {
        checkSubscribed()
        return findByName(playlistName).let {
            require(it.isPresent) { "Playlist '$playlistName' does not exist" }
            it.get().removeAudioItems(audioItems)
        }
    }

    // @JvmName required on generic interface methods to avoid JVM signature clashes with Java callers
    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("removeAudioItemIdsFromPlaylist")
    override fun removeAudioItemsFromPlaylist(audioItemIds: Collection<Int>, playlistName: String): Boolean {
        checkSubscribed()
        return findByName(playlistName).let {
            require(it.isPresent) { "Playlist '$playlistName' does not exist" }
            it.get().removeAudioItems(audioItemIds)
        }
    }

    override fun addPlaylistsToDirectory(playlistsToAdd: Set<P>, directoryName: String): Boolean {
        checkSubscribed()
        return findByName(directoryName).let {
            require(it.isPresent) { "Directory '$directoryName' does not exist" }
            require(it.get().isDirectory) { "Playlist '$directoryName' is not a directory" }
            it.get().addPlaylists(playlistsToAdd).also { added ->
                if (added) {
                    playlistsHierarchyMultiMap.putAll(it.get().uniqueId, playlistsToAdd)
                }
            }
        }
    }

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("addPlaylistNamesToDirectory")
    override fun addPlaylistsToDirectory(playlistNamesToAdd: Set<String>, directoryName: String): Boolean {
        checkSubscribed()
        return findByName(directoryName).let {
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
    }

    override fun removePlaylistsFromDirectory(playlistsToRemove: Set<P>, directoryName: String): Boolean {
        checkSubscribed()
        val directory = findByName(directoryName)
        require(directory.isPresent) { "Directory '$directoryName' does not exist" }
        val actualChildren =
            playlistsToRemove.filterTo(LinkedHashSet()) { playlist ->
                playlist in directory.get().playlists
            }
        return directory.get().removePlaylists(actualChildren).also { removed ->
            if (removed) {
                removeAll(actualChildren)
                actualChildren.forEach { playlist ->
                    playlistsHierarchyMultiMap.remove(directory.get().uniqueId, playlist)
                }
            }
        }
    }

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("removePlaylistNamesFromDirectory")
    override fun removePlaylistsFromDirectory(playlistsNamesToRemove: Set<String>, directoryName: String): Boolean {
        checkSubscribed()
        val directory = findByName(directoryName)
        require(directory.isPresent) { "Directory '$directoryName' does not exist" }
        val resolved =
            playlistsNamesToRemove.stream().map { playlistName ->
                findByName(playlistName)
                    .orElseThrow { IllegalArgumentException("Playlist '$playlistName' does not exist") }
            }.toList()
        val actualChildren =
            resolved.filterTo(LinkedHashSet()) { playlist ->
                playlist in directory.get().playlists
            }
        return directory.get().removePlaylists(actualChildren).also { removed ->
            if (removed) {
                removeAll(actualChildren)
                actualChildren.forEach { playlist ->
                    playlistsHierarchyMultiMap.remove(directory.get().uniqueId, playlist)
                }
            }
        }
    }

    override fun numberOfPlaylists() = search { it.isDirectory.not() }.count()

    override fun numberOfPlaylistDirectories() = search { it.isDirectory }.count()

    /**
     * Closes this hierarchy idempotently.
     *
     * The first call sets the closed flag and cancels the audio item event subscription and all
     * per-playlist mutation subscriptions. Subsequent calls return immediately without repeating
     * the teardown.
     */
    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        cancelBaseSubscriptions()
    }

    /**
     * Cancels the audio item event subscription and all per-playlist mutation subscriptions.
     *
     * Extracted from [close] so a subclass that owns the [closed] compare-and-set as its first
     * statement can perform its own teardown before invoking the base teardown, without the base
     * re-running the flag transition.
     */
    protected fun cancelBaseSubscriptions() {
        audioItemEventSubscriber.cancelSubscription()
        playlistMutationSubscriptions.values.forEach { it.cancel() }
        playlistMutationSubscriptions.clear()
    }
}