package net.transgressoft.commons.music.audio;

import com.google.common.collect.ImmutableSet;
import com.neovisionaries.i18n.CountryCode;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.Optional;
import java.util.Set;

import static com.google.common.truth.Truth.assertThat;
import static net.transgressoft.commons.music.audio.AlbumAttribute.ALBUM;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.spy;
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
    static byte[] coverBytes;

    static String name = "Yesterday";
    static String artistName = "The Beatles";
    static Artist artist = new ImmutableArtist(artistName, CountryCode.UK);
    static String albumName = "Help!";
    static String albumArtistName = "The Beatles Artist";
    static Artist albumArtist = new ImmutableArtist("The Beatles Artist");
    static boolean isCompilation = false;
    static short year = 1992;
    static String labelName = "EMI";
    static Label label = new ImmutableLabel(labelName);
    static String comments = "Best song ever!";
    static String genre = "Rock";
    static short trackNumber = 5;
    static short discNumber = 4;
    static int bpm = 128;
    static String encoder = "transgressoft";

    Album album;
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

    @BeforeEach
    void beforeEach() {
        album = spy(Album.class);
        when(album.albumArtist()).thenReturn(albumArtist);
        when(album.label()).thenReturn(label);
        when(album.year()).thenReturn(year);
        when(album.name()).thenReturn(albumName);
        when(album.coverImage()).thenReturn(Optional.of(coverBytes));
        when(album.isCompilation()).thenReturn(isCompilation);
    }

    @Test
    @DisplayName("Create AudioItem from Mp3 file")
    void createAudioItemFromMp3FileTest() throws Exception {
        prepareMp3FileMetadata();
        var audioItemRepository = new AudioItemInMemoryRepository(new HashMap<>(), null);

        audioItem = audioItemRepository.createFromFile(mp3FilePath);
        assertThat(audioItemRepository).containsExactly(audioItem);

        assertAudioItem(audioItem, mp3FilePath);
        assertThat("MPEG-1 Layer 2").isEqualTo(audioItem.encoding());
        assertThat(Integer.parseInt("320")).isEqualTo(audioItem.bitRate());
        assertThat(Duration.ofSeconds(61)).isEqualTo(audioItem.duration());
    }

    @Test
    @DisplayName("Create AudioItem from Wav file")
    void createAudioItemFromWavFileTest() throws Exception {
        prepareWavFileMetadata();
        var audioItemRepository = new AudioItemInMemoryRepository(new HashMap<>(), null);

        audioItem = audioItemRepository.createFromFile(wavFilePath);
        assertThat(audioItemRepository).containsExactly(audioItem);

        assertAudioItem(audioItem, wavFilePath);
        assertEquals("WAV PCM 24 bits", audioItem.encoding());
        assertEquals(Integer.parseInt("2116"), audioItem.bitRate());
        assertEquals(Duration.ofSeconds(104), audioItem.duration());
    }

    @Test
    @DisplayName("Create AudioItem from Flac file")
    void createAudioItemFromFlacFileTest() throws Exception {
        prepareFlacFileMetadata();
        var audioItemRepository = new AudioItemInMemoryRepository(new HashMap<>(), null);

        audioItem = audioItemRepository.createFromFile(flacFilePath);
        assertThat(audioItemRepository).containsExactly(audioItem);

        assertAudioItem(audioItem, flacFilePath);
        assertEquals("FLAC 16 bits", audioItem.encoding());
        assertEquals(Integer.parseInt("689"), audioItem.bitRate());
        assertEquals(Duration.ofSeconds(30), audioItem.duration());
    }

    @Test
    @DisplayName("Create AudioFile from M4a file")
    void createAudioItemFromM4aFileTest() throws Exception {
        prepareM4aFileMetadata();
        var audioItemRepository = new AudioItemInMemoryRepository(new HashMap<>(), null);

        audioItem = audioItemRepository.createFromFile(m4aFilePath);
        assertThat(audioItemRepository).containsExactly(audioItem);

        assertAudioItem(audioItem, m4aFilePath);
        assertEquals("Aac", audioItem.encoding());
        assertEquals(Integer.parseInt("256"), audioItem.bitRate());
        assertEquals(Duration.ofSeconds(296), audioItem.duration());
    }

    @Test
    @DisplayName("Contains AudioItem with artist")
    void containsAudioItemWithArtistTest() throws Exception {
        prepareMp3FileMetadata();
        var audioItemRepository = new AudioItemInMemoryRepository(new HashMap<>(), null);

        audioItem = audioItemRepository.createFromFile(mp3FilePath);
        album = audioItem.album();
        assertThat(audioItemRepository).containsExactly(audioItem);
        assertThat(audioItemRepository.containsAudioItemWithArtist(artistName)).isTrue();
        assertThat(audioItemRepository.containsAudioItemWithArtist(albumArtistName)).isTrue();
        var albums = audioItemRepository.artistAlbums(artist);
        var albumHere = albums.iterator().next();
        assertThat(album.albumArtist()).isEqualTo(albumHere.albumArtist());
        assertThat(album.audioItems()).containsExactlyElementsIn(albumHere.audioItems());
        assertThat(album.label()).isEqualTo(albumHere.label());
        assertThat(album.year()).isEqualTo(albumHere.year());
        assertThat(album.name()).isEqualTo(albumHere.name());
        assertThat(album.coverImage().get()).isEqualTo(albumHere.coverImage().get());
        assertThat(album.isCompilation()).isEqualTo(albumHere.isCompilation());
        assertEquals(albumHere.toString(), album.toString());
        assertEquals(album, albumHere);
        assertThat(audioItem.album().audioItems()).isEqualTo(ImmutableSet.of(audioItem));
        assertThat(audioItem.getId()).isEqualTo(1);
    }

    @Test
    @DisplayName("add with same id makes no difference")
    void addAudioItemWithSameIdDoesNotReplaceExistingTest() throws Exception {
        prepareMp3FileMetadata();
        var audioItemRepository = new AudioItemInMemoryRepository(new HashMap<>(), null);

        audioItem = audioItemRepository.createFromFile(mp3FilePath);
        album = audioItem.album();
        assertThat(audioItemRepository).containsExactly(audioItem);

        var testItem = createTestAudioItem(1, album);

        var result = audioItemRepository.add(testItem);
        assertFalse(result);
        assertThat(audioItem.album().audioItems()).containsExactly(audioItem);
        assertThat(audioItem.getId()).isEqualTo(1);
    }

    @Test
    @DisplayName("add with different id makes a difference")
    void addAudioItemWithDifferentIdAddsItTest() throws Exception {
        prepareMp3FileMetadata();
        var audioItemRepository = new AudioItemInMemoryRepository(new HashMap<>(), null);

        audioItem = audioItemRepository.createFromFile(mp3FilePath);
        album = audioItem.album();
        assertThat(audioItemRepository).containsExactly(audioItem);

        var testItem = createTestAudioItem(2, album);
        assertThat(testItem.getId()).isEqualTo(2);

        var result = audioItemRepository.add(testItem);
        assertTrue(result);
        assertThat(audioItem.album().audioItems()).containsExactly(audioItem, testItem);
        assertThat(audioItem.getId()).isEqualTo(1);
    }

    @Test
    @DisplayName("addOrReplace with same id replaces existing one")
    void addOrReplaceAudioItemReplacesExistingTest() throws Exception {
        prepareMp3FileMetadata();
        var audioItemRepository = new AudioItemInMemoryRepository(new HashMap<>(), null);

        audioItem = audioItemRepository.createFromFile(mp3FilePath);
        album = audioItem.album();
        assertThat(audioItem.getId()).isEqualTo(1);

        var testItem = createTestAudioItem(1, album);

        var result = audioItemRepository.addOrReplace(testItem);
        assertTrue(result);
        assertThat(audioItem.album().audioItems()).containsExactly(testItem);
        assertThat(audioItem.getId()).isEqualTo(1);
    }

    @Test
    @DisplayName("Audio set of audio items not created from the repository")
    void addAudioItemsFromAlbumTest() throws Exception {
        prepareMp3FileMetadata();
        var audioItemRepository = new AudioItemInMemoryRepository(new HashMap<>(), null);

        audioItem = audioItemRepository.createFromFile(mp3FilePath);
        album = audioItem.album();
        assertThat(audioItemRepository).hasSize(1);
        assertThat(audioItem.album().audioItems()).containsExactly(audioItem);

        var set = audioItemsSet();
        var result = audioItemRepository.addOrReplaceAll(set);
        assertTrue(result);
        assertThat(audioItemRepository).hasSize(9);
        assertThat(audioItemRepository.search(ALBUM.notEqualsTo(album))).hasSize(5);

        var helpAudioItems = audioItemRepository.search(ALBUM.nameEqualsTo((album.name()), false));
        assertThat(helpAudioItems).hasSize(4);
        assertThat(audioItem.album().audioItems()).containsExactlyElementsIn(helpAudioItems);
    }

    @Test
    @DisplayName("Remove operations")
    void removeOperationsTest() throws Exception {
        prepareMp3FileMetadata();
        prepareM4aFileMetadata();
        prepareFlacFileMetadata();
        prepareWavFileMetadata();

        var audioItemRepository = new AudioItemInMemoryRepository(new HashMap<>(), null);

        var mp3Item = audioItemRepository.createFromFile(mp3FilePath);
        var m4aItem = audioItemRepository.createFromFile(m4aFilePath);
        var flacItem = audioItemRepository.createFromFile(flacFilePath);
        var wavItem = audioItemRepository.createFromFile(wavFilePath);

        assertThat(audioItemRepository).hasSize(4);
        assertThat(audioItemRepository.remove(flacItem)).isTrue();
        assertThat(audioItemRepository).containsExactly(mp3Item, m4aItem, wavItem);

        assertThat(audioItemRepository.removeAll(Set.of(mp3Item, m4aItem, wavItem))).isTrue();
        assertThat(audioItemRepository).isEmpty();
    }

    private Set<AudioItem> audioItemsSet() {
        return ImmutableSet.<AudioItem>builder()
                .add(createTestAudioItem())
                .add(createTestAudioItem())
                .add(createTestAudioItem())
                .add(createTestAudioItem())
                .add(createTestAudioItem())
                .add(createTestAudioItem(album))
                .add(createTestAudioItem(album))
                .add(createTestAudioItem(album))
                .build();
    }

    private void assertAudioItem(AudioItem audioItem, Path path) {
        assertEquals(path, audioItem.path());
        assertThat(audioItem.title()).isEqualTo(name);
        assertThat(audioItem.album().name()).isEqualTo(albumName);
        assertThat(audioItem.album().albumArtist().name()).isEqualTo(albumArtistName);
        assertThat(audioItem.album().albumArtist().countryCode()).isEqualTo(CountryCode.UNDEFINED.name());
        assertThat(audioItem.artist().name()).isEqualTo(artistName);
        assertThat(audioItem.artist().countryCode()).isEqualTo(CountryCode.UK.name());
        assertThat(audioItem.genre()).isEqualTo(Genre.parseGenre(genre));
        assertThat(audioItem.comments()).isEqualTo(comments);
        assertThat(audioItem.album().label().name()).isEqualTo(labelName);
        assertThat(audioItem.album().label().countryCode()).isEqualTo(CountryCode.UNDEFINED.name());
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
        tag.setField(FieldKey.ALBUM, album.name());
        tag.setField(FieldKey.COUNTRY, artist.countryCode());
        tag.setField(FieldKey.ALBUM_ARTIST, album.albumArtist().name());
        tag.setField(FieldKey.ARTIST, artist.name());
        tag.setField(FieldKey.GENRE, genre);
        tag.setField(FieldKey.COMMENT, comments);
        tag.setField(FieldKey.GROUPING, album.label().name());
        tag.setField(FieldKey.TRACK, Short.toString(trackNumber));
        tag.setField(FieldKey.DISC_NO, Short.toString(discNumber));
        tag.setField(FieldKey.YEAR, Short.toString(album.year()));
        tag.setField(FieldKey.BPM, Integer.toString(bpm));
        tag.setField(FieldKey.ENCODER, encoder);
        tag.setField(FieldKey.IS_COMPILATION, Boolean.toString(album.isCompilation()));

        File tempCoverFile = File.createTempFile("tempCover", ".tmp");
        FileUtils.writeByteArrayToFile(tempCoverFile, coverBytes);
        tempCoverFile.deleteOnExit();
        Artwork cover = ArtworkFactory.createArtworkFromFile(tempCoverFile);

        tag.deleteArtworkField();
        tag.addField(cover);
    }
}