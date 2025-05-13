package net.transgressoft.commons.music.audio

import com.neovisionaries.i18n.CountryCode
import io.kotest.property.Arb
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.next
import java.nio.file.Path
import java.util.function.Consumer

/**
 * Java-friendly factory methods for creating test audio objects.
 * This class serves as a bridge to the Kotlin-based arbitrary generators.
 */
object AudioItemTestFactory {

    @JvmStatic
    fun createAudioItem(attributes: Consumer<AudioItemTestAttributes>): AudioItem = Arb.audioItem(attributes::accept).next()

    @JvmStatic
    @JvmOverloads
    fun createAlbumAudioItems(artist: Artist? = null, album: Album? = null, range: IntRange = 3..10): List<AudioItem> =
        Arb.albumAudioItems(artist, album, range).next()

    @JvmStatic
    @JvmOverloads
    fun createArtist(name: String? = null, countryCode: CountryCode? = null): Artist = Arb.artist(name, countryCode).next()

    @JvmStatic
    @JvmOverloads
    fun createAlbum(name: String? = null, albumArtist: Artist? = null, isCompilation: Boolean? = null, year: Short? = null, label: Label? = null): Album =
        Arb.album(name, albumArtist, isCompilation, year).next()

    @JvmStatic
    @JvmOverloads
    fun createLabel(name: String? = null, countryCode: CountryCode? = null): Label = Arb.label(name, countryCode).next()

    @JvmStatic
    @JvmOverloads
    fun createAudioFile(audioFileType: AudioFileType = Arb.enum<AudioFileType>().next()): Path = Arb.audioFilePath(audioFileType).next()
}