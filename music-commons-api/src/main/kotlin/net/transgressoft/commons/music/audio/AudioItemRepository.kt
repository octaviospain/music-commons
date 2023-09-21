package net.transgressoft.commons.music.audio

import net.transgressoft.commons.data.DataEvent
import net.transgressoft.commons.data.Repository
import com.neovisionaries.i18n.CountryCode
import java.nio.file.Path
import java.util.concurrent.Flow

interface AudioItemRepository<I : AudioItem> : Repository<Int, I>, Flow.Publisher<DataEvent<Int, I>> {

    val artistCatalogRegistry: ArtistCatalogRegistry

    fun createFromFile(audioItemPath: Path) : I

    fun containsAudioItemWithArtist(artistName: String): Boolean

    fun getRandomAudioItemsFromArtist(artistName: String, size: Short = 100, countryCode: CountryCode = CountryCode.UNDEFINED): List<I>
}
