package net.transgressoft.commons.music.audio

import net.transgressoft.commons.music.AudioUtils.beautifyArtistName
import com.neovisionaries.i18n.CountryCode
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.file
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
                        this.artist = arbitraryArtist
                        this.album = arbitraryAlbum
                        this.trackNumber = (it.plus(1)).toShort()
                        this.coverImageBytes = null
                    }.bind()
                )
            }
        }
    }

fun Arb.Companion.audioItem(audioItem: AudioItem, changeAction: AudioItemChange.() -> Unit = {}): Arb<AudioItem> =
    arbitrary {
        val change = AudioItemChange(audioItem.id).also(changeAction)
        mockk<AudioItem> {
            // immutable properties
            every { id } returns audioItem.id
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
                ImmutableAlbum(
                    change.albumName ?: audioItem.album.name,
                    change.albumArtist ?: audioItem.album.albumArtist,
                    change.isCompilation ?: audioItem.album.isCompilation,
                    change.year?.takeIf { year -> year > 0 } ?: audioItem.album.year,
                    change.label ?: audioItem.album.label
                )
            every { genre } returns (change.genre ?: audioItem.genre)
            every { comments } returns (change.comments ?: audioItem.comments)
            every { trackNumber } returns (change.trackNumber ?: audioItem.trackNumber)
            every { discNumber } returns (change.discNumber ?: audioItem.discNumber)
            every { bpm } returns (change.bpm ?: audioItem.bpm)
            every { coverImageBytes } returns (change.coverImageBytes ?: audioItem.coverImageBytes)

            every { this@mockk.equals(any()) } answers { callOriginal() }
            every { this@mockk.hashCode() } answers { callOriginal() }
            every { this@mockk.toString() } answers { callOriginal() }
        }
    }

fun Arb.Companion.audioItem(attributesAction: AudioItemTestAttributes.() -> Unit = {}): Arb<AudioItem> =
    arbitrary {
        val attributes = audioAttributes().bind()
        attributesAction(attributes)
        mockk<AudioItem> {
            // immutable properties
            every { id } returns attributes.id
            every { path } returns attributes.path
            every { duration } returns attributes.duration
            every { bitRate } returns attributes.bitRate
            every { encoder } returns attributes.encoder
            every { encoding } returns attributes.encoding
            every { dateOfCreation } returns attributes.dateOfCreation
            every { lastDateModified } returns attributes.lastDateModified
            every { playCount } returns attributes.playCount

            // mutable properties
            every { title } returns attributes.title
            every { artist } returns attributes.artist
            every { album } returns attributes.album
            every { genre } returns attributes.genre
            every { comments } returns attributes.comments
            every { trackNumber } returns attributes.trackNumber
            every { discNumber } returns attributes.discNumber
            every { bpm } returns attributes.bpm
            every { coverImageBytes } returns attributes.coverImageBytes
            every { playCount } returns attributes.playCount

            every { this@mockk.asJsonKeyValue() } answers { callOriginal() }
            every { this@mockk.asJsonValue() } answers { callOriginal() }
            every { this@mockk.equals(any()) } answers { callOriginal() }
            every { this@mockk.hashCode() } answers { callOriginal() }
            every { this@mockk.toString() } answers { callOriginal() }
        }
    }

fun Arb.Companion.artist(givenName: String? = null, countryCode: CountryCode? = null): Arb<Artist> =
    arbitrary {
        ImmutableArtist.of(
            givenName ?: beautifyArtistName(Arb.string().bind()),
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
        ImmutableAlbum(
            name ?: Arb.string().bind(),
            albumArtist ?: artist().bind(),
            isCompilation ?: Arb.boolean().bind(),
            year ?: Arb.short(1, Short.MAX_VALUE).bind(),
            label ?: label().bind()
        )
    }

fun Arb.Companion.label(name: String? = null, countryCode: CountryCode? = null) =
    arbitrary {
        ImmutableLabel.of(name ?: Arb.string().bind(), countryCode ?: CountryCode.entries.toTypedArray().random())
    }

fun Arb.Companion.audioFilePath(audioFileType: AudioFileType = Arb.enum<AudioFileType>().next()): Arb<Path> =
    arbitrary {
        val path = Arb.file().bind()
        val fileName = Arb.string().bind()
        val suffix = audioFileType.extension
        Path.of(path.toString(), "$fileName.$suffix")
    }

fun Arb.Companion.audioItemChange(): Arb<AudioItemChange> =
    arbitrary {
        val attributes = audioAttributes().bind()
        AudioItemChange(
            attributes.id,
            attributes.title,
            attributes.artist,
            attributes.album.name,
            attributes.album.albumArtist,
            attributes.album.isCompilation,
            attributes.album.year,
            attributes.album.label,
            attributes.genre,
            attributes.comments,
            attributes.trackNumber,
            attributes.discNumber,
            attributes.bpm,
            attributes.coverImageBytes,
            attributes.playCount
        )
    }

fun Arb.Companion.audioAttributes(
    id: Int? = null,
    path: Path? = null,
    title: String? = null,
    duration: Duration? = null,
    bitRate: Int? = null,
    artist: Artist? = null,
    album: Album? = null,
    genre: Genre? = null,
    comments: String? = null,
    trackNumber: Short? = null,
    discNumber: Short? = null,
    bpm: Float? = null,
    encoder: String? = null,
    encoding: String? = null,
    coverImageBytes: ByteArray? = testCoverBytes,
    dateOfCreation: LocalDateTime? = null,
    lastDateModified: LocalDateTime? = null,
    playCount: Short? = null
): Arb<AudioItemTestAttributes> =
    arbitrary {
        AudioItemTestAttributes(
            path ?: Arb.audioFilePath().bind(),
            title ?: Arb.string().bind(),
            duration ?: Arb.positiveLong().bind().nanoseconds.toJavaDuration(),
            bitRate ?: Arb.positiveInt().bind(),
            artist ?: artist().bind(),
            album ?: album().bind(),
            genre ?: Arb.enum<Genre>().bind(),
            comments ?: Arb.string().bind(),
            trackNumber ?: Arb.positiveShort().bind(),
            discNumber ?: Arb.positiveShort().bind(),
            bpm ?: Arb.float(10.0f..220.58f).bind(),
            encoder ?: Arb.string().bind(),
            encoding ?: Arb.string().bind(),
            coverImageBytes,
            dateOfCreation ?: Arb.localDateTime(LocalDateTime.of(2000, 1, 1, 0, 0)).next(),
            lastDateModified ?: Arb.localDateTime(LocalDateTime.of(2023, 1, 1, 0, 0)).next(),
            playCount ?: Arb.positiveShort().bind(),
            id ?: atomicInteger.getAndDecrement()
        )
    }

private val atomicInteger = AtomicInteger(Integer.MAX_VALUE)