package net.transgressoft.commons.music.audio

import mu.KotlinLogging
import org.apache.commons.io.FilenameUtils
import org.apache.commons.text.WordUtils
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.audio.AudioHeader
import org.jaudiotagger.audio.exceptions.CannotReadException
import org.jaudiotagger.audio.exceptions.InvalidAudioFrameException
import org.jaudiotagger.audio.exceptions.ReadOnlyFileException
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.Tag
import org.jaudiotagger.tag.TagException
import java.nio.file.Path
import java.time.Duration
import java.time.LocalDateTime
import java.util.*

internal class JAudioTaggerMetadataReader {

    private val logger = KotlinLogging.logger {}

    @Throws(AudioItemManipulationException::class)
    fun readAudioItem(audioItemPath: Path): AudioItemAttributes {
        logger.debug { "Parsing file $audioItemPath" }

        val result: AudioItemAttributes = try {

            val attributes = SimpleAudioItemAttributes()

            val audioItemFile = audioItemPath.toFile()
            val extension = FilenameUtils.getExtension(audioItemFile.name)
            val audioFile = AudioFileIO.read(audioItemFile)
            val tag = audioFile.tag
            val title = if (tag.hasField(FieldKey.TITLE)) tag.getFirst(FieldKey.TITLE) else ""
            val audioHeader = audioFile.audioHeader
            val duration = Duration.ofSeconds(audioHeader.trackLength.toLong())
            val bitRate = getBitRate(audioHeader)

            readMetadata(attributes, tag)
            readAlbum(attributes, extension, tag)

            attributes[AudioItemPathAttribute.PATH] = audioItemPath
            attributes[AudioItemStringAttribute.TITLE] = title
            attributes[AudioItemDurationAttribute.DURATION] = duration
            attributes[AudioItemIntegerAttribute.BITRATE] = bitRate
            attributes[AudioItemLocalDateTimeAttribute.DATE_OF_CREATION] = LocalDateTime.now()
            attributes[AudioItemStringAttribute.ENCODING] = audioHeader.encodingType
            attributes
        } catch (exception: Exception) {
            when (exception) {
                is CannotReadException, is ReadOnlyFileException, is TagException, is InvalidAudioFrameException -> {
                    val errorMessage = "Error parsing file $audioItemPath"
                    logger.debug(errorMessage, audioItemPath, exception)
                    throw AudioItemManipulationException(errorMessage, exception)
                }
                else -> {
                    throw exception
                }
            }
        }

        return result
    }

    private fun getBitRate(audioHeader: AudioHeader): Int {
        val bitRate = audioHeader.bitRate
        return if ("~" == bitRate.substring(0, 1)) {
            bitRate.substring(1).toInt()
        } else {
            bitRate.toInt()
        }
    }

    private fun readMetadata(attributes: AudioItemAttributes, tag: Tag) {
        if (tag.hasField(FieldKey.ARTIST)) {
            if (tag.hasField(FieldKey.COUNTRY)) {
                val country = tag.getFirst(FieldKey.COUNTRY)
                val countryCode = Locale.IsoCountryCode.valueOf(country)
                attributes[ArtistAttribute.ARTIST] = ImmutableArtist(beautifyArtistName(tag.getFirst(FieldKey.ARTIST)), countryCode)
            } else {
                attributes[ArtistAttribute.ARTIST] = ImmutableArtist(beautifyArtistName(tag.getFirst(FieldKey.ARTIST)))
            }
        }
        if (tag.hasField(FieldKey.GENRE)) {
            attributes[AudioItemStringAttribute.GENRE_NAME] = Genre.parseGenre(tag.getFirst(FieldKey.GENRE)).name
        }
        if (tag.hasField(FieldKey.COMMENT)) {
            attributes[AudioItemStringAttribute.COMMENTS] = tag.getFirst(FieldKey.COMMENT)
        }
        if (tag.hasField(FieldKey.ENCODER)) {
            attributes[AudioItemStringAttribute.ENCODER] = tag.getFirst(FieldKey.ENCODER)
        }
        if (tag.hasField(FieldKey.BPM)) {
            try {
                val bpm = tag.getFirst(FieldKey.BPM).toInt()
                attributes[AudioItemFloatAttribute.BPM] = (if (bpm < 1) 0 else bpm).toFloat()
            } catch (_: NumberFormatException) {
            }
        }
        if (tag.hasField(FieldKey.DISC_NO)) {
            try {
                val dn = tag.getFirst(FieldKey.DISC_NO).toShort()
                attributes[AudioItemShortAttribute.DISC_NUMBER] = (if (dn < 1) 0 else dn)
            } catch (_: NumberFormatException) {
            }
        }
        if (tag.hasField(FieldKey.TRACK)) {
            try {
                val trackNumber = tag.getFirst(FieldKey.TRACK).toShort()
                attributes[AudioItemShortAttribute.TRACK_NUMBER] = (if (trackNumber < 1) 0 else trackNumber)
            } catch (_: NumberFormatException) {
            }
        }
    }

    private fun readAlbum(attributes: AudioItemAttributes, extension: String, tag: Tag) {
        val album: Album
        var albumName = ""
        var albumArtist = ImmutableArtist.UNKNOWN
        var isCompilation = false
        var year: Short = -1
        var label = ImmutableLabel.UNKNOWN
        var coverBytes: ByteArray? = null
        if (tag.hasField(FieldKey.ALBUM)) albumName = tag.getFirst(FieldKey.ALBUM)
        if (tag.hasField(FieldKey.ALBUM_ARTIST)) {
            val artistAlbumName = tag.getFirst(FieldKey.ALBUM_ARTIST)
            albumArtist = ImmutableArtist(beautifyArtistName(artistAlbumName))
        }
        if (tag.hasField(FieldKey.GROUPING)) {
            val labelName = tag.getFirst(FieldKey.GROUPING)
            label = ImmutableLabel(labelName)
        }
        if (tag.hasField(FieldKey.YEAR)) {
            try {
                year = tag.getFirst(FieldKey.YEAR).toShort()
            } catch (_: NumberFormatException) {
            }
        }
        if (tag.hasField(FieldKey.IS_COMPILATION)) {
            isCompilation =
                if ("m4a" == extension) "1" == tag.getFirst(FieldKey.IS_COMPILATION)
                else "true" == tag.getFirst(FieldKey.IS_COMPILATION)
        }
        if (tag.artworkList.isNotEmpty()) coverBytes = tag.firstArtwork.binaryData
        album = ImmutableAlbum(albumName, albumArtist, isCompilation, year, label, coverBytes)
        attributes[AlbumAttribute.ALBUM] = album
    }

    private fun beautifyArtistName(name: String): String {
        return WordUtils.capitalize(name)
            .replace("\\s+".toRegex(), " ")
            .replace(" (?i)(vs)(\\.|\\s)".toRegex(), " vs ")
            .replace(" (?i)(versus) ".toRegex(), " versus ")
    }
}
