package com.transgressoft.commons.music;

import org.apache.commons.io.FileUtils;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.audio.exceptions.*;
import org.jaudiotagger.audio.wav.WavOptions;
import org.jaudiotagger.tag.*;
import org.jaudiotagger.tag.id3.ID3v24Tag;
import org.jaudiotagger.tag.images.ArtworkFactory;
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

        var audioFile = audioItem.path().toFile();
        try {
            var audio = AudioFileIO.read(audioFile);
            var format = audio.getAudioHeader().getFormat();
            if (format.startsWith("WAV")) {
                var wavTag = new WavTag(WavOptions.READ_ID3_ONLY);
                wavTag.setID3Tag(new ID3v24Tag());
                wavTag.setInfoTag(new WavInfoTag());
                audio.setTag(wavTag);
            }
            setTrackFieldsToTag(audio.getTag(), audioItem);
            audio.commit();
            LOG.debug("AudioItem {} written to file {}", audioItem, audioFile.getAbsolutePath());

            if (audioItem.album().coverImage().isPresent())
                overwriteCoverImage(audioItem, audioFile, audioItem.album().coverImage().get());
        }
        catch (IOException | CannotReadException | ReadOnlyFileException | TagException | CannotWriteException |
                InvalidAudioFrameException exception) {
            LOG.error("Error writing metadata of {}", audioItem, exception);
            var errorText = "Error writing metadata of " + audioItem.path();
            throw new AudioItemManipulationException(errorText, exception);
        }
    }

    private void setTrackFieldsToTag(Tag tag, AudioItem serializableAudioItem) throws FieldDataInvalidException {
        tag.setField(FieldKey.TITLE, serializableAudioItem.name());
        tag.setField(FieldKey.ALBUM, serializableAudioItem.album().name());
        tag.setField(FieldKey.ALBUM_ARTIST, serializableAudioItem.album().albumArtist().name());
        tag.setField(FieldKey.ARTIST, serializableAudioItem.artist().name());
        tag.setField(FieldKey.GENRE, serializableAudioItem.genre().name());
        tag.setField(FieldKey.COMMENT, serializableAudioItem.comments());
        tag.setField(FieldKey.GROUPING, serializableAudioItem.album().label().name());
        tag.setField(FieldKey.TRACK, Integer.toString(serializableAudioItem.trackNumber()));
        tag.deleteField(FieldKey.TRACK_TOTAL);
        tag.setField(FieldKey.DISC_NO, Integer.toString(serializableAudioItem.discNumber()));
        tag.deleteField(FieldKey.DISC_TOTAL);
        tag.setField(FieldKey.YEAR, Integer.toString(serializableAudioItem.album().year()));
        tag.setField(FieldKey.BPM, Float.toString(serializableAudioItem.bpm()));
        tag.setField(FieldKey.IS_COMPILATION, Boolean.toString(serializableAudioItem.album().isCompilation()));
    }

    private void overwriteCoverImage(AudioItem audioItem, File file, byte[] coverBytes) throws AudioItemManipulationException {
        LOG.debug("Writing cover image on file {}", file.getAbsolutePath());

        File tempCoverFile;
        try {
            tempCoverFile = File.createTempFile("tempCover_" + file.getName(), ".tmp");
            FileUtils.writeByteArrayToFile(tempCoverFile, coverBytes);
            tempCoverFile.deleteOnExit();

            var audioFile = AudioFileIO.read(file);
            var cover = ArtworkFactory.createArtworkFromFile(tempCoverFile);
            var tag = audioFile.getTag();
            tag.deleteArtworkField();
            tag.addField(cover);
            audioFile.commit();
            LOG.debug("Cover image of AudioItem {} written to file {}", audioItem, file);
        }
        catch (IOException | CannotWriteException | CannotReadException | TagException | ReadOnlyFileException |
                InvalidAudioFrameException exception) {
            LOG.error("Error writing cover image of {}", file, exception);
            var errorText = "Error writing cover image of " + file.getName();
            throw new AudioItemManipulationException(errorText, exception);
        }
    }
}
