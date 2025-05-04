package net.transgressoft.commons.music.audio

import net.transgressoft.commons.music.audio.ArbitraryAudioFile.getResourceAsFile
import net.transgressoft.commons.music.audio.AudioFileTagType.ID3_V_24
import net.transgressoft.commons.music.audio.AudioFileTagType.MP4_INFO
import io.kotest.core.TestConfiguration
import io.kotest.engine.spec.tempfile
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.next
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.Tag
import org.jaudiotagger.tag.TagOptionSingleton
import org.jaudiotagger.tag.images.ArtworkFactory
import org.jaudiotagger.tag.mp4.Mp4Tag
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption

private val testCover = getResourceAsFile("/testfiles/cover.jpg").toPath()
private val testCover2 = getResourceAsFile("/testfiles/cover-2.jpg").toPath()

val testCoverBytes = Files.readAllBytes(testCover)
val testCoverBytes2 = Files.readAllBytes(testCover)

object ArbitraryAudioFile : TestConfiguration() {

    fun Arb.Companion.realAudioFile(
        audioFileTagType: AudioFileTagType = Arb.enum<AudioFileTagType>().next(),
        attributesAction: AudioItemTestAttributes
    ): Arb<Path> =
        realAudioFile(audioFileTagType, {
            this.title = attributesAction.title
            this.duration = attributesAction.duration
            this.bitRate = attributesAction.bitRate
            this.artist = attributesAction.artist
            this.album = attributesAction.album
            this.bpm = attributesAction.bpm
            this.trackNumber = attributesAction.trackNumber
            this.discNumber = attributesAction.discNumber
            this.comments = attributesAction.comments
            this.genre = attributesAction.genre
            this.encoder = attributesAction.encoder
            this.dateOfCreation = attributesAction.dateOfCreation
            this.lastDateModified = attributesAction.lastDateModified
            this.playCount = attributesAction.playCount
            this.coverImageBytes = attributesAction.coverImageBytes
        })

    fun Arb.Companion.realAudioFile(
        audioFileTagType: AudioFileTagType = Arb.enum<AudioFileTagType>().next(),
        attributesAction: AudioItemTestAttributes.() -> Unit = {}
    ): Arb<Path> =
        arbitrary {
            val attributes = audioAttributes().bind()
            attributesAction(attributes)
            val tag = createTag(audioFileTagType, attributes, audioFileTagType.newActualTag())
            val extension = audioFileTagType.fileType.extension
            val testFile = audioFileTagType.testFile
            tempfile(suffix = ".$extension").also { file ->
                Files.copy(testFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
                file.deleteOnExit()
                AudioFileIO.read(file).apply {
                    this.tag = tag
                    commit()
                }
            }.toPath()
        }

    internal fun createTag(audioFileTagType: AudioFileTagType, attributes: AudioItemTestAttributes, tag: Tag): Tag {
        when (audioFileTagType) {
            MP4_INFO -> {
                TagOptionSingleton.getInstance().isWriteMp4GenresAsText = true
                TagOptionSingleton.getInstance().isWriteMp3GenresAsText = false
            }
            ID3_V_24 -> {
                TagOptionSingleton.getInstance().isWriteMp4GenresAsText = false
                TagOptionSingleton.getInstance().isWriteMp3GenresAsText = true
            }
            else -> {
                TagOptionSingleton.getInstance().isWriteMp4GenresAsText = false
                TagOptionSingleton.getInstance().isWriteMp3GenresAsText = false
            }
        }

        tag.setField(FieldKey.TITLE, attributes.title)
        tag.setField(FieldKey.ALBUM, attributes.album.name)
        tag.setField(FieldKey.COUNTRY, attributes.artist.countryCode.name)
        tag.setField(FieldKey.ALBUM_ARTIST, attributes.album.albumArtist.name)
        tag.setField(FieldKey.ARTIST, attributes.artist.name)
        tag.setField(FieldKey.GENRE, attributes.genre.name)
        attributes.comments?.let { tag.setField(FieldKey.COMMENT, it) }
        attributes.trackNumber?.let { tag.setField(FieldKey.TRACK, it.toString()) }
        attributes.discNumber?.let { tag.setField(FieldKey.DISC_NO, it.toString()) }
        attributes.album.year?.let { tag.setField(FieldKey.YEAR, it.toString()) }
        attributes.encoder?.let { tag.setField(FieldKey.ENCODER, it) }
        tag.setField(FieldKey.IS_COMPILATION, attributes.album.isCompilation.toString())
        tag.setField(FieldKey.GROUPING, attributes.album.label.name)
        attributes.bpm?.let {
            if (tag is Mp4Tag) {
                tag.setField(FieldKey.BPM, it.toInt().toString())
            } else {
                tag.setField(FieldKey.BPM, it.toString())
            }
        }
        attributes.coverImageBytes?.let { setArtworkTag(tag, it) }
        return tag
    }

    internal fun setArtworkTag(tag: Tag, coverBytes: ByteArray?) {
        tempfile("tempCover", ".tmp").also {
            Files.write(it.toPath(), coverBytes!!, StandardOpenOption.TRUNCATE_EXISTING)
            ArtworkFactory.createArtworkFromFile(it).let { artwork ->
                tag.artworkList.clear()
                tag.addField(artwork)
            }
        }
    }

    internal fun getResourceAsFile(resourcePath: String): File {
        val path = if (resourcePath.startsWith("/")) resourcePath else "/$resourcePath"
        val url =
            ArbitraryAudioFile::class.java.getResource(path) ?: ArbitraryAudioFile::class.java.classLoader.getResource(path.removePrefix("/"))
                ?: throw IllegalArgumentException("Resource not found: $resourcePath")

        return when (url.protocol) {
            "file" -> File(url.file)
            "jar" -> { // Extract from JAR to temp file
                val extension = url.file.substringAfterLast('.')
                val tempFile = File.createTempFile("resource-", "-tmp.$extension")
                tempFile.deleteOnExit()
                ArbitraryAudioFile::class.java.getResourceAsStream(resourcePath)?.use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                tempFile
            }

            else -> throw IllegalArgumentException("Unsupported protocol: ${url.protocol}")
        }
    }
}