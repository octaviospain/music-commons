package net.transgressoft.commons.music.playlist

import net.transgressoft.commons.ReactiveEntity
import net.transgressoft.commons.data.DataEvent
import net.transgressoft.commons.data.RepositoryBase
import net.transgressoft.commons.data.StandardDataEvent.Type.*
import net.transgressoft.commons.data.json.JsonFileRepository
import net.transgressoft.commons.event.TransEventSubscriber
import net.transgressoft.commons.music.audio.AudioItem
import net.transgressoft.commons.music.audio.AudioItemManipulationException
import net.transgressoft.commons.music.audio.AudioItemRepository
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
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer

class AudioPlaylistJsonRepository(override val name: String, jsonFile: File) : RepositoryBase<Int, MutableAudioPlaylist<AudioItem>>(),
    AudioPlaylistRepository<AudioItem, MutableAudioPlaylist<AudioItem>> {

    private val logger = KotlinLogging.logger {}

    private val playlistsHierarchyMultiMap: Multimap<String, MutableAudioPlaylist<AudioItem>> = MultimapBuilder.treeKeys().treeSetValues().build()

    private val serializablePlaylistsRepository =
        JsonFileRepository("JsonFileRepository-${jsonFile.nameWithoutExtension}", jsonFile, MapSerializer(Int.serializer(), InternalAudioPlaylist.serializer()))

    private val idCounter: AtomicInteger = AtomicInteger(1)

    constructor(name: String, file: File, audioItemRepository: AudioItemRepository<AudioItem>) : this(name, file) {
        require(file.exists().and(file.canWrite()).and(file.extension == "json")) {
            "Provided jsonFile does not exist, is not writable or is not a json file"
        }

        serializablePlaylistsRepository.runForAll {
            val playlistsWithAudioItems = MutablePlaylist(it.id, it.isDirectory, it.name, mapAudioItemsFromIds(it.audioItemIds, audioItemRepository))
            entitiesById[it.id] = playlistsWithAudioItems
        }
        serializablePlaylistsRepository.runForAll {
            val playlistsMissingPlaylists =
                entitiesById[it.id] ?: throw AudioItemManipulationException("AudioPlaylist with id ${it.id} not found during deserialization")
            val foundPlaylists = findDeserializedPlaylistsFromIds(it.playlistIds, entitiesById)
            playlistsMissingPlaylists.addPlaylists(foundPlaylists)
        }
        if (entitiesById.isNotEmpty()) {
            logger.info { "Loaded ${entitiesById.size} playlists from file ${file.absolutePath}" }
        }
    }

    private fun mapAudioItemsFromIds(audioItemIds: List<Int>, audioItemRepository: AudioItemRepository<AudioItem>) =
        audioItemIds.map {
            audioItemRepository.findById(it).orElseThrow { AudioItemManipulationException("AudioItem with id $it not found during deserialization") }
        }.toList()

    private fun findDeserializedPlaylistsFromIds(
        playlists: Set<Int>,
        playlistsById: Map<Int, MutableAudioPlaylist<AudioItem>>
    ): List<MutableAudioPlaylist<AudioItem>> {
        return playlists.stream().map {
            return@map playlistsById[it] ?: throw AudioItemManipulationException("AudioPlaylist with id $it not found during deserialization")
        }.toList()
    }

    override fun createPlaylist(name: String): MutableAudioPlaylist<AudioItem> = createPlaylist(name, emptyList())

    override fun createPlaylist(name: String, audioItems: List<AudioItem>): MutableAudioPlaylist<AudioItem> {
        require(findByName(name).isEmpty) { "Playlist with name '$name' already exists" }
        return MutablePlaylist(newId(), false, name, audioItems).also {
            logger.debug { "Created playlist $it" }
            add(it)
        }
    }

    override fun createPlaylistDirectory(name: String): MutableAudioPlaylist<AudioItem> = createPlaylistDirectory(name, emptyList())

    override fun createPlaylistDirectory(name: String, audioItems: List<AudioItem>): MutableAudioPlaylist<AudioItem> {
        require(findByName(name).isEmpty) { "Playlist with name '$name' already exists" }
        return MutablePlaylist(newId(), true, name, audioItems).also {
            logger.debug { "Created playlist directory $it" }
            add(it)
        }
    }

    private fun newId(): Int {
        var id: Int
        do {
            id = idCounter.getAndIncrement()
        } while (contains(id))
        return id
    }

    override val audioItemEventSubscriber: TransEventSubscriber<AudioItem, DataEvent<Int, out AudioItem>> =
        AudioItemEventSubscriber<AudioItem>(this.toString()).apply {
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

    override fun add(entity: MutableAudioPlaylist<AudioItem>): Boolean {
        return addInternal(entity)
    }

    private fun addInternal(playlist: MutableAudioPlaylist<AudioItem>): Boolean {
        var added = super.add(playlist)
        serializablePlaylistsRepository.add(playlist.toSerializablePlaylist())
        for (p in playlist.playlists) {
            playlistsHierarchyMultiMap.put(playlist.uniqueId, p)
            added = added or addInternal(p)
        }
        return added
    }

    private fun AudioPlaylist<AudioItem>.toSerializablePlaylist() =
        InternalAudioPlaylist(id, isDirectory, name, audioItems.map { it.id }.toList(), playlists.map { it.id }.toSet())

    override fun addOrReplace(entity: MutableAudioPlaylist<AudioItem>) = addOrReplaceAll(setOf(entity))

    override fun addOrReplaceAll(entities: Set<MutableAudioPlaylist<AudioItem>>): Boolean {
        val entitiesBeforeUpdate = mutableListOf<MutableAudioPlaylist<AudioItem>>()

        val addedAndReplaced = entities.stream().filter { it != null && entitiesById.containsValue(it) }
            .collect(partitioningBy { entity ->
                val entityBefore = entitiesById[entity.id]
                if (entityBefore != null)
                    entitiesBeforeUpdate.add(entityBefore)
                entitiesById[entity.id] = entity
                serializablePlaylistsRepository.addOrReplace(entity.toSerializablePlaylist())
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

    override fun remove(entity: MutableAudioPlaylist<AudioItem>): Boolean {
        return super.remove(entity).also { removed ->
            if (removed) {
                removeFromPlaylistsHierarchy(entity)
                serializablePlaylistsRepository.remove(entity.toSerializablePlaylist())
            }
        }
    }

    private fun removeFromPlaylistsHierarchy(playlist: MutableAudioPlaylist<AudioItem>) {
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

    override fun removeAll(entities: Set<MutableAudioPlaylist<AudioItem>>): Boolean {
        return super.removeAll(entities).also { removed ->
            if (removed) {
                entities.forEach(::removeFromPlaylistsHierarchy)
                serializablePlaylistsRepository.removeAll(entities.toSerializablePlaylists())
            }
        }
    }

    private fun Collection<AudioPlaylist<AudioItem>>.toSerializablePlaylists() = map { it.toSerializablePlaylist() }.toSet()

    override fun findByName(name: String): Optional<MutableAudioPlaylist<AudioItem>> = findFirst { it.name == name }

    override fun findParentPlaylist(playlist: MutableAudioPlaylist<AudioItem>): Optional<MutableAudioPlaylist<AudioItem>> =
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

        findParentPlaylist(playlistToMove.get()).ifPresent { parentPlaylist: MutableAudioPlaylist<AudioItem> ->
            parentPlaylist.removePlaylist(playlistToMove.get())
            logger.debug { "Playlist '$playlistNameToMove' removed from '$parentPlaylist'" }
        }

        destinationPlaylist.get().addPlaylist(playlistToMove.get())
        logger.debug { "Playlist '$playlistNameToMove' moved to '$destinationPlaylistName'" }
    }

    override fun addAudioItemsToPlaylist(audioItems: Collection<AudioItem>, playlistName: String): Boolean {
        return findByName(playlistName).let {
            require(it.isPresent) { "Playlist '$playlistName' does not exist" }
            it.get().addAudioItems(audioItems)
        }
    }

    override fun removeAudioItemsFromPlaylist(audioItems: Collection<AudioItem>, playlistName: String): Boolean {
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

    override fun addPlaylistsToDirectory(playlistsToAdd: Set<MutableAudioPlaylist<AudioItem>>, directoryName: String): Boolean {
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

    override fun removePlaylistsFromDirectory(playlistsToRemove: Set<MutableAudioPlaylist<AudioItem>>, directoryName: String): Boolean {
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

    override fun size(): Int {
        check(entitiesById.size == serializablePlaylistsRepository.size()) { "The size of the repository and its internal serialization is supposed to be the same" }
        return serializablePlaylistsRepository.size()
    }

    override fun entityClone(entity: MutableAudioPlaylist<AudioItem>): MutableAudioPlaylist<AudioItem> =
        MutablePlaylist(entity.id, entity.isDirectory, entity.name, entity.audioItems, entity.playlists)

    override fun toString() = "PlaylistRepository(name=$name, playlistsCount=${entitiesById.size})"

    @Serializable
    internal data class InternalAudioPlaylist(
        override val id: Int,
        val isDirectory: Boolean,
        val name: String,
        val audioItemIds: List<Int>,
        val playlistIds: Set<Int>,
    ) : ReactiveEntity<Int, InternalAudioPlaylist>() {

        override val uniqueId: String
            get() {
                return buildString {
                    if (isDirectory) {
                        append("D")
                    }
                    append(name)
                }
            }
    }

    internal inner class MutablePlaylist(
        override val id: Int,
        isDirectory: Boolean,
        name: String,
        audioItems: List<AudioItem> = listOf(),
        playlists: Set<MutableAudioPlaylist<AudioItem>> = setOf()
    ) : MutableAudioPlaylist<AudioItem> {

        override val audioItems: MutableList<AudioItem> = ArrayList(audioItems)
        override val playlists: MutableSet<MutableAudioPlaylist<AudioItem>> = HashSet(playlists)

        override var isDirectory: Boolean by observable(isDirectory) { _, oldValue, newValue ->
            if (newValue != oldValue) {
                serializeRepository(MutablePlaylist(id, oldValue, name, audioItems, playlists))
                logger.trace { "Playlist $uniqueId changed isDirectory from $oldValue to $newValue" }
            }
        }

        private fun serializeRepository(playlistBeforeBeingUpdated: MutablePlaylist) {
            serializablePlaylistsRepository.addOrReplace(this.toSerializablePlaylist())
            putUpdateEvent(this, playlistBeforeBeingUpdated)
        }

        override var name: String by observable(name) { _, oldValue, newValue ->
            require(findByName(newValue).isPresent) { "Playlist with name '$newValue' already exists" }
            if (newValue != oldValue) {
                serializeRepository(MutablePlaylist(id, isDirectory, oldValue, audioItems, playlists))
                logger.trace { "Playlist $uniqueId changed name from $oldValue to $newValue" }
            }
        }

        override fun addAudioItems(audioItems: Collection<AudioItem>): Boolean {
            val audioItemsBeforeAddition = ArrayList(this.audioItems)
            return this.audioItems.addAll(audioItems).also {
                if (it) {
                    serializeRepository(MutablePlaylist(id, isDirectory, name, audioItemsBeforeAddition, playlists))
                    logger.debug { "Added $audioItems to playlist $uniqueId" }
                }
            }
        }

        override fun removeAudioItems(audioItems: Collection<AudioItem>): Boolean {
            val audioItemsBeforeRemoval = ArrayList(this.audioItems)
            return this.audioItems.removeAll(audioItems).also {
                if (it) {
                    serializeRepository(MutablePlaylist(id, isDirectory, name, audioItemsBeforeRemoval, playlists))
                    logger.debug { "Removed $audioItems from playlist $uniqueId" }
                }
            }
        }

        @Suppress("INAPPLICABLE_JVM_NAME")
        @JvmName("removeAudioItemIds")
        override fun removeAudioItems(audioItemIds: Collection<Int>): Boolean {
            val audioItemsBeforeRemoval = ArrayList(audioItems)
            return this.audioItems.removeIf { audioItem -> audioItemIds.contains(audioItem.id) }.also {
                if (it) {
                    serializeRepository(MutablePlaylist(id, isDirectory, name, audioItemsBeforeRemoval, playlists))
                    logger.debug { "Removed audio items with ids $audioItemIds from playlist $uniqueId" }
                }
            }
        }

        override fun addPlaylists(playlists: Collection<MutableAudioPlaylist<AudioItem>>): Boolean {
            playlists.forEach {
                findParentPlaylist(it).ifPresent { parentPlaylist: MutableAudioPlaylist<AudioItem> ->
                    parentPlaylist.removePlaylist(it)
                    logger.debug { "Playlist '${it.name}' removed from '$parentPlaylist'" }
                }
            }
            val playlistsBeforeAddition = HashSet(this.playlists)
            return this.playlists.addAll(playlists).also {
                if (it) {
                    serializeRepository(MutablePlaylist(id, isDirectory, name, audioItems, playlistsBeforeAddition))
                    playlistsHierarchyMultiMap.putAll(uniqueId, playlists)
                    logger.debug { "Added $playlists to playlist $uniqueId" }
                }
            }
        }

        override fun removePlaylists(playlists: Collection<MutableAudioPlaylist<AudioItem>>): Boolean {
            val playlistsBeforeRemoval = HashSet(this.playlists)
            return this.playlists.removeAll(playlists.toSet()).also {
                if (it) {
                    serializeRepository(MutablePlaylist(id, isDirectory, name, audioItems, playlistsBeforeRemoval))
                    playlists.forEach { playlist ->
                        playlistsHierarchyMultiMap.remove(uniqueId, playlist)
                    }
                    logger.debug { "Removed $playlists from playlist $uniqueId" }
                }
            }
        }

        @Suppress("INAPPLICABLE_JVM_NAME")
        @JvmName("removePlaylistIds")
        override fun removePlaylists(playlistIds: Collection<Int>): Boolean {
            val playlistsBeforeRemoval = HashSet(this.playlists)
            return this.playlists.removeIf { playlist -> playlistIds.contains(playlist.id) }.also {
                if (it) {
                    serializeRepository(MutablePlaylist(id, isDirectory, name, audioItems, playlistsBeforeRemoval))
                    playlistIds.forEach { playlistId ->
                        findById(playlistId).ifPresent { playlist ->
                            playlistsHierarchyMultiMap.remove(uniqueId, playlist)
                        }
                    }
                    logger.debug { "Removed playlists with ids $playlistIds from playlist $uniqueId" }
                }
            }
        }

        override fun clearAudioItems() {
            if (audioItems.isNotEmpty()) {
                val audioItemsCopy = ArrayList(audioItems)
                val audioItemsSize = audioItems.size
                audioItems.clear()
                serializeRepository(MutablePlaylist(id, isDirectory, name, audioItemsCopy, playlists))
                logger.debug { "Cleared $audioItemsSize audio items from playlist $uniqueId" }
            }
        }

        override fun clearPlaylists() {
            if (playlists.isNotEmpty()) {
                val playlistsCopy = HashSet(playlists)
                val playlistSize = playlists.size
                playlists.clear()
                serializeRepository(MutablePlaylist(id, isDirectory, name, audioItems, playlistsCopy))
                logger.debug { "Cleared $playlistSize playlists from playlist $uniqueId" }
            }
        }

        override fun toImmutableAudioPlaylist() = ImmutablePlaylist(id, isDirectory, name, audioItems.toList(), playlists.toSet())

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as MutablePlaylist

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