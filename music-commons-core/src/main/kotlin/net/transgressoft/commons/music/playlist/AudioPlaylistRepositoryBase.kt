package net.transgressoft.commons.music.playlist

import net.transgressoft.commons.ReactiveEntityBase
import net.transgressoft.commons.data.DataEvent
import net.transgressoft.commons.data.StandardDataEvent.Type.*
import net.transgressoft.commons.data.json.JsonFileRepository
import net.transgressoft.commons.event.TransEventSubscriber
import net.transgressoft.commons.music.audio.ReactiveAudioItem
import net.transgressoft.commons.music.audio.event.AudioItemEventSubscriber
import net.transgressoft.commons.toIds
import mu.KotlinLogging
import org.jetbrains.kotlin.com.google.common.collect.Multimap
import org.jetbrains.kotlin.com.google.common.collect.MultimapBuilder
import org.jetbrains.kotlin.com.google.common.collect.Sets
import java.io.File
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.stream.Collectors.*
import kotlin.properties.Delegates.observable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.modules.SerializersModule

abstract class AudioPlaylistRepositoryBase<I : ReactiveAudioItem<I>, P : MutableAudioPlaylist<I, P>>(
    override val name: String,
    file: File,
    playlistSerializerBase: AudioPlaylistSerializerBase<I, P>,
    serializersModule: SerializersModule = SerializersModule {}
) : JsonFileRepository<Int, P>(file, MapSerializer(Int.serializer(), playlistSerializerBase), SerializersModule {
        include(serializersModule)
        include(playlistSerializerModule)
    }),
    AudioPlaylistRepository<I, P> {

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

    override val audioItemEventSubscriber: TransEventSubscriber<I, DataEvent<Int, out I>> =
        AudioItemEventSubscriber<I>(this.toString()).apply {
            addOnNextEventAction(DELETE) { event ->
                runForAll {
                    it.removeAudioItems(event.entitiesById.keys)
                }
            }
            addOnNextEventAction(UPDATE) { event ->
                runForAll {
                    val updatedAudioItems = event.entitiesById
                    val audioItemsToUpdate = Sets.intersection(updatedAudioItems.keys, it.audioItems.toIds().toSet()).toSet()
                    if (audioItemsToUpdate.isNotEmpty()) {
                        it.removeAudioItems(updatedAudioItems.keys)
                        it.addAudioItems(updatedAudioItems.values)
                    }
                }
            }
        }

    override fun add(entity: P): Boolean {
        return addInternal(entity)
    }

    private fun addInternal(playlist: P): Boolean {
        var added = super.add(playlist)
        for (p in playlist.playlists) {
            playlistsHierarchyMultiMap.put(playlist.uniqueId, p)
            added = added or addInternal(p)
        }
        return added
    }

    override fun addOrReplace(entity: P) = addOrReplaceAll(setOf(entity))

    override fun addOrReplaceAll(entities: Set<P>): Boolean {
        val entitiesBeforeUpdate = mutableListOf<P>()

        val addedAndReplaced = entities.stream().filter { it != null && entitiesById.containsValue(it) }
            .collect(partitioningBy { entity ->
                val entityBefore = entitiesById[entity.id]
                if (entityBefore != null)
                    entitiesBeforeUpdate.add(entityBefore)
                entitiesById[entity.id] = entity
                return@partitioningBy entityBefore == null
            })

        addedAndReplaced[true]?.let {
            if (it.isNotEmpty()) {
                putCreateEvent(it)
                logger.debug { "${it.size} entities were added: $it" }
            }
        }
        addedAndReplaced[false]?.let {
            if (it.isNotEmpty()) {
                putUpdateEvent(it, entitiesBeforeUpdate)
                logger.debug { "${it.size} entities were replaced: $it" }
            }
        }

        return addedAndReplaced.values.stream().flatMap { it.stream() }.findAny().isPresent
    }

    override fun remove(entity: P): Boolean {
        return super.remove(entity).also { removed ->
            if (removed) {
                removeFromPlaylistsHierarchy(entity)
            }
        }
    }

    private fun removeFromPlaylistsHierarchy(playlist: P) {
        playlistsHierarchyMultiMap.removeAll(playlist.uniqueId)
        if (playlistsHierarchyMultiMap.containsValue(playlist)) {
            playlistsHierarchyMultiMap.entries().stream()
                .filter { playlist == it.value }
                .forEach { entry ->
                    playlistsHierarchyMultiMap.remove(entry.key, entry.value)
                }
        }
        removeAll(playlist.playlists)
    }

    override fun removeAll(entities: Set<P>): Boolean {
        return super.removeAll(entities).also { removed ->
            if (removed) {
                entities.forEach(::removeFromPlaylistsHierarchy)
            }
        }
    }

    override fun findByName(name: String): Optional<P> = findFirst { it.name == name }

    override fun findParentPlaylist(playlist: MutableAudioPlaylist<I, P>): Optional<P> =
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

    override fun addAudioItemsToPlaylist(audioItems: Collection<I>, playlistName: String): Boolean {
        return findByName(playlistName).let {
            require(it.isPresent) { "Playlist '$playlistName' does not exist" }
            it.get().addAudioItems(audioItems)
        }
    }

    override fun removeAudioItemsFromPlaylist(audioItems: Collection<I>, playlistName: String): Boolean {
        return findByName(playlistName).let {
            require(it.isPresent) { "Playlist '$playlistName' does not exist" }
            it.get().removeAudioItems(audioItems)
        }
    }

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("removeAudioItemIdsFromPlaylist")
    override fun removeAudioItemsFromPlaylist(audioItemIds: Collection<Int>, playlistName: String): Boolean {
        return findByName(playlistName).let {
            require(it.isPresent) { "Playlist '$playlistName' does not exist" }
            it.get().removeAudioItems(audioItemIds)
        }
    }

    override fun addPlaylistsToDirectory(playlistsToAdd: Set<P>, directoryName: String): Boolean {
        return findByName(directoryName).let {
            require(it.isPresent) { "Directory '$directoryName' does not exist" }
            it.get().addPlaylists(playlistsToAdd).also { added ->
                if (added) {
                    playlistsHierarchyMultiMap.putAll(
                        it.get().uniqueId,
                        playlistsToAdd
                    )
                }
            }
        }
    }

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("addPlaylistNamesToDirectory")
    override fun addPlaylistsToDirectory(playlistNamesToAdd: Set<String>, directoryName: String): Boolean {
        return findByName(directoryName).let {
            require(it.isPresent) { "Directory '$directoryName' does not exist" }
            playlistNamesToAdd.stream().map { playlistName ->
                findByName(playlistName).orElseThrow { IllegalArgumentException("Playlist '$playlistName' does not exist") }
            }.toList().let { playlistsToAdd ->
                it.get().addPlaylists(playlistsToAdd).also { added ->
                    if (added) {
                        playlistsHierarchyMultiMap.putAll(
                            it.get().uniqueId,
                            playlistsToAdd
                        )
                    }
                }
            }
        }
    }

    override fun removePlaylistsFromDirectory(playlistsToRemove: Set<P>, directoryName: String): Boolean {
        return findByName(directoryName).let {
            require(it.isPresent) { "Directory '$directoryName' does not exist" }
            it.get().removePlaylists(playlistsToRemove).also { removed ->
                if (removed) {
                    removeAll(playlistsToRemove)
                    playlistsToRemove.forEach { playlist -> playlistsHierarchyMultiMap.remove(it.get().uniqueId, playlist) }
                }
            }
        }
    }

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("removePlaylistNamesFromDirectory")
    override fun removePlaylistsFromDirectory(playlistsNamesToRemove: Set<String>, directoryName: String): Boolean {
        return findByName(directoryName).let {
            require(it.isPresent) { "Directory '$directoryName' does not exist" }
            playlistsNamesToRemove.stream().map { playlistName ->
                findByName(playlistName).orElseThrow { IllegalArgumentException("Playlist '$playlistName' does not exist") }
            }.toList().let { playlistsToRemove ->
                it.get().removePlaylists(playlistsToRemove).also { removed ->
                    if (removed) {
                        removeAll(playlistsToRemove.toSet())
                        playlistsToRemove.forEach { playlist -> playlistsHierarchyMultiMap.remove(it.get().uniqueId, playlist) }
                    }
                }
            }
        }
    }

    override fun numberOfPlaylists() =
        entitiesById.values.stream()
            .filter { it.isDirectory.not() }
            .count().toInt()

    override fun numberOfPlaylistDirectories() =
        entitiesById.values.stream()
            .filter { it.isDirectory }
            .count().toInt()

    protected abstract inner class MutablePlaylistBase(
        override val id: Int,
        isDirectory: Boolean,
        name: String,
        audioItems: List<I> = listOf(),
        playlists: Set<P> = setOf()
    ) : ReactiveEntityBase<Int, P>(), MutableAudioPlaylist<I, P> {

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
            val result = this.audioItems.stream().anyMatch { !audioItems.contains(it) }
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
            val result = this.audioItems.stream().anyMatch { audioItemIds.contains(it.id) }
            setAndNotify(this.audioItems - audioItems.toSet(), this.audioItems) {
                this.audioItems.removeAll { audioItemIds.contains(it.id) }
                logger.debug { "Removed audio items with ids $audioItemIds from playlist $uniqueId" }
            }
            return result
        }

        override fun addPlaylists(playlists: Collection<P>): Boolean {
            playlists.forEach {
                findParentPlaylist(it).ifPresent { parentPlaylist: MutableAudioPlaylist<I, P> ->
                    parentPlaylist.removePlaylist(it)
                    logger.debug { "Playlist '${it.name}' removed from '$parentPlaylist'" }
                }
            }
            val result = this.playlists.stream().anyMatch { !playlists.contains(it) }
            setAndNotify(this.playlists + playlists, this.playlists) {
                this.playlists.addAll(playlists).also {
                    if (it) {
                        playlistsHierarchyMultiMap.putAll(uniqueId, playlists)
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
                            playlistsHierarchyMultiMap.remove(uniqueId, playlist)
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
            val result = this.playlists.stream().anyMatch { playlistIds.contains(it.id) }
            this.playlists.removeAll { playlist -> playlistIds.contains(playlist.id) }.also {
                if (it) {
                    playlistIds.forEach { playlistId ->
                        findById(playlistId).ifPresent { playlist ->
                            playlistsHierarchyMultiMap.remove(uniqueId, playlist)
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

            other as AudioPlaylistRepositoryBase<*, *>.MutablePlaylistBase

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

        override fun toString() = "MutablePlaylist(id=$id, isDirectory=$isDirectory, name='$name', audioItems=$audioItems, playlists=$playlists)"
    }
}