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

package net.transgressoft.commons.fx.music.audio

import net.transgressoft.commons.music.audio.Album
import net.transgressoft.commons.music.audio.Artist
import net.transgressoft.commons.music.audio.AudioItemMetadata
import net.transgressoft.commons.music.audio.Genre
import net.transgressoft.commons.music.audio.Label
import net.transgressoft.commons.music.audio.Pop
import net.transgressoft.commons.music.audio.Rock
import net.transgressoft.lirp.persistence.sql.SqlRepository
import net.transgressoft.lirp.persistence.sql.SqliteRepository
import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import com.neovisionaries.i18n.CountryCode
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.optional.shouldBePresent
import io.kotest.matchers.shouldBe
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration

/**
 * Verifies the KSP-generated [FXAudioItem_LirpTableDef] persists and reloads an [FXAudioItem]
 * through a [SqlRepository] backed by in-memory SQLite — the consumer wiring path for swapping a
 * JSON-backed audio repository for `SqliteRepository`.
 *
 * Confirms the embedded [Artist], the embedded [Album] (including its nested
 * [Album.albumArtist]/[Album.label] flyweights, reconstructed through their `@PersistenceCreator`
 * `of()` factories), genres (`@ElementCollection`) and the immutable file-header columns all
 * survive the round-trip.
 */
internal class FXAudioItemSqlRoundTripTest : StringSpec({

    val fs = Jimfs.newFileSystem(Configuration.unix())
    Files.createDirectories(fs.getPath("/audio"))

    fun testPath(name: String): Path = fs.getPath("/audio/$name.mp3")

    "FXAudioItem_LirpTableDef persists and reloads an FX audio item with artist, album and genres" {
        val genres = setOf(Rock, Pop, Genre.Custom("MyCustomGenre"))
        val artist = Artist.of("Daft Punk", CountryCode.FR)
        val album =
            Album(
                name = "Discovery",
                albumArtist = Artist.of("Daft Punk", CountryCode.FR),
                isCompilation = false,
                year = 2001,
                label = Label.of("Virgin", CountryCode.GB)
            )
        val metadata =
            AudioItemMetadata(
                title = "Test Track",
                artist = artist,
                album = album,
                genres = genres,
                duration = Duration.ofSeconds(245),
                bitRate = 320,
                comments = "Round-trip test",
                trackNumber = 3
            )
        val item = FXAudioItemTestBridge.createFxAudioItemFromMetadata(testPath("fx-track"), 1, metadata)
        val repo: SqlRepository<Int, ObservableAudioItem> = SqliteRepository.inMemory(FXAudioItem_LirpTableDef)
        repo.use { repo ->
            repo.add(item)
            repo.findById(1).shouldBePresent { reloaded ->
                reloaded.title shouldBe "Test Track"
                reloaded.artist shouldBe artist
                reloaded.artist.countryCode shouldBe CountryCode.FR
                reloaded.album shouldBe album
                reloaded.album.albumArtist shouldBe Artist.of("Daft Punk", CountryCode.FR)
                reloaded.album.label shouldBe Label.of("Virgin", CountryCode.GB)
                reloaded.album.year shouldBe 2001.toShort()
                reloaded.genres shouldBe genres
                reloaded.duration shouldBe Duration.ofSeconds(245)
                reloaded.bitRate shouldBe 320
                reloaded.comments shouldBe "Round-trip test"
                reloaded.trackNumber shouldBe 3.toShort()
            }
        }
    }
})