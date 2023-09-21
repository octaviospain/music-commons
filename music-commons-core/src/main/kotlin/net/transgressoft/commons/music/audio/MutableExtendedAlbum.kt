package net.transgressoft.commons.music.audio

import net.transgressoft.commons.music.AudioUtils.audioItemTrackDiscNumberComparator
import java.util.*

internal class MutableExtendedAlbum(
    name: String,
    albumArtist: Artist,
    isCompilation: Boolean,
    year: Short?,
    label: Label,
    audioItems: Set<MutableAudioItem> = emptySet()
) : ExtendedAlbum, ImmutableAlbum(name, albumArtist, isCompilation, year, label) {

    internal constructor(audioItem: MutableAudioItem) : this(audioItem.album.name, audioItem.album.albumArtist, audioItem.album.isCompilation, audioItem.album.year, audioItem.album.label) {
        audioItems.add(audioItem)
    }

    override var audioItems: SortedSet<MutableAudioItem> = audioItems.toSortedSet(audioItemTrackDiscNumberComparator)

    internal fun addAudioItem(audioItem: MutableAudioItem) {
        audioItems.add(audioItem)
    }

    internal fun removeAudioItem(audioItem: MutableAudioItem) {
        audioItems.remove(audioItem)
    }
}