package net.transgressoft.commons.music.audio

import com.neovisionaries.i18n.CountryCode
import net.transgressoft.commons.music.audio.AudioItemUtils.beautifyArtistName
import net.transgressoft.commons.music.audio.AudioItemUtils.getArtistsNamesInvolved
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.audio.AudioHeader
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.Tag
import java.nio.file.Path
import java.time.Duration
import java.time.LocalDateTime
import kotlin.io.path.extension

internal class JAudioTaggerMetadataReader(private val audioItemPath: Path) : AudioItemMetadataReader {

    private val title: String by lazy { getFieldIfExisting(FieldKey.TITLE) ?: "" }
    private val artist: Artist by lazy { readArtist() }
    private val artistsInvolved: Set<String> by lazy { getArtistsNamesInvolved(title, artist.name, album.albumArtist.name) }
    private val album: Album by lazy { readAlbum(extension) }
    private val duration: Duration
    private val genre: Genre by lazy { getFieldIfExisting(FieldKey.GENRE)?.let { Genre.parseGenre(it) } ?: Genre.UNDEFINED }
    private val comments: String? by lazy { getFieldIfExisting(FieldKey.COMMENT) }
    private val trackNumber: Short? by lazy { getFieldIfExisting(FieldKey.TRACK)?.takeIf { it != "0" }?.toShort() }
    private val discNumber: Short? by lazy { getFieldIfExisting(FieldKey.DISC_NO)?.takeIf { it != "0" }?.toShort() }
    private val bpm: Float? by lazy { getFieldIfExisting(FieldKey.BPM)?.takeIf { (it != "0") }?.toFloat() }
    private val encoder: String? by lazy { getFieldIfExisting(FieldKey.ENCODER) }
    private var bitRate: Int
    private val encoding: String?

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
    }

    override fun readAudioItemAttributes() =
        AudioItemAttributes(
            audioItemPath, title, artist, album, genre, comments, trackNumber, discNumber,
            bpm, duration, bitRate, encoder, encoding, LocalDateTime.now(), LocalDateTime.now()
        )

    private fun getBitRate(audioHeader: AudioHeader): Int {
        val bitRate = audioHeader.bitRate
        return if ("~" == bitRate.substring(0, 1)) {
            bitRate.substring(1).toInt()
        } else {
            bitRate.toInt()
        }
    }

    private fun getFieldIfExisting(fieldKey: FieldKey): String? = tag.hasField(fieldKey).takeIf { true }.run { tag.getFirst(fieldKey) }

    private fun readArtist(): Artist =
        getFieldIfExisting(FieldKey.ARTIST)?.let {
            val country = getFieldIfExisting(FieldKey.COUNTRY)?.let { _country -> CountryCode.valueOf(_country) } ?: CountryCode.UNDEFINED
            ImmutableArtist(beautifyArtistName(it), country)
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
                val year = getFieldIfExisting(FieldKey.YEAR)?.toShort()
                val label = getFieldIfExisting(FieldKey.GROUPING)?.let { ImmutableLabel(it) } as Label
                val coverBytes = tag.artworkList.isNotEmpty().takeIf { true }?.let { tag.firstArtwork.binaryData }
                ImmutableAlbum(this, ImmutableArtist(beautifyArtistName(albumArtistName)), isCompilation, year, label, coverBytes)
            }
        }
}
