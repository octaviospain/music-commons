package net.transgressoft.commons.music.waveform;

import java.nio.file.Path;

/**
 * @author Octavio Calleya
 */
public interface AudioWaveformTool<W extends AudioWaveform> {

    W extractWaveform(Path path) throws AudioWaveformProcessingException;

    W extractWaveform(Path path, int width, int height) throws AudioWaveformProcessingException;
}
