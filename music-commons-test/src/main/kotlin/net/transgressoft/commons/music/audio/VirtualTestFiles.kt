/******************************************************************************
 * Copyright (C) 2025  Octavio Calleya Garcia                                 *
 *                                                                            *
 * This program is free software: you can redistribute it and/or modify       *
 * it under the terms of the GNU General Public License as published by       *
 * the Free Software Foundation, either version 3 of the License, or          *
 * (at your option) any later version.                                        *
 *                                                                            *
 * This program is distributed in the hope that it will be useful,            *
 * but WITHOUT ANY WARRANTY; without even the implied warranty of             *
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the              *
 * GNU General Public License for more details.                               *
 *                                                                            *
 * You should have received a copy of the GNU General Public License          *
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.     *
 ******************************************************************************/

package net.transgressoft.commons.music.audio

import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.next
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.spyk
import org.jaudiotagger.audio.AudioFile
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.Tag
import org.jaudiotagger.tag.images.Artwork
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import kotlin.io.path.absolutePathString

object VirtualFiles {

    private val fileSystem = Jimfs.newFileSystem(Configuration.unix())

    init {
        mockkStatic("kotlin.io.FilesKt__FileReadWriteKt")
        mockkStatic("kotlin.io.FilesKt__UtilsKt")
        mockkStatic("java.nio.file.Paths")
        mockkStatic("org.jaudiotagger.audio.AudioFileIO")
    }

    fun Arb.Companion.virtualAlbumAudioFiles(
        artist: Artist? = null,
        album: Album? = null,
        size: IntRange = 20..50
    ): Arb<List<Path>> =
        arbitrary {
            val arbitraryArtist = artist ?: artist().bind()
            val arbitraryAlbum = album ?: album().bind()
            buildList {
                repeat(Arb.int(size).bind()) {
                    add(
                        virtualAudioFile {
                            this.artist = arbitraryArtist
                            this.album = arbitraryAlbum
                            this.trackNumber = (it.plus(1)).toShort()
                            this.discNumber = 1
                            this.coverImageBytes = testCoverBytes
                        }.bind()
                    )
                }
            }
        }

    fun Arb.Companion.virtualAudioFile(
        audioFileTagType: AudioFileTagType = Arb.enum<AudioFileTagType>().next(),
        attributesAction: AudioItemTestAttributes.() -> Unit = {}
    ): Arb<Path> =
        arbitrary {
            val attributes = audioAttributes().bind()
            attributesAction(attributes)
            create(audioFileTagType, attributes)
        }

    internal fun create(tagType: AudioFileTagType, attributes: AudioItemTestAttributes): Path {
        val fileName =
            buildString {
                append(attributes.trackNumber).append(" ")
                append(attributes.title)
            }
        val filePath =
            buildString {
                append("/").append(attributes.artist.name).append("/")
                append(attributes.album.name).append("/")
            }
        val tag = createMockedTag(tagType.newMockedTag(), attributes)
        val path = createPath(filePath, fileName, tagType.fileType.extension, tag)
        return path
    }

    private fun createMockedTag(tag: Tag, attributes: AudioItemTestAttributes): Tag {
        val artist = attributes.artist.name
        val album = attributes.album.name
        val albumArtist = attributes.album.albumArtist.name
        val title = attributes.title
        val genre = attributes.genre.name
        val year = attributes.album.year
        val trackNumber = attributes.trackNumber
        val discNumber = attributes.discNumber
        val bpm = attributes.bpm
        val comment = attributes.comments
        val encoder = attributes.encoder
        val grouping = attributes.album.label.name
        val isCompilation = attributes.album.isCompilation
        val country = attributes.artist.countryCode.name
        val coverBytes = attributes.coverImageBytes

        every { tag.getFirst(FieldKey.TITLE) } returns title
        every { tag.getFirst(FieldKey.ALBUM) } returns album
        every { tag.getFirst(FieldKey.ARTIST) } returns artist
        every { tag.getFirst(FieldKey.ALBUM_ARTIST) } returns albumArtist
        every { tag.getFirst(FieldKey.GENRE) } returns genre
        every { tag.getFirst(FieldKey.YEAR) } returns year.toString()
        every { tag.getFirst(FieldKey.GROUPING) } returns grouping
        every { tag.getFirst(FieldKey.IS_COMPILATION) } returns isCompilation.toString()
        every { tag.getFirst(FieldKey.COUNTRY) } returns country

        comment?.let { every { tag.getFirst(FieldKey.COMMENT) } returns it }
        trackNumber?.let { every { tag.getFirst(FieldKey.TRACK) } returns it.toString() }
        discNumber?.let { every { tag.getFirst(FieldKey.DISC_NO) } returns it.toString() }
        bpm?.let { every { tag.getFirst(FieldKey.BPM) } returns it.toString() }
        encoder?.let { every { tag.getFirst(FieldKey.ENCODER) } returns it }

        every { tag.hasField(FieldKey.TITLE) } returns true
        every { tag.hasField(FieldKey.ALBUM) } returns true
        every { tag.hasField(FieldKey.ARTIST) } returns true
        every { tag.hasField(FieldKey.ALBUM_ARTIST) } returns true
        every { tag.hasField(FieldKey.GENRE) } returns true
        every { tag.hasField(FieldKey.YEAR) } returns true
        every { tag.hasField(FieldKey.GROUPING) } returns true
        every { tag.hasField(FieldKey.IS_COMPILATION) } returns true
        every { tag.hasField(FieldKey.COUNTRY) } returns true
        every { tag.hasField(FieldKey.TRACK) } returns (trackNumber == null)
        every { tag.hasField(FieldKey.DISC_NO) } returns (discNumber == null)
        every { tag.hasField(FieldKey.BPM) } returns (bpm == null)
        every { tag.hasField(FieldKey.COMMENT) } returns (comment == null)
        every { tag.hasField(FieldKey.ENCODER) } returns (encoder == null)

        coverBytes?.let {
            every { tag.firstArtwork } returns
                mockk<Artwork> {
                    every { binaryData } returns it
                }
            every { tag.artworkList } returns listOf(mockk<Artwork>())
        }

        return tag
    }

    private fun createPath(pathToFile: String, fileName: String, extension: String, tag: Tag): Path {
        val path = fileSystem.getPath(pathToFile)
        val filePathString = "$pathToFile/$fileName.$extension"
        Files.createDirectories(path)
        val filePath = spyk(path.resolve(filePathString))
        val file = JimfsFileAdapter(filePath)
        val parent = filePath.parent
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent)
        }

        Files.write(filePath, byteArrayOf(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)

        every { filePath.toFile() } returns file
        every { filePath.absolutePathString() } returns filePathString

        val audioFile =
            mockk<AudioFile> {
                every { getTag() } returns tag
                every { audioHeader } returns
                    mockk {
                        every { trackLength } returns 180
                        every { sampleRateAsNumber } returns 44100
                        every { format } returns extension
                        every { encodingType } returns format.uppercase()
                        every { bitRate } returns "320"
                    }
            }

        every { Paths.get(filePathString) } returns filePath
        every { Files.exists(filePath) } returns true
        every { AudioFileIO.read(file) } returns audioFile

        return filePath
    }
}

internal class JimfsFileAdapter(private val path: Path) : File(path.toString()) {
    override fun exists() = Files.exists(path)

    override fun getName() = path.fileName.toString()

    override fun toPath() = path

    override fun getAbsolutePath() = path.toAbsolutePath().toString()

    override fun length() = Files.size(path)
}