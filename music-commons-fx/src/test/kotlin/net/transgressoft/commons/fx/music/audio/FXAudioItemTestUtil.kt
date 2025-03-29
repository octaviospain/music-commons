package net.transgressoft.commons.fx.music.audio

import net.transgressoft.commons.entity.IdentifiableEntity
import net.transgressoft.commons.music.AudioUtils.beautifyArtistName
import net.transgressoft.commons.music.audio.Album
import net.transgressoft.commons.music.audio.Artist
import net.transgressoft.commons.music.audio.Genre
import net.transgressoft.commons.music.audio.ImmutableAlbum
import net.transgressoft.commons.music.audio.ImmutableArtist
import net.transgressoft.commons.music.audio.ImmutableLabel
import net.transgressoft.commons.music.audio.Label
import net.transgressoft.commons.music.audio.UNASSIGNED_ID
import com.neovisionaries.i18n.CountryCode
import io.kotest.core.TestConfiguration
import io.kotest.engine.spec.tempfile
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.file
import io.kotest.property.arbitrary.float
import io.kotest.property.arbitrary.localDateTime
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.next
import io.kotest.property.arbitrary.positiveInt
import io.kotest.property.arbitrary.positiveShort
import io.kotest.property.arbitrary.short
import io.kotest.property.arbitrary.stringPattern
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.Tag
import org.jaudiotagger.tag.TagOptionSingleton
import org.jaudiotagger.tag.id3.ID3v24Tag
import org.jaudiotagger.tag.images.ArtworkFactory
import org.jaudiotagger.tag.mp4.Mp4Tag
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
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.toJavaDuration

internal object FXAudioItemTestUtil : TestConfiguration() {

    val mp3File = File("/testfiles/testeable.mp3".asURI())
    val testCoverBytes = Files.readAllBytes(File("/testfiles/cover.jpg".asURI()).toPath())

    private fun String.asURI(): URI = FXAudioItemTestUtil.javaClass.getResource(this)!!.toURI()

    val arbitraryMp3File: Arb<File>
        get() = arbitraryMp3File {}

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

    private val atomicInteger = AtomicInteger(9999)

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
                attributes.coverImageBytes
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
        lastDateModified: LocalDateTime? = null
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
                id ?: atomicInteger.getAndDecrement()
            )
        }

    fun arbitraryArtist(givenName: String? = null, countryCode: CountryCode? = null): Arb<Artist> =
        arbitrary {
            ImmutableArtist.of(
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

    fun FXAudioItem.asJsonKeyValue() = """
        {
            "$id": ${asJsonValue()}
        }
    """

    fun FXAudioItem.asJsonValue() = """
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

    fun arbitraryAudioItem(attributesAction: AudioItemTestAttributes.() -> Unit): Arb<FXAudioItem> =
        arbitrary {
            val attributes = arbitraryAudioAttributes().bind()
            attributesAction(attributes)
            FXAudioItem(arbitraryMp3File(attributes).bind().toPath(), attributes.id)
        }

    val arbitraryAudioItem
        get() = arbitraryAudioItem {}

    fun FXAudioItem.update(change: AudioItemChange) {
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

    fun FXAudioItem.update(changeAction: AudioItemChange.() -> Unit) {
        val change = AudioItemChange(id).also(changeAction)
        update(change)
    }
}

data class AudioItemChange(
    override val id: Int,
    var title: String? = null,
    var artist: Artist? = null,
    var albumName: String? = null,
    var albumArtist: Artist? = null,
    var isCompilation: Boolean? = null,
    var year: Short? = null,
    var label: Label? = null,
    var genre: Genre? = null,
    var comments: String? = null,
    var trackNumber: Short? = null,
    var discNumber: Short? = null,
    var bpm: Float? = null,
    var coverImageBytes: ByteArray? = null
) : IdentifiableEntity<Int> {

    override val uniqueId: String = id.toString()

    var album: Album? = null
        set(value) {
            field = value
            albumName = value?.name
            albumArtist = value?.albumArtist
            isCompilation = value?.isCompilation
            year = value?.year
            label = value?.label
        }

    override fun clone(): AudioItemChange = copy()
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
    var id: Int = UNASSIGNED_ID
)