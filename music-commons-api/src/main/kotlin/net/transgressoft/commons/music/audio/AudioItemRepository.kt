package net.transgressoft.commons.music.audio

import net.transgressoft.commons.data.DataEvent
import net.transgressoft.commons.data.Repository
import java.util.concurrent.Flow

interface AudioItemRepository<I : AudioItem> : Repository<I, Int>, Flow.Publisher<DataEvent<I>> {

    fun containsAudioItemWithArtist(artistName: String): Boolean

    fun getRandomAudioItemsFromArtist(artist: Artist, size: Int): List<I>

    fun artists(): Set<Artist>

    fun artistAlbums(artist: Artist): Set<Album>

    fun albumAudioItems(album: Album): Set<I>
}
