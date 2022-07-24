package net.transgressoft.commons.music;

import com.neovisionaries.i18n.CountryCode;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.audio.wav.WavOptions;
import org.jaudiotagger.tag.FieldDataInvalidException;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.flac.FlacTag;
import org.jaudiotagger.tag.id3.ID3v24Tag;
import org.jaudiotagger.tag.images.Artwork;
import org.jaudiotagger.tag.mp4.Mp4Tag;
import org.jaudiotagger.tag.wav.WavInfoTag;
import org.jaudiotagger.tag.wav.WavTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.time.Duration;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Octavio Calleya
 */
class JAudioTaggerMetadataWriterTest {

    static File mp3File;
    static File wavFile;
    static File flacFile;
    static File m4aFile;
    static File testCover;
    static AudioItem audioItem;

    static String name = "Name";
    static Artist artist = new ImmutableArtist("Artist", CountryCode.UK);
    static byte[] coverBytes;
    static Album album;
    static String comments = "Comments";
    static Genre genre = Genre.ROCK;
    static short trackNumber = 5;
    static short discNumber = 4;
    static float bpm = 128;
    static Duration duration = Duration.ofMinutes(2);
    static String encoder = "transgressoft";
    static String encoding = "LAME MP3";    // Encoding and bitRate cannot be written to the file though it's here in order to mock the audioItem
    static int bitRate = 320;

    AudioItemMetadataWriter metadataWriter;
    AudioFile audioFile;
    Tag tag;

    @BeforeAll
    public static void beforeAllTests() throws Exception {
        mp3File = new File(JAudioTaggerMetadataWriterTest.class.getResource("/testfiles/testeable.mp3").toURI());
        wavFile = new File(JAudioTaggerMetadataWriterTest.class.getResource("/testfiles/testeable.wav").toURI());
        flacFile = new File(JAudioTaggerMetadataWriterTest.class.getResource("/testfiles/testeable.flac").toURI());
        m4aFile = new File(JAudioTaggerMetadataWriterTest.class.getResource("/testfiles/testeable.m4a").toURI());
        testCover = new File(JAudioTaggerMetadataWriterTest.class.getResource("/testfiles/cover.jpg").toURI());

        coverBytes = Files.readAllBytes(testCover.toPath());
        album = new ImmutableAlbum("Album", artist, false, (short) 1992, new ImmutableLabel("EMI", CountryCode.US), coverBytes);

        audioItem = mock(AudioItem.class);
        when(audioItem.name()).thenReturn(name);
        when(audioItem.artist()).thenReturn(artist);
        when(audioItem.album()).thenReturn(album);
        when(audioItem.comments()).thenReturn(comments);
        when(audioItem.genre()).thenReturn(genre);
        when(audioItem.trackNumber()).thenReturn(trackNumber);
        when(audioItem.discNumber()).thenReturn(discNumber);
        when(audioItem.bpm()).thenReturn(bpm);
        when(audioItem.duration()).thenReturn(duration);
        when(audioItem.bitRate()).thenReturn(bitRate);
        when(audioItem.encoder()).thenReturn(encoder);
        when(audioItem.encoding()).thenReturn(encoding);
    }


    @Test
    @DisplayName("Write audio metadata on an mp3 file")
    void writeMetadataOnMp3FileTest() throws Exception {
        clearMp3FileMetadata();

        when(audioItem.path()).thenReturn(mp3File.toPath());
        when(audioItem.fileName()).thenReturn("testeable.mp3");
        metadataWriter = new JAudioTaggerMetadataWriter();
        metadataWriter.writeMetadata(audioItem);

        assertFileMetadata(mp3File);
        assertEquals(genre.capitalize(), tag.getFirst(FieldKey.GENRE));
        assertEquals("MPEG-1 Layer 2", audioFile.getAudioHeader().getEncodingType());
    }

    @Test
    @DisplayName("Write audio metadata on a wav file")
    void writeMetadataOnWavFileTest() throws Exception {
        clearWavFileMetadata();

        when(audioItem.path()).thenReturn(wavFile.toPath());
        when(audioItem.fileName()).thenReturn("testeable.wav");
        metadataWriter = new JAudioTaggerMetadataWriter();
        metadataWriter.writeMetadata(audioItem);

        assertFileMetadata(wavFile);
        assertEquals(genre.capitalize(), tag.getFirst(FieldKey.GENRE));
        assertEquals("WAV PCM 24 bits", audioFile.getAudioHeader().getEncodingType());
    }

    @Test
    @DisplayName("Write audio metadata on a flac file")
    void writeMetadataOnFlacFileTest() throws Exception {
        clearFlacFileMetadata();

        when(audioItem.path()).thenReturn(flacFile.toPath());
        when(audioItem.fileName()).thenReturn("testeable.flac");
        metadataWriter = new JAudioTaggerMetadataWriter();
        metadataWriter.writeMetadata(audioItem);

        assertFileMetadata(flacFile);
        assertEquals(genre.capitalize().toUpperCase(), tag.getFirst(FieldKey.GENRE));
        assertEquals("FLAC 16 bits", audioFile.getAudioHeader().getEncodingType());
    }

    @Test
    @DisplayName("Write audio metadata on a m4a file")
    void writeMetadataOnM4aFileTest() throws Exception {
        clearM4aFileMetadata();

        when(audioItem.path()).thenReturn(m4aFile.toPath());
        when(audioItem.fileName()).thenReturn("testeable.m4a");
        metadataWriter = new JAudioTaggerMetadataWriter();
        metadataWriter.writeMetadata(audioItem);

        assertFileMetadata(m4aFile);
        assertEquals(genre.capitalize().toUpperCase(), tag.getFirst(FieldKey.GENRE));
        assertEquals("Aac", audioFile.getAudioHeader().getEncodingType());
    }

    private void assertFileMetadata(File file) throws Exception {
        audioFile = AudioFileIO.read(file);
        tag = audioFile.getTag();
        Artwork coverOnFile = audioFile.getTag().getFirstArtwork();

        assertEquals(name, tag.getFirst(FieldKey.TITLE));
        assertEquals(album.name(), tag.getFirst(FieldKey.ALBUM));
        assertEquals(artist.name(), tag.getFirst(FieldKey.ALBUM_ARTIST));
        assertEquals(artist.name(), tag.getFirst(FieldKey.ARTIST));
        assertEquals(comments, tag.getFirst(FieldKey.COMMENT));
        assertEquals(album.label().name(), tag.getFirst(FieldKey.GROUPING));
        assertEquals(trackNumber, Integer.parseInt(tag.getFirst(FieldKey.TRACK)));
        assertEquals(discNumber, Short.parseShort(tag.getFirst(FieldKey.DISC_NO)));
        assertEquals(album.year(), Short.parseShort(tag.getFirst(FieldKey.YEAR)));
        assertEquals(bpm, Float.parseFloat(tag.getFirst(FieldKey.BPM)));
        assertEquals(album.isCompilation(), Boolean.valueOf(tag.getFirst(FieldKey.IS_COMPILATION)));
        assertEquals(encoder, tag.getFirst(FieldKey.ENCODER));
        assertTrue(Arrays.equals(coverBytes, coverOnFile.getBinaryData()));
    }

    void clearMp3FileMetadata() throws Exception {
        AudioFile audio = AudioFileIO.read(mp3File);
        Tag tag = new ID3v24Tag();
        tag.getArtworkList().clear();
        resetCommonTagFields(tag);
        audio.setTag(tag);
        audio.commit();
    }

    void clearWavFileMetadata() throws Exception {
        AudioFile audio = AudioFileIO.read(wavFile);
        WavTag wavTag = new WavTag(WavOptions.READ_ID3_ONLY);
        wavTag.setID3Tag(new ID3v24Tag());
        wavTag.setInfoTag(new WavInfoTag());
        resetCommonTagFields(wavTag);
        audio.setTag(wavTag);
        audio.commit();
    }

    void clearFlacFileMetadata() throws Exception {
        AudioFile audio = AudioFileIO.read(flacFile);
        Tag tag = new FlacTag();
        tag.getArtworkList().clear();
        resetCommonTagFields(tag);
        audio.setTag(tag);
        audio.commit();
    }

    void clearM4aFileMetadata() throws Exception {
        AudioFile audio = AudioFileIO.read(m4aFile);
        Tag tag = new Mp4Tag();
        tag.getArtworkList().clear();
        resetCommonTagFields(tag);
        audio.setTag(tag);
        audio.commit();
    }

    void resetCommonTagFields(Tag tag) throws FieldDataInvalidException {
        tag.setField(FieldKey.TITLE, "");
        tag.setField(FieldKey.ALBUM, "");
        tag.setField(FieldKey.ALBUM_ARTIST, "");
        tag.setField(FieldKey.ARTIST, "");
        tag.setField(FieldKey.GENRE, "");
        tag.setField(FieldKey.COMMENT, "");
        tag.setField(FieldKey.GROUPING, "");
        tag.setField(FieldKey.TRACK, Integer.toString(0));
        tag.setField(FieldKey.DISC_NO, Integer.toString(0));
        tag.setField(FieldKey.YEAR, Integer.toString(0));
        tag.setField(FieldKey.BPM, Integer.toString(0));
        tag.setField(FieldKey.IS_COMPILATION, Boolean.toString(false));
        tag.setField(FieldKey.ENCODER, "");
    }
}