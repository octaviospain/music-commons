package com.transgressoft.commons.music;

import org.apache.commons.io.FileUtils;
import org.jaudiotagger.audio.*;
import org.jaudiotagger.audio.exceptions.*;
import org.jaudiotagger.audio.wav.WavOptions;
import org.jaudiotagger.tag.*;
import org.jaudiotagger.tag.flac.FlacTag;
import org.jaudiotagger.tag.id3.ID3v24Tag;
import org.jaudiotagger.tag.images.*;
import org.jaudiotagger.tag.mp4.Mp4Tag;
import org.jaudiotagger.tag.wav.*;
import org.slf4j.*;

import java.io.*;

/**
 * @author Octavio Calleya
 */
public class JAudioTaggerMetadataWriter implements AudioItemMetadataWriter {

    private final Logger LOG = LoggerFactory.getLogger(getClass().getName());

    @Override
    public void writeMetadata(AudioItem audioItem) throws AudioItemManipulationException {
        LOG.debug("Writing AudioItem to file {}", audioItem.path());

        File audioFile = audioItem.path().toFile();
        try {
            AudioFile audio = AudioFileIO.read(audioFile);
            Tag emptyTag = createEmptyTag(audio.getAudioHeader().getFormat());
            audio.setTag(emptyTag);
            setTrackFieldsToTag(audio.getTag(), audioItem);
            audio.commit();
            LOG.debug("AudioItem {} written to file {}", audioItem, audioFile.getAbsolutePath());

            if (audioItem.album().coverImage().isPresent())
                overwriteCoverImage(audioItem, audioFile, audioItem.album().coverImage().get());
        }
        catch (IOException | CannotReadException | ReadOnlyFileException | TagException | CannotWriteException |
                InvalidAudioFrameException exception) {
            LOG.error("Error writing metadata of {}", audioItem, exception);
            String errorText = "Error writing metadata of " + audioItem.path();
            throw new AudioItemManipulationException(errorText, exception);
        }
    }

    private Tag createEmptyTag(String format) {
        if (format.startsWith("Wav")) {
            WavTag wavTag = new WavTag(WavOptions.READ_ID3_ONLY);
            wavTag.setID3Tag(new ID3v24Tag());
            wavTag.setInfoTag(new WavInfoTag());
            return wavTag;
        } else if (format.startsWith("Mp3")) {
            Tag tag = new ID3v24Tag();
            tag.getArtworkList().clear();
            return tag;
        } else if (format.startsWith("Flac")) {
            Tag tag = new FlacTag();
            tag.getArtworkList().clear();
            return tag;
        } else if (format.startsWith("Aac")) {
            Tag tag = new Mp4Tag();
            tag.getArtworkList().clear();
            return tag;
        } else {
            return new WavInfoTag();
        }
    }

    private void setTrackFieldsToTag(Tag tag, AudioItem audioItem) throws FieldDataInvalidException {
        tag.setField(FieldKey.TITLE, audioItem.name());
        tag.setField(FieldKey.ALBUM, audioItem.album().name());
        tag.setField(FieldKey.ALBUM_ARTIST, audioItem.album().albumArtist().name());
        tag.setField(FieldKey.ARTIST, audioItem.artist().name());
        tag.setField(FieldKey.GENRE, audioItem.genre().name());
        tag.setField(FieldKey.COMMENT, audioItem.comments());
        tag.setField(FieldKey.GROUPING, audioItem.album().label().name());
        tag.setField(FieldKey.TRACK, Integer.toString(audioItem.trackNumber()));
        tag.setField(FieldKey.DISC_NO, Integer.toString(audioItem.discNumber()));
        tag.setField(FieldKey.YEAR, Integer.toString(audioItem.album().year()));
        tag.setField(FieldKey.IS_COMPILATION, Boolean.toString(audioItem.album().isCompilation()));
        tag.setField(FieldKey.ENCODER, audioItem.encoder());

        String bpmString = Float.toString(audioItem.bpm());
        if (tag instanceof Mp4Tag) {
            int indexOfDot = bpmString.indexOf('.');
            tag.setField(FieldKey.BPM, bpmString.substring(0, indexOfDot));
        } else {
            tag.setField(FieldKey.BPM, bpmString);
        }
    }

    private void overwriteCoverImage(AudioItem audioItem, File file, byte[] coverBytes) throws AudioItemManipulationException {
        LOG.debug("Writing cover image on file {}", file.getAbsolutePath());

        File tempCoverFile;
        try {
            tempCoverFile = File.createTempFile("tempCover_" + file.getName(), ".tmp");
            FileUtils.writeByteArrayToFile(tempCoverFile, coverBytes);
            tempCoverFile.deleteOnExit();

            AudioFile audioFile = AudioFileIO.read(file);
            Artwork cover = ArtworkFactory.createArtworkFromFile(tempCoverFile);
            Tag tag = audioFile.getTag();
            tag.deleteArtworkField();
            tag.addField(cover);
            audioFile.commit();
            LOG.debug("Cover image of AudioItem {} written to file {}", audioItem, file);
        }
        catch (IOException | CannotWriteException | CannotReadException | TagException | ReadOnlyFileException |
                InvalidAudioFrameException exception) {
            LOG.error("Error writing cover image of {}", file, exception);
            String errorText = "Error writing cover image of " + file.getName();
            throw new AudioItemManipulationException(errorText, exception);
        }
    }
}
