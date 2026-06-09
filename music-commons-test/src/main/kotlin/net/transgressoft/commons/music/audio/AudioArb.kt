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
import io.kotest.property.arbitrary.Codepoint
import io.kotest.property.arbitrary.alphanumeric
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.float
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.localDateTime
import io.kotest.property.arbitrary.next
import io.kotest.property.arbitrary.positiveInt
import io.kotest.property.arbitrary.positiveLong
import io.kotest.property.arbitrary.positiveShort
import io.kotest.property.arbitrary.short
import io.kotest.property.arbitrary.string
import io.mockk.every
import io.mockk.mockk
import java.nio.file.Path
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.toJavaDuration

fun Arb.Companion.albumAudioItems(
    artist: Artist? = null,
    album: Album? = null,
    size: IntRange = 3..10
): Arb<List<AudioItem>> =
    arbitrary {
        val arbitraryArtist = artist ?: artist().bind()
        val arbitraryAlbum = album ?: album().bind()
        buildList {
            repeat(Arb.int(size).bind()) {
                add(
                    audioItem {
                        metadata =
                            metadata.copy(
                                artist = arbitraryArtist,
                                album = arbitraryAlbum,
                                trackNumber = (it.plus(1)).toShort(),
                                discNumber = 1,
                                coverBytes = null
                            )
                    }.bind()
                )
            }
        }.sortedWith(audioItemTrackDiscNumberComparator())
    }

fun Arb.Companion.audioItem(audioItem: AudioItem, changeAction: AudioItemChange.() -> Unit = {}): Arb<AudioItem> =
    arbitrary {
        val change = AudioItemChange(audioItem.id).also(changeAction)
        mockk<AudioItem> {
            // immutable properties
            every { id } returns audioItem.id
            every { uniqueId } answers { callOriginal() }
            every { path } returns audioItem.path
            every { duration } returns audioItem.duration
            every { bitRate } returns audioItem.bitRate
            every { encoder } returns audioItem.encoder
            every { encoding } returns audioItem.encoding
            every { dateOfCreation } returns audioItem.dateOfCreation
            every { lastDateModified } returns audioItem.lastDateModified
            every { playCount } returns audioItem.playCount

            // mutable properties
            every { title } returns (change.title ?: audioItem.title)
            every { artist } returns (change.artist ?: audioItem.artist)
            every { album } returns
                Album(
                    change.albumName ?: audioItem.album.name,
                    change.albumArtist ?: audioItem.album.albumArtist,
                    change.isCompilation ?: audioItem.album.isCompilation,
                    change.year?.takeIf { year -> year > 0 } ?: audioItem.album.year,
                    change.label ?: audioItem.album.label
                )
            every { genres } returns (change.genres ?: audioItem.genres)
            every { comments } returns (change.comments ?: audioItem.comments)
            every { trackNumber } returns (change.trackNumber ?: audioItem.trackNumber)
            every { discNumber } returns (change.discNumber ?: audioItem.discNumber)
            every { bpm } returns (change.bpm ?: audioItem.bpm)
            every { coverImageBytes } returns (change.coverImageBytes ?: audioItem.coverImageBytes)

            every { this@mockk.compareTo(any()) } answers {
                val other = firstArg<AudioItem>()
                audioItemTrackDiscNumberComparator<AudioItem>().compare(this@mockk, other)
            }
            every { this@mockk.artistsInvolved } answers { callOriginal() }
            every { this@mockk.equals(any()) } answers { callOriginal() }
            every { this@mockk.hashCode() } answers { callOriginal() }
            every { this@mockk.toString() } answers { callOriginal() }
        }
    }

fun Arb.Companion.audioItem(attributesAction: AudioItemTestAttributes.() -> Unit = {}): Arb<AudioItem> =
    arbitrary {
        val attributes = audioAttributes().bind()
        attributesAction(attributes)
        audioItem(attributes).bind()
    }

fun Arb.Companion.audioItem(attributes: AudioItemTestAttributes): Arb<AudioItem> =
    arbitrary {
        val metadata = attributes.metadata
        mockk<AudioItem> {
            // immutable properties
            every { id } returns attributes.id
            every { uniqueId } answers { callOriginal() }
            every { path } returns attributes.path
            every { duration } returns metadata.duration
            every { bitRate } returns metadata.bitRate
            every { encoder } returns metadata.encoder
            every { encoding } returns metadata.encoding
            every { dateOfCreation } returns attributes.dateOfCreation
            every { lastDateModified } returns attributes.lastDateModified
            every { playCount } returns attributes.playCount

            // mutable properties
            every { title } returns metadata.title
            every { artist } returns metadata.artist
            every { album } returns metadata.album
            every { genres } returns metadata.genres
            every { comments } returns metadata.comments
            every { trackNumber } returns metadata.trackNumber
            every { discNumber } returns metadata.discNumber
            every { bpm } returns metadata.bpm
            every { coverImageBytes } returns metadata.coverBytes
            every { playCount } returns attributes.playCount

            every { this@mockk.artistsInvolved } answers { callOriginal() }
            every { this@mockk.asJsonKeyValue() } answers { callOriginal() }
            every { this@mockk.asJsonValue() } answers { callOriginal() }
            every { this@mockk.compareTo(any()) } answers {
                val other = firstArg<AudioItem>()
                audioItemTrackDiscNumberComparator<AudioItem>().compare(this@mockk, other)
            }
            every { this@mockk.equals(any()) } answers { callOriginal() }
            every { this@mockk.hashCode() } answers { callOriginal() }
            every { this@mockk.toString() } answers { callOriginal() }
        }
    }

fun Arb.Companion.artist(givenName: String? = null, countryCode: CountryCode? = null): Arb<Artist> =
    arbitrary {
        Artist.of(
            givenName ?: beautifyArtistName(Arb.string(1..100).bind()),
            countryCode ?: CountryCode.entries.toTypedArray().random()
        )
    }

fun Arb.Companion.album(
    name: String? = null,
    albumArtist: Artist? = null,
    isCompilation: Boolean? = null,
    year: Short? = null,
    label: Label? = null
): Arb<Album> =
    arbitrary {
        Album(
            name ?: Arb.string(1..100).bind(),
            albumArtist ?: artist().bind(),
            isCompilation ?: Arb.boolean().bind(),
            year ?: Arb.short(1, Short.MAX_VALUE).bind(),
            label ?: label().bind()
        )
    }

fun Arb.Companion.label(name: String? = null, countryCode: CountryCode? = null) =
    arbitrary {
        Label.of(name ?: Arb.string(1..100).bind(), countryCode ?: CountryCode.entries.toTypedArray().random())
    }

fun Arb.Companion.audioFilePath(audioFileType: AudioFileType = Arb.enum<AudioFileType>().next()): Arb<Path> =
    arbitrary {
        // Path segments are restricted to Windows-safe alphanumeric characters because Arb.string()
        // produces arbitrary Unicode (including < > : " | ? * and other forbidden Windows path
        // characters), which makes Path.of(...) throw InvalidPathException on Windows. Mocked
        // audio items only need a syntactically valid Path — they do not resolve to disk.
        val dir = Arb.string(1..20, Codepoint.alphanumeric()).bind()
        val fileName = Arb.string(1..30, Codepoint.alphanumeric()).bind()
        val suffix = audioFileType.extension
        Path.of("music", dir, "$fileName.$suffix")
    }

fun Arb.Companion.audioItemChange(): Arb<AudioItemChange> =
    arbitrary {
        val attributes = audioAttributes().bind()
        val metadata = attributes.metadata
        AudioItemChange(
            attributes.id,
            metadata.title,
            metadata.artist,
            metadata.album.name,
            metadata.album.albumArtist,
            metadata.album.isCompilation,
            metadata.album.year,
            metadata.album.label,
            metadata.genres,
            metadata.comments,
            metadata.trackNumber,
            metadata.discNumber,
            metadata.bpm,
            metadata.coverBytes,
            attributes.playCount
        )
    }

fun Arb.Companion.genre(): Arb<Genre> =
    arbitrary {
        val samples =
            listOf(
                Genre.Rock, Genre.Alternative, Genre.Jazz, Genre.Blues, Genre.Electronic,
                Genre.HipHop, Genre.Classical, Genre.Folk, Genre.Metal, Genre.Pop
            )
        if (Arb.boolean().bind()) samples[Arb.int(0 until samples.size).bind()]
        // Custom genre strings use alphanumeric chars only because JAudioTagger normalizes
        // arbitrary Unicode (e.g., control characters, combining marks, NFD vs NFC) during
        // FLAC Vorbis Comment write/read round-trips, which would make `loadedAudioItem.genres
        // shouldBe audioItem.genres` flaky in MutableAudioItemTest.
        else Genre.Custom(Arb.string(1..30, Codepoint.alphanumeric()).bind())
    }

fun Arb.Companion.genres(): Arb<Set<Genre>> =
    arbitrary {
        val count = Arb.int(0..3).bind()
        (1..count).map { Arb.genre().bind() }.toSet()
    }

fun Arb.Companion.audioAttributes(
    id: Int? = null,
    path: Path? = null,
    title: String? = null,
    duration: Duration? = null,
    bitRate: Int? = null,
    artist: Artist? = null,
    album: Album? = null,
    genres: Set<Genre>? = null,
    comments: String? = null,
    trackNumber: Short? = null,
    discNumber: Short? = null,
    bpm: Float? = null,
    encoder: String? = null,
    encoding: String? = null,
    coverImageBytes: ByteArray? = null,
    dateOfCreation: LocalDateTime? = null,
    lastDateModified: LocalDateTime? = null,
    playCount: Short? = null
): Arb<AudioItemTestAttributes> =
    arbitrary {
        val metadata =
            AudioItemMetadata(
                title = title ?: Arb.string(1..100).bind(),
                artist = artist ?: artist().bind(),
                album = album ?: album().bind(),
                genres = genres ?: Arb.genres().bind(),
                comments = comments ?: Arb.string(1..100).bind(),
                trackNumber = trackNumber ?: Arb.positiveShort().bind(),
                discNumber = discNumber ?: Arb.positiveShort().bind(),
                bpm = bpm ?: Arb.float(10.0f..220.58f, includeNaNs = false).bind(),
                encoder = encoder ?: Arb.string(1..100).bind(),
                encoding = encoding ?: Arb.string(1..100).bind(),
                bitRate = bitRate ?: Arb.positiveInt().bind(),
                duration = duration ?: Arb.positiveLong().bind().nanoseconds.toJavaDuration(),
                coverBytes = coverImageBytes
            )
        AudioItemTestAttributes(
            path = path ?: Arb.audioFilePath().bind(),
            id = id ?: atomicInteger.getAndDecrement(),
            metadata = metadata,
            dateOfCreation = dateOfCreation ?: Arb.localDateTime(LocalDateTime.of(2000, 1, 1, 0, 0)).next(),
            lastDateModified = lastDateModified ?: Arb.localDateTime(LocalDateTime.of(2023, 1, 1, 0, 0)).next(),
            playCount = playCount ?: Arb.positiveShort().bind()
        )
    }

private val atomicInteger = AtomicInteger(Integer.MAX_VALUE)

/**
 * Returns an [Arb] that always produces the OGG Vorbis test fixture path.
 *
 * OGG uses Vorbis comments for metadata which are structurally different from ID3/MP4 tags.
 * This generator provides a constant path to the real OGG test file rather than a mocked
 * audio item, suitable for SPI verification and format-specific waveform/player tests.
 */
fun Arb.Companion.oggTestFile(): Arb<Path> =
    arbitrary { oggFile.toPath() }