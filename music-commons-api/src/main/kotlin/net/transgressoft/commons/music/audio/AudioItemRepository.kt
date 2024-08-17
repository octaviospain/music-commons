package net.transgressoft.commons.music.audio

import net.transgressoft.commons.data.DataEvent
import net.transgressoft.commons.data.Repository
import net.transgressoft.commons.music.player.event.AudioItemPlayerEvent
import java.nio.file.Path
import java.util.*
import java.util.concurrent.Flow

interface AudioItemRepository<I : ReactiveAudioItem<I>> : Repository<Int, I>, Flow.Publisher<DataEvent<Int, I>> {

    val playerSubscriber:  Flow.Subscriber<AudioItemPlayerEvent>

    fun createFromFile(audioItemPath: Path) : I

    fun findAlbumAudioItems(artist: Artist, albumName: String): Set<I>

    fun getArtistCatalog(artist: Artist): Optional<ArtistView<I>>

    fun containsAudioItemWithArtist(artistName: String): Boolean

    fun getRandomAudioItemsFromArtist(artist: Artist, size: Short = 100): List<I>
}
