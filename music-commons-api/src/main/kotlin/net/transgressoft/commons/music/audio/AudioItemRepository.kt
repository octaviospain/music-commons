package net.transgressoft.commons.music.audio

import net.transgressoft.commons.data.DataEvent
import net.transgressoft.commons.data.Repository
import java.nio.file.Path
import java.util.concurrent.Flow

interface AudioItemRepository<I : AudioItem> : Repository<Int, I>, Flow.Publisher<DataEvent<I>> {

    val artistCatalogRegistry: ArtistCatalogRegistry

    fun createFromFile(audioItemPath: Path) : I

    fun containsAudioItemWithArtist(artistName: String): Boolean

    fun getRandomAudioItemsFromArtist(artist: Artist, size: Int): List<I>
}
