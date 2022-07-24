package net.transgressoft.commons.music.audio;

/**
 * @author Octavio Calleya
 */
public interface AudioItemMetadataWriter {

    void writeMetadata(AudioItem serializableAudioItem) throws AudioItemManipulationException;
}
