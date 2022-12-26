package net.transgressoft.commons.music.audio

import com.neovisionaries.i18n.CountryCode
import net.transgressoft.commons.music.audio.AudioItemUtils.beautifyArtistName
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.audio.AudioHeader
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.Tag
import java.nio.file.Path
import java.time.Duration
import kotlin.io.path.extension

abstract class JAudioTaggerMetadataReaderBase<I : AudioItem>(audioItemPath: Path) : AudioItemMetadataReader<I> {

    protected val title: String by lazy { getFieldIfExisting(FieldKey.TITLE) ?: "" }
    protected val artist: Artist by lazy { readArtist() }
    protected val album: Album
    protected val duration: Duration
    protected val genre: Genre by lazy { getFieldIfExisting(FieldKey.GENRE)?.let { Genre.parseGenre(it) } ?: Genre.UNDEFINED }
    protected val comments: String? by lazy { getFieldIfExisting(FieldKey.COMMENT) }
    protected val trackNumber: Short? by lazy { getFieldIfExisting(FieldKey.TRACK)?.takeIf { it.isNotEmpty().and(it != "0") }?.toShortOrNull()?.takeIf { it > 0 } }
    protected val discNumber: Short? by lazy { getFieldIfExisting(FieldKey.DISC_NO)?.takeIf { it.isNotEmpty().and(it != "0") }?.toShortOrNull()?.takeIf { it > 0 } }
    protected val bpm: Float? by lazy { getFieldIfExisting(FieldKey.BPM)?.takeIf { it.isNotEmpty().and(it != "0") }?.toFloatOrNull()?.takeIf { it > 0 } }
    protected val encoder: String? by lazy { getFieldIfExisting(FieldKey.ENCODER) }
    protected var bitRate: Int
    protected val encoding: String?

    private val tag: Tag
    private val extension: String = audioItemPath.extension

    init {
        val audioItemFile = audioItemPath.toFile()
        val audioFile = AudioFileIO.read(audioItemFile)
        tag = audioFile.tag
        val audioHeader = audioFile.audioHeader
        encoding = audioHeader.encodingType
        duration = Duration.ofSeconds(audioHeader.trackLength.toLong())
        bitRate = getBitRate(audioHeader)
        album = readAlbum(extension)
    }

    private fun getFieldIfExisting(fieldKey: FieldKey): String? = tag.hasField(fieldKey).takeIf { it }.run { tag.getFirst(fieldKey) }

    private fun getBitRate(audioHeader: AudioHeader): Int {
        val bitRate = audioHeader.bitRate
        return if ("~" == bitRate.substring(0, 1)) {
            bitRate.substring(1).toInt()
        } else {
            bitRate.toInt()
        }
    }

    private fun readArtist(): Artist =
        getFieldIfExisting(FieldKey.ARTIST)?.let { artistName ->
            val country = getFieldIfExisting(FieldKey.COUNTRY)?.let { _country ->
                if (_country.isNotEmpty())
                    CountryCode.valueOf(_country)
                else CountryCode.UNDEFINED
            } ?: CountryCode.UNDEFINED
            ImmutableArtist(beautifyArtistName(artistName), country)
        } ?: ImmutableArtist("")

    private fun readAlbum(extension: String): Album =
        with(getFieldIfExisting(FieldKey.ALBUM)) {
            return if (this == null) {
                ImmutableAlbum.UNKNOWN
            } else {
                val albumArtistName = getFieldIfExisting(FieldKey.ALBUM_ARTIST) ?: ""
                val isCompilation = getFieldIfExisting(FieldKey.IS_COMPILATION)?.let {
                    if ("m4a" == extension) "1" == tag.getFirst(FieldKey.IS_COMPILATION)
                    else "true" == tag.getFirst(FieldKey.IS_COMPILATION)
                } ?: false
                val year = getFieldIfExisting(FieldKey.YEAR)?.toShortOrNull()?.takeIf { it > 0 }
                val label = getFieldIfExisting(FieldKey.GROUPING)?.let { ImmutableLabel(it) } as Label
                val coverBytes = tag.artworkList.isNotEmpty().takeIf { true }?.let { tag.firstArtwork.binaryData }
                ImmutableAlbum(this, ImmutableArtist(beautifyArtistName(albumArtistName)), isCompilation, year, label, coverBytes)
            }
        }
}
