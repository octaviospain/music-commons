package net.transgressoft.commons.music.audio

import mu.KotlinLogging
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.audio.wav.WavOptions
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.Tag
import org.jaudiotagger.tag.TagOptionSingleton
import org.jaudiotagger.tag.flac.FlacTag
import org.jaudiotagger.tag.id3.ID3v24Tag
import org.jaudiotagger.tag.images.Artwork
import org.jaudiotagger.tag.images.ArtworkFactory
import org.jaudiotagger.tag.mp4.Mp4Tag
import org.jaudiotagger.tag.wav.WavInfoTag
import org.jaudiotagger.tag.wav.WavTag
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

/**
 * @author Octavio Calleya
 */
internal class JAudioTaggerMetadataWriter {

    private val logger = KotlinLogging.logger {}

    @Throws(AudioItemManipulationException::class)
    fun writeMetadata(audioItem: AudioItem) {
        logger.debug { "Writing metadata of $audioItem to file '${audioItem.path.toAbsolutePath()}'" }

        val audioFile = audioItem.path.toFile()
        try {
            val audio = AudioFileIO.read(audioFile)
            createTagTag(audioItem, audio.audioHeader.format).let {
                audio.tag = it
            }

            audio.commit()
            logger.debug { "Metadata of $audioItem successfully written to file" }
        } catch (exception: Exception) {
            val errorText = "Error writing metadata of $audioItem"
            logger.error(errorText, exception)
            throw AudioItemManipulationException(errorText, exception)
        }
    }

    private fun createTagTag(audioItem: AudioItem, format: String): Tag {
        return when {
            format.startsWith("Wav", ignoreCase = true) -> {
                val wavTag = WavTag(WavOptions.READ_ID3_ONLY)
                wavTag.iD3Tag = ID3v24Tag()
                wavTag.infoTag = WavInfoTag()
                wavTag
            }

            format.startsWith("Mp3", ignoreCase = true) -> {
                TagOptionSingleton.getInstance().isWriteMp3GenresAsText = true
                val tag: Tag = ID3v24Tag()
                tag.artworkList.clear()
                tag
            }

            format.startsWith("Flac", ignoreCase = true) -> {
                val tag: Tag = FlacTag()
                tag.artworkList.clear()
                tag
            }

            format.startsWith("Aac", ignoreCase = true) -> {
                TagOptionSingleton.getInstance().isWriteMp4GenresAsText = true
                val tag: Tag = Mp4Tag()
                tag.artworkList.clear()
                tag
            }

            else -> WavInfoTag()
        }.also {
            setTrackFieldsToTag(audioItem, it)
        }
    }

    private fun setTrackFieldsToTag(audioItem: AudioItem, tag: Tag) {
        tag.setField(FieldKey.TITLE, audioItem.title)
        tag.setField(FieldKey.ALBUM, audioItem.album.name)
        tag.setField(FieldKey.ALBUM_ARTIST, audioItem.album.albumArtist.name)
        tag.setField(FieldKey.ARTIST, audioItem.artist.name)
        tag.setField(FieldKey.GENRE, audioItem.genre.capitalize())
        tag.setField(FieldKey.COUNTRY, audioItem.artist.countryCode.name)
        audioItem.comments?.let { tag.setField(FieldKey.COMMENT, it) }
        audioItem.trackNumber?.let { tag.setField(FieldKey.TRACK, it.toString()) }
        audioItem.album.year?.let { tag.setField(FieldKey.YEAR, it.toString()) }
        tag.setField(FieldKey.ENCODER, audioItem.encoder)
        tag.setField(FieldKey.GROUPING, audioItem.album.label.name)
        audioItem.discNumber?.let { tag.setField(FieldKey.DISC_NO, it.toString()) }
        tag.setField(FieldKey.IS_COMPILATION, audioItem.album.isCompilation.toString())
        audioItem.bpm?.let {
            if (tag is Mp4Tag) {
                tag.setField(FieldKey.BPM, it.toInt().toString())
            } else {
                tag.setField(FieldKey.BPM, it.toString())
            }
        }
        audioItem.coverImageBytes?.let {
            tag.deleteArtworkField()
            tag.addField(createArtwork(audioItem, it))
        }
    }

    private fun createArtwork(audioItem: AudioItem, coverBytes: ByteArray): Artwork {
        val tempCover: Path
        try {
            tempCover = Files.createTempFile("tempCover_" + audioItem.fileName, ".tmp")
            Files.write(tempCover, coverBytes, StandardOpenOption.CREATE)
            tempCover.toFile().deleteOnExit()
            return ArtworkFactory.createArtworkFromFile(tempCover.toFile())
        } catch (exception: IOException) {
            val errorText = "Error creating artwork of $audioItem"
            logger.error(errorText, exception)
            throw AudioItemManipulationException(errorText, exception)
        }
    }
}