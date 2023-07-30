package net.transgressoft.commons.music.audio

import com.neovisionaries.i18n.CountryCode
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import net.transgressoft.commons.music.audio.AudioItemTestUtil.flacFile
import net.transgressoft.commons.music.audio.AudioItemTestUtil.m4aFile
import net.transgressoft.commons.music.audio.AudioItemTestUtil.mp3File
import net.transgressoft.commons.music.audio.AudioItemTestUtil.testCoverBytes
import net.transgressoft.commons.music.audio.AudioItemTestUtil.wavFile
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
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Duration

/**
 * @author Octavio Calleya
 */
internal class JAudioTaggerMetadataWriterTest : StringSpec({

    val title = "Name"
    val artistName = "Artist"
    val artist = ImmutableArtist(artistName, CountryCode.UK)
    val albumName = "Album"
    val albumArtistName = "Artist"
    val albumArtist = ImmutableArtist(albumArtistName)
    val isCompilation = false
    val year: Short = 1992
    val labelName = "EMI"
    val label = ImmutableLabel(labelName, CountryCode.US)
    val comments = "Comments"
    val genre = Genre.ROCK
    val trackNumber: Short = 5
    val discNumber: Short = 4
    val bpm = 128f
    val duration = Duration.ofMinutes(2)
    val encoder = "transgressoft"
    val encoding = "LAME MP3" // Encoding and bitRate cannot be written to the file though it's here in order to mock the audioItem
    val bitRate = 320
    val album: Album = mock {
        on { this@on.albumArtist } doReturn albumArtist
        on { this@on.year } doReturn year
        on { this@on.name } doReturn albumName
        on { this@on.label } doReturn label
        on { this@on.isCompilation } doReturn isCompilation
    }

    val audioItem: AudioItem = mock {
        on { this@on.title } doReturn title
        on { this@on.artist } doReturn artist
        on { this@on.album } doReturn album
        on { this@on.comments } doReturn comments
        on { this@on.genre } doReturn genre
        on { this@on.trackNumber } doReturn trackNumber
        on { this@on.discNumber } doReturn discNumber
        on { this@on.bpm } doReturn bpm
        on { this@on.duration } doReturn duration
        on { this@on.bitRate } doReturn bitRate
        on { this@on.encoder } doReturn encoder
        on { this@on.encoding } doReturn encoding
        on { this@on.coverImage } doReturn testCoverBytes
    }

    lateinit var metadataWriter: JAudioTaggerMetadataWriter
    lateinit var audioFile: AudioFile

    beforeEach {
        metadataWriter = JAudioTaggerMetadataWriter()
    }

    "Write audio metadata on an mp3 file" {
        clearMp3FileMetadata()

        whenever(audioItem.path).thenReturn(mp3File.toPath())
        whenever(audioItem.fileName).thenReturn("testeable.mp3")

        metadataWriter.writeMetadata(audioItem)
        audioFile = AudioFileIO.read(mp3File)

        assertFileMetadata(audioFile.tag, audioItem)
        audioFile.audioHeader.encodingType shouldBe "MPEG-1 Layer 3"
    }

    "Write audio metadata on a wav file" {
        clearWavFileMetadata()

        whenever(audioItem.path).thenReturn(wavFile.toPath())
        whenever(audioItem.fileName).thenReturn("testeable.wav")

        metadataWriter.writeMetadata(audioItem)
        audioFile = AudioFileIO.read(wavFile)

        assertFileMetadata(audioFile.tag, audioItem)
        audioFile.audioHeader.encodingType shouldBe "WAV PCM 16 bits"
    }

    "Write audio metadata on a flac file" {
        clearFlacFileMetadata()

        whenever(audioItem.path).thenReturn(flacFile.toPath())
        whenever(audioItem.fileName).thenReturn("testeable.flac")

        metadataWriter.writeMetadata(audioItem)
        audioFile = AudioFileIO.read(flacFile)

        assertFileMetadata(audioFile.tag, audioItem)
        audioFile.audioHeader.encodingType shouldBe "FLAC 16 bits"
    }

    "Write audio metadata on a m4a file" {
        clearM4aFileMetadata()

        whenever(audioItem.path).thenReturn(m4aFile.toPath())
        whenever(audioItem.fileName).thenReturn("testeable.m4a")

        metadataWriter.writeMetadata(audioItem)
        audioFile = AudioFileIO.read(m4aFile)

        assertFileMetadata(audioFile.tag, audioItem)
        audioFile.audioHeader.encodingType shouldBe "Aac"
    }
})

fun assertFileMetadata(tag: Tag, audioItem: AudioItem) {
    tag.getFirst(FieldKey.TITLE) shouldBe audioItem.title
    tag.getFirst(FieldKey.ALBUM) shouldBe audioItem.album.name
    tag.getFirst(FieldKey.ALBUM_ARTIST) shouldBe audioItem.artist.name
    tag.getFirst(FieldKey.ARTIST) shouldBe audioItem.artist.name
    tag.getFirst(FieldKey.COMMENT) shouldBe audioItem.comments
    tag.getFirst(FieldKey.GROUPING) shouldBe audioItem.album.label.name
    tag.getFirst(FieldKey.TRACK).toInt() shouldBe audioItem.trackNumber?.toInt()
    tag.getFirst(FieldKey.DISC_NO).toShort() shouldBe audioItem.discNumber
    tag.getFirst(FieldKey.YEAR).toShort() shouldBe audioItem.album.year
    tag.getFirst(FieldKey.BPM).toFloat() shouldBe audioItem.bpm
    tag.getFirst(FieldKey.IS_COMPILATION).toBoolean() shouldBe audioItem.album.isCompilation
    tag.getFirst(FieldKey.ENCODER) shouldBe audioItem.encoder
    tag.firstArtwork.binaryData shouldBe testCoverBytes
}

fun clearMp3FileMetadata() {
    val audio = AudioFileIO.read(mp3File)
    val tag: Tag = ID3v24Tag()
    tag.artworkList.clear()
    resetCommonTagFields(tag)
    audio.tag = tag
    audio.commit()
}

fun clearWavFileMetadata() {
    val audio = AudioFileIO.read(wavFile)
    val wavTag = WavTag(WavOptions.READ_ID3_ONLY)
    wavTag.iD3Tag = ID3v24Tag()
    wavTag.infoTag = WavInfoTag()
    resetCommonTagFields(wavTag)
    audio.tag = wavTag
    audio.commit()
}

fun clearFlacFileMetadata() {
    val audio = AudioFileIO.read(flacFile)
    val tag: Tag = FlacTag()
    tag.artworkList.clear()
    resetCommonTagFields(tag)
    audio.tag = tag
    audio.commit()
}

fun clearM4aFileMetadata() {
    val audio = AudioFileIO.read(m4aFile)
    val tag: Tag = Mp4Tag()
    tag.artworkList.clear()
    resetCommonTagFields(tag)
    audio.tag = tag
    audio.commit()
}

fun resetCommonTagFields(tag: Tag) {
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