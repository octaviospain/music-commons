package net.transgressoft.commons.music.playlist

import com.google.common.collect.Multimap
import com.google.common.collect.MultimapBuilder
import mu.KotlinLogging
import net.transgressoft.commons.event.QueryEntitySubscriber
import net.transgressoft.commons.event.QueryEventPublisherBase
import net.transgressoft.commons.music.audio.AudioItem
import net.transgressoft.commons.music.event.AudioItemEventSubscriber
import net.transgressoft.commons.query.InMemoryRepository
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Predicate
import java.util.stream.Collectors

private class MutableAudioPlaylistInMemoryRepository<I : AudioItem>(playlistsById: MutableMap<Int, MutableAudioPlaylist<I>>) :
    InMemoryRepository<MutableAudioPlaylist<I>>(playlistsById) {

        // TODO fix the subscription to AudioPlaylist<I> to MutableAudioPlaylist<I>
//        override fun putCreateEvent(entities: Collection<MutableAudioPlaylist<I>>) {
//
//        }
}

abstract class AudioPlaylistInMemoryRepositoryBase<I : AudioItem, P : AudioPlaylist<I>>
protected constructor(
    playlistsById: MutableMap<Int, P>,
) : QueryEventPublisherBase<P>(), AudioPlaylistRepository<I, P> {

    private val logger = KotlinLogging.logger {}

    private val idCounter = AtomicInteger(1)
    private val idSet: MutableSet<Int> = HashSet()
    private val playlists = MutableAudioPlaylistInMemoryRepository(playlistsById.mapValues { it.value.toMutablePlaylist() }.toMutableMap())
    private val playlistsMultiMap: Multimap<String, String> = MultimapBuilder.treeKeys().treeSetValues().build()

    override val audioItemEventSubscriber: QueryEntitySubscriber<I> = AudioItemEventSubscriber()

    private fun getNewId(): Int {
        var id: Int
        do {
            id = idCounter.getAndIncrement()
        } while (idSet.contains(id))
        idSet.add(id)
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

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val that = other as AudioPlaylistInMemoryRepositoryBase<*, *>
        return playlistsMultiMap == that.playlistsMultiMap && playlists == that.playlists
    }

    override fun hashCode(): Int {
        return Objects.hash(playlistsMultiMap, playlists)
    }
}