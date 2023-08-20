package net.transgressoft.commons.music.audio

import com.neovisionaries.i18n.CountryCode
import io.kotest.core.TestConfiguration
import io.kotest.engine.spec.tempfile
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import net.transgressoft.commons.music.AudioUtils
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
import java.nio.file.*
import java.time.Duration
import java.time.LocalDateTime
import java.time.Month
import java.util.concurrent.atomic.AtomicInteger
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

    private fun String.asURI(): URI = object {}.javaClass.getResource(this)!!.toURI()

    private fun createTempFileWithTag(suffix: String, testFile: File, tag: Tag): File =
        tempfile(suffix = suffix).also { file ->
            Files.copy(testFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            file.deleteOnExit()
            AudioFileIO.read(file).apply {
                this.tag = tag
                commit()
            }
        }

    val arbitraryMp3File = arbitrary { createTempFileWithTag(".mp3", mp3File, fillTagWithRandomValues(ID3v24Tag())) }
    val arbitraryM4aFile = arbitrary { createTempFileWithTag(".m4a", m4aFile, fillTagWithRandomValues(Mp4Tag())) }
    val arbitraryFlacFile = arbitrary { createTempFileWithTag(".flac", flacFile, fillTagWithRandomValues(FlacTag())) }
    val arbitraryWavFile = arbitrary {
        createTempFileWithTag(
            ".wav", wavFile, fillTagWithRandomValues(WavTag(WavOptions.READ_ID3_ONLY).apply {
                iD3Tag = ID3v24Tag()
                infoTag = WavInfoTag()
            })
        )
    }

    val arbitraryAudioItemChange = arbitrary {
        AudioItemMetadataChange(
            title = Arb.stringPattern("[a-z]{5} [a-z]{5}").bind(),
            artist = ImmutableArtist(
                AudioUtils.beautifyArtistName(Arb.stringPattern("[a-z]{5} [a-z]{5}").bind()),
                CountryCode.values().random()
            ),
            albumName = Arb.stringPattern("[a-z]{5} [a-z]{5}").bind(),
            albumArtist = ImmutableArtist(AudioUtils.beautifyArtistName(Arb.stringPattern("[a-z]{5} [a-z]{5}").bind())),
            isCompilation = Arb.boolean().bind(),
            year = Arb.short().bind(),
            label = ImmutableLabel(Arb.stringPattern("[a-z]{5} [a-z]{5}").bind()),
            coverImage = Files.readAllBytes(testCover2.toPath()),
            genre = Genre.values().random(),
            comments = Arb.stringPattern("[a-z]{5} [a-z]{5}").bind(),
            trackNumber = Arb.short().bind(),
            discNumber = Arb.short().bind(),
            bpm = Arb.positiveInt(230).bind().toFloat()
        )
    }

    private fun fillTagWithRandomValues(tag: Tag): Tag {
        TagOptionSingleton.getInstance().isWriteMp4GenresAsText = true
        TagOptionSingleton.getInstance().isWriteMp3GenresAsText = true

        tag.setField(FieldKey.TITLE, Arb.stringPattern("[a-z]{5} [a-z]{5}").next())
        tag.setField(FieldKey.ALBUM, Arb.stringPattern("[a-z]{5} [a-z]{5}").next())
        tag.setField(FieldKey.COUNTRY, CountryCode.values().random().name)
        tag.setField(FieldKey.ALBUM_ARTIST, Arb.stringPattern("[a-z]{5} [a-z]{5}").next())
        tag.setField(FieldKey.ARTIST, Arb.stringPattern("[a-z]{5} [a-z]{5}").next())
        tag.setField(FieldKey.GENRE, Genre.values().random().name)
        tag.setField(FieldKey.COMMENT, Arb.stringPattern("[a-z]{5} [a-z]{5}").next())
        tag.setField(FieldKey.GENRE, Arb.stringPattern("[a-z]{5} [a-z]{5}").next())
        tag.setField(FieldKey.TRACK, Arb.short().next().toString())
        tag.setField(FieldKey.DISC_NO, Arb.short().next().toString())
        tag.setField(FieldKey.YEAR, Arb.short().next().toString())
        tag.setField(FieldKey.ENCODER, Arb.stringPattern("[a-z]{5} [a-z]{5}").next())
        tag.setField(FieldKey.IS_COMPILATION, Arb.boolean().next().toString())
        if (tag is Mp4Tag) {
            tag.setField(FieldKey.BPM, Arb.int(-1, 220).next().toString())
        } else {
            tag.setField(FieldKey.BPM, Arb.float(-1.0f, 220.58f).next().toString())
        }
        setArtworkTag(tag, testCoverBytes)
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

    val defaultArtistName = "DEFAULT_ARTIST_NAME"

    fun arbitraryArtist(givenName: String = defaultArtistName): Arb<Artist> = Arb.bind(
        Arb.string(),
        Arb.enum<CountryCode>()
    ) { name: String, countryCode: CountryCode ->
        ImmutableArtist(
            if (givenName == defaultArtistName) name else givenName,
            countryCode
        )
    }

    val defaultLabelName = "DEFAULT_LABEL_NAME"

    fun arbitraryLabel(
        name: String = defaultLabelName,
        countryCode: CountryCode = CountryCode.values().random(),
    ) = arbitrary {
        ImmutableLabel(if (name == defaultLabelName) Arb.string().bind() else defaultLabelName, countryCode)
    }

    val defaultAlbumName = "DEFAULT_ALBUM_NAME"
    val defaultArtist = ImmutableArtist("DEFAULT_ARTIST")
    val defaultIsCompilation = false
    val defaultYear = 1931.toShort()
    val defaultLabel = ImmutableLabel("DEFAULT_LABEL")

    fun arbitraryAlbum(
        name: String = defaultAlbumName,
        albumArtist: Artist = defaultArtist,
        isCompilation: Boolean = defaultIsCompilation,
        year: Short = defaultYear,
        label: Label = defaultLabel,
    ): Arb<Album> = arbitrary {
        ImmutableAlbum(
            if (name == defaultAlbumName) Arb.string().bind() else name,
            if (albumArtist == defaultArtist) arbitraryArtist().bind() else albumArtist,
            if (isCompilation == defaultIsCompilation) Arb.boolean().bind() else defaultIsCompilation,
            if (year == defaultYear) Arb.short().bind() else year,
            if (label == defaultLabel) arbitraryLabel().bind() else label
        )
    }

    val defaultInt = 0
    val defaultPath = Paths.get(System.getProperty("user.home"))
    val defaultTitle = "DEFAULT_TITLE"
    val defaultDuration = Duration.ofSeconds(23)
    val defaultBitRate = 320
    val defaultAlbum = ImmutableAlbum("DEFAULT_ALBUM", defaultArtist)
    val defaultComments = "DEFAULT_COMMENTS"
    val defaultTrackNumber = 1.toShort()
    val defaultDiscNumber = 1.toShort()
    val defaultBpm = 128f
    val defaultEncoder = "DEFAULT_ENCODER"
    val defaultEncoding = "DEFAULT_ENCODING"
    val defaultDateOfCreation = LocalDateTime.of(1931, Month.APRIL, 14, 0, 0, 0)
    val defaultDateOfModification = defaultDateOfCreation.plusYears(23)

    private val atomicInteger = AtomicInteger(9999)

    fun arbitraryAudioItem(
        id: Int = defaultInt,
        path: Path = defaultPath,
        title: String = defaultTitle,
        duration: Duration = defaultDuration,
        bitRate: Int = defaultBitRate,
        artist: Artist = defaultArtist,
        album: Album = defaultAlbum,
        genre: Genre = Genre.values().random(),
        comments: String? = defaultComments,
        trackNumber: Short? = defaultTrackNumber,
        discNumber: Short? = defaultDiscNumber,
        bpm: Float? = defaultBpm,
        encoder: String? = defaultEncoder,
        encoding: String? = defaultEncoding,
        coverImage: ByteArray? = testCoverBytes,
        dateOfCreation: LocalDateTime = defaultDateOfCreation,
        lastDateModified: LocalDateTime = defaultDateOfModification,
    ) = arbitrary {
        ImmutableAudioItem(
            if (id == defaultInt) atomicInteger.getAndDecrement() else id,
            if (path == defaultPath) Arb.file().bind().toPath() else path,
            if (title == defaultTitle) Arb.stringPattern("[a-z]{5} [a-z]{5}").bind() else title,
            if (duration == defaultDuration) Arb.long(1, Long.MAX_VALUE).bind().nanoseconds.toJavaDuration() else duration,
            if (bitRate == defaultBitRate) Arb.positiveInt().bind() else bitRate,
            if (artist == defaultArtist) arbitraryArtist().bind() else artist,
            if (album == defaultAlbum) arbitraryAlbum().bind() else album,
            genre,
            if (comments == defaultComments) Arb.stringPattern("[a-z]{5} [a-z]{5}").bind() else comments,
            if (trackNumber == defaultTrackNumber) Arb.short().bind() else trackNumber,
            if (discNumber == defaultDiscNumber) Arb.short().bind() else discNumber,
            if (bpm == defaultBpm) Arb.float(-1.0f, 220.58f).bind() else bpm,
            if (encoder == defaultEncoder) Arb.stringPattern("[a-z]{5} [a-z]{5}").bind() else encoder,
            if (encoding == defaultEncoding) Arb.stringPattern("[a-z]{5} [a-z]{5}").bind() else encoding,
            coverImage,
            if (dateOfCreation == defaultDateOfCreation) Arb.localDateTime().next() else dateOfCreation,
            if (lastDateModified == defaultDateOfModification) lastDateModified else lastDateModified
        )
    }
}