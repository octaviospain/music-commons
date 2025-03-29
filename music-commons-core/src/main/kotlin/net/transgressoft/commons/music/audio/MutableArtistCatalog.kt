package net.transgressoft.commons.music.audio

import net.transgressoft.commons.entity.IdentifiableEntity
import net.transgressoft.commons.music.AudioUtils
import mu.KotlinLogging
import java.util.*

internal data class MutableArtistCatalog<I>(val artist: Artist) : IdentifiableEntity<String> where I : ReactiveAudioItem<I> {

    private val logger = KotlinLogging.logger {}

    private val albums: MutableMap<String, SortedSet<I>> = mutableMapOf()

    init {
        logger.debug { "Artist catalog created for ${artist.id()}" }
    }

    constructor(audioItem: I) : this(audioItem.artist) {
        addAudioItem(audioItem)
    }

    override val id: String = "${artist.name}-${artist.countryCode.name}"

    override val uniqueId: String = "${artist.name}-${artist.countryCode.name}"

    val size: Int
        get() = albums.values.stream().flatMap { it.stream() }.count().toInt()

    val isEmpty: Boolean
        get() = albums.isEmpty()

    fun addAudioItem(audioItem: I): Boolean =
        albums.merge(audioItem.album.name, setOf(audioItem).toSortedSet(AudioUtils.audioItemTrackDiscNumberComparator())) { set, _ ->
            set.add(audioItem)
            logger.debug { "AudioItem $audioItem was added to album ${audioItem.album}" }
            set
        }.let { it?.size!! > 1 }

    fun removeAudioItem(audioItem: I): Boolean =
        albums[audioItem.album.name]?.removeIf { it.id == audioItem.id }?.also {
            if (it) {
                if (albums[audioItem.album.name]?.isEmpty() == true) {
                    albums.remove(audioItem.album.name)
                    logger.debug { "Album ${audioItem.album} was removed from artist catalog of $artist" }
                } else {
                    logger.debug { "AudioItem $audioItem was removed from album ${audioItem.album}" }
                }
            }
        } ?: false

    fun findAlbumAudioItems(albumName: String): Set<I> = albums[albumName] ?: emptySet()

    fun containsAudioItem(audioItem: I) = albums[audioItem.album.name]?.contains(audioItem) == true

    fun mergeAudioItem(audioItem: I) {
        removeAudioItem(audioItem)
        addAudioItem(audioItem)
    }

    fun getArtistView(): ArtistView<I> =
        albums.entries.map { AlbumView(it.key, it.value) }.toSet()
            .let { ArtistView(artist, it) }

    override fun clone(): MutableArtistCatalog<I> = copy()

    override fun toString() = "MutableArtistCatalog(artist=$artist, size=$size)"
}