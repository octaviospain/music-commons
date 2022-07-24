package net.transgressoft.commons.music.audio.operation;

import net.transgressoft.commons.music.audio.AudioItem;

import java.nio.file.Path;

/**
 * @author Octavio Calleya
 */
public interface AudioItemMetadataParser {

    AudioItem parseAudioItem(Path audioItemPath) throws AudioItemManipulationException;
}
