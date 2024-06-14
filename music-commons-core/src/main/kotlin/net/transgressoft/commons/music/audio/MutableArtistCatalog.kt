package net.transgressoft.commons.music.audio

import net.transgressoft.commons.IdentifiableEntity
import net.transgressoft.commons.music.AudioUtils
import mu.KotlinLogging
import java.util.*

internal data class MutableArtistCatalog(val artist: Artist) : IdentifiableEntity<String> {

    private val logger = KotlinLogging.logger {}

    private val albums: MutableMap<String, SortedSet<AudioItem>> = mutableMapOf()

    init {
        logger.debug { "Artist catalog created for ${artist.id()}" }
    }

    constructor(audioItem: AudioItem) : this(audioItem.artist) {
        addAudioItem(audioItem)
    }

    override val id: String = "${artist.name}-${artist.countryCode.name}"

    override val uniqueId: String = "${artist.name}-${artist.countryCode.name}"

    val size: Int
        get() = albums.values.stream().flatMap { it.stream() }.count().toInt()

    val isEmpty: Boolean
        get() = albums.isEmpty()

    fun addAudioItem(audioItem: AudioItem): Boolean {
        return albums.merge(audioItem.album.name, setOf(audioItem).toSortedSet(AudioUtils.audioItemTrackDiscNumberComparator)) { set, _ ->
            set.add(audioItem)
            logger.debug { "AudioItem $audioItem was added to album ${audioItem.album}" }
            set
        }.let { it?.size!! > 1 }
    }

    fun removeAudioItem(audioItem: AudioItem): Boolean {
        return albums[audioItem.album.name]?.removeIf { it.id == audioItem.id }?.also {
            if (it) {
                if (albums[audioItem.album.name]?.isEmpty() == true) {
                    albums.remove(audioItem.album.name)
                    logger.debug { "Album ${audioItem.album} was removed from artist catalog of $artist" }
                } else {
                    logger.debug { "AudioItem $audioItem was removed from album ${audioItem.album}" }
                }
            }
        } ?: false
    }

    fun findAlbumAudioItems(albumName: String): Set<AudioItem> = albums[albumName] ?: emptySet()

    fun containsAudioItem(audioItem: AudioItem) = albums[audioItem.album.name]?.contains(audioItem) ?: false

    fun mergeAudioItem(audioItem: AudioItem) {
        removeAudioItem(audioItem)
        addAudioItem(audioItem)
    }

    override fun toString() = "MutableArtistCatalog(artist=$artist, size=$size)"
}