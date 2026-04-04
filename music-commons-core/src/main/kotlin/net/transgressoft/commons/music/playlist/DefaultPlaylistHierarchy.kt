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
import net.transgressoft.lirp.persistence.RegistryBase
import net.transgressoft.lirp.persistence.Repository
import net.transgressoft.lirp.persistence.VolatileRepository
import net.transgressoft.lirp.persistence.json.JsonFileRepository
import mu.KotlinLogging
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

/**
 * Default implementation of [PlaylistHierarchy] for managing [MutableAudioPlaylist] instances.
 *
 * Playlist deserialization is handled by the companion object, which extends [AudioPlaylistSerializerBase]
 * and constructs real [MutableAudioPlaylist] instances directly during JSON deserialization via
 * [createPlaylistFromProperties]. Use [Companion.createAndBind] to create a hierarchy with
 * correct initialization order: the companion sets [Companion.instance] before constructing
 * any repository that uses [AudioPlaylistMapSerializer], so deserialized playlists are immediately
 * wired to this hierarchy's inner class factory.
 *
 * For JSON-backed persistence, prefer [Companion.createAndBind] with a [java.io.File] argument
 * over constructing a [JsonFileRepository] manually, since that overload handles two-phase
 * construction automatically.
 *
 * Audio item references are resolved from the audio library registered in
 * [net.transgressoft.lirp.persistence.LirpContext.default]. The audio library must be constructed
 * and registered before [Companion.createAndBind] is called.
 */
internal class DefaultPlaylistHierarchy private constructor(
    delegate: RepositoryDelegate<Int, MutableAudioPlaylist>
) : PlaylistHierarchyBase<AudioItem, MutableAudioPlaylist>(delegate) {

    private val logger = KotlinLogging.logger {}

    constructor(repository: Repository<Int, MutableAudioPlaylist> = VolatileRepository()) :
        this(RepositoryDelegate(repository))

    init {
        if (Companion.instance == null) {
            // Direct construction path: self-register immediately so that any
            // JsonFileRepository created after this point can deserialize playlists.
            Companion.instance = this
        }
        bindInitialRepository(MutableAudioPlaylist::class.java, AudioItem::class.java)
    }

    override fun createPlaylistFromProperties(
        id: Int,
        isDirectory: Boolean,
        name: String,
        initialAudioItemIds: List<Int>
    ): MutableAudioPlaylist = MutablePlaylist(id, isDirectory, name, initialAudioItemIds = initialAudioItemIds)

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
        RegistryBase.deregisterRepository(MutableAudioPlaylist::class.java)
        if (Companion.instance === this) Companion.instance = null
    }

    override fun toString() = "PlaylistRepository(playlistsCount=${size()})"

    /**
     * Concrete implementation of [MutableAudioPlaylist] bound to this hierarchy.
     *
     * Implemented as an inner class to inherit hierarchy management capabilities from
     * [MutablePlaylistBase] while accessing the enclosing [DefaultPlaylistHierarchy]
     * instance for playlist lookup operations during hierarchy updates.
     *
     * Audio item IDs stored in [audioItemIds] are resolved during [bindRepository]
     * initialization by looking up registered audio item repositories in LirpContext.
     */
    private inner class MutablePlaylist(
        id: Int,
        isDirectory: Boolean,
        name: String,
        audioItems: List<AudioItem> = listOf(),
        playlists: Set<MutableAudioPlaylist> = setOf(),
        initialAudioItemIds: List<Int> = audioItems.map { it.id }
    ) : MutablePlaylistBase(id, isDirectory, name, audioItems, playlists, initialAudioItemIds), MutableAudioPlaylist {

        override fun clone(): MutablePlaylist =
            MutablePlaylist(id, isDirectory, name, audioItems.toList(), playlists.toSet(), audioItemIds.toList())
    }

    /**
     * Companion object serializer for [DefaultPlaylistHierarchy].
     *
     * Extends [AudioPlaylistSerializerBase] so it can be used directly as a
     * [kotlinx.serialization.KSerializer] for [MutableAudioPlaylist]. When [instance] is set,
     * [createInstance] calls [createPlaylistFromProperties] to construct real [MutablePlaylist]
     * objects directly. If [instance] is null when deserialization is attempted, an error is thrown —
     * the hierarchy must be set before any repository backed by [AudioPlaylistMapSerializer] is created.
     *
     * Use [createAndBind] as the preferred factory when loading a persisted hierarchy:
     * the overload accepting a [java.io.File] sets [instance] before constructing a
     * [JsonFileRepository], enabling direct deserialization into real [MutablePlaylist] instances.
     */
    companion object : AudioPlaylistSerializerBase<AudioItem, MutableAudioPlaylist>() {

        /**
         * The active [DefaultPlaylistHierarchy] instance used by [createInstance] during deserialization.
         *
         * Set automatically by [DefaultPlaylistHierarchy] constructors and cleared in [DefaultPlaylistHierarchy.close].
         * Must be non-null before any [AudioPlaylistMapSerializer]-backed repository is created.
         */
        internal var instance: DefaultPlaylistHierarchy? = null

        /**
         * Creates a [DefaultPlaylistHierarchy] bound to [repository], registering it in LirpContext
         * and resolving any pre-loaded playlists in [repository].
         *
         * Assumes [repository] was constructed after [instance] was already set (i.e., the caller
         * managed the initialization order). Use [createAndBind] with a [java.io.File] for JSON
         * persistence to handle the initialization order automatically.
         *
         * @param repository the repository to bind and register in LirpContext
         * @return the fully initialized [DefaultPlaylistHierarchy]
         */
        fun createAndBind(repository: Repository<Int, MutableAudioPlaylist>): DefaultPlaylistHierarchy {
            val hierarchy = DefaultPlaylistHierarchy(repository)
            instance = hierarchy
            return hierarchy
        }

        /**
         * Creates a [DefaultPlaylistHierarchy] backed by a [JsonFileRepository] at [file], handling
         * two-phase construction to avoid initialization order issues.
         *
         * Sets [instance] before constructing the [JsonFileRepository] so that deserialized playlists
         * are created as inner class instances of this hierarchy. After loading, switches the backing
         * store to the JSON repository so all subsequent CRUD operations persist to [file].
         *
         * The audio library must be registered in LirpContext before calling this method so that
         * audio item references can be resolved during initialization.
         *
         * @param file the JSON file to load from and persist to
         * @return the fully initialized [DefaultPlaylistHierarchy]
         */
        fun createAndBind(file: java.io.File): DefaultPlaylistHierarchy {
            // Phase 1: Create hierarchy with volatile repo — sets instance for deserialization
            val hierarchy = DefaultPlaylistHierarchy()
            // Phase 2: Construct JsonFileRepository — createInstance() uses instance (hierarchy)
            val jsonRepo = JsonFileRepository(file, AudioPlaylistMapSerializer)
            // Phase 3: Switch backing to jsonRepo and resolve audio items / nested playlists
            hierarchy.switchToRepository(jsonRepo, MutableAudioPlaylist::class.java, AudioItem::class.java)
            return hierarchy
        }

        @Suppress("UNCHECKED_CAST")
        override fun createInstance(propertiesList: List<Any?>): MutableAudioPlaylist {
            val hierarchy = instance
            check(hierarchy != null) {
                "DefaultPlaylistHierarchy.instance must be set before deserialization. Use createAndBind() or set instance manually."
            }
            return hierarchy.createPlaylistFromProperties(
                id = propertiesList[0] as Int,
                isDirectory = propertiesList[1] as Boolean,
                name = propertiesList[2] as String,
                initialAudioItemIds = propertiesList[3] as List<Int>
            )
        }
    }
}

@JvmField
@get:JvmName("playlistSerializerModule")
internal val playlistSerializerModule =
    SerializersModule {
        polymorphic(ReactiveAudioPlaylist::class) {
            subclass(DefaultPlaylistHierarchy)
        }
    }