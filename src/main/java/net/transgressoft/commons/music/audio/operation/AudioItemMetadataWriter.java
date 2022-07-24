package net.transgressoft.commons.music.audio.operation;

import net.transgressoft.commons.music.audio.AudioItem;

/**
 * @author Octavio Calleya
 */
public interface AudioItemMetadataWriter {

    void writeMetadata(AudioItem serializableAudioItem) throws AudioItemManipulationException;
}
