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

import net.transgressoft.commons.music.audio.AudioItem
import net.transgressoft.commons.music.audio.AudioItemManipulationException
import net.transgressoft.lirp.entity.toIds
import net.transgressoft.lirp.event.CrudEvent.Type.CREATE
import net.transgressoft.lirp.event.CrudEvent.Type.DELETE
import net.transgressoft.lirp.event.CrudEvent.Type.UPDATE
import net.transgressoft.lirp.persistence.RegistryBase
import net.transgressoft.lirp.persistence.Repository
import net.transgressoft.lirp.persistence.VolatileRepository
import net.transgressoft.lirp.persistence.aggregateList
import mu.KotlinLogging
import kotlinx.serialization.Transient
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

/**
 * Default implementation of [PlaylistHierarchy] for managing [MutableAudioPlaylist] instances.
 *
 * Registers the underlying repository in [net.transgressoft.lirp.persistence.LirpContext.default]
 * so that nested playlist references and audio item IDs stored in serialized playlists are resolved
 * lazily via the [@Aggregate][net.transgressoft.lirp.persistence.Aggregate] delegate.
 *
 * Audio item references are resolved from the audio library registered in [net.transgressoft.lirp.persistence.LirpContext.default].
 * The audio library must be constructed and registered before this hierarchy is instantiated.
 */
class DefaultPlaylistHierarchy(
    repository: Repository<Int, MutableAudioPlaylist> = VolatileRepository()
): PlaylistHierarchyBase<AudioItem, MutableAudioPlaylist>(repository) {
    private val logger = KotlinLogging.logger {}

    init {
        deregisterExistingRegistration()
        RegistryBase.registerRepository(MutableAudioPlaylist::class.java, repository)

        disableEvents(CREATE, UPDATE, DELETE)
        // Replace deserialized ImmutablePlaylist stubs with real MutablePlaylist instances
        val stubs = toList()
        val audioItemRepository = findAudioItemRepository()
        stubs.forEach { stub ->
            val audioItemIds = if (stub is ImmutablePlaylist) stub.audioItemIds else stub.audioItems.toIds()
            val realPlaylist = MutablePlaylist(stub.id, stub.isDirectory, stub.name, initialAudioItemIds = audioItemIds)
            remove(stub)
            add(realPlaylist)
            // Resolve audio items directly from the registered AudioItem repository.
            // resolveAll() on audioItemsAggregate is not used here because RegistryBase caches
            // discoverRefs() results per repository instance, and the cache was already populated
            // with empty entries when ImmutablePlaylist stubs were loaded before init runs.
            audioItemRepository?.let { repo ->
                audioItemIds.mapNotNull { id ->
                    @Suppress("UNCHECKED_CAST")
                    (repo as? net.transgressoft.lirp.persistence.Repository<Int, AudioItem>)?.findById(id)?.orElse(null)
                }.forEach { realPlaylist.audioItems.add(it) }
            }
        }
        // Resolve nested playlists within the same repository (D-04: no @Aggregate needed)
        toList().forEach { playlist ->
            val stubPlaylistIds =
                stubs.find { it.id == playlist.id }
                    ?.let { stub -> if (stub is ImmutablePlaylist) stub.playlistIds else stub.playlists.toIds().toSet() }
                    ?: emptySet()
            val foundPlaylists = findDeserializedPlaylistsFromIds(stubPlaylistIds, repository)
            playlist.addPlaylists(foundPlaylists)
        }
        activateEvents(CREATE, UPDATE, DELETE)
    }

    private fun findAudioItemRepository(): Any? {
        return try {
            val context = getContext(repository) ?: return null
            val registryForMethod =
                context.javaClass.methods
                    .firstOrNull { it.name.startsWith("registryFor") && it.parameterCount == 1 && it.parameterTypes[0] == Class::class.java }
                    ?: return null
            registryForMethod.invoke(context, AudioItem::class.java)
        } catch (_: Exception) {
            null
        }
    }

    private fun findDeserializedPlaylistsFromIds(
        playlists: Set<Int>,
        repository: Repository<Int, MutableAudioPlaylist>
    ): List<MutableAudioPlaylist> =
        playlists.stream().map {
            repository.findById(it)
                .orElseThrow { AudioItemManipulationException("AudioPlaylist with id $it not found during deserialization") }
        }.toList()

    override fun createPlaylist(name: String): MutableAudioPlaylist = createPlaylist(name, emptyList())

    override fun createPlaylist(
        name: String,
        audioItems: List<AudioItem>
    ): MutableAudioPlaylist {
        require(findByName(name).isEmpty) { "Playlist with name '$name' already exists" }
        return MutablePlaylist(newId(), false, name, audioItems).also {
            logger.debug { "Created playlist $it" }
            add(it)
        }
    }

    override fun createPlaylistDirectory(name: String): MutableAudioPlaylist = createPlaylistDirectory(name, emptyList())

    override fun createPlaylistDirectory(
        name: String,
        audioItems: List<AudioItem>
    ): MutableAudioPlaylist {
        require(findByName(name).isEmpty) { "Playlist with name '$name' already exists" }
        return MutablePlaylist(newId(), true, name, audioItems).also {
            logger.debug { "Created playlist directory $it" }
            add(it)
        }
    }

    override fun close() {
        super.close()
        // Deregister the repository from LirpContext so subsequent DefaultPlaylistHierarchy
        // instances can register without conflict. Accessed via reflection since LirpContext
        // internals are not public API.
        deregisterFromLirpContext(repository)
    }

    private fun deregisterFromLirpContext(repo: Any) {
        try {
            val context = getContext(repo) ?: return
            val registryInterface = Class.forName("net.transgressoft.lirp.persistence.Registry")
            val deregisterMethod =
                context.javaClass.methods
                    .firstOrNull { it.name.startsWith("deregister") && it.parameterCount == 1 && registryInterface.isAssignableFrom(it.parameterTypes[0]) }
                    ?: return
            deregisterMethod.invoke(context, repo)
        } catch (_: Exception) {
            // Deregistration is best-effort; failure does not impact lifecycle semantics
        }
    }

    private fun deregisterExistingRegistration() {
        try {
            val context = getContext(repository) ?: return
            val registryForMethod =
                context.javaClass.methods
                    .firstOrNull { it.name.startsWith("registryFor") && it.parameterCount == 1 && it.parameterTypes[0] == Class::class.java }
                    ?: return
            val existing = registryForMethod.invoke(context, MutableAudioPlaylist::class.java) ?: return
            if (existing !== repository) {
                val registryInterface = Class.forName("net.transgressoft.lirp.persistence.Registry")
                val deregisterMethod =
                    context.javaClass.methods
                        .firstOrNull { it.name.startsWith("deregister") && it.parameterCount == 1 && registryInterface.isAssignableFrom(it.parameterTypes[0]) }
                        ?: return
                deregisterMethod.invoke(context, existing)
            }
        } catch (_: Exception) {
            // Best-effort; failure is non-critical since registerRepository will detect conflicts
        }
    }

    private fun getContext(repo: Any): Any? {
        return try {
            val getContextMethod =
                repo.javaClass.methods
                    .firstOrNull { it.name.startsWith("getContext") && it.parameterCount == 0 }
                    ?: return null
            getContextMethod.invoke(repo)
        } catch (_: Exception) {
            null
        }
    }

    override fun toString() = "PlaylistRepository(playlistsCount=${size()})"

    /**
     * Concrete implementation of [MutableAudioPlaylist] bound to this hierarchy.
     *
     * Implemented as an inner class to inherit hierarchy management capabilities from
     * [MutablePlaylistBase] while accessing the enclosing [DefaultPlaylistHierarchy]
     * instance for playlist lookup operations during hierarchy updates.
     *
     * Audio item IDs stored in [audioItemIds] are resolved lazily from [net.transgressoft.lirp.persistence.LirpContext]
     * via the [audioItemsAggregate] delegate when this playlist is added to the repository.
     * See [DefaultPlaylistHierarchyLirpRefAccessor] for the manually-written aggregate reference
     * accessor that enables resolution without KSP-generated code.
     */
    private inner class MutablePlaylist(
        id: Int,
        isDirectory: Boolean,
        name: String,
        audioItems: List<AudioItem> = listOf(),
        playlists: Set<MutableAudioPlaylist> = setOf(),
        initialAudioItemIds: List<Int> = audioItems.map { it.id }
    ): MutablePlaylistBase(id, isDirectory, name, audioItems, playlists, initialAudioItemIds), MutableAudioPlaylist {

        // Aggregate delegate for lazy resolution of audio item IDs from LirpContext.
        // The @Aggregate annotation is omitted to prevent KSP from generating a public accessor
        // that would fail due to the internal visibility of this class.
        // The resolution is wired via DefaultPlaylistHierarchyLirpRefAccessor instead.
        @Transient
        val audioItemsAggregate by aggregateList<Int, AudioItem> { audioItemIds }

        override fun clone(): MutablePlaylist = MutablePlaylist(id, isDirectory, name, audioItems.toList(), playlists.toSet(), audioItemIds.toList())
    }
}

@JvmField
@get:JvmName("playlistSerializerModule")
internal val playlistSerializerModule =
    SerializersModule {
        polymorphic(ReactiveAudioPlaylist::class) {
            subclass(MutableAudioPlaylistSerializer)
        }
    }