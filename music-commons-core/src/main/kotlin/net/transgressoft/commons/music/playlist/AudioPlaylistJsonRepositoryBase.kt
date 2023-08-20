package net.transgressoft.commons.music.playlist

import com.google.common.collect.Multimap
import com.google.common.collect.MultimapBuilder
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import net.transgressoft.commons.event.*
import net.transgressoft.commons.music.audio.AudioItem
import net.transgressoft.commons.music.audio.AudioItemManipulationException
import net.transgressoft.commons.music.audio.AudioItemRepository
import net.transgressoft.commons.music.event.AudioItemEventSubscriber
import net.transgressoft.commons.music.playlist.AudioPlaylistJsonRepositoryBase.*
import net.transgressoft.commons.query.InMemoryRepository
import java.io.File
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.Flow
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Predicate
import java.util.stream.Collectors
import kotlin.streams.toList

abstract class AudioPlaylistJsonRepositoryBase<I : AudioItem, P : AudioPlaylist<I>>(private val jsonFile: File? = null) :
    QueryEntityPublisherBase<P>(), AudioPlaylistRepository<I, P> {

    private val logger = KotlinLogging.logger(javaClass.name)

    private val playlistsById: MutableMap<Int, MutableAudioPlaylist<I>> = ConcurrentHashMap()

    private val idCounter = AtomicInteger(1)
    private val playlistsMultiMap: Multimap<String, String> = MultimapBuilder.treeKeys().treeSetValues().build()
    private val playlists = InMemoryRepository(playlistsById).also {
        it.subscribe(object : Flow.Subscriber<EntityEvent<out MutableAudioPlaylist<I>>> {

            override fun onSubscribe(subscription: Flow.Subscription) =
                logger.debug { "Internal playlists subscribed to MutableAudioPlaylist events" }

            override fun onError(throwable: Throwable) = logger.error("An error occurred on the internal playlists subscriber", throwable)

            override fun onComplete() = logger.debug { "Internal playlists subscriber completed the subscription" }

            override fun onNext(item: EntityEvent<out MutableAudioPlaylist<I>>) {
                val audioPlaylists = item.entities.map(::toAudioPlaylist).toSet()
                when {
                    item.isCreate() -> {
                        this@AudioPlaylistJsonRepositoryBase.putCreateEvent(audioPlaylists)
                        serializeToJson()
                    }

                    item.isRead() -> {
                        this@AudioPlaylistJsonRepositoryBase.putReadEvent(audioPlaylists)
                    }

                    item.isUpdate() -> {
                        this@AudioPlaylistJsonRepositoryBase.putUpdateEvent(audioPlaylists)
                        serializeToJson()
                    }

                    item.isDelete() -> {
                        this@AudioPlaylistJsonRepositoryBase.putDeleteEvent(audioPlaylists)
                        serializeToJson()
                    }
                }
            }
        })
    }


    private val executorService by lazy {
        Executors.newFixedThreadPool(1) { runnable ->
            Thread(runnable).apply {
                isDaemon = true
                name = "PlaylistJsonFileRepository-$jsonFile"
                setUncaughtExceptionHandler { thread, exception ->
                    logger.error(exception) { "Error in thread $thread" }
                }
            }
        }
    }

    private val json by lazy { Json { prettyPrint = true } }

    init {
        require(
            jsonFile?.exists()?.and((jsonFile.canWrite()).and(jsonFile.extension == "json"))
                ?: true
        ) {
            "Provided jsonFile does not exist, is not writable or is not a json file"
        }
    }

    override val audioItemEventSubscriber: QueryEntitySubscriber<I> = AudioItemEventSubscriber<I>(this.toString()).apply {
        addOnNextEventAction(QueryEntityEvent.Type.DELETE) {
            removeAudioItems(it.entities)
            serializeToJson()
        }
    }

    private fun getNewId(): Int {
        var id: Int
        do {
            id = idCounter.getAndIncrement()
        } while (playlistsById.containsKey(id))
        return id
    }

    override fun add(entity: P) = addInternal(entity)

    private fun addInternal(playlistToAdd: AudioPlaylist<I>): Boolean {
        var added = false
        playlists.add(playlistToAdd.toMutablePlaylist())
        for (p in playlistToAdd.playlists) {
            playlistsMultiMap.put(playlistToAdd.uniqueId, p.uniqueId)
            added = added or addInternal(p)
        }
        return added
    }

    override fun addOrReplace(entity: P) = addOrReplaceAll(setOf(entity))

    override fun addOrReplaceAll(entities: Set<P>): Boolean {
        val added = AtomicBoolean(false)
        entities.forEach { added.set(addOrReplaceInternal(it) || added.get()) }
        return added.get()
    }

    private fun addOrReplaceInternal(playlistToAdd: AudioPlaylist<I>): Boolean {
        return if (playlists.contains(Predicate<MutableAudioPlaylist<I>> { it.name == playlistToAdd.name })) {
            replace(playlistToAdd)
            true
        } else {
            addInternal(playlistToAdd)
        }
    }

    private fun replace(playlistToReplace: AudioPlaylist<I>) {
        val existing = playlists.find { it.name == playlistToReplace.name }!!
        existing.playlists.clear()
        existing.playlists.addAll(playlistToReplace.playlists)
        existing.audioItems.clear()
        existing.audioItems.addAll(playlistToReplace.audioItems)
        existing.isDirectory = playlistToReplace.isDirectory
    }

    override fun remove(entity: P) = removeAll(setOf(entity))

    override fun removeAll(entities: Set<P>) = removeRecursive(entities)

    private fun removeRecursive(playlistsToRemove: Set<AudioPlaylist<I>>): Boolean {
        val result = AtomicBoolean(false)
        for (p in playlistsToRemove) {
            playlistsMultiMap.asMap().remove(p.uniqueId)
            if (p.isDirectory) {
                result.set(removeRecursive(p.playlists) || result.get())
            }
            playlists.findById(p.id).ifPresent { result.set(playlists.remove(it) || result.get()) }
        }
        return result.get()
    }

    override fun clear() {
        playlists.clear()
        playlistsMultiMap.clear()
    }

    override operator fun contains(id: Int) = playlists.contains(id)

    override fun contains(predicate: Predicate<P>) =
        playlists.filter { predicate.test(toAudioPlaylist(it)) }
            .map { toAudioPlaylist(it) }
            .isNotEmpty()

    override fun search(predicate: Predicate<P>): List<P> =
        playlists.filter { predicate.test(toAudioPlaylist(it)) }
            .map { toAudioPlaylist(it) }
            .toList()

    override fun findById(id: Int): Optional<P> = playlists.findById(id).map { toAudioPlaylist(it) }

    override fun findByUniqueId(uniqueId: String): Optional<P> = playlists.findByUniqueId(uniqueId).map { toAudioPlaylist(it) }

    protected abstract fun toAudioPlaylist(mutableAudioPlaylist: MutableAudioPlaylist<I>): P

    override fun size() = playlists.size()

    override val isEmpty = playlists.isEmpty

    /**
     * Some of the underlying items returned by the iterator can be cast to a <tt>MutablePlaylist</tt> or a <tt>MutablePlaylistDirectory</tt>,
     * mutable operations on them should be avoided since changes won't be reflected in the repository correctly. This bug should be fixed.
     */
    override fun iterator(): Iterator<P> = playlists.map { toAudioPlaylist(it) }.iterator()

    override fun numberOfPlaylists() = playlists.size()

    override fun numberOfPlaylistDirectories() = playlists.search { it.isDirectory }.size

    @Throws(AudioPlaylistRepositoryException::class)
    override fun createPlaylist(name: String) = createPlaylist(name, emptyList())

    @Throws(AudioPlaylistRepositoryException::class)
    override fun createPlaylist(name: String, audioItems: List<I>): P =
        if (findByName(name) == null) {
            val newPlaylist = createMutablePlaylist(getNewId(), false, name, audioItems)
            playlists.add(newPlaylist)
            toAudioPlaylist(newPlaylist)
        } else {
            throw AudioPlaylistRepositoryException("Playlist with name '$name' already exists")
        }

    @Throws(AudioPlaylistRepositoryException::class)
    override fun createPlaylistDirectory(name: String) = createPlaylistDirectory(name, emptyList())

    @Throws(AudioPlaylistRepositoryException::class)
    override fun createPlaylistDirectory(name: String, audioItems: List<I>): P =
        if (findByName(name) == null) {
            val newPlaylist = createMutablePlaylist(getNewId(), true, name, audioItems)
            playlists.add(newPlaylist)
            toAudioPlaylist(newPlaylist)
        } else {
            throw AudioPlaylistRepositoryException("Playlist with name '$name' already exists")
        }

    protected abstract fun createMutablePlaylist(id: Int, isDirectory: Boolean, name: String, audioItems: List<I>): MutableAudioPlaylist<I>

    override fun addAudioItemsToPlaylist(audioItems: Collection<I>, playlist: P) {
        playlists.findById(playlist.id)
            .ifPresent { it.audioItems.addAll(audioItems) }
    }

    override fun addPlaylistsToDirectory(playlistsToAdd: Set<P>, directory: P) {
        playlists.findById(directory.id)
            .ifPresent {
                if (it.isDirectory) {
                    it.playlists.addAll(playlistsToAdd)
                    playlistsMultiMap.putAll(
                        it.uniqueId,
                        playlistsToAdd.stream().map { d -> d.uniqueId }.collect(Collectors.toSet())
                    )
                }
            }
    }

    override fun movePlaylist(playlistToMove: P, destinationPlaylist: P) {
        findById(playlistToMove.id)
            .ifPresent { playlist: P ->
                playlists.findById(destinationPlaylist.id).ifPresent { playlistDirectory: MutableAudioPlaylist<I> ->
                    findParentMutablePlaylist(playlistToMove).ifPresent { ancestor: MutableAudioPlaylist<I> ->
                        playlistsMultiMap.remove(ancestor.uniqueId, playlist.uniqueId)
                        ancestor.playlists.remove(playlist)
                    }
                    playlistsMultiMap.put(playlistDirectory.uniqueId, playlist.uniqueId)
                    playlistDirectory.playlists.add(playlist)
                    serializeToJson()
                    logger.debug { "Playlist '${playlistToMove.name}' moved to '${destinationPlaylist.name}'" }
                }
            }
    }

    private fun findParentMutablePlaylist(playlistNode: P): Optional<MutableAudioPlaylist<I>> =
        if (playlistsMultiMap.containsValue(playlistNode.uniqueId)) {
            playlistsMultiMap.entries().stream()
                .filter { playlistNode.uniqueId == it.value }
                .map { playlists.findByUniqueId(it.key).get() }
                .findFirst()
        } else {
            Optional.empty()
        }

    override fun removeAudioItemsFromPlaylist(audioItems: Collection<I>, playlist: P) =
        playlists.findById(playlist.id)
            .ifPresent { it.audioItems.removeAll(audioItems) }

    override fun removeAudioItems(audioItems: Collection<I>) = playlists.forEach { it.audioItems.removeAll(audioItems) }

    override fun findByName(name: String): P? = playlists.search { it.name == name }.let {
        if (it.isNotEmpty()) {
            assert(it.size == 1)
            toAudioPlaylist(it[0])
        } else null
    }

    override fun findParentPlaylist(playlist: P): P? = findParentMutablePlaylist(playlist).map { toAudioPlaylist(it) }.orElse(null)

    private fun serializeToJson() {
        jsonFile?.run {
            executorService.execute {
                json.encodeToString(ListSerializer(InternalAudioPlaylist.serializer()), mapToSerializablePlaylists(playlistsById.values)).let {
                    this@run.writeText(it)
                    logger.debug { "PlaylistRepository serialized to file $jsonFile" }
                }
            }
        }
    }

    private fun mapToSerializablePlaylists(audioPlaylists: MutableCollection<MutableAudioPlaylist<I>>): List<InternalAudioPlaylist> =
        audioPlaylists.stream().map {
            InternalAudioPlaylist(it.id,
                it.isDirectory,
                it.name,
                it.audioItems.map { audioItem -> audioItem.id }.toList(),
                it.playlists.map { playlist -> playlist.id }.toSet()
            )
        }.toList()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val that = other as AudioPlaylistJsonRepositoryBase<*, *>
        return playlistsMultiMap == that.playlistsMultiMap && playlists == that.playlists
    }

    override fun hashCode() = Objects.hash(playlistsMultiMap, playlists)

    override fun toString() = "PlaylistRepository[${this.hashCode()}]"

    @Serializable
    internal data class InternalAudioPlaylist(
        val id: Int,
        val isDirectory: Boolean,
        val name: String,
        val audioItemIds: List<Int>,
        val playlistIds: Set<Int>
    )
}

internal fun mapFromSerializablePlaylists(
    deserializedPlaylists: List<InternalAudioPlaylist>,
    audioItemRepository: AudioItemRepository<AudioItem>
): Map<Int, AudioPlaylist<AudioItem>> {
    val playlistsById = deserializedPlaylists
        .map { MutablePlaylist(it.id, it.isDirectory, it.name, mapAudioItemsFromIds(it.audioItemIds, audioItemRepository)) }
        .associateByTo(mutableMapOf()) { it.id }

    deserializedPlaylists.forEach {
        val foundPlaylists = findDeserializedPlaylistsFromIds(it.playlistIds, playlistsById)
        playlistsById[it.id]!!.playlists.addAll(foundPlaylists)
    }
    return playlistsById
}

internal fun mapAudioItemsFromIds(audioItemIds: List<Int>, audioItemRepository: AudioItemRepository<AudioItem>) =
    audioItemIds.map {
        audioItemRepository.findById(it).orElseThrow { AudioItemManipulationException("AudioItem with id $it not found during deserialization") }
    }.toList()

internal fun findDeserializedPlaylistsFromIds(
    playlists: Set<Int>,
    playlistsById: Map<Int, AudioPlaylist<AudioItem>>
): List<AudioPlaylist<AudioItem>> {
    return playlists.stream().map {
        return@map playlistsById[it] ?: throw AudioItemManipulationException("AudioPlaylist with id $it not found during deserialization")
    }.toList()
}