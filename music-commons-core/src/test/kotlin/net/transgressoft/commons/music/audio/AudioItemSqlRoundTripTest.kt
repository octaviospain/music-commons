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

package net.transgressoft.commons.music.audio

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
import kotlin.io.path.createTempDirectory

/**
 * Verifies that [MutableAudioItem] persists and reloads correctly through a [SqlRepository]
 * backed by in-memory or file-backed SQLite, using the KSP-generated [MutableAudioItem_LirpTableDef]
 * directly — no hand-written descriptor.
 *
 * Key assertions:
 * - genres (including [Genre.Custom]) round-trip via [GenreConverter] (`@ElementCollection`)
 * - the embedded [Artist] and the embedded [Album] (with its nested [Album.albumArtist]/[Album.label]
 *   flyweights) round-trip, reconstructed through their `@PersistenceCreator` `of()` factories
 * - duration, bitRate, comments, trackNumber, discNumber preserve their values
 * - empty-genres case uses the `[]` default expression and reloads as an empty set
 * - file-backed SQLite repo preserves data across distinct [SqlRepository] instances
 */
internal class AudioItemSqlRoundTripTest : StringSpec({

    val fs = Jimfs.newFileSystem(Configuration.unix())
    Files.createDirectories(fs.getPath("/audio"))

    fun testPath(name: String): Path = fs.getPath("/audio/$name.mp3")

    "MutableAudioItem_LirpTableDef persists and reloads audio item with populated genres and artist" {
        val genres = setOf(Genre.Rock, Genre.Pop, Genre.Custom("MyCustomGenre"))
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
        val item = MutableAudioItem(testPath("track-genres"), 1, metadata)
        val repo: SqlRepository<Int, AudioItem> = SqliteRepository.inMemory(MutableAudioItem_LirpTableDef)
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

    "MutableAudioItem_LirpTableDef persists and reloads audio item with empty genres" {
        val metadata =
            AudioItemMetadata(
                title = "Empty Genres Track",
                genres = emptySet(),
                duration = Duration.ofSeconds(180),
                bitRate = 128
            )
        val item = MutableAudioItem(testPath("track-empty"), 2, metadata)
        val repo: SqlRepository<Int, AudioItem> = SqliteRepository.inMemory(MutableAudioItem_LirpTableDef)
        repo.use { repo ->
            repo.add(item)
            repo.findById(2).shouldBePresent { reloaded ->
                reloaded.title shouldBe "Empty Genres Track"
                reloaded.genres shouldBe emptySet()
                reloaded.duration shouldBe Duration.ofSeconds(180)
            }
        }
    }

    "MutableAudioItem_LirpTableDef reload from file-backed SQLite repository preserves genres and duration" {
        val tmpDir = createTempDirectory("audio-sql-test")
        val dbPath = tmpDir.resolve("audio_test.db")
        val genres = setOf(Genre.Jazz, Genre.Classical, Genre.Custom("CustomTestGenre"))
        val metadata =
            AudioItemMetadata(
                title = "Persisted Track",
                genres = genres,
                duration = Duration.ofSeconds(360),
                bitRate = 256,
                discNumber = 2
            )
        val item = MutableAudioItem(testPath("track-persist"), 3, metadata)

        val writeRepo: SqlRepository<Int, AudioItem> = SqliteRepository.fileBacked(dbPath, MutableAudioItem_LirpTableDef)
        writeRepo.use { writeRepo ->
            writeRepo.add(item)
        }

        val readRepo: SqlRepository<Int, AudioItem> = SqliteRepository.fileBacked(dbPath, MutableAudioItem_LirpTableDef)
        try {
            readRepo.findById(3).shouldBePresent { r ->
                r.id shouldBe 3
                r.title shouldBe "Persisted Track"
                r.genres shouldBe genres
                r.duration shouldBe Duration.ofSeconds(360)
                r.bitRate shouldBe 256
                r.discNumber shouldBe 2.toShort()
            }
        } finally {
            readRepo.close()
            tmpDir.toFile().deleteRecursively()
        }
    }
})