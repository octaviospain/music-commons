package net.transgressoft.commons.music.audio.operation;

import net.transgressoft.commons.music.Genre;
import net.transgressoft.commons.music.audio.AudioItem;
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
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Octavio Calleya
 */
class JAudioTaggerMetadataParserTest {

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
        mp3File = new File(JAudioTaggerMetadataParserTest.class.getResource("/testfiles/testeable.mp3").toURI());
        mp3FilePath = mp3File.toPath();
        wavFile = new File(JAudioTaggerMetadataParserTest.class.getResource("/testfiles/testeable.wav").toURI());
        wavFilePath = wavFile.toPath();
        flacFile = new File(JAudioTaggerMetadataParserTest.class.getResource("/testfiles/testeable.flac").toURI());
        flacFilePath = flacFile.toPath();
        m4aFile = new File(JAudioTaggerMetadataParserTest.class.getResource("/testfiles/testeable.m4a").toURI());
        m4aFilePath = m4aFile.toPath();
        testCover = new File(JAudioTaggerMetadataParserTest.class.getResource("/testfiles/cover.jpg").toURI());
        coverBytes = Files.readAllBytes(testCover.toPath());

    }

    @Test
    @DisplayName("Create AudioItem from Mp3 file")
    void createAudioItemFromMp3FileTest() throws Exception {
        prepareMp3FileMetadata();

        AudioItemMetadataParser audioItemMetadataParser = new JAudioTaggerMetadataParser();
        audioItem = audioItemMetadataParser.parseAudioItem(mp3File.toPath());

        assertAudioItem(audioItem, mp3FilePath);
        assertEquals("MPEG-1 Layer 2", audioItem.encoding());
        assertEquals(Integer.parseInt("320"), audioItem.bitRate());
        assertEquals(Duration.ofSeconds(61), audioItem.duration());
    }

    @Test
    @DisplayName("Create AudioItem from Wav file")
    void createAudioItemFromWavFileTest() throws Exception {
        prepareWavFileMetadata();

        AudioItemMetadataParser audioItemMetadataParser = new JAudioTaggerMetadataParser();
        audioItem = audioItemMetadataParser.parseAudioItem(wavFile.toPath());

        assertAudioItem(audioItem, wavFilePath);
        assertEquals("WAV PCM 24 bits", audioItem.encoding());
        assertEquals(Integer.parseInt("2116"), audioItem.bitRate());
        assertEquals(Duration.ofSeconds(104), audioItem.duration());
    }

    @Test
    @DisplayName("Create AudioItem from Flac file")
    void createAudioItemFromFlacFileTest() throws Exception {
        prepareFlacFileMetadata();

        AudioItemMetadataParser audioItemMetadataParser = new JAudioTaggerMetadataParser();
        audioItem = audioItemMetadataParser.parseAudioItem(flacFile.toPath());

        assertAudioItem(audioItem, flacFilePath);
        assertEquals("FLAC 16 bits", audioItem.encoding());
        assertEquals(Integer.parseInt("689"), audioItem.bitRate());
        assertEquals(Duration.ofSeconds(30), audioItem.duration());
    }

    @Test
    @DisplayName("Create AudioFile from M4a file")
    void createAudioItemFromM4aFileTest() throws Exception {
        prepareM4aFileMetadata();

        AudioItemMetadataParser audioItemMetadataParser = new JAudioTaggerMetadataParser();
        audioItem = audioItemMetadataParser.parseAudioItem(m4aFile.toPath());

        assertAudioItem(audioItem, m4aFilePath);
        assertEquals("Aac", audioItem.encoding());
        assertEquals(Integer.parseInt("256"), audioItem.bitRate());
        assertEquals(Duration.ofSeconds(296), audioItem.duration());
    }

    void assertAudioItem(AudioItem audioItem, Path path) {
        assertEquals(path, audioItem.path());
        assertEquals(name, audioItem.title());
        assertEquals(album, audioItem.album().name());
        assertEquals(albumArtist, audioItem.album().albumArtist().name());
        assertEquals(artist, audioItem.artist().name());
        assertEquals(Genre.parseGenre(genre), audioItem.genre());
        assertEquals(comments, audioItem.comments());
        assertEquals(label, audioItem.album().label().name());
        assertEquals(trackNumber, audioItem.trackNumber());
        assertEquals(discNumber, audioItem.discNumber());
        assertEquals(year, audioItem.album().year());
        assertEquals(bpm, audioItem.bpm());
        assertEquals(isCompilation, audioItem.album().isCompilation());
        assertEquals(encoder, audioItem.encoder());
        assertTrue(Arrays.equals(coverBytes, audioItem.album().coverImage().get()));
    }

    void prepareMp3FileMetadata() throws Exception {
        AudioFile audio = AudioFileIO.read(mp3File);
        Tag tag = new ID3v24Tag();
        setCommonTagFields(tag);
        audio.setTag(tag);
        audio.commit();
    }

    void prepareWavFileMetadata() throws Exception {
        AudioFile audio = AudioFileIO.read(wavFile);
        WavTag wavTag = new WavTag(WavOptions.READ_ID3_ONLY);
        wavTag.setID3Tag(new ID3v24Tag());
        wavTag.setInfoTag(new WavInfoTag());
        setCommonTagFields(wavTag);
        audio.setTag(wavTag);
        audio.commit();
    }

    void prepareFlacFileMetadata() throws Exception {
        AudioFile audio = AudioFileIO.read(flacFile);
        Tag tag = new FlacTag();
        setCommonTagFields(tag);
        audio.setTag(tag);
        audio.commit();
    }

    void prepareM4aFileMetadata() throws Exception {
        AudioFile audio = AudioFileIO.read(m4aFile);
        Tag tag = new Mp4Tag();
        setCommonTagFields(tag);
        audio.setTag(tag);
        audio.commit();
    }

    void setCommonTagFields(Tag tag) throws Exception {
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
