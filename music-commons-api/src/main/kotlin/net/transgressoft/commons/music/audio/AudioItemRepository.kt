package net.transgressoft.commons.music.audio

import net.transgressoft.commons.event.EntityEvent
import net.transgressoft.commons.query.Repository
import java.nio.file.Path
import java.util.concurrent.Flow

interface AudioItemRepository<I : AudioItem> : Repository<I>, Flow.Publisher<EntityEvent<out I>> {

    @Throws(AudioItemManipulationException::class)
    fun createFromFile(path: Path): AudioItem

    fun containsAudioItemWithArtist(artistName: String): Boolean

    fun artists(): Set<Artist>

    fun artistAlbums(artist: Artist): Set<Album>

    fun albumAudioItems(album: Album): Set<I>

    fun editAudioItems(audioItemIds: Set<Int>, change: AudioItemMetadataChange)
}
