package com.transgressoft.commons.music;

/**
 * @author Octavio Calleya
 */
public interface AudioItemMetadataWriter {

    void writeMetadata(AudioItem serializableAudioItem) throws AudioItemManipulationException;
}
