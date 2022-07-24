package com.transgressoft.commons.music.waveform;

import java.nio.file.Path;

/**
 * @author Octavio Calleya
 */
public interface AudioWaveformTool {

    AudioWaveform extractWaveform(Path path) throws AudioWaveformProcessingException;

    AudioWaveform extractWaveform(Path path, int width, int height) throws AudioWaveformProcessingException;
}
