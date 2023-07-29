package net.transgressoft.commons.music.audio

import com.neovisionaries.i18n.CountryCode
import org.jaudiotagger.audio.AudioFile
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.audio.wav.WavOptions
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.Tag
import org.jaudiotagger.tag.flac.FlacTag
import org.jaudiotagger.tag.id3.ID3v24Tag
import org.jaudiotagger.tag.mp4.Mp4Tag
import org.jaudiotagger.tag.wav.WavInfoTag
import org.jaudiotagger.tag.wav.WavTag
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration

/**
 * @author Octavio Calleya
 */
internal class JAudioTaggerMetadataWriterTest {

    var mp3File: File = File(javaClass.getResource("/testfiles/testeable.mp3").toURI())
    var mp3FilePath: Path = mp3File.toPath()
    var wavFile: File = File(javaClass.getResource("/testfiles/testeable.wav").toURI())
    var wavFilePath: Path = wavFile.toPath()
    var flacFile: File = File(javaClass.getResource("/testfiles/testeable.flac").toURI())
    var flacFilePath: Path = flacFile.toPath()
    var m4aFile: File = File(javaClass.getResource("/testfiles/testeable.m4a").toURI())
    var m4aFilePath: Path = m4aFile.toPath()
    var testCover: File = File(javaClass.getResource("/testfiles/cover.jpg").toURI())
    var coverBytes: ByteArray = Files.readAllBytes(testCover.toPath())

    var title = "Name"
    var artistName = "Artist"
    var artist = ImmutableArtist(artistName, CountryCode.UK)
    var albumName = "Album"
    var albumArtistName = "Artist"
    var albumArtist = ImmutableArtist(albumArtistName)
    var isCompilation = false
    var year: Short = 1992
    var labelName = "EMI"
    var label = ImmutableLabel(labelName, CountryCode.US)
    var comments = "Comments"
    var genre = Genre.ROCK
    var trackNumber: Short = 5
    var discNumber: Short = 4
    var bpm = 128f
    var duration = Duration.ofMinutes(2)
    var encoder = "transgressoft"
    var encoding = "LAME MP3" // Encoding and bitRate cannot be written to the file though it's here in order to mock the audioItem
    var bitRate = 320
    var album: Album = mock {
        on { albumArtist } doReturn albumArtist
        on { year } doReturn year
        on { name } doReturn albumName
        on { coverImage } doReturn coverBytes
        on { label } doReturn label
        on { isCompilation } doReturn isCompilation
    }

    val audioItem: AudioItem = mock {
        on { title } doReturn title
        on { artist } doReturn artist
        on { album } doReturn album
        on { comments } doReturn comments
        on { genre } doReturn genre
        on { trackNumber } doReturn trackNumber
        on { discNumber } doReturn discNumber
        on { bpm } doReturn bpm
        on { duration } doReturn duration
        on { bitRate } doReturn bitRate
        on { encoder } doReturn encoder
        on { encoding } doReturn encoding
    }

    lateinit var metadataWriter: JAudioTaggerMetadataWriter
    lateinit var audioFile: AudioFile
    lateinit var tag: Tag

    @Test
    fun `Write audio metadata on an mp3 file`() {
        clearMp3FileMetadata()
        whenever(audioItem.path).thenReturn(mp3FilePath)
        whenever(audioItem.fileName).thenReturn("testeable.mp3")

        metadataWriter = JAudioTaggerMetadataWriter()
        metadataWriter.writeMetadata(audioItem)

        assertFileMetadata(mp3File)
        assertEquals(genre.capitalize(), tag.getFirst(FieldKey.GENRE))
        assertEquals("MPEG-1 Layer 3", audioFile.audioHeader.encodingType)
    }

    @Test
    fun `Write audio metadata on a wav file`() {
        clearWavFileMetadata()
        whenever(audioItem.path).thenReturn(wavFilePath)
        whenever(audioItem.fileName).thenReturn("testeable.wav")

        metadataWriter = JAudioTaggerMetadataWriter()
        metadataWriter.writeMetadata(audioItem)

        assertFileMetadata(wavFile)
        assertEquals(genre.capitalize(), tag.getFirst(FieldKey.GENRE))
        assertEquals("WAV PCM 16 bits", audioFile.audioHeader.encodingType)
    }

    @Test
    fun `Write audio metadata on a flac file`() {
        clearFlacFileMetadata()
        whenever(audioItem.path).thenReturn(flacFilePath)
        whenever(audioItem.fileName).thenReturn("testeable.flac")

        metadataWriter = JAudioTaggerMetadataWriter()
        metadataWriter.writeMetadata(audioItem)

        assertFileMetadata(flacFile)
        assertEquals(genre.capitalize(), tag.getFirst(FieldKey.GENRE))
        assertEquals("FLAC 16 bits", audioFile.audioHeader.encodingType)
    }

    @Test
    fun `Write audio metadata on a m4a file`() {
        clearM4aFileMetadata()
        whenever(audioItem.path).thenReturn(m4aFilePath)
        whenever(audioItem.fileName).thenReturn("testeable.m4a")

        metadataWriter = JAudioTaggerMetadataWriter()
        metadataWriter.writeMetadata(audioItem)

        assertFileMetadata(m4aFile)
        assertEquals(genre.capitalize(), tag.getFirst(FieldKey.GENRE))
        assertEquals("Aac", audioFile.audioHeader.encodingType)
    }

    private fun assertFileMetadata(file: File?) {
        audioFile = AudioFileIO.read(file)
        tag = audioFile.tag
        val coverOnFile = audioFile.tag.firstArtwork
        assertEquals(title, tag.getFirst(FieldKey.TITLE))
        assertEquals(album.name, tag.getFirst(FieldKey.ALBUM))
        assertEquals(artist.name, tag.getFirst(FieldKey.ALBUM_ARTIST))
        assertEquals(artist.name, tag.getFirst(FieldKey.ARTIST))
        assertEquals(comments, tag.getFirst(FieldKey.COMMENT))
        assertEquals(album.label.name, tag.getFirst(FieldKey.GROUPING))
        assertEquals(trackNumber.toInt(), tag.getFirst(FieldKey.TRACK).toInt())
        assertEquals(discNumber, tag.getFirst(FieldKey.DISC_NO).toShort())
        assertEquals(album.year, tag.getFirst(FieldKey.YEAR).toShort())
        assertEquals(bpm, tag.getFirst(FieldKey.BPM).toFloat())
        assertEquals(album.isCompilation, tag.getFirst(FieldKey.IS_COMPILATION).toBoolean())
        assertEquals(encoder, tag.getFirst(FieldKey.ENCODER))
        Assertions.assertArrayEquals(coverBytes, coverOnFile.binaryData)
    }

    private fun clearMp3FileMetadata() {
        val audio = AudioFileIO.read(mp3File)
        val tag: Tag = ID3v24Tag()
        tag.artworkList.clear()
        resetCommonTagFields(tag)
        audio.tag = tag
        audio.commit()
    }

    private fun clearWavFileMetadata() {
        val audio = AudioFileIO.read(wavFile)
        val wavTag = WavTag(WavOptions.READ_ID3_ONLY)
        wavTag.iD3Tag = ID3v24Tag()
        wavTag.infoTag = WavInfoTag()
        resetCommonTagFields(wavTag)
        audio.tag = wavTag
        audio.commit()
    }

    private fun clearFlacFileMetadata() {
        val audio = AudioFileIO.read(flacFile)
        val tag: Tag = FlacTag()
        tag.artworkList.clear()
        resetCommonTagFields(tag)
        audio.tag = tag
        audio.commit()
    }

    private fun clearM4aFileMetadata() {
        val audio = AudioFileIO.read(m4aFile)
        val tag: Tag = Mp4Tag()
        tag.artworkList.clear()
        resetCommonTagFields(tag)
        audio.tag = tag
        audio.commit()
    }

    private fun resetCommonTagFields(tag: Tag) {
        tag.setField(FieldKey.TITLE, "")
        tag.setField(FieldKey.ALBUM, "")
        tag.setField(FieldKey.ALBUM_ARTIST, "")
        tag.setField(FieldKey.ARTIST, "")
        tag.setField(FieldKey.GENRE, "")
        tag.setField(FieldKey.COMMENT, "")
        tag.setField(FieldKey.GROUPING, "")
        tag.setField(FieldKey.TRACK, 0.toString())
        tag.setField(FieldKey.DISC_NO, 0.toString())
        tag.setField(FieldKey.YEAR, 0.toString())
        tag.setField(FieldKey.BPM, 0.toString())
        tag.setField(FieldKey.IS_COMPILATION, false.toString())
        tag.setField(FieldKey.ENCODER, "")
    }
}