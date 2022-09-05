package net.transgressoft.commons.music.audio

import net.transgressoft.commons.query.Repository
import java.nio.file.Path

interface AudioItemRepository<I : AudioItem> : Repository<I> {

    @Throws(AudioItemManipulationException::class)
    fun createFromFile(path: Path): AudioItem

    fun containsAudioItemWithArtist(artistName: String): Boolean

    fun artistAlbums(artist: Artist): Set<Album>
}
