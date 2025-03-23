package net.transgressoft.commons.music.audio

import net.transgressoft.commons.music.AudioUtils.beautifyArtistName
import com.neovisionaries.i18n.CountryCode
import io.kotest.core.TestConfiguration
import io.kotest.engine.spec.tempfile
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.file
import io.kotest.property.arbitrary.float
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.localDateTime
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.next
import io.kotest.property.arbitrary.positiveInt
import io.kotest.property.arbitrary.positiveShort
import io.kotest.property.arbitrary.short
import io.kotest.property.arbitrary.stringPattern
import io.mockk.every
import io.mockk.mockk
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.audio.wav.WavOptions
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.Tag
import org.jaudiotagger.tag.TagOptionSingleton
import org.jaudiotagger.tag.flac.FlacTag
import org.jaudiotagger.tag.id3.ID3v24Tag
import org.jaudiotagger.tag.images.ArtworkFactory
import org.jaudiotagger.tag.mp4.Mp4Tag
import org.jaudiotagger.tag.wav.WavInfoTag
import org.jaudiotagger.tag.wav.WavTag
import java.io.File
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.extension
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.toJavaDuration

internal object AudioItemTestUtil : TestConfiguration() {

    fun File.tag(): Tag = AudioFileIO.read(this).tag

    val mp3File = File("/testfiles/testeable.mp3".asURI())
    val m4aFile = File("/testfiles/testeable.m4a".asURI())
    val flacFile = File("/testfiles/testeable.flac".asURI())
    val wavFile = File("/testfiles/testeable.wav".asURI())
    private val testCover = File("/testfiles/cover.jpg".asURI())
    private val testCover2 = File("/testfiles/cover-2.jpg".asURI())
    val testCoverBytes = Files.readAllBytes(testCover.toPath())
    val testCoverBytes2 = Files.readAllBytes(testCover2.toPath())

    private fun String.asURI(): URI = object {}.javaClass.getResource(this)!!.toURI()

    fun arbitraryMp3File(attributes: AudioItemTestAttributes): Arb<File> =
        arbitrary {
            createTempFileWithTag(
                mp3File, fillTagWithRandomValues(attributes, ID3v24Tag())
            )
        }

    fun arbitraryMp3File(attributes: AudioItemTestAttributes.() -> Unit): Arb<File> =
        arbitrary {
            createTempFileWithTag(
                mp3File, fillTagWithRandomValues(arbitraryAudioAttributes().bind().also(attributes), ID3v24Tag())
            )
        }

    val arbitraryMp3File: Arb<File>
        get() = arbitraryMp3File {}

    fun arbitraryM4aFile(attributes: AudioItemTestAttributes.() -> Unit): Arb<File> =
        arbitrary {
            createTempFileWithTag(
                m4aFile,
                fillTagWithRandomValues(
                    arbitraryAudioAttributes().bind().also(attributes), Mp4Tag()
                )
            )
        }

    val arbitraryM4aFile: Arb<File>
        get() = arbitraryM4aFile {}

    fun arbitraryFlacFile(attributes: AudioItemTestAttributes.() -> Unit): Arb<File> =
        arbitrary {
            createTempFileWithTag(
                flacFile,
                fillTagWithRandomValues(
                    arbitraryAudioAttributes().bind().also(attributes), FlacTag()
                )
            )
        }

    val arbitraryFlacFile: Arb<File>
        get() = arbitraryFlacFile {}

    fun arbitraryWavFile(attributes: AudioItemTestAttributes.() -> Unit): Arb<File> =
        arbitrary {
            createTempFileWithTag(
                wavFile,
                fillTagWithRandomValues(
                    arbitraryAudioAttributes().bind().also(attributes),
                    WavTag(WavOptions.READ_ID3_ONLY).apply {
                        iD3Tag = ID3v24Tag()
                        infoTag = WavInfoTag()
                    }
                )
            )
        }

    val arbitraryWavFile: Arb<File>
        get() = arbitraryWavFile {}

    private fun fillTagWithRandomValues(attributes: AudioItemTestAttributes, tag: Tag): Tag {
        TagOptionSingleton.getInstance().isWriteMp4GenresAsText = true
        TagOptionSingleton.getInstance().isWriteMp3GenresAsText = true

        tag.setField(FieldKey.TITLE, attributes.title)
        tag.setField(FieldKey.ALBUM, attributes.album.name)
        tag.setField(FieldKey.COUNTRY, attributes.artist.countryCode.name)
        tag.setField(FieldKey.ALBUM_ARTIST, attributes.album.albumArtist.name)
        tag.setField(FieldKey.ARTIST, attributes.artist.name)
        tag.setField(FieldKey.GENRE, attributes.genre.name)
        tag.setField(FieldKey.COMMENT, attributes.comments)
        tag.setField(FieldKey.TRACK, attributes.trackNumber.toString())
        tag.setField(FieldKey.DISC_NO, attributes.discNumber.toString())
        tag.setField(FieldKey.YEAR, attributes.album.year.toString())
        tag.setField(FieldKey.ENCODER, attributes.encoder)
        tag.setField(FieldKey.IS_COMPILATION, attributes.album.isCompilation.toString())
        tag.setField(FieldKey.GROUPING, attributes.album.label.name)
        if (tag is Mp4Tag) {
            tag.setField(FieldKey.BPM, attributes.bpm?.toInt().toString())
        } else {
            tag.setField(FieldKey.BPM, attributes.bpm?.toString())
        }
        attributes.coverImageBytes?.let { setArtworkTag(tag, it) }
        return tag
    }

    fun setArtworkTag(tag: Tag, coverBytes: ByteArray?) {
        File.createTempFile("tempCover", ".tmp").apply {
            Files.write(toPath(), coverBytes!!, StandardOpenOption.CREATE)
            deleteOnExit()
            ArtworkFactory.createArtworkFromFile(this).let { artwork ->
                tag.artworkList.clear()
                tag.addField(artwork)
            }
        }
    }

    private fun createTempFileWithTag(testFile: File, tag: Tag): File =
        tempfile(suffix = ".${testFile.extension}")
            .also { file ->
                Files.copy(testFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
                file.deleteOnExit()
                AudioFileIO.read(file).apply {
                    this.tag = tag
                    commit()
                }
            }

    val arbitraryAudioItemChange: Arb<AudioItemChange> =
        arbitrary {
            val attributes = arbitraryAudioAttributes().bind()
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

    private val atomicInteger = AtomicInteger(9999)

    fun arbitraryArtist(givenName: String? = null, countryCode: CountryCode? = null): Arb<Artist> =
        arbitrary {
            ImmutableArtist(
                givenName ?: beautifyArtistName(Arb.stringPattern("[a-z]{5} [a-z]{5}").bind()),
                countryCode ?: CountryCode.entries.toTypedArray().random()
            )
        }

    fun arbitraryLabel(name: String? = null, countryCode: CountryCode? = null) =
        arbitrary {
            ImmutableLabel(name ?: Arb.stringPattern("[a-z]{5} [a-z]{5}").bind(), countryCode ?: CountryCode.entries.toTypedArray().random())
        }

    fun arbitraryAlbum(
        name: String? = null,
        albumArtist: Artist? = null,
        isCompilation: Boolean? = null,
        year: Short? = null,
        label: Label? = null
    ): Arb<Album> =
        arbitrary {
            ImmutableAlbum(
                name ?: Arb.stringPattern("[a-z]{5} [a-z]{5}").bind(),
                albumArtist ?: arbitraryArtist().bind(),
                isCompilation ?: Arb.boolean().bind(),
                year ?: Arb.short(1, Short.MAX_VALUE).bind(),
                label ?: arbitraryLabel().bind()
            )
        }

    fun arbitraryAudioAttributes(
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
                path ?: Arb.file().bind().toPath(),
                title ?: Arb.stringPattern("[a-z]{5} [a-z]{5}").bind(),
                duration ?: Arb.long(1, Long.MAX_VALUE).bind().nanoseconds.toJavaDuration(),
                bitRate ?: Arb.positiveInt().bind(),
                artist ?: arbitraryArtist().bind(),
                album ?: arbitraryAlbum().bind(),
                genre ?: Genre.entries.toTypedArray().random(),
                comments ?: Arb.stringPattern("[a-z]{5} [a-z]{5}").bind(),
                trackNumber ?: Arb.positiveShort().bind(),
                discNumber ?: Arb.positiveShort().bind(),
                bpm ?: Arb.float(10.0f, 220.58f).bind(),
                encoder ?: Arb.stringPattern("[a-z]{5} [a-z]{5}").bind(),
                encoding ?: Arb.stringPattern("[a-z]{5} [a-z]{5}").bind(),
                coverImageBytes,
                dateOfCreation ?: Arb.localDateTime(LocalDateTime.of(2000, 1, 1, 0, 0)).next(),
                lastDateModified ?: Arb.localDateTime(LocalDateTime.of(2023, 1, 1, 0, 0)).next(),
                playCount ?: Arb.positiveShort().bind(),
                id ?: atomicInteger.getAndDecrement()
            )
        }

    fun arbitraryAudioItem(attributesAction: AudioItemTestAttributes.() -> Unit): Arb<MutableAudioItem> =
        arbitrary {
            val attributes = arbitraryAudioAttributes().bind()
            attributesAction(attributes)
            MutableAudioItem(arbitraryMp3File(attributes).bind().toPath(), attributes.id)
        }

    val arbitraryAudioItem
        get() = arbitraryAudioItem {}

    fun arbitraryAlbumAudioItems(
        artist: Artist? = null,
        album: Album? = null,
        size: IntRange = 3..10
    ): Arb<List<AudioItem>> =
        arbitrary {
            val arbitraryArtist = artist ?: arbitraryArtist().bind()
            val arbitraryAlbum = album ?: arbitraryAlbum().bind()
            buildList {
                repeat(Arb.int(size).bind()) {
                    add(
                        arbitraryAudioItem {
                            this.artist = arbitraryArtist
                            this.album = arbitraryAlbum
                            this.trackNumber = (it.plus(1)).toShort()
                            this.coverImageBytes = null
                        }.bind()
                    )
                }
            }
        }

    fun AudioItem.update(change: AudioItemChange) {
        change.title?.let { title = it }
        change.artist?.let { artist = it }
        album =
            ImmutableAlbum(
                change.albumName ?: album.name,
                change.albumArtist ?: album.albumArtist,
                change.isCompilation ?: album.isCompilation,
                change.year?.takeIf { year -> year > 0 } ?: album.year,
                change.label ?: album.label
            )
        change.genre ?: genre
        change.comments ?: comments
        change.trackNumber?.takeIf { trackNum -> trackNum > 0 } ?: trackNumber
        change.discNumber?.takeIf { discNum -> discNum > 0 } ?: discNumber
        change.bpm?.takeIf { bpm -> bpm > 0 } ?: bpm
        change.coverImageBytes ?: coverImageBytes
    }

    fun AudioItem.update(changeAction: AudioItemChange.() -> Unit) {
        val change = AudioItemChange(id).also(changeAction)
        update(change)
    }

    fun AudioItem.asJsonKeyValue() = """
        {
            "$id": ${asJsonValue()}
        }
    """

    fun AudioItem.asJsonValue() = """
       {
            "id": $id,
            "path": "$path",
            "title": "$title",
            "duration": ${duration.toSeconds()},
            "bitRate": $bitRate,
            "artist": {
                "name": "${artist.name}",
                "countryCode": "${artist.countryCode.name}"
            },
            "album": {
                "name": "${album.name}",
                "albumArtist": {
                    "name": "${album.albumArtist.name}"
                },
                "isCompilation": ${album.isCompilation},
                "year": ${album.year},
                "label": {
                    "name": "${album.label.name}"
                }
            },
            "genre": "${genre.name}",
            "comments": "$comments",
            "trackNumber": $trackNumber,
            "discNumber": $discNumber,
            "bpm": $bpm,
            "encoder": "$encoder",
            "encoding": "$encoding",
            "dateOfCreation": ${dateOfCreation.toEpochSecond(ZoneOffset.UTC)},
            "lastDateModified": ${lastDateModified.toEpochSecond(ZoneOffset.UTC)},
            "playCount": $playCount
        }
    """

    fun createMockedAudioFilePaths(count: Int): List<Path> {
        val formats = listOf("mp3", "flac", "wav", "m4a")
        return (1..count).map { index ->
            val format = formats[index % formats.size]
            createMockedAudioFilePath(index, format)
        }
    }

    fun createMockedAudioFilePath(index: Int, format: String): Path {
        val fileName = "test-file-$index.$format"
        val mockFile =
            mockk<File> {
                every { extension } returns format
                every { path } returns "/mocked/path/$fileName"
            }

        val fileNameMock =
            mockk<Path> {
                every { this@mockk.toString() } returns fileName
            }

        val mockPath =
            mockk<Path> {
                every { toFile() } returns mockFile
                every { this@mockk.fileName } returns fileNameMock
            }
        every { mockFile.toPath() } returns mockPath

        // Mock the audio file reading process based on format
        val audioFileIO =
            mockk<org.jaudiotagger.audio.AudioFile> {
                val tag =
                    when (format) {
                        "mp3" -> createMockedTag(mockk<ID3v24Tag>(), index)
                        "flac" -> createMockedTag(mockk<FlacTag>(), index)
                        "wav" ->
                            createMockedTag(
                                mockk<WavTag> {
                                    every { iD3Tag } returns mockk<ID3v24Tag>()
                                    every { infoTag } returns mockk<WavInfoTag>()
                                },
                                index
                            )
                        "m4a" -> createMockedTag(mockk<Mp4Tag>(), index)
                        else -> createMockedTag(mockk<ID3v24Tag>(), index)
                    }

                every { getTag() } returns tag
                every { audioHeader } returns
                    mockk {
                        every { trackLength } returns 180
                        every { sampleRateAsNumber } returns 44100
                        every { encodingType } returns format.uppercase()
                        every { bitRate } returns "320"
                    }
            }

        every { Files.exists(mockPath) } returns true
        every { AudioFileIO.read(mockFile) } returns audioFileIO

        return mockPath
    }

    fun createMockedTag(tag: Tag, index: Int): Tag {
        val artist = "Artist $index"
        val album = "Album ${index / 10}"
        val albumArtist = "Various Artists"
        val title = "Track $index"

        every { tag.getFirst(FieldKey.TITLE) } returns title
        every { tag.getFirst(FieldKey.ALBUM) } returns album
        every { tag.getFirst(FieldKey.ARTIST) } returns artist
        every { tag.getFirst(FieldKey.ALBUM_ARTIST) } returns albumArtist
        every { tag.getFirst(FieldKey.GENRE) } returns "Pop"
        every { tag.getFirst(FieldKey.YEAR) } returns "2023"
        every { tag.getFirst(FieldKey.TRACK) } returns "${index % 10 + 1}"
        every { tag.getFirst(FieldKey.DISC_NO) } returns "1"
        every { tag.getFirst(FieldKey.BPM) } returns "120"
        every { tag.getFirst(FieldKey.COMMENT) } returns "Test comment $index"
        every { tag.getFirst(FieldKey.ENCODER) } returns "Test encoder"
        every { tag.getFirst(FieldKey.GROUPING) } returns "Test Label"
        every { tag.getFirst(FieldKey.IS_COMPILATION) } returns "false"
        every { tag.getFirst(FieldKey.COUNTRY) } returns "US"

        every { tag.hasField(FieldKey.TITLE) } returns true
        every { tag.hasField(FieldKey.ALBUM) } returns true
        every { tag.hasField(FieldKey.ARTIST) } returns true
        every { tag.hasField(FieldKey.ALBUM_ARTIST) } returns true
        every { tag.hasField(FieldKey.GENRE) } returns true
        every { tag.hasField(FieldKey.YEAR) } returns true
        every { tag.hasField(FieldKey.TRACK) } returns true
        every { tag.hasField(FieldKey.DISC_NO) } returns true
        every { tag.hasField(FieldKey.BPM) } returns true
        every { tag.hasField(FieldKey.COMMENT) } returns true
        every { tag.hasField(FieldKey.ENCODER) } returns true
        every { tag.hasField(FieldKey.GROUPING) } returns true
        every { tag.hasField(FieldKey.IS_COMPILATION) } returns true
        every { tag.hasField(FieldKey.COUNTRY) } returns true

        // Mock artwork list to return empty list to avoid complex bitmap mocking
        every { tag.artworkList } returns emptyList()
        every { tag.artworkList } returns emptyList()

        return tag
    }
}

data class AudioItemTestAttributes(
    var path: Path,
    var title: String,
    var duration: Duration,
    var bitRate: Int,
    var artist: Artist,
    var album: Album,
    var genre: Genre,
    var comments: String? = null,
    var trackNumber: Short? = null,
    var discNumber: Short? = null,
    var bpm: Float? = null,
    var encoder: String? = null,
    var encoding: String? = null,
    var coverImageBytes: ByteArray? = null,
    var dateOfCreation: LocalDateTime,
    var lastDateModified: LocalDateTime,
    var playCount: Short,
    var id: Int = UNASSIGNED_ID
)