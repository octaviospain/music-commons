/******************************************************************************
 * Copyright (C) 2026  Octavio Calleya Garcia                                 *
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

package net.transgressoft.commons.persistence.music.audio

import net.transgressoft.commons.music.CoreMusicLibrary
import net.transgressoft.commons.music.audio.Album
import net.transgressoft.commons.music.audio.ArbitraryAudioFile.realAudioFile
import net.transgressoft.commons.music.audio.Artist
import net.transgressoft.commons.music.audio.AudioFileTagType.ID3_V_24
import net.transgressoft.commons.music.audio.AudioItem
import net.transgressoft.commons.music.audio.Genre
import net.transgressoft.commons.music.audio.Label
import net.transgressoft.commons.music.audio.audioAttributes
import net.transgressoft.commons.music.audio.parseGenre
import net.transgressoft.commons.music.audio.testCoverBytes
import net.transgressoft.commons.music.testing.reactiveScope
import net.transgressoft.lirp.persistence.json.JsonFileRepository
import com.neovisionaries.i18n.CountryCode
import io.kotest.core.annotation.DisplayName
import io.kotest.core.spec.style.StringSpec
import io.kotest.engine.spec.tempfile
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.next
import java.time.Duration

@DisplayName("AudioItem JSON round-trip")
internal class AudioItemSerializerRoundTripTest : StringSpec({

    val reactive = reactiveScope()

    "AudioItemMapSerializer round-trips custom field types through a JsonFileRepository" {
        val audioFile = tempfile("audioLibrary-rt", ".json").apply { deleteOnExit() }

        val library =
            CoreMusicLibrary.builder()
                .audioRepository(JsonFileRepository(audioFile, AudioItemMapSerializer))
                .build()

        // Real ID3v2.4 file so duration/cover/encoder are populated from a true audio header,
        // and a default-filesystem path so the file:// URI round-trips with provider identity.
        val attributes =
            Arb.audioAttributes(
                title = "Round Trip",
                duration = Duration.ofSeconds(225),
                artist = Artist.of("Boards of Canada", CountryCode.GB),
                album = Album("Geogaddi", Artist.of("Boards of Canada", CountryCode.GB), false, 2002, Label.of("Warp", CountryCode.GB)),
                genres = setOf(parseGenre("Electronic").first(), Genre.Custom("IDM")),
                trackNumber = 4,
                discNumber = 1
            ).next()
        val path = Arb.realAudioFile(ID3_V_24, attributes).next()
        val original: AudioItem = library.audioItemFromFile(path)
        reactive.advance()

        library.close()

        val reloaded =
            CoreMusicLibrary.builder()
                .audioRepository(JsonFileRepository(audioFile, AudioItemMapSerializer))
                .build()

        val loaded = reloaded.audioLibrary().findById(original.id).orElse(null)
        loaded.shouldNotBeNull()

        loaded.id shouldBe original.id
        loaded.path shouldBe original.path
        loaded.title shouldBe original.title
        loaded.duration shouldBe original.duration
        loaded.artist.countryCode shouldBe original.artist.countryCode
        loaded.album.label.countryCode shouldBe original.album.label.countryCode
        loaded.genres shouldBe original.genres
        loaded.trackNumber shouldBe original.trackNumber
        loaded.discNumber shouldBe original.discNumber

        reloaded.close()
    }

    "AudioItemMapSerializer leaves cover bytes out of the JSON wire (covers travel out of band)" {
        val audioFile = tempfile("audioLibrary-cover", ".json").apply { deleteOnExit() }

        val library =
            CoreMusicLibrary.builder()
                .audioRepository(JsonFileRepository(audioFile, AudioItemMapSerializer))
                .build()

        val coveredAttributes = Arb.audioAttributes(coverImageBytes = testCoverBytes).next()
        val original = library.audioItemFromFile(Arb.realAudioFile(ID3_V_24, coveredAttributes).next())
        original.coverImageBytes.shouldNotBeNull()
        reactive.advance()

        library.close()

        // The cover bytes never reach the JSON wire — the persisted document carries no base64 blob
        // nor the raw byte sequence. Covers are re-loaded from the audio file lazily by the library.
        val persistedJson = audioFile.readText()
        persistedJson.contains("coverBytes") shouldBe false
        persistedJson.contains("coverImageBytes") shouldBe false
    }
})