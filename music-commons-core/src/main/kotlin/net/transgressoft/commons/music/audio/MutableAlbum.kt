package net.transgressoft.commons.music.audio

import net.transgressoft.commons.music.AudioUtils.audioItemTrackDiscNumberComparator
import mu.KotlinLogging
import java.util.*

internal class MutableAlbum(
    name: String,
    albumArtist: Artist,
    isCompilation: Boolean,
    year: Short?,
    label: Label,
    audioItems: Set<MutableAudioItem> = emptySet()
) : Album, ImmutableAlbum(name, albumArtist, isCompilation, year, label) {

    private val logger = KotlinLogging.logger {}

    internal constructor(audioItem: MutableAudioItem) : this(audioItem.album.name, audioItem.album.albumArtist, audioItem.album.isCompilation, audioItem.album.year, audioItem.album.label) {
        audioItems.add(audioItem)
    }

    override var audioItems: SortedSet<MutableAudioItem> = audioItems.toSortedSet(audioItemTrackDiscNumberComparator)

    internal fun addAudioItem(audioItem: MutableAudioItem): Boolean {
        return audioItems.add(audioItem).also {
            logger.debug { "Audio item ${audioItem.uniqueId} was added to album $name. Current audio items $audioItems" }
        }
    }

    internal fun removeAudioItem(audioItem: MutableAudioItem): Boolean {
        return audioItems.remove(audioItem).also {
            logger.debug { "Audio item ${audioItem.uniqueId} was removed from album $name. Current audio items $audioItems" }
        }
    }
}