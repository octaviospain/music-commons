package net.transgressoft.commons.music.audio

import mu.KotlinLogging
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
            val audioHeader = audio.audioHeader
            val emptyTag = createEmptyTag(audioHeader.format)
            audio.tag = emptyTag
            setTrackFieldsToTag(audio.tag, audioItem, audioHeader.encodingType)
            TagOptionSingleton.getInstance().isWriteMp4GenresAsText = true
            TagOptionSingleton.getInstance().isWriteMp3GenresAsText = true
            audio.commit()
            logger.debug { "Metadata of $audioItem successfully written to file" }

            audioItem.coverImage?.let {
                overwriteCoverImage(audioItem, audioFile, it)
            }
        } catch (exception: Exception) {
            val errorText = "Error writing metadata of $audioItem"
            logger.error(errorText, exception)
            throw AudioItemManipulationException(errorText, exception)
        }
    }

    private fun createEmptyTag(format: String): Tag {
        return when {
            format.startsWith("Wav", ignoreCase = true) -> {
                val wavTag = WavTag(WavOptions.READ_ID3_ONLY)
                wavTag.iD3Tag = ID3v24Tag()
                wavTag.infoTag = WavInfoTag()
                wavTag
            }
            format.startsWith("Mp3", ignoreCase = true) -> {
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
                val tag: Tag = Mp4Tag()
                tag.artworkList.clear()
                tag
            }
            else -> WavInfoTag()
        }
    }

    private fun setTrackFieldsToTag(tag: Tag, audioItem: AudioItem, encodingType: String) {
        tag.setField(FieldKey.TITLE, audioItem.title)
        tag.setField(FieldKey.ALBUM, audioItem.album.name)
        tag.setField(FieldKey.ALBUM_ARTIST, audioItem.album.albumArtist.name)
        tag.setField(FieldKey.ARTIST, audioItem.artist.name)
        tag.setField(FieldKey.GENRE, audioItem.genre.capitalize())
        tag.setField(FieldKey.COUNTRY, audioItem.artist.countryCode.name)
        audioItem.comments?.let { tag.setField(FieldKey.COMMENT, it)}
        audioItem.trackNumber?.let { tag.setField(FieldKey.TRACK, it.toString()) }
        audioItem.album.year?.let { tag.setField(FieldKey.YEAR, it.toString()) }
        tag.setField(FieldKey.ENCODER, audioItem.encoder)
        if (encodingType != "Alac") {
            tag.setField(FieldKey.GROUPING, audioItem.album.label.name)
            audioItem.discNumber?.let { tag.setField(FieldKey.DISC_NO, it.toString()) }
            tag.setField(FieldKey.IS_COMPILATION, audioItem.album.isCompilation.toString())
            audioItem.bpm?.let {
                val bpmString = it.toString()
                if (tag is Mp4Tag) {
                    val indexOfDot = bpmString.indexOf('.')
                    tag.setField(FieldKey.BPM, bpmString.substring(0, indexOfDot))
                } else {
                    tag.setField(FieldKey.BPM, bpmString)

                }
            }
        }
    }

    private fun overwriteCoverImage(audioItem: AudioItem, file: File, coverBytes: ByteArray) {
        logger.debug { "Saving cover image on file ${file.absolutePath}" }
        val tempCover: Path
        try {
            tempCover = Files.createTempFile("tempCover_" + file.name, ".tmp")
            Files.write(tempCover, coverBytes, StandardOpenOption.CREATE)
            tempCover.toFile().deleteOnExit()
            val audioFile = AudioFileIO.read(file)
            val cover = ArtworkFactory.createArtworkFromFile(tempCover.toFile())
            val tag = audioFile.tag
            tag.deleteArtworkField()
            tag.addField(cover)
            audioFile.commit()

            logger.debug { "Cover image of $audioItem successfully written to file" }
        } catch (exception: IOException) {
            val errorText = "Error writing cover image of $audioItem"
            logger.error(errorText, exception)
            throw AudioItemManipulationException(errorText, exception)
        }
    }
}