package net.transgressoft.commons.music;

import java.nio.file.Path;

/**
 * @author Octavio Calleya
 */
public interface AudioItemMetadataParser {

    AudioItem parseAudioItem(Path audioItemPath) throws AudioItemManipulationException;
}
