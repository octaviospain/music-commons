package com.transgressoft.commons.music.waveform;

import java.nio.file.Path;

/**
 * @author Octavio Calleya
 */
public interface AudioWaveformTool {

    SimpleAudioWaveform extractWaveform(Path path) throws AudioWaveformProcessingException;

    SimpleAudioWaveform extractWaveform(Path path, int width, int height) throws AudioWaveformProcessingException;
}
