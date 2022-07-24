package net.transgressoft.commons.music.waveform;

/**
 * @author Octavio Calleya
 */
public interface AudioWaveform {

    int width();

    int height();

    float[] amplitudes();

    AudioWaveform scale(int width, int height);
}
