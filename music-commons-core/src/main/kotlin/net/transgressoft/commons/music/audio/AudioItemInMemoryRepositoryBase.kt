package net.transgressoft.commons.music.audio

import mu.KotlinLogging
import net.transgressoft.commons.query.InMemoryRepository
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Consumer
import java.util.stream.Collectors

/**
 * @author Octavio Calleya
 */
abstract class AudioItemInMemoryRepositoryBase<I : AudioItem>(
    audioItems: MutableMap<Int, I>,
) : InMemoryRepository<I>(audioItems), AudioItemRepository<I> {

    private val logger = KotlinLogging.logger {}

    private val idCounter = AtomicInteger(1)
    private val albumsByArtist: MutableMap<Artist, MutableSet<Album>>

    init {
        albumsByArtist = audioItems.values.stream().collect(
            Collectors.groupingBy({ it.artist },
                Collectors.mapping({ it.album },
                    Collectors.toSet())))
    }

    protected fun newId(): Int {
        var id: Int
        do {
            id = idCounter.getAndIncrement()
        } while (contains(id))
        return id
    }

    override fun add(entity: I): Boolean {
        val added = super.add(entity)
        addOrReplaceAlbumByArtist(entity, added)
        return added
    }

    private fun addOrReplaceAlbumByArtist(audioItem: AudioItem, added: Boolean) {
        val artist = audioItem.artist
        val album = audioItem.album
        if (added) {
            if (albumsByArtist.containsKey(artist)) {
                val mappedAlbums = albumsByArtist[artist]
                if (!albumsByArtist[artist]!!.contains(album)) {
                    mappedAlbums!!.add(album)
                }
            } else {
                val newSet = HashSet<Album>()
                newSet.add(album)
                albumsByArtist[artist] = newSet
            }
        }
    }

    override fun addOrReplace(entity: I): Boolean {
        val addedOrReplaced = super.addOrReplace(entity)
        addOrReplaceAlbumByArtist(entity, addedOrReplaced)
        return addedOrReplaced
    }

    override fun addOrReplaceAll(entities: Set<I>): Boolean {
        val addedOrReplaced = super.getAddedOrReplacedEntities(entities)
        addedOrReplaced[true]?.let { addedList ->
            if (addedList.isNotEmpty()) {
                addedList.forEach(Consumer { addOrReplaceAlbumByArtist(it, true) })
                putCreateEvent(addedList)
            }
        }
        addedOrReplaced[false]?.let { replacedList ->
            if (replacedList.isNotEmpty()) {
                replacedList.forEach(Consumer { addOrReplaceAlbumByArtist(it, true) })
                putUpdateEvent(replacedList)
            }
        }
        return addedOrReplaced.values.stream().flatMap { it.stream() }.findAny().isPresent
    }

    override fun remove(entity: I): Boolean {
        val removed = super.remove(entity)
        removeAlbumByArtistInternal(entity)
        return removed
    }

    private fun removeAlbumByArtistInternal(audioItem: AudioItem) {
        val artist = audioItem.artist
        if (albumsByArtist.containsKey(artist)) {
            var albums = albumsByArtist[audioItem.artist]
            albums = albums?.stream()?.filter { album: Album -> isAlbumNotEmpty(album) }?.collect(Collectors.toSet())
            if (albums != null) {
                if (albums.isEmpty()) {
                    albumsByArtist.remove(artist)
                } else {
                    albumsByArtist[artist] = albums
                }
            }
        }
    }

    protected abstract fun isAlbumNotEmpty(album: Album): Boolean

    override fun removeAll(entities: Set<I>): Boolean {
        val removed = super.removeAll(entities)
        entities.forEach { audioItem: AudioItem -> removeAlbumByArtistInternal(audioItem) }
        return removed
    }

    override fun artists(): Set<Artist> = albumsByArtist.keys.toSet()

    override fun artistAlbums(artist: Artist): Set<Album> = albumsByArtist[artist]?.toSet() ?: emptySet()
}
