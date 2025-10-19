/******************************************************************************
 * Copyright (C) 2025  Octavio Calleya Garcia                                 *
 *                                                                            *
 * This program is free software: you can redistribute it and/or modify       *
 * it under the terms of the GNU General Public License as published by       *
 * the Free Software Foundation, either version 3 of the License, or          *
 * (at your option) any later version.                                        *
 *                                                                            *
 * This program is distributed in the hope that it will be useful,            *
 * but WITHOUT ANY WARRANTY; without even the implied warranty of             *
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the              *
 * GNU General Public License for more details.                               *
 *                                                                            *
 * You should have received a copy of the GNU General Public License          *
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.     *
 ******************************************************************************/

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