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
import net.transgressoft.commons.fx.music.conditionalDeregister
import net.transgressoft.commons.fx.music.guardedRegister
import net.transgressoft.commons.music.playlist.PlaylistHierarchyBase
import net.transgressoft.commons.music.playlist.event.AudioPlaylistEventSubscriber
import net.transgressoft.lirp.entity.toIds
import net.transgressoft.lirp.event.CrudEvent.Type.CREATE
import net.transgressoft.lirp.event.CrudEvent.Type.DELETE
import net.transgressoft.lirp.event.CrudEvent.Type.UPDATE
import net.transgressoft.lirp.persistence.Repository
import net.transgressoft.lirp.persistence.VolatileRepository
import javafx.application.Platform
import javafx.beans.property.ReadOnlySetProperty
import javafx.beans.property.SimpleSetProperty
import javafx.collections.FXCollections
import javafx.collections.ObservableSet
import mu.KotlinLogging
import mu.withLoggingContext

/**
 * JavaFX-compatible playlist hierarchy with observable playlist collections.
 *
 * Maintains an observable set of all playlists in the hierarchy that automatically syncs
 * with repository changes. Enables JavaFX UI components to bind directly to the playlist
 * collection and receive automatic updates when playlists are created, modified, or removed.
 * All modifications are executed on the JavaFX Application Thread for thread safety. The
 * [Platform.runLater] dispatches intentionally omit error handling because they perform only simple
 * set mutations; any exception indicates a programming error that should surface through the default
 * uncaught exception handler.
 *
 * Playlist deserialization is driven by `lirpSerializer(FXPlaylist(0))` passed to a
 * [net.transgressoft.lirp.persistence.json.JsonFileRepository] at construction time.
 *
 * Only one live instance is permitted per JVM; constructing a second while one is live throws
 * [IllegalStateException]. [close] conditionally deregisters only when this instance still owns the slot.
 */
internal class FXPlaylistHierarchy(
    private val repository: Repository<Int, ObservablePlaylist> = VolatileRepository("FXPlaylistHierarchy")
) : PlaylistHierarchyBase<ObservableAudioItem, ObservablePlaylist>(repository), ObservablePlaylistHierarchy {

    private val logger = KotlinLogging.logger {}

    override val playlistElementType = ObservablePlaylist::class

    private val observablePlaylistsSet: ObservableSet<ObservablePlaylist> = FXCollections.observableSet()

    override val playlistsProperty: ReadOnlySetProperty<ObservablePlaylist> =
        SimpleSetProperty(this, "playlists", observablePlaylistsSet)

    private val playlistChangesSubscriber =
        AudioPlaylistEventSubscriber<ObservablePlaylist, ObservableAudioItem>("InternalAudioPlaylistSubscriber").apply {
            addOnNextEventAction(CREATE, UPDATE) { event ->
                synchronized(playlistsProperty) {
                    Platform.runLater {
                        if (closed.get()) return@runLater
                        observablePlaylistsSet.addAll(event.entities.values)
                    }
                }
            }
            addOnNextEventAction(DELETE) { event ->
                event.entities.values.forEach { playlist ->
                    if (playlist is FXPlaylist) playlist.detachAllChildRecursiveListeners()
                }
                synchronized(playlistsProperty) {
                    Platform.runLater {
                        if (closed.get()) return@runLater
                        observablePlaylistsSet.removeAll(event.entities.values.toSet())
                    }
                }
            }
        }

    init {
        guardedRegister(ObservablePlaylist::class.java, repository)

        // Re-sync aggregates after all entities are loaded. During repository load, entities
        // are added one at a time, so forward references (e.g. ROOT playlist referencing CHILD
        // playlist) cannot resolve because the referenced entity isn't in the repo yet. Audio
        // item aggregates also need re-sync because the audio repository is fully populated
        // before the playlist hierarchy loads, but FXPlaylist.init runs before bindEntityRefs
        // attaches the audio item registry. Now that all entities are loaded and bound,
        // syncLocalCache materializes the backing IDs into the local FX-observable cache,
        // and triggerCoverHydration recomputes cover/recursive properties from the freshly
        // populated audio item aggregate. FXPlaylist's child-recursive listener attaches
        // during this pass, so any descendant whose triggerCoverHydration runs after its
        // parent's still cascades the recursive aggregate up the chain.
        forEach { playlist ->
            if (playlist is FXPlaylist) {
                val audioSyncResult = runCatching { playlist.audioItemsAggregate.syncLocalCache() }
                playlist.playlistsAggregate.syncLocalCache()
                if (audioSyncResult.isSuccess) {
                    playlist.triggerCoverHydration()
                } else {
                    logger.warn(audioSyncResult.exceptionOrNull()) {
                        "Skipping cover hydration because audioItemsAggregate sync failed for $playlist"
                    }
                }
            }
        }

        forEach { playlist ->
            Platform.runLater {
                if (closed.get()) return@runLater
                observablePlaylistsSet.add(playlist)
            }
        }

        subscribe(playlistChangesSubscriber)
    }

    override fun createPlaylist(name: String): ObservablePlaylist = createPlaylist(name, emptyList<Int>())

    override fun createPlaylist(
        name: String,
        audioItems: List<ObservableAudioItem>
    ): ObservablePlaylist = createPlaylist(name, audioItems.toIds())

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("createPlaylistWithIds")
    override fun createPlaylist(
        name: String,
        audioItemIds: List<Int>
    ): ObservablePlaylist {
        require(findByName(name).isEmpty) { "Playlist with name '$name' already exists" }
        return FXPlaylist(newId(), name, false, audioItemIds).also {
            withLoggingContext("playlistId" to it.id.toString()) { logger.trace { "Created playlist $it" } }
            add(it)
            // The FXPlaylist init runs refreshDerivedState before lirp registry binding completes,
            // so its cover and audioItemsRecursive view are computed against an empty aggregate.
            // After add() the aggregate delegates are bound — re-trigger the recompute so a
            // playlist created with non-empty audioItemIds shows its cover image immediately.
            it.triggerCoverHydration()
        }
    }

    override fun createPlaylistDirectory(name: String): ObservablePlaylist = createPlaylistDirectory(name, emptyList())

    override fun createPlaylistDirectory(
        name: String,
        audioItems: List<ObservableAudioItem>
    ): ObservablePlaylist {
        require(findByName(name).isEmpty) { "Playlist with name '$name' already exists" }
        return FXPlaylist(newId(), name, true, audioItems.toIds()).also {
            withLoggingContext("playlistId" to it.id.toString()) { logger.trace { "Created playlist $it" } }
            add(it)
            it.triggerCoverHydration()
        }
    }

    /**
     * Marks this hierarchy closed as the first action, then detaches child listeners, cancels the
     * base class subscriptions and the internal playlist changes subscriber, and finally
     * conditionally deregisters the playlist repository from LirpContext only if this instance still
     * owns the slot.
     *
     * The [closed] compare-and-set runs before any teardown so a concurrent `Platform.runLater` body
     * guarded by `closed.get()` never observes an open flag while the hierarchy is being torn down.
     */
    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        forEach { playlist ->
            if (playlist is FXPlaylist) playlist.detachAllChildRecursiveListeners()
        }
        cancelBaseSubscriptions()
        playlistChangesSubscriber.cancelSubscription()
        conditionalDeregister(ObservablePlaylist::class.java, repository)
    }

    override fun toString() = "observablePlaylistHierarchy(playlistsCount=${size()})"
}