package net.transgressoft.commons.music.audio;

import com.google.common.collect.ImmutableSet;
import net.transgressoft.commons.music.MusicLibraryTestBase;
import org.apache.commons.io.FileUtils;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.audio.wav.WavOptions;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.flac.FlacTag;
import org.jaudiotagger.tag.id3.ID3v24Tag;
import org.jaudiotagger.tag.images.Artwork;
import org.jaudiotagger.tag.images.ArtworkFactory;
import org.jaudiotagger.tag.mp4.Mp4Tag;
import org.jaudiotagger.tag.wav.WavInfoTag;
import org.jaudiotagger.tag.wav.WavTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.Set;

import static com.google.common.truth.Truth.assertThat;
import static net.transgressoft.commons.music.audio.StringAudioItemAttribute.ALBUM;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AudioItemInMemoryRepositoryTest extends MusicLibraryTestBase {

    static File mp3File;
    static Path mp3FilePath;
    static File wavFile;
    static Path wavFilePath;
    static File flacFile;
    static Path flacFilePath;
    static File m4aFile;
    static Path m4aFilePath;
    static File testCover;

    static String name = "Yesterday";
    static String artist = "The Beatles";
    static byte[] coverBytes;
    static String album = "Help!";
    static String albumArtist = "The Beatles Artist";
    static String comments = "Best song ever!";
    static String genre = "Rock";
    static String label = "EMI";
    static short year = 1992;
    static short trackNumber = 5;
    static short discNumber = 4;
    static int bpm = 128;
    static boolean isCompilation = false;
    static String encoder = "transgressoft";

    AudioItem audioItem;

    @BeforeAll
    public static void beforeAllTests() throws Exception {
        mp3File = new File(AudioItemInMemoryRepositoryTest.class.getResource("/testfiles/testeable.mp3").toURI());
        mp3FilePath = mp3File.toPath();
        wavFile = new File(AudioItemInMemoryRepositoryTest.class.getResource("/testfiles/testeable.wav").toURI());
        wavFilePath = wavFile.toPath();
        flacFile = new File(AudioItemInMemoryRepositoryTest.class.getResource("/testfiles/testeable.flac").toURI());
        flacFilePath = flacFile.toPath();
        m4aFile = new File(AudioItemInMemoryRepositoryTest.class.getResource("/testfiles/testeable.m4a").toURI());
        m4aFilePath = m4aFile.toPath();
        testCover = new File(AudioItemInMemoryRepositoryTest.class.getResource("/testfiles/cover.jpg").toURI());
        coverBytes = Files.readAllBytes(testCover.toPath());
    }

    @Test
    @DisplayName("Contains AudioItem with artist")
    void containsAudioItemWithArtistTest() throws Exception {
        prepareMp3FileMetadata();
        var audioItemRepository = new AudioItemInMemoryRepository(new HashMap<>());

        audioItem = audioItemRepository.createFromFile(mp3File.toPath());
        assertThat(audioItemRepository).hasSize(1);
        assertThat(audioItemRepository.containsAudioItemWithArtist(artist)).isTrue();
        assertThat(audioItemRepository.containsAudioItemWithArtist(albumArtist)).isTrue();
    }

    @Test
    @DisplayName("Create AudioItem from Mp3 file")
    void createAudioItemFromMp3FileTest() throws Exception {
        prepareMp3FileMetadata();
        var audioItemRepository = new AudioItemInMemoryRepository(new HashMap<>());

        audioItem = audioItemRepository.createFromFile(mp3File.toPath());
        assertThat(audioItemRepository).hasSize(1);

        assertAudioItem(audioItem, mp3FilePath);
        assertThat("MPEG-1 Layer 2").isEqualTo(audioItem.encoding());
        assertThat(Integer.parseInt("320")).isEqualTo(audioItem.bitRate());
        assertThat(Duration.ofSeconds(61)).isEqualTo(audioItem.duration());
    }

    @Test
    @DisplayName("Create AudioItem from Wav file")
    void createAudioItemFromWavFileTest() throws Exception {
        prepareWavFileMetadata();
        var audioItemRepository = new AudioItemInMemoryRepository(new HashMap<>());

        audioItem = audioItemRepository.createFromFile(wavFile.toPath());
        assertThat(audioItemRepository).hasSize(1);

        assertAudioItem(audioItem, wavFilePath);
        assertEquals("WAV PCM 24 bits", audioItem.encoding());
        assertEquals(Integer.parseInt("2116"), audioItem.bitRate());
        assertEquals(Duration.ofSeconds(104), audioItem.duration());
    }

    @Test
    @DisplayName("Create AudioItem from Flac file")
    void createAudioItemFromFlacFileTest() throws Exception {
        prepareFlacFileMetadata();
        var audioItemRepository = new AudioItemInMemoryRepository(new HashMap<>());

        audioItem = audioItemRepository.createFromFile(flacFile.toPath());
        assertThat(audioItemRepository).hasSize(1);

        assertAudioItem(audioItem, flacFilePath);
        assertEquals("FLAC 16 bits", audioItem.encoding());
        assertEquals(Integer.parseInt("689"), audioItem.bitRate());
        assertEquals(Duration.ofSeconds(30), audioItem.duration());
    }

    @Test
    @DisplayName("Create AudioFile from M4a file")
    void createAudioItemFromM4aFileTest() throws Exception {
        prepareM4aFileMetadata();
        var audioItemRepository = new AudioItemInMemoryRepository(new HashMap<>());

        audioItem = audioItemRepository.createFromFile(m4aFile.toPath());
        assertThat(audioItemRepository).hasSize(1);

        assertAudioItem(audioItem, m4aFilePath);
        assertEquals("Aac", audioItem.encoding());
        assertEquals(Integer.parseInt("256"), audioItem.bitRate());
        assertEquals(Duration.ofSeconds(296), audioItem.duration());
    }

    @Test
    @DisplayName("Audio items from album")
    void audioItemsFromAlbumTest() throws Exception {
        prepareMp3FileMetadata();
        var audioItemRepository = new AudioItemInMemoryRepository(new HashMap<>());

        audioItem = audioItemRepository.createFromFile(mp3FilePath);
        assertThat(audioItemRepository).hasSize(1);
        assertThat(audioItem.album().audioItems()).containsExactly(audioItem);

        audioItemRepository.addOrReplaceAll(audioItemsSet());
        assertThat(audioItemRepository).hasSize(9);
        assertThat(audioItemRepository.search(ALBUM.notEqualsTo(album))).hasSize(3);

        var helpAudioItems = audioItemRepository.search(ALBUM.equalsTo(album));
        assertThat(helpAudioItems).hasSize(6);
        assertThat(audioItem.album().audioItems()).containsExactlyElementsIn(helpAudioItems);
    }

    private Set<AudioItem> audioItemsSet() {
        int id = 95;
        return ImmutableSet.<AudioItem>builder()
                .add(mockOfHelpAlbum(id++))
                .add(mockOfHelpAlbum(id++))
                .add(mockOfHelpAlbum(id++))
                .add(mockOfHelpAlbum(id++))
                .add(mockOfHelpAlbum(id))
                .add(createTestAudioItem())
                .add(createTestAudioItem())
                .add(createTestAudioItem())
                .build();
    }

    private AudioItem mockOfHelpAlbum(int id) {
        var audioItemMock = mock(AudioItem.class);
        when(audioItemMock.getId()).thenReturn(id);
        when(audioItemMock.title()).thenReturn("HelpAudioItem");
        when(audioItemMock.getAttribute(ALBUM)).thenReturn(album);
        return audioItemMock;
    }

    private void assertAudioItem(AudioItem audioItem, Path path) {
        assertEquals(path, audioItem.path());
        assertThat(audioItem.title()).isEqualTo(name);
        assertThat(audioItem.album().name()).isEqualTo(album);
        assertThat(audioItem.album().albumArtist().name()).isEqualTo(albumArtist);
        assertThat(audioItem.artist().name()).isEqualTo(artist);
        assertThat(audioItem.genre()).isEqualTo(Genre.parseGenre(genre));
        assertThat(audioItem.comments()).isEqualTo(comments);
        assertThat(audioItem.album().label().name()).isEqualTo(label);
        assertThat(audioItem.trackNumber()).isEqualTo(trackNumber);
        assertThat(audioItem.discNumber()).isEqualTo(discNumber);
        assertThat(audioItem.album().year()).isEqualTo(year);
        assertThat(audioItem.bpm()).isEqualTo(bpm);
        assertThat(audioItem.album().isCompilation()).isEqualTo(isCompilation);
        assertThat(audioItem.encoder()).isEqualTo(encoder);
        assertThat(audioItem.album().coverImage().get()).isEqualTo(coverBytes);
    }

    private void prepareMp3FileMetadata() throws Exception {
        AudioFile audio = AudioFileIO.read(mp3File);
        Tag tag = new ID3v24Tag();
        setCommonTagFields(tag);
        audio.setTag(tag);
        audio.commit();
    }

    private void prepareWavFileMetadata() throws Exception {
        AudioFile audio = AudioFileIO.read(wavFile);
        WavTag wavTag = new WavTag(WavOptions.READ_ID3_ONLY);
        wavTag.setID3Tag(new ID3v24Tag());
        wavTag.setInfoTag(new WavInfoTag());
        setCommonTagFields(wavTag);
        audio.setTag(wavTag);
        audio.commit();
    }

    private void prepareFlacFileMetadata() throws Exception {
        AudioFile audio = AudioFileIO.read(flacFile);
        Tag tag = new FlacTag();
        setCommonTagFields(tag);
        audio.setTag(tag);
        audio.commit();
    }

    private void prepareM4aFileMetadata() throws Exception {
        AudioFile audio = AudioFileIO.read(m4aFile);
        Tag tag = new Mp4Tag();
        setCommonTagFields(tag);
        audio.setTag(tag);
        audio.commit();
    }

    private void setCommonTagFields(Tag tag) throws Exception {
        tag.setField(FieldKey.TITLE, name);
        tag.setField(FieldKey.ALBUM, album);
        tag.setField(FieldKey.ALBUM_ARTIST, albumArtist);
        tag.setField(FieldKey.ARTIST, artist);
        tag.setField(FieldKey.GENRE, genre);
        tag.setField(FieldKey.COMMENT, comments);
        tag.setField(FieldKey.GROUPING, label);
        tag.setField(FieldKey.TRACK, Short.toString(trackNumber));
        tag.setField(FieldKey.DISC_NO, Short.toString(discNumber));
        tag.setField(FieldKey.YEAR, Short.toString(year));
        tag.setField(FieldKey.BPM, Integer.toString(bpm));
        tag.setField(FieldKey.ENCODER, encoder);
        tag.setField(FieldKey.IS_COMPILATION, Boolean.toString(isCompilation));

        File tempCoverFile = File.createTempFile("tempCover", ".tmp");
        FileUtils.writeByteArrayToFile(tempCoverFile, coverBytes);
        tempCoverFile.deleteOnExit();
        Artwork cover = ArtworkFactory.createArtworkFromFile(tempCoverFile);

        tag.deleteArtworkField();
        tag.addField(cover);
    }
}