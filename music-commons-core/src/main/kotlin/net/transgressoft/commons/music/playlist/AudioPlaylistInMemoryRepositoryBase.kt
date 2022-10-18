package net.transgressoft.commons.music.playlist

import com.google.common.collect.Multimap
import com.google.common.collect.MultimapBuilder
import mu.KotlinLogging
import net.transgressoft.commons.event.QueryEntitySubscriber
import net.transgressoft.commons.music.audio.AudioItem
import net.transgressoft.commons.music.event.AudioItemEventSubscriber
import net.transgressoft.commons.query.InMemoryRepository
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Predicate
import java.util.stream.Collectors

abstract class AudioPlaylistInMemoryRepositoryBase<I : AudioItem, P : AudioPlaylist<I>>
protected constructor(
    playlistsById: MutableMap<Int, P>,
) : AudioPlaylistRepository<I, P> {

    private val logger = KotlinLogging.logger {}

    private val idCounter = AtomicInteger(1)
    private val idSet: MutableSet<Int> = HashSet()
    private val playlists = InMemoryRepository(playlistsById.mapValues { it.value.toMutablePlaylist() }.toMutableMap() )
    private val playlistsMultiMap: Multimap<String, String> = MultimapBuilder.treeKeys().treeSetValues().build()

    override val audioItemEventSubscriber: QueryEntitySubscriber<I> = AudioItemEventSubscriber()

    protected fun getNewId(): Int {
        var id: Int
        do {
            id = idCounter.getAndIncrement()
        } while (idSet.contains(id))
        idSet.add(id)
        return id
    }

    override fun add(entity: P) = addOrReplaceAll(setOf(entity))

    override fun addOrReplace(entity: P) = addOrReplaceAll(setOf(entity))

    override fun addOrReplaceAll(entities: Set<P>): Boolean {
        val added = AtomicBoolean(false)
        entities.forEach { added.set(added.get() || addInternal(it)) }
        return added.get()
    }

    private fun addInternal(playlistToAdd: AudioPlaylist<I>): Boolean {
        var added = false
        playlists.add(playlistToAdd.toMutablePlaylist())
        for (p in playlistToAdd.playlists) {
            playlistsMultiMap.put(playlistToAdd.uniqueId, p.uniqueId)
            added = added or addInternal(p)
        }
        return added
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
        playlists.filter { predicate.test(it.toAudioPlaylist() as P) }
            .map { it as P }
            .isNotEmpty()

    override fun search(predicate: Predicate<P>): List<P> =
        playlists.filter { predicate.test(it.toAudioPlaylist() as P) }
            .map { it as P }
            .toList()

    override fun findById(id: Int): Optional<P> = playlists.findById(id).map { it.toAudioPlaylist() as P }

    override fun findByUniqueId(uniqueId: String): Optional<P> = playlists.findByUniqueId(uniqueId).map { it.toAudioPlaylist() as P }

    override fun size() = playlists.size()

    override val isEmpty = playlists.isEmpty

    override fun iterator(): Iterator<P> = playlists.map { it.toAudioPlaylist() as P }.iterator()

    override fun numberOfPlaylists() = playlists.size()

    override fun numberOfPlaylistDirectories() = playlists.search { it.isDirectory }.size

    @Throws(AudioPlaylistRepositoryException::class)
    override fun createPlaylist(name: String) = createPlaylist(name, emptyList())

    @Throws(AudioPlaylistRepositoryException::class)
    override fun createPlaylist(name: String, audioItems: List<I>): P =
        if (findByName(name) == null) {
            val newPlaylist = MutablePlaylist(getNewId(), false, name, audioItems.toMutableList())
            playlists.add(newPlaylist)
            newPlaylist.toAudioPlaylist() as P
        } else {
            throw AudioPlaylistRepositoryException("Playlist with name '$name' already exists")
        }

    @Throws(AudioPlaylistRepositoryException::class)
    override fun createPlaylistDirectory(name: String) = createPlaylistDirectory(name, emptyList())

    @Throws(AudioPlaylistRepositoryException::class)
    override fun createPlaylistDirectory(name: String, audioItems: List<I>): P =
        if (findByName(name) == null) {
            val newPlaylist = MutablePlaylist(getNewId(), true, name, audioItems.toMutableList())
            playlists.add(newPlaylist)
            newPlaylist.toAudioPlaylist() as P
        } else {
            throw AudioPlaylistRepositoryException("Playlist with name '$name' already exists")
        }

    override fun addAudioItemsToPlaylist(audioItems: Collection<I>, playlist: P) {
        playlists.findById(playlist.id)
            .ifPresent { it.audioItems.addAll(audioItems) }
    }

    override fun addPlaylistsToDirectory(playlist: Set<P>, directory: P) {
        val mutablePlaylists: Set<MP> = toMutablePlaylists(playlist)
        directories.findById(directory.id)
            .ifPresent {
                it.addPlaylists(mutablePlaylists as Set<P>)
                playlistsMultiMap.putAll(
                    it.uniqueId,
                    mutablePlaylists.stream().map { d -> d.uniqueId }.collect(Collectors.toSet())
                )
            }
    }

    override fun movePlaylist(playlistToMove: P, destinationPlaylist: D) {
        findByIdInternal(playlistToMove.id)
            .ifPresent { playlist: MP ->
                directories.findById(destinationPlaylist.id).ifPresent { playlistDirectory: MD ->
                    ancestor(playlistToMove).ifPresent { ancestor: MD ->
                        playlistsMultiMap.remove(ancestor.uniqueId, playlist.uniqueId)
                        ancestor.removePlaylists(playlist as P)
                    }
                    playlistsMultiMap.put(playlistDirectory.uniqueId, playlist.uniqueId)
                    playlistDirectory.addPlaylists(playlist as P)
                    logger.debug { "Playlist '${playlistToMove.name}' moved to '${destinationPlaylist.name}'" }
                }
            }
    }

    private fun ancestor(playlistNode: P): Optional<P> =
        if (playlistsMultiMap.containsValue(playlistNode.uniqueId)) {
            playlistsMultiMap.entries().stream()
                .filter { playlistNode.uniqueId == it.value }
                .map { playlists.findByUniqueId(it.key).get() as P }
                .findFirst()
        } else {
            Optional.empty()
        }

    override fun removeAudioItemsFromPlaylist(audioItems: Collection<I>, playlist: P) =
        playlists.findById(playlist.id)
            .ifPresent { it.audioItems.removeAll(audioItems) }

    override fun removeAudioItems(audioItems: Collection<I>) = playlists.forEach { it.audioItems.removeAll(audioItems) }

    override fun findByName(name: String): P? = playlists.search { it.name == name } as P?

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val that = other as AudioPlaylistInMemoryRepositoryBase<I, P>
        return playlistsMultiMap == that.playlistsMultiMap && playlists == that.playlists
    }

    override fun hashCode(): Int {
        return Objects.hash(playlistsMultiMap, playlists)
    }
}