package net.transgressoft.commons.music.playlist

import com.google.common.collect.Multimap
import com.google.common.collect.MultimapBuilder
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import mu.KotlinLogging
import net.transgressoft.commons.IdentifiableEntity
import net.transgressoft.commons.data.DataEvent
import net.transgressoft.commons.data.JsonFileRepository
import net.transgressoft.commons.data.RepositoryBase
import net.transgressoft.commons.data.StandardDataEvent
import net.transgressoft.commons.event.TransEventSubscriber
import net.transgressoft.commons.music.audio.AudioItem
import net.transgressoft.commons.music.audio.AudioItemManipulationException
import net.transgressoft.commons.music.audio.AudioItemRepository
import net.transgressoft.commons.music.audio.UNASSIGNED_ID
import net.transgressoft.commons.music.event.AudioItemEventSubscriber
import net.transgressoft.commons.toIds
import java.io.File
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.stream.Collectors
import java.util.stream.Collectors.partitioningBy

class AudioPlaylistJsonRepository(jsonFile: File) : RepositoryBase<MutableAudioPlaylist<AudioItem>, Int>(),
    AudioPlaylistRepository<AudioItem, MutableAudioPlaylist<AudioItem>> {

    private val logger = KotlinLogging.logger {}

    private val idCounter: AtomicInteger = AtomicInteger(1)
    private val playlistsMultiMap: Multimap<String, String> = MultimapBuilder.treeKeys().treeSetValues().build()
    private val serializablePlaylistsRepository = JsonFileRepository(jsonFile, Int.serializer(), InternalAudioPlaylist.serializer())

    constructor(file: File, audioItemRepository: AudioItemRepository<AudioItem>) : this(file) {
        require(file.exists().and(file.canWrite()).and(file.extension == "json")) {
            "Provided jsonFile does not exist, is not writable or is not a json file"
        }

        serializablePlaylistsRepository.runMatching({ true }) {
            val playlistsWithAudioItems = MutablePlaylist(it.id, it.isDirectory, it.name, mapAudioItemsFromIds(it.audioItemIds, audioItemRepository))
            entitiesById[it.id] = playlistsWithAudioItems
        }
        serializablePlaylistsRepository.runMatching({ true }) {
            val playlistsMissingPlaylists = entitiesById[it.id] ?: throw AudioItemManipulationException("AudioPlaylist with id ${it.id} not found during deserialization")
            val foundPlaylists = findDeserializedPlaylistsFromIds(it.playlistIds, entitiesById)
            playlistsMissingPlaylists.playlists.addAll(foundPlaylists)
        }
    }

    private fun mapAudioItemsFromIds(audioItemIds: List<Int>, audioItemRepository: AudioItemRepository<AudioItem>) =
        audioItemIds.map {
            audioItemRepository.findById(it).orElseThrow { AudioItemManipulationException("AudioItem with id $it not found during deserialization") }
        }.toList()

    private fun findDeserializedPlaylistsFromIds(playlists: Set<Int>, playlistsById: Map<Int, MutableAudioPlaylist<AudioItem>>): List<MutableAudioPlaylist<AudioItem>> {
        return playlists.stream().map {
            return@map playlistsById[it] ?: throw AudioItemManipulationException("AudioPlaylist with id $it not found during deserialization")
        }.toList()
    }

    override fun addPlaylist(playlist: AudioPlaylist<AudioItem>): MutableAudioPlaylist<AudioItem> {
        require(findByName(playlist.name).isEmpty) { "Playlist with name '${playlist.name}' already exists" }

        var playlistId = playlist.id
        val playlistToAdd = if (playlist.id <= UNASSIGNED_ID) {
            playlist.toMutablePlaylist().apply { id = newId() }.also { playlistId = it.id }
        } else {
            playlist.toMutablePlaylist()
        }
        assert(add(playlistToAdd)) { "Result of addition of new playlist is expected to be true" }
        return findById(playlistId).get()
    }

    private fun newId(): Int {
        var id: Int
        do {
            id = idCounter.getAndIncrement()
        } while (contains(id))
        return id
    }

    override val audioItemEventSubscriber: TransEventSubscriber<AudioItem, DataEvent<out AudioItem>> =
        AudioItemEventSubscriber<AudioItem>(this.toString()).apply {
            addOnNextEventAction(StandardDataEvent.Type.DELETE) {
                removeAudioItems(it.entities.toIds())
            }
        }

    override fun add(entity: MutableAudioPlaylist<AudioItem>): Boolean {
        return addInternal(entity)
    }

    private fun addInternal(playlist: MutableAudioPlaylist<AudioItem>): Boolean {
        var added = super.add(playlist)
        serializablePlaylistsRepository.add(playlist.toSerializablePlaylist())
        for (p in playlist.playlists) {
            playlistsMultiMap.put(playlist.uniqueId, p.uniqueId)
            added = added or addInternal(p)
        }
        return added
    }

    private fun AudioPlaylist<AudioItem>.toSerializablePlaylist() =
        InternalAudioPlaylist(id, isDirectory, name, audioItems.map { it.id }.toList(), playlists.map { it.id }.toSet())

    override fun addOrReplace(entity: MutableAudioPlaylist<AudioItem>) = addOrReplaceAll(setOf(entity))

    override fun addOrReplaceAll(entities: Set<MutableAudioPlaylist<AudioItem>>): Boolean {
        val addedAndReplaced = getAddedOrReplacedPlaylists(entities)

        addedAndReplaced[true]?.let {
            if (it.isNotEmpty()) {
                putCreateEvent(it)
                logger.debug { "${it.size} entities were added: $it" }
            }
        }
        addedAndReplaced[false]?.let {
            if (it.isNotEmpty()) {
                putUpdateEvent(it)
                logger.debug { "${it.size} entities were replaced: $it" }
            }
        }

        return addedAndReplaced.values.stream().flatMap { it.stream() }.findAny().isPresent
    }

    private fun getAddedOrReplacedPlaylists(entities: Set<MutableAudioPlaylist<AudioItem>>): Map<Boolean, List<MutableAudioPlaylist<AudioItem>>> {
        return entities.stream().filter { it != null && entitiesById.containsValue(it) }
            .collect(partitioningBy {
                val result = !entitiesById.containsKey(it.id)
                entitiesById[it.id] = it
                serializablePlaylistsRepository.addOrReplace(it.toSerializablePlaylist())
                return@partitioningBy result
            })
    }

    override fun remove(entity: MutableAudioPlaylist<AudioItem>): Boolean {
        return super.remove(entity).also {
            serializablePlaylistsRepository.remove(entity.toSerializablePlaylist())
        }
    }

    override fun removeAll(entities: Set<MutableAudioPlaylist<AudioItem>>): Boolean {
        return super.removeAll(entities).also {
            serializablePlaylistsRepository.removeAll(entities.toSerializablePlaylists())
        }
    }

    private fun Collection<AudioPlaylist<AudioItem>>.toSerializablePlaylists() = map { it.toSerializablePlaylist() }.toSet()

    override fun removeAudioItems(audioItemIds: Set<Int>) {
        runMatching({ true }) {
            it.audioItems.removeIf { audioItem -> audioItemIds.contains(audioItem.id) }
        }
    }

    override fun removeAudioItems(audioItems: Collection<AudioItem>) {
        runMatching({ true }) {
            it.audioItems.removeIf { audioItem -> audioItems.contains(audioItem) }
        }
    }

    override fun findByName(name: String): Optional<MutableAudioPlaylist<AudioItem>> = find { it.name == name }

    override fun numberOfPlaylists() =
        entitiesById.values.stream()
            .filter { it.isDirectory.not() }
            .count().toInt()

    override fun numberOfPlaylistDirectories() =
        entitiesById.values.stream()
            .filter { it.isDirectory }
            .count().toInt()

    override fun findParentPlaylist(playlist: MutableAudioPlaylist<AudioItem>): Optional<MutableAudioPlaylist<AudioItem>> =
        if (playlistsMultiMap.containsValue(playlist.uniqueId)) {
            playlistsMultiMap.entries().stream()
                .filter { playlist.uniqueId == it.value }
                .map { findByUniqueId(it.key).get() }
                .findFirst()
        } else {
            Optional.empty()
        }

    override fun movePlaylist(playlistToMove: MutableAudioPlaylist<AudioItem>, destinationPlaylist: MutableAudioPlaylist<AudioItem>) {
        findById(playlistToMove.id)
            .ifPresent { playlist: MutableAudioPlaylist<AudioItem> ->
                findById(destinationPlaylist.id).ifPresent { playlistDirectory: MutableAudioPlaylist<AudioItem> ->
                    findParentPlaylist(playlistToMove).ifPresent { ancestor: MutableAudioPlaylist<AudioItem> ->
                        playlistsMultiMap.remove(ancestor.uniqueId, playlist.uniqueId)
                        ancestor.playlists.remove(playlist)
                        serializablePlaylistsRepository.addOrReplace(ancestor.toSerializablePlaylist())
                    }
                    playlistsMultiMap.put(playlistDirectory.uniqueId, playlist.uniqueId)
                    playlistDirectory.playlists.add(playlist)
                    serializablePlaylistsRepository.addOrReplace(playlistDirectory.toSerializablePlaylist())
                    logger.debug { "Playlist '${playlistToMove.name}' moved to '${destinationPlaylist.name}'" }
                }
            }
    }

    override fun removeAudioItemsFromPlaylist(audioItems: Collection<AudioItem>, playlist: MutableAudioPlaylist<AudioItem>) {
        entitiesById[playlist.id]?.audioItems?.removeAll(audioItems)
    }

    override fun addPlaylistsToDirectory(playlistsToAdd: Set<MutableAudioPlaylist<AudioItem>>, directory: MutableAudioPlaylist<AudioItem>) {
        entitiesById[directory.id]?.let {
            if (it.isDirectory) {
                it.playlists.addAll(playlistsToAdd)
                playlistsMultiMap.putAll(
                    it.uniqueId,
                    playlistsToAdd.stream().map { d -> d.uniqueId }.collect(Collectors.toSet())
                )
            }
        }
    }

    override fun addAudioItemsToPlaylist(audioItems: Collection<AudioItem>, playlist: MutableAudioPlaylist<AudioItem>) {
        entitiesById[playlist.id]?.audioItems?.addAll(audioItems)
    }

    override fun size(): Int {
        return serializablePlaylistsRepository.size()
    }

    @Serializable
    internal data class InternalAudioPlaylist(
        override val id: Int,
        val isDirectory: Boolean,
        val name: String,
        val audioItemIds: List<Int>,
        val playlistIds: Set<Int>,
    ) : IdentifiableEntity<Int> {

        override val uniqueId: String
            get() {
                return buildString {
                    append(id)
                    if (isDirectory) {
                        append("-D")
                    }
                    append("-$name")
                }
            }
    }
}