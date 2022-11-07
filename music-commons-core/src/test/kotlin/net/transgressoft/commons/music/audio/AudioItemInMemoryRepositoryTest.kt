package net.transgressoft.commons.music.audio

import com.google.common.collect.ImmutableSet
import com.google.common.truth.Truth.assertThat
import com.neovisionaries.i18n.CountryCode
import net.transgressoft.commons.music.MusicLibraryTestBase
import org.jaudiotagger.audio.AudioFile
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.audio.wav.WavOptions
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.Tag
import org.jaudiotagger.tag.flac.FlacTag
import org.jaudiotagger.tag.id3.ID3v24Tag
import org.jaudiotagger.tag.images.Artwork
import org.jaudiotagger.tag.images.ArtworkFactory
import org.jaudiotagger.tag.mp4.Mp4Tag
import org.jaudiotagger.tag.wav.WavInfoTag
import org.jaudiotagger.tag.wav.WavTag
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.spy
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Duration

internal class AudioItemInMemoryRepositoryTest : MusicLibraryTestBase() {

    val mp3File = File(javaClass.getResource("/testfiles/testeable.mp3").toURI())
    val mp3FilePath = mp3File.toPath()
    val wavFile = File(javaClass.getResource("/testfiles/testeable.wav").toURI())
    val wavFilePath = wavFile.toPath()
    val flacFile = File(javaClass.getResource("/testfiles/testeable.flac").toURI())
    val flacFilePath = flacFile.toPath()
    val m4aFile = File(javaClass.getResource("/testfiles/testeable.m4a").toURI())
    val m4aFilePath = m4aFile.toPath()
    val testCover = File(javaClass.getResource("/testfiles/cover.jpg").toURI())
    val coverBytes = Files.readAllBytes(testCover.toPath())

    val title = "Yesterday"
    val artistName = "The Beatles"
    val artist = ImmutableArtist(artistName, CountryCode.UK)
    val albumName = "Help!"
    val albumArtistName = "The Beatles Artist"
    val albumArtist: Artist = ImmutableArtist(albumArtistName)
    val isCompilation = false
    val year: Short = 1992
    val labelName = "EMI"
    val label = ImmutableLabel(labelName)
    val comments = "Best song ever!"
    val genre = "Rock"
    val trackNumber: Short = 5
    val discNumber: Short = 4
    val bpm = 128
    val encoder = "transgressoft"

    lateinit var album: Album
    lateinit var audioItem: AudioItem
    lateinit var audioItemRepository: AudioItemRepository<AudioItem>

    private fun Album.audioItems(): Set<AudioItem> = audioItemRepository.search { it.album == this }.toSet()

    @BeforeEach
    fun beforeEach() {
        album = spy {
            on { albumArtist } doReturn albumArtist
            on { year } doReturn year
            on { name } doReturn albumName
            on { coverImage } doReturn coverBytes
            on { label } doReturn label
            on { isCompilation } doReturn isCompilation
        }
        audioItemRepository = AudioItemInMemoryRepository()
    }

    @Test
    fun `Create AudioItem from Mp3 file`() {
        prepareMp3FileMetadata()

        audioItem = audioItemRepository.createFromFile(mp3FilePath)

        assertThat(audioItemRepository).containsExactly(audioItem)
        assertAudioItem(audioItem, mp3FilePath)
        assertThat("MPEG-1 Layer 2").isEqualTo(audioItem.encoding)
        assertThat("320".toInt()).isEqualTo(audioItem.bitRate)
        assertThat<Duration>(Duration.ofSeconds(61)).isEqualTo(audioItem.duration)
    }

    @Test
    fun `Create AudioItem from Wav file`() {
        prepareWavFileMetadata()

        audioItem = audioItemRepository.createFromFile(wavFilePath)

        assertThat(audioItemRepository).containsExactly(audioItem)
        assertAudioItem(audioItem, wavFilePath)
        Assertions.assertEquals("WAV PCM 24 bits", audioItem.encoding)
        Assertions.assertEquals("2116".toInt(), audioItem.bitRate)
        Assertions.assertEquals(Duration.ofSeconds(104), audioItem.duration)
    }

    @Test
    fun `Create AudioItem from Flac file`() {
        prepareFlacFileMetadata()

        audioItem = audioItemRepository.createFromFile(flacFilePath)

        assertThat(audioItemRepository).containsExactly(audioItem)
        assertAudioItem(audioItem, flacFilePath)
        Assertions.assertEquals("FLAC 16 bits", audioItem.encoding)
        Assertions.assertEquals("689".toInt(), audioItem.bitRate)
        Assertions.assertEquals(Duration.ofSeconds(30), audioItem.duration)
    }

    @Test
    fun `Create AudioFile from M4a file`() {
        prepareM4aFileMetadata()

        audioItem = audioItemRepository.createFromFile(m4aFilePath)

        assertThat(audioItemRepository).containsExactly(audioItem)
        assertAudioItem(audioItem, m4aFilePath)
        Assertions.assertEquals("Aac", audioItem.encoding)
        Assertions.assertEquals("256".toInt(), audioItem.bitRate)
        Assertions.assertEquals(Duration.ofSeconds(296), audioItem.duration)
    }

    @Test
    fun `Contains AudioItem with artist`() {
        prepareMp3FileMetadata()

        audioItem = audioItemRepository.createFromFile(mp3FilePath)
        album = audioItem.album

        assertThat(audioItemRepository).containsExactly(audioItem)
        assertThat(audioItemRepository.containsAudioItemWithArtist(artistName)).isTrue()
        assertThat(audioItemRepository.containsAudioItemWithArtist(albumArtistName)).isTrue()

        val albums = audioItemRepository.artistAlbums(artist)
        val albumHere = albums.iterator().next()

        assertThat<Artist>(album.albumArtist).isEqualTo(albumHere.albumArtist)
        assertThat(album.audioItems()).containsExactlyElementsIn(albumHere.audioItems())
        assertThat(album.label).isEqualTo(albumHere.label)
        assertThat(album.year).isEqualTo(albumHere.year)
        assertThat(album.name).isEqualTo(albumHere.name)
        assertThat(album.coverImage).isEqualTo(albumHere.coverImage)
        assertThat(album.isCompilation).isEqualTo(albumHere.isCompilation)
        Assertions.assertEquals(albumHere.toString(), album.toString())
        Assertions.assertEquals(album, albumHere)
        assertThat(audioItem.album.audioItems()).isEqualTo(ImmutableSet.of(audioItem))
        assertThat(audioItem.id).isEqualTo(1)
    }

    @Test
    fun `add with same id makes no difference`() {
        prepareMp3FileMetadata()

        audioItem = audioItemRepository.createFromFile(mp3FilePath)
        album = audioItem.album

        assertThat(audioItemRepository).containsExactly(audioItem)
        val testItem: AudioItem = createTestAudioItem(1, album)
        val result = audioItemRepository.add(testItem)
        assertFalse(result)
        assertThat(audioItem.album.audioItems()).containsExactly(audioItem)
        assertThat(audioItem.id).isEqualTo(1)
    }

    @Test
    fun `add with different id makes a difference`() {
        prepareMp3FileMetadata()

        audioItem = audioItemRepository.createFromFile(mp3FilePath)
        album = audioItem.album

        assertThat(audioItemRepository).containsExactly(audioItem)
        val testItem: AudioItem = createTestAudioItem(2, album)
        assertThat(testItem.id).isEqualTo(2)
        val result = audioItemRepository.add(testItem)
        Assertions.assertTrue(result)
        assertThat(audioItem.album.audioItems()).containsExactly(audioItem, testItem)
        assertThat(audioItem.id).isEqualTo(1)
    }

    @Test
    fun `addOrReplace with same id replaces existing one`() {
        prepareMp3FileMetadata()

        audioItem = audioItemRepository.createFromFile(mp3FilePath)
        album = audioItem.album

        assertThat(audioItem.id).isEqualTo(1)
        val testItem: AudioItem = createTestAudioItem(1, album)
        val result = audioItemRepository.addOrReplace(testItem)
        Assertions.assertTrue(result)
        assertThat(audioItem.album.audioItems()).containsExactly(testItem)
        assertThat(audioItem.id).isEqualTo(1)
    }

    @Test
    fun `Audio set of audio items not created from the repository`() {
        prepareMp3FileMetadata()

        audioItem = audioItemRepository.createFromFile(mp3FilePath)
        album = audioItem.album

        assertThat(audioItem.album.audioItems()).containsExactly(audioItem)
        val set = audioItemsSet()
        val result = audioItemRepository.addOrReplaceAll(set)
        Assertions.assertTrue(result)
        assertThat(audioItemRepository).hasSize(9)
        assertThat(audioItemRepository.search { it.album != album } ).hasSize(5)

        var helpAudioItems = audioItemRepository.search { it.album.name == album.name }
        assertThat(audioItem.album.audioItems()).containsExactlyElementsIn(helpAudioItems)

        helpAudioItems = audioItemRepository.search { it.album.name == album.name.uppercase() }
        assertThat(audioItem.album.audioItems()).containsAtLeastElementsIn(helpAudioItems)

        helpAudioItems = audioItemRepository.search { it.album.name.equals(album.name.uppercase(), ignoreCase = true) }
        assertThat(audioItem.album.audioItems()).containsExactlyElementsIn(helpAudioItems)

        helpAudioItems = audioItemRepository.search { it.album == album }
        assertThat(audioItem.album.audioItems()).containsExactlyElementsIn(helpAudioItems)

    }

    @Test
    fun `Remove operations`() {
        prepareMp3FileMetadata()
        prepareM4aFileMetadata()
        prepareFlacFileMetadata()
        prepareWavFileMetadata()

        val mp3Item = audioItemRepository.createFromFile(mp3FilePath)
        val m4aItem = audioItemRepository.createFromFile(m4aFilePath)
        val flacItem = audioItemRepository.createFromFile(flacFilePath)
        val wavItem = audioItemRepository.createFromFile(wavFilePath)

        assertThat(audioItemRepository).hasSize(4)
        assertThat(audioItemRepository.remove(flacItem)).isTrue()
        assertThat(audioItemRepository).containsExactly(mp3Item, m4aItem, wavItem)
        assertThat(audioItemRepository.removeAll(setOf(mp3Item, m4aItem, wavItem))).isTrue()
        assertThat(audioItemRepository).isEmpty()
    }

    private fun audioItemsSet() = buildSet {
        add(createTestAudioItem())
        add(createTestAudioItem())
        add(createTestAudioItem())
        add(createTestAudioItem())
        add(createTestAudioItem())
        add(createTestAudioItem(album))
        add(createTestAudioItem(album))
        add(createTestAudioItem(album))
    }

    private fun assertAudioItem(audioItem: AudioItem, path: Path?) {
        assertThat(audioItem.path).isEqualTo(path)
        assertThat(audioItem.title).isEqualTo(title)
        assertThat(audioItem.album.name).isEqualTo(albumName)
        assertThat(audioItem.album.albumArtist.name).isEqualTo(albumArtistName)
        assertThat(audioItem.album.albumArtist.countryCode).isEqualTo(CountryCode.UNDEFINED)
        assertThat(audioItem.artist.name).isEqualTo(artistName)
        assertThat(audioItem.artist.countryCode).isEqualTo(CountryCode.UK)
        assertThat(audioItem.genre).isEqualTo(Genre.parseGenre(genre))
        assertThat(audioItem.comments).isEqualTo(comments)
        assertThat(audioItem.album.label.name).isEqualTo(labelName)
        assertThat(audioItem.album.label.countryCode).isEqualTo(CountryCode.UNDEFINED)
        assertThat(audioItem.trackNumber).isEqualTo(trackNumber)
        assertThat(audioItem.discNumber).isEqualTo(discNumber)
        assertThat(audioItem.album.year).isEqualTo(year)
        assertThat(audioItem.bpm).isEqualTo(bpm)
        assertThat(audioItem.album.isCompilation).isEqualTo(isCompilation)
        assertThat(audioItem.encoder).isEqualTo(encoder)
        assertThat(audioItem.album.coverImage).isEqualTo(coverBytes)
    }

    private fun prepareMp3FileMetadata() {
        val audio: AudioFile = AudioFileIO.read(mp3File)
        val tag: Tag = ID3v24Tag()
        setCommonTagFields(tag)
        audio.tag = tag
        audio.commit()
    }

    private fun prepareWavFileMetadata() {
        val audio: AudioFile = AudioFileIO.read(wavFile)
        val wavTag = WavTag(WavOptions.READ_ID3_ONLY)
        wavTag.iD3Tag = ID3v24Tag()
        wavTag.infoTag = WavInfoTag()
        setCommonTagFields(wavTag)
        audio.tag = wavTag
        audio.commit()
    }

    private fun prepareFlacFileMetadata() {
        val audio: AudioFile = AudioFileIO.read(flacFile)
        val tag: Tag = FlacTag()
        setCommonTagFields(tag)
        audio.tag = tag
        audio.commit()
    }

    private fun prepareM4aFileMetadata() {
        val audio: AudioFile = AudioFileIO.read(m4aFile)
        val tag: Tag = Mp4Tag()
        setCommonTagFields(tag)
        audio.tag = tag
        audio.commit()
    }

    private fun setCommonTagFields(tag: Tag) {
        tag.setField(FieldKey.TITLE, title)
        tag.setField(FieldKey.ALBUM, album.name)
        tag.setField(FieldKey.COUNTRY, artist.countryCode.name)
        tag.setField(FieldKey.ALBUM_ARTIST, album.albumArtist.name)
        tag.setField(FieldKey.ARTIST, artist.name)
        tag.setField(FieldKey.GENRE, genre)
        tag.setField(FieldKey.COMMENT, comments)
        tag.setField(FieldKey.GROUPING, album.label.name)
        tag.setField(FieldKey.TRACK, trackNumber.toString())
        tag.setField(FieldKey.DISC_NO, discNumber.toString())
        tag.setField(FieldKey.YEAR, album.year.toString())
        tag.setField(FieldKey.BPM, bpm.toString())
        tag.setField(FieldKey.ENCODER, encoder)
        tag.setField(FieldKey.IS_COMPILATION, album.isCompilation.toString())

        val tempCoverFile = File.createTempFile("tempCover", ".tmp")
        Files.write(tempCoverFile.toPath(), coverBytes, StandardOpenOption.CREATE)
        tempCoverFile.deleteOnExit()

        val cover: Artwork = ArtworkFactory.createArtworkFromFile(tempCoverFile)
        tag.deleteArtworkField()
        tag.addField(cover)
    }
}